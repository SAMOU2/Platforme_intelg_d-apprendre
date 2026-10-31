package fr.ihm2026.core.i18n;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * Accès centralisé aux libellés (BNF-11, IHM-11).
 *
 * <p><strong>Deux précautions apprises à la dure</strong> (voir README §5) :</p>
 * <ol>
 *   <li>Le nom de base est {@code fr.ihm2026.i18n.messages} : il exige un fichier
 *       <em>racine</em> {@code messages.properties}. Un fichier uniquement suffixé
 *       ({@code messages_fr.properties}) ne suffit pas : sur un poste configuré en
 *       {@code en_US}, la JVM cherche {@code messages_en_US}, puis {@code messages_en},
 *       puis le fichier racine — jamais {@code messages_fr}. D'où l'ajout du fichier racine.</li>
 *   <li>La locale est <em>fixée</em> au français : l'application est monolingue, mais elle ne doit
 *       pas dépendre de la langue du système d'exploitation pour s'afficher.</li>
 * </ol>
 *
 * <p>Si le paquet de ressources est absent, l'application <strong>ne s'arrête pas</strong> :
 * les libellés s'affichent sous la forme {@code «clé»}, ce qui rend le défaut visible sans
 * empêcher la démonstration.</p>
 */
public final class UiText {

    private static final String BASE = "fr.ihm2026.i18n.messages";
    private static final Locale LOCALE = Locale.FRENCH;
    private static final ResourceBundle BUNDLE = charger();

    private UiText() {
    }

    private static ResourceBundle charger() {
        try {
            return ResourceBundle.getBundle(BASE, LOCALE, UiText.class.getClassLoader());
        } catch (MissingResourceException e) {
            // Ressource absente : on continue avec des libellés marqués «clé».
            return null;
        }
    }

    /** Vrai si le fichier de libellés a bien été trouvé (utilisé par les tests). */
    public static boolean disponible() {
        return BUNDLE != null;
    }

    public static String get(String key) {
        if (BUNDLE == null) {
            return "«" + key + "»";
        }
        try {
            return BUNDLE.getString(key);
        } catch (MissingResourceException e) {
            return "«" + key + "»";
        }
    }

    /**
     * Libellé paramétré. Les apostrophes sont échappées automatiquement : {@link MessageFormat}
     * les interprète sinon comme des délimiteurs, ce qui affichait « l''exercice » à l'écran.
     * Les fichiers de libellés s'écrivent donc naturellement, sans doublement manuel.
     */
    public static String get(String key, Object... args) {
        return MessageFormat.format(get(key).replace("'", "''"), args);
    }
}
