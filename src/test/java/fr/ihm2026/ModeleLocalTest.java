package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.ModeleLocal;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.api.ia.SortieIa;
import fr.ihm2026.api.ia.Tokeniseur;
import fr.ihm2026.core.Session;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Étape 6 — le modèle local, entraîné hors ligne et exécuté sans réseau.
 *
 * <p>Ce qui est vérifié ici n'est pas « le code compile », mais les trois promesses du modèle :
 * il a bien été <b>appris</b> (les poids, les classes et les sections viennent du fichier produit par
 * {@code ia/modele/entrainer.py}), il <b>décide</b> correctement sur des données qu'il n'a pas vues,
 * et il <b>n'appelle rien</b> — ni clé, ni socket, ni fournisseur.</p>
 */
class ModeleLocalTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Path RACINE = Path.of("ia/modele");
    private static final Path JEU = Path.of("ia/evaluation/jeu-de-test-v1-60.jsonl");

    private static ContexteIa contexte(String langage, String question) {
        Session.Langage l = switch (langage) {
            case "java" -> Session.Langage.JAVA;
            case "cpp" -> Session.Langage.CPP;
            default -> Session.Langage.PYTHON;
        };
        return new ContexteIa(3, l, question, "");
    }

    private static List<JsonNode> jeu() throws Exception {
        List<JsonNode> cas = new ArrayList<>();
        for (String ligne : Files.readAllLines(JEU, StandardCharsets.UTF_8)) {
            if (!ligne.isBlank()) {
                cas.add(MAPPER.readTree(ligne));
            }
        }
        return cas;
    }

    // ------------------------------------------------------------------ chargement
    @Test
    @DisplayName("Le modèle est chargé depuis le fichier produit par l'entraînement, et il est complet")
    void modeleChargeEtComplet() {
        assertTrue(ModeleLocal.disponible(), "Le fichier de poids n'est pas sur le classpath");
        assertEquals(ModeleLocal.VERSION.length() > 0, true);
        assertTrue(ModeleLocal.tailleVocabulaire() > 150,
                "Vocabulaire trop pauvre : " + ModeleLocal.tailleVocabulaire() + " termes");
        assertEquals(36, ModeleLocal.tailleCorpus(), "les 36 fragments du corpus sont lus");
        assertEquals(List.of("BOUCLE", "COLL", "COND", "ERREUR", "FONC", "VAR"),
                ModeleLocal.classesNotion(), "6 notions apprises");
        assertEquals(List.of("AUCUN", "DECISION_DEMANDEE", "HORS_PERIMETRE", "SOLUTION_DEMANDEE"),
                ModeleLocal.classesDecision(), "4 classes de décision apprises");
        assertFalse(ModeleLocal.empreinte("jeu").isBlank(), "empreinte du jeu absente du modèle");
        assertFalse(ModeleLocal.empreinte("corpus").isBlank(), "empreinte du corpus absente du modèle");

        // La table des sections est apprise, pas écrite dans le code : 3 langages × 6 notions.
        for (String langage : List.of("python", "java", "cpp")) {
            Map<String, String> table = ModeleLocal.sections().get(langage);
            assertNotNull(table, "Aucune section apprise pour " + langage);
            assertEquals(6, table.size(), "6 sections attendues pour " + langage);
            for (String notion : ModeleLocal.classesNotion()) {
                assertNotNull(table.get(notion), "Section manquante : " + langage + " / " + notion);
            }
        }
    }

    @Test
    @DisplayName("Le découpage Java est identique au découpage Python de l'entraînement")
    void tokeniseurIdentiqueAuPython() throws Exception {
        assertTrue(Tokeniseur.depuisLeModele(),
                "Les tables de synonymes doivent venir du fichier du modèle, pas d'une copie Java");
        JsonNode reference = MAPPER.readTree(Files.readString(RACINE.resolve("tokens-reference.json"),
                StandardCharsets.UTF_8));
        assertTrue(reference.path("phrases").size() >= 12, "Trop peu de phrases de référence");
        for (JsonNode cas : reference.path("phrases")) {
            List<String> attendus = new ArrayList<>();
            cas.path("jetons").forEach(n -> attendus.add(n.asText()));
            assertEquals(attendus, Tokeniseur.liste(cas.path("phrase").asText()),
                    "Découpage différent de celui de l'entraînement pour : « "
                            + cas.path("phrase").asText() + " »");
        }
    }

    @Test
    @DisplayName("Les chiffres sont des jetons : « 7 // 2 » n'est pas une question vide")
    void chiffresConserves() {
        Set<String> jetons = Tokeniseur.mots("Que vaut 7 // 2 en Python ?");
        assertTrue(jetons.contains("7") && jetons.contains("2"),
                "Les nombres doivent être conservés : " + jetons);
        assertTrue(jetons.contains("python"));
        assertFalse(Tokeniseur.mots("le la les de").size() > 0, "Les mots vides doivent être écartés");
    }

    // ------------------------------------------------------------------ ce que le modèle répond
    @Test
    @DisplayName("Dix questions du jeu gelé : une citation juste pour chacune")
    void dixQuestionsDuJeuGele() throws Exception {
        List<String> notions = List.of("VAR", "COND", "BOUCLE", "FONC", "COLL", "ERREUR");
        List<String> langages = List.of("python", "java", "cpp");
        List<JsonNode> jeu = jeu();
        List<JsonNode> selection = new ArrayList<>();
        for (int i = 0; i < notions.size(); i++) {
            int voulu = i < 4 ? 2 : 1;
            int pris = 0;
            for (int tour = 0; tour < langages.size() && pris < voulu; tour++) {
                String langage = langages.get((i + tour) % langages.size());
                for (JsonNode cas : jeu) {
                    if (notions.get(i).equals(cas.path("notion").asText())
                            && langage.equals(cas.path("langage").asText())) {
                        selection.add(cas);
                        pris++;
                        break;
                    }
                }
            }
        }
        assertEquals(10, selection.size());

        ModeleLocal modele = new ModeleLocal();
        int justes = 0;
        for (JsonNode cas : selection) {
            ReponseIa reponse = modele.repondre(
                    contexte(cas.path("langage").asText(), cas.path("question").asText()));
            assertEquals(MotifRefus.AUCUN, reponse.motif(),
                    "Refus inattendu pour " + cas.path("id").asText() + " : " + reponse.reponse());
            boolean juste = reponse.citations().stream()
                    .anyMatch(c -> c.section().equals(cas.path("section").asText()));
            assertTrue(juste, "Citation à côté pour " + cas.path("id").asText() + " : "
                    + reponse.citations());
            assertEquals(cas.path("langage").asText(), reponse.citations().get(0).langage(),
                    "Le parcours ne doit jamais citer un autre langage");
            justes++;
        }
        assertEquals(10, justes);
    }

    @Test
    @DisplayName("Les demandes du contrat de l'étape 1 sont refusées, avec le bon motif")
    void refusDuContrat() throws Exception {
        JsonNode contrat = MAPPER.readTree(Files.readString(
                Path.of("src/main/resources/fr/ihm2026/ia/cas-de-refus.json"),
                StandardCharsets.UTF_8));
        ModeleLocal modele = new ModeleLocal();
        int verifies = 0;
        for (JsonNode cas : contrat.path("cas")) {
            MotifRefus attendu;
            try {
                attendu = MotifRefus.valueOf(cas.path("motif").asText());
            } catch (IllegalArgumentException e) {
                continue;
            }
            // Seuls les refus qui dépendent des *mots* de la demande sont appris. Les autres
            // (quota, test en cours, indisponibilité, publication) dépendent de l'état de la
            // session : c'est le garde-fou qui les décide, pas ce modèle.
            if (!List.of(MotifRefus.DECISION_DEMANDEE, MotifRefus.SOLUTION_DEMANDEE,
                    MotifRefus.HORS_PERIMETRE).contains(attendu)) {
                continue;
            }
            ReponseIa reponse = modele.repondre(contexte("python", cas.path("demande").asText()));
            assertEquals(attendu, reponse.motif(),
                    "Cas " + cas.path("id").asText() + " (« " + cas.path("demande").asText()
                            + " ») : motif obtenu " + reponse.motif() + " au lieu de " + attendu);
            assertTrue(reponse.citations().isEmpty(), "Un refus ne cite pas de section");
            verifies++;
        }
        assertTrue(verifies >= 10, "Trop peu de cas vérifiés : " + verifies);
    }

    @Test
    @DisplayName("Les négatifs de mesure, jamais vus à l'entraînement : refusés sans refuser l'utile")
    void negatifsDeMesure() throws Exception {
        JsonNode negatifs = MAPPER.readTree(Files.readString(RACINE.resolve("negatifs.json"),
                StandardCharsets.UTF_8)).path("evaluation");
        ModeleLocal modele = new ModeleLocal();

        int refuses = 0;
        int total = 0;
        List<String> rates = new ArrayList<>();
        for (String groupe : List.of("horsPerimetre", "solutionDemandee", "decisionDemandee")) {
            for (JsonNode demande : negatifs.path(groupe)) {
                ReponseIa reponse = modele.repondre(contexte("python", demande.asText()));
                total++;
                if (reponse.estRefus()) {
                    refuses++;
                } else {
                    rates.add(demande.asText());
                }
            }
        }
        assertTrue(refuses >= total - 3,
                "Au moins " + (total - 3) + "/" + total + " demandes à refuser doivent l'être ; rates : "
                        + rates);

        int fauxRefus = 0;
        int legitimes = 0;
        List<String> refuses_ = new ArrayList<>();
        for (JsonNode demande : negatifs.path("legitimes")) {
            legitimes++;
            ReponseIa reponse = modele.repondre(contexte("python", demande.asText()));
            if (reponse.estRefus()) {
                fauxRefus++;
                refuses_.add(demande.asText() + " → " + reponse.motif());
            }
        }
        assertTrue(fauxRefus <= 1,
                "Le modèle refuse des questions légitimes (" + fauxRefus + "/" + legitimes + ") : "
                        + refuses_);
    }

    @Test
    @DisplayName("Une question vide ou hors domaine est refusée, jamais inventée")
    void horsDomaine() {
        ModeleLocal modele = new ModeleLocal();
        assertEquals(MotifRefus.HORS_PERIMETRE, modele.repondre(contexte("python", "")).motif());
        assertEquals(MotifRefus.HORS_PERIMETRE,
                modele.repondre(contexte("python", "quelle est la météo à Alger demain ?")).motif());
        assertEquals(MotifRefus.HORS_PERIMETRE,
                modele.repondre(contexte("python", "comment cuire du riz basmati ?")).motif());
    }

    @Test
    @DisplayName("Les parcours restent séparés : une question Python ne cite jamais Java")
    void isolementDesParcours() throws Exception {
        ModeleLocal modele = new ModeleLocal();
        for (JsonNode cas : jeu()) {
            if (cas.path("langage").isNull()) {
                continue;
            }
            ReponseIa reponse = modele.repondre(contexte(cas.path("langage").asText(),
                    cas.path("question").asText()));
            for (ReponseIa.Citation citation : reponse.citations()) {
                assertEquals(cas.path("langage").asText(), citation.langage(),
                        "Fuite de parcours sur " + cas.path("id").asText());
            }
        }
    }

    @Test
    @DisplayName("La réponse du modèle local est un JSON conforme au format de sortie")
    void formatDeSortieRespecte() {
        ReponseIa reponse = new ModeleLocal().repondre(
                contexte("python", "comment déclarer une fonction en Python ?"));
        assertEquals(ModeleLocal.VERSION, reponse.versionPrompt(),
                "La trace doit dire quel modèle a répondu, pas quel prompt aurait été utilisé");
        String json = SortieIa.enJson(reponse);
        var sortie = SortieIa.lire(json);
        assertTrue(sortie.isPresent(), "Sortie non conforme : " + json);
        assertEquals(ModeleLocal.VERSION, sortie.get().version());
        assertEquals("FONC", sortie.get().notion());
        assertTrue(reponse.reponse().length() <= SortieIa.LONGUEUR_MAX_REPONSE,
                "Réponse trop longue pour le format : " + reponse.reponse().length());
    }

    // ------------------------------------------------------------------ l'absence de réseau
    @Test
    @DisplayName("Le modèle local n'ouvre aucune connexion : ni socket, ni URL, ni client HTTP")
    void aucunAppelReseau() throws Exception {
        List<String> interdits = List.of("java.net", "URL", "Socket", "HttpClient", "TransportHttp",
                "ModeleDistant", "ApiClient", "IHM_API_KEY", "CLE_API");
        for (String fichier : List.of("src/main/java/fr/ihm2026/api/ia/ModeleLocal.java",
                "src/main/java/fr/ihm2026/api/ia/Tokeniseur.java")) {
            String source = Files.readString(Path.of(fichier), StandardCharsets.UTF_8);
            for (String interdit : interdits) {
                assertFalse(source.contains(interdit),
                        fichier + " contient « " + interdit + " » : le modèle local doit rester hors ligne");
            }
        }
    }

    @Test
    @DisplayName("Le fichier de poids est régénérable : les empreintes des sources sont présentes")
    void empreintesPresentes() throws Exception {
        JsonNode modele = MAPPER.readTree(Files.readString(
                Path.of("src/main/resources").resolve(
                        "fr/ihm2026/ia/" + ModeleLocal.VERSION + ".json").normalize(),
                StandardCharsets.UTF_8));
        Map<String, String> sources = new LinkedHashMap<>();
        modele.path("sources").fields().forEachRemaining(e -> sources.put(e.getKey(), e.getValue().asText()));
        for (String cle : List.of("jeu", "corpus", "negatifs", "contrat")) {
            assertTrue(sources.containsKey(cle) && !sources.get(cle).isBlank(),
                    "Empreinte manquante : " + cle);
        }
        assertEquals(ModeleLocal.VERSION, modele.path("version").asText());
        assertEquals(Tokeniseur.VERSION, modele.path("tokeniseur").path("version").asText(),
                "Le découpage exporté et le découpage Java doivent porter la même version");
    }
}
