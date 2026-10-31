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
        int port = Integer.parseInt(System.getenv().getOrDefault("IHM_AUTH_PORT", "8443"));
        if (port < 1 || port > 65535) {
            throw new IllegalStateException("IHM_AUTH_PORT doit être entre 1 et 65535.");
        }

        char[] motDePasseMagasin = secretMagasin.toCharArray();
        try {
            KeyStore magasin = KeyStore.getInstance(typeMagasin);
            try (var entree = java.nio.file.Files.newInputStream(Path.of(fichierMagasin))) {
                magasin.load(entree, motDePasseMagasin);
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

    public static void main(String[] args) throws Exception {
        try (ServeurAuthentificationHttps serveur = demarrerDepuisEnvironnement()) {
            System.out.println("API d'authentification HTTPS prête sur le port " + serveur.port() + ".");
            Thread.currentThread().join();
        }
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
