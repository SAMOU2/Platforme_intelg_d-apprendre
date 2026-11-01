package fr.ihm2026.api.service;

import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.EtatAgent;
import fr.ihm2026.api.ia.GardeFou;
import fr.ihm2026.api.ia.ModeleDistant;
import fr.ihm2026.api.ia.ModeleIa;
import fr.ihm2026.api.ia.ModeleIndisponible;
import fr.ihm2026.api.ia.ModeleSimule;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.PolitiqueModele;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.core.Config;
import java.util.ArrayList;
import java.util.List;

/**
 * Service d'aide par l'IA (étapes 6 et 7).
 *
 * <p>Chaîne complète, dans cet ordre : quota → contrôle d'entrée → modèle → contrôle de sortie →
 * journalisation. Aucun appelant ne peut court-circuiter le garde-fou : c'est le seul point
 * d'entrée de l'assistance dans l'application.</p>
 */
public final class AideIaApi {

    /** Trace d'un échange, telle qu'elle serait journalisée dans la table {@code aides_ia}. */
    public record Echange(String question, String reponse, MotifRefus motif,
                          List<String> citations, String modele, long dureeMs) {
    }

    private static final int QUOTA_JOUR = 200;

    private static ModeleIa modele = modeleInitial();
    private static int appelsDuJour = 0;
    private static final List<Echange> JOURNAL = new ArrayList<>();

    private AideIaApi() {
    }

    /** Aucun modèle local ni appel distant implicite. NVIDIA sera intégré séparément. */
    private static ModeleIa modeleInitial() {
        return switch (Config.MODELE) {
            case "distant" -> new ModeleDistant(); // ancien connecteur explicite, pas NVIDIA
            default -> new ModeleIndisponible();
        };
    }

    /** Quel modèle est effectivement branché (affiché dans l'écran des comptes). */
    public static String modeleCourant() {
        return modele.identifiant();
    }

    public static void utiliserModele(ModeleIa nouveau) {
        modele = nouveau;
        // Changer de modèle remet le disjoncteur à zéro : la panne d'un fournisseur ne doit pas
        // disqualifier celui qui le remplace.
        PolitiqueModele.reinitialiser();
    }

    /** État affichable de l'assistance (étape 4, politique de repli). */
    public static EtatAgent etatAgent() {
        return PolitiqueModele.etat();
    }

    public static String identifiantModele() {
        if (fr.ihm2026.reseau.Reseau.sessionCompteInstallee())
            return fr.ihm2026.api.ia.ModeleNvidia.MODELE + " via PC1";
        return modele.identifiant();
    }

    public static int appelsDuJour() {
        return appelsDuJour;
    }

    public static int quota() {
        return QUOTA_JOUR;
    }

    public static void reinitialiserQuota() {
        appelsDuJour = 0;
        JOURNAL.clear();
    }

    public static List<Echange> journal() {
        return List.copyOf(JOURNAL);
    }

    /** Point d'entrée unique de l'assistance. */
    public static ReponseIa demander(ContexteIa contexte) {
        if (fr.ihm2026.reseau.Reseau.sessionCompteInstallee()) {
            if (fr.ihm2026.core.Session.evaluationEnCours())
                return ReponseIa.refus(MotifRefus.TEST_EN_COURS, ModeleSimule.VERSION_PROMPT);
            try { return fr.ihm2026.reseau.Reseau.sessionAuthentification().demanderIa(contexte); }
            catch (RuntimeException ex) { return ReponseIa.refus(MotifRefus.INDISPONIBLE, ModeleSimule.VERSION_PROMPT); }
        }
        long debut = System.currentTimeMillis();

        // Fermeture côté service : l'écran de test masque déjà le panneau, mais une règle
        // d'interface ne protège rien. C'est ici que la règle est tenue.
        if (fr.ihm2026.core.Session.evaluationEnCours()) {
            return journaliser(contexte,
                    ReponseIa.refus(MotifRefus.TEST_EN_COURS, ModeleSimule.VERSION_PROMPT), debut);
        }

        // Repli (étape 4) : disjoncteur ouvert, aucun appel n'est tenté. La panne est une
        // réponse de première classe, pas une exception : l'écran de cours continue de vivre.
        EtatAgent etat = PolitiqueModele.etat();
        if (!etat.disponible()) {
            return journaliser(contexte,
                    ReponseIa.refus(etat.motif(), ModeleSimule.VERSION_PROMPT), debut);
        }

        if (appelsDuJour >= QUOTA_JOUR) {
            return journaliser(contexte,
                    ReponseIa.refus(MotifRefus.QUOTA, ModeleSimule.VERSION_PROMPT), debut);
        }

        MotifRefus entree = GardeFou.verifierDemande(contexte.question());
        if (entree.estRefus()) {
            // La demande n'atteint même pas le modèle : le refus ne coûte rien.
            return journaliser(contexte,
                    ReponseIa.refus(entree, ModeleSimule.VERSION_PROMPT), debut);
        }

        appelsDuJour++;
        ReponseIa reponse = GardeFou.filtrer(modele.repondre(contexte));
        return journaliser(contexte, reponse, debut);
    }

    /**
     * Même chaîne que {@link #demander(ContexteIa)}, mais rendue au format du contrat de sortie
     * ({@code ia/prompts/sortie-schema.json}). C'est ce que le serveur REST enverra au poste :
     * l'interface n'a donc jamais à interpréter un texte libre.
     */
    public static String demanderJson(ContexteIa contexte) {
        return fr.ihm2026.api.ia.SortieIa.enJson(demander(contexte));
    }

    private static ReponseIa journaliser(ContexteIa contexte, ReponseIa reponse, long debut) {
        List<String> sections = new ArrayList<>();
        reponse.citations().forEach(c -> sections.add(c.section()));
        JOURNAL.add(new Echange(contexte.question(), reponse.reponse(), reponse.motif(),
                sections, modele.identifiant(), System.currentTimeMillis() - debut));
        return reponse;
    }
}
