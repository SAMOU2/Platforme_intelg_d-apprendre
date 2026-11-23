package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.BindException;
import java.nio.file.NoSuchFileException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateExpiredException;
import java.sql.SQLException;
import org.junit.jupiter.api.Test;

class ServeurAuthentificationHttpsTest {
    @Test void portLibreAttribueParLeSystemeEtRepliSiOccupe() throws Exception {
        try(var occupe=new java.net.ServerSocket(0)) {
            var serveur=ServeurAuthentificationHttps.ouvrirPort(occupe.getLocalPort());
            try {
                assertTrue(serveur.getAddress().getPort()>0);
                assertTrue(serveur.getAddress().getPort()!=occupe.getLocalPort());
            } finally {serveur.start();serveur.stop(0);}
        }
        var automatique=ServeurAuthentificationHttps.ouvrirPort(0);
        try {assertTrue(automatique.getAddress().getPort()>0);}
        finally {automatique.start();automatique.stop(0);}
    }

    @Test
    void expliqueLesErreursPostgresSansExposerLeSecret() {
        String message = ServeurAuthentificationHttps.diagnosticDemarrage(
                new SQLException("secret-interdit", "28P01"));
        assertTrue(message.contains("IHM_DB_PASSWORD"));
        assertFalse(message.contains("secret-interdit"));
        assertTrue(ServeurAuthentificationHttps.diagnosticDemarrage(
                new SQLException("", "42P01")).contains("09-inscriptions-et-codes.sql"));
        assertTrue(ServeurAuthentificationHttps.diagnosticDemarrage(
                new SQLException("", "3D000")).contains("inexistante"));
    }

    @Test
    void expliquePortEtCertificat() {
        assertTrue(ServeurAuthentificationHttps.diagnosticDemarrage(
                new BindException()).contains("IHM_AUTH_PORT"));
        assertTrue(ServeurAuthentificationHttps.diagnosticDemarrage(
                new CertificateExpiredException()).contains("renouveler"));
        assertTrue(ServeurAuthentificationHttps.diagnosticDemarrage(
                new NoSuchFileException("secret-interdit")).contains("IHM_TLS_KEYSTORE"));
    }

    @Test
    void retrouveLaCauseDuMauvaisMotDePasseDuMagasin() {
        String message = ServeurAuthentificationHttps.diagnosticDemarrage(
                new java.io.IOException("secret-interdit", new UnrecoverableKeyException()));
        assertTrue(message.contains("IHM_TLS_PASSWORD"));
        assertFalse(message.contains("secret-interdit"));
    }

    @Test
    void expliqueLaCauseProfondeDunEchecSmtp() {
        // Jakarta Mail enveloppe la vraie cause : c'est elle qui dit pourquoi aucun code n'est parti.
        var racine = new java.net.ConnectException("Connection refused");
        var enveloppe = new IllegalStateException("Impossible d'envoyer l'e-mail de notification", racine);
        String message = AuthentificationApiHttps.messageExplicite(enveloppe);
        assertTrue(message.contains("Impossible d'envoyer"), message);
        assertTrue(message.contains("Connection refused"), message);
    }

    @Test
    void expliqueLeRefusDauthentificationSmtp() {
        String message = AuthentificationApiHttps.messageExplicite(
                new IllegalStateException("535 Authentication failed"));
        assertTrue(message.contains("535"), message);
    }
}
