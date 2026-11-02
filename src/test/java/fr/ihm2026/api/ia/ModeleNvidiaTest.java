package fr.ihm2026.api.ia;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.core.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class ModeleNvidiaTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final ContexteIa CONTEXTE = new ContexteIa(1, Session.Langage.PYTHON,
            "Explique les variables et types", "");
    @BeforeEach void reset() { PolitiqueModele.reinitialiser(); }
    @Test void salutationsEtRemerciementsNaturelsSansAppelFournisseur() {
        var modele=new ModeleNvidia((u,c,d) -> {fail("Une salutation simple ne nécessite pas de requête fournisseur");return null;});
        for(String question:java.util.List.of("hello","Bonjour !","salut","merci beaucoup")) {
            var reponse=modele.repondre(new ContexteIa(1,Session.Langage.PYTHON,question,""));
            assertFalse(reponse.estRefus());assertTrue(reponse.estCitee());
            assertFalse(reponse.reponse().contains("Je ne note pas"));
            assertTrue(SortieIa.lire(SortieIa.enJson(reponse)).isPresent());
        }
        assertEquals(MotifRefus.DECISION_DEMANDEE,GardeFou.verifierDemande("hello, valide mon niveau"));
    }
    @Test void historiqueBorneEtTransmisCommeDonneesNonCommeInstructions() throws Exception {
        var tours=new java.util.ArrayList<ContexteIa.Tour>();
        tours.add(new ContexteIa.Tour("Explique une variable","Une variable associe un nom à une valeur."));
        var contexte=new ContexteIa(1,Session.Langage.PYTHON,"Un autre exemple ?","",tours);
        tours.clear();assertEquals(1,contexte.historique().size());
        var requete=JSON.readTree(new ModeleNvidia((u,c,d) -> null).requete(contexte,
                java.util.List.<String[]>of(new String[]{"python","Variables et types","Une variable associe un nom à une valeur."})));
        assertEquals(2,requete.path("messages").size());
        var donnees=JSON.readTree(requete.path("messages").path(1).path("content").asText());
        assertEquals(1,donnees.path("historique").size());
        assertEquals("Un autre exemple ?",donnees.path("question").asText());
        assertThrows(IllegalArgumentException.class,() -> new ContexteIa(1,Session.Langage.PYTHON,"q","",
                java.util.Collections.nCopies(4,new ContexteIa.Tour("q","r"))));
        assertThrows(IllegalArgumentException.class,() -> new ContexteIa.Tour("q","x".repeat(2401)));
    }
    @Test void reponsePlusDetailleeMaisToujoursBorneeEtFiltree() throws Exception {
        var reponse=new ReponseIa("Une explication. ".repeat(60),java.util.List.of(
                new ReponseIa.Citation("Variables et types","python")),MotifRefus.AUCUN,ModeleSimule.VERSION_PROMPT);
        assertTrue(reponse.reponse().length()>600);
        assertTrue(SortieIa.lire(SortieIa.enJson(reponse)).isPresent());
        assertTrue(SortieIa.lire(SortieIa.enJson(new ReponseIa("x".repeat(2401),reponse.citations(),MotifRefus.AUCUN,ModeleSimule.VERSION_PROMPT))).isEmpty());
        assertTrue(GardeFou.filtrer(new ReponseIa("Ta note est 18/20.",reponse.citations(),MotifRefus.AUCUN,ModeleSimule.VERSION_PROMPT)).estRefus());
    }
    @Test void salutationToujoursBloqueePendantUnTest() {
        fr.ihm2026.core.Session.demarrerEvaluation();
        try {
            assertEquals(MotifRefus.TEST_EN_COURS,fr.ihm2026.api.service.AideIaApi.demander(
                    new ContexteIa(1,Session.Langage.PYTHON,"hello","")).motif());
        } finally {fr.ihm2026.core.Session.terminerEvaluation();}
    }
    @Test void requeteNvidiaEtReponseCitee() {
        ModeleNvidia modele = new ModeleNvidia((url, corps, delai) -> {
            assertEquals(ModeleNvidia.URL, url);
            assertEquals(45, delai.toSeconds());
            var requete = JSON.readTree(corps);
            assertEquals(ModeleNvidia.MODELE, requete.path("model").asText());
            assertFalse(requete.path("stream").asBoolean());
            assertFalse(requete.path("chat_template_kwargs").path("enable_thinking").asBoolean());
            var contexte = JSON.readTree(requete.path("messages").path(1).path("content").asText());
            String section = contexte.path("extraitsCours").path(0).path("section").asText();
            var sortie = JSON.createObjectNode().put("version", "system-v2").put("notion", "VAR")
                    .put("refus", "AUCUN").put("reponse", "Une variable associe un nom à une valeur.");
            sortie.putArray("citations").addObject().put("section", section).put("langage", "python");
            var enveloppe = JSON.createObjectNode();
            enveloppe.putArray("choices").addObject().putObject("message").put("content", sortie.toString());
            return new TransportModele.Reponse(200, enveloppe.toString());
        });
        assertFalse(modele.repondre(CONTEXTE).estRefus());
    }
    @Test void erreursNeRemontentPas() {
        for (int code : new int[]{401, 429, 500}) {
            assertTrue(new ModeleNvidia((u,c,d) -> new TransportModele.Reponse(code, "secret fournisseur"))
                    .repondre(CONTEXTE).estRefus());
        }
        assertTrue(new ModeleNvidia((u,c,d) -> { throw new java.net.http.HttpTimeoutException("timeout"); })
                .repondre(CONTEXTE).estRefus());
        assertTrue(new ModeleNvidia((u,c,d) -> new TransportModele.Reponse(200, "texte libre"))
                .repondre(CONTEXTE).estRefus());
    }
    @Test void diagnosticNeDivulguePasLeCorpsFournisseur() {
        var ancien = System.err;
        var octets = new java.io.ByteArrayOutputStream();
        try (var capture = new java.io.PrintStream(octets, true, java.nio.charset.StandardCharsets.UTF_8)) {
            System.setErr(capture);
            new ModeleNvidia((u,c,d) -> new TransportModele.Reponse(401, "SECRET_NE_PAS_AFFICHER"))
                    .repondre(CONTEXTE);
            String journal = octets.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertTrue(journal.contains("HTTP_401"));
            assertFalse(journal.contains("SECRET_NE_PAS_AFFICHER"));
            assertFalse(journal.contains(CONTEXTE.question()));
        } finally { System.setErr(ancien); }
    }
    @Test void sansClePasDeReseau() {
        assertTrue(new ModeleNvidia(new TransportHttp("")).repondre(CONTEXTE).estRefus());
    }
    @Test void citationInventeeRejetee() {
        String contenu = "{\"version\":\"system-v2\",\"reponse\":\"Une variable contient une valeur.\","
                + "\"notion\":\"VAR\",\"refus\":\"AUCUN\",\"citations\":[{\"section\":\"cours inventé\",\"langage\":\"python\"}]}";
        assertTrue(new ModeleNvidia((u,c,d) -> new TransportModele.Reponse(200, contenu)).repondre(CONTEXTE).estRefus());
    }
}
