package fr.ihm2026.reseau;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Découverte indicative uniquement : l'identité reste vérifiée par HTTPS. */
public final class DecouverteServeurs {
    private DecouverteServeurs() { }

    public record Serveur(String nom, String hote) {
        @Override public String toString() { return nom; }
    }

    public static String nomLocal() {
        String nom = System.getProperty("ihm.serveur.nom",
                System.getenv().getOrDefault("IHM_SERVEUR_NOM", "Plateforme Samou"));
        nom = nom.replaceAll("[\\p{Cntrl}]", " ").strip();
        return nom.isEmpty() ? "Plateforme Samou" : nom.substring(0, Math.min(80, nom.length()));
    }

    /** Collecte pendant deux secondes ; inclut le même PC et les interfaces LAN actives. */
    public static List<Serveur> chercher(int port) throws IOException {
        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port invalide");
        Map<String, Serveur> trouves = new LinkedHashMap<>();
        Set<InetAddress> destinations = new LinkedHashSet<>();
        destinations.add(InetAddress.getByName("127.0.0.1"));
        destinations.add(InetAddress.getByName("255.255.255.255"));
        for (NetworkInterface carte : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!carte.isUp() || carte.isLoopback()) continue;
            for (InterfaceAddress adresse : carte.getInterfaceAddresses()) {
                if (adresse.getBroadcast() != null) destinations.add(adresse.getBroadcast());
            }
        }
        byte[] demande = "IHM2026:DISCOVER:2".getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (InetAddress destination : destinations) {
                try { socket.send(new DatagramPacket(demande, demande.length, destination, port)); }
                catch (IOException ignore) { /* Une interface inaccessible n'empêche pas les autres. */ }
            }
            long fin = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < fin) {
                socket.setSoTimeout(Math.max(1, (int) ((fin - System.nanoTime()) / 1_000_000)));
                DatagramPacket paquet = new DatagramPacket(new byte[512], 512);
                try { socket.receive(paquet); }
                catch (SocketTimeoutException termine) { break; }
                if (paquet.getPort() != port) continue;
                String texte = new String(paquet.getData(), paquet.getOffset(), paquet.getLength(), StandardCharsets.UTF_8);
                Serveur serveur = lireAnnonce(texte, paquet.getAddress().getHostAddress());
                if (serveur != null) {
                    boolean local = paquet.getAddress().isLoopbackAddress()
                            || NetworkInterface.getByInetAddress(paquet.getAddress()) != null;
                    trouves.putIfAbsent(local ? "ce-pc" : serveur.hote(), serveur);
                }
            }
        }
        return List.copyOf(trouves.values());
    }

    static Serveur lireAnnonce(String texte, String hote) {
        String prefixe = "IHM2026:SERVER:2:";
        if (!texte.startsWith(prefixe)) return null;
        String nom = texte.substring(prefixe.length()).strip();
        if (nom.isEmpty() || nom.length() > 80 || nom.chars().anyMatch(Character::isISOControl)) return null;
        // L'adresse provient du paquet, jamais du texte fourni par un serveur inconnu.
        return new Serveur(nom, hote);
    }
}
