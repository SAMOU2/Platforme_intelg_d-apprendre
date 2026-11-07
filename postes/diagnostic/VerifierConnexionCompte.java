// =====================================================================
//  DIAGNOSTIC - "est-ce que mon compte se connecte ?" sans l'interface.
//
//  Lance par postes\verifier-connexion-compte.cmd, directement par le JDK.
//  Il envoie la MEME requete que l'ecran de connexion (POST /api/auth/connexion)
//  avec la meme confiance (autorite privee du JDK) et affiche :
//    - le temps de reponse, pour voir si le service repond ou bloque ;
//    - le code HTTP et la reponse du serveur ;
//    - l'interpretation : session ouverte, code envoye par e-mail,
//      identifiants refuses, compte inactif, service injoignable.
//
//  Regles : l'adresse e-mail et le mot de passe sont lus depuis l'entree
//  standard (jamais en argument : les arguments sont visibles dans le
//  gestionnaire de taches). Le mot de passe n'est jamais affiche, jamais
//  ecrit sur le disque, et efface de la memoire des la requete envoyee.
// =====================================================================

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

public class VerifierConnexionCompte {

    private static final int DELAI_CONNEXION = 5000;
    private static final int DELAI_LECTURE = 60000;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            System.out.println("Usage : java VerifierConnexionCompte.java <adresse> <port>");
            System.out.println("L'e-mail puis le mot de passe sont lus ensuite.");
            return;
        }
        String hote = arguments[0].trim();
        String portTexte = arguments[1].trim();
        int port;
        try {
            port = Integer.parseInt(portTexte);
        } catch (NumberFormatException ex) {
            System.out.println("[ERREUR] Port invalide : " + portTexte);
            return;
        }

        String email = lireLigne("E-mail");
        char[] secret = lireSecret("Mot de passe");
        if (email == null || email.isBlank()) {
            System.out.println("[ERREUR] Aucune adresse e-mail.");
            return;
        }
        if (secret.length == 0) {
            System.out.println("[ERREUR] Mot de passe vide.");
            return;
        }

        String cible = "https://" + hote + ":" + port + "/api/auth/connexion";
        System.out.println("Serveur : " + cible);
        System.out.println("JDK     : " + System.getProperty("java.home"));
        System.out.println("");

        SSLContext confiance;
        try {
            confiance = fr.ihm2026.api.service.ConfiancePlateforme.depuisJdk()
                    .orElseGet(() -> {
                        try { return SSLContext.getDefault(); } catch (Exception ex) { return null; }
                    });
        } catch (Throwable ex) {
            confiance = null;
        }
        System.out.println("Confiance : " + (confiance == null
                ? "magasin par defaut du JDK"
                : "autorite privee de la plateforme"));

        String corps;
        try {
            corps = "{\"email\":" + citer(email) + ",\"motDePasse\":" + citer(new String(secret)) + "}";
        } finally {
            Arrays.fill(secret, '\0');
        }

        long debut = System.nanoTime();
        int statut = -1;
        String reponse = "";
        try {
            URL adresse = URI.create(cible).toURL();
            HttpsURLConnection lien = (HttpsURLConnection) adresse.openConnection();
            if (confiance != null) lien.setSSLSocketFactory(confiance.getSocketFactory());
            lien.setConnectTimeout(DELAI_CONNEXION);
            lien.setReadTimeout(DELAI_LECTURE);
            lien.setRequestMethod("POST");
            lien.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            lien.setRequestProperty("Accept", "application/json");
            lien.setDoOutput(true);
            byte[] donnees = corps.getBytes(StandardCharsets.UTF_8);
            lien.setFixedLengthStreamingMode(donnees.length);
            try (OutputStream sortie = lien.getOutputStream()) {
                sortie.write(donnees);
            }
            statut = lien.getResponseCode();
            InputStream flux = statut >= 400 ? lien.getErrorStream() : lien.getInputStream();
            reponse = flux == null ? "" : lire(flux);
        } catch (SSLHandshakeException ex) {
            long duree = dureeMs(debut);
            System.out.println("");
            System.out.println("[ECHEC CONFIANCE] apres " + duree + " ms : " + ex.getMessage());
            System.out.println("  -> l'autorite privee n'est pas approuvee dans ce JDK.");
            System.out.println("  -> lancer postes\\approuver-autorite-client.bat avec ce meme JDK.");
            return;
        } catch (Exception ex) {
            long duree = dureeMs(debut);
            System.out.println("");
            System.out.println("[ECHEC] apres " + duree + " ms : " + ex.getClass().getSimpleName()
                    + " : " + ex.getMessage());
            System.out.println("  -> le service n'a pas repondu. Causes possibles :");
            System.out.println("     - le port affiche par la fenetre d'authentification a change ;");
            System.out.println("       relancer ce diagnostic avec le port actuel ;");
            System.out.println("     - la fenetre d'authentification n'est plus ouverte ;");
            System.out.println("     - le pare-feu Windows bloque ce port sur le réseau local.");
            return;
        }
        long duree = dureeMs(debut);

        System.out.println("");
        System.out.println("Reponse en " + duree + " ms : HTTP " + statut);
        System.out.println("Corps : " + reponse);
        System.out.println("");
        System.out.println("=== Interpretation ===");
        if (statut == 200) {
            if (reponse.contains("\"jeton\"")) {
                System.out.println("CONNEXION OK : session ouverte directement");
                System.out.println("(poste de confiance encore valide, aucun code e-mail demande).");
            } else if (reponse.contains("\"identifiant\"")) {
                System.out.println("CONNEXION OK : le serveur a accepte e-mail et mot de passe.");
                System.out.println("Il a envoye un code par e-mail : saisir ce code dans l'interface.");
            } else {
                System.out.println("Reponse inattendue du serveur (200 sans jeton ni identifiant).");
            }
        } else if (statut == 401) {
            System.out.println("IDENTIFIANTS REFUSES : e-mail ou mot de passe incorrect,");
            System.out.println("ou compte inexistant/inactif. Le serveur repond donc bien :");
            System.out.println("le probleme n'est ni le certificat, ni le port, ni le reseau.");
        } else if (statut == 403) {
            System.out.println("REFUSE : compte non active (PIN) ou non accepte par l'administrateur.");
        } else if (statut == 404 || statut == 405) {
            System.out.println("Route non trouvee : verifier que le port est bien celui du service");
            System.out.println("d'authentification (pas celui de la salle 7777).");
        } else {
            System.out.println("Reponse inattendue : " + statut + ". Copier ce texte pour analyse.");
        }
    }

    private static long dureeMs(long debutNano) {
        return java.time.Duration.ofNanos(System.nanoTime() - debutNano).toMillis();
    }

    private static String lire(InputStream flux) throws Exception {
        ByteArrayOutputStream tampon = new ByteArrayOutputStream();
        byte[] bloc = new byte[4096];
        int lus;
        while ((lus = flux.read(bloc)) > 0) tampon.write(bloc, 0, lus);
        flux.close();
        return tampon.toString(StandardCharsets.UTF_8);
    }

    private static String lireLigne(String invite) throws Exception {
        System.out.print(invite + " (depuis l'entree standard) : ");
        System.out.flush();
        byte[] bloc = new byte[512];
        int lus = System.in.read(bloc);
        if (lus <= 0) return null;
        String texte = new String(bloc, 0, lus, StandardCharsets.UTF_8);
        int fin = texte.indexOf('\n');
        if (fin < 0) fin = texte.length();
        return texte.substring(0, fin).replace("\r", "").strip();
    }

    private static char[] lireSecret(String invite) throws Exception {
        String ligne = lireLigne(invite);
        return ligne == null ? new char[0] : ligne.toCharArray();
    }

    private static String citer(String valeur) {
        StringBuilder texte = new StringBuilder("\"");
        for (char c : valeur.toCharArray()) {
            switch (c) {
                case '"': texte.append("\\\""); break;
                case '\\': texte.append("\\\\"); break;
                case '\n': texte.append("\\n"); break;
                case '\r': texte.append("\\r"); break;
                case '\t': texte.append("\\t"); break;
                default:
                    if (c < 0x20) texte.append(String.format("\\u%04x", (int) c));
                    else texte.append(c);
            }
        }
        return texte.append('"').toString();
    }
}
