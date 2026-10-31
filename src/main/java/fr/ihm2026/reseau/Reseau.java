package fr.ihm2026.reseau;

import fr.ihm2026.core.Session;
import fr.ihm2026.api.service.ClientAuthentificationHttps;
import fr.ihm2026.model.ResultatVM;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * État de la liaison d'un poste avec le serveur de la salle, et fonctions appelées par l'interface.
 *
 * <p>Deux principes :</p>
 * <ul>
 *   <li>l'interface ne donne accès aux écrans métier qu'après connexion au serveur ; les fonctions
 *       de publication de résultats restent toutefois tolérantes à une interruption réseau ;</li>
 *   <li><b>Une publication ne bloque jamais l'application.</b> Publier un résultat est une
 *       tentative : si le serveur a disparu entre deux clics, l'apprenant voit le message d'erreur
 *       mais son résultat reste affiché — le réseau ne doit pas confisquer un travail fait.</li>
 * </ul>
 */
public final class Reseau {

    /** Adresse par défaut proposée : celle du poste serveur, sinon la machine locale. */
    private static final String ADRESSE_DEFAUT = IdentitePoste.serveurDefaut()
            + ":" + IdentitePoste.port();
    private static final Logger LOGGER = Logger.getLogger(Reseau.class.getName());

    private static ClientReseau client;
    private static ClientAuthentificationHttps authentification;
    private static String dernierMessage = "";

    private Reseau() {
    }

    public static boolean connecte() {
        return client != null;
    }

    public static boolean authentifie() {
        return client != null && authentification != null && authentification.actif();
    }

    public static boolean sessionCompteInstallee() {
        return authentification != null;
    }

    /** Session de compte courante, réservée aux appels API d'administration autorisés. */
    public static ClientAuthentificationHttps sessionAuthentification() {
        if (!authentifie()) {
            throw new ClientAuthentificationHttps.ErreurAuthentification(
                    "La session est expirée. Reconnectez-vous.");
        }
        return authentification;
    }

    /** Attache la session HTTPS validée au poste réseau courant. */
    public static void installerAuthentification(ClientAuthentificationHttps session) {
        if (client == null) {
            throw new IllegalStateException("Le poste doit être connecté au serveur avant l'authentification.");
        }
        if (authentification != null) {
            authentification.close();
        }
        authentification = java.util.Objects.requireNonNull(session, "session");
        Session.setUtilisateur(session.compte().nom());
    }

    /** Compatibilité avec les scripts historiques ; une authentification compte exige une saisie. */
    public static boolean connexionAuto() {
        return !"false".equalsIgnoreCase(
                System.getProperty("ihm.connexion.auto", "true").strip());
    }

    public static ClientReseau client() {
        return client;
    }

    /**
     * Adresse à pré-remplir dans l'écran de connexion : la liaison courante, sinon la dernière
     * adresse qui a fonctionné sur ce poste, sinon celle donnée au lancement.
     */
    public static String adresseProposee() {
        if (client != null) {
            return client.adresse();
        }
        // 1) l'adresse écrite dans le fichier de lancement du poste (--serveur=...) fait foi ;
        // 2) sinon la dernière adresse qui a fonctionné ; 3) sinon la valeur par défaut.
        String explicite = System.getProperty("ihm.serveur", "").strip();
        if (!explicite.isEmpty()) {
            return explicite.contains(":") ? explicite : explicite + ":" + IdentitePoste.port();
        }
        String memorisee = MemoireAdresse.lire();
        return memorisee == null ? ADRESSE_DEFAUT : memorisee;
    }

    /** Dernier message du serveur (accueil, refus, erreur) : l'écran l'affiche tel quel. */
    public static String dernierMessage() {
        return dernierMessage;
    }

    /**
     * Décode « 192.168.1.10 », « 192.168.1.10:7777 » ou « http://192.168.1.10:7777/ » en
     * {@code [hôte, port]}. Le port par défaut est celui du serveur (7777).
     */
    public static String[] decoderAdresse(String saisie) {
        String texte = saisie == null ? "" : saisie.strip();
        if (texte.startsWith("http://")) {
            texte = texte.substring("http://".length());
        }
        if (texte.startsWith("https://")) {
            texte = texte.substring("https://".length());
        }
        while (texte.endsWith("/")) {
            texte = texte.substring(0, texte.length() - 1);
        }
        if (texte.isEmpty()) {
            throw new ClientReseau.ErreurReseau("Saisir l'adresse IP du poste serveur"
                    + " (par exemple 192.168.1.10).");
        }
        String hote = texte;
        int port = IdentitePoste.port();
        int deuxPoints = texte.lastIndexOf(':');
        if (deuxPoints > 0) {
            hote = texte.substring(0, deuxPoints);
            try {
                port = Integer.parseInt(texte.substring(deuxPoints + 1).strip());
            } catch (NumberFormatException e) {
                throw new ClientReseau.ErreurReseau("Le port « "
                        + texte.substring(deuxPoints + 1) + " » n'est pas un nombre.");
            }
        }
        if (hote.isBlank() || hote.contains(" ")) {
            throw new ClientReseau.ErreurReseau("Adresse invalide : « " + saisie + " ».");
        }
        return new String[]{hote, String.valueOf(port)};
    }

    /**
     * Se connecte au serveur, s'y annonce avec le rôle du poste, et retient la liaison.
     * Lève {@link ClientReseau.ErreurReseau} avec un message affichable en cas d'échec.
     */
    public static EtatReseau.Sante connecter(String saisie, Session.Role role) {
        String[] morceaux = decoderAdresse(saisie);
        ClientReseau candidat = new ClientReseau(morceaux[0], Integer.parseInt(morceaux[1]));
        EtatReseau.Sante sante = candidat.sante();          // échoue si le serveur est absent
        EtatReseau.Reponse accueil = candidat.connecter(IdentitePoste.nom(), role.name());
        if (accueil == null || !accueil.ok()) {
            throw new ClientReseau.ErreurReseau(accueil == null
                    ? "Le serveur a refusé la connexion sans explication."
                    : accueil.message());
        }
        client = candidat;
        dernierMessage = accueil.message();
        // L'adresse qui vient de fonctionner est reproposée au prochain lancement du poste.
        MemoireAdresse.ecrire(candidat.adresse());
        return sante;
    }

    /** Coupe la liaison et prévient le serveur (sans échouer si le serveur est déjà parti). */
    public static void deconnecter() {
        ClientReseau connexion = client;
        ClientAuthentificationHttps session = authentification;
        client = null;
        authentification = null;
        dernierMessage = "";
        Session.terminerEvaluation();
        Session.setUtilisateur("Poste non connecté");
        Session.setDernierResultat(null);
        if (connexion != null) {
            try {
                connexion.deconnecter(IdentitePoste.nom());
            } catch (RuntimeException e) {
                LOGGER.log(Level.WARNING,
                        "Déconnexion locale terminée, mais le serveur n'a pas confirmé le retrait du poste.",
                        e);
            }
        }
        if (session != null) {
            session.close();
        }
    }

    /** Postes connectés, ou liste vide hors ligne (l'interface n'a pas de cas particulier). */
    public static List<EtatReseau.Poste> postes() {
        if (client == null) {
            return List.of();
        }
        try {
            return client.postes();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /** Résultats publiés sur le serveur, ou liste vide hors ligne. */
    public static List<EtatReseau.Resultat> resultats() {
        if (client == null) {
            return List.of();
        }
        try {
            return client.resultats();
        } catch (RuntimeException e) {
            return List.of();
        }
    }

    /**
     * Publie le dernier résultat d'un test. Renvoie {@code null} hors ligne : l'appelant n'a rien
     * à faire du réseau quand il n'y a pas de réseau.
     */
    public static String publier(ResultatVM resultat, Session.Langage langage, int niveau) {
        if (client == null || resultat == null) {
            return null;
        }
        try {
            EtatReseau.Reponse reponse = client.publierResultat(new EtatReseau.Resultat(
                    Session.utilisateur(), langage.label(), niveau, resultat.getNote(),
                    resultat.getSeuil(), resultat.isReussi(), ""));
            return reponse == null ? null : reponse.message();
        } catch (RuntimeException e) {
            return "Résultat non transmis : " + e.getMessage();
        }
    }
}
