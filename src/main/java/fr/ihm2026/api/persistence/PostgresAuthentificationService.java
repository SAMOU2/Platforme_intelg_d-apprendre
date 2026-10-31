package fr.ihm2026.api.persistence;

import fr.ihm2026.core.Session;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Vérifie les credentials et gère des sessions opaques persistées dans PostgreSQL.
 * La couche HTTP appelante doit exposer ces opérations uniquement sur HTTPS.
 */
public final class PostgresAuthentificationService {

    private static final int ECHECS_MAX = 5;
    private static final SecureRandom ALEA = new SecureRandom();
    private static final String MESSAGE_REFUS = "Identifiants invalides ou compte indisponible.";

    private final PostgresConfiguration configuration;

    public PostgresAuthentificationService(PostgresConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public ChallengeConnexion demanderCodeConnexion(String email, char[] motDePasse,
            String adresseSource) throws SQLException {
        Objects.requireNonNull(motDePasse, "motDePasse");
        if (adresseSource == null || adresseSource.isBlank()) {
            java.util.Arrays.fill(motDePasse, '\0');
            throw new IllegalArgumentException("L'adresse source est obligatoire.");
        }

        String emailCle = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        String emailNormalise;
        try {
            emailNormalise = PostgresCompteRepository.normaliserEmail(email);
        } catch (IllegalArgumentException ex) {
            emailNormalise = emailCle;
        }

        try {
            if (tropDechecs(emailNormalise, empreinte(adresseSource))) {
                throw new AuthentificationRefuseeException();
            }

            CompteAuthentifie compte = verifierCompte(emailNormalise, motDePasse);
            if (compte == null || !compte.actif() || !"ACCEPTE".equals(compte.statutInscription())) {
                enregistrerEchec(emailNormalise, empreinte(adresseSource));
                throw new AuthentificationRefuseeException();
            }

            effacerEchecs(emailNormalise, empreinte(adresseSource));
            return creerCodeConnexion(compte);
        } finally {
            java.util.Arrays.fill(motDePasse, '\0');
        }
    }

    public ResultatConnexion confirmerCodeConnexion(String identifiant, String code)
            throws SQLException {
        java.util.UUID id;
        try {
            id = java.util.UUID.fromString(identifiant);
        } catch (RuntimeException ex) {
            throw new AuthentificationRefuseeException();
        }
        if (code == null || !code.matches("[0-9]{6}")) {
            throw new AuthentificationRefuseeException();
        }

        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT c.utilisateur_id, c.empreinte_code, c.echecs,
                           u.nom, u.email, u.role::text AS role, u.actif,
                           u.statut_inscription
                    FROM codes_connexion c
                    JOIN utilisateurs u ON u.id = c.utilisateur_id
                    WHERE c.identifiant = ? AND c.expire_le > now()
                    FOR UPDATE OF c, u
                    """)) {
                requete.setObject(1, id);
                try (ResultSet resultat = requete.executeQuery()) {
                    if (!resultat.next() || resultat.getInt("echecs") >= 5
                            || !resultat.getBoolean("actif")
                            || !"ACCEPTE".equals(resultat.getString("statut_inscription"))) {
                        connexion.rollback();
                        throw new AuthentificationRefuseeException();
                    }
                    String empreinteCode = resultat.getString("empreinte_code");
                    boolean valide;
                    try (PreparedStatement verifier = connexion.prepareStatement(
                            "SELECT crypt(?, ?) = ?")) {
                        verifier.setString(1, code);
                        verifier.setString(2, empreinteCode);
                        verifier.setString(3, empreinteCode);
                        try (ResultSet resultatVerification = verifier.executeQuery()) {
                            valide = resultatVerification.next() && resultatVerification.getBoolean(1);
                        }
                    }
                    if (!valide) {
                        try (PreparedStatement incrementer = connexion.prepareStatement("""
                                UPDATE codes_connexion SET echecs = echecs + 1
                                WHERE identifiant = ?
                                """)) {
                            incrementer.setObject(1, id);
                            incrementer.executeUpdate();
                        }
                        connexion.commit();
                        throw new AuthentificationRefuseeException();
                    }
                    CompteAuthentifie compte = new CompteAuthentifie(resultat.getLong("utilisateur_id"),
                            resultat.getString("nom"), resultat.getString("email"),
                            Session.Role.valueOf(resultat.getString("role")), true,
                            resultat.getString("statut_inscription"));
                    try (PreparedStatement supprimer = connexion.prepareStatement(
                            "DELETE FROM codes_connexion WHERE identifiant = ?")) {
                        supprimer.setObject(1, id);
                        supprimer.executeUpdate();
                    }
                    ResultatConnexion session = creerSession(connexion, compte);
                    connexion.commit();
                    return session;
                }
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                throw ex;
            }
        }
    }

    public void annulerCodeConnexion(String identifiant) throws SQLException {
        try {
            java.util.UUID id = java.util.UUID.fromString(identifiant);
            try (Connection connexion = ouvrir();
                 PreparedStatement requete = connexion.prepareStatement(
                         "DELETE FROM codes_connexion WHERE identifiant = ?")) {
                requete.setObject(1, id);
                requete.executeUpdate();
            }
        } catch (IllegalArgumentException ex) {
            throw new AuthentificationRefuseeException();
        }
    }

    public Optional<SessionAuthentifiee> valider(String jeton) throws SQLException {
        if (jeton == null || jeton.length() > 128) {
            return Optional.empty();
        }
        String empreinteJeton = empreinte(jeton);
        String sqlSession = """
                SELECT s.id, s.utilisateur_id, s.expiration_max,
                       u.nom, u.email, u.role::text AS role, u.actif,
                       u.statut_inscription
                FROM sessions s
                JOIN utilisateurs u ON u.id = s.utilisateur_id
                WHERE s.empreinte_jeton = ?
                  AND s.expire_le > now()
                  AND s.expiration_max > now()
                FOR UPDATE OF s, u
                """;
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try (PreparedStatement requete = connexion.prepareStatement(sqlSession)) {
                requete.setString(1, empreinteJeton);
                try (ResultSet resultat = requete.executeQuery()) {
                    if (!resultat.next() || !resultat.getBoolean("actif")
                            || !"ACCEPTE".equals(resultat.getString("statut_inscription"))) {
                        connexion.commit();
                        return Optional.empty();
                    }
                    java.util.UUID sessionId = resultat.getObject("id", java.util.UUID.class);
                    CompteAuthentifie compte = new CompteAuthentifie(
                            resultat.getLong("utilisateur_id"), resultat.getString("nom"),
                            resultat.getString("email"), Session.Role.valueOf(resultat.getString("role")),
                            true, "ACCEPTE");
                    try (PreparedStatement renouveler = connexion.prepareStatement("""
                            UPDATE sessions
                            SET derniere_activite = now(),
                                expire_le = LEAST(expiration_max, now() + INTERVAL '30 minutes')
                            WHERE id = ?
                            RETURNING expire_le
                            """)) {
                        renouveler.setObject(1, sessionId);
                        try (ResultSet maj = renouveler.executeQuery()) {
                            if (!maj.next()) {
                                throw new SQLException("La session n'a pas pu être renouvelée.");
                            }
                            Instant expiration = maj.getTimestamp(1).toInstant();
                            connexion.commit();
                            return Optional.of(new SessionAuthentifiee(compte.id(), compte.nom(),
                                    compte.email(), compte.role(), expiration));
                        }
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                throw ex;
            }
        }
    }

    public void deconnecter(String jeton) throws SQLException {
        if (jeton == null || jeton.length() > 128) {
            return;
        }
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement(
                     "DELETE FROM sessions WHERE empreinte_jeton = ?")) {
            requete.setString(1, empreinte(jeton));
            requete.executeUpdate();
        }
    }

    private CompteAuthentifie verifierCompte(String email, char[] motDePasse) throws SQLException {
        String sql = """
                SELECT id, nom, email, role::text AS role, actif, statut_inscription,
                       mot_de_passe = crypt(?, mot_de_passe) AS mot_de_passe_valide
                FROM utilisateurs
                WHERE lower(email) = ?
                """;
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setString(1, new String(motDePasse));
            requete.setString(2, email);
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    simulerVerificationCouteuse(motDePasse);
                    return null;
                }
                if (!resultat.getBoolean("mot_de_passe_valide")) {
                    return null;
                }
                return new CompteAuthentifie(resultat.getLong("id"), resultat.getString("nom"),
                        resultat.getString("email"),
                        Session.Role.valueOf(resultat.getString("role")),
                        resultat.getBoolean("actif"), resultat.getString("statut_inscription"));
            }
        }
    }

    private void simulerVerificationCouteuse(char[] motDePasse) throws SQLException {
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement(
                     "SELECT crypt(?, gen_salt('bf', 12))")) {
            requete.setString(1, new String(motDePasse));
            requete.executeQuery().close();
        }
    }

    private ResultatConnexion creerSession(Connection connexion, CompteAuthentifie compte)
            throws SQLException {
        byte[] aleatoire = new byte[32];
        ALEA.nextBytes(aleatoire);
        String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatoire);
        java.util.Arrays.fill(aleatoire, (byte) 0);

        String sql = """
                INSERT INTO sessions
                    (utilisateur_id, empreinte_jeton, cree_le, derniere_activite,
                     expire_le, expiration_max)
                VALUES (?, ?, now(), now(), now() + INTERVAL '30 minutes',
                        now() + INTERVAL '8 hours')
                RETURNING expire_le
                """;
        try (PreparedStatement requete = connexion.prepareStatement(sql)) {
            requete.setLong(1, compte.id());
            requete.setString(2, empreinte(jeton));
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    throw new SQLException("PostgreSQL n'a retourné aucune session.");
                }
                Instant expiration = resultat.getTimestamp(1).toInstant();
                SessionAuthentifiee session = new SessionAuthentifiee(compte.id(), compte.nom(),
                        compte.email(), compte.role(), expiration);
                return new ResultatConnexion(jeton, session);
            }
        }
    }

    private ChallengeConnexion creerCodeConnexion(CompteAuthentifie compte) throws SQLException {
        String identifiant = java.util.UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", ALEA.nextInt(1_000_000));
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try (PreparedStatement supprimer = connexion.prepareStatement(
                    "DELETE FROM codes_connexion WHERE utilisateur_id = ?")) {
                supprimer.setLong(1, compte.id());
                supprimer.executeUpdate();
            }
            try (PreparedStatement inserer = connexion.prepareStatement("""
                    INSERT INTO codes_connexion
                        (identifiant, utilisateur_id, empreinte_code, expire_le)
                    VALUES (?::uuid, ?, crypt(?, gen_salt('bf', 12)), now() + INTERVAL '10 minutes')
                    """)) {
                inserer.setString(1, identifiant);
                inserer.setLong(2, compte.id());
                inserer.setString(3, code);
                inserer.executeUpdate();
                connexion.commit();
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                throw ex;
            }
        }
        return new ChallengeConnexion(identifiant, code, compte.email(), compte.nom());
    }

    private boolean tropDechecs(String email, String source) throws SQLException {
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement("""
                     SELECT echecs >= ?
                     FROM tentatives_authentification
                     WHERE email_normalise = ?
                       AND empreinte_source = ?
                       AND debut_fenetre > now() - INTERVAL '15 minutes'
                     """)) {
            requete.setInt(1, ECHECS_MAX);
            requete.setString(2, email);
            requete.setString(3, source);
            try (ResultSet resultat = requete.executeQuery()) {
                return resultat.next() && resultat.getBoolean(1);
            }
        }
    }

    private void enregistrerEchec(String email, String source) throws SQLException {
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement("""
                     INSERT INTO tentatives_authentification
                         (email_normalise, empreinte_source, debut_fenetre, echecs)
                     VALUES (?, ?, now(), 1)
                     ON CONFLICT (email_normalise, empreinte_source) DO UPDATE
                     SET echecs = CASE
                             WHEN tentatives_authentification.debut_fenetre
                                  <= now() - INTERVAL '15 minutes' THEN 1
                             ELSE tentatives_authentification.echecs + 1
                         END,
                         debut_fenetre = CASE
                             WHEN tentatives_authentification.debut_fenetre
                                  <= now() - INTERVAL '15 minutes' THEN now()
                             ELSE tentatives_authentification.debut_fenetre
                         END
                     """)) {
            requete.setString(1, email);
            requete.setString(2, source);
            requete.executeUpdate();
        }
    }

    private void effacerEchecs(String email, String source) throws SQLException {
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement("""
                     DELETE FROM tentatives_authentification
                     WHERE email_normalise = ? AND empreinte_source = ?
                     """)) {
            requete.setString(1, email);
            requete.setString(2, source);
            requete.executeUpdate();
        }
    }

    private Connection ouvrir() throws SQLException {
        return java.sql.DriverManager.getConnection(configuration.url(),
                configuration.utilisateur(), configuration.motDePasse());
    }

    private static String empreinte(String valeur) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(valeur.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 est indisponible.", ex);
        }
    }

    private record CompteAuthentifie(long id, String nom, String email, Session.Role role,
            boolean actif, String statutInscription) {
    }

    public record ChallengeConnexion(String identifiant, String code, String email, String nom) {
    }

    public record SessionAuthentifiee(long utilisateurId, String nom, String email, Session.Role role,
            Instant expireLe) {
    }

    public record ResultatConnexion(String jeton, SessionAuthentifiee session) {
    }

    public static final class AuthentificationRefuseeException extends RuntimeException {
        public AuthentificationRefuseeException() {
            super(MESSAGE_REFUS);
        }
    }
}
