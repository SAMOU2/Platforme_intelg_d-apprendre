package fr.ihm2026.reseau;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Découverte indicative uniquement : l'identité reste vérifiée par HTTPS. */
public final class DecouverteServeurs {
    private DecouverteServeurs() { }

    /** Valeur de repli historique : elle ne doit plus jamais être utilisée à l'aveugle. */
    public static final int PORT_REPLI = 8443;

    public record Serveur(String nom, String hote, int portHttps) {
        public Serveur(String nom,String hote){this(nom,hote,PORT_REPLI);}
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
        Set<String> clesModernes = new HashSet<>();
        Set<String> depuisRegistreLocal = new HashSet<>();
        Set<InetAddress> destinations = new LinkedHashSet<>();
        destinations.add(InetAddress.getByName("255.255.255.255"));
        for (NetworkInterface carte : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (!InterfacesReseau.estLanPhysique(carte)) continue;
            for (InterfaceAddress adresse : carte.getInterfaceAddresses()) {
                if (adresse.getBroadcast() != null) destinations.add(adresse.getBroadcast());
            }
        }
        byte[] demande = "IHM2026:DISCOVER:2".getBytes(StandardCharsets.UTF_8);
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            for (InetAddress destination : destinations) {
                try {
                    byte[] moderne="IHM2026:DISCOVER:3".getBytes(StandardCharsets.UTF_8);
                    socket.send(new DatagramPacket(moderne, moderne.length, destination, port));
                    socket.send(new DatagramPacket(demande, demande.length, destination, port));
                }
                catch (IOException ignore) { /* Une interface inaccessible n'empêche pas les autres. */ }
            }
            long fin = System.nanoTime() + 2_000_000_000L;
            while (System.nanoTime() < fin) {
                socket.setSoTimeout(Math.max(1, (int) ((fin - System.nanoTime()) / 1_000_000)));
                DatagramPacket paquet = new DatagramPacket(new byte[512], 512);
                try { socket.receive(paquet); }
                catch (SocketTimeoutException termine) { break; }
                if (paquet.getPort() != port || paquet.getAddress().isLoopbackAddress()) continue;
                NetworkInterface locale=NetworkInterface.getByInetAddress(paquet.getAddress());
                if(locale!=null && !InterfacesReseau.estLanPhysique(locale))continue;
                String texte = new String(paquet.getData(), paquet.getOffset(), paquet.getLength(), StandardCharsets.UTF_8);
                Serveur serveur = lireAnnonce(texte, paquet.getAddress().getHostAddress());
                if (serveur != null) {
                    boolean annonceV3 = texte.startsWith("IHM2026:SERVER:3:");
                    // Même PC et annonce sans port : lire le registre du port HTTPS plutot
                    // que de laisser le poste viser 8443, qui n'est jamais le port reel.
                    boolean depuisRegistre = false;
                    if (!annonceV3 && locale != null) {
                        int publie = InstanceHttpsLocale.portActif();
                        if (publie > 0) {
                            serveur = new Serveur(serveur.nom(), serveur.hote(), publie);
                            depuisRegistre = true;
                        }
                    }
                    String cle=locale!=null ? "ce-pc" : serveur.hote();
                    // Une annonce avec le port HTTPS réel remplace un repli sans port pour
                    // la même clé, mais une seconde réponse du même PC (autre carte) ne doit
                    // pas écraser la première au hasard de l'ordre d'arrivée.
                    if (annonceV3 || depuisRegistre) {
                        if (clesModernes.add(cle) || (annonceV3 && depuisRegistreLocal.remove(cle))) {
                            trouves.put(cle, serveur);
                        }
                        if (depuisRegistre) depuisRegistreLocal.add(cle);
                    } else if (!clesModernes.contains(cle)) {
                        trouves.putIfAbsent(cle, serveur);
                    }
                }
            }
        }
        return List.copyOf(trouves.values());
    }

    static Serveur lireAnnonce(String texte, String hote) {
        int portHttps=PORT_REPLI;
        if(texte.startsWith("IHM2026:SERVER:3:")) {
            String[] champs=texte.substring("IHM2026:SERVER:3:".length()).split(":",2);
            if(champs.length!=2)return null;
            try {portHttps=Integer.parseInt(champs[0]);}catch(NumberFormatException ex){return null;}
            if(portHttps<1 || portHttps>65535)return null;
            texte="IHM2026:SERVER:2:"+champs[1];
        }
        String prefixe = "IHM2026:SERVER:2:";
        if (!texte.startsWith(prefixe)) return null;
        String nom = texte.substring(prefixe.length()).strip();
        if (nom.isEmpty() || nom.length() > 80 || nom.chars().anyMatch(Character::isISOControl)) return null;
        // L'adresse provient du paquet, jamais du texte fourni par un serveur inconnu.
        return new Serveur(nom, hote, portHttps);
    }
}
