package fr.ihm2026.api.service;

import fr.ihm2026.api.mock.MockBackend;
import fr.ihm2026.core.Config;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.UtilisateurVM;
import fr.ihm2026.reseau.Reseau;
import java.util.Arrays;
import java.util.List;

/** Comptes, rôles, journal d'audit et supervision de l'agent IA (AD1). */
public final class AdminApi {

    private AdminApi() {
    }

    public static List<UtilisateurVM> comptes() {
        if (Reseau.sessionCompteInstallee()) {
            return Reseau.sessionAuthentification().comptesAdministrateur().stream()
                    .map(AdminApi::versVue)
                    .toList();
        }
        if (Config.USE_MOCK) {
            return MockBackend.comptes();
        }
        throw new IllegalStateException("Connectez-vous avec un compte administrateur pour gérer les comptes.");
    }

    public static UtilisateurVM creerCompte(String nom, String email, Session.Role role,
            boolean actif) {
        if (Reseau.sessionCompteInstallee()) {
            throw new IllegalArgumentException("Un mot de passe temporaire est obligatoire.");
        }
        if (Config.USE_MOCK) {
            return MockBackend.creerCompte(nom, email, role, actif);
        }
        throw new IllegalStateException("Connectez-vous avec un compte administrateur pour gérer les comptes.");
    }

    public static UtilisateurVM creerCompte(String nom, String email, char[] motDePasse,
            Session.Role role, boolean actif) {
        if (motDePasse == null) {
            throw new IllegalArgumentException("Saisir un mot de passe temporaire.");
        }
        try {
            if (Reseau.sessionCompteInstallee()) {
                return versVue(Reseau.sessionAuthentification()
                        .creerCompte(nom, email, motDePasse, role, actif));
            }
            if (Config.USE_MOCK) {
                return MockBackend.creerCompte(nom, email, role, actif);
            }
            throw new IllegalStateException(
                    "Connectez-vous avec un compte administrateur pour gérer les comptes.");
        } finally {
            Arrays.fill(motDePasse, '\0');
        }
    }

    public static UtilisateurVM definirCompteActif(String email, boolean actif) {
        if (Reseau.sessionCompteInstallee()) {
            return versVue(Reseau.sessionAuthentification().definirCompteActif(email, actif));
        }
        if (Config.USE_MOCK) {
            return MockBackend.definirCompteActif(email, actif);
        }
        throw new IllegalStateException("Connectez-vous avec un compte administrateur pour gérer les comptes.");
    }

    public static UtilisateurVM deciderInscription(String email, boolean acceptee) {
        if (Reseau.sessionCompteInstallee()) {
            return versVue(Reseau.sessionAuthentification()
                    .deciderInscription(email, acceptee));
        }
        if (Config.USE_MOCK) {
            throw new UnsupportedOperationException(
                    "La validation des inscriptions nécessite une session administrateur réelle.");
        }
        throw new IllegalStateException(
                "Connectez-vous avec un compte administrateur pour traiter les inscriptions.");
    }

    public static ClientAuthentificationHttps.InvitationInscription creerInvitation(
            String email, Session.Role role) {
        if (Reseau.sessionCompteInstallee()) {
            return Reseau.sessionAuthentification().creerInvitation(email, role);
        }
        if (Config.USE_MOCK) {
            throw new UnsupportedOperationException(
                    "Les PIN d'inscription nécessitent une session administrateur réelle.");
        }
        throw new IllegalStateException(
                "Connectez-vous avec un compte administrateur pour créer une invitation.");
    }

    public static List<UtilisateurVM> resultats(Session.Langage langage) {
        if (Config.USE_MOCK) {
            return MockBackend.resultatsDuNiveau();
        }
        throw new UnsupportedOperationException("GET /enseignant/resultats : v0.4");
    }

    private static UtilisateurVM versVue(ClientAuthentificationHttps.CompteAdmin compte) {
        return new UtilisateurVM(compte.nom(), compte.email(), compte.role().name(),
                compte.actif(), 0, 0, compte.statutInscription());
    }
}
