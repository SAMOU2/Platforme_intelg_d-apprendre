package fr.ihm2026.api.persistence;

import static org.junit.jupiter.api.Assertions.*;
import fr.ihm2026.core.Session;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Nécessite une base de test PostgreSQL migrée jusqu'à 15 ; ne jamais utiliser en production. */
@EnabledIfEnvironmentVariable(named = "IHM_DB_URL", matches = "jdbc:postgresql://.+")
class GestionComptesIntegrationTest {
    @Test
    void reinitialisationUniqueLimiteeEtRevocation() throws Exception {
        var cfg = PostgresConfiguration.depuisEnvironnement();
        var comptes = new PostgresCompteRepository(cfg);
        var auth = new PostgresAuthentificationService(cfg);
        String email = "reset-" + UUID.randomUUID() + "@example.org";
        String source = UUID.randomUUID().toString();
        try {
            comptes.creer("Test reset", email, "ancien-password-2026".toCharArray(), Session.Role.ADMINISTRATEUR, true);
            var login = auth.demanderCodeConnexion(email, "ancien-password-2026".toCharArray(), source);
            var session = auth.confirmerCodeConnexion(login.identifiant(), login.code(), true, "Test");
            var reste = auth.demanderCodeConnexion(email, "ancien-password-2026".toCharArray(), source);
            var challenge = auth.demanderReinitialisation(email, source).orElseThrow();
            String mauvais = challenge.code().equals("00000000") ? "00000001" : "00000000";
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.reinitialiserMotDePasse(email, mauvais, "nouveau-password-2026".toCharArray()));
            char[] nouveau = "nouveau-password-2026".toCharArray();
            auth.reinitialiserMotDePasse(email, challenge.code(), nouveau);
            assertEquals('\0', nouveau[0]);
            assertTrue(auth.valider(session.jeton()).isEmpty());
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.confirmerCodeConnexion(reste.identifiant(), reste.code()));
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.reinitialiserMotDePasse(email, challenge.code(), "autre-password-2026".toCharArray()));
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.demanderCodeConnexion(email, "ancien-password-2026".toCharArray(), source));
            assertTrue(auth.commencerConnexion(email, "nouveau-password-2026".toCharArray(), source,
                    session.appareilConfiance().jeton()).codeRequis());
            var nouvelEssai = auth.demanderReinitialisation(email, source).orElseThrow();
            String faux = nouvelEssai.code().equals("00000000") ? "00000001" : "00000000";
            for (int i = 0; i < 5; i++) assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.reinitialiserMotDePasse(email, faux, "nouveau-password-2026".toCharArray()));
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.reinitialiserMotDePasse(email, nouvelEssai.code(), "nouveau-password-2026".toCharArray()));
            assertTrue(auth.demanderReinitialisation("inconnu-" + UUID.randomUUID() + "@example.org", source).isEmpty());
        } finally {
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse());
                 var q = c.prepareStatement("DELETE FROM utilisateurs WHERE lower(email) = lower(?)")) {
                q.setString(1, email); q.executeUpdate();
            }
        }
    }

    @Test
    void expirationEtQuotaDesDemandes() throws Exception {
        var cfg = PostgresConfiguration.depuisEnvironnement();
        var depot = new PostgresCompteRepository(cfg);
        var auth = new PostgresAuthentificationService(cfg);
        String email = "expire-" + UUID.randomUUID() + "@example.org";
        String source = UUID.randomUUID().toString();
        try {
            long id = depot.creer("Compte", email, "password-test-2026".toCharArray(), Session.Role.APPRENANT, true).id();
            var ancien = auth.demanderReinitialisation(email, source).orElseThrow();
            var nouveau = auth.demanderReinitialisation(email, source).orElseThrow();
            if (!ancien.code().equals(nouveau.code())) {
                assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                        () -> auth.reinitialiserMotDePasse(email, ancien.code(), "nouveau-password-2026".toCharArray()));
            }
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse());
                 var q = c.prepareStatement("UPDATE reinitialisations_mot_de_passe SET expire_le = now() - interval '1 second' WHERE utilisateur_id = ?")) {
                q.setLong(1, id); q.executeUpdate();
            }
            assertThrows(PostgresAuthentificationService.AuthentificationRefuseeException.class,
                    () -> auth.reinitialiserMotDePasse(email, nouveau.code(), "nouveau-password-2026".toCharArray()));
            assertTrue(auth.demanderReinitialisation(email, source).isPresent());
            assertTrue(auth.demanderReinitialisation(email, source).isEmpty());
        } finally {
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse());
                 var q = c.prepareStatement("DELETE FROM utilisateurs WHERE email = ?")) {
                q.setString(1, email); q.executeUpdate();
            }
        }
    }

    @Test
    void suppressionEnseignantSupprimeParcoursEtCopiesAssociees() throws Exception {
        var cfg = PostgresConfiguration.depuisEnvironnement();
        var depot = new PostgresCompteRepository(cfg);
        String suffixe = UUID.randomUUID().toString();
        String adminEmail = "admin-eff-" + suffixe + "@example.org";
        String profEmail = "prof-eff-" + suffixe + "@example.org";
        String eleveEmail = "eleve-eff-" + suffixe + "@example.org";
        long admin = 0, langage = 0;
        try {
            admin = depot.creer("Admin test", adminEmail, "password-admin-2026".toCharArray(), Session.Role.ADMINISTRATEUR, true).id();
            long prof = depot.creer("Prof", profEmail, "password-prof-2026".toCharArray(), Session.Role.ENSEIGNANT, true).id();
            long eleve = depot.creer("Élève", eleveEmail, "password-eleve-2026".toCharArray(), Session.Role.APPRENANT, true).id();
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse());
                 var q = c.prepareStatement("INSERT INTO langages(code,libelle,version,image_execution) VALUES (?,?,?,?) RETURNING id")) {
                q.setString(1, "test-" + suffixe); q.setString(2, "Test"); q.setString(3, "1"); q.setString(4, "test");
                try (var r = q.executeQuery()) { assertTrue(r.next()); langage = r.getLong(1); }
                long parcours;
                try (var p = c.prepareStatement("INSERT INTO parcours(langage_id,libelle) VALUES (?,?) RETURNING id")) {
                    p.setLong(1, langage); p.setString(2, "Parcours");
                    try (var r = p.executeQuery()) { assertTrue(r.next()); parcours = r.getLong(1); }
                }
                try (var p = c.prepareStatement("INSERT INTO parcours_enseignants VALUES (?,?,?)")) {
                    p.setLong(1, prof); p.setLong(2, langage); p.setLong(3, parcours); p.executeUpdate();
                }
                long niveau;
                try (var p = c.prepareStatement("INSERT INTO niveaux(parcours_id,rang,titre,seuil,cree_par) VALUES (?,1,'Test',10,?) RETURNING id")) {
                    p.setLong(1, parcours); p.setLong(2, prof);
                    try (var r = p.executeQuery()) { assertTrue(r.next()); niveau = r.getLong(1); }
                }
                long test;
                try (var p = c.prepareStatement("INSERT INTO tests(niveau_id) VALUES (?) RETURNING id")) {
                    p.setLong(1, niveau); try (var r = p.executeQuery()) { assertTrue(r.next()); test = r.getLong(1); }
                }
                try (var p = c.prepareStatement("INSERT INTO tentatives(apprenant_id,test_id,numero,enseignant_id) VALUES (?,?,1,?)")) {
                    p.setLong(1, eleve); p.setLong(2, test); p.setLong(3, prof); p.executeUpdate();
                }
                try (var p = c.prepareStatement("INSERT INTO affectations_pedagogiques(apprenant_id,enseignant_id,affecte_par) VALUES (?,?,?)")) {
                    p.setLong(1, eleve); p.setLong(2, prof); p.setLong(3, admin); p.executeUpdate();
                }
            }
            assertEquals(1, depot.supprimerComptes(List.of(profEmail), admin));
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse());
                 var q = c.prepareStatement("SELECT (SELECT count(*) FROM parcours WHERE langage_id = ?), (SELECT count(*) FROM tentatives WHERE apprenant_id = ?), (SELECT count(*) FROM affectations_pedagogiques WHERE apprenant_id = ?), (SELECT count(*) FROM utilisateurs WHERE id = ?)")) {
                q.setLong(1, langage); q.setLong(2, eleve); q.setLong(3, eleve); q.setLong(4, eleve);
                try (var r = q.executeQuery()) { assertTrue(r.next()); assertEquals(0, r.getInt(1)); assertEquals(0, r.getInt(2)); assertEquals(0, r.getInt(3)); assertEquals(1, r.getInt(4)); }
            }
        } finally {
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse())) {
                try (var q = c.prepareStatement("DELETE FROM journal_audit WHERE acteur_id = ? OR cible IN (?, ?, ?)")) {
                    q.setLong(1, admin); q.setString(2, adminEmail); q.setString(3, profEmail); q.setString(4, eleveEmail); q.executeUpdate();
                }
                try (var q = c.prepareStatement("DELETE FROM parcours WHERE langage_id = ?")) { q.setLong(1, langage); q.executeUpdate(); }
                try (var q = c.prepareStatement("DELETE FROM langages WHERE id = ?")) { q.setLong(1, langage); q.executeUpdate(); }
                try (var q = c.prepareStatement("DELETE FROM affectations_pedagogiques WHERE apprenant_id IN (SELECT id FROM utilisateurs WHERE email = ?)")) { q.setString(1, eleveEmail); q.executeUpdate(); }
                try (var q = c.prepareStatement("DELETE FROM utilisateurs WHERE email IN (?, ?, ?)") ) {
                    q.setString(1, adminEmail); q.setString(2, profEmail); q.setString(3, eleveEmail); q.executeUpdate();
                }
            }
        }
    }

    @Test
    void suppressionLotAtomiqueEtProtectionAdministrateur() throws Exception {
        var cfg = PostgresConfiguration.depuisEnvironnement();
        var depot = new PostgresCompteRepository(cfg);
        String suffixe = UUID.randomUUID().toString();
        String adminEmail = "admin-supp-" + suffixe + "@example.org";
        String a = "a-supp-" + suffixe + "@example.org";
        String b = "b-supp-" + suffixe + "@example.org";
        long admin = 0;
        try {
            admin = depot.creer("Admin test", adminEmail, "password-admin-2026".toCharArray(), Session.Role.ADMINISTRATEUR, true).id();
            depot.creer("A", a, "password-eleve-2026".toCharArray(), Session.Role.APPRENANT, true);
            depot.creer("B", b, "password-prof-2026".toCharArray(), Session.Role.ENSEIGNANT, true);
            long acteur = admin;
            assertThrows(IllegalStateException.class, () -> depot.supprimerComptes(List.of(a, adminEmail), acteur));
            assertThrows(IllegalStateException.class, () -> depot.supprimerComptes(List.of(a, "inexistant@example.org"), acteur));
            assertEquals(1, depot.lister().stream().filter(c -> c.email().equals(a)).count());
            assertEquals(2, depot.supprimerComptes(List.of(a, b), acteur));
            assertEquals(0, depot.lister().stream().filter(c -> c.email().equals(a) || c.email().equals(b)).count());
        } finally {
            try (var c = DriverManager.getConnection(cfg.url(), cfg.utilisateur(), cfg.motDePasse());
                 var audit = c.prepareStatement("DELETE FROM journal_audit WHERE acteur_id = ? OR cible IN (?, ?, ?)");
                 var q = c.prepareStatement("DELETE FROM utilisateurs WHERE email IN (?, ?, ?)") ) {
                audit.setLong(1, admin); audit.setString(2, adminEmail); audit.setString(3, a); audit.setString(4, b); audit.executeUpdate();
                q.setString(1, adminEmail); q.setString(2, a); q.setString(3, b); q.executeUpdate();
            }
        }
    }
}
