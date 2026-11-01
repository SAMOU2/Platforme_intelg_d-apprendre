package fr.ihm2026.reseau;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * Serveur local de la salle : le poste PC4.
 *
 * <p>Il ne remplace ni la base PostgreSQL ni l'API métier : c'est un <b>concentrateur de
 * présence et de résultats</b> pour les quatre postes d'une même salle, sur le réseau local. Il
 * répond en JSON sur six adresses :</p>
 *
 * <pre>
 *   GET  /api/sante        état du serveur (version, nombre de postes, nombre de résultats)
 *   POST /api/connexion    un poste s'annonce        {"poste":"PC3","role":"APPRENANT"}
 *   POST /api/deconnexion  un poste s'en va          {"poste":"PC3"}
 *   GET  /api/postes       liste des postes connectés
 *   GET  /api/resultats    résultats publiés
 *   POST /api/resultats    publication d'une note    {"apprenant":"Amina", ...}
 * </pre>
 *
 * <p>Trois choix de conception :</p>
 * <ol>
 *   <li><b>Aucune dépendance ajoutée.</b> Le serveur est celui du JDK
 *       ({@code com.sun.net.httpserver}) et le JSON est celui de Jackson, déjà présent pour
 *       l'agent d'assistance. Rien à installer sur les postes.</li>
 *   <li><b>Le serveur ne décide pas.</b> Il horodate et diffuse. La validation d'un niveau reste
 *       calculée par le poste apprenant à partir du seuil, et l'enseignant garde la main : le
 *       serveur ne note pas.</li>
 *   <li><b>Tout est lisible à la console.</b> Chaque connexion s'écrit en une ligne datée, ce qui
 *       suffit à diagnostiquer un poste qui « n'arrive pas à se connecter » sans outil réseau.</li>
 * </ol>
 *
 * <p>Instanciation directe pour les tests ({@code new ServeurLocal(0)} → port éphémère) ; en
 * production, {@link #lancerEnConsole()} bloque le processus du poste serveur.</p>
 */
public final class ServeurLocal implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter HEURE = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final HttpServer http;
    private final List<EtatReseau.Poste> postes = new CopyOnWriteArrayList<>();
    private final List<EtatReseau.Resultat> resultats = new CopyOnWriteArrayList<>();
    private final String version;
    private final java.net.DatagramSocket socketDecouverte;
    private final Thread serviceDecouverte;

    /** Démarre le serveur sur le port demandé (0 = port éphémère choisi par le système). */
    public ServeurLocal(int port) throws IOException {
        this(port, versionLue());
    }

    public ServeurLocal(int port, String version) throws IOException {
        this.version = version;
        this.http = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
        this.socketDecouverte = new java.net.DatagramSocket(
                new InetSocketAddress("0.0.0.0", http.getAddress().getPort()));
        this.socketDecouverte.setSoTimeout(1000);
        this.serviceDecouverte = new Thread(this::servirDecouverte, "ihm-decouverte-salle");
        this.serviceDecouverte.setDaemon(true);
        this.http.createContext("/api/", this::traiter);
        // Quatre fils suffisent : quatre postes, quelques requêtes par minute.
        this.http.setExecutor(Executors.newFixedThreadPool(4));
        this.http.start();
        this.serviceDecouverte.start();
    }

    private static String versionLue() {
        try {
            return fr.ihm2026.core.Config.VERSION;
        } catch (RuntimeException e) {
            return "0.0.0";
        }
    }

    /** Port réellement écouté (utile quand on a demandé le port 0). */
    public int port() {
        return http.getAddress().getPort();
    }

    public List<EtatReseau.Poste> postes() {
        return List.copyOf(postes);
    }

    public List<EtatReseau.Resultat> resultats() {
        return List.copyOf(resultats);
    }

    @Override
    public void close() {
        http.stop(0);
        socketDecouverte.close();
    }

    private void servirDecouverte() {
        byte[] tampon = new byte[64];
        while (!socketDecouverte.isClosed()) {
            java.net.DatagramPacket paquet = new java.net.DatagramPacket(tampon, tampon.length);
            try {
                socketDecouverte.receive(paquet);
                String demande = new String(paquet.getData(), paquet.getOffset(), paquet.getLength(),
                        java.nio.charset.StandardCharsets.US_ASCII);
                if ("IHM2026:DISCOVER:2".equals(demande)) {
                    byte[] annonce = ("IHM2026:SERVER:2:" + DecouverteServeurs.nomLocal())
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8);
                    socketDecouverte.send(new java.net.DatagramPacket(annonce, annonce.length,
                            paquet.getSocketAddress()));
                } else if ("IHM2026:DISCOVER".equals(demande)) {
                    byte[] reponse = "IHM2026:OK".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
                    socketDecouverte.send(new java.net.DatagramPacket(reponse, reponse.length,
                            paquet.getSocketAddress()));
                }
            } catch (java.net.SocketTimeoutException ex) {
                // Le délai permet de constater l'arrêt du socket sans bloquer la fermeture.
            } catch (java.net.SocketException ex) {
                if (!socketDecouverte.isClosed()) {
                    journal("service de découverte interrompu : " + ex.getMessage());
                }
                return;
            } catch (IOException ex) {
                journal("échec du service de découverte : " + ex.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ routage

    private void traiter(HttpExchange e) throws IOException {
        String chemin = e.getRequestURI().getPath();
        String methode = e.getRequestMethod();
        try {
            switch (chemin) {
                case "/api/sante" -> {
                    if (!"GET".equals(methode)) {
                        envoyer(e, 405, new EtatReseau.Reponse(false, "Méthode attendue : GET."));
                        return;
                    }
                    envoyer(e, 200, new EtatReseau.Sante("ihm-2026", version, "serveur",
                            postes.size(), resultats.size(), heure()));
                }
                case "/api/postes" -> {
                    if (!"GET".equals(methode)) {
                        envoyer(e, 405, new EtatReseau.Reponse(false, "Méthode attendue : GET."));
                        return;
                    }
                    envoyer(e, 200, new EtatReseau.Postes(postes));
                }
                case "/api/connexion" -> connexion(e);
                case "/api/deconnexion" -> deconnexion(e);
                case "/api/resultats" -> resultats(e, methode);
                default -> envoyer(e, 404, new EtatReseau.Reponse(false,
                        "Adresse inconnue : " + chemin));
            }
        } catch (RuntimeException ex) {
            journal("erreur " + chemin + " : " + ex.getMessage());
            envoyer(e, 500, new EtatReseau.Reponse(false, "Erreur du serveur : " + ex.getMessage()));
        }
    }

    private void connexion(HttpExchange e) throws IOException {
        if (!"POST".equals(e.getRequestMethod())) {
            envoyer(e, 405, new EtatReseau.Reponse(false, "Méthode attendue : POST."));
            return;
        }
        JsonNode corps = lire(e);
        String nom = texte(corps, "poste", "PC?");
        String role = texte(corps, "role", "");
        if (!List.of("APPRENANT", "ENSEIGNANT", "ADMINISTRATEUR").contains(role)) {
            // Un rôle inconnu est refusé au lieu d'être deviné : un poste mal configuré doit se
            // voir à l'écran, pas se retrouver apprenant par accident.
            envoyer(e, 400, new EtatReseau.Reponse(false,
                    "Rôle inconnu : « " + role + " » (attendu APPRENANT, ENSEIGNANT ou ADMINISTRATEUR)."));
            return;
        }
        String adresse = adresseDe(e);
        postes.removeIf(p -> p.poste().equalsIgnoreCase(nom));   // reconnexion = remplacement
        postes.add(new EtatReseau.Poste(nom, role, adresse, heure()));
        journal(nom + " " + role + " connecté depuis " + adresse);
        envoyer(e, 200, new EtatReseau.Reponse(true,
                "Bienvenue " + nom + " — " + postes.size() + " poste(s) en ligne, version "
                        + version + "."));
    }

    private void deconnexion(HttpExchange e) throws IOException {
        if (!"POST".equals(e.getRequestMethod())) {
            envoyer(e, 405, new EtatReseau.Reponse(false, "Méthode attendue : POST."));
            return;
        }
        String nom = texte(lire(e), "poste", "");
        boolean retire = postes.removeIf(p -> p.poste().equalsIgnoreCase(nom));
        journal(nom + (retire ? " déconnecté" : " : déconnexion ignorée (poste inconnu)"));
        envoyer(e, 200, new EtatReseau.Reponse(retire,
                retire ? nom + " déconnecté." : "Poste inconnu : " + nom));
    }

    private void resultats(HttpExchange e, String methode) throws IOException {
        if ("GET".equals(methode)) {
            envoyer(e, 200, new EtatReseau.Resultats(resultats));
            return;
        }
        if (!"POST".equals(methode)) {
            envoyer(e, 405, new EtatReseau.Reponse(false, "Méthodes acceptées : GET, POST."));
            return;
        }
        JsonNode corps = lire(e);
        String apprenant = texte(corps, "apprenant", "Un apprenant");
        String langage = texte(corps, "langage", "Python");
        int niveau = entier(corps, "niveau", 1);
        int note = entier(corps, "note", 0);
        int seuil = entier(corps, "seuil", 12);
        // `valide` est recalculé ici à partir du seuil transmis : un client ne peut pas déclarer
        // « validé » avec 4/20. Le serveur ne note toujours pas, il vérifie une soustraction.
        EtatReseau.Resultat r = new EtatReseau.Resultat(apprenant, langage, niveau, note, seuil,
                note >= seuil, heure());
        resultats.add(r);
        journal("résultat reçu de " + apprenant + " : " + note + "/20 niveau " + niveau
                + " (" + langage + ")");
        envoyer(e, 200, new EtatReseau.Reponse(true,
                "Résultat enregistré : " + note + "/20 (" + resultats.size() + " au total)."));
    }

    // ------------------------------------------------------------------ outils

    private static JsonNode lire(HttpExchange e) throws IOException {
        byte[] corps = e.getRequestBody().readAllBytes();
        if (corps.length == 0) {
            return MAPPER.createObjectNode();
        }
        return MAPPER.readTree(new String(corps, StandardCharsets.UTF_8));
    }

    private static String texte(JsonNode n, String champ, String defaut) {
        JsonNode v = n.get(champ);
        return v == null || v.isNull() ? defaut : v.asText(defaut);
    }

    private static int entier(JsonNode n, String champ, int defaut) {
        JsonNode v = n.get(champ);
        return v == null || !v.canConvertToInt() ? defaut : v.asInt(defaut);
    }

    private static String adresseDe(HttpExchange e) {
        InetSocketAddress distant = e.getRemoteAddress();
        return distant == null ? "inconnue" : distant.getAddress().getHostAddress();
    }

    private static String heure() {
        return LocalTime.now().format(HEURE);
    }

    private void envoyer(HttpExchange e, int code, Object corps) throws IOException {
        ObjectNode racine = MAPPER.valueToTree(corps);
        byte[] octets = MAPPER.writerWithDefaultPrettyPrinter()
                .writeValueAsString(racine).getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        e.sendResponseHeaders(code, octets.length);
        try (OutputStream out = e.getResponseBody()) {
            out.write(octets);
        }
    }

    private static void journal(String ligne) {
        System.out.println("[serveur " + heure() + "] " + ligne);
    }

    // ------------------------------------------------------------------ poste serveur (PC4)

    /**
     * Mode « poste serveur » : le processus ne crée aucune fenêtre, il écoute et journalise
     * jusqu'à Ctrl+C. C'est ce que lance {@code postes/serveur-pc4.cmd}.
     */
    public static void lancerEnConsole() {
        int port = IdentitePoste.port();
        System.out.println("=====================================================");
        System.out.println(" Serveur local IHM 2026 — " + fr.ihm2026.core.Config.VERSION);
        System.out.println(" Poste      : " + IdentitePoste.nom() + " (rôle serveur)");
        System.out.println(" Écoute     : 0.0.0.0:" + port + " (toutes les cartes réseau)");
        System.out.println(" Découverte : automatique sur le réseau local (aucune adresse IP à saisir)");
        System.out.println(" Postes     : administrateur · enseignant · apprenant");
        System.out.println(" Arrêt      : Ctrl+C");
        System.out.println("=====================================================");
        try (ServeurLocal serveur = new ServeurLocal(port)) {
            journal("prêt — " + port + " ouvert");
            Thread.currentThread().join();
        } catch (IOException e) {
            System.out.println("[serveur] impossible d'écouter sur le port " + port
                    + " : " + e.getMessage());
            System.out.println("[serveur] un autre programme utilise peut-être ce port ;"
                    + " relancer avec -Dihm.port=7778");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Adresses IPv4 des cartes locales : c'est celle-là que les autres postes doivent taper. */
    public static String adressesLocales() {
        List<String> trouves = new ArrayList<>();
        try {
            var interfaces = java.net.NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                var carte = interfaces.nextElement();
                if (!carte.isUp() || carte.isLoopback()) {
                    continue;
                }
                for (var adresse : java.util.Collections.list(carte.getInetAddresses())) {
                    String ip = adresse.getHostAddress();
                    if (adresse instanceof java.net.Inet4Address) {
                        trouves.add(ip);
                    }
                }
            }
        } catch (IOException ignore) {
            // Pas de carte réseau lisible : la boucle locale suffit pour un poste unique.
        }
        return trouves.isEmpty() ? "127.0.0.1" : String.join(" ou ", trouves);
    }

    /** Utilitaire de test : un serveur démarré sur un port libre, refermé automatiquement. */
    public static Optional<ServeurLocal> demarrer(int port) {
        try {
            return Optional.of(new ServeurLocal(port));
        } catch (IOException e) {
            return Optional.empty();
        }
    }
}
