package fr.ihm2026.api.service;

import fr.ihm2026.api.mock.MockBackend;
import fr.ihm2026.core.Config;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.ResultatVM;
import java.util.List;

/** Test de validation : questions, exécution du code, soumission. */
public final class TestApi {

    private TestApi() {
    }

    public record Question(String enonce, List<String> propositions, int bonneReponse) {
    }

    public static List<Question> questions() {
        if (Config.USE_MOCK) {
            return MockBackend.questions().stream()
                    .map(q -> new Question(q.enonce(), q.propositions(), q.bonneReponse()))
                    .toList();
        }
        throw new UnsupportedOperationException("GET /niveaux/{rang}/test/questions : v0.3");
    }

    public static MockBackend.ExecutionSimulee executer(String code, Session.Langage langage) {
        if (Config.USE_MOCK) {
            return MockBackend.executer(code, langage);
        }
        throw new UnsupportedOperationException("POST /tentatives/execution : v0.3");
    }

    public static ResultatVM soumettre(int bonnesReponses, int total, int seuil) {
        if (Config.USE_MOCK) {
            return MockBackend.corriger(bonnesReponses, total, seuil);
        }
        throw new UnsupportedOperationException("POST /tentatives : v0.3");
    }
}
