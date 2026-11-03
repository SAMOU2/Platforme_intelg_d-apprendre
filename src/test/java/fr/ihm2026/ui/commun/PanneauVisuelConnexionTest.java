package fr.ihm2026.ui.commun;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import java.io.InputStream;
import javafx.geometry.Rectangle2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PanneauVisuelConnexionTest {

    @Test
    @DisplayName("Tous les rôles reçoivent le fond plein écran animé")
    void fondPleinEcranTousLesRoles() {
        for (Session.Role role : Session.Role.values()) {
            assertTrue(PanneauVisuelConnexion.roleEligible(role),
                    "Rôle sans fond animé : " + role);
        }
        assertFalse(PanneauVisuelConnexion.roleEligible(null));
    }

    @Test
    @DisplayName("Le cadre remplit le panneau sans déformer ni sortir de l’image")
    void cadreSansDeformation() {
        Rectangle2D cadre = PanneauVisuelConnexion.cadreImage(998, 470, 500, 700, 0.35);

        assertEquals(500.0 / 700.0, cadre.getWidth() / cadre.getHeight(), 0.000001);
        assertTrue(cadre.getMinX() >= 0);
        assertTrue(cadre.getMinY() >= 0);
        assertTrue(cadre.getMaxX() <= 998);
        assertTrue(cadre.getMaxY() <= 470);
    }

    @Test
    @DisplayName("La progression déplace le regard vers les écrans et zoome légèrement")
    void panoramiqueProgressif() {
        Rectangle2D debut = PanneauVisuelConnexion.cadreImage(998, 470, 500, 700, 0);
        Rectangle2D fin = PanneauVisuelConnexion.cadreImage(998, 470, 500, 700, 1);

        assertTrue(fin.getMinX() + fin.getWidth() / 2
                > debut.getMinX() + debut.getWidth() / 2);
        assertTrue(fin.getWidth() < debut.getWidth());
        assertEquals(debut, PanneauVisuelConnexion.cadreImage(998, 470, 500, 700, -2));
        assertEquals(fin, PanneauVisuelConnexion.cadreImage(998, 470, 500, 700, 2));
        assertEquals(Rectangle2D.EMPTY,
                PanneauVisuelConnexion.cadreImage(998, 470, 0, 700, 0.5));
    }

    @Test
    @DisplayName("L’illustration nettoyée est présente dans les ressources")
    void ressourcePresente() throws Exception {
        try (InputStream flux = PanneauVisuelConnexion.class.getResourceAsStream(
                PanneauVisuelConnexion.ressourceImage())) {
            assertNotNull(flux);
            byte[] signature = flux.readNBytes(2);
            assertEquals((byte) 0xff, signature[0]);
            assertEquals((byte) 0xd8, signature[1]);
        }
    }
}
