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
    @Test void sansClePasDeReseau() {
        assertTrue(new ModeleNvidia(new TransportHttp("")).repondre(CONTEXTE).estRefus());
    }
    @Test void citationInventeeRejetee() {
        String contenu = "{\"version\":\"system-v2\",\"reponse\":\"Une variable contient une valeur.\","
                + "\"notion\":\"VAR\",\"refus\":\"AUCUN\",\"citations\":[{\"section\":\"cours inventé\",\"langage\":\"python\"}]}";
        assertTrue(new ModeleNvidia((u,c,d) -> new TransportModele.Reponse(200, contenu)).repondre(CONTEXTE).estRefus());
    }
}
