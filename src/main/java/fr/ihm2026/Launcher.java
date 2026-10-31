package fr.ihm2026;

import java.util.Locale;

/**
 * Point de lancement « neutre » de l'application.
 *
 * <p>Pourquoi cette classe existe : quand la classe principale d'un programme Java
 * <em>étend</em> {@code javafx.application.Application}, le lanceur de la JVM exige que les
 * modules JavaFX soient présents sur le <strong>chemin de modules</strong> ({@code --module-path}).
 * Si JavaFX est simplement dans le <strong>classpath</strong> (cas d'un lancement depuis un IDE ou
 * depuis un fichier {@code argfile} généré par l'extension Java de VS Code), la JVM s'arrête avec :</p>
 *
 * <pre>Error: JavaFX runtime components are missing, and are required to run this application</pre>
 *
 * <p>Une classe de lancement qui n'étend pas {@code Application} contourne ce contrôle :
 * JavaFX s'initialise alors depuis le classpath. C'est la solution recommandée par OpenJFX pour
 * les applications non modulaires — et c'est précisément le choix de la v0.1 (pas de
 * {@code module-info.java}, voir README §4).</p>
 *
 * <p>Elle sert aussi de {@code mainClass} à {@code mvn javafx:run} : le plugin place JavaFX sur le
 * chemin de modules, le lancement fonctionne donc dans les deux modes.</p>
 */
public final class Launcher {

    private Launcher() {
    }

    public static void main(String[] args) {
        // Locale fixée avant tout appel à JavaFX : sinon un poste configuré en anglais affiche
        // les dates et les nombres au format américain (12/8/2026 au lieu du 08/12/2026).
        Locale.setDefault(Locale.FRANCE);

        // v0.13.0 : les fichiers de lancement des postes passent par des arguments
        // (--role=administrateur --poste=PC1 --serveur=192.168.1.10) plutôt que par des options
        // de JVM, parce que `mvn javafx:run` relance une seconde JVM et ne transmet pas -D.
        // On les traduit ici en propriétés système, avant toute lecture de Config.
        appliquerArguments(args);

        // Poste serveur (PC1) : aucun écran à ouvrir, le processus écoute et journalise.
        if (fr.ihm2026.reseau.IdentitePoste.estServeur()) {
            fr.ihm2026.reseau.ServeurLocal.lancerEnConsole();
            return;
        }
        App.main(args);
    }

    /** Traduit {@code --cle=valeur} en propriété système {@code ihm.cle}. */
    static void appliquerArguments(String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--") || arg.indexOf('=') < 0) {
                continue;
            }
            String cle = arg.substring(2, arg.indexOf('=')).strip().toLowerCase();
            String valeur = arg.substring(arg.indexOf('=') + 1).strip();
            if (ListeAutorisee.contains(cle)) {
                System.setProperty("ihm." + cle, valeur);
            }
        }
    }

    /** Les seules options acceptées en ligne de commande : rien d'autre n'entre par ici. */
    private static final java.util.Set<String> ListeAutorisee =
            java.util.Set.of("role", "poste", "serveur", "port", "style", "theme", "smoketest",
                    "animations", "captures", "modele");
}
