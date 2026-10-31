package fr.ihm2026.api.ia;

import fr.ihm2026.core.Session;
import fr.ihm2026.model.UtilisateurVM;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Positionnement <b>B</b> — agent côté enseignant (étape 9).
 *
 * <p>L'agent ne publie rien : il prépare un <b>brouillon</b> à partir des résultats agrégés,
 * que l'enseignant relit, modifie ou jette. Le statut d'un contenu produit ici est
 * {@link Statut#BROUILLON_IA} et il n'existe aucun chemin de code menant directement à
 * {@link Statut#PUBLIE} : la publication exige une action humaine explicite.</p>
 *
 * <p>La synthèse est <b>déterministe</b> : elle est calculée à partir des notes réelles, sans
 * modèle génératif. C'est un choix assumé — sur des données chiffrées, une phrase générée
 * n'apporte rien qu'un calcul ne donne déjà, et introduit un risque d'erreur.</p>
 */
public final class SyntheseIa {

    /** Cycle de vie d'un contenu assisté par l'agent (aligné sur le schéma PostgreSQL). */
    public enum Statut {
        BROUILLON_IA, EN_RELECTURE, PUBLIE
    }

    /**
     * Brouillon produit par l'agent, avec son statut et sa provenance.
     * {@code motif} vaut {@link MotifRefus#AUCUN} quand la synthèse a pu être produite :
     * un refus est une réponse de première classe, ici comme dans le positionnement A.
     */
    public record Brouillon(String titre, String corps, Statut statut, String source,
                            MotifRefus motif) {

        public Brouillon(String titre, String corps, Statut statut, String source) {
            this(titre, corps, statut, source, MotifRefus.AUCUN);
        }

        public boolean estRefus() {
            return motif.estRefus();
        }

        /** Un brouillon n'est jamais publiable tel quel : seule une action humaine le publie. */
        public boolean publiableSansValidation() {
            return false;
        }
    }

    /**
     * Contrôle de périmètre en position B : l'agent ne produit que pour les trois langages
     * du cursus. Une demande hors programme est refusée avec le même motif qu'en position A.
     *
     * @param langageDemande libellé demandé par l'enseignant (« Rust », « Java »…)
     */
    public static MotifRefus verifierPerimetre(String langageDemande) {
        String demande = fr.ihm2026.api.ia.GardeFou.normaliser(langageDemande);
        for (Session.Langage langage : Session.Langage.values()) {
            if (demande.contains(fr.ihm2026.api.ia.GardeFou.normaliser(langage.label()))) {
                return MotifRefus.AUCUN;
            }
        }
        return MotifRefus.HORS_PERIMETRE;
    }

    /** Nombre minimal de résultats pour qu'une synthèse de groupe ait un sens. */
    public static final int MINIMUM_RESULTATS = 3;

    private SyntheseIa() {
    }

    /**
     * Produit un brouillon de synthèse pour une promotion et un niveau donnés.
     *
     * @param lignes résultats individuels du niveau (notes et nombre de tentatives)
     */
    public static Brouillon synthetiser(List<UtilisateurVM> lignes, int seuil) {
        if (lignes.size() < MINIMUM_RESULTATS) {
            // Refus motivé plutôt que synthèse sur un échantillon non représentatif.
            return new Brouillon("Synthèse du niveau", MotifRefus.DONNEES_INSUFFISANTES.message(),
                    Statut.BROUILLON_IA, "moins de " + MINIMUM_RESULTATS + " résultats",
                    MotifRefus.DONNEES_INSUFFISANTES);
        }

        List<UtilisateurVM> triees = new ArrayList<>(lignes);
        triees.sort(Comparator.comparingInt(UtilisateurVM::getNote));

        double moyenne = triees.stream().mapToInt(UtilisateurVM::getNote).average().orElse(0);
        long valides = triees.stream().filter(u -> u.getNote() >= seuil).count();
        long difficulte = triees.stream().filter(UtilisateurVM::isEnDifficulte).count();
        long reussitePremiere = triees.stream()
                .filter(u -> u.getNote() >= seuil && u.getTentatives() <= 1).count();

        StringBuilder corps = new StringBuilder();
        corps.append("Sur ").append(triees.size()).append(" apprenants évalués, ")
             .append(valides).append(" atteignent le seuil de ").append(seuil).append("/20 (")
             .append(String.format("%.0f", 100.0 * valides / triees.size())).append(" %). ")
             .append("La moyenne du groupe est de ").append(String.format("%.1f", moyenne))
             .append("/20.\n\n");

        if (difficulte > 0) {
            corps.append(difficulte).append(" apprenant(s) sont sous 10/20 : ");
            corps.append("ils ont besoin d'un accompagnement avant la prochaine tentative.\n");
        }
        if (reussitePremiere == 0 && valides > 0) {
            corps.append("Aucune validation n'a été obtenue dès la première tentative : ")
                 .append("la notion mérite d'être reprise en séance avant le test.\n");
        }

        List<UtilisateurVM> plusFaibles = triees.subList(0, Math.min(3, triees.size()));
        corps.append("\nPoints à reprendre en priorité : ");
        for (int i = 0; i < plusFaibles.size(); i++) {
            UtilisateurVM u = plusFaibles.get(i);
            if (i > 0) {
                corps.append(", ");
            }
            corps.append(u.getNom()).append(" (").append(u.getNote()).append("/20, ")
                 .append(u.getTentatives()).append(" tentative(s))");
        }
        corps.append(".\n\nCette synthèse est un brouillon : elle ne note personne et ne décide ")
             .append("d'aucune validation. À relire avant tout usage.");

        return new Brouillon("Synthèse du niveau — " + Session.langage().label(),
                corps.toString(), Statut.BROUILLON_IA, "résultats du niveau 3");
    }

    /**
     * Publication d'un brouillon. Sans confirmation humaine explicite, la réponse est
     * {@link MotifRefus#PUBLICATION_AUTOMATIQUE} : aucun chemin de code ne publie seul.
     *
     * @param confirmationHumaine vrai uniquement si l'enseignant a validé la boîte de dialogue
     */
    public static MotifRefus publier(Brouillon brouillon, boolean confirmationHumaine) {
        if (brouillon == null || brouillon.estRefus()) {
            return brouillon == null ? MotifRefus.PUBLICATION_AUTOMATIQUE : brouillon.motif();
        }
        return confirmationHumaine ? MotifRefus.AUCUN : MotifRefus.PUBLICATION_AUTOMATIQUE;
    }
}
