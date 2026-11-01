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
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/**
 * Écran d'entrée d'un poste du réseau local (v0.13.0).
 *
 * <p>Chaque poste humain ouvre sa <b>propre</b> fenêtre, avec son rôle déjà fixé par son fichier de
 * lancement. Le serveur est découvert automatiquement sur le réseau local. L'inscription ne demande aucun PIN ; le compte accepté
 * est ensuite activé par un PIN envoyé par e-mail ; la connexion est confirmée par e-mail.</p>
 *
 * <p>L'authentification HTTPS et l'annonce du poste se font hors du fil d'affichage
 * ({@link TaskRunner}) afin que l'interface reste réactive en cas de problème réseau.</p>
 */
public class VueConnexionServeur extends StackPane {

    private final Router router;
    private final Session.Role role;
    private final TextField adresseServeur = new TextField();
    private final TextField codeInvitation = new TextField();
    private final TextField nom = new TextField();
    private final TextField email = new TextField();
    private final TextField codeConnexion = new TextField();
    private final PasswordField motDePasse = new PasswordField();
    private final PasswordField confirmationMotDePasse = new PasswordField();
    private final Label etiquetteNom = new Label("Nom et prénom");
    private final Label etiquetteInvitation = new Label("PIN d'activation après acceptation");
    private final Label etiquetteConfirmation = new Label("Confirmer le mot de passe");
    private final Label etiquetteCodeConnexion = new Label("Code reçu par e-mail");
    private final Label etat = new Label();
    private final Button connecter = new Button("Se connecter");
    private final Button inscription = new Button("Créer un compte");
    private boolean modeActivation;
    private final Button activation = new Button("Activer mon compte avec le PIN reçu");
    private final Label etiquetteMotDePasse = new Label("Mot de passe");
    private boolean modeInscription;
    private boolean attenteCodeConnexion;
    private String identifiantChallenge;
    private String hoteServeur;
    private long generationDecouverte;

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

        Label decouverteServeur = new Label("Choisissez votre serveur local");
        javafx.scene.control.ComboBox<fr.ihm2026.reseau.DecouverteServeurs.Serveur> serveurs =
                new javafx.scene.control.ComboBox<>();
        serveurs.setId("serveurs-locaux");
        serveurs.setPromptText("Sélectionner un serveur…");
        serveurs.setMaxWidth(Double.MAX_VALUE);
        Button actualiserServeurs = new Button("Actualiser");
        Label etatDecouverte = new Label();
        etatDecouverte.setWrapText(true);
        javafx.scene.control.TitledPane avance = new javafx.scene.control.TitledPane(
                "Connexion avancée — adresse manuelle", adresseServeur);
        avance.setExpanded(false);
        serveurs.disableProperty().bind(adresseServeur.disableProperty());
        avance.disableProperty().bind(adresseServeur.disableProperty());
        serveurs.setOnAction(e -> {
            var choix = serveurs.getValue();
            if (choix != null) adresseServeur.setText(choix.hote());
        });
        Runnable rechercher = () -> {
            long generation = ++generationDecouverte;
            actualiserServeurs.setDisable(true);
            etatDecouverte.setText("Recherche des serveurs sur le réseau local…");
            TaskRunner.run(() -> fr.ihm2026.reseau.DecouverteServeurs.chercher(IdentitePoste.port()),
                    liste -> {
                        if (generation != generationDecouverte) return;
                        actualiserServeurs.setDisable(false);
                        serveurs.getItems().setAll(liste);
                        etatDecouverte.setText(liste.isEmpty()
                                ? "Aucun serveur trouvé. Vérifiez PC1 ou utilisez la connexion avancée."
                                : "Choisissez un serveur. Son nom ne garantit pas son identité : HTTPS reste vérifié.");
                    }, erreur -> {
                        if (generation != generationDecouverte) return;
                        actualiserServeurs.setDisable(false);
                        etatDecouverte.setText("Recherche impossible. Utilisez la connexion avancée.");
                    });
        };
        actualiserServeurs.setOnAction(e -> rechercher.run());
        ChoixWifi choixWifi = new ChoixWifi(rechercher, () -> {
            generationDecouverte++;
            actualiserServeurs.setDisable(false);
            serveurs.getSelectionModel().clearSelection();
            serveurs.getItems().clear();
            adresseServeur.clear();
            etatDecouverte.setText("En attente de la connexion Wi-Fi par Windows…");
        });
        choixWifi.disableProperty().bind(adresseServeur.disableProperty());
        adresseServeur.setText(System.getProperty("ihm.serveur", ""));
        adresseServeur.setPromptText("Exemple : 192.168.1.10 (Docker uniquement sur PC1)");
        adresseServeur.setMaxWidth(Double.MAX_VALUE);
        adresseServeur.getStyleClass().add("champ-adresse");
        adresseServeur.setId("serveur-ip");
        decouverteServeur.setLabelFor(serveurs);
        adresseServeur.setAccessibleText("Adresse IP ou nom du PC serveur. Vide pour la découverte automatique.");
        decouverteServeur.getStyleClass().add("login-mode");
        etiquetteInvitation.getStyleClass().add("role-nom");
        codeInvitation.setPromptText("PIN à 8 chiffres reçu après acceptation");
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

        etiquetteEmail.setLabelFor(email);
        etiquetteMotDePasse.setLabelFor(motDePasse);
        etiquetteNom.setLabelFor(nom);
        etiquetteInvitation.setLabelFor(codeInvitation);
        etiquetteConfirmation.setLabelFor(confirmationMotDePasse);
        etiquetteCodeConnexion.setLabelFor(codeConnexion);
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

        connecter.setId("connexion-valider");
        inscription.setId("connexion-inscription");
        inscription.setVisible(role != Session.Role.ADMINISTRATEUR);
        inscription.setManaged(role != Session.Role.ADMINISTRATEUR);
        activation.setId("compte-activation");
        codeInvitation.setId("compte-pin-activation");
        email.setId("connexion-email");
        motDePasse.setId("connexion-mot-de-passe");
        codeConnexion.setId("connexion-code");
        etat.setId("connexion-etat");
        connecter.getStyleClass().add("btn-primary");
        connecter.setDefaultButton(true);
        connecter.setOnAction(e -> seConnecter());
        activation.setOnAction(e -> {
            modeActivation = !modeActivation;
            modeInscription = false;
            motDePasse.clear();
            confirmationMotDePasse.clear();
            codeInvitation.clear();
            actualiserModeInscription();
        });
        inscription.setOnAction(e -> {
            if (modeActivation) {
                modeActivation = false;
                codeInvitation.clear();
                actualiserModeInscription();
                return;
            }
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
        VBox actionsCompte = new VBox(8, inscription, activation);
        actionsCompte.setAlignment(Pos.CENTER);

        etat.getStyleClass().add("login-mode");
        etat.setWrapText(true);
        etat.setText("version " + fr.ihm2026.core.Config.VERSION + " · poste " + IdentitePoste.nom()
                + " · rôle fixé : " + this.role.label());

        Label aide = new Label("Envoyez votre demande sans PIN. Après acceptation, un PIN d'activation "
                + "vous sera envoyé par e-mail. Un code distinct est envoyé à chaque connexion.");
        aide.getStyleClass().add("login-mode");
        aide.setWrapText(true);

        VBox carte = new VBox(10, marque, titre, sousTitre, choixWifi, decouverteServeur, serveurs, actualiserServeurs, etatDecouverte, avance,
                etiquetteInvitation, codeInvitation, etiquetteNom, nom, etiquetteEmail, email,
                etiquetteMotDePasse, motDePasse,
                etiquetteConfirmation, confirmationMotDePasse, etiquetteCodeConnexion, codeConnexion,
                ligne, actionsCompte, etat, aide);
        carte.getStyleClass().addAll("card", "login-card", "carte-reseau");
        carte.setAlignment(Pos.CENTER);
        carte.setMaxWidth(560);
        carte.setMaxHeight(Region.USE_PREF_SIZE);
        actualiserModeInscription();

        // Le formulaire d'inscription doit rester accessible sur un petit portable.
        carte.setMinHeight(Region.USE_PREF_SIZE);
        StackPane contenu = new StackPane(carte);
        contenu.setPadding(new javafx.geometry.Insets(24));
        ScrollPane defilement = new ScrollPane(contenu);
        defilement.setId("connexion-defilement");
        defilement.getStyleClass().add("connexion-scroll");
        defilement.setFitToWidth(true);
        defilement.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contenu.minHeightProperty().bind(defilement.heightProperty().subtract(4));
        getChildren().add(defilement);
        rechercher.run();
        setAlignment(Pos.CENTER);

        // Entrée depuis la carte vaut « Se connecter ».
        carte.setOnKeyPressed(e -> {
            if (e.getCode() == javafx.scene.input.KeyCode.ENTER) {
                seConnecter();
                e.consume(); // ne pas déclencher aussi le bouton par défaut
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
        if (connecter.isDisabled()) {
            return; // Entrée ne doit pas lancer une seconde requête pendant le traitement.
        }
        if (modeActivation) {
            activerCompte();
            return;
        }
        if (modeInscription) {
            sInscrire();
            return;
        }
        if (attenteCodeConnexion) {
            confirmerConnexion();
            return;
        }
        String adresse = adresseServeur.getText();
        if (adresse == null || adresse.isBlank()) {
            etat.setText("Choisissez un serveur dans la liste ou renseignez la connexion avancée.");
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
        activation.setDisable(true);
        connecter.setDisable(true);
        inscription.setDisable(true);
        email.setDisable(true);
        motDePasse.setDisable(true);
        adresseServeur.setDisable(true);
        etat.setText("Connexion au serveur central et envoi du code de sécurité…");
        TaskRunner.run(() -> {
            try {
                String hote = ClientReseau.choisirHote(adresse, IdentitePoste.port());
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
            inscription.setVisible(true);
            inscription.setManaged(true);
            definirChampsActifs(true);
            adresseServeur.setDisable(true);
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
        activation.setDisable(true);
        connecter.setDisable(true);
        inscription.setDisable(true);
        codeConnexion.setDisable(true);
        etat.setText("Vérification du code de sécurité…");
        TaskRunner.run(() -> {
            ClientAuthentificationHttps session = ClientAuthentificationHttps.confirmerCodeConnexion(
                    hoteServeur, ClientAuthentificationHttps.portConfigure(),
                    identifiantChallenge, code.strip());
            if (session.compte().role() != role) {
                session.close();
                throw new CodeConsommeException(
                        "Cette demande de compte est associée au rôle « "
                                + session.compte().role().label() + " », pas au rôle « "
                                + role.label() + " » de ce poste.");
            }
            try {
                Reseau.connecter(hoteServeur + ":" + IdentitePoste.port(), role);
                return session;
            } catch (RuntimeException ex) {
                session.close();
                throw new CodeConsommeException(ex.getMessage());
            }
        }, session -> {
            Reseau.installerAuthentification(session);
            Session.setRole(session.compte().role());
            router.go(ecranDuRole());
        }, erreur -> {
            inscription.setDisable(false);
            if (erreur instanceof CodeConsommeException) {
                attenteCodeConnexion = false;
                identifiantChallenge = null;
                hoteServeur = null;
                actualiserModeInscription();
                definirChampsActifs(true);
                codeConnexion.clear();
                etat.setText(erreur.getMessage() + " Recommencez la connexion pour recevoir un nouveau code.");
                motDePasse.requestFocus();
                return;
            }
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

    /** Le code a été consommé : une nouvelle authentification est nécessaire. */
    private static final class CodeConsommeException extends RuntimeException {
        private CodeConsommeException(String message) {
            super(message);
        }
    }

    private void activerCompte() {
        String adresse = adresseServeur.getText();
        if (adresse == null || adresse.isBlank()) {
            etat.setText("Choisissez un serveur dans la liste ou renseignez la connexion avancée.");
            return;
        }
        String emailSaisi = email.getText();
        String pin = codeInvitation.getText().strip();
        if (emailSaisi.isBlank() || !pin.matches("[0-9]{8}")) {
            etat.setText("Saisissez votre e-mail et le PIN d'activation à 8 chiffres reçu après acceptation.");
            return;
        }
        definirChampsActifs(false);
        TaskRunner.run(() -> {
            String hote = ClientReseau.choisirHote(adresse, IdentitePoste.port());
            ClientAuthentificationHttps.activerCompte(hote, ClientAuthentificationHttps.portConfigure(),
                    emailSaisi, pin, role);
            return true;
        }, resultat -> {
            modeActivation = false;
            codeInvitation.clear();
            definirChampsActifs(true);
            actualiserModeInscription();
            etat.setText("Compte activé. Connectez-vous avec votre e-mail et votre mot de passe.");
            motDePasse.requestFocus();
        }, erreur -> {
            definirChampsActifs(true);
            codeInvitation.clear();
            etat.setText(erreur.getMessage());
        });
    }

    private void sInscrire() {
        String adresse = adresseServeur.getText();
        if (adresse == null || adresse.isBlank()) {
            etat.setText("Choisissez un serveur dans la liste ou renseignez la connexion avancée.");
            return;
        }
        String nomSaisi = nom.getText();
        String emailSaisi = email.getText();
        char[] secret = motDePasse.getText().toCharArray();
        char[] confirmation = confirmationMotDePasse.getText().toCharArray();
        motDePasse.clear();
        confirmationMotDePasse.clear();
        if (nomSaisi == null || nomSaisi.isBlank() || emailSaisi == null || emailSaisi.isBlank()) {
            java.util.Arrays.fill(secret, '\0');
            java.util.Arrays.fill(confirmation, '\0');
            etat.setText("Le nom et l'e-mail sont obligatoires.");
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
        activation.setDisable(true);
        connecter.setDisable(true);
        inscription.setDisable(true);
        codeInvitation.setDisable(true);
        nom.setDisable(true);
        email.setDisable(true);
        motDePasse.setDisable(true);
        confirmationMotDePasse.setDisable(true);
        adresseServeur.setDisable(true);
        etat.setText("Envoi de la demande au serveur central…");
        TaskRunner.run(() -> {
            try {
                String hote = ClientReseau.choisirHote(adresse, IdentitePoste.port());
                return ClientAuthentificationHttps.inscrireCompte(
                        hote, ClientAuthentificationHttps.portConfigure(),
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
        inscription.setVisible(role != Session.Role.ADMINISTRATEUR || attenteCodeConnexion || modeActivation);
        inscription.setManaged(inscription.isVisible());
        etiquetteInvitation.setVisible(modeActivation);
        etiquetteInvitation.setManaged(modeActivation);
        codeInvitation.setVisible(modeActivation);
        codeInvitation.setManaged(modeActivation);
        motDePasse.setVisible(!modeActivation);
        motDePasse.setManaged(!modeActivation);
        etiquetteMotDePasse.setVisible(!modeActivation);
        etiquetteMotDePasse.setManaged(!modeActivation);
        activation.setText(modeActivation ? "Retour à la connexion" : "Activer mon compte avec le PIN reçu");
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
        inscription.setText(modeActivation ? "Retour à la connexion" : modeInscription ? "J'ai déjà un compte" : "Créer un compte");
        if (attenteCodeConnexion) {
            inscription.setText("Annuler");
        }
        connecter.setText(modeActivation ? "Activer mon compte" : modeInscription ? "Envoyer ma demande"
                : attenteCodeConnexion ? "Valider le code" : "Se connecter");
        connecter.setDefaultButton(true);
        etat.setText(modeActivation ? "Après acceptation : saisissez votre e-mail et le PIN reçu (48 heures)." : modeInscription
                ? "Votre demande sera en attente jusqu'à la décision d'un administrateur."
                : "version " + fr.ihm2026.core.Config.VERSION + " · poste " + IdentitePoste.nom()
                        + " · rôle fixé : " + role.label());
    }

    private void definirChampsActifs(boolean actifs) {
        adresseServeur.setDisable(!actifs);
        connecter.setDisable(!actifs);
        inscription.setDisable(!actifs);
        activation.setDisable(!actifs || attenteCodeConnexion);
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
