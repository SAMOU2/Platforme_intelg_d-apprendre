package fr.ihm2026.core;

/**
 * Style visuel de l'application (v0.12.0).
 *
 * <p>Deux styles cohabitent, et c'est volontaire : le style <b>macOS</b> reste celui par défaut, et
 * le style <b>rétro</b> est une couche — palette phosphore, boutons biseautés, machine à écrire,
 * balayage cathodique — que l'on active sans rien perdre. Un style n'est pas un thème : changer de
 * style change aussi les <b>animations</b> (une transition de 1995 ne se fait pas en fondu), alors
 * qu'un thème ne change que les couleurs.</p>
 *
 * <p>Sélection : {@code -Dihm.style=retro}, ou le bouton de la barre de titre à l'exécution.</p>
 */
public final class StyleVisuel {

    public static final String MACOS = "macos";
    public static final String RETRO = "retro";

    private static boolean retro =
            RETRO.equalsIgnoreCase(System.getProperty("ihm.style", MACOS));

    private StyleVisuel() {
    }

    public static boolean estRetro() {
        return retro;
    }

    public static void setRetro(boolean valeur) {
        retro = valeur;
    }

    /** Libellé affiché dans la barre de titre : le style courant doit être nommé. */
    public static String libelle() {
        return retro ? RETRO : MACOS;
    }
}
