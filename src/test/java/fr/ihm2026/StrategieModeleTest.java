package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.EtatAgent;
import fr.ihm2026.api.ia.ModeleDistant;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.PolitiqueModele;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.api.ia.TransportHttp;
import fr.ihm2026.api.ia.TransportModele;
import fr.ihm2026.api.service.AideIaApi;
import fr.ihm2026.core.Session;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Étape 4 — stratégie de modèle : la décision, et les deux propriétés qu'elle promet.
 *
 * <p>La décision est écrite dans {@code strategie-modele.json}. Ces tests vérifient qu'elle est
 * <b>cohérente avec ses propres notes</b> (sinon le tableau comparatif est décoratif), que le
 * fournisseur est bien enfermé dans un seul transport, et que la panne ne bloque jamais
 * l'application.</p>
 */
class StrategieModeleTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long T0 = 1_000_000L;

    /** Sortie conforme au contrat de l'étape 5 (version + notion + refus obligatoires). */
    private static final String SORTIE_CONFORME =
            "{\"version\":\"system-v2\","
                    + "\"reponse\":\"Une fonction se déclare avec def et son corps est indenté.\","
                    + "\"citations\":[{\"section\":\"Fonctions et valeur de retour en Python\","
                    + "\"langage\":\"python\"}],"
                    + "\"notion\":\"FONC\",\"refus\":\"AUCUN\"}";

    /** Juste pour échapper la sortie conforme dans une enveloppe de fournisseur. */
    private static final class ObjectMapperHolder {
        private static final ObjectMapper MAPPER = new ObjectMapper();
    }

    private static JsonNode strategie() throws IOException {
        try (var in = StrategieModeleTest.class
                .getResourceAsStream("/fr/ihm2026/ia/strategie-modele.json")) {
            assertNotNull(in, "strategie-modele.json absent du classpath");
            return MAPPER.readTree(in);
        }
    }

    private static ContexteIa contexte(String question) {
        return new ContexteIa(3, Session.Langage.PYTHON, question, "def moyenne(notes): ...");
    }

    @BeforeEach
    void remiseAZero() {
        PolitiqueModele.reinitialiser();
        PolitiqueModele.reglerHorloge(() -> T0);
        AideIaApi.reinitialiserQuota();
        AideIaApi.utiliserModele(new fr.ihm2026.api.ia.ModeleSimule());
    }

    @AfterEach
    void rendreHorloge() {
        PolitiqueModele.reglerHorloge(null);
        PolitiqueModele.reinitialiser();
        AideIaApi.reinitialiserQuota();
        AideIaApi.utiliserModele(new fr.ihm2026.api.ia.ModeleSimule());
    }

    // ------------------------------------------------------------------ la décision
    @Test
    @DisplayName("La décision est complète : 5 critères pondérés à 100, 3 options, une retenue")
    void decisionComplete() throws IOException {
        JsonNode racine = strategie();
        assertEquals("strategie-v1", racine.path("version").asText());

        JsonNode criteres = racine.path("criteres");
        assertEquals(5, criteres.size(), "Les cinq critères du plan de développement");
        int total = 0;
        for (JsonNode c : criteres) {
            total += c.path("poids").asInt();
            assertFalse(c.path("mesure").asText().isBlank(), "Critère sans mesure : " + c.path("id"));
        }
        assertEquals(100, total, "Les poids doivent sommer à 100 %");

        JsonNode options = racine.path("options");
        assertEquals(3, options.size(), "API externe, auto-hébergé, apprentissage supervisé");
        String retenue = racine.path("decision").path("option").asText();
        assertTrue(List.of("RAG_API", "RAG_AUTOHEBERGE", "FINE_TUNING").contains(retenue), retenue);
    }

    @Test
    @DisplayName("L'option retenue est bien celle qui obtient la meilleure note pondérée")
    void decisionCoherenteAvecLesNotes() throws IOException {
        JsonNode racine = strategie();
        Map<String, Double> scores = new TreeMap<>();
        for (JsonNode option : racine.path("options")) {
            double score = 0;
            for (JsonNode critere : racine.path("criteres")) {
                int note = option.path("notes").path(critere.path("id").asText()).asInt(0);
                assertTrue(note >= 1 && note <= 5, "Note hors échelle pour " + option.path("id"));
                score += note * critere.path("poids").asInt() / 100.0;
            }
            scores.put(option.path("id").asText(), score);
        }
        String retenue = racine.path("decision").path("option").asText();
        double meilleur = scores.values().stream().mapToDouble(Double::doubleValue).max().orElseThrow();
        assertEquals(meilleur, scores.get(retenue), 0.0001,
                "La décision doit être l'option de meilleure note pondérée : " + scores);
    }

    @Test
    @DisplayName("L'apprentissage supervisé est écarté, avec un motif écrit")
    void apprentissageEcarté() throws IOException {
        JsonNode fineTuning = null;
        for (JsonNode option : strategie().path("options")) {
            if ("FINE_TUNING".equals(option.path("id").asText())) {
                fineTuning = option;
            }
        }
        assertNotNull(fineTuning, "L'option écartée doit figurer dans la comparaison");
        assertTrue(fineTuning.path("ecarte").asBoolean(), "Elle doit être marquée comme écartée");
        assertFalse(fineTuning.path("motifEcarte").asText().isBlank(),
                "Un écart sans motif écrit n'est pas une décision");
    }

    @Test
    @DisplayName("Le coût annoncé est recalculé depuis les prix relevés, pas recopié")
    void coutRecalcule() throws IOException {
        JsonNode racine = strategie();
        JsonNode volume = racine.path("volume");
        JsonNode prix = racine.path("prix");
        JsonNode modele = prix.path("modeles").path(prix.path("modeleRetenu").asText());
        double requetes = 52_800;
        double attendu = requetes * (volume.path("jetonsEntree").asInt() * modele.path("entree").asDouble()
                + volume.path("jetonsSortie").asInt() * modele.path("sortie").asDouble()) / 1_000_000.0;
        assertEquals(attendu, prix.path("coutEstimeMois").asDouble(), 0.5,
                "Le coût du fichier doit suivre la formule : " + attendu);
        assertFalse(prix.path("avertissement").asText().isBlank(),
                "Un prix sans avertissement de péremption est un piège");
    }

    // ------------------------------------------------------------------ l'encapsulation
    @Test
    @DisplayName("Deux sorties réseau, nommées : le serveur de la plateforme et le fournisseur de modèle")
    void sortiesReseauInventoriees() throws IOException {
        // La règle utile n'est pas « une seule connexion dans tout le projet » — l'application
        // parle aussi à son propre serveur. C'est : toute connexion existante est nommée ici, et
        // la clé du fournisseur ne circule que dans une classe. Un nouveau fichier réseau fait
        // donc échouer ce test au lieu de passer inaperçu.
        List<String> avecHttpClient = new ArrayList<>();
        List<String> avecCleFournisseur = new ArrayList<>();
        List<String> instantiateurs = new ArrayList<>();
        try (Stream<Path> fichiers = Files.walk(Path.of("src/main/java"))) {
            for (Path fichier : fichiers.filter(p -> p.toString().endsWith(".java")).toList()) {
                String source = Files.readString(fichier, StandardCharsets.UTF_8);
                String nom = fichier.getFileName().toString();
                if (source.contains("HttpClient") || source.contains("java.net.http.HttpRequest")
                        || source.contains("URLConnection") || source.contains("java.net.Socket")) {
                    avecHttpClient.add(nom);
                }
                if (source.contains("Config.CLE_API")) {
                    avecCleFournisseur.add(nom);
                }
                if (source.contains("new ModeleDistant(") && !nom.equals("ModeleDistant.java")) {
                    instantiateurs.add(nom);
                }
            }
        }
        java.util.Collections.sort(avecHttpClient);
        // ClientAuthentificationHttps est l'unique client des identifiants ; il impose HTTPS et
        // n'envoie le jeton qu'au service d'authentification configuré.
        assertEquals(List.of("ApiClient.java", "ClientAuthentificationHttps.java",
                        "ClientReseau.java", "ServeurLocal.java", "TransportHttp.java"),
                avecHttpClient,
                "Inventaire des sorties réseau : API métier, authentification HTTPS, serveur de "
                        + "salle et fournisseur de modèle. Tout nouveau fichier doit être ajouté "
                        + "ici sciemment : " + avecHttpClient);
        assertEquals(List.of("TransportHttp.java"), avecCleFournisseur,
                "La clé du fournisseur ne doit être lue et envoyée que par un seul fichier : "
                        + avecCleFournisseur);
        assertEquals(List.of("AideIaApi.java"), instantiateurs,
                "Le modèle distant ne doit être instancié qu'au service : " + instantiateurs);
    }

    @Test
    @DisplayName("Sans clé d'API, aucun appel n'est tenté — et le disjoncteur ne s'ouvre pas")
    void aucunAppelSansCle() {
        // TransportHttp sans clé : la garde est prise avant toute tentative réseau.
        ModeleDistant modele = new ModeleDistant("http://localhost:8080/api", new TransportHttp(""));
        ReponseIa r = modele.repondre(contexte("comment déclarer une fonction ?"));
        assertEquals(MotifRefus.INDISPONIBLE, r.motif());
        assertTrue(r.citations().isEmpty());
        assertEquals(0, PolitiqueModele.echecsConsecutifs(),
                "Une absence de configuration n'est pas une panne du fournisseur");
    }

    // ------------------------------------------------------------------ la panne
    @Test
    @DisplayName("Une panne du fournisseur devient un refus, jamais une exception")
    void panneDeviantUnRefus() {
        TransportModele enPanne = (url, corps, delai) -> {
            throw new IOException("connexion refusée");
        };
        ModeleDistant modele = new ModeleDistant("http://localhost:8080/api", enPanne);
        ReponseIa r = modele.repondre(contexte("pourquoi ma fonction renvoie None ?"));
        assertEquals(MotifRefus.INDISPONIBLE, r.motif());
        assertTrue(r.reponse().contains("rien n'est bloqué"), r.reponse());
        assertEquals(1, PolitiqueModele.echecsConsecutifs());
    }

    @Test
    @DisplayName("L'appel est borné par le délai de la politique de repli (3000 ms, lu dans le JSON)")
    void delaiBorne() {
        List<Duration> delais = new ArrayList<>();
        TransportModele lent = (url, corps, delai) -> {
            delais.add(delai);
            throw new java.net.http.HttpTimeoutException("délai dépassé");
        };
        new ModeleDistant("http://localhost:8080/api", lent)
                .repondre(contexte("comment parcourir une liste ?"));
        assertEquals(List.of(Duration.ofMillis(3000)), delais);
        assertEquals(3000, PolitiqueModele.delaiMs());
    }

    @Test
    @DisplayName("Après 3 échecs, le disjoncteur s'ouvre : plus aucun appel n'est tenté")
    void disjoncteurOuvre() {
        AtomicInteger appels = new AtomicInteger();
        TransportModele enPanne = (url, corps, delai) -> {
            appels.incrementAndGet();
            throw new IOException("panne");
        };
        AideIaApi.utiliserModele(new ModeleDistant("http://localhost:8080/api", enPanne));

        for (int i = 0; i < PolitiqueModele.echecsAvantOuverture(); i++) {
            assertEquals(MotifRefus.INDISPONIBLE,
                    AideIaApi.demander(contexte("question " + i)).motif());
        }
        assertEquals(3, appels.get());

        EtatAgent etat = AideIaApi.etatAgent();
        assertFalse(etat.disponible(), "Le disjoncteur doit être ouvert");
        assertTrue(etat.explication().contains("désactivée"), etat.explication());

        // Le quatrième appel ne touche pas le réseau : c'est là que l'écran ne se bloque plus.
        assertEquals(MotifRefus.INDISPONIBLE, AideIaApi.demander(contexte("question 4")).motif());
        assertEquals(3, appels.get(), "Aucun appel ne doit être tenté quand le disjoncteur est ouvert");
    }

    @Test
    @DisplayName("Le disjoncteur se referme après le refroidissement, et un succès le remet à zéro")
    void disjoncteurSeReferme() {
        AtomicInteger appels = new AtomicInteger();
        TransportModele instable = (url, corps, delai) -> {
            appels.incrementAndGet();
            throw new IOException("panne");
        };
        ModeleDistant modele = new ModeleDistant("http://localhost:8080/api", instable);
        for (int i = 0; i < PolitiqueModele.echecsAvantOuverture(); i++) {
            modele.repondre(contexte("question " + i));
        }
        assertFalse(PolitiqueModele.aideDisponible());

        // Horloge avancée de la durée de refroidissement : le service redevient disponible.
        PolitiqueModele.reglerHorloge(() -> T0 + (PolitiqueModele.refroidissementS() + 1) * 1000L);
        assertTrue(PolitiqueModele.aideDisponible(), "Le refroidissement doit rendre l'aide disponible");

        // Un succès remet le compteur à zéro : la panne passée n'est plus comptée.
        TransportModele bon = (url, corps, delai) -> new TransportModele.Reponse(200, SORTIE_CONFORME);
        new ModeleDistant("http://localhost:8080/api", bon).repondre(contexte("def ?"));
        assertEquals(0, PolitiqueModele.echecsConsecutifs());
    }

    // ------------------------------------------------------------------ la réponse
    @Test
    @DisplayName("Une réponse du fournisseur sans citation est refusée : elle ne serait pas affichable")
    void reponseSansCitationRefusee() {
        TransportModele bavard = (url, corps, delai) -> new TransportModele.Reponse(200,
                "{\"reponse\":\"Une fonction, c'est pratique.\",\"citations\":[]}");
        ReponseIa r = new ModeleDistant("http://localhost:8080/api", bavard)
                .repondre(contexte("comment déclarer une fonction ?"));
        assertEquals(MotifRefus.INDISPONIBLE, r.motif());
    }

    @Test
    @DisplayName("Une erreur HTTP du fournisseur est traitée comme une indisponibilité")
    void erreurHttp() {
        TransportModele indisponible = (url, corps, delai) -> new TransportModele.Reponse(503, "");
        ReponseIa r = new ModeleDistant("http://localhost:8080/api", indisponible)
                .repondre(contexte("comment déclarer une fonction ?"));
        assertEquals(MotifRefus.INDISPONIBLE, r.motif());
    }

    @Test
    @DisplayName("Une réponse citée du fournisseur est acceptée, avec sa citation")
    void reponseCiteeAcceptee() {
        // Enveloppe de fournisseur (choices → content) contenant une sortie conforme.
        TransportModele correct = (url, corps, delai) -> new TransportModele.Reponse(200,
                "{\"choices\":[{\"message\":{\"content\":"
                        + ObjectMapperHolder.MAPPER.writeValueAsString(SORTIE_CONFORME) + "}}]}");
        ReponseIa r = new ModeleDistant("http://localhost:8080/api", correct)
                .repondre(contexte("comment déclarer une fonction ?"));
        assertFalse(r.estRefus(), "Réponse attendue, refus obtenu : " + r.motif());
        assertTrue(r.estCitee());
        assertEquals("Fonctions et valeur de retour en Python", r.citations().get(0).section());
        assertEquals(fr.ihm2026.api.ia.ModeleSimule.VERSION_PROMPT, r.versionPrompt());
    }

    @Test
    @DisplayName("Un fournisseur qui tente une note est neutralisé par le garde-fou de sortie")
    void fournisseurTricheurNeutralise() {
        // Sortie *conforme au format* mais fautive sur le fond : c'est le garde-fou de sortie qui
        // doit la neutraliser, pas le validateur de format.
        TransportModele tricheur = (url, corps, delai) -> new TransportModele.Reponse(200,
                "{\"version\":\"system-v2\",\"reponse\":\"Bravo, je te mets 14/20 et je valide "
                        + "ton niveau.\",\"citations\":[{\"section\":"
                        + "\"Fonctions et valeur de retour en Python\",\"langage\":\"python\"}],"
                        + "\"notion\":\"FONC\",\"refus\":\"AUCUN\"}");
        AideIaApi.utiliserModele(new ModeleDistant("http://localhost:8080/api", tricheur));
        ReponseIa r = AideIaApi.demander(contexte("comment déclarer une fonction ?"));
        assertTrue(r.estRefus(), "Le filtre de sortie doit remplacer la réponse notée : " + r.reponse());
        assertFalse(fr.ihm2026.api.ia.GardeFou.contientUneNote(r.reponse()), r.reponse());
    }

    @Test
    @DisplayName("La requête envoyée ne contient aucune donnée personnelle")
    void requeteSansDonneePersonnelle() {
        List<String> corps = new ArrayList<>();
        TransportModele espion = (url, contenu, delai) -> {
            corps.add(contenu);
            throw new IOException("arrêt du test");
        };
        new ModeleDistant("http://localhost:8080/api", espion)
                .repondre(contexte("pourquoi ma fonction renvoie None ?"));
        String envoyé = corps.get(0);
        assertTrue(envoyé.contains("pourquoi ma fonction renvoie None ?"), envoyé);
        for (String interdit : List.of("Samuel", "@", "email", "apprenant_id", "mot de passe")) {
            assertFalse(envoyé.contains(interdit), "Donnée interdite dans la requête : " + interdit);
        }
    }
}
