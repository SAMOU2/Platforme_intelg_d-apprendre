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
import java.util.Locale;
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
            // Une seule source pour l'adresse du compte : la forme relue est dérivée de la forme
            // écrite. Cela teste la normalisation en minuscules sans risquer deux adresses
            // presque identiques qui produiraient deux fichiers hachés différents.
            String emailCompte = "Samou@Example.org";
            String emailMinuscules = emailCompte.toLowerCase(Locale.ROOT);
            String partieLocale = emailCompte.substring(0, emailCompte.indexOf('@'));
            String jeton = "a".repeat(43);
            Instant expiration = Instant.now().plus(2, ChronoUnit.DAYS);

            MemoireAppareilConfiance.ecrire(emailCompte, Session.Role.ADMINISTRATEUR,
                    jeton, expiration);

            var lu = MemoireAppareilConfiance.lire(
                    emailMinuscules, Session.Role.ADMINISTRATEUR);
            assertTrue(lu.isPresent(), () -> "Le jeton écrit dans "
                    + MemoireAppareilConfiance.dossier() + " doit pouvoir être relu. "
                    + "Diagnostic production : " + MemoireAppareilConfiance.lireAvecDiagnostic(
                            emailMinuscules, Session.Role.ADMINISTRATEUR).diagnostic()
                    + ". Fichiers présents : " + fichiersPresents());
            assertEquals(jeton, lu.get().jeton());
            assertEquals(expiration.toEpochMilli(), lu.get().expireLe().toEpochMilli());
            assertTrue(MemoireAppareilConfiance.lire(
                    emailMinuscules, Session.Role.ENSEIGNANT).isEmpty(),
                    "Un autre rôle de poste ne doit pas réutiliser ce jeton.");

            try (var fichiers = Files.walk(MemoireAppareilConfiance.dossier())) {
                for (Path fichier : fichiers.filter(Files::isRegularFile).toList()) {
                    assertFalse(fichier.getFileName().toString().contains(partieLocale));
                    String contenu = Files.readString(fichier);
                    assertFalse(contenu.contains(emailCompte));
                    assertFalse(contenu.contains("mot-de-passe"));
                }
            }

            MemoireAppareilConfiance.ecrire(emailCompte, Session.Role.ADMINISTRATEUR,
                    "b".repeat(43), Instant.now().minus(1, ChronoUnit.SECONDS));
            assertEquals(jeton, MemoireAppareilConfiance.lire(
                    emailCompte, Session.Role.ADMINISTRATEUR).orElseThrow().jeton(),
                    "Un jeton déjà expiré ne doit pas remplacer l'entrée valide.");

            MemoireAppareilConfiance.oublier(
                    emailCompte, Session.Role.ADMINISTRATEUR);
            assertTrue(MemoireAppareilConfiance.lire(
                    emailMinuscules, Session.Role.ADMINISTRATEUR).isEmpty());
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
