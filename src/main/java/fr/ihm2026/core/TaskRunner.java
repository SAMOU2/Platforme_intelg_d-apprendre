package fr.ihm2026.core;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javafx.concurrent.Task;

/** Exécute un travail hors du FX Application Thread (BNF-T3). */
public final class TaskRunner {

    private TaskRunner() {
    }

    public static <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> onSuccess.accept(task.getValue()));
        task.setOnFailed(e -> onFailure.accept(task.getException()));
        Thread thread = new Thread(task, "ihm-task");
        thread.setDaemon(true);
        thread.start();
    }

    /** Variante avec un court délai simulé (v0.1 : pas encore de serveur). */
    public static <T> void runSlow(Callable<T> work, long millis, Consumer<T> onSuccess) {
        run(() -> {
            Thread.sleep(millis);
            return work.call();
        }, onSuccess, error -> onSuccess.accept(null));
    }
}
