package fr.ihm2026.api.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import fr.ihm2026.api.service.AuthentificationApiHttps;
import fr.ihm2026.core.Session;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "IHM_DB_URL", matches = "jdbc:postgresql://.+")
class PostgresAuthentificationServiceIntegrationTest {

    private PostgresConfiguration configuration;
    private PostgresCompteRepository comptes;
    private PostgresAuthentificationService authentification;
    private String email;
    private final String source = "192.0.2.10";

    @BeforeEach
    void preparer() throws SQLException {
        configuration = PostgresConfiguration.depuisEnvironnement();
        comptes = new PostgresCompteRepository(configuration);
        authentification = new PostgresAuthentificationService(configuration);
        email = "auth-" + UUID.randomUUID() + "@example.org";
        comptes.creer("Compte authentification", email,
                "mot-de-passe-integration-2026".toCharArray(), Session.Role.ENSEIGNANT, true);
    }

    @AfterEach
    void nettoyer() throws SQLException {
        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             PreparedStatement echec = connexion.prepareStatement(
                     "DELETE FROM tentatives_authentification WHERE email_normalise = ?");
             PreparedStatement compte = connexion.prepareStatement(
                     "DELETE FROM utilisateurs WHERE lower(email) = lower(?)")) {
            echec.setString(1, email);
            echec.executeUpdate();
            compte.setString(1, email);
            compte.executeUpdate();
        }
    }

    @Test
    @DisplayName("Le serveur vérifie le mot de passe et le code à usage unique avant la session")
    void connecterValiderEtDeconnecter() throws Exception {
        char[] mauvaisSecret = "mot-de-passe-incorrect".toCharArray();
        var refus = assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.demanderCodeConnexion(email, mauvaisSecret, source));
        assertEquals("Identifiants invalides ou compte indisponible.", refus.getMessage());
        assertTrue(mauvaisSecret[0] == '\0');

        char[] secret = "mot-de-passe-integration-2026".toCharArray();
        var challenge = authentification.demanderCodeConnexion(email.toUpperCase(), secret, source);
        assertTrue(secret[0] == '\0');
        var resultat = authentification.confirmerCodeConnexion(
                challenge.identifiant(), challenge.code());
        assertEquals(Session.Role.ENSEIGNANT, resultat.session().role());
        assertEquals(email, resultat.session().email());
        assertEquals(43, resultat.jeton().length());
        assertTrue(authentification.valider(resultat.jeton()).isPresent());
        assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.confirmerCodeConnexion(
                        challenge.identifiant(), challenge.code()));

        assertFalse(empreinteEnBaseEgaleAuJeton(resultat.jeton()),
                "Seule l'empreinte du jeton doit être conservée.");
        authentification.deconnecter(resultat.jeton());
        assertTrue(authentification.valider(resultat.jeton()).isEmpty());
    }

    @Test
    @DisplayName("Les échecs répétés bloquent les identifiants avec un message générique")
    void limiterLesEchecsDeConnexion() {
        String messageRefus = null;
        for (int i = 0; i < 5; i++) {
            var refus = assertThrows(
                    PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> authentification.demanderCodeConnexion(
                            email, "secret-incorrect-123".toCharArray(), source));
            if (messageRefus == null) {
                messageRefus = refus.getMessage();
            }
            assertEquals(messageRefus, refus.getMessage());
        }
        var bloque = assertThrows(
                PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.demanderCodeConnexion(
                        email, "mot-de-passe-integration-2026".toCharArray(), source));
        assertEquals(messageRefus, bloque.getMessage());
    }

    @Test
    @DisplayName("Un compte inactif ne peut pas obtenir de session")
    void refuserCompteInactif() throws SQLException {
        String emailInactif = "inactive-" + UUID.randomUUID() + "@example.org";
        comptes.creer("Compte inactif", emailInactif,
                "mot-de-passe-integration-2026".toCharArray(), Session.Role.APPRENANT, false);
        try {
            var refus = assertThrows(
                    PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> authentification.demanderCodeConnexion(emailInactif,
                            "mot-de-passe-integration-2026".toCharArray(), source));
            assertEquals("Identifiants invalides ou compte indisponible.", refus.getMessage());
        } finally {
            try (var connexion = DriverManager.getConnection(configuration.url(),
                    configuration.utilisateur(), configuration.motDePasse());
                 PreparedStatement suppression = connexion.prepareStatement(
                         "DELETE FROM utilisateurs WHERE lower(email) = lower(?)")) {
                suppression.setString(1, emailInactif);
                suppression.executeUpdate();
            }
        }
    }

    @Test
    @DisplayName("L'API d'authentification refuse explicitement le transport HTTP")
    void apiRefuseHttpEnClair() throws Exception {
        HttpServer serveur = HttpServer.create(
                new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        serveur.createContext("/api/", new AuthentificationApiHttps(authentification, comptes));
        serveur.start();
        try {
            URI base = URI.create("http://127.0.0.1:" + serveur.getAddress().getPort());
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest connexion = HttpRequest.newBuilder(base.resolve("/api/auth/connexion"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> reponse = client.send(connexion, HttpResponse.BodyHandlers.ofString());
            assertEquals(426, reponse.statusCode());
            assertTrue(new ObjectMapper().readTree(reponse.body()).get("erreur").asText()
                    .contains("HTTPS"));
            assertEquals(1, comptes.lister().stream()
                    .filter(compte -> compte.email().equalsIgnoreCase(email))
                    .count());

            HttpRequest verification = HttpRequest.newBuilder(base.resolve("/api/auth/session"))
                    .GET()
                    .build();
            assertEquals(426, client.send(verification, HttpResponse.BodyHandlers.ofString())
                    .statusCode());
        } finally {
            serveur.stop(0);
        }
    }

    @Test
    @DisplayName("Cinq codes incorrects bloquent aussi le bon code")
    void limiterLesEssaisDuCode() throws SQLException {
        var challenge = authentification.demanderCodeConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source);
        String mauvaisCode = challenge.code().equals("000000") ? "000001" : "000000";
        for (int i = 0; i < 5; i++) {
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> authentification.confirmerCodeConnexion(challenge.identifiant(), mauvaisCode));
        }
        assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.confirmerCodeConnexion(challenge.identifiant(), challenge.code()));
    }

    @Test
    @DisplayName("Redemander un code invalide le précédent")
    void nouveauCodeRemplaceLePrecedent() throws SQLException {
        var ancien = authentification.demanderCodeConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source);
        var nouveau = authentification.demanderCodeConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source);
        assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.confirmerCodeConnexion(ancien.identifiant(), ancien.code()));
        var session = authentification.confirmerCodeConnexion(nouveau.identifiant(), nouveau.code());
        assertTrue(authentification.valider(session.jeton()).isPresent());
    }

    @Test
    @DisplayName("Un appareil de confiance évite le code pendant 48 heures, sans éviter le mot de passe")
    void appareilConfiance48Heures() throws SQLException {
        var challenge = authentification.demanderCodeConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source);
        var premiereSession = authentification.confirmerCodeConnexion(
                challenge.identifiant(), challenge.code(), true, "PC de test");
        var confiance = premiereSession.appareilConfiance();
        assertNotNull(confiance);
        assertEquals(43, confiance.jeton().length());
        assertTrue(confiance.expireLe().isAfter(Instant.now().plus(47, ChronoUnit.HOURS)));
        assertTrue(confiance.expireLe().isBefore(Instant.now().plus(49, ChronoUnit.HOURS)));

        // Fermer la session simule la fermeture de l'application : la confiance subsiste.
        authentification.deconnecter(premiereSession.jeton());
        assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.commencerConnexion(email,
                        "mot-de-passe-toujours-incorrect".toCharArray(), source,
                        confiance.jeton()));

        var directe = authentification.commencerConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source, confiance.jeton());
        assertFalse(directe.codeRequis());
        assertNotNull(directe.session());
        assertEquals(confiance.expireLe(), directe.session().appareilConfiance().expireLe(),
                "Utiliser le poste ne doit pas prolonger la fenêtre de 48 heures.");
        authentification.deconnecter(directe.session().jeton());

        long utilisateurId = premiereSession.session().utilisateurId();
        comptes.definirActif(email, false, utilisateurId);
        assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                () -> authentification.commencerConnexion(email,
                        "mot-de-passe-integration-2026".toCharArray(), source,
                        confiance.jeton()));
        comptes.definirActif(email, true, utilisateurId);
        var apresDesactivation = authentification.commencerConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source, confiance.jeton());
        assertTrue(apresDesactivation.codeRequis(),
                "La désactivation doit révoquer définitivement les appareils de confiance.");
        authentification.annulerCodeConnexion(apresDesactivation.challenge().identifiant());

        var fauxJeton = authentification.commencerConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source, "x".repeat(43));
        assertTrue(fauxJeton.codeRequis());
        authentification.annulerCodeConnexion(fauxJeton.challenge().identifiant());

        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             PreparedStatement expirer = connexion.prepareStatement("""
                     UPDATE appareils_confiance
                     SET expire_le = now() - INTERVAL '1 second'
                     WHERE utilisateur_id = (
                         SELECT id FROM utilisateurs WHERE lower(email) = lower(?))
                     """)) {
            expirer.setString(1, email);
            assertTrue(expirer.executeUpdate() >= 1);
        }
        var apresExpiration = authentification.commencerConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source, confiance.jeton());
        assertTrue(apresExpiration.codeRequis());

        var renouvelee = authentification.confirmerCodeConnexion(
                apresExpiration.challenge().identifiant(), apresExpiration.challenge().code(),
                true, "PC de test");
        assertNotNull(renouvelee.appareilConfiance());
        authentification.deconnecter(renouvelee.jeton());
        authentification.revoquerAppareilConfiance(renouvelee.appareilConfiance().jeton());
        var apresRevocation = authentification.commencerConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source,
                renouvelee.appareilConfiance().jeton());
        assertTrue(apresRevocation.codeRequis());
        authentification.annulerCodeConnexion(apresRevocation.challenge().identifiant());
    }

    @Test
    @DisplayName("Un échec d'insertion de session ne consomme pas le code")
    void echecSessionConserveLeCode() throws SQLException {
        var challenge = authentification.demanderCodeConnexion(email,
                "mot-de-passe-integration-2026".toCharArray(), source);
        // Contrainte propre à ce compte de test ; aucune session existante n'est modifiée.
        String contrainte = "test_auth_" + UUID.randomUUID().toString().replace("-", "");
        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             var requete = connexion.createStatement()) {
            long id = comptes.lister().stream().filter(c -> c.email().equals(email))
                    .findFirst().orElseThrow().id();
            requete.execute("ALTER TABLE sessions ADD CONSTRAINT " + contrainte
                    + " CHECK (utilisateur_id <> " + id + ") NOT VALID");
            try {
                assertThrows(SQLException.class, () -> authentification.confirmerCodeConnexion(
                        challenge.identifiant(), challenge.code()));
            } finally {
                requete.execute("ALTER TABLE sessions DROP CONSTRAINT " + contrainte);
            }
        }
        var session = authentification.confirmerCodeConnexion(challenge.identifiant(), challenge.code());
        assertTrue(authentification.valider(session.jeton()).isPresent());
    }

    private boolean empreinteEnBaseEgaleAuJeton(String jeton) throws SQLException {
        try (var connexion = DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
             PreparedStatement requete = connexion.prepareStatement(
                     "SELECT EXISTS (SELECT 1 FROM sessions WHERE empreinte_jeton = ?)")) {
            requete.setString(1, jeton);
            try (var resultat = requete.executeQuery()) {
                resultat.next();
                return resultat.getBoolean(1);
            }
        }
    }
}
