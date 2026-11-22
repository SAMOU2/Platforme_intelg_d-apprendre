package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RouterAuthorizationTest {

    @Test
    @DisplayName("Un apprenant ne peut ouvrir que les écrans apprenant")
    void apprenantLimiteAuxEcransApprenant() {
        assertTrue(Router.autorise(Session.Role.APPRENANT, Router.PARCOURS));
        assertTrue(Router.autorise(Session.Role.APPRENANT, Router.RESULTAT));
        assertFalse(Router.autorise(Session.Role.APPRENANT, Router.COMPTES));
        assertFalse(Router.autorise(Session.Role.APPRENANT, Router.CONDITIONS));
        assertFalse(Router.autorise(Session.Role.APPRENANT, Router.RESULTATS));
    }

    @Test
    @DisplayName("Le catalogue des enseignants et l'atelier du niveau sont des écrans apprenant")
    void inscriptionEtAtelierReservesAuxApprenants() {
        // L'apprenant choisit lui-même son enseignant, puis enchaîne cours → exercices → test
        // dans une seule fenêtre : les deux écrans lui appartiennent.
        assertTrue(Router.autorise(Session.Role.APPRENANT, Router.OFFRES));
        assertTrue(Router.autorise(Session.Role.APPRENANT, Router.ATELIER));
        assertFalse(Router.autorise(Session.Role.ENSEIGNANT, Router.OFFRES));
        assertFalse(Router.autorise(Session.Role.ENSEIGNANT, Router.ATELIER));
        assertFalse(Router.autorise(Session.Role.ADMINISTRATEUR, Router.OFFRES));
        assertFalse(Router.autorise(Session.Role.ADMINISTRATEUR, Router.ATELIER));
        assertFalse(Router.autorise(null, Router.OFFRES));
        assertFalse(Router.autorise(null, Router.ATELIER));
    }

    @Test
    @DisplayName("Les étapes de l'atelier s'enchaînent sans revenir au cours")
    void ordreDesEtapesDeAtelier() {
        assertEquals(Session.EtapeAtelier.EXERCICES, Session.EtapeAtelier.COURS.suivante());
        assertEquals(Session.EtapeAtelier.TEST, Session.EtapeAtelier.EXERCICES.suivante());
        assertEquals(Session.EtapeAtelier.TEST, Session.EtapeAtelier.TEST.suivante());
    }

    @Test
    @DisplayName("Les écrans enseignant et administrateur sont distincts")
    void droitsEnseignantEtAdministrateurDistincts() {
        assertTrue(Router.autorise(Session.Role.ENSEIGNANT, Router.CONDITIONS));
        assertTrue(Router.autorise(Session.Role.ENSEIGNANT, Router.RESULTATS));
        assertFalse(Router.autorise(Session.Role.ENSEIGNANT, Router.COMPTES));

        assertTrue(Router.autorise(Session.Role.ADMINISTRATEUR, Router.COMPTES));
        assertFalse(Router.autorise(Session.Role.ADMINISTRATEUR, Router.CONDITIONS));
        assertFalse(Router.autorise(Session.Role.ADMINISTRATEUR, Router.PARCOURS));
    }

    @Test
    @DisplayName("Aucun rôle ne permet d'ouvrir un écran métier")
    void roleAbsentRefuseEcransMetier() {
        assertFalse(Router.autorise(null, Router.PARCOURS));
        assertFalse(Router.autorise(null, Router.COMPTES));
    }
}
