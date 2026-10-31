package fr.ihm2026.api.ia;

import fr.ihm2026.core.Session;

/**
 * Contexte transmis au modèle.
 *
 * <p>Volontairement pauvre : un rang de niveau, un langage, la question et le code en cours.
 * <b>Aucune donnée personnelle</b> (ni nom, ni courriel, ni identifiant de compte) ne franchit
 * cette frontière — étape 1 du cadrage et paradigme de minimisation.</p>
 */
public record ContexteIa(int rangNiveau, Session.Langage langage, String question, String code) {

    public ContexteIa {
        question = question == null ? "" : question.strip();
        code = code == null ? "" : code;
    }
}
