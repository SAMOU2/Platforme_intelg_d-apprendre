package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.ModeleSimule;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.PolitiqueModele;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.api.ia.SortieIa;
import fr.ihm2026.api.service.AideIaApi;
import fr.ihm2026.core.Session;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Étape 5 — prompts et format de sortie.
 *
 * <p>Les deux promesses de cette étape sont testables, donc testées : le prompt livré est bien
 * celui de la version journalisée et il emploie le vocabulaire du contrat de refus mot pour mot ;
 * la sortie du modèle est validée par un format unique, et dix questions du jeu gelé de l'étape 2
 * produisent un JSON valide avec une citation juste.</p>
 */
class FormatSortieTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path PROMPT_MAITRE = Path.of("ia/prompts/" + ModeleSimule.VERSION_PROMPT + ".md");
    private static final Path JEU = Path.of("ia/evaluation/jeu-de-test-v1-60.jsonl");

    private static String ressource(String chemin) throws Exception {
        try (InputStream in = FormatSortieTest.class.getResourceAsStream(chemin)) {
            assertNotNull(in, "Ressource absente du classpath : " + chemin);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String prompt() throws Exception {
        return ressource("/fr/ihm2026/ia/" + ModeleSimule.VERSION_PROMPT + ".md");
    }

    @BeforeEach
    void remiseAZero() {
        PolitiqueModele.reinitialiser();
        AideIaApi.reinitialiserQuota();
        AideIaApi.utiliserModele(new ModeleSimule());
    }

    // ------------------------------------------------------------------ le prompt
    @Test
    @DisplayName("Le prompt chargé est celui de la version journalisée, et c'est le fichier maître")
    void promptEstCeluiDeLaVersionCourante() throws Exception {
        String prompt = prompt();
        assertTrue(prompt.startsWith("# Prompt système"),
                "Le prompt du classpath n'est pas celui de ia/prompts : " + prompt.substring(0, 40));
        assertTrue(prompt.contains(ModeleSimule.VERSION_PROMPT),
                "Le prompt ne porte pas la version " + ModeleSimule.VERSION_PROMPT);
        // Le pom copie ia/prompts sur le classpath : les deux doivent être identiques, sinon
        // ce n'est pas le fichier relu qui a été livré.
        assertEquals(Files.readString(PROMPT_MAITRE, StandardCharsets.UTF_8), prompt,
                "Le prompt livré a dérivé du fichier maître ia/prompts/");
    }

    @Test
    @DisplayName("Le prompt emploie les huit libellés du contrat de refus, mot pour mot")
    void promptReprendLeVocabulaireDuContrat() throws Exception {
        String prompt = prompt();
        for (MotifRefus motif : MotifRefus.values()) {
            if (!motif.estRefus()) {
                continue;
            }
            String libelle = motif.message();
            assertFalse(libelle.isBlank(), "Libellé vide pour " + motif);
            assertTrue(prompt.contains(libelle),
                    "Le prompt ne reprend pas le libellé du contrat pour " + motif
                            + " (« " + libelle + " ») — il divergerait de l'interface");
        }
    }

    @Test
    @DisplayName("Le prompt porte les interdits, la langue et l'obligation de citer")
    void promptImposeLesRegles() throws Exception {
        String prompt = prompt().toLowerCase();
        assertTrue(prompt.contains("ne notes pas"), "Interdit de notation absent");
        assertTrue(prompt.contains("ne valides pas"), "Interdit de validation absent");
        assertTrue(prompt.contains("solution complète"), "Interdit de solution absente");
        assertTrue(prompt.contains("en français"), "La langue de réponse n'est pas imposée");
        assertTrue(prompt.contains("cites") && prompt.contains("citation"),
                "L'obligation de citer n'est pas explicite");
        assertTrue(prompt.contains("hors périmètre") || prompt.contains("hors du parcours"),
                "Le périmètre n'est pas borné");
    }

    @Test
    @DisplayName("Le prompt contient cinq exemples, dont deux refus")
    void cinqExemplesDontDeuxRefus() throws Exception {
        String prompt = prompt();
        int exemples = prompt.split("### Exemple", -1).length - 1;
        int refus = 0;
        for (String bloc : prompt.split("### Exemple")) {
            if (bloc.contains("\"refus\": \"AUCUN\"") || bloc.isBlank()) {
                continue;
            }
            if (bloc.contains("\"refus\": \"")) {
                refus++;
            }
        }
        assertEquals(5, exemples, "Le plan demande cinq exemples few-shot");
        assertEquals(2, refus, "Deux des cinq exemples doivent être des refus");
    }

    // ------------------------------------------------------------------ le schéma
    @Test
    @DisplayName("Le schéma de sortie décrit les cinq champs, et ses motifs sont ceux du code")
    void schemaCoherentAvecLeCode() throws Exception {
        JsonNode schema = MAPPER.readTree(ressource("/fr/ihm2026/ia/sortie-schema.json"));
        List<String> requis = new ArrayList<>();
        schema.path("required").forEach(n -> requis.add(n.asText()));
        assertEquals(List.of("version", "reponse", "citations", "notion", "refus"), requis);

        List<String> motifsSchema = new ArrayList<>();
        schema.path("properties").path("refus").path("enum").forEach(n -> motifsSchema.add(n.asText()));
        List<String> motifsCode = new ArrayList<>();
        for (MotifRefus m : MotifRefus.values()) {
            if (m.estRefus()) {
                motifsCode.add(m.name());
            }
        }
        motifsCode.sort(null);
        motifsSchema.sort(null);
        assertEquals(motifsCode, motifsSchema,
                "Le schéma et l'énumération Java doivent lister les mêmes motifs");
        assertEquals(ModeleSimule.VERSION_PROMPT,
                schema.path("properties").path("version").path("const").asText(),
                "La version du schéma doit suivre celle du prompt");
    }

    // ------------------------------------------------------------------ la validation
    private static String sortie(String reponse, String citations, String notion, String refus) {
        return "{\"version\":\"" + ModeleSimule.VERSION_PROMPT + "\",\"reponse\":\"" + reponse
                + "\",\"citations\":" + citations + ",\"notion\":\"" + notion
                + "\",\"refus\":\"" + refus + "\"}";
    }

    private static final String CITATION =
            "[{\"section\":\"Fonctions et valeur de retour en Python\",\"langage\":\"python\"}]";

    @Test
    @DisplayName("Une sortie conforme est acceptée, un refus aussi")
    void sortiesConformesAcceptees() {
        Optional<SortieIa> reponse = SortieIa.lire(sortie("Une fonction se declare avec def.",
                CITATION, "FONC", "AUCUN"));
        assertTrue(reponse.isPresent(), "Une réponse citée doit être acceptée");
        assertEquals("FONC", reponse.get().notion());
        assertFalse(reponse.get().refus().estRefus());

        Optional<SortieIa> refus = SortieIa.lire(sortie("Je ne note pas.", "[]", "DIVERS",
                "DECISION_DEMANDEE"));
        assertTrue(refus.isPresent(), "Un refus sans citation doit être accepté");
        assertEquals(MotifRefus.DECISION_DEMANDEE, refus.get().refus());
    }

    @Test
    @DisplayName("Une réponse sans citation, un refus qui cite, un motif inconnu : tous rejetés")
    void sortiesNonConformesRejetees() {
        assertTrue(SortieIa.lire(sortie("Une fonction se declare avec def.", "[]", "FONC", "AUCUN"))
                .isEmpty(), "Une réponse sans citation n'est pas affichable");
        assertTrue(SortieIa.lire(sortie("Je ne note pas.", CITATION, "DIVERS", "DECISION_DEMANDEE"))
                .isEmpty(), "Un refus ne cite rien");
        assertTrue(SortieIa.lire(sortie("Texte.", CITATION, "FONC", "NOTE_REFUSEE")).isEmpty(),
                "Un motif inconnu du code doit être rejeté");
        assertTrue(SortieIa.lire(sortie("Texte.", "[{\"section\":\"Boucles\",\"langage\":\"rust\"}]",
                "BOUCLE", "AUCUN")).isEmpty(), "Un langage hors des trois parcours est rejeté");
        assertTrue(SortieIa.lire(sortie("Texte.", CITATION, "INCONNUE", "AUCUN")).isEmpty(),
                "Une notion inconnue est rejetée");
        assertTrue(SortieIa.lire("{\"reponse\":\"Texte.\",\"citations\":" + CITATION + "}").isEmpty(),
                "Un champ manquant est rejeté");
        assertTrue(SortieIa.lire("pas du json").isEmpty(), "Un texte libre est rejeté");
        assertTrue(SortieIa.lire(null).isEmpty());
    }

    @Test
    @DisplayName("Sérialiser puis relire une réponse redonne la même réponse")
    void allerRetour() {
        ReponseIa origine = new ReponseIa("Une fonction se declare avec def.",
                List.of(new ReponseIa.Citation("Fonctions et valeur de retour en Python", "python")),
                MotifRefus.AUCUN, ModeleSimule.VERSION_PROMPT);
        String json = SortieIa.enJson(origine);
        Optional<SortieIa> relue = SortieIa.lire(json);
        assertTrue(relue.isPresent(), "La sortie sérialisée doit être relisible : " + json);
        ReponseIa retour = relue.get().versReponse();
        assertEquals(origine.reponse(), retour.reponse());
        assertEquals(origine.citations(), retour.citations());
        assertEquals(origine.motif(), retour.motif());
        assertEquals(origine.versionPrompt(), retour.versionPrompt());
    }

    // ------------------------------------------------------------------ la preuve du critère
    @Test
    @DisplayName("Dix questions du jeu gelé produisent un JSON valide, avec une citation juste")
    void dixQuestionsDuJeuGele() throws Exception {
        List<JsonNode> jeu = new ArrayList<>();
        for (String ligne : Files.readAllLines(JEU, StandardCharsets.UTF_8)) {
            if (!ligne.isBlank()) {
                jeu.add(MAPPER.readTree(ligne));
            }
        }
        assertTrue(jeu.size() >= 10, "Jeu de test introuvable ou trop court : " + JEU);

        // Dix questions réparties sur les six notions, et sur les trois parcours : pour chaque
        // notion, on prend une question dans deux langages différents, en faisant tourner le
        // langage de départ. La règle est fixe, donc la sélection est reproductible à l'identique.
        List<JsonNode> selection = new ArrayList<>();
        List<String> notions = List.of("VAR", "COND", "BOUCLE", "FONC", "COLL", "ERREUR");
        List<String> langages = List.of("python", "java", "cpp");
        for (int i = 0; i < notions.size(); i++) {
            String notion = notions.get(i);
            int voulu = i < 4 ? 2 : 1;
            int pris = 0;
            for (int tour = 0; tour < langages.size() && pris < voulu; tour++) {
                String langage = langages.get((i + tour) % langages.size());
                for (JsonNode cas : jeu) {
                    if (notion.equals(cas.path("notion").asText())
                            && langage.equals(cas.path("langage").asText())) {
                        selection.add(cas);
                        pris++;
                        break;
                    }
                }
            }
        }
        assertEquals(10, selection.size(), "Dix questions attendues dans la sélection");

        int avecCitationJuste = 0;
        for (JsonNode cas : selection) {
            Session.Langage langage = switch (cas.path("langage").asText()) {
                case "java" -> Session.Langage.JAVA;
                case "cpp" -> Session.Langage.CPP;
                default -> Session.Langage.PYTHON;
            };
            ContexteIa contexte = new ContexteIa(3, langage, cas.path("question").asText(), "");

            String json = AideIaApi.demanderJson(contexte);
            Optional<SortieIa> sortie = SortieIa.lire(json);
            assertTrue(sortie.isPresent(),
                    "Sortie invalide pour " + cas.path("id").asText() + " : " + json);
            assertEquals(ModeleSimule.VERSION_PROMPT, sortie.get().version());
            assertEquals(MotifRefus.AUCUN, sortie.get().refus(),
                    "Refus inattendu pour " + cas.path("id").asText());

            String attendue = cas.path("section").asText();
            boolean juste = sortie.get().citations().stream()
                    .anyMatch(c -> c.section().equals(attendue));
            if (juste) {
                avecCitationJuste++;
            }
        }
        assertTrue(avecCitationJuste >= 10,
                "Le critère de sortie de l'étape exige au moins une citation juste pour chacune "
                        + "des dix questions ; obtenu : " + avecCitationJuste + "/10");
    }

    @Test
    @DisplayName("La version du prompt est journalisée avec chaque échange")
    void versionJournalisee() {
        AideIaApi.demander(new ContexteIa(3, Session.Langage.PYTHON,
                "comment déclarer une fonction en Python ?", ""));
        assertFalse(AideIaApi.journal().isEmpty(), "L'échange doit être journalisé");
        String modele = AideIaApi.journal().get(0).modele();
        assertTrue(modele.contains(ModeleSimule.VERSION_PROMPT),
                "Le journal doit porter la version du prompt : " + modele);
        assertEquals(ModeleSimule.VERSION_PROMPT, AideIaApi.journal().get(0).modele()
                .substring(modele.indexOf('@') + 1));
    }
}
