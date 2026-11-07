package fr.ihm2026.core;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ThemeConnexionTest {
    @Test void connexionToujoursSombreEtPreferenceConserveeApresConnexion() {
        assertTrue(ThemeManager.paletteSombre(false, true));
        assertTrue(ThemeManager.paletteSombre(true, true));
        assertFalse(ThemeManager.paletteSombre(false, false));
        assertTrue(ThemeManager.paletteSombre(true, false));
    }
}
