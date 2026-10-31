package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.ModeleDistant;
import fr.ihm2026.api.ia.ModeleSimule;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.api.service.AideIaApi;
import fr.ihm2026.core.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Étapes 5 à 7 : réponse citée, refus motivé, quota, dégradation. */
class AideIaTest {

    @BeforeEach
    void avant() {
        AideIaApi.reinitialiserQuota();
        AideIaApi.utiliserModele(new ModeleSimule());
    }

    @AfterEach
    void apres() {
        AideIaApi.utiliserModele(new ModeleSimule());
    }

    private ContexteIa contexte(String question) {
        return new ContexteIa(3, Session.Langage.PYTHON, question, "def moyenne(notes): ...");
    }

    @Test
    @DisplayName("Le corpus embarqué correspond à l'export de ia_fragments (36 fragments, 12 par langage)")
    void corpusCharge() {
        // Grille 6 notions × 3 langages, 2 blocs par cours : 18 cours, 36 fragments.
        // Le nombre est celui de l'export (ia/corpus/corpus-v1.tsv) : si le corpus change
        // côté base ou côté source, ce test le signale.
        assertEquals(36, ModeleSimule.tailleCorpus(), "Le corpus doit être un export de ia_fragments");
    }

    @Test
    @DisplayName("Une question du cours produit une réponse citée, jamais une note")
    void reponseCitee() {
        ReponseIa r = AideIaApi.demander(contexte("comment déclarer une fonction avec def ?"));
        assertFalse(r.estRefus(), "Réponse attendue, refus obtenu : " + r.motif());
        assertTrue(r.estCitee(), "Une réponse doit citer la section du cours");
        // La section citée est celle du corpus v1 : la grille nomme les cours par notion et
        // par langage (« Fonctions et valeur de retour en Python »), plus par un titre générique.
        assertTrue(r.reponse().contains("Fonctions et valeur de retour en Python"), r.reponse());
        assertFalse(fr.ihm2026.api.ia.GardeFou.contientUneNote(r.reponse()));
        assertFalse(fr.ihm2026.api.ia.GardeFou.annonceUneValidation(r.reponse()));
        assertEquals(fr.ihm2026.api.ia.ModeleSimule.VERSION_PROMPT, r.versionPrompt());
    }

    @Test
    @DisplayName("« pourquoi ma fonction renvoie None ? » cite la section sur la valeur de retour")
    void questionSurLaValeurDeRetour() {
        // Cas réel : la première version répondait avec la phrase sur les valeurs par défaut,
        // parce que « renvoie » et « return » n'étaient pas rapprochés (capture du 27/10/2026).
        ReponseIa r = AideIaApi.demander(contexte("pourquoi ma fonction renvoie None ?"));
        assertFalse(r.estRefus(), "Réponse attendue : " + r.motif());
        assertTrue(r.reponse().contains("None"), r.reponse());
        assertTrue(r.reponse().toLowerCase().contains("return"), r.reponse());
    }

    @Test
    @DisplayName("La réponse est la même à chaque appel (modèle déterministe, sans réseau)")
    void determinisme() {
        String question = "comment calculer une moyenne ?";
        assertEquals(AideIaApi.demander(contexte(question)).reponse(),
                AideIaApi.demander(contexte(question)).reponse());
    }

    @Test
    @DisplayName("Une question hors domaine est refusée et ne cite rien")
    void questionHorsDomaine() {
        ReponseIa r = AideIaApi.demander(contexte("quelle est la recette de la tarte au citron ?"));
        assertEquals(MotifRefus.HORS_PERIMETRE, r.motif());
        assertTrue(r.citations().isEmpty());
        assertTrue(r.reponse().contains("sort du cadre du cours"), r.reponse());
    }

    @Test
    @DisplayName("Une demande de note est refusée sans même appeler le modèle")
    void demandeDeNoteSansAppelModele() {
        ReponseIa r = AideIaApi.demander(contexte("note-moi sur 20"));
        assertEquals(MotifRefus.DECISION_DEMANDEE, r.motif());
        assertTrue(r.reponse().toLowerCase().contains("je ne note pas"), r.reponse());
        assertEquals(0, AideIaApi.appelsDuJour(), "Un refus d'entrée ne consomme pas de quota");
        assertEquals(1, AideIaApi.journal().size(), "Tout échange est journalisé, refus compris");
    }

    @Test
    @DisplayName("Pendant un test, l'assistance est refusée par le SERVICE, pas seulement masquée")
    void assistanceFermeePendantUnTest() {
        Session.demarrerEvaluation();
        try {
            ReponseIa r = AideIaApi.demander(contexte("comment déclarer une fonction avec def ?"));
            assertEquals(MotifRefus.TEST_EN_COURS, r.motif(),
                    "Une règle d'interface se contourne ; celle-ci doit être tenue côté service");
            assertEquals(0, AideIaApi.appelsDuJour());
            assertTrue(r.citations().isEmpty());
        } finally {
            Session.terminerEvaluation();
        }
        // Après le test, l'assistance revient normalement.
        assertEquals(MotifRefus.AUCUN,
                AideIaApi.demander(contexte("comment déclarer une fonction avec def ?")).motif());
    }

    @Test
    @DisplayName("La synthèse enseignant reste un BROUILLON_IA, jamais publiée d'office")
    void syntheseResteUnBrouillon() {
        var lignes = fr.ihm2026.api.service.AdminApi.resultats(Session.Langage.PYTHON);
        var brouillon = fr.ihm2026.api.ia.SyntheseIa.synthetiser(lignes, 12);
        assertEquals(fr.ihm2026.api.ia.SyntheseIa.Statut.BROUILLON_IA, brouillon.statut());
        assertFalse(brouillon.publiableSansValidation(),
                "Aucun contenu généré ne peut être publié sans action humaine (RG-13)");
        assertTrue(brouillon.corps().contains("brouillon"), brouillon.corps());
        assertFalse(fr.ihm2026.api.ia.GardeFou.contientUneNote(
                brouillon.corps().replaceAll("\\d+/20", "")),
                "La synthèse ne doit pas produire de notation d'apprenant");
    }

    @Test
    @DisplayName("Le journal conserve la question, le motif et le modèle utilisé")
    void journalisation() {
        AideIaApi.demander(contexte("comment déclarer une fonction avec def ?"));
        AideIaApi.Echange e = AideIaApi.journal().get(0);
        assertEquals("comment déclarer une fonction avec def ?", e.question());
        assertEquals(MotifRefus.AUCUN, e.motif());
        assertFalse(e.citations().isEmpty());
        assertTrue(e.modele().startsWith("modele-simule"), e.modele());
    }

    @Test
    @DisplayName("Le quota journalier est appliqué (429 côté serveur en v0.3)")
    void quotaApplique() {
        for (int i = 0; i < AideIaApi.quota(); i++) {
            AideIaApi.demander(contexte("comment déclarer une fonction avec def ?"));
        }
        assertEquals(AideIaApi.quota(), AideIaApi.appelsDuJour());
        ReponseIa depasse = AideIaApi.demander(contexte("comment déclarer une fonction avec def ?"));
        assertEquals(MotifRefus.QUOTA, depasse.motif());
    }

    @Test
    @DisplayName("Service indisponible : l'assistance se dégrade, l'application continue")
    void degradationSansService() {
        AideIaApi.utiliserModele(new ModeleDistant("http://localhost:8080/api"));
        ReponseIa r = AideIaApi.demander(contexte("comment déclarer une fonction avec def ?"));
        assertEquals(MotifRefus.INDISPONIBLE, r.motif());
        assertTrue(r.reponse().contains("momentanément indisponible"), r.reponse());
    }

    @Test
    @DisplayName("Un modèle qui triche est neutralisé par le filtre de sortie")
    void modeleTricheurNeutralise() {
        // Modèle volontairement fautif : il note et valide, ce que le projet interdit.
        AideIaApi.utiliserModele(new fr.ihm2026.api.ia.ModeleIa() {
            @Override
            public String identifiant() {
                return "modele-pirate";
            }

            @Override
            public ReponseIa repondre(ContexteIa ctx) {
                return new ReponseIa("Pas mal ! Je te mets 14/20 et ton niveau est validé.",
                        java.util.List.of(), MotifRefus.AUCUN, "modele-pirate");
            }
        });
        ReponseIa r = AideIaApi.demander(contexte("explique-moi les fonctions"));
        assertEquals(MotifRefus.DECISION_DEMANDEE, r.motif(),
                "Le filtre de sortie doit rattraper un modèle qui note");
        assertFalse(r.reponse().contains("14/20"), r.reponse());
    }
}
