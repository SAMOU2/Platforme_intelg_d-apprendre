package fr.ihm2026.ui.commun;

import fr.ihm2026.api.service.ClientAuthentificationHttps;
import fr.ihm2026.core.Animations;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.reseau.ClientReseau;
import fr.ihm2026.reseau.EtatReseau;
import fr.ihm2026.reseau.IdentitePoste;
import fr.ihm2026.reseau.MemoireAppareilConfiance;
import fr.ihm2026.reseau.Reseau;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
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
    private final Label symboleEtat = new Label("ℹ");
    private final ProgressIndicator indicateurChargement = new ProgressIndicator();
    private final HBox bandeauEtat = new HBox(8, symboleEtat, etat, indicateurChargement);
    private ScrollPane defilementConnexion;
    private final Button connecter = new Button("Se connecter");
    private final CheckBox memoriserAppareil =
            new CheckBox("Faire confiance à ce poste pendant 2 jours");
    private final Button oublierAppareil = new Button("Oublier ce poste de confiance");
    private final Button inscription = new Button("Créer un compte");
    private boolean modeActivation;
    private final Button activation = new Button("Activer mon compte avec le PIN reçu");
    private final Label etiquetteMotDePasse = new Label("Mot de passe");
    private boolean modeInscription;
    private boolean attenteCodeConnexion;
    private String identifiantChallenge;
    private String hoteServeur;
    private long generationDecouverte;
    private final TextField portHttpsManuel = new TextField(Integer.toString(ClientAuthentificationHttps.portConfigure()));
    private int portChoisi = ClientAuthentificationHttps.portConfigure();
    private int portChallenge;
    private int portHttpsPour(String hote) {
        if(portChoisi<1 || portChoisi>65535)throw new IllegalArgumentException("Le port HTTPS doit être compris entre 1 et 65535.");
        return portChoisi;
    }

    public VueConnexionServeur(Router router, Session.Role role) {
        this.router = router;
        this.role = java.util.Objects.requireNonNull(role,
                "Un rôle de poste doit être configuré avant la connexion.");
        portHttpsManuel.textProperty().addListener((o,a,b) -> {
            try { portChoisi=Integer.parseInt(b.strip()); } catch(NumberFormatException ex){portChoisi=-1;}
        });
        getStyleClass().add("login-view");
        boolean visuelConnexion = PanneauVisuelConnexion.roleEligible(role);
        if (visuelConnexion) {
            getStyleClass().add("login-view--visuel");
        }
        etat.textProperty().addListener((o, ancien, message) -> actualiserPresentationEtat(message));

        Label decouverteServeur = new Label("Connexion à la plateforme du réseau");
        javafx.scene.control.ComboBox<fr.ihm2026.reseau.DecouverteServeurs.Serveur> serveurs =
                new javafx.scene.control.ComboBox<>();
        serveurs.setId("serveurs-locaux");
        serveurs.setAccessibleText("Plateforme du réseau à rejoindre");
        portHttpsManuel.setAccessibleText("Port HTTPS, connexion manuelle de secours");
        serveurs.setPromptText("Plusieurs plateformes : choisissez celle de votre établissement…");
        serveurs.setMaxWidth(Double.MAX_VALUE);
        serveurs.setVisible(false);
        serveurs.setManaged(false);
        Button actualiserServeurs = new Button("Réessayer");
        Button parametresWifi = new Button("Ouvrir les paramètres Wi-Fi Windows");
        parametresWifi.setVisible(fr.ihm2026.reseau.WifiWindows.disponible());
        parametresWifi.setManaged(parametresWifi.isVisible());
        Label etatDecouverte = new Label();
        etatDecouverte.setWrapText(true);
        etatDecouverte.setMaxWidth(Double.MAX_VALUE);
        etatDecouverte.getStyleClass().addAll("etat-reseau", "etat-information");
        etatDecouverte.textProperty().addListener((o, ancien, message) ->
                actualiserPresentationReseau(etatDecouverte, message));
        javafx.scene.control.TitledPane avance = new javafx.scene.control.TitledPane(
                "Options avancées — adresse manuelle", new VBox(8,new Label("Adresse du serveur"),adresseServeur,
                        new Label("Port HTTPS (annoncé automatiquement, saisie manuelle en secours)"),portHttpsManuel));
        avance.setExpanded(false);
        serveurs.disableProperty().bind(adresseServeur.disableProperty());
        avance.disableProperty().bind(adresseServeur.disableProperty());
        parametresWifi.disableProperty().bind(adresseServeur.disableProperty());
        javafx.beans.property.BooleanProperty rechercheEnCours =
                new javafx.beans.property.SimpleBooleanProperty(false);
        actualiserServeurs.disableProperty().bind(rechercheEnCours.or(adresseServeur.disableProperty()));
        serveurs.setOnAction(e -> {
            var choix = serveurs.getValue();
            if (choix != null && !adresseServeur.isDisabled()) {
                portHttpsManuel.setText(Integer.toString(choix.portHttps()));
                adresseServeur.setText(choix.hote());
                etatDecouverte.setText("Plateforme sélectionnée : " + choix.nom()
                        + ". Connectez-vous à votre compte. L'identité sera vérifiée par HTTPS.");
            }
        });
        Runnable rechercher = () -> {
            if (adresseServeur.isDisabled() || rechercheEnCours.get()) return;
            long generation = ++generationDecouverte;
            rechercheEnCours.set(true);
            // Garder le formulaire et la selection utilisables pendant la recherche de fond.
            if(serveurs.getItems().isEmpty())etatDecouverte.setText("Recherche de la plateforme sur votre réseau…");
            TaskRunner.run(() -> fr.ihm2026.reseau.DecouverteServeurs.chercher(IdentitePoste.port()),
                    liste -> {
                        if (generation != generationDecouverte) return;
                        rechercheEnCours.set(false);
                        if (adresseServeur.isDisabled()) return;
                        if (avance.isExpanded() && !adresseServeur.getText().isBlank()) {
                            etatDecouverte.setText("Adresse manuelle conservée. Connectez-vous à votre compte.");
                            return;
                        }
                        var selection=serveurs.getValue();
                        if(!serveurs.getItems().equals(liste))serveurs.getItems().setAll(liste);
                        serveurs.setVisible(liste.size()>1);serveurs.setManaged(liste.size()>1);
                        if (liste.size() == 1) {
                            serveurs.getSelectionModel().selectFirst();
                            adresseServeur.setText(liste.get(0).hote());
                            portHttpsManuel.setText(Integer.toString(liste.get(0).portHttps()));
                        } else if (liste.size() > 1) {
                            serveurs.setVisible(true);
                            serveurs.setManaged(true);
                            if(selection!=null && liste.contains(selection))serveurs.getSelectionModel().select(selection);
                            else adresseServeur.clear();
                            etatDecouverte.setText("Plusieurs plateformes détectées. Choisissez celle de votre établissement.");
                        } else {
                            adresseServeur.clear();
                            etatDecouverte.setText("Aucun serveur de plateforme trouvé. Vérifiez votre connexion réseau "
                                    + "et que PC1 est démarré, puis cliquez sur Réessayer.");
                        }
                    }, erreur -> {
                        if (generation != generationDecouverte) return;
                        rechercheEnCours.set(false);
                        etatDecouverte.setText("Recherche impossible. Vérifiez votre réseau, puis réessayez "
                                + "ou utilisez les options avancées.");
                    });
        };
        actualiserServeurs.setOnAction(e -> {
            if (!avance.isExpanded()) adresseServeur.clear();
            rechercher.run();
        });
        new ActualisationVue<Long>(this,System::nanoTime,tick -> {
            if(!avance.isExpanded() && !adresseServeur.isDisabled() && !rechercheEnCours.get())rechercher.run();
        },erreur -> { });
        sceneProperty().addListener((o,a,b) -> {if(b==null)generationDecouverte++;});
        parametresWifi.setOnAction(e -> TaskRunner.run(() -> {
            fr.ihm2026.reseau.WifiWindows.ouvrirParametres();
            return true;
        }, ok -> etatDecouverte.setText("Windows gère votre connexion Wi-Fi. Une fois connecté, cliquez sur Réessayer."),
                erreur -> etatDecouverte.setText("Ouvrez les paramètres réseau dans Windows, puis cliquez sur Réessayer.")));
        adresseServeur.setText(System.getProperty("ihm.serveur", ""));
        avance.setExpanded(!adresseServeur.getText().isBlank());
        adresseServeur.setPromptText("Exemple : 192.168.1.10 (Docker uniquement sur PC1)");
        adresseServeur.setMaxWidth(Double.MAX_VALUE);
        adresseServeur.getStyleClass().add("champ-adresse");
        adresseServeur.setId("serveur-ip");
        decouverteServeur.setLabelFor(serveurs);
        adresseServeur.setAccessibleText("Adresse IP ou nom du PC serveur, uniquement pour la connexion manuelle de secours.");
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

        email.setAccessibleText("Adresse e-mail du compte");
        nom.setAccessibleText("Nom et prénom");
        motDePasse.setAccessibleText("Mot de passe du compte");
        confirmationMotDePasse.setAccessibleText("Confirmation du mot de passe");
        codeInvitation.setAccessibleText("PIN d’activation, reçu après acceptation du compte");
        codeConnexion.setAccessibleText("Code de connexion reçu par e-mail, six chiffres");
        memoriserAppareil.setAccessibleText("Faire confiance à ce poste pendant deux jours. "
                + "Le mot de passe restera demandé, mais le code e-mail ne sera pas redemandé "
                + "avant quarante-huit heures. Ne cochez pas cette case sur un poste partagé.");
        oublierAppareil.setAccessibleText("Oublier ce poste de confiance pour l'adresse e-mail saisie");
        oublierAppareil.setOnAction(e -> oublierAppareilConfiance());
        email.textProperty().addListener((observable, ancien, nouveau) -> {
            if (!modeActivation && !modeInscription
                    && MemoireAppareilConfiance.lire(nouveau, role).isPresent()) {
                memoriserAppareil.setSelected(true);
            }
        });
        etat.setFocusTraversable(true);
        etat.setAccessibleHelp("État de la connexion ou erreur. Utilisez les commandes de lecture du lecteur d’écran.");
        bandeauEtat.setId("connexion-bandeau-etat");
        bandeauEtat.getStyleClass().addAll("etat-formulaire", "etat-information");
        bandeauEtat.setAlignment(Pos.CENTER_LEFT);
        bandeauEtat.setMaxWidth(Double.MAX_VALUE);
        symboleEtat.getStyleClass().add("etat-symbole");
        symboleEtat.setFocusTraversable(false);
        symboleEtat.setAccessibleText("Information");
        etat.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(etat, Priority.ALWAYS);
        indicateurChargement.getStyleClass().add("indicateur-chargement");
        indicateurChargement.setPrefSize(16, 16);
        indicateurChargement.setVisible(false);
        indicateurChargement.setManaged(false);
        indicateurChargement.setAccessibleText("Chargement en cours");
        etatDecouverte.setFocusTraversable(true);
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
        memoriserAppareil.setId("connexion-appareil-confiance");
        oublierAppareil.setId("connexion-oublier-appareil");
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
        ligne.setAlignment(Pos.CENTER_LEFT);
        VBox actionsCompte = new VBox(8, inscription, activation, oublierAppareil);
        actionsCompte.setAlignment(Pos.CENTER_LEFT);

        etat.getStyleClass().add("etat-texte");
        etat.setWrapText(true);
        etat.setText("version " + fr.ihm2026.core.Config.VERSION + " · poste " + IdentitePoste.nom()
                + " · rôle fixé : " + this.role.label());

        Label aide = new Label("Envoyez votre demande sans PIN. Après acceptation, un PIN d'activation "
                + "vous sera envoyé par e-mail. Sur un poste de confiance, le code e-mail de connexion "
                + "n'est redemandé qu'après 2 jours ; le mot de passe reste demandé à chaque ouverture.");
        aide.getStyleClass().add("login-mode");
        aide.setWrapText(true);

        VBox carte = new VBox(10, decouverteServeur, etatDecouverte, serveurs, actualiserServeurs, parametresWifi, avance,
                etiquetteInvitation, codeInvitation, etiquetteNom, nom, etiquetteEmail, email,
                etiquetteMotDePasse, ChampSecret.envelopper(motDePasse),
                etiquetteConfirmation, ChampSecret.envelopper(confirmationMotDePasse), etiquetteCodeConnexion, codeConnexion,
                memoriserAppareil, ligne, bandeauEtat, actionsCompte, aide);
        carte.getStyleClass().addAll("card", "login-card", "carte-reseau");
        carte.setAlignment(Pos.CENTER_LEFT);
        carte.setMaxWidth(560);
        carte.setMaxHeight(Region.USE_PREF_SIZE);
        actualiserModeInscription();

        // Le formulaire d'inscription doit rester accessible sur un petit portable.
        carte.setMinHeight(Region.USE_PREF_SIZE);
        StackPane contenu = new StackPane(carte);
        contenu.setPadding(new javafx.geometry.Insets(24));
        // Carte de connexion ancrée à droite, sur le fond plein écran, dans tous les rôles.
        contenu.setAlignment(Pos.CENTER_RIGHT);
        ScrollPane defilement = new ScrollPane(contenu);
        defilementConnexion = defilement;
        defilement.setId("connexion-defilement");
        defilement.getStyleClass().add("connexion-scroll");
        defilement.setFitToWidth(true);
        defilement.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        contenu.minHeightProperty().bind(defilement.heightProperty().subtract(4));
        if (visuelConnexion) {
            // Fond plein écran animé derrière le formulaire, dans tous les rôles.
            // Le panneau occupe toute la vue (maxSize illimité) et son image est non
            // gérée : le visuel ne participe jamais au calcul de la taille de la fenêtre.
            PanneauVisuelConnexion visuel = new PanneauVisuelConnexion(role);
            getChildren().addAll(visuel, defilement);
        } else {
            getChildren().add(defilement);
        }
        rechercher.run();
        setAlignment(Pos.CENTER_RIGHT);

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
            setAlignment(deborde ? Pos.TOP_RIGHT : Pos.CENTER_RIGHT);
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
            // Entrée du login : la carte monte et se découvre (fondu + légère croissance).
            Animations.apparaitrePanneau(carte);
        });
    }

    /**
     * Le message d'état reste près du bouton principal. Le symbole et la couleur
     * doublent le texte ; aucun des deux ne remplace l'explication complète.
     */
    private void actualiserPresentationEtat(String message) {
        String texte = message == null ? "" : message;
        String minuscules = texte.toLowerCase(java.util.Locale.ROOT);
        boolean chargement = texte.endsWith("…");
        String classe;
        String symbole;
        String nature;
        if (chargement) {
            classe = "etat-chargement";
            symbole = "↻";
            nature = "Chargement";
        } else if (minuscules.contains("impossible") || minuscules.contains("indisponible")
                || minuscules.contains("inaccessible") || minuscules.contains("invalide")
                || minuscules.contains("expir") || minuscules.contains("interromp")
                || minuscules.contains("annulée") || minuscules.contains("illisible")
                || minuscules.contains("incomplète") || minuscules.contains("n'a pas répondu")
                || minuscules.contains("réservée") || minuscules.contains("refus")
                || minuscules.contains("associé au rôle") || minuscules.contains("n'a pas pu")) {
            classe = "etat-erreur";
            symbole = "✕";
            nature = "Erreur";
        } else if (minuscules.startsWith("compte activé")
                || minuscules.startsWith("poste de confiance oublié")
                || minuscules.startsWith("demande de compte")
                || minuscules.startsWith("un code")
                || minuscules.contains("demande envoyée")) {
            classe = "etat-succes";
            symbole = "✓";
            nature = "Succès";
        } else if (minuscules.startsWith("saisissez") || minuscules.startsWith("attendez")
                || minuscules.startsWith("le nom") || minuscules.startsWith("les mots")
                || minuscules.startsWith("le mot de passe doit")
                || minuscules.startsWith("aucun poste")
                || minuscules.startsWith("après acceptation")
                || minuscules.startsWith("votre demande sera")
                || minuscules.startsWith("vérification annulée")
                || minuscules.startsWith("poste oublié sur cet ordinateur")) {
            classe = "etat-avertissement";
            symbole = "⚠";
            nature = "À vérifier";
        } else {
            classe = "etat-information";
            symbole = "ℹ";
            nature = "Information";
        }
        bandeauEtat.getStyleClass().removeAll("etat-information", "etat-chargement",
                "etat-succes", "etat-avertissement", "etat-erreur");
        bandeauEtat.getStyleClass().add(classe);
        symboleEtat.setText(symbole);
        symboleEtat.setAccessibleText(nature);
        etat.setAccessibleText(texte.isBlank() ? nature : nature + " : " + texte);
        indicateurChargement.setVisible(chargement);
        indicateurChargement.setManaged(chargement);
        if (chargement || "etat-erreur".equals(classe) || "etat-succes".equals(classe)) {
            javafx.application.Platform.runLater(this::rendreEtatVisible);
        }
    }

    private static void actualiserPresentationReseau(Label etiquette, String message) {
        String texte = message == null ? "" : message;
        String minuscules = texte.toLowerCase(java.util.Locale.ROOT);
        String classe;
        String nature;
        if (texte.endsWith("…")) {
            classe = "etat-chargement";
            nature = "Recherche réseau en cours";
        } else if (minuscules.startsWith("plateforme sélectionnée")
                || minuscules.startsWith("adresse manuelle conservée")
                || minuscules.startsWith("windows gère")) {
            classe = "etat-succes";
            nature = "Plateforme prête";
        } else if (minuscules.startsWith("aucun serveur") || minuscules.contains("impossible")) {
            classe = "etat-erreur";
            nature = "Plateforme introuvable";
        } else if (minuscules.startsWith("plusieurs plateformes")
                || minuscules.startsWith("ouvrez les paramètres")) {
            classe = "etat-avertissement";
            nature = "Choix réseau à vérifier";
        } else {
            classe = "etat-information";
            nature = "État du réseau";
        }
        etiquette.getStyleClass().removeAll("etat-information", "etat-chargement",
                "etat-succes", "etat-avertissement", "etat-erreur");
        etiquette.getStyleClass().add(classe);
        etiquette.setAccessibleText(texte.isBlank() ? nature : nature + " : " + texte);
    }

    private void rendreEtatVisible() {
        if (defilementConnexion == null || bandeauEtat.getScene() == null) {
            return;
        }
        javafx.scene.Node contenu = defilementConnexion.getContent();
        if (contenu == null || contenu.getScene() == null) {
            return;
        }
        double hauteurContenu = contenu.getBoundsInLocal().getHeight();
        double hauteurVisible = defilementConnexion.getViewportBounds().getHeight();
        double defilable = hauteurContenu - hauteurVisible;
        if (defilable <= 0) {
            return;
        }
        double hautBandeau = bandeauEtat.localToScene(0, 0).getY()
                - contenu.localToScene(0, 0).getY();
        defilementConnexion.setVvalue(Math.max(0, Math.min(1, (hautBandeau - 16) / defilable)));
    }

    /** Écran d'accueil du rôle : c'est le seul endroit où le rôle choisit une destination. */
    public static String ecranDuRole(Session.Role role) {
        return switch (role) {
            case ENSEIGNANT -> Router.RESULTATS;
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
            etat.setText("Attendez la détection de la plateforme, cliquez sur Réessayer ou utilisez les options avancées.");
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
        boolean utiliserConfiance = memoriserAppareil.isSelected();
        memoriserAppareil.setDisable(true);
        oublierAppareil.setDisable(true);
        etat.setText("Connexion au serveur central…");
        TaskRunner.run(() -> {
            try {
                String hote = ClientReseau.choisirHote(adresse, IdentitePoste.port());
                int port = portHttpsPour(hote);
                var confiance = utiliserConfiance
                        ? MemoireAppareilConfiance.lire(identifiant, role)
                        : java.util.Optional.<MemoireAppareilConfiance.Confiance>empty();
                var debut = ClientAuthentificationHttps.commencerConnexion(
                        hote, port, identifiant, secret,
                        confiance.map(MemoireAppareilConfiance.Confiance::jeton).orElse(null));
                if (!debut.codeRequis()) {
                    return new ResultatDebutConnexion(hote, port,
                            preparerSession(debut.session(), hote, false), null,
                            confiance.isPresent());
                }
                return new ResultatDebutConnexion(hote, port, null, debut.challenge(),
                        confiance.isPresent());
            } finally {
                java.util.Arrays.fill(secret, '\0');
            }
        }, demande -> {
            if (demande.sessionDirecte() != null) {
                terminerConnexion(demande.sessionDirecte());
                return;
            }
            if (demande.confianceEnvoyee()) {
                // L'ancien jeton est expiré/révoqué ; la case reste cochée afin que
                // le code sur le point d'être validé ouvre une nouvelle fenêtre de 48 h.
                MemoireAppareilConfiance.oublier(identifiant, role);
            }
            hoteServeur = demande.hote();
            portChallenge = demande.port();
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
            oublierAppareil.setVisible(false);
            oublierAppareil.setManaged(false);
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
        boolean memoriser = memoriserAppareil.isSelected();
        activation.setDisable(true);
        connecter.setDisable(true);
        inscription.setDisable(true);
        codeConnexion.setDisable(true);
        memoriserAppareil.setDisable(true);
        oublierAppareil.setDisable(true);
        etat.setText("Vérification du code de sécurité…");
        TaskRunner.run(() -> {
            ClientAuthentificationHttps session = ClientAuthentificationHttps.confirmerCodeConnexion(
                    hoteServeur, portChallenge,
                    identifiantChallenge, code.strip(), memoriser, IdentitePoste.libelle());
            return preparerSession(session, hoteServeur, true);
        }, session -> {
            terminerConnexion(session);
        }, erreur -> {
            inscription.setDisable(false);
            memoriserAppareil.setDisable(false);
            oublierAppareil.setDisable(false);
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

    private ClientAuthentificationHttps preparerSession(ClientAuthentificationHttps session,
            String hote, boolean apresCode) {
        if (session.compte().role() != role) {
            String message = "Ce compte est associé au rôle « "
                    + session.compte().role().label() + " », pas au rôle « "
                    + role.label() + " » de ce poste.";
            session.close();
            if (apresCode) {
                throw new CodeConsommeException(message);
            }
            throw new ClientAuthentificationHttps.ErreurAuthentification(message);
        }
        try {
            Reseau.connecter(hote + ":" + IdentitePoste.port(), role);
            return session;
        } catch (RuntimeException ex) {
            session.close();
            if (apresCode) {
                throw new CodeConsommeException(ex.getMessage());
            }
            throw ex;
        }
    }

    private void terminerConnexion(ClientAuthentificationHttps session) {
        session.appareilConfiance().ifPresent(confiance -> MemoireAppareilConfiance.ecrire(
                session.compte().email(), role, confiance.jeton(), confiance.expireLe()));
        Reseau.installerAuthentification(session);
        Session.setRole(session.compte().role());
        router.go(ecranDuRole());
    }

    private void oublierAppareilConfiance() {
        if (modeActivation || modeInscription || attenteCodeConnexion) {
            return;
        }
        String identifiant = email.getText();
        if (identifiant == null || identifiant.isBlank()) {
            etat.setText("Saisissez d'abord l'adresse e-mail du compte dont ce poste doit être oublié.");
            email.requestFocus();
            return;
        }
        var confiance = MemoireAppareilConfiance.lire(identifiant, role);
        MemoireAppareilConfiance.oublier(identifiant, role);
        memoriserAppareil.setSelected(false);
        if (confiance.isEmpty()) {
            etat.setText("Aucun poste de confiance n'est enregistré localement pour ce compte.");
            return;
        }
        String adresse = adresseServeur.getText();
        if (adresse == null || adresse.isBlank()) {
            etat.setText("Poste oublié sur cet ordinateur : son jeton local a été supprimé. "
                    + "L'enregistrement serveur éventuel expirera au plus tard après 48 heures.");
            return;
        }
        String jeton = confiance.get().jeton();
        oublierAppareil.setDisable(true);
        etat.setText("Oubli du poste de confiance…");
        TaskRunner.run(() -> {
            String hote = ClientReseau.choisirHote(adresse, IdentitePoste.port());
            ClientAuthentificationHttps.oublierAppareilConfiance(
                    hote, portHttpsPour(hote), jeton);
            return true;
        }, ok -> {
            oublierAppareil.setDisable(false);
            etat.setText("Poste de confiance oublié. Un code e-mail sera demandé à la prochaine connexion.");
        }, erreur -> {
            oublierAppareil.setDisable(false);
            etat.setText("Poste oublié sur cet ordinateur, mais la révocation n'a pas pu être "
                    + "confirmée par le serveur. Le jeton expirera au plus tard après 48 heures.");
        });
    }

    private void activerCompte() {
        String adresse = adresseServeur.getText();
        if (adresse == null || adresse.isBlank()) {
            etat.setText("Attendez la détection de la plateforme, cliquez sur Réessayer ou utilisez les options avancées.");
            return;
        }
        String emailSaisi = email.getText();
        String pin = codeInvitation.getText().strip();
        if (emailSaisi.isBlank() || !pin.matches("[0-9]{8}")) {
            etat.setText("Saisissez votre e-mail et le PIN d'activation à 8 chiffres reçu après acceptation.");
            return;
        }
        definirChampsActifs(false);
        etat.setText("Activation du compte…");
        TaskRunner.run(() -> {
            String hote = ClientReseau.choisirHote(adresse, IdentitePoste.port());
            ClientAuthentificationHttps.activerCompte(hote, portHttpsPour(hote),
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
            etat.setText("Attendez la détection de la plateforme, cliquez sur Réessayer ou utilisez les options avancées.");
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
                        hote, portHttpsPour(hote),
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
            etat.setText(compte.message() != null && !compte.message().isBlank()
                    ? compte.message()
                    : "Demande de compte " + role.label()
                            + " envoyée. Elle doit être traitée avant votre première connexion.");
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
        boolean connexionCompte = !modeActivation && !modeInscription;
        memoriserAppareil.setVisible(connexionCompte);
        memoriserAppareil.setManaged(connexionCompte);
        oublierAppareil.setVisible(connexionCompte && !attenteCodeConnexion);
        oublierAppareil.setManaged(connexionCompte && !attenteCodeConnexion);
        inscription.setText(modeActivation ? "Retour à la connexion" : modeInscription ? "J'ai déjà un compte" : "Créer un compte");
        if (attenteCodeConnexion) {
            inscription.setText("Annuler");
        }
        connecter.setText(modeActivation ? "Activer mon compte" : modeInscription ? "Envoyer ma demande"
                : attenteCodeConnexion ? "Valider le code" : "Se connecter");
        connecter.setDefaultButton(true);
        etat.setText(modeActivation ? "Après acceptation : saisissez votre e-mail et le PIN reçu (48 heures)." : modeInscription
                ? "Votre demande sera traitée selon la politique du serveur : pré-acceptation possible pour certains apprenants, sinon décision d'un administrateur."
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
        memoriserAppareil.setDisable(!actifs);
        oublierAppareil.setDisable(!actifs);
        inscription.setDisable(!actifs);
    }

    private record ResultatDebutConnexion(String hote, int port,
            ClientAuthentificationHttps sessionDirecte,
            ClientAuthentificationHttps.ChallengeConnexion challenge,
            boolean confianceEnvoyee) {
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
