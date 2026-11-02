package fr.ihm2026.api.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.security.KeyStore;
import javax.net.ssl.*;

/** Test crypto reel, couteux (keytool/RSA) : activer IHM_TEST_TLS_RESEAU=1. Aucun secret reel. */
@EnabledIfEnvironmentVariable(named="IHM_TEST_TLS_RESEAU",matches="1")
class IdentiteTlsReseauTest {
    @Test void renouvellementEtVerificationHttps(@TempDir Path dossier) throws Exception {
        String secret="fixture-"+UUID.randomUUID();Path ca=dossier.resolve("ca.p12");
        String executableJava=Path.of(System.getProperty("java.home"),"bin",System.getProperty("os.name").startsWith("Windows")?"java.exe":"java").toString();
        var commande=new ProcessBuilder(executableJava,"-cp",System.getProperty("java.class.path"),IdentiteTlsReseau.class.getName(),"initialiser");
        commande.environment().put("IHM_TLS_CA_KEYSTORE",ca.toString());commande.environment().put("IHM_TLS_CA_PASSWORD",secret);
        commande.redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD);
        Process preparation=commande.start();
        try {assertTrue(preparation.waitFor(120,TimeUnit.SECONDS));assertEquals(0,preparation.exitValue());}
        finally {if(preparation.isAlive())preparation.destroyForcibly();}
        var ips=new AtomicReference<Set<String>>(Set.of("127.0.0.1"));
        try(var identite=new IdentiteTlsReseau(ca,secret,secret,ips::get,false)) {
            var avant=identite.certificatCourant();assertEquals(-1,avant.getBasicConstraints());
            KeyStore racine=KeyStore.getInstance("PKCS12");
            try(var in=Files.newInputStream(ca)){racine.load(in,secret.toCharArray());}
            var confiance=KeyStore.getInstance(KeyStore.getDefaultType());confiance.load(null,null);
            confiance.setCertificateEntry("ca",racine.getCertificate("autorite-ihm"));
            var fabrique=TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());fabrique.init(confiance);
            var contexte=SSLContext.getInstance("TLS");contexte.init(null,fabrique.getTrustManagers(),null);
            var serveur=ServeurAuthentificationHttps.ouvrirPort(0);
            serveur.setHttpsConfigurator(new com.sun.net.httpserver.HttpsConfigurator(identite.contexte()));
            serveur.createContext("/",e -> {e.sendResponseHeaders(200,0);e.getResponseBody().close();});serveur.start();
            try {
                var client=java.net.http.HttpClient.newBuilder().sslContext(contexte).connectTimeout(java.time.Duration.ofSeconds(5)).build();
                int port=serveur.getAddress().getPort();
                assertEquals(200,client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://127.0.0.1:"+port+"/")).build(),java.net.http.HttpResponse.BodyHandlers.discarding()).statusCode());
                assertThrows(SSLHandshakeException.class,() -> client.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create("https://localhost:"+port+"/")).build(),java.net.http.HttpResponse.BodyHandlers.discarding()));
                ips.set(Set.of("127.0.0.1","192.0.2.15"));identite.renouvelerSiNecessaire();
                var apres=identite.certificatCourant();assertNotEquals(avant.getSerialNumber(),apres.getSerialNumber());
                assertTrue(apres.getSubjectAlternativeNames().stream().anyMatch(s -> s.get(1).equals("192.0.2.15")));
                apres.verify(racine.getCertificate("autorite-ihm").getPublicKey());
                ips.set(Set.of());assertThrows(IllegalStateException.class,identite::renouvelerSiNecessaire);
                assertEquals(apres.getSerialNumber(),identite.certificatCourant().getSerialNumber());
            } finally {serveur.stop(0);}
        }
        try(var fichiers=Files.list(dossier)){assertFalse(fichiers.anyMatch(p -> p.getFileName().toString().startsWith("certificat-en-cours-")));}
    }
}
