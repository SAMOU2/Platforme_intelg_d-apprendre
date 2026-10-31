import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.SortieIa;
import fr.ihm2026.api.service.AideIaApi;
import fr.ihm2026.core.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Étape 5 — preuve de la sortie, reproductible.
 *
 * <p>Rejoue dix questions du jeu gelé de l'étape 2 — la sélection est la même que celle de
 * {@code FormatSortieTest.dixQuestionsDuJeuGele} — et écrit le résultat brut, JSON compris, dans
 * {@code ia/evaluation/preuve-10-questions.json}. C'est ce fichier que le document de l'étape
 * reprend : aucune ligne du tableau de preuve n'est recopiée à la main.</p>
 *
 * <pre>
 * ./mvnw -B -o -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * java -cp "target/classes:$(cat /tmp/cp.txt)" ia/preuve_sortie.java
 * </pre>
 */
public class PreuveSortie {
    public static void main(String[] args) throws Exception {
        // Le modèle est fixé explicitement : cette preuve décrit le prompt system-v2 (étape 5), et
        // non le modèle local (étape 6). Le modèle par défaut de l'application a changé depuis ;
        // la preuve, elle, doit rester celle de l'étape qu'elle documente.
        AideIaApi.utiliserModele(new fr.ihm2026.api.ia.ModeleSimule());
        ObjectMapper m = new ObjectMapper();
        Path dossier = Path.of("ia/evaluation");
        Files.createDirectories(dossier);
        List<JsonNode> jeu = new ArrayList<>();
        for (String l : Files.readAllLines(Path.of("ia/evaluation/jeu-de-test-v1-60.jsonl"), StandardCharsets.UTF_8)) {
            if (!l.isBlank()) jeu.add(m.readTree(l));
        }
        List<String> notions = List.of("VAR", "COND", "BOUCLE", "FONC", "COLL", "ERREUR");
        List<String> langages = List.of("python", "java", "cpp");
        List<JsonNode> sel = new ArrayList<>();
        for (int i = 0; i < notions.size(); i++) {
            int voulu = i < 4 ? 2 : 1, pris = 0;
            for (int t = 0; t < 3 && pris < voulu; t++) {
                String lang = langages.get((i + t) % 3);
                for (JsonNode c : jeu) {
                    if (notions.get(i).equals(c.path("notion").asText()) && lang.equals(c.path("langage").asText())) {
                        sel.add(c); pris++; break;
                    }
                }
            }
        }
        ArrayNode sortie = m.createArrayNode();
        for (JsonNode c : sel) {
            Session.Langage l = switch (c.path("langage").asText()) {
                case "java" -> Session.Langage.JAVA; case "cpp" -> Session.Langage.CPP;
                default -> Session.Langage.PYTHON; };
            String json = AideIaApi.demanderJson(new ContexteIa(3, l, c.path("question").asText(), ""));
            SortieIa s = SortieIa.lire(json).orElseThrow();
            ObjectNode o = sortie.addObject();
            o.put("id", c.path("id").asText());
            o.put("langage", c.path("langage").asText());
            o.put("notion", c.path("notion").asText());
            o.put("question", c.path("question").asText());
            o.put("sectionAttendue", c.path("section").asText());
            o.put("citationObtenue", s.citations().isEmpty() ? "" : s.citations().get(0).section());
            o.put("refus", s.refus().name());
            o.put("juste", s.citations().stream().anyMatch(x -> x.section().equals(c.path("section").asText())));
            o.put("json", json);
        }
        Files.writeString(dossier.resolve("preuve-10-questions.json"),
                m.writerWithDefaultPrettyPrinter().writeValueAsString(sortie), StandardCharsets.UTF_8);
        System.out.println("10 questions : " + sortie.size() + " · justes : "
                + java.util.stream.StreamSupport.stream(sortie.spliterator(), false).filter(x -> x.path("juste").asBoolean()).count());
    }
}
