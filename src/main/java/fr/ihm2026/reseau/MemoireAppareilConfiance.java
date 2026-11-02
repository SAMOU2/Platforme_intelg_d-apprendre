package fr.ihm2026.reseau;

import fr.ihm2026.core.Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;

/**
 * Conserve, dans le profil de l'utilisateur Windows, le jeton opaque d'un poste
 * de confiance. Le mot de passe n'est jamais enregistré : il reste demandé à
 * chaque ouverture. Ce jeton permet seulement au serveur d'éviter un nouveau
 * code e-mail pendant la fenêtre de 48 heures accordée après le code validé.
 *
 * <p>Chaque couple compte/rôle de poste possède son propre fichier sous
 * {@code ~/.ihm2026/appareils-confiance/}. Ainsi, plusieurs fenêtres de rôles
 * différents sur le même PC ne s'écrasent pas mutuellement.</p>
 */
public final class MemoireAppareilConfiance {

    private MemoireAppareilConfiance() {
    }

    public static synchronized Optional<Confiance> lire(String email, Session.Role role) {
        if (email == null || email.isBlank() || role == null) {
            return Optional.empty();
        }
        Path fichier = fichierPour(email, role);
        try {
            if (!Files.isRegularFile(fichier)) {
                return Optional.empty();
            }
            String[] champs = Files.readString(fichier, StandardCharsets.UTF_8).strip().split("\\|", -1);
            if (champs.length != 2 || !jetonValide(champs[0])) {
                Files.deleteIfExists(fichier);
                return Optional.empty();
            }
            Instant expireLe = Instant.ofEpochMilli(Long.parseLong(champs[1]));
            if (!expireLe.isAfter(Instant.now())) {
                Files.deleteIfExists(fichier);
                return Optional.empty();
            }
            return Optional.of(new Confiance(champs[0], expireLe));
        } catch (IOException | RuntimeException ex) {
            return Optional.empty();
        }
    }

    public static synchronized void ecrire(String email, Session.Role role, String jeton,
            Instant expireLe) {
        if (email == null || email.isBlank() || role == null || !jetonValide(jeton)
                || expireLe == null || !expireLe.isAfter(Instant.now())) {
            return;
        }
        Path fichier = fichierPour(email, role);
        try {
            Files.createDirectories(fichier.getParent());
            restreindreAcces(fichier.getParent(), true);
            // Écriture directe, comme MemoireAdresse : sous Windows, le fichier temporaire
            // suivi d'un déplacement s'est révélé fragile dans le test d'intégration local.
            // Une écriture interrompue produit au pire un fichier rejeté à la lecture, donc
            // un nouveau code e-mail demandé.
            Files.writeString(fichier, jeton + "|" + expireLe.toEpochMilli()
                    + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                    StandardOpenOption.WRITE);
            restreindreAcces(fichier, false);
        } catch (IOException | RuntimeException ignore) {
            // La mémorisation est un confort : son échec ne doit pas bloquer la session déjà ouverte.
        }
    }

    public static synchronized void oublier(String email, Session.Role role) {
        if (email == null || email.isBlank() || role == null) {
            return;
        }
        try {
            Files.deleteIfExists(fichierPour(email, role));
        } catch (IOException | RuntimeException ignore) {
            // Le jeton peut aussi être révoqué côté serveur par l'appelant.
        }
    }

    /** Dossier des jetons de confiance, utile au dépannage sans révéler leur valeur. */
    public static Path dossier() {
        return Path.of(System.getProperty("user.home", "."), ".ihm2026", "appareils-confiance");
    }

    private static Path fichierPour(String email, Session.Role role) {
        return dossier().resolve(cle(email, role) + ".txt");
    }

    private static String cle(String email, Session.Role role) {
        String brut = email.strip().toLowerCase(Locale.ROOT) + "|" + role.name();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(brut.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 est indisponible.", ex);
        }
    }

    private static boolean jetonValide(String jeton) {
        return jeton != null && jeton.length() >= 32 && jeton.length() <= 128
                && jeton.chars().allMatch(caractere -> caractere == '-' || caractere == '_'
                        || (caractere >= 'A' && caractere <= 'Z')
                        || (caractere >= 'a' && caractere <= 'z')
                        || (caractere >= '0' && caractere <= '9'));
    }

    private static void restreindreAcces(Path chemin, boolean dossier) {
        try {
            var vue = Files.getFileAttributeView(chemin,
                    java.nio.file.attribute.PosixFileAttributeView.class);
            if (vue != null) {
                vue.setPermissions(PosixFilePermissions.fromString(
                        dossier ? "rwx------" : "rw-------"));
            }
            // Sur Windows, Files.getFileAttributeView(Posix) retourne null. On conserve alors
            // volontairement les ACL héritées du profil utilisateur. Les méthodes historiques
            // java.io.File n'y offrent pas d'équivalent POSIX fiable pour ce besoin (lecture et
            // exécution sont essentiellement sans effet, l'écriture bascule l'attribut DOS
            // lecture-seule) ; elles n'apportaient donc pas la confidentialité recherchée.
        } catch (IOException | RuntimeException ignore) {
            // Le durcissement POSIX est opportuniste ; il ne doit jamais empêcher l'écriture.
        }
    }

    public record Confiance(String jeton, Instant expireLe) {
    }
}
