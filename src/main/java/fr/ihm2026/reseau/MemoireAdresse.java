package fr.ihm2026.reseau;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Retient l'adresse du serveur d'une fois sur l'autre (v0.13.2).
 *
 * <p>En salle, un poste se connecte toujours au même serveur. Redemander l'adresse à chaque
 * lancement est une corvée qui invite aux fautes de frappe : on garde donc la dernière adresse qui
 * a <b>fonctionné</b> dans un petit fichier texte du profil utilisateur
 * ({@code ~/.ihm2026/serveur.txt}), et on la repropose.</p>
 *
 * <p>Le fichier est facultatif : s'il est absent, illisible ou vide, on retombe sur l'adresse
 * fournie au lancement ({@code --serveur}). Aucune erreur d'entrée/sortie ne remonte à
 * l'utilisateur pour un simple confort.</p>
 */
public final class MemoireAdresse {

    private static final Path FICHIER =
            Path.of(System.getProperty("user.home", "."), ".ihm2026", "serveur.txt");

    private MemoireAdresse() {
    }

    /** Adresse enregistrée, ou {@code null} si aucune ne l'a encore été. */
    public static String lire() {
        try {
            if (!Files.isRegularFile(FICHIER)) {
                return null;
            }
            String texte = Files.readString(FICHIER, StandardCharsets.UTF_8).strip();
            return texte.isEmpty() ? null : texte;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /** Enregistre l'adresse qui vient de fonctionner (échec silencieux : c'est un confort). */
    public static void ecrire(String adresse) {
        if (adresse == null || adresse.isBlank()) {
            return;
        }
        try {
            Files.createDirectories(FICHIER.getParent());
            Files.writeString(FICHIER, adresse.strip() + System.lineSeparator(),
                    StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignore) {
            // Un profil en lecture seule ne doit pas empêcher de se connecter.
        }
    }

    /** Emplacement du fichier, pour la documentation et le dépannage. */
    public static Path fichier() {
        return FICHIER;
    }
}
