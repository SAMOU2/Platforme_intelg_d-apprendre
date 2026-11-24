package fr.ihm2026.api.ia;

import java.util.List;

/**
 * Réponse de l'agent (étape 5 : sortie structurée).
 *
 * <p>Le champ {@code citations} contient les sections de cours réellement retrouvées dans le
 * corpus : une réponse sans citation n'est pas affichée. {@code refus} est une valeur de retour
 * ordinaire, ce qui évite de confondre un refus pédagogique avec une erreur technique.</p>
 */
public record ReponseIa(
        String reponse,
        List<Citation> citations,
        MotifRefus motif,
        String versionPrompt) {

    public ReponseIa {
        citations = List.copyOf(citations);
    }

    /** Section de cours citée : c'est elle que l'IHM transforme en lien vers le cours. */
    public record Citation(String section, String langage) {
    }

    public static ReponseIa refus(MotifRefus motif, String versionPrompt) {
        return new ReponseIa(motif.message(), List.of(), motif, versionPrompt);
    }

    /**
     * Refus accompagne de sa cause technique, affichee apres le message pedagogique.
     *
     * <p>« Assistance momentanement indisponible » ne permet aucune correction. La cause (session
     * expiree, quota, PC1 injoignable, modele absent, refus du fournisseur) est deja redigee pour
     * l'utilisateur par le service ; on l'ajoute telle quelle. Le texte reste un refus : il ne
     * note pas, ne valide pas et ne transmet aucun secret.</p>
     */
    public static ReponseIa refusDetaille(MotifRefus motif, String versionPrompt, String cause) {
        String detail = cause == null ? "" : cause.strip();
        if (detail.isEmpty() || motif.message().contains(detail)) {
            return refus(motif, versionPrompt);
        }
        return new ReponseIa(motif.message() + "\n\nCause : " + detail,
                List.of(), motif, versionPrompt);
    }

    public boolean estRefus() {
        return motif.estRefus();
    }

    public boolean estCitee() {
        return !estRefus() && !citations.isEmpty();
    }
}
