package fr.ihm2026;

import fr.ihm2026.api.ia.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RetraitModeleLocalTest {
    @Test void fournisseurInactifSansAppelExterne() {
        var modele = new ModeleIndisponible();
        assertTrue(modele.identifiant().contains("ia-desactivee"));
        assertTrue(modele.repondre(null).estRefus());
    }
    @Test void lexiqueIndependantDesPoids() {
        assertTrue(Tokeniseur.depuisRessource());
        assertTrue(Tokeniseur.mots("Que retourne 7 ?").contains("retour"));
        assertTrue(Tokeniseur.mots("Que retourne 7 ?").contains("7"));
        assertNull(getClass().getResource("/fr/ihm2026/ia/modele-local-v1.json"));
        assertNull(getClass().getResource("/fr/ihm2026/ia/modele-local-v2.json"));
    }
}
