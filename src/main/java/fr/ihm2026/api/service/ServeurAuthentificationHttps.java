package fr.ihm2026.api.service;

import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import fr.ihm2026.api.persistence.PostgresAuthentificationService;
import fr.ihm2026.api.persistence.PostgresCompteRepository;
import fr.ihm2026.api.persistence.PostgresConfiguration;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;

/** Point d'entrée explicite du service d'authentification TLS ; aucun repli vers HTTP. */
public final class ServeurAuthentificationHttps implements AutoCloseable {

    private final HttpsServer serveur;
    private final ExecutorService executants;

    private ServeurAuthentificationHttps(int port, SSLContext tls,
            PostgresAuthentificationService authentification,
            PostgresCompteRepository comptes) throws IOException {
        serveur = HttpsServer.create(new InetSocketAddress(port), 0);
        serveur.setHttpsConfigurator(new HttpsConfigurator(tls));
        serveur.createContext("/api/", new AuthentificationApiHttps(authentification, comptes));
        executants = Executors.newFixedThreadPool(4);
        serveur.setExecutor(executants);
        serveur.start();
    }

    public static ServeurAuthentificationHttps demarrerDepuisEnvironnement() throws Exception {
        String fichierMagasin = variableObligatoire("IHM_TLS_KEYSTORE");
        String secretMagasin = variableObligatoire("IHM_TLS_PASSWORD");
        String typeMagasin = System.getenv().getOrDefault("IHM_TLS_KEYSTORE_TYPE", "PKCS12");
        int port;
        try {
            port = Integer.parseInt(System.getenv().getOrDefault("IHM_AUTH_PORT", "8443").strip());
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("IHM_AUTH_PORT doit être un nombre entre 1 et 65535.");
        }
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("IHM_AUTH_PORT doit être entre 1 et 65535.");
        }

        char[] motDePasseMagasin = secretMagasin.toCharArray();
        try {
            KeyStore magasin = KeyStore.getInstance(typeMagasin);
            try (var entree = java.nio.file.Files.newInputStream(Path.of(fichierMagasin))) {
                magasin.load(entree, motDePasseMagasin);
            }
            boolean cleServeur = false;
            var alias = magasin.aliases();
            while (alias.hasMoreElements()) {
                String nom = alias.nextElement();
                if (magasin.isKeyEntry(nom)) {
                    var certificat = magasin.getCertificate(nom);
                    if (certificat instanceof java.security.cert.X509Certificate x509) {
                        x509.checkValidity();
                        cleServeur = true;
                    }
                }
            }
            if (!cleServeur) {
                throw new IllegalStateException("IHM_TLS_KEYSTORE doit contenir une clé privée "
                        + "et son certificat serveur, pas seulement un certificat de confiance.");
            }
            KeyManagerFactory gestionnaireCles = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            gestionnaireCles.init(magasin, motDePasseMagasin);
            SSLContext tls = SSLContext.getInstance("TLS");
            tls.init(gestionnaireCles.getKeyManagers(), null, null);
            PostgresConfiguration configuration = PostgresConfiguration.depuisEnvironnement();
            PostgresAuthentificationService service =
                    new PostgresAuthentificationService(configuration);
            PostgresCompteRepository comptes = new PostgresCompteRepository(configuration);
            comptes.assurerAdministrateurInitialDepuisEnvironnement();
            return new ServeurAuthentificationHttps(port, tls, service, comptes);
        } finally {
            Arrays.fill(motDePasseMagasin, '\0');
        }
    }

    public int port() {
        return serveur.getAddress().getPort();
    }

    public static void main(String[] args) {
        try (ServeurAuthentificationHttps serveur = demarrerDepuisEnvironnement()) {
            System.out.println("API d'authentification HTTPS prête sur le port " + serveur.port() + ".");
            Thread.currentThread().join();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (Exception ex) {
            // Ne pas afficher les paramètres JDBC, secrets ou détails internes dans la console.
            System.err.println("[ERREUR] " + diagnosticDemarrage(ex));
            System.exit(1);
        }
    }

    static String diagnosticDemarrage(Exception erreur) {
        for (Throwable cause = erreur; cause != null; cause = cause.getCause()) {
            if (cause instanceof java.net.BindException) {
                return "Le port HTTPS est déjà utilisé ou interdit. Arrêter l'autre service "
                        + "ou modifier IHM_AUTH_PORT (et ihm.auth.port sur les clients).";
            }
            if (cause instanceof java.security.cert.CertificateExpiredException
                    || cause instanceof java.security.cert.CertificateNotYetValidException) {
                return "Le certificat serveur est expiré ou pas encore valide. Vérifier l'horloge "
                        + "et renouveler le certificat dans IHM_TLS_KEYSTORE.";
            }
            if (cause instanceof java.nio.file.NoSuchFileException) {
                return "Magasin TLS introuvable. Vérifier le chemin IHM_TLS_KEYSTORE "
                        + "depuis le dossier racine du projet, ou utiliser un chemin absolu.";
            }
            if (cause instanceof java.nio.file.AccessDeniedException) {
                return "Le compte serveur ne peut pas lire IHM_TLS_KEYSTORE. Vérifier ses permissions.";
            }
            if (cause instanceof java.security.UnrecoverableKeyException) {
                return "Mot de passe du magasin ou de la clé privée incorrect. Vérifier IHM_TLS_PASSWORD.";
            }
            if (cause instanceof java.sql.SQLException sql) {
                String etat = sql.getSQLState();
                if ("28P01".equals(etat) || "28000".equals(etat)) {
                    return "Connexion PostgreSQL refusée. Vérifier IHM_DB_USER et IHM_DB_PASSWORD.";
                }
                if ("3D000".equals(etat)) {
                    return "Base PostgreSQL inexistante. Créer la base indiquée par IHM_DB_URL.";
                }
                if ("42P01".equals(etat) || "42703".equals(etat) || "42883".equals(etat)) {
                    return "Schéma d'authentification incomplet. Appliquer dans l'ordre les scripts "
                            + "db/01-schema.sql, 02-seed.sql (démo seulement), 07-auth.sql, "
                            + "08-auth-sessions.sql et 09-inscriptions-et-codes.sql. "
                            + "Un volume Docker existant n'applique pas les nouveaux scripts automatiquement.";
                }
                return "PostgreSQL indisponible ou mal configuré. Vérifier le service, IHM_DB_URL "
                        + "et les migrations (voir db/README-db.md).";
            }
        }
        if (erreur instanceof IllegalStateException || erreur instanceof IllegalArgumentException) {
            return erreur.getMessage() == null ? "Configuration serveur invalide." : erreur.getMessage();
        }
        return "Impossible de démarrer le service HTTPS. Vérifier le format du magasin "
                + "IHM_TLS_KEYSTORE, son type IHM_TLS_KEYSTORE_TYPE et IHM_TLS_PASSWORD.";
    }

    @Override
    public void close() {
        serveur.stop(0);
        executants.shutdownNow();
    }

    private static String variableObligatoire(String nom) {
        String valeur = System.getenv(nom);
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalStateException("La variable d'environnement " + nom + " est obligatoire.");
        }
        return valeur;
    }
}
