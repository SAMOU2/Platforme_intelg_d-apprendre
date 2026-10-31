package fr.ihm2026.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "IHM_DB_URL", matches = "jdbc:postgresql://.+")
class PostgresCompteRepositoryIntegrationTest {

    @Test
    @DisplayName("L'inscription reste en attente et journalise le rôle demandé")
    void inscriptionCreeUnApprenantEtJournalise() throws Exception {
        PostgresConfiguration configuration = PostgresConfiguration.depuis(System.getenv());
        PostgresCompteRepository depot = new PostgresCompteRepository(configuration);
        String email = "inscription-" + UUID.randomUUID() + "@example.org";
        PostgresCompteRepository.Compte cree = null;
        PostgresCompteRepository.Invitation invitation = null;
        try {
            invitation = depot.creerInvitation(email, Session.Role.APPRENANT,
                    administrateurId(configuration));
            cree = depot.inscrireCompte("Apprenant inscrit", email,
                    "mot-de-passe-inscription-2026".toCharArray(),
                    Session.Role.APPRENANT, invitation.pin());
            String pinConsomme = invitation.pin();

            assertEquals(Session.Role.APPRENANT, cree.role());
            assertFalse(cree.actif());
            assertEquals("EN_ATTENTE", cree.statutInscription());
            assertTrue(inscriptionJournalisee(configuration, email));
            assertThrows(IllegalArgumentException.class, () -> depot.inscrireCompte(
                    "Apprenant inscrit", email, "autre-mot-de-passe-2026".toCharArray(),
                    Session.Role.APPRENANT, pinConsomme),
                    "Un PIN déjà consommé ne peut pas servir une seconde fois.");
        } finally {
            try (var connexion = DriverManager.getConnection(configuration.url(),
                    configuration.utilisateur(), configuration.motDePasse());
                 PreparedStatement suppressionInvitations = connexion.prepareStatement(
                         "DELETE FROM invitations_inscription WHERE lower(email_cible) = lower(?)");
                 PreparedStatement suppressionCompte = connexion.prepareStatement(
                         "DELETE FROM utilisateurs WHERE lower(email) = lower(?)")) {
                suppressionInvitations.setString(1, email);
                suppressionInvitations.executeUpdate();
                if (cree != null) {
                    suppressionCompte.setString(1, email);
                    suppressionCompte.executeUpdate();
                }
            }
        }
    }

    @Test
    @DisplayName("Le compte est stocké avec bcrypt et relu depuis une nouvelle connexion")
    void creerEtRelireComptePersiste() throws Exception {
        PostgresConfiguration configuration = PostgresConfiguration.depuis(System.getenv());
        PostgresCompteRepository depot = new PostgresCompteRepository(configuration);
        String email = "integration-" + UUID.randomUUID() + "@example.org";
        String secret = "mot-de-passe-integration-2026";
        char[] secretEffacable = secret.toCharArray();
        PostgresCompteRepository.Compte cree = null;

        try {
            cree = depot.creer("Test intégration", email,
                    secretEffacable, Session.Role.APPRENANT, true);

            assertTrue(secretEffacable[0] == '\0', "Le secret fourni doit être effacé.");
            SQLException doublon = assertThrows(SQLException.class,
                    () -> depot.creer("Doublon", email.toUpperCase(Locale.ROOT),
                            "mot-de-passe-doublon-2026".toCharArray(),
                            Session.Role.APPRENANT, true));
            assertEquals("23505", doublon.getSQLState(),
                    "Une adresse e-mail déjà utilisée doit être refusée.");
            assertFalse(verificationBcrypt(configuration, email, secret),
                    "Le secret ne doit pas être stocké en clair.");
            assertTrue(verifierSecret(configuration, email, secret),
                    "Le hachage PostgreSQL doit vérifier le secret.");

            PostgresCompteRepository.Compte compteCree = cree;
            PostgresCompteRepository.Compte relu = depot.lister().stream()
                    .filter(compte -> compte.id() == compteCree.id())
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Le compte créé doit être relisible."));
            assertEquals(email.toLowerCase(), relu.email());
            assertEquals(Session.Role.APPRENANT, relu.role());
            assertTrue(relu.actif());
        } finally {
            if (cree != null) {
                try (var connexion = DriverManager.getConnection(configuration.url(),
                        configuration.utilisateur(), configuration.motDePasse());
                     PreparedStatement suppression = connexion.prepareStatement(
                             "DELETE FROM utilisateurs WHERE email = ?")) {
                    suppression.setString(1, email.toLowerCase());
                    suppression.executeUpdate();
                }
            }
        }
    }

    private static boolean verificationBcrypt(PostgresConfiguration configuration, String email,
            String secret) throws Exception {
        return lireVerification(configuration,
                "SELECT mot_de_passe = ? FROM utilisateurs WHERE email = ?", secret, email);
    }

    private static boolean verifierSecret(PostgresConfiguration configuration, String email,
            String secret) throws Exception {
        return lireVerification(configuration,
                "SELECT mot_de_passe = crypt(?, mot_de_passe) FROM utilisateurs WHERE email = ?",
                secret, email);
    }

    private static boolean lireVerification(PostgresConfiguration configuration, String sql,
            String secret, String email) throws Exception {
        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setString(1, secret);
            requete.setString(2, email);
            try (var resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    throw new AssertionError("Le compte d'intégration n'a pas été créé.");
                }
                return resultat.getBoolean(1);
            }
        }
    }

    private static boolean inscriptionJournalisee(PostgresConfiguration configuration,
            String email) throws Exception {
        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             PreparedStatement requete = connexion.prepareStatement("""
                     SELECT EXISTS (SELECT 1 FROM journal_audit
                     WHERE action = 'INSCRIPTION_COMPTE' AND cible = ? AND acteur_id IS NULL)
                     """)) {
            requete.setString(1, email.toLowerCase(Locale.ROOT));
            try (var resultat = requete.executeQuery()) {
                return resultat.next() && resultat.getBoolean(1);
            }
        }
    }

    private static long administrateurId(PostgresConfiguration configuration) throws Exception {
        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             PreparedStatement requete = connexion.prepareStatement("""
                     SELECT id FROM utilisateurs
                     WHERE role = 'ADMINISTRATEUR' AND actif
                     ORDER BY id LIMIT 1
                     """);
             var resultat = requete.executeQuery()) {
            if (!resultat.next()) {
                throw new AssertionError("L'intégration PostgreSQL requiert un administrateur actif.");
            }
            return resultat.getLong(1);
        }
    }
}
