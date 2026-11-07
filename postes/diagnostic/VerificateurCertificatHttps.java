// =====================================================================
//  DIAGNOSTIC - verification de l'accessibilite du certificat HTTPS.
//
//  Ce fichier n'est pas compile par Maven : il est lance directement par
//  le JDK (lancement d'un fichier source unique, Java 11+) via
//  postes\verifier-certificat-https.cmd. Il ne modifie aucune donnee, ne
//  desactive rien et n'ecrit aucun secret.
//
//  Il repond a trois questions, dans cet ordre :
//    1. le service HTTPS repond-il sur l'adresse et le port indiques ?
//    2. le JDK qui lance l'application fait-il confiance au certificat ?
//    3. le certificat presente couvre-t-il bien l'adresse utilisee (SAN) ?
// =====================================================================

import java.net.URL;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Date;
import java.util.List;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class VerificateurCertificatHttps {

    private static final int DELAI = 10000;

    public static void main(String[] arguments) throws Exception {
        if (arguments.length < 2) {
            System.out.println("Usage : java VerificateurCertificatHttps.java <adresse> <port>");
            System.out.println("Exemple : java VerificateurCertificatHttps.java 192.168.8.100 28160");
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
        String cible = "https://" + hote + ":" + port + "/api/auth/connexion";
        System.out.println("Cible  : " + cible);
        System.out.println("JDK    : " + System.getProperty("java.home"));
        System.out.println("");

        System.out.println("=== 1. Connexion HTTPS avec la confiance du JDK (comme l'application) ===");
        boolean confiance = true;
        try {
            HttpsURLConnection lien = ouvrir(cible, null);
            int statut = lien.getResponseCode();
            System.out.println("STATUT=" + statut
                    + "  -> le service a repondu : TLS negocie et certificat approuve par ce JDK.");
            System.out.println("(404 est normal sur cette adresse en GET : elle n'accepte que POST.)");
        } catch (SSLHandshakeException ex) {
            confiance = false;
            System.out.println("[ECHEC CONFIANCE] " + ex.getMessage());
            System.out.println("  -> soit l'autorite privee n'est pas dans le magasin de ce JDK,");
            System.out.println("     soit l'adresse " + hote + " n'est pas couverte par le certificat.");
            System.out.println("  -> executer postes\\approuver-autorite-client.bat avec ce meme JDK.");
        } catch (Exception ex) {
            confiance = false;
            System.out.println("[ECHEC] " + ex.getClass().getSimpleName() + " : " + ex.getMessage());
            System.out.println("  -> aucun certificat obtenu : verifier le port affiche par la fenetre");
            System.out.println("     d'authentification, le pare-feu Windows et l'adresse IP du serveur.");
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
                        System.out.println("  -> le serveur a change de reseau/adresse : laisser le serveur");
                        System.out.println("     d'authentification ouvert 15 s pour qu'il renouvelle son");
                        System.out.println("     certificat, ou utiliser l'adresse listee ci-dessus.");
                    }
                }
            }
        } catch (Exception ex) {
            System.out.println("[ECHEC] lecture du certificat impossible : " + ex);
        }

        System.out.println("");
        System.out.println("=== 3. Conclusion ===");
        if (confiance) {
            System.out.println("Certificat accessible et approuve par ce JDK sur " + hote + ":" + port + ".");
        } else {
            System.out.println("Connexion non etablie : relire les messages ci-dessus avant tout changement.");
            System.out.println("Ne jamais desactiver la verification TLS pour 'que ca marche'.");
        }
    }

    private static HttpsURLConnection ouvrir(String cible, SSLContext contexte) throws Exception {
        HttpsURLConnection lien = (HttpsURLConnection) new URL(cible).openConnection();
        if (contexte != null) lien.setSSLSocketFactory(contexte.getSocketFactory());
        lien.setConnectTimeout(DELAI);
        lien.setReadTimeout(DELAI);
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
