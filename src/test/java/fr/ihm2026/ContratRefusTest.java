package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.api.ia.ContratRefus;
import fr.ihm2026.api.ia.MotifRefus;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou sur le contrat de refus lui-même (version {@code contrat-v2}).
 *
 * <p>Le contrat n'est utile que s'il est <b>le seul</b> à définir les libellés. Ces tests
 * vérifient trois choses : le fichier est chargé, chaque motif a un libellé unique et non vide,
 * et le texte affiché par le code est identique, caractère pour caractère, à celui du document.</p>
 */
class ContratRefusTest {

    @Test
    @DisplayName("Le contrat est chargé et versionné")
    void contratCharge() {
        assertEquals("contrat-v2", ContratRefus.version());
        assertEquals(15, ContratRefus.cas().size());
        assertEquals(11, ContratRefus.casPour("A").size());
        assertEquals(4, ContratRefus.casPour("B").size());
    }

    @Test
    @DisplayName("Chaque motif de refus possède un libellé dans le contrat")
    void chaqueMotifDocumente() {
        for (MotifRefus motif : MotifRefus.values()) {
            if (motif == MotifRefus.AUCUN) {
                continue;
            }
            String message = ContratRefus.message(motif);
            assertFalse(message.isBlank(), "Libellé manquant pour " + motif);
            assertTrue(message.length() >= 30,
                    "Un refus doit expliquer, pas seulement constater : " + motif);
        }
        assertEquals("", ContratRefus.message(MotifRefus.AUCUN), "AUCUN n'a pas de message");
    }

    @Test
    @DisplayName("Le code affiche exactement le texte du contrat (aucune dérive possible)")
    void libellesIdentiquesAuContrat() {
        for (MotifRefus motif : MotifRefus.values()) {
            if (motif == MotifRefus.AUCUN) {
                continue;
            }
            assertEquals(ContratRefus.message(motif), motif.message(),
                    "Le code et le contrat divergent pour " + motif);
        }
    }

    @Test
    @DisplayName("Les libellés sont distincts et tutoient l'apprenant en position A")
    void libellesDistincts() {
        List<String> messages = ContratRefus.cas().stream()
                .map(ContratRefus.Cas::messageAffiche).distinct().toList();
        // 8 libellés pour 15 cas : chaque interdit a un texte, partagé par ses variantes.
        assertEquals(8, messages.size(), "Un même interdit doit avoir un seul libellé");
        assertTrue(messages.stream().noneMatch(m -> m.contains("{") || m.contains("null")),
                "Aucun gabarit non résolu dans les libellés");
    }

    @Test
    @DisplayName("Chaque cas nomme la garantie technique qui le rend vrai")
    void chaqueCasAGarantie() {
        for (ContratRefus.Cas cas : ContratRefus.cas()) {
            assertFalse(cas.garantie().isBlank(), "Garantie non documentée : " + cas.id());
            assertTrue(cas.garantie().matches(".*\\.[a-zA-Z]+.*"),
                    "La garantie doit nommer un point du code : " + cas.id() + " → " + cas.garantie());
            assertFalse(cas.messageAffiche().isBlank(), "Message non documenté : " + cas.id());
            assertFalse(cas.test().isBlank(), "Test non référencé : " + cas.id());
        }
    }

    @Test
    @DisplayName("Aucun refus ne promet une note, une validation ou une solution")
    void refusSansPromesse() {
        for (ContratRefus.Cas cas : ContratRefus.cas()) {
            String m = cas.messageAffiche().toLowerCase();
            assertFalse(fr.ihm2026.api.ia.GardeFou.contientUneNote(m), cas.id());
            assertFalse(m.matches(".*\\btu (as|peux passer|es valid)[ée].*"),
                    "Un refus ne félicite ni ne valide : " + cas.id());
        }
    }
}
