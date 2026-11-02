package fr.ihm2026.api.service;

import fr.ihm2026.api.mock.MockBackend;
import fr.ihm2026.core.Config;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.UtilisateurVM;
import fr.ihm2026.reseau.Reseau;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Comptes, rôles, journal d'audit et supervision de l'agent IA (AD1). */
public final class AdminApi {

    private static final DateTimeFormatter FORMAT_DATE =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());

    private AdminApi() {
    }

    public record FiltreComptes(String recherche, Session.Role role, String statut, Boolean actif,
            String domaine, int page, int taille) {

        public FiltreComptes {
            recherche = texteOuNull(recherche);
            statut = texteOuNull(statut);
            domaine = texteOuNull(domaine);
            page = Math.max(1, page);
            taille = Math.max(1, Math.min(200, taille));
        }

        public static FiltreComptes parDefaut() {
            return new FiltreComptes(null, null, null, null, null, 1, 50);
        }

        private static String texteOuNull(String valeur) {
            return valeur == null || valeur.isBlank() ? null : valeur.strip();
        }
    }

    public record PageComptes(List<UtilisateurVM> comptes, long total, int page, int taille,
            long enAttente, long acceptes, long refuses) {
    }

    public record ResultatLot(List<UtilisateurVM> traites, List<String> erreurs,
            int emailsNonEnvoyes) {
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

    public static PageComptes comptes(FiltreComptes filtre) {
        var filtreEffectif = filtre == null ? FiltreComptes.parDefaut() : filtre;
        if (Reseau.sessionCompteInstallee()) {
            var page = Reseau.sessionAuthentification().comptesAdministrateur(
                    new ClientAuthentificationHttps.FiltreComptesAdmin(
                            filtreEffectif.recherche(), filtreEffectif.role(), filtreEffectif.statut(),
                            filtreEffectif.actif(), filtreEffectif.domaine(), filtreEffectif.page(),
                            filtreEffectif.taille()));
            return new PageComptes(page.comptes().stream().map(AdminApi::versVue).toList(),
                    page.total(), page.page(), page.taille(), page.enAttente(), page.acceptes(),
                    page.refuses());
        }
        if (Config.USE_MOCK) {
            List<UtilisateurVM> tous = MockBackend.comptes();
            List<UtilisateurVM> base = tous.stream()
                    .filter(compte -> correspondFiltre(compte, filtreEffectif, false))
                    .toList();
            List<UtilisateurVM> filtres = base.stream()
                    .filter(compte -> correspondFiltre(compte, filtreEffectif, true))
                    .toList();
            int debut = Math.min(filtres.size(), (filtreEffectif.page() - 1) * filtreEffectif.taille());
            int fin = Math.min(filtres.size(), debut + filtreEffectif.taille());
            return new PageComptes(List.copyOf(filtres.subList(debut, fin)), filtres.size(),
                    filtreEffectif.page(), filtreEffectif.taille(),
                    base.stream().filter(UtilisateurVM::isEnAttente).count(),
                    base.stream().filter(c -> "ACCEPTE".equals(c.getStatutInscription())).count(),
                    base.stream().filter(c -> "REFUSE".equals(c.getStatutInscription())).count());
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

    public static ResultatLot deciderInscriptions(List<String> emails, boolean acceptee) {
        if (Reseau.sessionCompteInstallee()) {
            var resultat = Reseau.sessionAuthentification().deciderInscriptions(emails, acceptee);
            return new ResultatLot(resultat.traites().stream().map(AdminApi::versVue).toList(),
                    resultat.erreurs(), resultat.emailsNonEnvoyes());
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

    private static boolean correspondFiltre(UtilisateurVM compte, FiltreComptes filtre,
            boolean inclureStatut) {
        if (filtre.recherche() != null) {
            String recherche = filtre.recherche().toLowerCase(Locale.ROOT);
            if (!compte.getNom().toLowerCase(Locale.ROOT).contains(recherche)
                    && !compte.getEmail().toLowerCase(Locale.ROOT).contains(recherche)) {
                return false;
            }
        }
        if (filtre.role() != null
                && !compte.getRole().equalsIgnoreCase(filtre.role().name())
                && !compte.getRole().equalsIgnoreCase(filtre.role().label())) {
            return false;
        }
        if (inclureStatut && filtre.statut() != null
                && !compte.getStatutInscription().equalsIgnoreCase(filtre.statut())) {
            return false;
        }
        if (filtre.actif() != null && compte.isActif() != filtre.actif()) {
            return false;
        }
        if (filtre.domaine() != null) {
            String domaine = filtre.domaine().strip().toLowerCase(Locale.ROOT);
            int arobase = domaine.lastIndexOf('@');
            if (arobase >= 0) {
                domaine = domaine.substring(arobase + 1);
            }
            String email = compte.getEmail().toLowerCase(Locale.ROOT);
            int position = email.lastIndexOf('@');
            if (position < 0 || !email.substring(position + 1).equals(domaine)) {
                return false;
            }
        }
        return true;
    }

    private static UtilisateurVM versVue(ClientAuthentificationHttps.CompteAdmin compte) {
        return new UtilisateurVM(compte.nom(), compte.email(), compte.role().name(),
                compte.actif(), 0, 0, compte.statutInscription(),
                compte.creeLe() == null ? "" : FORMAT_DATE.format(compte.creeLe()),
                compte.recommandationBot(), compte.motifBot());
    }
}
