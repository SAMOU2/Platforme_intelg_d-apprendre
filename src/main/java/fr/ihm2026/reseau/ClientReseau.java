package fr.ihm2026.reseau;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;

/**
 * Le client réseau d'un poste : tout ce qu'un poste sait faire sur le serveur local.
 *
 * <p>Les délais sont courts et volontairement <b>courts</b> (2 s de connexion, 5 s de réponse) :
 * sur un réseau local, un serveur qui ne répond pas en deux secondes est éteint, et l'utilisateur
 * doit le savoir tout de suite plutôt que de regarder une fenêtre figée.</p>
 *
 * <p>Les erreurs sont traduites en {@link ErreurReseau} avec un message en français qui dit quoi
 * faire : c'est ce message qui s'affiche dans l'écran de connexion.</p>
 */
public final class ClientReseau {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String hote;
    private final int port;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    public ClientReseau(String hote, int port) {
        this.hote = hote == null ? "" : hote.strip();
        this.port = port;
    }

    /** Résout le serveur par diffusion locale sans demander une adresse IP ou un PIN de salle. */
    public static String decouvrirServeur(int port) {
        if (port < 1 || port > 65535) {
            throw new ErreurReseau("Le port de découverte du serveur est invalide.");
        }
        byte[] demande = "IHM2026:DISCOVER".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        byte[] reponse = new byte[32];
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.setBroadcast(true);
            socket.setSoTimeout(2000);
            socket.send(new DatagramPacket(demande, demande.length,
                    InetAddress.getByName("255.255.255.255"), port));
            DatagramPacket paquet = new DatagramPacket(reponse, reponse.length);
            socket.receive(paquet);
            String texte = new String(paquet.getData(), paquet.getOffset(), paquet.getLength(),
                    java.nio.charset.StandardCharsets.US_ASCII);
            if (!"IHM2026:OK".equals(texte)) {
                throw new ErreurReseau("Réponse invalide du service de découverte.");
            }
            return paquet.getAddress().getHostAddress();
        } catch (SocketTimeoutException ex) {
            throw new ErreurReseau(
                    "Aucun serveur trouvé sur le réseau local. Vérifier que le serveur est démarré.", ex);
        } catch (IOException ex) {
            throw new ErreurReseau("Découverte du serveur impossible : " + ex.getMessage(), ex);
        }
    }

    public String hote() {
        return hote;
    }

    public int port() {
        return port;
    }

    /** « 192.168.1.10:7777 » — la forme affichée partout dans l'interface. */
    public String adresse() {
        return hote + ":" + port;
    }

    /** Sonde de présence : lève {@link ErreurReseau} si le serveur est absent ou ne parle pas JSON. */
    public EtatReseau.Sante sante() {
        return lire("/api/sante", EtatReseau.Sante.class);
    }

    /** Annonce le poste et son rôle ; le serveur répond avec un message d'accueil lisible. */
    public EtatReseau.Reponse connecter(String nomPoste, String role) {
        return poster("/api/connexion",
                java.util.Map.of("poste", nomPoste, "role", role), EtatReseau.Reponse.class);
    }

    public EtatReseau.Reponse deconnecter(String nomPoste) {
        return poster("/api/deconnexion",
                java.util.Map.of("poste", nomPoste), EtatReseau.Reponse.class);
    }

    public List<EtatReseau.Poste> postes() {
        return lire("/api/postes", EtatReseau.Postes.class).postes();
    }

    public List<EtatReseau.Resultat> resultats() {
        return lire("/api/resultats", EtatReseau.Resultats.class).resultats();
    }

    public EtatReseau.Reponse publierResultat(EtatReseau.Resultat r) {
        return poster("/api/resultats", r, EtatReseau.Reponse.class);
    }

    // ------------------------------------------------------------------ plomberie HTTP

    private <T> T lire(String chemin, Class<T> type) {
        HttpRequest requete = HttpRequest.newBuilder(uri(chemin))
                .timeout(Duration.ofSeconds(5))
                .header("Accept", "application/json")
                .GET()
                .build();
        return executer(requete, type);
    }

    private <T> T poster(String chemin, Object corps, Class<T> type) {
        String json;
        try {
            json = MAPPER.writeValueAsString(corps);
        } catch (Exception e) {
            throw new ErreurReseau("Impossible de préparer la demande : " + e.getMessage(), e);
        }
        HttpRequest requete = HttpRequest.newBuilder(uri(chemin))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return executer(requete, type);
    }

    private URI uri(String chemin) {
        try {
            return URI.create("http://" + hote + ":" + port + chemin);
        } catch (IllegalArgumentException e) {
            throw new ErreurReseau("Adresse de serveur invalide : « " + adresse() + " ».", e);
        }
    }

    private <T> T executer(HttpRequest requete, Class<T> type) {
        HttpResponse<String> reponse;
        try {
            reponse = http.send(requete, HttpResponse.BodyHandlers.ofString());
        } catch (java.net.ConnectException e) {
            throw new ErreurReseau("Aucun serveur ne répond à l'adresse " + adresse()
                    + ". Vérifier que le poste serveur (PC4) est allumé et que l'adresse IP est"
                    + " la bonne.", e);
        } catch (java.net.http.HttpConnectTimeoutException e) {
            throw new ErreurReseau("Le serveur " + adresse() + " n'a pas répondu en 2 secondes."
                    + " Vérifier le câble, le Wi-Fi, puis l'adresse IP.", e);
        } catch (java.io.IOException e) {
            throw new ErreurReseau("Communication interrompue avec " + adresse() + " : "
                    + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ErreurReseau("Connexion annulée.", e);
        }
        if (reponse.statusCode() >= 400) {
            // Le serveur explique toujours le refus en clair : on relaie son message tel quel
            // plutôt que d'inventer un « erreur 400 » que personne ne peut interpréter.
            String message = messageDe(reponse.body());
            throw new ErreurReseau(message == null
                    ? "Le serveur a refusé la demande (" + reponse.statusCode() + ")." : message);
        }
        try {
            return MAPPER.readValue(reponse.body(), type);
        } catch (Exception e) {
            throw new ErreurReseau("Réponse illisible de " + adresse()
                    + " : ce serveur n'est pas un serveur IHM 2026.", e);
        }
    }

    private static String messageDe(String corps) {
        try {
            var noeud = MAPPER.readTree(corps);
            var message = noeud.get("message");
            return message == null ? null : message.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /** Erreur réseau destinée à l'utilisateur : son message est déjà rédigé pour l'écran. */
    public static final class ErreurReseau extends RuntimeException {

        private static final long serialVersionUID = 1L;

        public ErreurReseau(String message) {
            super(message);
        }

        public ErreurReseau(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
