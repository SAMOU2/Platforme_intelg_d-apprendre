package fr.ihm2026.ui.admin;

import fr.ihm2026.api.service.AdminApi;
import fr.ihm2026.api.service.ClientAuthentificationHttps;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.model.UtilisateurVM;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.EtatVide;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** AD1 — administrer les comptes et les rôles (un seul rôle par compte, confirmation avant suppression). */
public class ComptesView extends BorderPane {

    private boolean operationCompteEnCours;
    private final TableView<UtilisateurVM> table =
            new TableView<>(FXCollections.observableArrayList(AdminApi.comptes()));
    private final Label resumeInscriptions = new Label();

    public ComptesView() {
        getStyleClass().add("vue");
        setTop(new EnteteVue("Comptes et rôles",
                "Validez les demandes de création avant d'autoriser la connexion."));

        // Les colonnes se partagent la largeur : plus de colonne vide en bout de tableau.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getStyleClass().add("table-comptes");
        table.setPlaceholder(new EtatVide("Aucun compte enregistré."));

        TableColumn<UtilisateurVM, String> cNom = new TableColumn<>("Nom");
        cNom.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getNom()));
        cNom.setPrefWidth(200);

        TableColumn<UtilisateurVM, String> cEmail = new TableColumn<>("E-mail");
        cEmail.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getEmail()));
        cEmail.setPrefWidth(260);

        TableColumn<UtilisateurVM, String> cRole = new TableColumn<>("Rôle");
        cRole.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getRole()));
        cRole.setPrefWidth(150);

        TableColumn<UtilisateurVM, String> cEtat = new TableColumn<>("État");
        cEtat.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getEtatCompte()));
        cEtat.setPrefWidth(120);

        table.getColumns().addAll(cNom, cEmail, cRole, cEtat);
        VBox.setVgrow(table, Priority.ALWAYS);

        Button creer = new Button("Créer un compte");
        creer.getStyleClass().add("btn-primary");
        creer.setOnAction(e -> creerCompte());

        Button genererInvitation = new Button("Renvoyer le PIN d'activation");
        genererInvitation.setOnAction(e -> creerInvitation());

        Button desactiver = new Button("Désactiver");
        desactiver.setDisable(true);
        desactiver.setOnAction(e -> definirActif(false));

        Button activer = new Button("Activer");
        activer.setDisable(true);
        activer.setOnAction(e -> definirActif(true));
        Button accepter = new Button("Accepter");
        accepter.setDisable(true);
        accepter.getStyleClass().add("btn-primary");
        accepter.setOnAction(e -> deciderInscription(true));
        Button refuser = new Button("Refuser");
        refuser.setDisable(true);
        refuser.setOnAction(e -> deciderInscription(false));
        table.getSelectionModel().selectedItemProperty().addListener((obs, ancienne, selection) -> {
            boolean enAttente = selection != null && selection.isEnAttente();
            accepter.setDisable(!enAttente);
            refuser.setDisable(!enAttente);
            boolean accepte = selection != null && "ACCEPTE".equals(selection.getStatutInscription());
            desactiver.setDisable(!accepte || !selection.isActif());
            activer.setDisable(!accepte || selection.isActif());
        });

        Button actualiser = new Button("Actualiser");
        actualiser.setOnAction(e -> actualiserComptes());
        Button audit = new Button("Journal d'audit");
        audit.setOnAction(e -> Dialogue.info(getScene().getWindow(), "Journal d'audit",
                journalTexte()));

        HBox barre = new HBox(10, creer, genererInvitation, accepter, refuser,
                activer, desactiver, actualiser, audit);
        barre.getStyleClass().add("barre-actions");

        resumeInscriptions.getStyleClass().add("texte-secondaire");
        actualiserResumeInscriptions();
        Label note = new Label("Les comptes en attente ne peuvent pas se connecter. Toute décision est journalisée.");
        note.getStyleClass().add("texte-secondaire");

        Button affectations = new Button("Affecter un apprenant à un enseignant");
        affectations.setOnAction(e -> fr.ihm2026.ui.commun.SuiviPedagogique.affecter(getScene().getWindow()));
        affectations.setDisable(!fr.ihm2026.reseau.Reseau.sessionCompteInstallee());
        Button preparerTest = new Button("Configurer le sujet d'un test");
        preparerTest.setDisable(!fr.ihm2026.reseau.Reseau.sessionCompteInstallee());
        preparerTest.setOnAction(e -> fr.ihm2026.ui.commun.SuiviPedagogique.preparerTest(getScene().getWindow()));
        VBox contenu = new VBox(12, resumeInscriptions, table, barre, affectations, preparerTest, note);
        contenu.getStyleClass().add("colonne");
        setCenter(contenu);
        BorderPane.setMargin(contenu, new Insets(4, 0, 0, 0));
    }

    private void deciderInscription(boolean acceptee) {
        if (operationCompteEnCours) return;
        UtilisateurVM selection = table.getSelectionModel().getSelectedItem();
        if (selection == null || !selection.isEnAttente()) {
            Dialogue.erreur(getScene().getWindow(), "Aucune demande en attente",
                    "Sélectionnez une inscription en attente.");
            return;
        }
        String decision = acceptee ? "Accepter" : "Refuser";
        String question = acceptee
                ? "Accepter et envoyer un PIN d’activation à " + selection.getNom() + " pour le rôle "
                        + selection.getRole() + " ?"
                : "Refuser définitivement la demande de " + selection.getNom() + " ?";
        if (Dialogue.confirmer(getScene().getWindow(), decision + " l'inscription ?", question, decision)) {
            operationCompteEnCours = true;
            table.setDisable(true);
            TaskRunner.run(() -> AdminApi.deciderInscription(selection.getEmail(), acceptee), resultat -> {
                operationCompteEnCours = false;
                table.setDisable(false);
                remplacer(selection, resultat);
                if (acceptee) Dialogue.info(getScene().getWindow(), "Demande acceptée",
                        "Le PIN a été envoyé par e-mail. Le compte reste inactif jusqu'à son activation.");
            }, erreur -> {
                operationCompteEnCours = false;
                table.setDisable(false);
                Dialogue.erreur(getScene().getWindow(), "Décision / notification", erreur.getMessage());
            });
        }
    }

    private void actualiserComptes() {
        try {
            table.getItems().setAll(AdminApi.comptes());
            actualiserResumeInscriptions();
        } catch (IllegalStateException | UnsupportedOperationException
                | ClientAuthentificationHttps.ErreurAuthentification e) {
            Dialogue.erreur(getScene().getWindow(), "Actualisation impossible", e.getMessage());
        }
    }

    private void remplacer(UtilisateurVM ancien, UtilisateurVM nouveau) {
        int index = table.getItems().indexOf(ancien);
        if (index >= 0) {
            table.getItems().set(index, nouveau);
        }
        actualiserResumeInscriptions();
    }

    private void actualiserResumeInscriptions() {
        long attente = table.getItems().stream().filter(UtilisateurVM::isEnAttente).count();
        long acceptees = table.getItems().stream()
                .filter(compte -> "ACCEPTE".equals(compte.getStatutInscription())).count();
        resumeInscriptions.setText(attente + " demande(s) en attente · "
                + acceptees + " compte(s) accepté(s)");
    }

    private void creerCompte() {
        TextField nom = new TextField();
        nom.setPromptText("Nom et prénom");
        TextField email = new TextField();
        email.setPromptText("prenom.nom@univ.dz");
        PasswordField motDePasse = new PasswordField();
        motDePasse.setPromptText("Au moins 12 caractères");
        ComboBox<Session.Role> role = new ComboBox<>();
        role.getItems().setAll(Session.Role.values());
        role.setValue(Session.Role.APPRENANT);
        CheckBox actif = new CheckBox("Compte actif");
        actif.setSelected(true);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("Nom"), nom);
        form.addRow(1, new Label("E-mail"), email);
        form.addRow(2, new Label("Mot de passe temporaire"), fr.ihm2026.ui.commun.ChampSecret.envelopper(motDePasse));
        form.addRow(3, new Label("Rôle"), role);
        form.addRow(4, new Label("État"), actif);

        javafx.scene.control.Dialog<ButtonTypeResultat> dialogue = new javafx.scene.control.Dialog<>();
        dialogue.setTitle("Créer un compte");
        dialogue.setHeaderText("Un seul rôle par compte (ALT du modèle AD1).");
        dialogue.getDialogPane().setContent(form);
        var boutonCreer = new javafx.scene.control.ButtonType("Créer",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(boutonCreer,
                new javafx.scene.control.ButtonType("Annuler", javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        dialogue.setResultConverter(b -> b == boutonCreer ? new ButtonTypeResultat(nom.getText(),
                email.getText(), motDePasse.getText().toCharArray(), role.getValue(),
                actif.isSelected()) : null);

        dialogue.initOwner(getScene().getWindow());
        Dialogue.appliquerTheme(dialogue.getDialogPane(), getScene().getWindow());
        dialogue.showAndWait().ifPresent(resultat -> {
            if (resultat.nom() == null || resultat.nom().isBlank() || resultat.email() == null
                    || resultat.email().isBlank()) {
                java.util.Arrays.fill(resultat.motDePasse(), '\0');
                Dialogue.erreur(getScene().getWindow(), "Champs incomplets",
                        "Le nom et l'e-mail sont obligatoires pour créer un compte.");
                return;
            }

            if (resultat.role() == null) {
                Dialogue.erreur(getScene().getWindow(), "Rôle manquant",
                        "Sélectionnez un rôle pour créer le compte.");
                java.util.Arrays.fill(resultat.motDePasse(), '\0');
                return;
            }
            if (resultat.motDePasse().length < 12
                    || new String(resultat.motDePasse()).getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
                java.util.Arrays.fill(resultat.motDePasse(), '\0');
                Dialogue.erreur(getScene().getWindow(), "Mot de passe invalide",
                        "Le mot de passe doit contenir au moins 12 caractères et au plus 72 octets UTF-8.");
                return;
            }
            try {
                table.getItems().add(AdminApi.creerCompte(resultat.nom(), resultat.email(),
                        resultat.motDePasse(), resultat.role(), resultat.actif()));
                actualiserResumeInscriptions();
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
                    | ClientAuthentificationHttps.ErreurAuthentification e) {
                java.util.Arrays.fill(resultat.motDePasse(), '\0');
                Dialogue.erreur(getScene().getWindow(), "Création impossible", e.getMessage());
            }
        });
    }

    private void creerInvitation() {
        TextField email = new TextField();
        email.setPromptText("prenom.nom@univ.dz");
        ComboBox<Session.Role> role = new ComboBox<>();
        role.getItems().setAll(Session.Role.values());
        role.setValue(Session.Role.APPRENANT);

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.addRow(0, new Label("E-mail destinataire"), email);
        form.addRow(1, new Label("Rôle autorisé"), role);

        javafx.scene.control.Dialog<ButtonTypeResultatInvitation> dialogue =
                new javafx.scene.control.Dialog<>();
        dialogue.setTitle("Renvoyer le PIN d’activation");
        dialogue.setHeaderText("Compte déjà accepté uniquement. Un nouveau PIN invalide le précédent, valable 48 heures.");
        dialogue.getDialogPane().setContent(form);
        var boutonCreer = new javafx.scene.control.ButtonType("Générer",
                javafx.scene.control.ButtonBar.ButtonData.OK_DONE);
        dialogue.getDialogPane().getButtonTypes().addAll(boutonCreer,
                new javafx.scene.control.ButtonType("Annuler",
                        javafx.scene.control.ButtonBar.ButtonData.CANCEL_CLOSE));
        dialogue.setResultConverter(b -> b == boutonCreer
                ? new ButtonTypeResultatInvitation(email.getText(), role.getValue()) : null);
        dialogue.initOwner(getScene().getWindow());
        Dialogue.appliquerTheme(dialogue.getDialogPane(), getScene().getWindow());
        dialogue.showAndWait().ifPresent(resultat -> {
            if (resultat.email() == null || resultat.email().isBlank() || resultat.role() == null) {
                Dialogue.erreur(getScene().getWindow(), "Informations manquantes",
                        "Saisissez l'adresse e-mail et choisissez le rôle de l'invitation.");
                return;
            }
            if (operationCompteEnCours) return;
            operationCompteEnCours = true;
            TaskRunner.run(() -> AdminApi.creerInvitation(resultat.email(), resultat.role()), invitation -> {
                operationCompteEnCours = false;
                Dialogue.info(getScene().getWindow(), "Invitation envoyée",
                        invitation.message()
                                + "\nDestinataire : " + invitation.email()
                                + "\nRôle : " + invitation.role().label()
                                + "\nValable jusqu'au : " + invitation.expireLe());
            }, erreur -> {
                operationCompteEnCours = false;
                Dialogue.erreur(getScene().getWindow(), "Renvoi du PIN impossible", erreur.getMessage());
            });
        });
    }

    private void definirActif(boolean actif) {
        UtilisateurVM selection = table.getSelectionModel().getSelectedItem();
        if (selection == null) {
            Dialogue.erreur(getScene().getWindow(), "Aucune sélection",
                    "Sélectionnez d'abord un compte dans la liste.");
            return;
        }
        String action = actif ? "Activer" : "Désactiver";
        String message = actif
                ? "Autoriser à nouveau " + selection.getNom() + " à utiliser son compte ?"
                : "Le compte de " + selection.getNom() + " ne pourra plus se connecter. "
                        + "Ses tentatives et ses notes sont conservées.";
        if (Dialogue.confirmer(getScene().getWindow(), action + " ce compte ?", message, action)) {
            try {
                remplacer(selection, AdminApi.definirCompteActif(selection.getEmail(), actif));
            } catch (IllegalArgumentException | IllegalStateException | UnsupportedOperationException
                    | ClientAuthentificationHttps.ErreurAuthentification e) {
                Dialogue.erreur(getScene().getWindow(), action + " impossible", e.getMessage());
            }
        }
    }

    private record ButtonTypeResultat(String nom, String email, char[] motDePasse,
            Session.Role role, boolean actif) {
    }

    private record ButtonTypeResultatInvitation(String email, Session.Role role) {
    }

    /** Journal réel des échanges avec l'assistant (étape 11 : traçabilité). */
    private static String journalTexte() {
        var echanges = fr.ihm2026.api.service.AideIaApi.journal();
        StringBuilder b = new StringBuilder();
        b.append("Assistance IA — ").append(echanges.size()).append(" échange(s) cette session · ")
         .append(fr.ihm2026.api.service.AideIaApi.appelsDuJour()).append("/")
         .append(fr.ihm2026.api.service.AideIaApi.quota()).append(" appels facturés\n")
         .append("Modèle : ").append(fr.ihm2026.api.service.AideIaApi.identifiantModele())
         .append("\n\n");
        if (echanges.isEmpty()) {
            b.append("Aucun échange avec l'assistant sur cette session.");
        }
        for (var e : echanges) {
            b.append("• ").append(e.question()).append("\n   → ").append(e.motif())
             .append(" · ").append(e.dureeMs()).append(" ms")
             .append(e.citations().isEmpty() ? " · sans citation" : " · cite : " + e.citations().get(0))
             .append("\n");
        }
        b.append("\nAucune note n'est attribuée par l'assistant : les refus sont journalisés comme les réponses.");
        return b.toString();
    }
}
