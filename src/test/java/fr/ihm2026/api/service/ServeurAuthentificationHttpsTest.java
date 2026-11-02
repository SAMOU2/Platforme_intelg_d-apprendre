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
}
