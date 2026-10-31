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

    public boolean estRefus() {
        return motif.estRefus();
    }

    public boolean estCitee() {
        return !estRefus() && !citations.isEmpty();
    }
}
