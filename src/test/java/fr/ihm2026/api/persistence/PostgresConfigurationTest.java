package fr.ihm2026.api.persistence;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PostgresConfigurationTest {

    @Test
    @DisplayName("La configuration PostgreSQL exige URL, utilisateur et secret")
    void configurationIncompleteRefusee() {
        assertThrows(IllegalStateException.class, () -> PostgresConfiguration.depuis(Map.of()));
        assertThrows(IllegalStateException.class, () -> PostgresConfiguration.depuis(Map.of(
                "IHM_DB_URL", "jdbc:postgresql://localhost/ihm2026",
                "IHM_DB_USER", "ihm2026")));
    }

    @Test
    @DisplayName("La configuration masque le mot de passe lorsqu'elle est affichée")
    void configurationNeRevelePasLeSecret() {
        var configuration = PostgresConfiguration.depuis(Map.of(
                "IHM_DB_URL", "jdbc:postgresql://localhost/ihm2026",
                "IHM_DB_USER", "ihm2026",
                "IHM_DB_PASSWORD", "secret-de-test"));
        assertTrue(configuration.toString().contains("<masqué>"));
        assertTrue(!configuration.toString().contains("secret-de-test"));
    }

    @Test
    @DisplayName("Le secret PostgreSQL conserve les espaces extérieurs")
    void configurationConserveLesEspacesDuSecret() {
        var configuration = PostgresConfiguration.depuis(Map.of(
                "IHM_DB_URL", "jdbc:postgresql://localhost/ihm2026",
                "IHM_DB_USER", "ihm2026",
                "IHM_DB_PASSWORD", " secret de test "));
        assertEquals(" secret de test ", configuration.motDePasse());
    }

    @Test
    @DisplayName("L'adresse e-mail est normalisée sans dépendre de la locale")
    void normaliserEmail() {
        assertEquals("samuel@example.org",
                PostgresCompteRepository.normaliserEmail("  Samuel@Example.org "));
        assertThrows(IllegalArgumentException.class,
                () -> PostgresCompteRepository.normaliserEmail("adresse invalide"));
    }

    @Test
    @DisplayName("Un nouveau compte exige une adresse, un rôle et un mot de passe long")
    void validerCompte() {
        assertDoesNotThrow(() -> PostgresCompteRepository.validerCompte("Nom", "a@exemple.org",
                "mot-de-passe-valide".toCharArray(), Session.Role.APPRENANT));
        assertThrows(IllegalArgumentException.class,
                () -> PostgresCompteRepository.validerCompte("Nom", "a@exemple.org",
                        "trop-court".toCharArray(), Session.Role.APPRENANT));
    }

    @Test
    @DisplayName("La limite bcrypt est vérifiée en octets UTF-8 et le secret est effacé au refus")
    void rejeterMotDePasseAuDelaDeLaLimiteBcrypt() {
        var configuration = PostgresConfiguration.depuis(Map.of(
                "IHM_DB_URL", "jdbc:postgresql://localhost/ihm2026",
                "IHM_DB_USER", "ihm2026",
                "IHM_DB_PASSWORD", "secret"));
        var depot = new PostgresCompteRepository(configuration);
        assertDoesNotThrow(() -> PostgresCompteRepository.validerCompte(
                "Nom", "a@exemple.org", "é".repeat(36).toCharArray(), Session.Role.APPRENANT));
        assertThrows(IllegalArgumentException.class, () -> PostgresCompteRepository.validerCompte(
                "Nom", "a@exemple.org", "😀".repeat(6).toCharArray(), Session.Role.APPRENANT));
        char[] motDePasse = "é".repeat(37).toCharArray();

        assertThrows(IllegalArgumentException.class, () -> depot.creer(
                "Nom", "a@exemple.org", motDePasse, Session.Role.APPRENANT, true));
        assertArrayEquals(new char[motDePasse.length], motDePasse);
    }
}
