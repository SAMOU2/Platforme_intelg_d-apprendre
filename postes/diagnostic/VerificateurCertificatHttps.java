// =====================================================================
//  DIAGNOSTIC - verification de l'accessibilite du certificat HTTPS.
//
//  Ce fichier n'est pas compile par Maven : il est lance directement par
//  le JDK (lancement d'un fichier source unique, Java 11+) via
//  postes\verifier-certificat-https.cmd. Il ne modifie aucune donnee, ne
//  desactive rien et n'ecrit aucun secret.
//
//  Il repond a quatre questions, dans cet ordre :
//    0. combien de temps met ce JDK a charger son magasin de confiance ?
//    1. le service repond-il DES LA PREMIERE requete, ou seulement ensuite ?
//    2. le JDK fait-il confiance au certificat a l'adresse demandee ?
//    3. que contient le certificat presente (dates, adresses SAN) ?
// =====================================================================

import java.net.URI;
import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509TrustManager;

public class VerificateurCertificatHttps {

    private static final int DELAI_CONNEXION = 15000;
    private static final int DELAI_LECTURE = 20000;
    private static final int ESSAIS = 3;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            System.out.println("Usage : java VerificateurCertificatHttps.java <adresse> <port>");
            System.out.println("Exemple : java VerificateurCertificatHttps.java 192.168.8.100 28160");
            return;
        }
        String hote = arguments[0].trim();
        int port;
        try {
            port = Integer.parseInt(arguments[1].trim());
        } catch (NumberFormatException ex) {
            System.out.println("[ERREUR] Port invalide : " + arguments[1]);
            return;
        }
        String cible = "https://" + hote + ":" + port + "/api/auth/connexion";
        System.out.println("Cible  : " + cible);
        System.out.println("JDK    : " + System.getProperty("java.home"));
        System.out.println("Delais : connexion " + (DELAI_CONNEXION / 1000) + " s, lecture "
                + (DELAI_LECTURE / 1000) + " s, " + ESSAIS + " essais.");
        System.out.println("");

        System.out.println("=== 0. Chargement du magasin de confiance du JDK ===");
        long debutConfiance = System.nanoTime();
        TrustManagerFactory usine = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        usine.init((java.security.KeyStore) null);
        System.out.println("Magasin charge en " + millis(debutConfiance) + " ms ("
                + usine.getTrustManagers().length + " gestionnaires).");
        System.out.println("");

        System.out.println("=== 1. Reponses successives avec la confiance du JDK (comme l'application) ===");
        int reussis = 0;
        for (int essai = 1; essai <= ESSAIS; essai++) {
            long debut = System.nanoTime();
            try {
                HttpsURLConnection lien = ouvrir(cible, null);
                int statut = lien.getResponseCode();
                String suite;
                try { suite = lien.getCipherSuite(); } catch (RuntimeException ex) { suite = "?"; }
                System.out.println("Essai " + essai + " : STATUT=" + statut + " en " + millis(debut)
                        + " ms  | suite TLS : " + suite);
                System.out.println("           TLS negocie et certificat approuve par ce JDK."
                        + " (405/404 est normal en GET : la route n'accepte que POST.)");
                reussis++;
            } catch (SSLHandshakeException ex) {
                System.out.println("Essai " + essai + " : [ECHEC CONFIANCE] apres " + millis(debut)
                        + " ms : " + ex.getMessage());
                System.out.println("           -> autorite absente de ce JDK, ou adresse non couverte par le certificat.");
                System.out.println("           -> lancer postes\\approuver-autorite-client.bat avec ce meme JDK.");
            } catch (Exception ex) {
                System.out.println("Essai " + essai + " : [ECHEC] apres " + millis(debut) + " ms : "
                        + ex.getClass().getSimpleName() + " : " + ex.getMessage());
                System.out.println("           -> le serveur n'a pas repondu dans le delai : port perime,");
                System.out.println("              service arrete, pare-feu, ou serveur occupe par un autre client.");
            }
        }
        System.out.println("");

        System.out.println("=== 2. Certificat presente par le serveur (lecture sans validation) ===");
        try {
            HttpsURLConnection lien = ouvrir(cible, contextePermissif());
            lien.setHostnameVerifier((nom, session) -> true);
            lien.getResponseCode();
            Certificate[] chaine = lien.getServerCertificates();
            for (int i = 0; i < chaine.length; i++) {
                X509Certificate certificat = (X509Certificate) chaine[i];
                System.out.println("--- certificat " + (i + 1) + " ---");
                System.out.println("Sujet     : " + certificat.getSubjectX500Principal());
                System.out.println("Emetteur  : " + certificat.getIssuerX500Principal());
                System.out.println("Valide du : " + certificat.getNotBefore());
                System.out.println("Valide au : " + certificat.getNotAfter());
                System.out.println("Expire    : " + (certificat.getNotAfter().before(new Date()) ? "OUI" : "non"));
                var san = certificat.getSubjectAlternativeNames();
                if (san == null) {
                    System.out.println("SAN       : AUCUN -> l'adresse " + hote + " ne peut pas etre validee.");
                } else {
                    boolean couverte = false;
                    for (Object entree : san) {
                        List<?> valeur = (List<?>) entree;
                        System.out.println("SAN       : type " + valeur.get(0) + " = " + valeur.get(1));
                        if (String.valueOf(valeur.get(1)).equals(hote)) couverte = true;
                    }
                    System.out.println("Adresse " + hote + " couverte : " + (couverte ? "OUI" : "NON"));
                    if (!couverte) {
                        System.out.println("  -> laisser le serveur d'authentification ouvert ~15 s pour qu'il");
                        System.out.println("     renouvelle son certificat, ou utiliser une adresse listee ici.");
                    } else if (san.size() > 1) {
                        System.out.println("  -> plusieurs adresses : normal avant la PR #44, anormal apres.");
                        System.out.println("     Si WSL/WARP apparait encore, le serveur n'a pas ete recompile.");
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("[ECHEC] lecture du certificat impossible : " + ex);
        }

        System.out.println("");
        System.out.println("=== 3. Conclusion ===");
        if (reussis == ESSAIS) {
            System.out.println("OK : " + reussis + "/" + ESSAIS + " requetes abouties. Le service et le certificat");
            System.out.println("sont accessibles sur " + hote + ":" + port + " avec ce JDK.");
        } else if (reussis > 0) {
            System.out.println("INSTABLE : " + reussis + "/" + ESSAIS + " requetes abouties. Le certificat est bon,");
            System.out.println("mais le serveur ne repond pas a chaque fois : voir les temps affiches ci-dessus.");
            System.out.println("Si seul le 1er essai echoue : prechauffage du serveur, reessayer cote application.");
            System.out.println("Si les essais echouent par intermittence : un autre client occupe le serveur.");
        } else {
            System.out.println("Connexion jamais etablie : relire les messages ci-dessus avant tout changement.");
            System.out.println("Ne jamais desactiver la verification TLS pour 'que ca marche'.");
        }
    }

    private static long millis(long debutNano) {
        return Duration.ofNanos(System.nanoTime() - debutNano).toMillis();
    }

    private static HttpsURLConnection ouvrir(String cible, SSLContext contexte) throws Exception {
        URL adresse = URI.create(cible).toURL();
        HttpsURLConnection lien = (HttpsURLConnection) adresse.openConnection();
        if (contexte != null) lien.setSSLSocketFactory(contexte.getSocketFactory());
        lien.setConnectTimeout(DELAI_CONNEXION);
        lien.setReadTimeout(DELAI_LECTURE);
        lien.setRequestMethod("GET");
        lien.setInstanceFollowRedirects(false);
        return lien;
    }

    /** Contexte de lecture seule : il sert uniquement a AFFICHER le certificat, jamais a se connecter. */
    private static SSLContext contextePermissif() throws Exception {
        SSLContext contexte = SSLContext.getInstance("TLS");
        TrustManager[] toutLire = new TrustManager[] { new X509TrustManager() {
            public void checkClientTrusted(X509Certificate[] chaine, String type) { }
            public void checkServerTrusted(X509Certificate[] chaine, String type) { }
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
        } };
        contexte.init(null, toutLire, new SecureRandom());
        return contexte;
    }
}
