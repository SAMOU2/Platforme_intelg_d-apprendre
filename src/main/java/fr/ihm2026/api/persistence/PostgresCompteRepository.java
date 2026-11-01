package fr.ihm2026.api.persistence;

import fr.ihm2026.core.Session;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.security.SecureRandom;

/**
 * Stockage des comptes dans PostgreSQL. Les mots de passe sont hachés dans PostgreSQL par
 * pgcrypto ; aucun secret en clair n'est écrit dans la table utilisateurs.
 */
public final class PostgresCompteRepository {

    private static final SecureRandom ALEA = new SecureRandom();
    private final PostgresConfiguration configuration;

    public PostgresCompteRepository(PostgresConfiguration configuration) {
        this.configuration = Objects.requireNonNull(configuration, "configuration");
    }

    public static PostgresCompteRepository depuisEnvironnement() {
        return new PostgresCompteRepository(PostgresConfiguration.depuisEnvironnement());
    }

    public List<Compte> lister() throws SQLException {
        String sql = """
                SELECT id, nom, email, role::text AS role, actif, statut_inscription
                FROM utilisateurs
                ORDER BY CASE statut_inscription WHEN 'EN_ATTENTE' THEN 0 ELSE 1 END, nom, email
                """;
        try (Connection connexion = ouvrir();
             PreparedStatement requete = connexion.prepareStatement(sql);
             ResultSet resultat = requete.executeQuery()) {
            List<Compte> comptes = new ArrayList<>();
            while (resultat.next()) {
                comptes.add(lireCompte(resultat));
            }
            return List.copyOf(comptes);
        }
    }

    public Compte creer(String nom, String email, char[] motDePasse, Session.Role role, boolean actif)
            throws SQLException {
        return creer(nom, email, motDePasse, role, actif, null, null);
    }

    public Compte creer(String nom, String email, char[] motDePasse, Session.Role role, boolean actif,
            long acteurId) throws SQLException {
        return creer(nom, email, motDePasse, role, actif, Long.valueOf(acteurId), "CREATION_COMPTE");
    }

    /** Demande publique : aucun PIN avant la décision, jamais de rôle administrateur. */
    public Compte inscrireCompte(String nom, String email, char[] motDePasse, Session.Role role)
            throws SQLException {
        Objects.requireNonNull(motDePasse, "motDePasse");
        try {
            validerCompte(nom, email, motDePasse, role);
            if (role != Session.Role.APPRENANT && role != Session.Role.ENSEIGNANT) {
                throw new IllegalArgumentException("L'inscription publique est réservée aux apprenants et enseignants.");
            }
            return creer(nom, email, motDePasse, role, false, null, "INSCRIPTION_COMPTE", "EN_ATTENTE");
        } finally {
            Arrays.fill(motDePasse, '\0');
        }
    }

    /** Renvoi réservé à un compte accepté qui attend encore son activation. */
    public Invitation creerInvitation(String email, Session.Role role, long acteurId) throws SQLException {
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try (PreparedStatement q = connexion.prepareStatement("""
                    SELECT id, nom, email, role::text AS role, actif, statut_inscription
                    FROM utilisateurs WHERE lower(email) = ? FOR UPDATE
                    """)) {
                q.setString(1, normaliserEmail(email));
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("Compte introuvable.");
                    Compte compte = lireCompte(r);
                    if (compte.role() != role || compte.actif() || !"ACCEPTE".equals(compte.statutInscription())) {
                        throw new IllegalArgumentException("Le renvoi exige un compte accepté en attente d'activation.");
                    }
                    Invitation invitation = creerPinActivation(connexion, compte, acteurId);
                    connexion.commit();
                    return invitation;
                }
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                throw ex;
            }
        }
    }

    private Invitation creerPinActivation(Connection connexion, Compte compte, long acteurId) throws SQLException {
        String pin = String.format(Locale.ROOT, "%08d", ALEA.nextInt(100_000_000));
        try (PreparedStatement q = connexion.prepareStatement("""
                UPDATE invitations_inscription SET utilise_le = now()
                WHERE lower(email_cible) = ? AND role_cible = ? AND utilise_le IS NULL
                """)) {
            q.setString(1, compte.email());
            q.setString(2, compte.role().name());
            q.executeUpdate();
        }
        try (PreparedStatement q = connexion.prepareStatement("""
                INSERT INTO invitations_inscription
                    (id, email_cible, role_cible, empreinte_pin, expire_le, cree_par)
                VALUES (?, ?, ?, crypt(?, gen_salt('bf', 12)), now() + interval '48 hours', ?)
                RETURNING expire_le
                """)) {
            q.setObject(1, UUID.randomUUID());
            q.setString(2, compte.email());
            q.setString(3, compte.role().name());
            q.setString(4, pin);
            q.setLong(5, acteurId);
            try (ResultSet r = q.executeQuery()) {
                if (!r.next()) throw new SQLException("Création du PIN impossible.");
                journaliser(connexion, acteurId, "CREATION_PIN_ACTIVATION", compte.email(), compte.role().name(), false);
                return new Invitation(compte.email(), compte.role(), pin, r.getTimestamp(1).toInstant());
            }
        }
    }

    public void activerCompte(String email, String pin, Session.Role role) throws SQLException {
        if (pin == null || !pin.matches("[0-9]{8}") || role == null) {
            throw new IllegalArgumentException("PIN d'activation invalide, expiré ou déjà utilisé.");
        }
        try (Connection c = ouvrir()) {
            c.setAutoCommit(false);
            try (PreparedStatement q = c.prepareStatement("""
                    SELECT u.id AS utilisateur_id, i.id, crypt(?, i.empreinte_pin) = i.empreinte_pin AS valide
                    FROM utilisateurs u JOIN invitations_inscription i
                      ON lower(i.email_cible) = lower(u.email) AND i.role_cible = u.role::text
                    WHERE lower(u.email) = ? AND u.role::text = ?
                      AND u.statut_inscription = 'ACCEPTE' AND NOT u.actif
                      AND i.utilise_le IS NULL AND i.expire_le > now() AND i.echecs < 5
                    FOR UPDATE OF u, i
                    """)) {
                q.setString(1, pin);
                q.setString(2, normaliserEmail(email));
                q.setString(3, role.name());
                try (ResultSet r = q.executeQuery()) {
                    if (!r.next()) throw new IllegalArgumentException("PIN d'activation invalide, expiré ou déjà utilisé.");
                    UUID id = r.getObject("id", UUID.class);
                    if (!r.getBoolean("valide")) {
                        try (PreparedStatement e = c.prepareStatement("UPDATE invitations_inscription SET echecs = echecs + 1 WHERE id = ?")) {
                            e.setObject(1, id);
                            e.executeUpdate();
                        }
                        c.commit();
                        throw new IllegalArgumentException("PIN d'activation invalide, expiré ou déjà utilisé.");
                    }
                    try (PreparedStatement e = c.prepareStatement("UPDATE invitations_inscription SET utilise_le = now() WHERE id = ?")) {
                        e.setObject(1, id);
                        e.executeUpdate();
                    }
                    try (PreparedStatement e = c.prepareStatement("UPDATE utilisateurs SET actif = true WHERE id = ?")) {
                        e.setLong(1, r.getLong("utilisateur_id"));
                        e.executeUpdate();
                    }
                    journaliser(c, null, "ACTIVATION_PAR_PIN", normaliserEmail(email), role.name(), true);
                    c.commit();
                }
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        }
    }

    /** Crée le premier administrateur uniquement si aucun compte administrateur n'existe. */
    public void assurerAdministrateurInitialDepuisEnvironnement() throws SQLException {
            String nom = System.getenv("IHM_BOOTSTRAP_ADMIN_NAME");
            String email = System.getenv("IHM_BOOTSTRAP_ADMIN_EMAIL");
            String motDePasse = System.getenv("IHM_BOOTSTRAP_ADMIN_PASSWORD");
            boolean aucun = (nom == null || nom.isBlank())
                    && (email == null || email.isBlank())
                    && (motDePasse == null || motDePasse.isBlank());
            boolean complet = nom != null && !nom.isBlank()
                    && email != null && !email.isBlank()
                    && motDePasse != null && !motDePasse.isBlank();

            try (Connection connexion = ouvrir()) {
                connexion.setAutoCommit(false);
                try {
                    try (PreparedStatement verrou = connexion.prepareStatement(
                            "SELECT pg_advisory_xact_lock(20261031, 1)")) {
                        verrou.execute();
                    }
                    long admins;
                    try (PreparedStatement requete = connexion.prepareStatement("""
                            SELECT count(*) FROM utilisateurs
                            WHERE role = 'ADMINISTRATEUR'
                            """);
                         ResultSet resultat = requete.executeQuery()) {
                        resultat.next();
                        admins = resultat.getLong(1);
                    }
                    if (admins > 0) {
                        connexion.commit();
                        return;
                    }
                    // Les variables d'amorçage ne servent plus une fois l'admin créé.
                    if (!aucun && !complet) {
                        throw new IllegalStateException(
                                "Pour l'amorçage admin, définir ensemble IHM_BOOTSTRAP_ADMIN_NAME, "
                                        + "IHM_BOOTSTRAP_ADMIN_EMAIL et IHM_BOOTSTRAP_ADMIN_PASSWORD.");
                    }
                    if (aucun) {
                        throw new IllegalStateException(
                                "Aucun administrateur n'existe. Configurer les variables "
                                        + "IHM_BOOTSTRAP_ADMIN_* pour créer le premier compte.");
                    }
                    char[] secret = motDePasse.toCharArray();
                    try {
                        validerCompte(nom, email, secret, Session.Role.ADMINISTRATEUR);
                        try (PreparedStatement inserer = connexion.prepareStatement("""
                                INSERT INTO utilisateurs
                                    (nom, email, mot_de_passe, role, actif, statut_inscription)
                                VALUES (?, ?, crypt(?, gen_salt('bf', 12)),
                                        'ADMINISTRATEUR'::role_utilisateur, true, 'ACCEPTE')
                                RETURNING email
                                """)) {
                            inserer.setString(1, nom.strip());
                            inserer.setString(2, normaliserEmail(email));
                            inserer.setString(3, new String(secret));
                            try (ResultSet resultat = inserer.executeQuery()) {
                                resultat.next();
                                journaliser(connexion, null, "AMORCAGE_ADMINISTRATEUR",
                                        resultat.getString(1), "ADMINISTRATEUR", true);
                            }
                        }
                    } finally {
                        Arrays.fill(secret, '\0');
                    }
                    connexion.commit();
                } catch (SQLException | RuntimeException ex) {
                    connexion.rollback();
                    throw ex;
                }
            }
        }

    private Compte creer(String nom, String email, char[] motDePasse, Session.Role role, boolean actif,
            Long acteurId, String actionAudit) throws SQLException {
        return creer(nom, email, motDePasse, role, actif, acteurId, actionAudit, "ACCEPTE");
    }

    private Compte creer(String nom, String email, char[] motDePasse, Session.Role role, boolean actif,
            Long acteurId, String actionAudit, String statut) throws SQLException {
        Objects.requireNonNull(motDePasse, "motDePasse");
        try {
            validerCompte(nom, email, motDePasse, role);
            String sql = """
                    INSERT INTO utilisateurs
                        (nom, email, mot_de_passe, role, actif, statut_inscription)
                    VALUES (?, ?, crypt(?, gen_salt('bf', 12)), ?::role_utilisateur, ?, ?)
                    RETURNING id, nom, email, role::text AS role, actif, statut_inscription
                    """;
            try (Connection connexion = ouvrir();
                 PreparedStatement requete = connexion.prepareStatement(sql)) {
                connexion.setAutoCommit(false);
                requete.setString(1, nom.strip());
                requete.setString(2, normaliserEmail(email));
                requete.setString(3, new String(motDePasse));
                requete.setString(4, role.name());
                requete.setBoolean(5, actif);
                requete.setString(6, statut);
                try (ResultSet resultat = requete.executeQuery()) {
                    if (!resultat.next()) {
                        throw new SQLException("PostgreSQL n'a retourné aucun compte après création.");
                    }
                    Compte compte = lireCompte(resultat);
                    if (actionAudit != null) {
                        journaliser(connexion, acteurId, actionAudit, compte.email(),
                                compte.role().name(), compte.actif());
                    }
                    connexion.commit();
                    return compte;
                } catch (SQLException | RuntimeException ex) {
                    connexion.rollback();
                    throw ex;
                }
            }
        } finally {
            java.util.Arrays.fill(motDePasse, '\0');
        }
    }

    public Compte definirActif(String email, boolean actif, long acteurId) throws SQLException {
        String normalise = normaliserEmail(email);
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try {
                // Sérialise les désactivations concurrentes pour ne jamais perdre le dernier admin.
                try (PreparedStatement verrou = connexion.prepareStatement(
                        "SELECT pg_advisory_xact_lock(20261029, 1)")) {
                    verrou.execute();
                }

                Compte existant;
                try (PreparedStatement requete = connexion.prepareStatement("""
                        SELECT id, nom, email, role::text AS role, actif, statut_inscription
                        FROM utilisateurs
                        WHERE lower(email) = ?
                        FOR UPDATE
                        """)) {
                    requete.setString(1, normalise);
                    try (ResultSet resultat = requete.executeQuery()) {
                        if (!resultat.next()) {
                            throw new IllegalArgumentException("Ce compte n'existe pas.");
                        }
                        existant = lireCompte(resultat);
                    }
                }

                if (actif) {
                    if (!"ACCEPTE".equals(existant.statutInscription())) {
                        throw new IllegalStateException("Acceptez d'abord la demande d'inscription.");
                    }
                    try (PreparedStatement q = connexion.prepareStatement("""
                            SELECT 1 FROM invitations_inscription
                            WHERE lower(email_cible) = ? AND role_cible = ? AND utilise_le IS NULL
                            """)) {
                        q.setString(1, existant.email());
                        q.setString(2, existant.role().name());
                        try (ResultSet r = q.executeQuery()) {
                            if (r.next()) throw new IllegalStateException("Le compte doit être activé par son PIN.");
                        }
                    }
                }

                if (!actif && existant.actif()
                        && existant.role() == Session.Role.ADMINISTRATEUR
                        && nombreAdministrateursActifs(connexion) <= 1) {
                    throw new IllegalStateException(
                            "Le dernier compte administrateur actif ne peut pas être désactivé.");
                }

                try (PreparedStatement maj = connexion.prepareStatement("""
                        UPDATE utilisateurs
                        SET actif = ?
                        WHERE id = ?
                        RETURNING id, nom, email, role::text AS role, actif, statut_inscription
                        """)) {
                    maj.setBoolean(1, actif);
                    maj.setLong(2, existant.id());
                    try (ResultSet resultat = maj.executeQuery()) {
                        if (!resultat.next()) {
                            throw new SQLException("Le compte n'a pas pu être mis à jour.");
                        }
                        Compte actualise = lireCompte(resultat);
                        journaliser(connexion, acteurId,
                                actif ? "ACTIVATION_COMPTE" : "DESACTIVATION_COMPTE",
                                actualise.email(), actualise.role().name(), actualise.actif());
                        connexion.commit();
                        return actualise;
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                throw ex;
            }
        }
    }

    public DecisionInscription deciderInscription(String email, boolean acceptee, long acteurId) throws SQLException {
        String normalise = normaliserEmail(email);
        try (Connection connexion = ouvrir()) {
            connexion.setAutoCommit(false);
            try {
                String statut = acceptee ? "ACCEPTE" : "REFUSE";
                try (PreparedStatement requete = connexion.prepareStatement("""
                        UPDATE utilisateurs
                        SET statut_inscription = ?, actif = ?
                        WHERE lower(email) = ? AND statut_inscription = 'EN_ATTENTE'
                        RETURNING id, nom, email, role::text AS role, actif, statut_inscription
                        """)) {
                    requete.setString(1, statut);
                    requete.setBoolean(2, false);
                    requete.setString(3, normalise);
                    try (ResultSet resultat = requete.executeQuery()) {
                        if (!resultat.next()) {
                            throw new IllegalArgumentException(
                                    "Cette inscription est introuvable ou a déjà été traitée.");
                        }
                        Compte compte = lireCompte(resultat);
                        journaliser(connexion, acteurId,
                                acceptee ? "ACCEPTATION_INSCRIPTION" : "REFUS_INSCRIPTION",
                                compte.email(), compte.role().name(), compte.actif());
                        Invitation activation = acceptee ? creerPinActivation(connexion, compte, acteurId) : null;
                        connexion.commit();
                        return new DecisionInscription(compte, activation);
                    }
                }
            } catch (SQLException | RuntimeException ex) {
                connexion.rollback();
                throw ex;
            }
        }
    }

    public record DecisionInscription(Compte compte, Invitation activation) {}

    public static String normaliserEmail(String email) {
        if (email == null) {
            throw new IllegalArgumentException("L'adresse e-mail est obligatoire.");
        }
        String normalise = email.strip().toLowerCase(Locale.ROOT);
        if (!normalise.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            throw new IllegalArgumentException("L'adresse e-mail est invalide.");
        }
        return normalise;
    }

    static void validerCompte(String nom, String email, char[] motDePasse, Session.Role role) {
        if (nom == null || nom.isBlank()) {
            throw new IllegalArgumentException("Le nom est obligatoire.");
        }
        normaliserEmail(email);
        if (Character.codePointCount(motDePasse, 0, motDePasse.length) < 12) {
            throw new IllegalArgumentException("Le mot de passe doit contenir au moins 12 caractères.");
        }
        if (StandardCharsets.UTF_8.encode(CharBuffer.wrap(motDePasse)).remaining() > 72) {
            throw new IllegalArgumentException("Le mot de passe ne peut pas dépasser 72 octets en UTF-8.");
        }
        if (role == null) {
            throw new IllegalArgumentException("Le rôle est obligatoire.");
        }
    }

    private Connection ouvrir() throws SQLException {
        return DriverManager.getConnection(configuration.url(), configuration.utilisateur(),
                configuration.motDePasse());
    }

    private static long nombreAdministrateursActifs(Connection connexion) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                SELECT count(*)
                FROM utilisateurs
                WHERE role = 'ADMINISTRATEUR' AND actif
                """);
             ResultSet resultat = requete.executeQuery()) {
            if (!resultat.next()) {
                throw new SQLException("Impossible de compter les administrateurs actifs.");
            }
            return resultat.getLong(1);
        }
    }

    private static void journaliser(Connection connexion, Long acteurId, String action,
            String emailCible, String role, boolean actif) throws SQLException {
        try (PreparedStatement requete = connexion.prepareStatement("""
                INSERT INTO journal_audit (acteur_id, action, cible, details)
                VALUES (?, ?, ?, jsonb_build_object('role', ?, 'actif', ?))
                """)) {
            if (acteurId == null) {
                requete.setNull(1, java.sql.Types.BIGINT);
            } else {
                requete.setLong(1, acteurId);
            }
            requete.setString(2, action);
            requete.setString(3, emailCible);
            requete.setString(4, role);
            requete.setBoolean(5, actif);
            requete.executeUpdate();
        }
    }

    private static Compte lireCompte(ResultSet resultat) throws SQLException {
        return new Compte(resultat.getLong("id"), resultat.getString("nom"),
                resultat.getString("email"), Session.Role.valueOf(resultat.getString("role")),
                resultat.getBoolean("actif"), resultat.getString("statut_inscription"));
    }

    public record Compte(long id, String nom, String email, Session.Role role, boolean actif,
            String statutInscription) {
    }

    public record Invitation(String email, Session.Role role, String pin, java.time.Instant expireLe) {
    }
}
