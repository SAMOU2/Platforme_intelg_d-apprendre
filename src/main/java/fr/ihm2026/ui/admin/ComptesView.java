package fr.ihm2026.ui.admin;

import fr.ihm2026.api.service.AdminApi;
import fr.ihm2026.api.service.ClientAuthentificationHttps;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.model.UtilisateurVM;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.EtatVide;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** AD1 — administrer les comptes et les rôles (un seul rôle par compte, confirmation avant suppression). */
public class ComptesView extends BorderPane {

    private static final int TAILLE_PAGE = 50;

    private boolean operationCompteEnCours;
    private final TableView<UtilisateurVM> table =
            new TableView<>(FXCollections.observableArrayList());
    private final Label resumeInscriptions = new Label("Chargement des comptes…");
    private final TextField recherche = new TextField();
    private final TextField domaine = new TextField();
    private final ComboBox<String> filtreStatut = new ComboBox<>();
    private final ComboBox<String> filtreRole = new ComboBox<>();
    private final ComboBox<String> filtreActif = new ComboBox<>();
    private final Label etatPagination = new Label();
    private final Button pagePrecedente = new Button("Page précédente");
    private final Button pageSuivante = new Button("Page suivante");
    private final Button accepterLot = new Button("Accepter la sélection");
    private final Button refuserLot = new Button("Refuser la sélection");
    private final Set<String> emailsCoches = new LinkedHashSet<>();
    private int pageCourante = 1;
    private long totalComptesFiltres;

    public ComptesView() {
        getStyleClass().add("vue");
        setTop(new EnteteVue("Comptes et rôles",
                "Filtrez la file, contrôlez l'avis du bot, puis validez les demandes restantes."));

        // Les colonnes se partagent la largeur : plus de colonne vide en bout de tableau.
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.getStyleClass().add("table-comptes");
        table.setPlaceholder(new EtatVide("Aucun compte enregistré."));

        TableColumn<UtilisateurVM, Boolean> cSelection = new TableColumn<>("Lot");
        cSelection.setPrefWidth(52);
        cSelection.setCellValueFactory(d -> new ReadOnlyBooleanWrapper(
                emailsCoches.contains(d.getValue().getEmail())));
        cSelection.setCellFactory(colonne -> new TableCell<>() {
            private final CheckBox caseCocher = new CheckBox();
            private UtilisateurVM compteCourant;

            {
                caseCocher.setOnAction(e -> {
                    if (compteCourant == null) {
                        return;
                    }
                    if (caseCocher.isSelected()) {
                        emailsCoches.add(compteCourant.getEmail());
                    } else {
                        emailsCoches.remove(compteCourant.getEmail());
                    }
                    actualiserBoutonsLot();
                });
            }

            @Override
            protected void updateItem(Boolean valeur, boolean vide) {
                super.updateItem(valeur, vide);
                compteCourant = vide || getTableRow() == null ? null : getTableRow().getItem();
                if (compteCourant == null) {
                    setGraphic(null);
                    return;
                }
                caseCocher.setSelected(emailsCoches.contains(compteCourant.getEmail()));
                caseCocher.setDisable(!compteCourant.isEnAttente());
                caseCocher.setAccessibleText(
                        "Sélectionner pour traitement par lot la demande de " + compteCourant.getEmail());
                setGraphic(caseCocher);
            }
        });

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

        TableColumn<UtilisateurVM, String> cCreeLe = new TableColumn<>("Créé le");
        cCreeLe.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getCreeLe()));
        cCreeLe.setPrefWidth(130);

        TableColumn<UtilisateurVM, String> cBot = new TableColumn<>("Avis du bot de tri");
        cBot.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getAvisBot()));
        cBot.setPrefWidth(210);
        cBot.setCellFactory(colonne -> new TableCell<>() {
            @Override
            protected void updateItem(String valeur, boolean vide) {
                super.updateItem(valeur, vide);
                if (vide || valeur == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                setText(valeur);
                UtilisateurVM compte = getTableRow() == null ? null : getTableRow().getItem();
                setTooltip(compte == null || compte.getMotifBot().isBlank()
                        ? null
                        : new Tooltip(compte.getMotifBot()));
            }
        });

        table.getColumns().addAll(cSelection, cNom, cEmail, cRole, cEtat, cCreeLe, cBot);
        VBox.setVgrow(table, Priority.ALWAYS);

        Label etiquetteRecherche = new Label("Recherche");
        etiquetteRecherche.setLabelFor(recherche);
        recherche.setPromptText("Nom ou e-mail");
        recherche.getStyleClass().add("champ");
        recherche.setAccessibleText("Rechercher un compte par nom ou e-mail");
        recherche.setOnAction(e -> {
            pageCourante = 1;
            chargerComptes();
        });

        Label etiquetteDomaine = new Label("Domaine");
        etiquetteDomaine.setLabelFor(domaine);
        domaine.setPromptText("ex. univ.dz");
        domaine.getStyleClass().add("champ");
        domaine.setAccessibleText("Filtrer par domaine e-mail, par exemple univ.dz");
        domaine.setOnAction(e -> {
            pageCourante = 1;
            chargerComptes();
        });

        Label etiquetteStatut = new Label("Statut");
        etiquetteStatut.setLabelFor(filtreStatut);
        filtreStatut.getItems().setAll("Tous les statuts", "En attente", "Acceptés", "Refusés");
        filtreStatut.setValue("Tous les statuts");
        filtreStatut.setAccessibleText("Filtrer par statut d'inscription");

        Label etiquetteRole = new Label("Rôle");
        etiquetteRole.setLabelFor(filtreRole);
        filtreRole.getItems().setAll("Tous les rôles", "Apprenant", "Enseignant", "Administrateur");
        filtreRole.setValue("Tous les rôles");
        filtreRole.setAccessibleText("Filtrer par rôle de compte");

        Label etiquetteActif = new Label("État");
        etiquetteActif.setLabelFor(filtreActif);
        filtreActif.getItems().setAll("Tous les états", "Actifs seulement", "Inactifs seulement");
        filtreActif.setValue("Tous les états");
        filtreActif.setAccessibleText("Filtrer par compte actif ou inactif");

        Button filtrer = new Button("Filtrer");
        filtrer.getStyleClass().add("btn-primary");
        filtrer.setOnAction(e -> {
            pageCourante = 1;
            chargerComptes();
        });
        Button reinitialiserFiltres = new Button("Réinitialiser les filtres");
        reinitialiserFiltres.setOnAction(e -> {
            recherche.clear();
            domaine.clear();
            filtreStatut.setValue("Tous les statuts");
            filtreRole.setValue("Tous les rôles");
            filtreActif.setValue("Tous les états");
            pageCourante = 1;
            chargerComptes();
        });
        FlowPane filtres = new FlowPane(8, 8, etiquetteRecherche, recherche,
                etiquetteDomaine, domaine, etiquetteStatut, filtreStatut,
                etiquetteRole, filtreRole, etiquetteActif, filtreActif,
                filtrer, reinitialiserFiltres);
        filtres.getStyleClass().addAll("barre-actions", "barre-filtres");

        etatPagination.getStyleClass().add("texte-secondaire");
        etatPagination.setFocusTraversable(true);
        pagePrecedente.setDisable(true);
        pagePrecedente.setAccessibleText("Page précédente des comptes");
        pagePrecedente.setOnAction(e -> {
            if (pageCourante > 1) {
                pageCourante--;
                chargerComptes();
            }
        });
        pageSuivante.setDisable(true);
        pageSuivante.setAccessibleText("Page suivante des comptes");
        pageSuivante.setOnAction(e -> {
            if (pageCourante < nombrePages(totalComptesFiltres, TAILLE_PAGE)) {
                pageCourante++;
                chargerComptes();
            }
        });
        HBox pagination = new HBox(10, pagePrecedente, etatPagination, pageSuivante);
        pagination.getStyleClass().add("barre-actions");

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
        accepterLot.getStyleClass().add("btn-primary");
        accepterLot.setDisable(true);
        accepterLot.setAccessibleText("Accepter toutes les demandes cochées dans cette page");
        accepterLot.setOnAction(e -> deciderSelection(true));
        refuserLot.setDisable(true);
        refuserLot.setAccessibleText("Refuser toutes les demandes cochées dans cette page");
        refuserLot.setOnAction(e -> deciderSelection(false));
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

        FlowPane barre = new FlowPane(8, 8, creer, genererInvitation, accepter, refuser,
                accepterLot, refuserLot, activer, desactiver, actualiser, audit);
        barre.getStyleClass().add("barre-actions");

        resumeInscriptions.getStyleClass().add("texte-secondaire");
        resumeInscriptions.setFocusTraversable(true);
        Label note = new Label("Les comptes en attente ne peuvent pas se connecter. Le bot ne pré-accepte que des apprenants selon la politique PC1 ; les enseignants et les exceptions restent à valider ici. Toute décision est journalisée.");
        note.getStyleClass().add("texte-secondaire");

        Button affectations = new Button("Affecter un apprenant à un enseignant");
        affectations.setOnAction(e -> fr.ihm2026.ui.commun.SuiviPedagogique.affecter(getScene().getWindow()));
        affectations.setDisable(!fr.ihm2026.reseau.Reseau.sessionCompteInstallee());
        Button preparerTest = new Button("Configurer le sujet d'un test");
        preparerTest.setDisable(!fr.ihm2026.reseau.Reseau.sessionCompteInstallee());
        preparerTest.setOnAction(e -> fr.ihm2026.ui.commun.SuiviPedagogique.preparerTest(getScene().getWindow()));
        Button publierContenus = new Button("Publier le cours et l'entraînement d'un niveau");
        publierContenus.setDisable(!fr.ihm2026.reseau.Reseau.sessionCompteInstallee());
        publierContenus.setOnAction(e -> fr.ihm2026.ui.commun.SuiviPedagogique.publierContenus(getScene().getWindow()));
        VBox contenu = new VBox(12, resumeInscriptions, filtres, table, pagination, barre,
                affectations, publierContenus, preparerTest, note);
        contenu.getStyleClass().add("colonne");
        setCenter(contenu);
        BorderPane.setMargin(contenu, new Insets(4, 0, 0, 0));
        chargerComptes();
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
                emailsCoches.remove(resultat.getEmail());
                if (acceptee) Dialogue.info(getScene().getWindow(), "Demande acceptée",
                        "Le PIN a été envoyé par e-mail. Le compte reste inactif jusqu'à son activation.");
                chargerComptes();
            }, erreur -> {
                operationCompteEnCours = false;
                table.setDisable(false);
                Dialogue.erreur(getScene().getWindow(), "Décision / notification", erreur.getMessage());
            });
        }
    }

    private void actualiserComptes() {
        chargerComptes();
    }

    private void chargerComptes() {
        if (operationCompteEnCours) {
            return;
        }
        operationCompteEnCours = true;
        table.setDisable(true);
        actualiserBoutonsLot();
        AdminApi.FiltreComptes filtre = filtreCourant();
        TaskRunner.run(() -> AdminApi.comptes(filtre), page -> {
            operationCompteEnCours = false;
            table.setDisable(false);
            int pages = nombrePages(page.total(), page.taille());
            if (page.total() > 0 && pageCourante > pages) {
                pageCourante = pages;
                chargerComptes();
                return;
            }
            totalComptesFiltres = page.total();
            pageCourante = page.page();
            emailsCoches.clear();
            table.getItems().setAll(page.comptes());
            actualiserResumeInscriptions(page);
            actualiserPagination(page);
            actualiserBoutonsLot();
        }, erreur -> {
            operationCompteEnCours = false;
            table.setDisable(false);
            actualiserBoutonsLot();
            Dialogue.erreur(fenetre(), "Actualisation impossible", messageErreur(erreur));
        });
    }

    private AdminApi.FiltreComptes filtreCourant() {
        String statut = switch (filtreStatut.getValue() == null ? "" : filtreStatut.getValue()) {
            case "En attente" -> "EN_ATTENTE";
            case "Acceptés" -> "ACCEPTE";
            case "Refusés" -> "REFUSE";
            default -> null;
        };
        Session.Role role = switch (filtreRole.getValue() == null ? "" : filtreRole.getValue()) {
            case "Apprenant" -> Session.Role.APPRENANT;
            case "Enseignant" -> Session.Role.ENSEIGNANT;
            case "Administrateur" -> Session.Role.ADMINISTRATEUR;
            default -> null;
        };
        Boolean actif = switch (filtreActif.getValue() == null ? "" : filtreActif.getValue()) {
            case "Actifs seulement" -> Boolean.TRUE;
            case "Inactifs seulement" -> Boolean.FALSE;
            default -> null;
        };
        return new AdminApi.FiltreComptes(recherche.getText(), role, statut, actif,
                domaine.getText(), pageCourante, TAILLE_PAGE);
    }

    private void deciderSelection(boolean acceptee) {
        if (operationCompteEnCours) {
            return;
        }
        List<String> emails = table.getItems().stream()
                .filter(UtilisateurVM::isEnAttente)
                .filter(compte -> emailsCoches.contains(compte.getEmail()))
                .map(UtilisateurVM::getEmail)
                .toList();
        if (emails.isEmpty()) {
            Dialogue.erreur(fenetre(), "Aucune demande cochée",
                    "Cochez au moins une demande en attente dans la page affichée.");
            return;
        }
        String decision = acceptee ? "Accepter" : "Refuser";
        String question = acceptee
                ? "Accepter " + emails.size() + " demande(s) cochée(s) et envoyer un PIN "
                        + "individuel à chacune ? Vérifiez le rôle et l'avis du bot avant de confirmer."
                : "Refuser définitivement " + emails.size() + " demande(s) cochée(s) ?";
        if (!Dialogue.confirmer(fenetre(), decision + " la sélection ?", question, decision)) {
            return;
        }
        operationCompteEnCours = true;
        table.setDisable(true);
        actualiserBoutonsLot();
        TaskRunner.run(() -> AdminApi.deciderInscriptions(emails, acceptee), resultat -> {
            operationCompteEnCours = false;
            table.setDisable(false);
            StringBuilder message = new StringBuilder();
            message.append(resultat.traites().size()).append(" demande(s) traitée(s).");
            if (!resultat.erreurs().isEmpty()) {
                message.append("\n").append(resultat.erreurs().size())
                        .append(" demande(s) ignorée(s) ou en erreur :")
                        .append("\n- ").append(String.join("\n- ", resultat.erreurs()));
            }
            if (resultat.emailsNonEnvoyes() > 0) {
                message.append("\n").append(resultat.emailsNonEnvoyes())
                        .append(" e-mail(s) n'a/n'ont pas pu être envoyé(s). ")
                        .append("Les décisions restent enregistrées ; renvoyez les PIN si nécessaire.");
            }
            Dialogue.info(fenetre(), "Traitement par lot terminé", message.toString());
            chargerComptes();
        }, erreur -> {
            operationCompteEnCours = false;
            table.setDisable(false);
            Dialogue.erreur(fenetre(), "Traitement par lot impossible", messageErreur(erreur));
            chargerComptes();
        });
    }

    private void actualiserBoutonsLot() {
        long selection = table.getItems().stream()
                .filter(UtilisateurVM::isEnAttente)
                .filter(compte -> emailsCoches.contains(compte.getEmail()))
                .count();
        boolean disponible = selection > 0 && !operationCompteEnCours;
        accepterLot.setDisable(!disponible);
        refuserLot.setDisable(!disponible);
        accepterLot.setText(selection > 0
                ? "Accepter la sélection (" + selection + ")"
                : "Accepter la sélection");
        refuserLot.setText(selection > 0
                ? "Refuser la sélection (" + selection + ")"
                : "Refuser la sélection");
    }

    private void actualiserResumeInscriptions(AdminApi.PageComptes page) {
        resumeInscriptions.setText(page.enAttente() + " demande(s) en attente · "
                + page.acceptes() + " compte(s) accepté(s) · "
                + page.refuses() + " refusé(s) · "
                + page.total() + " résultat(s) filtré(s)");
    }

    private void actualiserPagination(AdminApi.PageComptes page) {
        int pages = nombrePages(page.total(), page.taille());
        etatPagination.setText("Page " + page.page() + "/" + pages
                + " · " + page.comptes().size() + " ligne(s) affichée(s)");
        pagePrecedente.setDisable(operationCompteEnCours || page.page() <= 1);
        pageSuivante.setDisable(operationCompteEnCours || page.page() >= pages);
    }

    private static int nombrePages(long total, int taille) {
        if (total <= 0 || taille <= 0) {
            return 1;
        }
        return (int) Math.min(Integer.MAX_VALUE, (total + taille - 1) / taille);
    }

    private javafx.stage.Window fenetre() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static String messageErreur(Throwable erreur) {
        return erreur == null || erreur.getMessage() == null || erreur.getMessage().isBlank()
                ? "Opération impossible."
                : erreur.getMessage();
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
                AdminApi.creerCompte(resultat.nom(), resultat.email(),
                        resultat.motDePasse(), resultat.role(), resultat.actif());
                chargerComptes();
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
                AdminApi.definirCompteActif(selection.getEmail(), actif);
                chargerComptes();
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
