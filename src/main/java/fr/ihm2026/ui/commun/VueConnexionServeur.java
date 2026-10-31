package fr.ihm2026.ui.commun;

import fr.ihm2026.api.service.ClientAuthentificationHttps;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.reseau.ClientReseau;
import fr.ihm2026.reseau.EtatReseau;
import fr.ihm2026.reseau.IdentitePoste;
import fr.ihm2026.reseau.Reseau;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran d'entrée d'un poste du réseau local (v0.13.0).
 *
 * <p>Chaque poste humain ouvre sa <b>propre</b> fenêtre, avec son rôle déjà fixé par son fichier de
 * lancement. Le serveur est découvert automatiquement sur le réseau local. L'inscription exige
 * un PIN individuel remis par l'administrateur ; la connexion est confirmée par e-mail.</p>
 *
 * <p>L'authentification HTTPS et l'annonce du poste se font hors du fil d'affichage
 * ({@link TaskRunner}) afin que l'interface reste réactive en cas de problème réseau.</p>
 */
public class VueConnexionServeur extends StackPane {

    private final Router router;
    private final Session.Role role;
    private final TextField codeInvitation = new TextField();
    private final TextField nom = new TextField();
    private final TextField email = new TextField();
    private final TextField codeConnexion = new TextField();
    private final PasswordField motDePasse = new PasswordField();
    private final PasswordField confirmationMotDePasse = new PasswordField();
    private final Label etiquetteNom = new Label("Nom et prénom");
    private final Label etiquetteInvitation = new Label("PIN individuel d'inscription");
    private final Label etiquetteConfirmation = new Label("Confirmer le mot de passe");
    private final Label etiquetteCodeConnexion = new Label("Code reçu par e-mail");
    private final Label etat = new Label();
    private final Button connecter = new Button("Se connecter");
    private final Button inscription = new Button("Créer un compte");
    private boolean modeInscription;
    private boolean attenteCodeConnexion;
    private String identifiantChallenge;
    private String hoteServeur;

    public VueConnexionServeur(Router router, Session.Role role) {
        this.router = router;
        this.role = java.util.Objects.requireNonNull(role,
                "Un rôle de poste doit être configuré avant la connexion.");
        getStyleClass().add("login-view");

        Label marque = new Label("IHM 2026");
        marque.getStyleClass().add("login-marque");
        Label titre = new Label(IdentitePoste.nom() + " — " + this.role.label());
        titre.getStyleClass().add("login-titre");
        Label sousTitre = new Label("Se connecter avec un compte autorisé pour ce rôle.");
        sousTitre.getStyleClass().add("login-soustitre");

        Label decouverteServeur = new Label("Serveur détecté automatiquement sur le réseau local");
        decouverteServeur.getStyleClass().add("login-mode");
        etiquetteInvitation.getStyleClass().add("role-nom");
        codeInvitation.setPromptText("PIN individuel à 8 chiffres fourni par l'administrateur");
        codeInvitation.getStyleClass().add("champ-adresse");
        codeInvitation.setMaxWidth(Double.MAX_VALUE);

        Label etiquetteEmail = new Label("Adresse e-mail du compte");
        etiquetteEmail.getStyleClass().add("role-nom");
        email.setPromptText("nom@exemple.fr");
        email.getStyleClass().add("champ-adresse");
        email.setMaxWidth(Double.MAX_VALUE);

        nom.setPromptText("Nom et prénom");
        nom.getStyleClass().add("champ-adresse");
        nom.setMaxWidth(Double.MAX_VALUE);
        etiquetteNom.getStyleClass().add("role-nom");

        Label etiquetteMotDePasse = new Label("Mot de passe");
        etiquetteMotDePasse.getStyleClass().add("role-nom");
        motDePasse.setPromptText("Mot de passe du compte");
        motDePasse.getStyleClass().add("champ-adresse");
        motDePasse.setMaxWidth(Double.MAX_VALUE);
        confirmationMotDePasse.setPromptText("Confirmer le mot de passe");
        confirmationMotDePasse.getStyleClass().add("champ-adresse");
        confirmationMotDePasse.setMaxWidth(Double.MAX_VALUE);
        etiquetteConfirmation.getStyleClass().add("role-nom");
        codeConnexion.setPromptText("6 chiffres");
        codeConnexion.getStyleClass().add("champ-adresse");
        codeConnexion.setMaxWidth(Double.MAX_VALUE);
        etiquetteCodeConnexion.getStyleClass().add("role-nom");
        etiquetteCodeConnexion.setVisible(false);
        etiquetteCodeConnexion.setManaged(false);
        codeConnexion.setVisible(false);
        codeConnexion.setManaged(false);
        etiquetteInvitation.setVisible(false);
        etiquetteInvitation.setManaged(false);
        codeInvitation.setVisible(false);
        codeInvitation.setManaged(false);

        connecter.getStyleClass().add("btn-primary");
        connecter.setDefaultButton(true);
        connecter.setOnAction(e -> seConnecter());
        inscription.setOnAction(e -> {
            if (attenteCodeConnexion) {
                attenteCodeConnexion = false;
                identifiantChallenge = null;
                hoteServeur = null;
                codeConnexion.clear();
                modeInscription = false;
                actualiserModeInscription();
                definirChampsActifs(true);
                etat.setText("Vérification annulée. Vous pouvez recommencer la connexion.");
                return;
            }
            if (modeInscription) {
                motDePasse.clear();
                confirmationMotDePasse.clear();
            }
            modeInscription = !modeInscription;
            actualiserModeInscription();
        });

        HBox ligne = new HBox(10, connecter);
        ligne.setAlignment(Pos.CENTER);
        HBox actionsCompte = new HBox(10, inscription);
        actionsCompte.setAlignment(Pos.CENTER);

        etat.getStyleClass().add("login-mode");
        etat.setWrapText(true);
        etat.setText("version " + fr.ihm2026.core.Config.VERSION + " · poste " + IdentitePoste.nom()
                + " · rôle fixé : " + this.role.label());

        Label aide = new Label("À l'inscription, utilisez le PIN individuel remis par l'administrateur."
                + " Après acceptation du compte, un code de sécurité sera envoyé par e-mail à chaque connexion.");
        aide.getStyleClass().add("login-mode");
        aide.setWrapText(true);

        VBox carte = new VBox(10, marque, titre, sousTitre, decouverteServeur, ligne,
                etiquetteInvitation, codeInvitation, etiquetteNom, nom, etiquetteEmail, email,
                etiquetteMotDePasse, motDePasse,
                etiquetteConfirmation, confirmationMotDePasse, etiquetteCodeConnexion, codeConnexion,
                actionsCompte, etat, aide);
        carte.getStyleClass().addAll("card", "login-card", "carte-reseau");
        carte.setAlignment(Pos.CENTER);
        carte.setMaxWidth(560);
        carte.setMaxHeight(Region.USE_PREF_SIZE);
        actualiserModeInscription();

        getChildren().add(carte);
        setAlignment(Pos.CENTER);

        // Entrée depuis la carte vaut « Se connecter ».
        carte.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                seConnecter();
            }
        });
        // Filet d'affichage (v0.14.3) : si la fenêtre dépasse l'écran, une carte centrée sort à
        // droite. On l'ancre alors en haut à gauche, où elle reste visible et utilisable.
        Runnable placer = () -> {
            javafx.scene.Scene scene = getScene();
            if (scene == null) {
                return;
            }
            javafx.geometry.Rectangle2D ecran = javafx.stage.Screen.getPrimary().getVisualBounds();
            boolean deborde = scene.getWidth() > ecran.getWidth() + 2
                    || scene.getHeight() > ecran.getHeight() + 2;
            setAlignment(deborde ? Pos.TOP_LEFT : Pos.CENTER);
            setPadding(deborde ? new javafx.geometry.Insets(16) : javafx.geometry.Insets.EMPTY);
        };
        sceneProperty().addListener((o, ancienne, nouvelle) -> {
            if (nouvelle != null) {
                nouvelle.widthProperty().addListener((x, a, b) -> placer.run());
                nouvelle.heightProperty().addListener((x, a, b) -> placer.run());
                placer.run();
            }
        });

        javafx.application.Platform.runLater(() -> {
            javafx.stage.Window fenetre = email.getScene() == null
                    ? null : email.getScene().getWindow();
            if (fenetre != null) {
                fenetre.requestFocus();     // la fenêtre doit être active pour recevoir le clavier
            }
            email.requestFocus();
            placer.run();
        });
    }

    /** Écran d'accueil du rôle : c'est le seul endroit où le rôle choisit une destination. */
    public static String ecranDuRole(Session.Role role) {
        return switch (role) {
            case ENSEIGNANT -> Router.CONDITIONS;
            case ADMINISTRATEUR -> Router.COMPTES;
            default -> Router.PARCOURS;
        };
    }

    private String ecranDuRole() {
        return ecranDuRole(role);
    }

    private void seConnecter() {
        if (modeInscription) {
            sInscrire();
            return;
        }
        if (attenteCodeConnexion) {
            confirmerConnexion();
            return;
        }
        String identifiant = email.getText();
        char[] secret = motDePasse.getText().toCharArray();
        if (identifiant == null || identifiant.isBlank() || secret.length == 0) {
            java.util.Arrays.fill(secret, '\0');
            etat.setText("Saisissez votre adresse e-mail et votre mot de passe.");
            return;
        }
        motDePasse.clear();
        connecter.setDisable(true);
        inscription.setDisable(true);
        email.setDisable(true);
        motDePasse.setDisable(true);
        etat.setText("Recherche automatique du serveur et envoi du code de sécurité…");
        TaskRunner.run(() -> {
            try {
                String hote = ClientReseau.decouvrirServeur(IdentitePoste.port());
                var challenge = ClientAuthentificationHttps.demanderCodeConnexion(
                        hote, ClientAuthentificationHttps.portConfigure(), identifiant, secret);
                return new DemandeCode(hote, challenge);
            } finally {
                java.util.Arrays.fill(secret, '\0');
            }
        }, demande -> {
            hoteServeur = demande.hote();
            identifiantChallenge = demande.challenge().identifiant();
            attenteCodeConnexion = true;
            codeConnexion.setVisible(true);
            codeConnexion.setManaged(true);
            etiquetteCodeConnexion.setVisible(true);
            etiquetteCodeConnexion.setManaged(true);
            connecter.setText("Valider le code");
            inscription.setText("Annuler");
            definirChampsActifs(true);
            email.setDisable(true);
            motDePasse.setDisable(true);
            codeConnexion.setDisable(false);
            etat.setText(demande.challenge().message() + " Saisissez-le pour terminer la connexion.");
            codeConnexion.requestFocus();
        }, erreur -> {
            definirChampsActifs(true);
            etat.setText(erreur instanceof ClientReseau.ErreurReseau
                    || erreur instanceof ClientAuthentificationHttps.ErreurAuthentification
                    ? erreur.getMessage()
                    : "Connexion impossible : " + erreur.getMessage());
            email.requestFocus();
        });
    }

    private void confirmerConnexion() {
        String code = codeConnexion.getText();
        if (code == null || !code.strip().matches("[0-9]{6}")) {
            etat.setText("Saisissez le code de sécurité à six chiffres reçu par e-mail.");
            return;
        }
        connecter.setDisable(true);
        codeConnexion.setDisable(true);
        etat.setText("Vérification du code de sécurité…");
        TaskRunner.run(() -> {
            ClientAuthentificationHttps session = ClientAuthentificationHttps.confirmerCodeConnexion(
                    hoteServeur, ClientAuthentificationHttps.portConfigure(),
                    identifiantChallenge, code.strip());
            if (session.compte().role() != role) {
                session.close();
                throw new ClientAuthentificationHttps.ErreurAuthentification(
                        "Cette demande de compte est associée au rôle « "
                                + session.compte().role().label() + " », pas au rôle « "
                                + role.label() + " » de ce poste.");
            }
            try {
                Reseau.connecter(hoteServeur + ":" + IdentitePoste.port(), role);
                return session;
            } catch (RuntimeException ex) {
                session.close();
                throw ex;
            }
        }, session -> {
            Reseau.installerAuthentification(session);
            Session.setRole(session.compte().role());
            router.go(ecranDuRole());
        }, erreur -> {
            connecter.setDisable(false);
            codeConnexion.setDisable(false);
            codeConnexion.clear();
            etat.setText(erreur instanceof ClientReseau.ErreurReseau
                    || erreur instanceof ClientAuthentificationHttps.ErreurAuthentification
                    ? erreur.getMessage()
                    : "Connexion impossible : " + erreur.getMessage());
            codeConnexion.requestFocus();
        });
    }

    private void sInscrire() {
        String pin = codeInvitation.getText();
        String nomSaisi = nom.getText();
        String emailSaisi = email.getText();
        char[] secret = motDePasse.getText().toCharArray();
        char[] confirmation = confirmationMotDePasse.getText().toCharArray();
        motDePasse.clear();
        confirmationMotDePasse.clear();
        if (nomSaisi == null || nomSaisi.isBlank() || emailSaisi == null || emailSaisi.isBlank()
                || pin == null || !pin.strip().matches("[0-9]{8}")) {
            java.util.Arrays.fill(secret, '\0');
            java.util.Arrays.fill(confirmation, '\0');
            etat.setText("Le nom, l'e-mail et le PIN individuel à huit chiffres sont obligatoires.");
            return;
        }
        if (!java.util.Arrays.equals(secret, confirmation)) {
            java.util.Arrays.fill(secret, '\0');
            java.util.Arrays.fill(confirmation, '\0');
            etat.setText("Les mots de passe ne correspondent pas.");
            return;
        }
        java.util.Arrays.fill(confirmation, '\0');
        int octetsUtf8 = java.nio.charset.StandardCharsets.UTF_8
                .encode(java.nio.CharBuffer.wrap(secret)).remaining();
        if (Character.codePointCount(secret, 0, secret.length) < 12 || octetsUtf8 > 72) {
            java.util.Arrays.fill(secret, '\0');
            etat.setText("Le mot de passe doit contenir au moins 12 caractères et au plus 72 octets UTF-8.");
            return;
        }
        connecter.setDisable(true);
        inscription.setDisable(true);
        codeInvitation.setDisable(true);
        nom.setDisable(true);
        email.setDisable(true);
        motDePasse.setDisable(true);
        confirmationMotDePasse.setDisable(true);
        etat.setText("Recherche du serveur et vérification de votre PIN individuel…");
        TaskRunner.run(() -> {
            try {
                String hote = ClientReseau.decouvrirServeur(IdentitePoste.port());
                return ClientAuthentificationHttps.inscrireCompte(
                        hote, ClientAuthentificationHttps.portConfigure(), pin.strip(),
                        nomSaisi, emailSaisi, secret, role);
            } finally {
                java.util.Arrays.fill(secret, '\0');
            }
        }, compte -> {
            definirChampsActifs(true);
            modeInscription = false;
            email.setText(compte.email());
            codeInvitation.clear();
            actualiserModeInscription();
            etat.setText("Demande de compte " + role.label()
                    + " envoyée. L'administrateur doit l'accepter avant votre première connexion.");
        }, erreur -> {
            definirChampsActifs(true);
            etat.setText(erreur instanceof ClientReseau.ErreurReseau
                    || erreur instanceof ClientAuthentificationHttps.ErreurAuthentification
                    ? erreur.getMessage()
                    : "Inscription impossible : " + erreur.getMessage());
        });
    }

    private void actualiserModeInscription() {
        etiquetteInvitation.setVisible(modeInscription);
        etiquetteInvitation.setManaged(modeInscription);
        codeInvitation.setVisible(modeInscription);
        codeInvitation.setManaged(modeInscription);
        nom.setVisible(modeInscription);
        nom.setManaged(modeInscription);
        etiquetteNom.setVisible(modeInscription);
        etiquetteNom.setManaged(modeInscription);
        confirmationMotDePasse.setVisible(modeInscription);
        confirmationMotDePasse.setManaged(modeInscription);
        etiquetteConfirmation.setVisible(modeInscription);
        etiquetteConfirmation.setManaged(modeInscription);
        etiquetteCodeConnexion.setVisible(attenteCodeConnexion && !modeInscription);
        etiquetteCodeConnexion.setManaged(attenteCodeConnexion && !modeInscription);
        codeConnexion.setVisible(attenteCodeConnexion && !modeInscription);
        codeConnexion.setManaged(attenteCodeConnexion && !modeInscription);
        inscription.setText(modeInscription ? "J'ai déjà un compte" : "Créer un compte");
        if (attenteCodeConnexion) {
            inscription.setText("Annuler");
        }
        connecter.setText(modeInscription ? "Envoyer ma demande"
                : attenteCodeConnexion ? "Valider le code" : "Se connecter");
        connecter.setDefaultButton(true);
        etat.setText(modeInscription
                ? "Votre demande sera en attente jusqu'à la décision d'un administrateur."
                : "version " + fr.ihm2026.core.Config.VERSION + " · poste " + IdentitePoste.nom()
                        + " · rôle fixé : " + role.label());
    }

    private void definirChampsActifs(boolean actifs) {
        connecter.setDisable(!actifs);
        inscription.setDisable(!actifs);
        codeInvitation.setDisable(!actifs);
        nom.setDisable(!actifs);
        email.setDisable(!actifs);
        motDePasse.setDisable(!actifs);
        confirmationMotDePasse.setDisable(!actifs);
        codeConnexion.setDisable(!actifs);
        inscription.setDisable(!actifs);
    }

    private record DemandeCode(String hote,
            ClientAuthentificationHttps.ChallengeConnexion challenge) {
    }

    /** Résumé affiché après coup par d'autres écrans : « PC3 → 192.168.1.10:7777 (3 postes) ». */
    public static String resume(EtatReseau.Sante sante) {
        if (sante == null) {
            return "hors ligne";
        }
        return Reseau.adresseProposee() + " · " + sante.postes() + " poste(s) · version "
                + sante.version();
    }
}
