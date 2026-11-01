package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou de la direction visuelle « macOS » (v0.9.0).
 *
 * <p>La décision d'interface est simple à énoncer : <b>les composants ne citent que des jetons de
 * couleur, les palettes sont dans les deux fichiers de thème</b>. Elle est facile à trahir — une
 * couleur écrite en dur dans un composant ne se voit pas en thème clair et devient illisible en
 * thème sombre. Ces tests la rendent exécutable :</p>
 * <ul>
 *   <li>toute couleur utilisée par {@code app.css} est déclarée comme jeton dans le fichier ;</li>
 *   <li>le thème clair et le thème sombre déclarent <b>exactement</b> les mêmes jetons — sinon une
 *       bascule d'apparence laisserait un texte sans couleur ;</li>
 *   <li>aucune couleur opaque en dur dans {@code app.css} hors liste blanche assumée (dégradé de
 *       connexion, coloration syntaxique), et ces exceptions ont leur surcharge sombre.</li>
 * </ul>
 */
class StyleTest {

    private static final String APP = "/fr/ihm2026/css/app.css";
    private static final String CLAIR = "/fr/ihm2026/css/theme-light.css";
    private static final String SOMBRE = "/fr/ihm2026/css/theme-dark.css";
    private static final Pattern JETON = Pattern.compile("-color-[a-z0-9-]+");
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{3,8}\\b");
    private static final Pattern DECLARATION = Pattern.compile("(-color-[a-z0-9-]+)\\s*:");

    private static String lire(String chemin) throws Exception {
        try (InputStream in = StyleTest.class.getResourceAsStream(chemin)) {
            assertNotNull(in, "Feuille de style absente du classpath : " + chemin);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static Set<String> declarees(String css) {
        Set<String> jetons = new LinkedHashSet<>();
        Matcher m = DECLARATION.matcher(css);
        while (m.find()) {
            jetons.add(m.group(1));
        }
        return jetons;
    }

    private static Set<String> utilisees(String css) {
        Set<String> jetons = new TreeSet<>();
        Matcher m = JETON.matcher(css);
        while (m.find()) {
            jetons.add(m.group());
        }
        return jetons;
    }

    @Test
    @DisplayName("Tous les jetons utilisés par les composants sont déclarés dans app.css")
    void jetonsTousDeclares() throws Exception {
        String app = lire(APP);
        // Les jetons sont déclarés dans les deux palettes ; app.css ne fait que les citer.
        Set<String> definis = new LinkedHashSet<>(declarees(lire(CLAIR)));
        definis.addAll(declarees(lire(SOMBRE)));
        definis.addAll(declarees(app));
        Set<String> utilises = utilisees(app);
        utilises.removeAll(definis);
        assertTrue(utilises.isEmpty(),
                "Jetons utilisés mais jamais déclarés : " + utilises);
        assertTrue(definis.size() >= 15,
                "Palette trop pauvre pour une direction visuelle : " + definis.size() + " jetons");
        // Les jetons de la direction macOS doivent exister : accent système, surfaces, fenêtre.
        for (String jeton : List.of("-color-accent", "-color-fenetre", "-color-surface",
                "-color-surface-2", "-color-surface-3", "-color-texte", "-color-texte-2")) {
            assertTrue(definis.contains(jeton), "Jeton de la direction visuelle manquant : " + jeton);
        }
    }

    @Test
    @DisplayName("Thème clair et thème sombre déclarent exactement les mêmes jetons")
    void themesSymetriques() throws Exception {
        Set<String> clair = new TreeSet<>(declarees(lire(CLAIR)));
        Set<String> sombre = new TreeSet<>(declarees(lire(SOMBRE)));
        Set<String> manquantsEnSombre = new TreeSet<>(clair);
        manquantsEnSombre.removeAll(sombre);
        Set<String> manquantsEnClair = new TreeSet<>(sombre);
        manquantsEnClair.removeAll(clair);
        assertTrue(manquantsEnSombre.isEmpty(),
                "Jetons du thème clair absents du thème sombre : " + manquantsEnSombre);
        assertTrue(manquantsEnClair.isEmpty(),
                "Jetons du thème sombre absents du thème clair : " + manquantsEnClair);
        assertEquals(clair, sombre);
        assertTrue(clair.size() >= 12, "Trop peu de jetons surchargés : " + clair.size());
    }

    @Test
    @DisplayName("Pas de couleur opaque en dur dans les composants, hors exceptions assumées")
    void pasDeCouleurEnDur() throws Exception {
        // Exceptions : le dégradé de la fenêtre de connexion et la coloration syntaxique, qui ont
        // leur propre surcharge dans theme-dark.css. Tout le reste doit passer par un jeton.
        Set<String> autorisees = Set.of(
                "#3a5fcd", "#5e5ce6", "#a06ad4", "#e26f8c",              // fond de connexion
                "#ff5f57", "#febc2e", "#28c840",                          // pastilles macOS
                "#1a8bff",                                                // survol du bouton principal
                "#a1328f", "#c1481c",                                     // coloration syntaxique
                "#f5f5f7");
        String app = lire(APP);
        Matcher m = HEX.matcher(app);
        Set<String> fautives = new TreeSet<>();
        while (m.find()) {
            String couleur = m.group().toLowerCase();
            if (!autorisees.contains(couleur)) {
                fautives.add(couleur);
            }
        }
        assertTrue(fautives.isEmpty(),
                "Couleurs en dur dans app.css : " + fautives
                        + " — elles doivent devenir des jetons dans les fichiers de thème");
    }

    @Test
    @DisplayName("Notifications et actions de fenêtre utilisent les palettes clair et sombre")
    void notificationsEtActionsFenetreUtilisentLesJetons() throws Exception {
        String app = lire(APP);
        assertTrue(app.contains("-fx-text-fill: -color-texte-feu;"));
        assertTrue(app.contains("-fx-background-color: -color-notification-fond;"));
        assertTrue(app.contains(".notification .label { -fx-text-fill: -color-notification-texte; }"));
        for (String theme : List.of(CLAIR, SOMBRE)) {
            Set<String> jetons = declarees(lire(theme));
            assertTrue(jetons.containsAll(Set.of("-color-texte-feu",
                    "-color-notification-fond", "-color-notification-texte")),
                    "Palette notifications/fenêtre incomplète : " + theme);
        }
    }

    @Test
    @DisplayName("Les signes distinctifs de la direction macOS sont présents")
    void signaturesMacOs() throws Exception {
        String app = lire(APP);
        for (String classe : List.of(".calque-fenetre", ".fenetre", ".barre-titre", ".feux",
                ".feu-rouge", ".feu-jaune", ".feu-vert", ".bouton-nav", ".titre-fenetre",
                ".barre-laterale", ".entete-section", ".ligne-laterale", ".avatar",
                ".btn-primary", ".card", ".panel-ia", ".ia-utilisateur", ".ia-reponse",
                ".scroll-bar .thumb", ".tab-pane")) {
            assertTrue(app.contains(classe), "Classe attendue absente de app.css : " + classe);
        }
        assertTrue(app.contains("-fx-background-radius: 12")
                        || app.contains("-fx-background-radius: 10"),
                "Les surfaces doivent être arrondies (cartes, panneaux)");
        assertTrue(app.contains("dropshadow"), "Les surfaces doivent porter une ombre douce");
        assertFalse(app.contains("-fx-background-color: #007aff"),
                "L'accent doit passer par le jeton -color-accent, pas par sa valeur");
    }

    @Test
    @DisplayName("Le style rétro surcharge les mêmes jetons, et rien que des jetons connus")
    void styleRetroCoherent() throws Exception {
        String retro = lire("/fr/ihm2026/css/retro.css");
        assertTrue(retro.contains(".racine--retro"),
                "Toutes les surcharges rétro doivent descendre de la classe racine--retro");
        Set<String> declares = declarees(retro);
        assertTrue(declares.size() >= 15,
                "Le style rétro doit redéfinir la palette, pas seulement la police : " + declares);
        Set<String> connus = new TreeSet<>(declarees(lire(CLAIR)));
        connus.addAll(declarees(lire(SOMBRE)));
        Set<String> inconnus = new TreeSet<>(declares);
        inconnus.removeAll(connus);
        assertTrue(inconnus.isEmpty(),
                "Jetons inventés par retro.css : " + inconnus + " — la palette reste la même liste");
        assertTrue(retro.contains("JetBrains Mono"), "Le style rétro s'écrit en monospace");
        assertFalse(retro.contains("dropshadow"),
                "Un style old school n'a pas d'ombres portées");
        // Et le style se pilote depuis le code, pas depuis un fichier CSS oublié.
        String theme = Files.readString(Path.of("src/main/java/fr/ihm2026/core/ThemeManager.java"),
                StandardCharsets.UTF_8);
        assertTrue(theme.contains("racine--retro") && theme.contains("appliquerStyle"),
                "ThemeManager doit appliquer la classe racine--retro");
        String app = Files.readString(Path.of("src/main/java/fr/ihm2026/App.java"),
                StandardCharsets.UTF_8);
        assertTrue(app.contains("Retro.installerBalayage") && app.contains("Retro.allumage"),
                "L'application doit poser le balayage cathodique et l'allumage en style rétro");
    }

    @Test
    @DisplayName("Les polices de l'interface sont embarquées, donc identiques sous Windows")
    void policesEmbarquees() throws Exception {
        for (String police : List.of("/fr/ihm2026/fonts/Inter.ttf",
                "/fr/ihm2026/fonts/JetBrainsMono-Regular.ttf",
                "/fr/ihm2026/fonts/JetBrainsMono-Bold.ttf")) {
            try (InputStream in = StyleTest.class.getResourceAsStream(police)) {
                assertNotNull(in, "Police absente du classpath : " + police);
                assertTrue(in.readNBytes(4).length == 4, "Police illisible : " + police);
            }
        }
        String app = lire(APP);
        assertTrue(app.contains("\"Inter\"") || app.contains("-fx-font-family: \"Inter\""),
                "La police d'interface doit être Inter (repli libre de SF Pro)");
        assertTrue(app.contains("JetBrains Mono"),
                "L'éditeur et les citations de code doivent utiliser JetBrains Mono");
        // Et aucune police propriétaire Apple dans les déclarations : elle n'existe pas sous Windows.
        assertFalse(app.contains("-fx-font-family: \"SF Pro Text\""),
                "SF Pro ne peut pas être la police principale : elle est absente sous Windows");
    }

    @Test
    @DisplayName("Les feuilles de style du thème et de la syntaxe existent et se chargent")
    void feuillesPresentes() throws Exception {
        for (String chemin : List.of(APP, CLAIR, SOMBRE,
                "/fr/ihm2026/css/syntax/syntax-java.css",
                "/fr/ihm2026/css/syntax/syntax-python.css",
                "/fr/ihm2026/css/syntax/syntax-cpp.css")) {
            assertFalse(lire(chemin).isBlank(), "Feuille vide : " + chemin);
        }
    }
}
