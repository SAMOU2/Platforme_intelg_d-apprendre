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

    /** Applique le thème courant à la scène (ordre : AtlantaFX, app.css, surcharge de thème). */
    public static void apply(Scene scene) {
        // Le style rétro est un thème sombre : il doit aussi recevoir la feuille d'agent sombre,
        // sinon les commandes que nous ne redéfinissons pas (en-têtes d'onglets, barres de
        // défilement, zones de saisie) gardent les gris clairs de PrimerLight et font des taches
        // blanches sur le fond phosphore.
        boolean sombre = dark || StyleVisuel.estRetro();
        Application.setUserAgentStylesheet(
                (sombre ? new PrimerDark() : new PrimerLight()).getUserAgentStylesheet());
        scene.getStylesheets().setAll(
                css("/fr/ihm2026/css/app.css"),
                css(dark ? "/fr/ihm2026/css/theme-dark.css" : "/fr/ihm2026/css/theme-light.css"),
                css("/fr/ihm2026/css/retro.css"),
                css("/fr/ihm2026/css/syntax/syntax-java.css"),
                css("/fr/ihm2026/css/syntax/syntax-python.css"),
                css("/fr/ihm2026/css/syntax/syntax-cpp.css"));
    }

    private static String css(String path) {
        URL url = ThemeManager.class.getResource(path);
        if (url == null) {
            throw new IllegalStateException("Feuille de style introuvable : " + path);
        }
        return url.toExternalForm();
    }
}
