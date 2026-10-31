package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Config;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou sur la version affichée.
 *
 * <p>Constat réel : l'écran de connexion affichait {@code 0.1.0} alors que le pom était déjà en
 * {@code 0.1.3}. La version est désormais filtrée depuis le pom au build ; ce test interdit
 * toute dérive silencieuse entre les deux.</p>
 */
class VersionTest {

    @Test
    @DisplayName("La version affichée par l'application est celle du pom.xml")
    void versionAligneeSurLePom() throws IOException {
        String pom = Files.readString(Path.of("pom.xml"), StandardCharsets.UTF_8);
        Matcher m = Pattern.compile("<version>([0-9][^<]*)</version>").matcher(pom);
        assertTrue(m.find(), "Aucune balise <version> dans le pom");
        String versionPom = m.group(1).trim();

        assertEquals(versionPom, Config.VERSION,
                "La version affichée doit suivre le pom : mettre a jour <version> du pom "
                        + "et relancer le build (filtrage de version.properties)");
    }

    @Test
    @DisplayName("Le filtrage a bien eu lieu (pas de ${project.version} à l'écran)")
    void filtrageEffectue() {
        assertFalse(Config.VERSION.contains("${"), "Le fichier de version n'a pas été filtré : " + Config.VERSION);
        assertFalse(Config.VERSION.isBlank());
        assertTrue(Config.VERSION.matches("[0-9]+\\.[0-9]+\\.[0-9]+.*"), Config.VERSION);
    }
}
