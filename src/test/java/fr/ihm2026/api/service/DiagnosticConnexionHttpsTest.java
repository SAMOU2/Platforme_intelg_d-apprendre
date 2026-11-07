package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import javax.net.ssl.SSLHandshakeException;
import org.junit.jupiter.api.Test;

class DiagnosticConnexionHttpsTest {
    @Test void distingueConnexionEtReponseTardive() {
        assertTrue(message(new HttpConnectTimeoutException("secret")).contains("Délai de connexion"));
        assertTrue(message(new HttpTimeoutException("secret")).contains("n'a pas répondu à temps"));
        assertTrue(message(new IOException("secret", new ConnectException())).contains("refusée"));
        assertFalse(message(new IOException("secret")).contains("secret"));
    }

    @Test void identifieTlsMemeLorsqueEnveloppeDansUneIOException() {
        assertTrue(message(new IOException("secret", new SSLHandshakeException("secret")))
                .contains("Échec TLS"));
    }

    private static String message(Exception ex) {
        return ClientAuthentificationHttps.inaccessible("192.168.8.100", 8292, ex).getMessage();
    }
}
