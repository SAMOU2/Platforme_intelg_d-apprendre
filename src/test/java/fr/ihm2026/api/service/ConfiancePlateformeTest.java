package fr.ihm2026.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLHandshakeException;

/**
 * La confiance doit venir de l'autorite privee approuvee sur le JDK, jamais de l'adresse :
 * l'adresse du serveur change a chaque Wi-Fi, l'autorite jamais.
 *
 * Les deux certificats de test (src/test/resources) sont des autorites autosignees sans cle
 * privee, valables un siecle, utilisees uniquement comme points de depart de confiance.
 */
class ConfiancePlateformeTest {

    @Test void seuleLautoriteDeLaPlateformeEstRetenue() throws Exception {
        X509Certificate plateforme = lire("confiance-plateforme-ca.pem");

        KeyStore approuvee = KeyStore.getInstance(KeyStore.getDefaultType());
        approuvee.load(null, null);
        approuvee.setCertificateEntry("plateforme-ca-fixture", plateforme);
        assertTrue(ConfiancePlateforme.depuisMagasin(approuvee).isPresent(),
                "un alias plateforme-ca-* doit fournir un contexte de confiance");

        KeyStore etrangere = KeyStore.getInstance(KeyStore.getDefaultType());
        etrangere.load(null, null);
        etrangere.setCertificateEntry("autre-autorite", plateforme);
        assertTrue(ConfiancePlateforme.depuisMagasin(etrangere).isEmpty(),
                "une autorite sans le prefixe de la plateforme ne doit jamais etre retenue");

        KeyStore vide = KeyStore.getInstance(KeyStore.getDefaultType());
        vide.load(null, null);
        assertTrue(ConfiancePlateforme.depuisMagasin(vide).isEmpty(),
                "sans autorite approuvee, l'appelant garde le comportement strict");
    }

    /**
     * Test de handshake reel (RSA, keytool), couteux : activer IHM_TEST_TLS_RESEAU=1.
     * Il prouve qu'un certificat signe par l'autorite de la plateforme est accepte meme
     * quand le nom demande (localhost) ne figure pas dans ses adresses, et qu'une autorite
     * etrangere est refusee.
     */
    @Test
    @EnabledIfEnvironmentVariable(named = "IHM_TEST_TLS_RESEAU", matches = "1")
    void connexionAccepteeQuelQueSoitLeReseau(@TempDir Path dossier) throws Exception {
        String secret = "fixture-" + UUID.randomUUID();
        Path ca = dossier.resolve("ca.p12");
        String executable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        var commande = new ProcessBuilder(executable, "-cp", System.getProperty("java.class.path"),
                IdentiteTlsReseau.class.getName(), "initialiser");
        commande.environment().put("IHM_TLS_CA_KEYSTORE", ca.toString());
        commande.environment().put("IHM_TLS_CA_PASSWORD", secret);
        commande.redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD);
        Process preparation = commande.start();
        try {
            assertTrue(preparation.waitFor(120, TimeUnit.SECONDS));
            assertEquals(0, preparation.exitValue());
        } finally {
            if (preparation.isAlive()) preparation.destroyForcibly();
        }

        KeyStore racine = KeyStore.getInstance("PKCS12");
        try (var in = Files.newInputStream(ca)) { racine.load(in, secret.toCharArray()); }
        X509Certificate autorite = (X509Certificate) racine.getCertificate("autorite-ihm");
        assertNotNull(autorite);

        AtomicReference<Set<String>> adresses = new AtomicReference<>(Set.of("127.0.0.1"));
        try (var identite = new IdentiteTlsReseau(ca, secret, secret, adresses::get, false)) {
            var serveur = ServeurAuthentificationHttps.ouvrirPort(0);
            serveur.setHttpsConfigurator(
                    new com.sun.net.httpserver.HttpsConfigurator(identite.contexte()));
            serveur.createContext("/", e -> { e.sendResponseHeaders(200, 0); e.getResponseBody().close(); });
            serveur.start();
            int port = serveur.getAddress().getPort();
            try {
                KeyStore approuvee = KeyStore.getInstance(KeyStore.getDefaultType());
                approuvee.load(null, null);
                approuvee.setCertificateEntry("plateforme-ca-fixture", autorite);
                SSLContext confiance = ConfiancePlateforme.depuisMagasin(approuvee).orElseThrow();
                var client = java.net.http.HttpClient.newBuilder()
                        .sslContext(confiance)
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build();
                // « localhost » n'est pas dans les adresses du certificat : c'est exactement le
                // cas d'un changement de Wi-Fi, ou le serveur garde son autorite mais change d'IP.
                assertEquals(200, client.send(java.net.http.HttpRequest
                                .newBuilder(java.net.URI.create("https://localhost:" + port + "/")).build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());

                KeyStore fausse = KeyStore.getInstance(KeyStore.getDefaultType());
                fausse.load(null, null);
                fausse.setCertificateEntry("plateforme-ca-etrangere", lire("confiance-autorite-etrangere.pem"));
                SSLContext etranger = ConfiancePlateforme.depuisMagasin(fausse).orElseThrow();
                var clientEtranger = java.net.http.HttpClient.newBuilder()
                        .sslContext(etranger)
                        .connectTimeout(java.time.Duration.ofSeconds(5))
                        .build();
                assertThrows(SSLHandshakeException.class, () -> clientEtranger.send(java.net.http.HttpRequest
                                .newBuilder(java.net.URI.create("https://localhost:" + port + "/")).build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()));
            } finally {
                serveur.stop(0);
            }
        }
    }

    private static X509Certificate lire(String nom) throws Exception {
        try (InputStream source = ConfiancePlateformeTest.class.getResourceAsStream("/" + nom)) {
            assertNotNull(source, "certificat de test absent : " + nom);
            return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(source);
        }
    }
}
