package fr.ihm2026.reseau;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Lecture des SSID uniquement. Aucun profil ou secret Wi-Fi n'est lu ni écrit. */
public final class WifiWindows {
    private WifiWindows() { }
    public record Etat(List<String> disponibles, Set<String> connectes) { }
    public static boolean disponible() {
        return System.getProperty("os.name", "").startsWith("Windows");
    }
    public static Etat lire() throws IOException {
        if (!disponible()) throw new IOException("Fonction Wi-Fi réservée à Windows.");
        String sortie = executer("$OutputEncoding = [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new(); "
                + "netsh wlan show networks mode=ssid; if ($LASTEXITCODE -ne 0) { exit 1 }; "
                + "Write-Output '---IHM-INTERFACES---'; netsh wlan show interfaces; exit $LASTEXITCODE");
        String[] parties = sortie.split("---IHM-INTERFACES---", 2);
        if (parties.length != 2) throw new IOException("Lecture Wi-Fi Windows impossible.");
        return new Etat(ssids(parties[0], true), new LinkedHashSet<>(ssids(parties[1], false)));
    }
    static List<String> ssids(String texte, boolean numerotes) {
        Pattern motif = Pattern.compile(numerotes ? "^\\s*SSID\\s+\\d+\\s*:\\s?(.*)$" : "^\\s*SSID\\s*:\\s?(.*)$");
        Set<String> noms = new LinkedHashSet<>();
        for (String ligne : texte.split("\\R")) {
            var match = motif.matcher(ligne);
            if (match.matches()) {
                String nom = match.group(1).strip();
                if (!nom.isEmpty() && nom.chars().noneMatch(Character::isISOControl)) noms.add(nom);
            }
        }
        return List.copyOf(noms);
    }
    public static void ouvrirParametres() throws IOException {
        if (!disponible()) throw new IOException("Fonction réservée à Windows.");
        executer("Start-Process 'ms-settings:network-wifi'");
    }
    private static String executer(String script) throws IOException {
        // Script constant : un SSID n'est jamais injecté dans une commande.
        var fichier = Files.createTempFile("ihm-wifi-", ".txt");
        Process processus = null;
        try {
            processus = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", script)
                    .redirectErrorStream(true).redirectOutput(fichier.toFile()).start();
            if (!processus.waitFor(12, TimeUnit.SECONDS)) {
                processus.destroyForcibly();
                processus.waitFor(2, TimeUnit.SECONDS);
                throw new IOException("Windows ne répond pas à la demande Wi-Fi.");
            }
            if (processus.exitValue() != 0) throw new IOException(
                    "Wi-Fi indisponible : vérifiez la carte Wi-Fi, le service WLAN et les autorisations de localisation Windows.");
            if (Files.size(fichier) > 1_000_000) throw new IOException("Réponse Windows trop volumineuse.");
            return Files.readString(fichier, StandardCharsets.UTF_8).replace("\uFEFF", "");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (processus != null) processus.destroyForcibly();
            throw new IOException("Lecture Wi-Fi interrompue.", ex);
        } finally { Files.deleteIfExists(fichier); }
    }
}
