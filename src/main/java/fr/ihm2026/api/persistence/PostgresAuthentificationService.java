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
        return commencerConnexion(email, motDePasse, adresseSource, null).challenge();
    }

    /**
     * Commence une connexion par mot de passe. Un jeton d'appareil de confiance encore
     * valable (48 heures au maximum, sans prolongation) permet d'ouvrir directement la
     * session ; sinon un code e-mail à usage unique reste obligatoire.
     */
    public DebutConnexion commencerConnexion(String email, char[] motDePasse,
            String adresseSource, String jetonConfiance) throws SQLException {
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
            var sessionDirecte = ouvrirSessionAvecAppareilConfiance(compte, jetonConfiance, motDePasse);
            if (sessionDirecte.isPresent()) {
                return new DebutConnexion(null, sessionDirecte.get());
            }
            return new DebutConnexion(creerCodeConnexion(compte, motDePasse), null);
        } finally {
            java.util.Arrays.fill(motDePasse, '\0');
        }
    }

    public ResultatConnexion confirmerCodeConnexion(String identifiant, String code)
            throws SQLException {
        return confirmerCodeConnexion(identifiant, code, false, null);
    }

    public ResultatConnexion confirmerCodeConnexion(String identifiant, String code,
            boolean memoriserAppareil, String libellePoste) throws SQLException {
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
                    AppareilConfiance appareilConfiance = null;
                    if (memoriserAppareil) {
                        var avantConfiance = connexion.setSavepoint();
                        try {
                            appareilConfiance = creerAppareilConfiance(
                                    connexion, compte, libellePoste);
                        } catch (SQLException ex) {
                            if (!"42P01".equals(ex.getSQLState())) {
                                throw ex;
                            }
                            // PC1 pas encore migré : la session reste valable, sans confiance.
                            connexion.rollback(avantConfiance);
                        }
                    }
                    connexion.commit();
                    return appareilConfiance == null ? session
                            : new ResultatConnexion(session.jeton(), session.session(),
                                    appareilConfiance);
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
        String jeton = nouveauJetonOpaque();

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

    private AppareilConfiance creerAppareilConfiance(Connection connexion,
            CompteAuthentifie compte, String libellePoste) throws SQLException {
        String jeton = nouveauJetonOpaque();
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO appareils_confiance
                    (utilisateur_id, empreinte_jeton, libelle_poste, expire_le)
                VALUES (?, ?, ?, now() + INTERVAL '48 hours')
                RETURNING expire_le
                """)) {
            requete.setLong(1, compte.id());
            requete.setString(2, empreinte(jeton));
            requete.setString(3, nettoyerLibellePoste(libellePoste));
            try (ResultSet resultat = requete.executeQuery()) {
                if (!resultat.next()) {
                    throw new SQLException("PostgreSQL n'a retourné aucun appareil de confiance.");
                }
                return new AppareilConfiance(jeton, resultat.getTimestamp(1).toInstant());
            }
        }
    }

    private Optional<ResultatConnexion> ouvrirSessionAvecAppareilConfiance(
            CompteAuthentifie compte, String jetonConfiance, char[] motDePasse) throws SQLException {
        if (!jetonOpaqueValide(jetonConfiance)) {
            return Optional.empty();
        }
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try (PreparedStatement requete = connexion.prepareStatement("""
                    SELECT a.id, a.expire_le
                    FROM appareils_confiance a
                    JOIN utilisateurs u ON u.id = a.utilisateur_id
                    WHERE a.utilisateur_id = ?
                      AND a.empreinte_jeton = ?
                      AND a.revoque_le IS NULL
                      AND a.expire_le > now()
                      AND u.actif
                      AND u.statut_inscription = 'ACCEPTE'
                      AND u.mot_de_passe = crypt(?, u.mot_de_passe)
                    FOR UPDATE OF a, u
                    """)) {
                requete.setLong(1, compte.id());
                requete.setString(2, empreinte(jetonConfiance));
                requete.setString(3, new String(motDePasse));
                java.util.UUID appareilId;
                Instant expireLe;
                try (ResultSet resultat = requete.executeQuery()) {
                    if (!resultat.next()) {
                        connexion.rollback();
                        return Optional.empty();
                    }
                    appareilId = resultat.getObject("id", java.util.UUID.class);
                    expireLe = resultat.getTimestamp("expire_le").toInstant();
                }
                try (PreparedStatement toucher = connexion.prepareStatement("""
                        UPDATE appareils_confiance
                        SET derniere_utilisation = now()
                        WHERE id = ?
                        """)) {
                    toucher.setObject(1, appareilId);
                    toucher.executeUpdate();
                }
                ResultatConnexion session = creerSession(connexion, compte);
                connexion.commit();
                return Optional.of(new ResultatConnexion(session.jeton(), session.session(),
                        new AppareilConfiance(jetonConfiance, expireLe)));
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                if (ex instanceof SQLException sql && "42P01".equals(sql.getSQLState())) {
                    return Optional.empty();
                }
                throw ex;
            }
        }
    }

    /** Révoque un appareil de confiance ; le prochain accès exigera un nouveau code. */
    public void revoquerAppareilConfiance(String jetonConfiance) throws SQLException {
        if (!jetonOpaqueValide(jetonConfiance)) {
            return;
        }
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement("""
                     UPDATE appareils_confiance
                     SET revoque_le = now()
                     WHERE empreinte_jeton = ? AND revoque_le IS NULL
                     """)) {
            requete.setString(1, empreinte(jetonConfiance));
            requete.executeUpdate();
        }
        // Une base non migrée ou inaccessible ne doit pas être annoncée comme révoquée.
    }

    private static String nouveauJetonOpaque() {
        byte[] aleatoire = new byte[32];
        ALEA.nextBytes(aleatoire);
        String jeton = Base64.getUrlEncoder().withoutPadding().encodeToString(aleatoire);
        java.util.Arrays.fill(aleatoire, (byte) 0);
        return jeton;
    }

    private static boolean jetonOpaqueValide(String jeton) {
        return jeton != null && jeton.length() >= 32 && jeton.length() <= 128
                && jeton.chars().allMatch(caractere -> caractere == '-' || caractere == '_'
                        || (caractere >= 'A' && caractere <= 'Z')
                        || (caractere >= 'a' && caractere <= 'z')
                        || (caractere >= '0' && caractere <= '9'));
    }

    private static String nettoyerLibellePoste(String libelle) {
        if (libelle == null) {
            return null;
        }
        String propre = libelle.replaceAll("[\\p{Cntrl}]", " ").strip();
        if (propre.isBlank()) {
            return null;
        }
        return propre.length() <= 80 ? propre : propre.substring(0, 80).strip();
    }

    private ChallengeConnexion creerCodeConnexion(CompteAuthentifie compte, char[] motDePasse) throws SQLException {
        String identifiant = java.util.UUID.randomUUID().toString();
        String code = String.format(Locale.ROOT, "%06d", ALEA.nextInt(1_000_000));
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            // Revérifier sous verrou : un ancien mot de passe ne doit pas créer de code
            // après une réinitialisation concurrente.
            try (PreparedStatement verrou = connexion.prepareStatement("""
                    SELECT 1 FROM utilisateurs WHERE id = ? AND actif AND statut_inscription = 'ACCEPTE'
                    AND mot_de_passe = crypt(?, mot_de_passe) FOR UPDATE
                    """)) {
                verrou.setLong(1, compte.id()); verrou.setString(2, new String(motDePasse));
                try (ResultSet r = verrou.executeQuery()) {
                    if (!r.next()) { connexion.rollback(); throw new AuthentificationRefuseeException(); }
                }
            }
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

    /** Crée au plus trois codes par compte et vingt par adresse source et par heure. */
    public Optional<ChallengeReinitialisation> demanderReinitialisation(String email, String source)
            throws SQLException {
        String adresse;
        try { adresse = PostgresCompteRepository.normaliserEmail(email); }
        catch (IllegalArgumentException ex) { return Optional.empty(); }
        if (adresse.length() > 254 || source == null || source.isBlank()) return Optional.empty();
        try (Connection c = ouvrir()) {
            c.setAutoCommit(false);
            try {
                // Nettoyage opportuniste des quotas périmés ; aucune donnée utilisateur en clair.
                if (ALEA.nextInt(1000) == 0) {
                    try (PreparedStatement purge = c.prepareStatement(
                            "DELETE FROM quotas_reinitialisation WHERE debut < now() - INTERVAL '2 hours'")) {
                        purge.executeUpdate();
                    }
                }
                // Un ordre constant évite les interblocages entre demandes concurrentes.
                boolean quotaEmail = quotaReinitialisation(c, "e:" + empreinte(adresse), 3);
                boolean quotaSource = quotaReinitialisation(c, "s:" + empreinte(source), 20);
                if (!quotaEmail || !quotaSource) { c.commit(); return Optional.empty(); }
                long id;
                String nom, destination;
                try (PreparedStatement q = c.prepareStatement("""
                        SELECT id, nom, email FROM utilisateurs WHERE lower(email) = ?
                        AND actif AND statut_inscription = 'ACCEPTE' FOR UPDATE
                        """)) {
                    q.setString(1, adresse);
                    try (ResultSet r = q.executeQuery()) {
                        if (!r.next()) { c.commit(); return Optional.empty(); }
                        id = r.getLong("id"); nom = r.getString("nom"); destination = r.getString("email");
                    }
                }
                String code = String.format(Locale.ROOT, "%08d", ALEA.nextInt(100_000_000));
                try (PreparedStatement q = c.prepareStatement("""
                        INSERT INTO reinitialisations_mot_de_passe(utilisateur_id, empreinte_code, expire_le, echecs)
                        VALUES (?, crypt(?, gen_salt('bf', 12)), now() + INTERVAL '10 minutes', 0)
                        ON CONFLICT (utilisateur_id) DO UPDATE SET empreinte_code = EXCLUDED.empreinte_code,
                            expire_le = EXCLUDED.expire_le, echecs = 0
                        """)) {
                    q.setLong(1, id); q.setString(2, code); q.executeUpdate();
                }
                c.commit();
                return Optional.of(new ChallengeReinitialisation(destination, nom, code));
            } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
        }
    }

    private static boolean quotaReinitialisation(Connection c, String cle, int maximum) throws SQLException {
        try (PreparedStatement q = c.prepareStatement("""
                INSERT INTO quotas_reinitialisation(cle, debut, nombre) VALUES (?, now(), 1)
                ON CONFLICT (cle) DO UPDATE SET
                  nombre = CASE WHEN quotas_reinitialisation.debut <= now() - INTERVAL '1 hour'
                                THEN 1 ELSE quotas_reinitialisation.nombre + 1 END,
                  debut = CASE WHEN quotas_reinitialisation.debut <= now() - INTERVAL '1 hour'
                               THEN now() ELSE quotas_reinitialisation.debut END
                RETURNING nombre
                """)) {
            q.setString(1, cle);
            try (ResultSet r = q.executeQuery()) { return r.next() && r.getInt(1) <= maximum; }
        }
    }

    public void reinitialiserMotDePasse(String email, String code, char[] nouveau) throws SQLException {
        Objects.requireNonNull(nouveau, "nouveau");
        try {
            PostgresCompteRepository.validerCompte("Compte", email, nouveau, Session.Role.APPRENANT);
            if (code == null || !code.matches("[0-9]{8}")) throw new AuthentificationRefuseeException();
            try (Connection c = ouvrir()) {
                c.setAutoCommit(false);
                try {
                    long id;
                    boolean correspond;
                    try (PreparedStatement q = c.prepareStatement("""
                            SELECT u.id, r.empreinte_code = crypt(?, r.empreinte_code) AS valide
                            FROM utilisateurs u JOIN reinitialisations_mot_de_passe r ON r.utilisateur_id = u.id
                            WHERE lower(u.email) = ? AND u.actif AND u.statut_inscription = 'ACCEPTE'
                              AND r.expire_le > now() AND r.echecs < 5 FOR UPDATE OF u, r
                            """)) {
                        q.setString(1, code); q.setString(2, PostgresCompteRepository.normaliserEmail(email));
                        try (ResultSet r = q.executeQuery()) {
                            if (!r.next()) { c.rollback(); throw new AuthentificationRefuseeException(); }
                            id = r.getLong("id"); correspond = r.getBoolean("valide");
                        }
                    }
                    if (!correspond) {
                        try (PreparedStatement q = c.prepareStatement("UPDATE reinitialisations_mot_de_passe SET echecs = echecs + 1 WHERE utilisateur_id = ?")) {
                            q.setLong(1, id); q.executeUpdate();
                        }
                        c.commit();
                        throw new AuthentificationRefuseeException();
                    }
                    try (PreparedStatement q = c.prepareStatement("UPDATE utilisateurs SET mot_de_passe = crypt(?, gen_salt('bf', 12)) WHERE id = ?")) {
                        q.setString(1, new String(nouveau)); q.setLong(2, id); q.executeUpdate();
                    }
                    for (String sql : new String[] {
                        "DELETE FROM reinitialisations_mot_de_passe WHERE utilisateur_id = ?",
                        "DELETE FROM codes_connexion WHERE utilisateur_id = ?",
                        "DELETE FROM sessions WHERE utilisateur_id = ?",
                        "DELETE FROM appareils_confiance WHERE utilisateur_id = ?"
                    }) {
                        try (PreparedStatement q = c.prepareStatement(sql)) { q.setLong(1, id); q.executeUpdate(); }
                    }
                    c.commit();
                } catch (SQLException | RuntimeException ex) { c.rollback(); throw ex; }
            }
        } finally { java.util.Arrays.fill(nouveau, '\0'); }
    }

    public record ChallengeReinitialisation(String email, String nom, String code) {}

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

    public record DebutConnexion(ChallengeConnexion challenge, ResultatConnexion session) {
        public boolean codeRequis() {
            return session == null;
        }
    }

    public record AppareilConfiance(String jeton, Instant expireLe) {
    }

    public record ResultatConnexion(String jeton, SessionAuthentifiee session,
            AppareilConfiance appareilConfiance) {
        public ResultatConnexion(String jeton, SessionAuthentifiee session) {
            this(jeton, session, null);
        }
    }

    public static final class AuthentificationRefuseeException extends RuntimeException {
        public AuthentificationRefuseeException() {
            super(MESSAGE_REFUS);
        }
    }
}
