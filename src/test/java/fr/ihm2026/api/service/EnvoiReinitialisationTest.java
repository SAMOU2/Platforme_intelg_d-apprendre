package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.SQLException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(10)
class EnvoiReinitialisationTest {
    @Test
    void attendLaFinDeLenvoiAvantDeRepondre() throws Exception {
        try (var envoi = new EnvoiReinitialisation(); var client = Executors.newSingleThreadExecutor()) {
            var commence = new CountDownLatch(1);
            var terminer = new CountDownLatch(1);
            var envoye = new AtomicBoolean();
            var reponse = client.submit(() -> {
                envoi.attendre(() -> {
                    commence.countDown();
                    terminer.await();
                    envoye.set(true);
                    return null;
                });
                return envoye.get();
            });
            try {
                assertTrue(commence.await(2, TimeUnit.SECONDS));
                assertFalse(reponse.isDone(), "Pas de succès tant que le serveur SMTP n'a pas répondu");
            } finally {
                terminer.countDown();
            }
            assertTrue(reponse.get(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void refusSmtpRemonteAuLieuDeDisparaitreDansUnThread() {
        try (var envoi = new EnvoiReinitialisation()) {
            var cause = new IllegalStateException("535 Authentication failed");
            assertSame(cause, assertThrows(IllegalStateException.class,
                    () -> envoi.attendre(() -> { throw cause; })));
        }
    }

    @Test
    void migrationAbsenteRemonteAvecSonSqlState() {
        try (var envoi = new EnvoiReinitialisation()) {
            var cause = new SQLException("table absente", "42P01");
            var erreur = assertThrows(SQLException.class,
                    () -> envoi.attendre(() -> { throw cause; }));
            assertSame(cause, erreur);
            assertEquals("42P01", erreur.getSQLState());
        }
    }

    @Test
    void compteNonEligibleSansEnvoiResteUneDemandeAcceptee() {
        try (var envoi = new EnvoiReinitialisation()) {
            assertDoesNotThrow(() -> envoi.attendre(() -> null));
        }
    }

    @Test
    void configurationAbsenteEstDetecteeAvantLaRechercheDuCompte() {
        var smtp = new ServiceEmail(Map.of());
        var erreur = assertThrows(IllegalStateException.class, smtp::verifierConfiguration);
        assertTrue(erreur.getMessage().contains("IHM_SMTP_HOST"));
        assertTrue(erreur.getMessage().contains("IHM_SMTP_FROM"));
    }

    @Test
    void delaiDepasseNestJamaisUnSuccesEtInterromptLenvoi() throws Exception {
        var interrompu = new CountDownLatch(1);
        try (var envoi = new EnvoiReinitialisation(1, 1, Duration.ofMillis(200))) {
            var erreur = assertThrows(IllegalStateException.class, () -> envoi.attendre(() -> {
                try {
                    new CountDownLatch(1).await();
                } catch (InterruptedException ex) {
                    interrompu.countDown();
                    throw ex;
                }
                return null;
            }));
            assertTrue(erreur.getMessage().contains("n'a pas pu être confirmé"));
            assertTrue(interrompu.await(2, TimeUnit.SECONDS));
        }
    }

    @Test
    void refusDeLaFileNestJamaisUnSucces() {
        var envoi = new EnvoiReinitialisation();
        envoi.close();
        assertThrows(RejectedExecutionException.class, () -> envoi.attendre(() -> null));
    }

    @Test
    void interruptionEstConserveeEtNestPasUnSucces() {
        try (var envoi = new EnvoiReinitialisation()) {
            Thread.currentThread().interrupt();
            try {
                assertThrows(IllegalStateException.class, () -> envoi.attendre(() -> {
                    new CountDownLatch(1).await();
                    return null;
                }));
                assertTrue(Thread.currentThread().isInterrupted());
            } finally {
                Thread.interrupted();
            }
        }
    }
}
