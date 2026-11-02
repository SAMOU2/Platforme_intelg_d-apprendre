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

    /** Petits réessais contre les verrouillages transitoires Windows (antivirus/indexation). */
    private static final int ESSAIS_ECRITURE = 3;
    private static final int ESSAIS_LECTURE = 5;

    private MemoireAppareilConfiance() {
    }

    public static synchronized Optional<Confiance> lire(String email, Session.Role role) {
        if (email == null || email.isBlank() || role == null) {
            return Optional.empty();
        }
        Path fichier = fichierPour(email, role);
        for (int essai = 0; essai < ESSAIS_LECTURE; essai++) {
            try {
                // Ne pas dépendre uniquement de isRegularFile() sous Windows : ses attributs
                // peuvent être momentanément indéterminés ou classer un point de réanalyse
                // comme « autre » alors que le contenu reste lisible. Si l'existence est
                // incertaine, Files.readString ci-dessous tranchera et sera réessayé.
                if (Files.notExists(fichier) || Files.isDirectory(fichier)) {
                    return Optional.empty();
                }
                String contenu = Files.readString(fichier, StandardCharsets.UTF_8).strip();
                if (contenu.startsWith("\ufeff")) {
                    contenu = contenu.substring(1).strip();
                }
                int separateur = contenu.indexOf('|');
                if (separateur <= 0 || separateur != contenu.lastIndexOf('|')) {
                    supprimerSilencieusement(fichier);
                    return Optional.empty();
                }
                String jeton = contenu.substring(0, separateur).strip();
                String expiration = contenu.substring(separateur + 1).strip();
                if (!jetonValide(jeton)) {
                    supprimerSilencieusement(fichier);
                    return Optional.empty();
                }
                Instant expireLe;
                try {
                    expireLe = Instant.ofEpochMilli(Long.parseLong(expiration));
                } catch (NumberFormatException ex) {
                    supprimerSilencieusement(fichier);
                    return Optional.empty();
                }
                if (!expireLe.isAfter(Instant.now())) {
                    supprimerSilencieusement(fichier);
                    return Optional.empty();
                }
                return Optional.of(new Confiance(jeton, expireLe));
            } catch (IOException ex) {
                // Sous Windows, un antivirus ou l'indexation peut brièvement verrouiller un
                // fichier qui vient d'être écrit dans AppData\Local\Temp ou le profil.
                if (essai == ESSAIS_LECTURE - 1 || !pauseAvantNouvelEssai(essai)) {
                    return Optional.empty();
                }
            } catch (RuntimeException ex) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    public static synchronized void ecrire(String email, Session.Role role, String jeton,
            Instant expireLe) {
        if (email == null || email.isBlank() || role == null || !jetonValide(jeton)
                || expireLe == null || !expireLe.isAfter(Instant.now())) {
            return;
        }
        Path fichier = fichierPour(email, role);
        String contenu = jeton + "|" + expireLe.toEpochMilli() + System.lineSeparator();
        for (int essai = 0; essai < ESSAIS_ECRITURE; essai++) {
            try {
                Files.createDirectories(fichier.getParent());
                restreindreAcces(fichier.getParent(), true);
                // Écriture directe, comme MemoireAdresse. La relecture de contrôle ci-dessous
                // détecte une écriture Windows transitoirement incomplète ou verrouillée et
                // déclenche un nouvel essai au lieu de laisser silencieusement un fichier
                // inutilisable. Après le dernier échec, la session déjà ouverte continue :
                // seul le confort des 48 heures sera perdu et un code sera redemandé.
                Files.writeString(fichier, contenu, StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING,
                        StandardOpenOption.WRITE);
                restreindreAcces(fichier, false);
                var relu = lire(email, role);
                if (relu.isPresent() && relu.get().jeton().equals(jeton)
                        && relu.get().expireLe().toEpochMilli() == expireLe.toEpochMilli()) {
                    return;
                }
                if (essai == ESSAIS_ECRITURE - 1 || !pauseAvantNouvelEssai(essai)) {
                    return;
                }
            } catch (IOException | RuntimeException ex) {
                if (essai == ESSAIS_ECRITURE - 1 || !pauseAvantNouvelEssai(essai)) {
                    return;
                }
            }
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

    private static void supprimerSilencieusement(Path fichier) {
        try {
            Files.deleteIfExists(fichier);
        } catch (IOException | RuntimeException ignore) {
            // Un fichier corrompu mais momentanément verrouillé reste sans effet : il sera
            // rejeté à la prochaine lecture et ne donne aucun accès.
        }
    }

    private static boolean pauseAvantNouvelEssai(int essai) {
        try {
            Thread.sleep(10L * (essai + 1));
            return true;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
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
