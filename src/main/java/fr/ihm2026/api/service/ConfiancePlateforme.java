package fr.ihm2026.api.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/**
 * Confiance de l'application envers le service d'authentification de la plateforme.
 *
 * <p>Sur un réseau local sans DNS, l'adresse du serveur change à chaque Wi-Fi. Exiger que
 * l'adresse figure dans le certificat (le contrôle du nom du Web public) ferait échouer la
 * connexion après chaque changement de réseau, ce qui est exactement le cas d'usage de la
 * plateforme. La confiance repose donc sur l'autorité privée approuvée à la main sur chaque
 * JDK (postes\approuver-autorite-client.bat) : la chaîne du certificat doit être validée,
 * mais le nom ou l'adresse du serveur n'est plus exigé.</p>
 *
 * <p>Ordre de validation : d'abord les seules entrées {@code plateforme-ca-*} du magasin du
 * JDK, puis, à défaut, le magasin complet (compatibilité avec un certificat déjà approuvé
 * avant la mise en place de l'autorité). La vérification TLS n'est jamais désactivée : la
 * chaîne est toujours validée, seul le contrôle du nom est écarté. Si aucune autorité de la
 * plateforme n'est approuvée, l'appelant garde le comportement strict habituel.</p>
 */
public final class ConfiancePlateforme {

    /** Préfixe d'alias posé par postes\approuver-autorite-client.ps1. */
    static final String PREFIXE_ALIAS = "plateforme-ca-";
    private static final String MOT_DE_PASSE_CACERTS = "changeit";
    private static final List<String> TYPES_MAGASIN = List.of("JKS", "PKCS12");
    private static final Logger LOGGER = Logger.getLogger(ConfiancePlateforme.class.getName());

    private ConfiancePlateforme() { }

    /** Contexte validant la chaîne sans exiger le nom ; vide si aucune confiance n'est lisible. */
    public static Optional<SSLContext> depuisJdk() {
        List<X509TrustManager> gestionnaires = new ArrayList<>();
        try {
            Optional<KeyStore> magasin = magasinDuJdk();
            if (magasin.isPresent()) {
                gestionnaire(magasin.get()).ifPresent(gestionnaires::add);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Autorité de la plateforme non lisible ("
                    + ex.getClass().getSimpleName() + ").", ex);
        }
        gestionnaireParDefaut().ifPresent(gestionnaires::add);
        if (gestionnaires.isEmpty()) return Optional.empty();
        try {
            SSLContext contexte = SSLContext.getInstance("TLS");
            contexte.init(null, new TrustManager[] { new Composite(gestionnaires) }, null);
            return Optional.of(contexte);
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Contexte de confiance indisponible : contrôle strict conservé.", ex);
            return Optional.empty();
        }
    }

    /** Extrait les entrées {@code plateforme-ca-*} d'un magasin et en fait un contexte dédié. */
    static Optional<SSLContext> depuisMagasin(KeyStore magasin) throws Exception {
        Optional<X509TrustManager> gestionnaire = gestionnaire(magasin);
        if (gestionnaire.isEmpty()) return Optional.empty();
        SSLContext contexte = SSLContext.getInstance("TLS");
        contexte.init(null, new TrustManager[] { new SansControleNom(gestionnaire.get()) }, null);
        return Optional.of(contexte);
    }

    /** Gestionnaire limité aux autorités de la plateforme ; vide si le magasin n'en a aucune. */
    private static Optional<X509TrustManager> gestionnaire(KeyStore magasin) throws Exception {
        KeyStore plateforme = KeyStore.getInstance(KeyStore.getDefaultType());
        plateforme.load(null, null);
        int retenues = 0;
        for (String alias : Collections.list(magasin.aliases())) {
            if (alias == null || !alias.toLowerCase(Locale.ROOT).startsWith(PREFIXE_ALIAS)) continue;
            if (!magasin.isCertificateEntry(alias)) continue;
            plateforme.setCertificateEntry(alias, magasin.getCertificate(alias));
            retenues++;
        }
        if (retenues == 0) return Optional.empty();
        TrustManagerFactory usine =
                TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        usine.init(plateforme);
        for (TrustManager candidat : usine.getTrustManagers()) {
            if (candidat instanceof X509TrustManager x509) {
                LOGGER.info("Autorité(s) de la plateforme approuvée(s) dans ce JDK : " + retenues
                        + ". Le certificat est accepté quelle que soit l'adresse du réseau.");
                return Optional.of(x509);
            }
        }
        return Optional.empty();
    }

    /** Magasin complet du JDK : pour rester compatible avec une confiance déjà installée. */
    private static Optional<X509TrustManager> gestionnaireParDefaut() {
        try {
            TrustManagerFactory usine =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            usine.init((KeyStore) null);
            for (TrustManager candidat : usine.getTrustManagers()) {
                if (candidat instanceof X509TrustManager x509) return Optional.of(x509);
            }
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING, "Magasin de confiance du JDK illisible.", ex);
        }
        return Optional.empty();
    }

    /** Magasin du JDK : propriété javax.net.ssl.trustStore si elle est définie, sinon cacerts. */
    static Optional<KeyStore> magasinDuJdk() throws Exception {
        String chemin = System.getProperty("javax.net.ssl.trustStore");
        String motDePasse = System.getProperty("javax.net.ssl.trustStorePassword");
        Path fichier;
        char[] secret;
        if (chemin != null && !chemin.isBlank()) {
            fichier = Path.of(chemin);
            secret = motDePasse == null ? null : motDePasse.toCharArray();
        } else {
            fichier = Path.of(System.getProperty("java.home"), "lib", "security", "cacerts");
            secret = MOT_DE_PASSE_CACERTS.toCharArray();
        }
        if (!Files.isReadable(fichier)) return Optional.empty();
        KeyStore magasin = charger(fichier, secret);
        if (secret != null) Arrays.fill(secret, '\0');
        return Optional.ofNullable(magasin);
    }

    private static KeyStore charger(Path fichier, char[] secret) {
        for (String type : TYPES_MAGASIN) {
            try {
                KeyStore magasin = KeyStore.getInstance(type);
                try (var entree = Files.newInputStream(fichier)) {
                    magasin.load(entree, secret);
                }
                return magasin;
            } catch (Exception ex) {
                // type ou mot de passe inadapté : on essaie le suivant.
            }
        }
        return null;
    }

    /**
     * Valide la chaîne contre l'autorité de la plateforme, sans exiger que l'adresse figure
     * dans le certificat : l'adresse change à chaque Wi-Fi, l'autorité jamais.
     */
    private static final class SansControleNom extends X509ExtendedTrustManager {

        private final X509TrustManager delegue;

        SansControleNom(X509TrustManager delegue) { this.delegue = delegue; }

        @Override public void checkServerTrusted(X509Certificate[] chaine, String type) throws CertificateException {
            delegue.checkServerTrusted(chaine, type);
        }

        @Override public void checkServerTrusted(X509Certificate[] chaine, String type, java.net.Socket socket)
                throws CertificateException {
            delegue.checkServerTrusted(chaine, type);
        }

        @Override public void checkServerTrusted(X509Certificate[] chaine, String type, javax.net.ssl.SSLEngine moteur)
                throws CertificateException {
            delegue.checkServerTrusted(chaine, type);
        }

        @Override public void checkClientTrusted(X509Certificate[] chaine, String type) throws CertificateException {
            throw new CertificateException("Aucun certificat client n'est accepté.");
        }

        @Override public void checkClientTrusted(X509Certificate[] chaine, String type, java.net.Socket socket)
                throws CertificateException {
            throw new CertificateException("Aucun certificat client n'est accepté.");
        }

        @Override public void checkClientTrusted(X509Certificate[] chaine, String type, javax.net.ssl.SSLEngine moteur)
                throws CertificateException {
            throw new CertificateException("Aucun certificat client n'est accepté.");
        }

        @Override public X509Certificate[] getAcceptedIssuers() { return delegue.getAcceptedIssuers(); }
    }

    /** Autorité de la plateforme d'abord, puis magasin complet : jamais d'impasse silencieuse. */
    private static final class Composite extends X509ExtendedTrustManager {

        private final List<X509TrustManager> gestionnaires;

        Composite(List<X509TrustManager> gestionnaires) { this.gestionnaires = List.copyOf(gestionnaires); }

        @Override public void checkServerTrusted(X509Certificate[] chaine, String type) throws CertificateException {
            CertificateException premiere = null;
            for (X509TrustManager gestionnaire : gestionnaires) {
                try {
                    gestionnaire.checkServerTrusted(chaine, type);
                    return;
                } catch (CertificateException ex) {
                    if (premiere == null) premiere = ex;
                }
            }
            throw premiere;
        }

        @Override public void checkServerTrusted(X509Certificate[] chaine, String type, java.net.Socket socket)
                throws CertificateException {
            checkServerTrusted(chaine, type);
        }

        @Override public void checkServerTrusted(X509Certificate[] chaine, String type, javax.net.ssl.SSLEngine moteur)
                throws CertificateException {
            checkServerTrusted(chaine, type);
        }

        @Override public void checkClientTrusted(X509Certificate[] chaine, String type) throws CertificateException {
            throw new CertificateException("Aucun certificat client n'est accepté.");
        }

        @Override public void checkClientTrusted(X509Certificate[] chaine, String type, java.net.Socket socket)
                throws CertificateException {
            throw new CertificateException("Aucun certificat client n'est accepté.");
        }

        @Override public void checkClientTrusted(X509Certificate[] chaine, String type, javax.net.ssl.SSLEngine moteur)
                throws CertificateException {
            throw new CertificateException("Aucun certificat client n'est accepté.");
        }

        @Override public X509Certificate[] getAcceptedIssuers() {
            List<X509Certificate> acceptees = new ArrayList<>();
            for (X509TrustManager gestionnaire : gestionnaires) {
                acceptees.addAll(Arrays.asList(gestionnaire.getAcceptedIssuers()));
            }
            return acceptees.toArray(new X509Certificate[0]);
        }
    }
}
