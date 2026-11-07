package fr.ihm2026.ui.commun;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConseilsFlottantsConnexionTest {
    @Test void phrasesCourtesEtNonVides() {
        var conseils = ConseilsFlottantsConnexion.conseils();
        assertEquals(6, conseils.size());
        assertEquals(conseils.size(), conseils.stream().distinct().count());
        assertTrue(conseils.stream().allMatch(s -> !s.isBlank() && s.length() < 100));
    }

    @Test void margePreserveLaColonneEtMasqueLesPetitesFenetres() {
        assertFalse(ConseilsFlottantsConnexion.espaceDisponible(900, 720));
        assertFalse(ConseilsFlottantsConnexion.espaceDisponible(1280, 500));
        assertFalse(ConseilsFlottantsConnexion.espaceDisponible(Double.NaN, 720));
        assertTrue(ConseilsFlottantsConnexion.espaceDisponible(1280, 720));
        double marge = ConseilsFlottantsConnexion.largeurMarge(1280);
        assertEquals(320, marge);
        assertEquals(640, 1280 - 2 * marge); // colonne de 556 px et espace autour
        assertEquals(0, ConseilsFlottantsConnexion.largeurMarge(-1));
    }
}
