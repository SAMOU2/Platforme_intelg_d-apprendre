package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import fr.ihm2026.model.EtatNiveau;
import fr.ihm2026.model.NiveauVM;
import fr.ihm2026.model.ResultatVM;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests du socle : les règles affichées par l'interface (aucune dépendance JavaFX). */
class ModeleTest {

    @Test
    @DisplayName("Un niveau verrouillé explique toujours pourquoi (IHM-02)")
    void niveauVerrouilleExplique() {
        NiveauVM niveau = new NiveauVM(4, "Structures de données", EtatNiveau.VERROUILLE, 12, 0, 0, "Python");
        assertTrue(niveau.getExplication().contains("niveau 3"),
                "L'explication doit nommer le niveau qui débloque celui-ci");
        assertEquals("Verrouillé", niveau.getLibelleEtat());
        assertEquals("etat-verrouille", niveau.getEtat().styleClass());
    }

    @Test
    @DisplayName("Un niveau validé rappelle la note et le seuil")
    void niveauValideExplique() {
        NiveauVM niveau = new NiveauVM(1, "Variables", EtatNiveau.VALIDE, 10, 16, 3, "Python");
        assertTrue(niveau.getExplication().contains("16"));
        assertTrue(niveau.getExplication().contains("10"));
    }

    @Test
    @DisplayName("Un échec calcule l'écart au seuil (BF-R3)")
    void ecartAuSeuil() {
        ResultatVM echec = new ResultatVM(9, 12, List.of("3 questions fausses"), List.of("Niveau 3"), 1);
        assertFalse(echec.isReussi());
        assertEquals(3, echec.getEcart());

        ResultatVM reussite = new ResultatVM(14, 12, List.of(), List.of(), 2);
        assertTrue(reussite.isReussi());
        assertEquals(0, reussite.getEcart(), "Aucun écart quand le seuil est atteint");
    }

    @Test
    @DisplayName("Les parcours sont indépendants : trois langages, trois jeux de niveaux")
    void parcoursIndependants() {
        for (Session.Langage langage : Session.Langage.values()) {
            var niveaux = fr.ihm2026.api.mock.MockBackend.niveaux(langage);
            assertEquals(5, niveaux.size(), "Chaque parcours compte 5 niveaux");
            assertEquals(langage.label(), niveaux.get(0).getLangage());
        }
    }

    @Test
    @DisplayName("La correction simulée respecte le seuil du niveau")
    void correctionSimulee() {
        ResultatVM resultat = fr.ihm2026.api.mock.MockBackend.corriger(3, 3, 12);
        assertEquals(20, resultat.getNote());
        assertTrue(resultat.isReussi());

        ResultatVM echec = fr.ihm2026.api.mock.MockBackend.corriger(1, 3, 12);
        assertEquals(7, echec.getNote());
        assertFalse(echec.isReussi());
        assertFalse(echec.getCoursARevoir().isEmpty(), "Un échec propose toujours des cours à revoir (BF-R4)");
    }
}
