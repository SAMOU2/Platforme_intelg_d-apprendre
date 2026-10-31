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
