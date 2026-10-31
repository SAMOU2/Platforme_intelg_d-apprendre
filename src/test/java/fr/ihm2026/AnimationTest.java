package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Animations;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javafx.util.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou des animations (v0.11.0).
 *
 * <p>Une animation d'interface a deux façons d'être mauvaise : trop longue (elle retarde le
 * travail) ou non désactivable (elle gêne les personnes sensibles au mouvement). Ces tests fixent
 * les deux bornes, et vérifient que chaque endroit qui anime est bien branché sur
 * {@link Animations} — de la même manière que l'inventaire des sorties réseau de l'étape 4 : un
 * contrat, pas une intention.</p>
 */
class AnimationTest {

    @AfterEach
    void retablir() {
        Animations.activer();
        Animations.terminerTout();
    }

    @Test
    @DisplayName("Aucune animation ne dépasse un quart de seconde")
    void dureesBornees() {
        List<Duration> durees = List.of(Animations.RAPIDE, Animations.STANDARD, Animations.AMPLE);
        for (Duration d : durees) {
            assertTrue(d.greaterThan(Duration.ZERO), "Une durée nulle n'anime rien : " + d);
            assertTrue(d.lessThanOrEqualTo(Duration.millis(250)),
                    "Animation trop longue (" + d.toMillis() + " ms) : au-delà de 250 ms, "
                            + "l'interface paraît lente");
        }
        assertTrue(Animations.RAPIDE.lessThan(Animations.STANDARD),
                "L'appui doit être plus court que le changement d'écran");
        assertTrue(Animations.STANDARD.lessThan(Animations.AMPLE),
                "L'ouverture de fenêtre peut être la plus ample, mais reste ≤ 250 ms");
    }

    @Test
    @DisplayName("-Dihm.animations=off : plus aucune animation n'est créée")
    void desactivationTotale() {
        assertTrue(Animations.actives(), "Les animations sont actives par défaut");
        Animations.desactiver();
        assertFalse(Animations.actives());
        // Les méthodes publiques doivent être sûres quand tout est coupé : aucun objet JavaFX
        // n'est créé, donc aucun accès hors du fil d'interface.
        Animations.changerEcran(null, null, true);
        Animations.apparaitreBulle(null);
        Animations.ouvrirFenetre(null);
        Animations.basculerApparence(null, () -> { });
        assertEquals(0, Animations.animationsEnCours());
    }

    @Test
    @DisplayName("terminerTout() ramène l'interface à son état final, sans exception")
    void terminerToutEstSur() {
        Animations.terminerTout();     // rien en cours : ne doit rien casser
        assertEquals(0, Animations.animationsEnCours());
        Animations.activer();
        Animations.terminerTout();
        assertEquals(0, Animations.animationsEnCours());
    }

    @Test
    @DisplayName("Chaque endroit qui anime est branché sur Animations, et le smoketest les termine")
    void branchementsVerifies() throws Exception {
        List<String> fichiers = List.of(
                "src/main/java/fr/ihm2026/core/Router.java",
                "src/main/java/fr/ihm2026/core/ThemeManager.java",
                "src/main/java/fr/ihm2026/ui/commun/BarreTitre.java",
                "src/main/java/fr/ihm2026/ui/ia/PanneauAssistant.java",
                "src/main/java/fr/ihm2026/App.java");
        for (String fichier : fichiers) {
            String source = Files.readString(Path.of(fichier), StandardCharsets.UTF_8);
            assertTrue(source.contains("Animations."),
                    fichier + " n'utilise pas Animations : l'animation attendue manque");
        }
        String smoke = Files.readString(Path.of("src/main/java/fr/ihm2026/core/SmokeTest.java"),
                StandardCharsets.UTF_8);
        assertTrue(smoke.contains("Animations.terminerTout()"),
                "Le smoketest doit terminer les animations avant de capturer l'écran");
    }

    @Test
    @DisplayName("La transition rétro est nerveuse : 160 ms, sans accélération")
    void transitionRetro() throws Exception {
        String source = Files.readString(Path.of("src/main/java/fr/ihm2026/core/Animations.java"),
                StandardCharsets.UTF_8);
        int debut = source.indexOf("public static void balayer");
        assertTrue(debut > 0, "Animations.balayer doit exister (transition old school)");
        String methode = source.substring(debut, source.indexOf("public static void apparaitreBulle"));
        assertTrue(methode.contains("Duration.millis(160)"),
                "Le balayage doit se terminer en 160 ms");
        assertFalse(methode.contains("Interpolator."),
                "Un changement de chaîne n'a pas de courbe d'accélération");
        assertTrue(source.contains("public static void suivre"),
                "Les animations construites ailleurs doivent pouvoir être suivies");
        assertTrue(source.contains("public static void aTerminer"),
                "Un état animé doit pouvoir être ramené à son état final");
        String retro = Files.readString(Path.of("src/main/java/fr/ihm2026/ui/commun/Retro.java"),
                StandardCharsets.UTF_8);
        assertTrue(retro.contains("Animations.suivre") && retro.contains("Animations.aTerminer"),
                "Le balayage, le rouet et la machine à écrire passent par Animations");
        assertTrue(retro.contains("ecrire(") && retro.contains("demarrerRouet("),
                "Machine à écrire et rouet de patience font partie du style rétro");
    }

    @Test
    @DisplayName("La bascule d'apparence passe par le fondu, jamais par une réapplication sèche")
    void basculeApparenceAnimee() throws Exception {
        String theme = Files.readString(Path.of("src/main/java/fr/ihm2026/core/ThemeManager.java"),
                StandardCharsets.UTF_8);
        assertTrue(theme.contains("basculer(Scene scene, boolean sombre)"),
                "ThemeManager doit exposer une bascule animée");
        assertTrue(theme.contains("Animations.basculerApparence"),
                "La bascule doit passer par Animations.basculerApparence");
        String barre = Files.readString(
                Path.of("src/main/java/fr/ihm2026/ui/commun/BarreTitre.java"),
                StandardCharsets.UTF_8);
        assertTrue(barre.contains("ThemeManager.basculer"),
                "Le bouton d'apparence doit utiliser la bascule animée");
    }
}
