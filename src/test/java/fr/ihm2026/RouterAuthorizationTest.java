package fr.ihm2026;

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
