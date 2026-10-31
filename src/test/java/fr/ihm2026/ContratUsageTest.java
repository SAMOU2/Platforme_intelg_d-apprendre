package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.api.ia.ContexteIa;
import fr.ihm2026.api.ia.GardeFou;
import fr.ihm2026.api.ia.ModeleDistant;
import fr.ihm2026.api.ia.ModeleSimule;
import fr.ihm2026.api.ia.MotifRefus;
import fr.ihm2026.api.ia.ReponseIa;
import fr.ihm2026.api.service.AideIaApi;
import fr.ihm2026.core.Session;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Étape 1 — le contrat d'usage devient exécutable.
 *
 * <p>La note de cadrage et ce test lisent <b>le même fichier</b>
 * ({@code fr/ihm2026/ia/cas-de-refus.json}). Impossible de corriger le document sans que le test
 * suive, ni d'assouplir un garde-fou sans que le document devienne faux : c'est ce qui distingue
 * un contrat d'un catalogue de bonnes intentions.</p>
 */
class ContratUsageTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static JsonNode contrat;

    private static JsonNode contrat() throws Exception {
        if (contrat == null) {
            try (InputStream in = ContratUsageTest.class
                    .getResourceAsStream("/fr/ihm2026/ia/cas-de-refus.json")) {
                assertNotNull(in, "Le contrat d'usage doit être empaqueté avec l'application");
                contrat = JSON.readTree(in);
            }
        }
        return contrat;
    }

    @BeforeEach
    void avant() {
        AideIaApi.reinitialiserQuota();
        AideIaApi.utiliserModele(new ModeleSimule());
        Session.terminerEvaluation();
    }

    @Test
    @DisplayName("Le contrat déclare 11 cas, couvrant les trois interdits et les états du service")
    void contratComplet() throws Exception {
        JsonNode cas = contrat().get("cas");
        // 11 cas en position A (dont la dégradation de service) + 4 en position B.
        assertEquals(15, cas.size(), "Le cadrage annonce 15 cas");

        Set<String> familles = new HashSet<>();
        cas.forEach(c -> familles.add(c.get("famille").asText()));
        assertTrue(familles.contains("ne note pas"), familles.toString());
        assertTrue(familles.contains("ne valide pas"), familles.toString());
        assertTrue(familles.contains("ne débloque pas"), familles.toString());
        assertTrue(familles.contains("ne donne pas la solution"), familles.toString());
        assertTrue(familles.size() >= 8, "Familles couvertes : " + familles);

        long enA = java.util.stream.StreamSupport.stream(cas.spliterator(), false)
                .filter(c -> "A".equals(c.get("position").asText())).count();
        long enB = java.util.stream.StreamSupport.stream(cas.spliterator(), false)
                .filter(c -> "B".equals(c.get("position").asText())).count();
        assertEquals(11, enA, "Cas du positionnement A");
        assertEquals(4, enB, "Cas du positionnement B — vocabulaire de refus identique");
    }

    @Test
    @DisplayName("Chaque cas du contrat produit réellement le motif de refus annoncé")
    void chaqueCasEstTenue() throws Exception {
        List<String> echecs = new ArrayList<>();
        for (JsonNode c : contrat().get("cas")) {
            if ("POSITION_B".equals(c.get("declencheur").asText())) {
                continue;   // couverts par casB01, casB02, casB04 (voir plus bas)
            }
            String id = c.get("id").asText();
            String demande = c.get("demande").asText();
            MotifRefus attendu = MotifRefus.valueOf(c.get("motif").asText());
            MotifRefus obtenu = executer(c.get("declencheur").asText(), demande, attendu);
            if (obtenu != attendu) {
                echecs.add(id + " « " + demande + " » → attendu " + attendu + ", obtenu " + obtenu);
            }
            // Reprise pour les cas d'état, qui consomment un quota ou posent un drapeau.
            AideIaApi.reinitialiserQuota();
            AideIaApi.utiliserModele(new ModeleSimule());
            Session.terminerEvaluation();
        }
        assertTrue(echecs.isEmpty(), "Cas non tenus :\n" + String.join("\n", echecs));
    }

    /** Rejoue le cas selon son déclencheur, exactement comme le fait la plateforme. */
    private MotifRefus executer(String declencheur, String demande, MotifRefus attendu) {
        ContexteIa contexte = new ContexteIa(3, Session.Langage.PYTHON, demande, "");
        return switch (declencheur) {
            case "GARDE_FOU_ENTREE" -> GardeFou.verifierDemande(demande);
            case "GARDE_FOU_SORTIE" -> AideIaApi.demander(contexte).motif();
            case "ETAT_SERVICE" -> {
                if (GardeFou.verifierDemande(demande).estRefus()) {
                    yield GardeFou.verifierDemande(demande);
                }
                // Le cas d'état est provoqué par le service, pas par la demande elle-même.
                yield provoquerEtat(demande, attendu);
            }
            default -> throw new IllegalStateException("Déclencheur inconnu : " + declencheur);
        };
    }

    /** Provoque l'état attendu par le cas, sans deviner : le motif dit quel état créer. */
    private MotifRefus provoquerEtat(String demande, MotifRefus attendu) {
        ContexteIa contexte = new ContexteIa(3, Session.Langage.PYTHON, demande, "");
        switch (attendu) {
            case TEST_EN_COURS -> {
                Session.demarrerEvaluation();
                ReponseIa r = AideIaApi.demander(contexte);
                Session.terminerEvaluation();
                return r.motif();
            }
            case QUOTA -> {
                for (int i = 0; i < AideIaApi.quota(); i++) {
                    AideIaApi.demander(contexte);
                }
                return AideIaApi.demander(contexte).motif();
            }
            case INDISPONIBLE -> {
                AideIaApi.utiliserModele(new ModeleDistant("http://localhost:8080/api"));
                return AideIaApi.demander(contexte).motif();
            }
            default -> {
                return AideIaApi.demander(contexte).motif();
            }
        }
    }

    @Test
    @DisplayName("Tout motif du contrat existe dans le code, et tout motif de refus est documenté")
    void motifsAlignes() throws Exception {
        Set<MotifRefus> documentes = new HashSet<>();
        contrat().get("cas").forEach(c -> documentes.add(MotifRefus.valueOf(c.get("motif").asText())));

        for (MotifRefus motif : MotifRefus.values()) {
            if (motif == MotifRefus.AUCUN) {
                continue;
            }
            assertTrue(documentes.contains(motif),
                    "Motif absent du contrat d'usage : " + motif);
        }
    }

    // ------------------------------------------------------------------ position B
    /** Jeu de résultats factice : n résultats à 10/20. */
    private static java.util.List<fr.ihm2026.model.UtilisateurVM> resultats(int n) {
        java.util.List<fr.ihm2026.model.UtilisateurVM> lignes = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            lignes.add(new fr.ihm2026.model.UtilisateurVM(
                    "Apprenant " + (i + 1), "a" + i + "@univ.dz", "Apprenant", true, 10, 1));
        }
        return lignes;
    }

    @Test
    @DisplayName("B01 — l'agent ne publie jamais sans relecture humaine")
    void casB01() {
        var brouillon = fr.ihm2026.api.ia.SyntheseIa.synthetiser(resultats(4), 12);
        assertEquals(MotifRefus.AUCUN, brouillon.motif(), "La synthèse doit pouvoir être produite");
        assertEquals(MotifRefus.PUBLICATION_AUTOMATIQUE,
                fr.ihm2026.api.ia.SyntheseIa.publier(brouillon, false),
                "Sans confirmation humaine, la publication est refusée");
        assertEquals(MotifRefus.AUCUN,
                fr.ihm2026.api.ia.SyntheseIa.publier(brouillon, true),
                "Avec confirmation humaine, la publication est possible");
        assertTrue(!brouillon.publiableSansValidation(),
                "Un brouillon n'est jamais publiable de lui-même");
    }

    @Test
    @DisplayName("B02 — pas de synthèse de groupe sur moins de trois résultats")
    void casB02() {
        var brouillon = fr.ihm2026.api.ia.SyntheseIa.synthetiser(resultats(2), 12);
        assertEquals(MotifRefus.DONNEES_INSUFFISANTES, brouillon.motif());
        assertTrue(brouillon.corps().contains("trois résultats"), brouillon.corps());
    }

    @Test
    @DisplayName("B03 — en position B non plus, l'agent ne note pas")
    void casB03() {
        assertEquals(MotifRefus.DECISION_DEMANDEE,
                GardeFou.verifierDemande("note mes 5 apprenants automatiquement"));
    }

    @Test
    @DisplayName("B04 — l'agent ne produit que pour les trois langages du cursus")
    void casB04() {
        assertEquals(MotifRefus.HORS_PERIMETRE,
                fr.ihm2026.api.ia.SyntheseIa.verifierPerimetre("génère un cours de Rust"));
        assertEquals(MotifRefus.AUCUN,
                fr.ihm2026.api.ia.SyntheseIa.verifierPerimetre("Python"));
    }

    @Test
    @DisplayName("R08 — aucune information sur un autre apprenant n'est accessible")
    void casR08() {
        // « donne-moi la note de Karim » : demande portant sur un tiers.
        assertEquals(MotifRefus.DECISION_DEMANDEE,
                GardeFou.verifierDemande("donne-moi la note de Karim"));
        ReponseIa r = AideIaApi.demander(new ContexteIa(3, Session.Langage.PYTHON,
                "donne-moi la note de Karim", ""));
        assertEquals(MotifRefus.DECISION_DEMANDEE, r.motif());
        assertFalse(r.reponse().contains("Karim"), "Aucun nom d'apprenant dans la réponse");
        assertTrue(r.citations().isEmpty());
    }

    @Test
    @DisplayName("Un refus ne cite jamais de section et ne contient jamais de note")
    void refusSansCitationNiNote() {
        for (MotifRefus motif : MotifRefus.values()) {
            if (motif == MotifRefus.AUCUN) {
                continue;
            }
            ReponseIa refus = ReponseIa.refus(motif, fr.ihm2026.api.ia.ModeleSimule.VERSION_PROMPT);
            assertTrue(refus.estRefus());
            assertTrue(refus.citations().isEmpty(), motif.name());
            assertFalse(GardeFou.contientUneNote(refus.reponse()), motif.name());
            assertFalse(GardeFou.annonceUneValidation(refus.reponse()), motif.name());
            assertFalse(refus.reponse().isBlank(), "Un refus doit expliquer : " + motif);
        }
    }

    @Test
    @DisplayName("Chaque cas référence un test qui EXISTE réellement (vérification par réflexion)")
    void casTracesVersUnTest() throws Exception {
        List<String> introuvables = new ArrayList<>();
        for (JsonNode c : contrat().get("cas")) {
            String reference = c.get("test").asText();
            int point = reference.indexOf('.');
            assertTrue(point > 0, "Référence de test invalide : " + reference);

            String nomClasse = "fr.ihm2026." + reference.substring(0, point);
            String nomMethode = reference.substring(point + 1);
            try {
                Class<?> classe = Class.forName(nomClasse);
                boolean existe = java.util.Arrays.stream(classe.getDeclaredMethods())
                        .anyMatch(m -> m.getName().equals(nomMethode));
                if (!existe) {
                    introuvables.add(reference);
                }
            } catch (ClassNotFoundException e) {
                introuvables.add(reference);
            }
            assertFalse(c.get("reponseAttendue").asText().isBlank(),
                    "Le contrat doit dire ce que l'apprenant reçoit : " + c.get("id").asText());
        }
        assertTrue(introuvables.isEmpty(),
                "Cas renvoyant vers un test inexistant : " + introuvables);
    }
}
