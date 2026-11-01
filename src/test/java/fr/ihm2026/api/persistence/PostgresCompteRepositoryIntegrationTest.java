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

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Session.Role.class, names = {"APPRENANT", "ENSEIGNANT"})
    void inscriptionSansPinPuisActivation(Session.Role role) throws Exception {
        var configuration = PostgresConfiguration.depuis(System.getenv());
        var depot = new PostgresCompteRepository(configuration);
        var auth = new PostgresAuthentificationService(configuration);
        String email = "activation-" + UUID.randomUUID() + "@example.org";
        long admin = administrateurId(configuration);
        try {
            char[] secret = "mot-de-passe-inscription-2026".toCharArray();
            var compte = depot.inscrireCompte("Compte en attente", email, secret, role);
            assertTrue(secret[0] == '\0');
            assertFalse(compte.actif());
            assertEquals("EN_ATTENTE", compte.statutInscription());
            assertTrue(inscriptionJournalisee(configuration, email));
            assertThrows(IllegalArgumentException.class, () -> depot.activerCompte(email, "12345678", role));
            assertThrows(IllegalStateException.class, () -> depot.definirActif(email, true, admin));
            var decision = depot.deciderInscription(email, true, admin);
            assertEquals("ACCEPTE", decision.compte().statutInscription());
            assertFalse(decision.compte().actif());
            assertTrue(decision.activation().pin().matches("[0-9]{8}"));
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.demanderCodeConnexion(email, "mot-de-passe-inscription-2026".toCharArray(), "192.0.2.1"));
            assertThrows(IllegalStateException.class, () -> depot.definirActif(email, true, admin));
            assertThrows(IllegalArgumentException.class, () -> depot.activerCompte(email,
                    decision.activation().pin(), role == Session.Role.APPRENANT ? Session.Role.ENSEIGNANT : Session.Role.APPRENANT));
            depot.activerCompte(email.toUpperCase(Locale.ROOT), decision.activation().pin(), role);
            assertThrows(IllegalArgumentException.class, () -> depot.activerCompte(email, decision.activation().pin(), role));
            var codeConnexion = auth.demanderCodeConnexion(email, "mot-de-passe-inscription-2026".toCharArray(), "192.0.2.1");
            var session = auth.confirmerCodeConnexion(codeConnexion.identifiant(), codeConnexion.code());
            assertEquals(role, session.session().role());
        } finally {
            nettoyerActivation(configuration, email);
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = Session.Role.class, names = {"APPRENANT", "ENSEIGNANT"})
    void pinLimiteExpireEtRenvoi(Session.Role role) throws Exception {
        var configuration = PostgresConfiguration.depuis(System.getenv());
        var depot = new PostgresCompteRepository(configuration);
        String email = "pin-" + UUID.randomUUID() + "@example.org";
        long admin = administrateurId(configuration);
        try {
            depot.inscrireCompte("PIN", email, "mot-de-passe-inscription-2026".toCharArray(), role);
            var decision = depot.deciderInscription(email, true, admin);
            String pin = decision.activation().pin();
            String faux = pin.equals("00000000") ? "00000001" : "00000000";
            for (int i = 0; i < 5; i++) {
                assertThrows(IllegalArgumentException.class, () -> depot.activerCompte(email, faux, role));
            }
            assertThrows(IllegalArgumentException.class, () -> depot.activerCompte(email, pin, role));
            var nouveau = depot.creerInvitation(email, role, admin);
            try (var c = DriverManager.getConnection(configuration.url(), configuration.utilisateur(), configuration.motDePasse());
                 var q = c.prepareStatement("UPDATE invitations_inscription SET expire_le = now() - interval '1 second' WHERE email_cible = ? AND utilise_le IS NULL")) {
                q.setString(1, email);
                q.executeUpdate();
            }
            assertThrows(IllegalArgumentException.class, () -> depot.activerCompte(email, nouveau.pin(), role));
            var dernier = depot.creerInvitation(email, role, admin);
            depot.activerCompte(email, dernier.pin(), role);
            assertThrows(IllegalArgumentException.class, () -> depot.creerInvitation(email, role, admin));
        } finally {
            nettoyerActivation(configuration, email);
        }
    }

    @Test
    void refusEtInscriptionAdminInterdite() throws Exception {
        var configuration = PostgresConfiguration.depuis(System.getenv());
        var depot = new PostgresCompteRepository(configuration);
        String email = "refus-" + UUID.randomUUID() + "@example.org";
        long admin = administrateurId(configuration);
        char[] secret = "mot-de-passe-inscription-2026".toCharArray();
        assertThrows(IllegalArgumentException.class, () -> depot.inscrireCompte("Admin public", email, secret, Session.Role.ADMINISTRATEUR));
        assertTrue(secret[0] == '\0');
        try {
            depot.inscrireCompte("Refus", email, "mot-de-passe-inscription-2026".toCharArray(), Session.Role.APPRENANT);
            var refus = depot.deciderInscription(email, false, admin);
            assertEquals("REFUSE", refus.compte().statutInscription());
            assertFalse(refus.compte().actif());
            assertEquals(null, refus.activation());
            assertThrows(IllegalStateException.class, () -> depot.definirActif(email, true, admin));
            assertThrows(IllegalArgumentException.class, () -> depot.creerInvitation(email, Session.Role.APPRENANT, admin));
        } finally {
            nettoyerActivation(configuration, email);
        }
    }

    private static void nettoyerActivation(PostgresConfiguration configuration, String email) throws SQLException {
        try (var c = DriverManager.getConnection(configuration.url(), configuration.utilisateur(), configuration.motDePasse())) {
            for (String sql : new String[]{"DELETE FROM invitations_inscription WHERE email_cible = ?",
                    "DELETE FROM tentatives_authentification WHERE email_normalise = ?",
                    "DELETE FROM utilisateurs WHERE lower(email) = ?"}) {
                try (var q = c.prepareStatement(sql)) {
                    q.setString(1, email);
                    q.executeUpdate();
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
