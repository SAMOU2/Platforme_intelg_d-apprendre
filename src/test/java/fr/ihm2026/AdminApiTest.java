package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.api.service.AdminApi;
import fr.ihm2026.core.Session;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AdminApiTest {

    @Test
    @DisplayName("Un compte désactivé peut être réactivé et désactivé à nouveau")
    void basculerEtatCompte() {
        String email = "yacine.rami@univ.dz";
        try {
            assertFalse(compteActif(email));
            AdminApi.definirCompteActif(email, true);
            assertTrue(compteActif(email));
        } finally {
            AdminApi.definirCompteActif(email, false);
        }
        assertFalse(compteActif(email));
    }

    @Test
    @DisplayName("Le dernier compte administrateur actif ne peut pas être désactivé")
    void protegerDernierAdministrateurActif() {
        assertThrows(IllegalStateException.class,
                () -> AdminApi.definirCompteActif("nadia.cherif@univ.dz", false));
        assertTrue(compteActif("nadia.cherif@univ.dz"));
    }

    @Test
    @DisplayName("La création d'un compte est conservée dans la liste d'administration")
    void creerCompteAjouteLeCompte() {
        String email = "test-" + UUID.randomUUID() + "@univ.dz";
        var compte = AdminApi.creerCompte("Compte de test", email, Session.Role.APPRENANT, true);
        assertEquals(email, compte.getEmail());
        assertTrue(compteActif(email));
    }

    @Test
    @DisplayName("La création avec mot de passe temporaire efface le secret après usage")
    void creerCompteEffaceLeMotDePasse() {
        String email = "test-secret-" + UUID.randomUUID() + "@univ.dz";
        char[] motDePasse = "mot-de-passe-test-2026".toCharArray();

        var compte = AdminApi.creerCompte("Compte sécurisé", email, motDePasse,
                Session.Role.APPRENANT, true);

        assertEquals(email, compte.getEmail());
        assertTrue(compteActif(email));
        assertTrue(java.util.Arrays.equals(new char[motDePasse.length], motDePasse));
    }

    private static boolean compteActif(String email) {
        return AdminApi.comptes().stream()
                .filter(compte -> compte.getEmail().equalsIgnoreCase(email))
                .findFirst()
                .orElseThrow()
                .isActif();
    }
}
