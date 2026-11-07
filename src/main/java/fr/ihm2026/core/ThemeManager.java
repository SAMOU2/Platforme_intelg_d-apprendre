package fr.ihm2026.core;

import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import java.net.URL;
import javafx.application.Application;
import javafx.scene.Scene;

/**
 * Thème de l'application : AtlantaFX (clair / sombre) + nos feuilles CSS3.
 * Aucune couleur n'est écrite en Java (BNF-T4, IHM-JFX3).
 */
public final class ThemeManager {

    private ThemeManager() {
    }

    /**
     * Apparence courante. Elle peut être imposée au lancement par {@code -Dihm.theme=dark} (ou
     * {@code light}) : c'est ainsi que le smoketest produit les captures des deux palettes, et
     * l'utilisateur peut retrouver son réglage dans un raccourci.
     */
    private static boolean dark = "dark".equalsIgnoreCase(System.getProperty("ihm.theme", "light"));
    private static final String ECRAN_CONNEXION = ThemeManager.class.getName() + ".connexion";

    public static boolean isDark() {
        return dark;
    }

    public static void setDark(boolean value) {
        dark = value;
    }

    public static void toggle() {
        dark = !dark;
    }

    /**
     * Bascule l'apparence avec un fondu court (v0.11.0) : sans lui, la scène clignote le temps
     * que le système d'exploitation recharge ses feuilles de style. `-Dihm.animations=off` applique
     * le changement immédiatement.
     */
    public static void basculer(Scene scene, boolean sombre) {
        Animations.basculerApparence(scene.getRoot(), () -> {
            dark = sombre;
            apply(scene);
            fr.ihm2026.ui.commun.Notifications.afficher(scene.getWindow(),
                    fr.ihm2026.ui.commun.Notifications.Type.INFORMATION,
                    sombre ? "Thème sombre activé." : "Thème clair activé.");
        });
    }

    /**
     * Active ou désactive le style rétro (v0.12.0) : la classe {@code racine--retro} sur la racine
     * suffit, puisque toutes les surcharges de {@code retro.css} en découlent.
     */
    public static void appliquerStyle(javafx.scene.Parent racine) {
        boolean retro = StyleVisuel.estRetro();
        racine.getStyleClass().remove("racine--retro");
        if (retro) {
            racine.getStyleClass().add("racine--retro");
        }
    }

    /** Applique la préférence courante, sauf sur un écran de connexion fixé en sombre. */
    public static void apply(Scene scene) {
        boolean connexion = Boolean.TRUE.equals(scene.getProperties().get(ECRAN_CONNEXION));
        boolean paletteSombre = paletteSombre(dark, connexion);
        // Le style rétro utilise aussi la feuille d'agent sombre, indépendamment de la
        // préférence clair/sombre stockée, comme avant.
        boolean agentSombre = paletteSombre || StyleVisuel.estRetro();
        Application.setUserAgentStylesheet(
                (agentSombre ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
        scene.getStylesheets().setAll(
                css("/fr/ihm2026/css/app.css"),
                css(paletteSombre ? "/fr/ihm2026/css/theme-dark.css" : "/fr/ihm2026/css/theme-light.css"),
                css("/fr/ihm2026/css/retro.css"),
                css("/fr/ihm2026/css/syntax/syntax-java.css"),
                css("/fr/ihm2026/css/syntax/syntax-python.css"),
                css("/fr/ihm2026/css/syntax/syntax-cpp.css"));
    }

    /**
     * La connexion utilise toujours la palette sombre, sans changer la préférence de
     * l'utilisateur pour les écrans métier. L'état est lié à la scène afin qu'une bascule
     * d'apparence retardée par son animation respecte aussi l'écran actuellement affiché.
     */
    public static void appliquerPourEcran(Scene scene, boolean connexion) {
        scene.getProperties().put(ECRAN_CONNEXION, connexion);
        apply(scene);
    }

    static boolean paletteSombre(boolean preferenceSombre, boolean connexion) {
        return connexion || preferenceSombre;
    }

    private static String css(String path) {
        URL url = ThemeManager.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Feuille de style introuvable : " + path);
        }
        return url.toExternalForm();
    }
}
