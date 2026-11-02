package fr.ihm2026.reseau;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MemoireAppareilConfianceTest {

    @Test
    @DisplayName("Le poste conserve un jeton par compte/rôle, sans e-mail ni mot de passe")
    void conserveEtOublieParCompteEtRole(@TempDir Path profil) throws Exception {
        String ancienProfil = System.getProperty("user.home");
        System.setProperty("user.home", profil.toString());
        try {
            String jeton = "a".repeat(43);
            Instant expiration = Instant.now().plus(2, ChronoUnit.DAYS);

            MemoireAppareilConfiance.ecrire("Samou@Example.org", Session.Role.ADMINISTRATEUR,
                    jeton, expiration);

            var lu = MemoireAppareilConfiance.lire(
                    "samoumegharba210@gmail.com", Session.Role.ADMINISTRATEUR);
            assertTrue(lu.isPresent(), () -> "Le jeton écrit dans "
                    + MemoireAppareilConfiance.dossier() + " doit pouvoir être relu. "
                    + "Fichiers présents : " + fichiersPresents());
            assertEquals(jeton, lu.get().jeton());
            assertEquals(expiration.toEpochMilli(), lu.get().expireLe().toEpochMilli());
            assertTrue(MemoireAppareilConfiance.lire(
                    "samoumegharba210@gmail.com", Session.Role.ENSEIGNANT).isEmpty(),
                    "Un autre rôle de poste ne doit pas réutiliser ce jeton.");

            try (var fichiers = Files.walk(MemoireAppareilConfiance.dossier())) {
                for (Path fichier : fichiers.filter(Files::isRegularFile).toList()) {
                    assertFalse(fichier.getFileName().toString().contains("Samou"));
                    String contenu = Files.readString(fichier);
                    assertFalse(contenu.contains("Samou@Example.org"));
                    assertFalse(contenu.contains("mot-de-passe"));
                }
            }

            MemoireAppareilConfiance.ecrire("Samou@Example.org", Session.Role.ADMINISTRATEUR,
                    "b".repeat(43), Instant.now().minus(1, ChronoUnit.SECONDS));
            assertEquals(jeton, MemoireAppareilConfiance.lire(
                    "Samou@Example.org", Session.Role.ADMINISTRATEUR).orElseThrow().jeton(),
                    "Un jeton déjà expiré ne doit pas remplacer l'entrée valide.");

            MemoireAppareilConfiance.oublier(
                    "Samou@Example.org", Session.Role.ADMINISTRATEUR);
            assertTrue(MemoireAppareilConfiance.lire(
                    "Samou@Example.org", Session.Role.ADMINISTRATEUR).isEmpty());
        } finally {
            if (ancienProfil == null) {
                System.clearProperty("user.home");
            } else {
                System.setProperty("user.home", ancienProfil);
            }
        }
    }

    private static String fichiersPresents() {
        Path dossier = MemoireAppareilConfiance.dossier();
        if (!Files.isDirectory(dossier)) {
            return "dossier absent";
        }
        try (var fichiers = Files.list(dossier)) {
            return fichiers.map(MemoireAppareilConfianceTest::decrireFichier).toList().toString();
        } catch (Exception ex) {
            return "liste impossible (" + ex.getClass().getSimpleName() + " : "
                    + ex.getMessage() + ")";
        }
    }

    private static String decrireFichier(Path fichier) {
        String description = fichier.getFileName() + " (régulier="
                + Files.isRegularFile(fichier);
        try {
            String contenu = Files.readString(fichier, StandardCharsets.UTF_8).strip();
            int separateur = contenu.indexOf('|');
            String expiration = separateur >= 0 && separateur + 1 <= contenu.length()
                    ? contenu.substring(separateur + 1)
                    : "";
            return description + ", taille=" + Files.size(fichier)
                    + ", contenu=" + contenu.length() + " caractères"
                    + ", séparateur=" + separateur
                    + ", expiration=" + expiration.length() + " caractères"
                    + ", expirationNumérique="
                    + (!expiration.isEmpty() && expiration.chars().allMatch(Character::isDigit))
                    + ")";
        } catch (Exception ex) {
            return description + ", lecture impossible=" + ex.getClass().getSimpleName()
                    + " : " + ex.getMessage() + ")";
        }
    }
}
