package fr.ihm2026.api.service;

import java.sql.SQLException;
import java.time.Duration;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Attend le résultat DB + SMTP, sans transformer un échec en faux succès HTTP. */
final class EnvoiReinitialisation implements AutoCloseable {
    private final ThreadPoolExecutor traitements;
    private final Duration attente;

    EnvoiReinitialisation() {
        this(2, 100, Duration.ofSeconds(90));
    }

    // Délais et capacité réduits pour tester aussi la saturation et les envois lents.
    EnvoiReinitialisation(int paralleles, int capacite, Duration attente) {
        this.attente = attente;
        traitements = new ThreadPoolExecutor(paralleles, paralleles, 0L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(capacite), r -> {
                    Thread t = new Thread(r, "reinitialisation-email");
                    t.setDaemon(true);
                    return t;
                });
    }

    void attendre(Callable<Void> envoi) throws SQLException {
        var resultat = traitements.submit(envoi);
        try {
            resultat.get(attente.toMillis(), TimeUnit.MILLISECONDS);
        } catch (ExecutionException ex) {
            if (ex.getCause() instanceof SQLException sql) throw sql;
            if (ex.getCause() instanceof RuntimeException cause) throw cause;
            throw new IllegalStateException("Échec du traitement de réinitialisation.", ex.getCause());
        } catch (TimeoutException ex) {
            resultat.cancel(true);
            traitements.purge();
            throw new IllegalStateException("L'envoi du code n'a pas pu être confirmé dans le délai prévu. "
                    + "Vérifiez votre boîte mail avant de redemander un code ; sur PC1, lancer postes\\tester-smtp.cmd.", ex);
        } catch (InterruptedException ex) {
            resultat.cancel(true);
            traitements.purge();
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Demande de réinitialisation interrompue ; envoi non confirmé.", ex);
        }
    }

    @Override
    public void close() {
        traitements.shutdownNow();
    }
}
