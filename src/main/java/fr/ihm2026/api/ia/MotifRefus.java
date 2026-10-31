package fr.ihm2026.api.ia;

/**
 * Motifs de refus de l'agent — étape 1 du développement (contrat d'usage).
 *
 * <p>Un refus est une réponse de première classe, pas une exception : l'interface l'affiche
 * comme une orientation pédagogique, jamais comme une panne (étape 5, format de sortie).</p>
 */
public enum MotifRefus {

    /** Réponse fournie normalement. */
    AUCUN(""),
    /** La question sort du domaine enseigné (Java, Python, C++ du parcours). */
    HORS_PERIMETRE("Cette question sort du cadre du cours : je ne peux aider que sur "
            + "les notions Java, Python et C++ du parcours."),
    /** L'apprenant demande la solution complète au lieu d'une piste. */
    SOLUTION_DEMANDEE("Je ne donne pas la solution complète : ce serait t'enlever l'exercice. "
            + "Voici une piste et la section du cours à relire."),
    /** Demande de note, de validation ou de déblocage : interdit par le projet (RG-13). */
    DECISION_DEMANDEE("Je ne note pas, je ne valide pas et je ne débloque pas de niveau : "
            + "seul l'enseignant en décide. Je peux t'expliquer la notion, en revanche."),
    /** Évaluation en cours : l'assistance est fermée pendant un test (RG-13). */
    TEST_EN_COURS("L'assistance est fermée pendant un test de validation. "
            + "Tu pourras la retrouver après avoir soumis ton travail."),
    /**
     * Positionnement B : demande de publication sans relecture humaine.
     * L'agent ne publie jamais — il produit un brouillon.
     */
    PUBLICATION_AUTOMATIQUE("L'agent ne publie pas : il prépare un brouillon que vous relisez "
            + "et publiez vous-même. Enseignant, vous restez l'auteur du contenu."),
    /**
     * Positionnement B : trop peu de résultats pour produire une synthèse de groupe
     * (risque de généralisation abusive et de réidentification).
     */
    DONNEES_INSUFFISANTES("Trop peu de résultats pour une synthèse de groupe : trois résultats "
            + "au minimum sont nécessaires. Utilisez le tableau individuel en attendant."),
    /** Quota journalier d'appels atteint. */
    QUOTA("Le quota quotidien d'assistance est atteint. Le cours et l'exercice restent "
            + "accessibles normalement."),
    /** Service d'IA indisponible : l'application continue sans lui. */
    INDISPONIBLE("L'assistance est momentanément indisponible. Tu peux continuer "
            + "l'exercice : rien n'est bloqué.");

    private final String message;

    MotifRefus(String message) {
        this.message = message;
    }

    /**
     * Libellé affiché à l'apprenant, lu dans le contrat de refus ({@link ContratRefus}).
     * Les textes ci-dessus ne servent que de repli si le fichier de contrat est absent.
     */
    public String message() {
        return ContratRefus.message(this);
    }

    /** Repli utilisé uniquement si le contrat est illisible (ne pas appeler directement). */
    public String messageParDefaut() {
        return message;
    }

    public boolean estRefus() {
        return this != AUCUN;
    }
}
