package fr.ihm2026.ui.enseignant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.ui.commun.ActualisationVue;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.EtatVide;
import fr.ihm2026.ui.commun.Notifications;
import fr.ihm2026.ui.commun.SuiviPedagogique;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/**
 * E0 — les demandes d'inscription reçues par l'enseignant.
 *
 * <p>L'apprenant choisit lui-même son enseignant dans le catalogue de son interface, clique sur
 * « S'inscrire » et saisit son nom. La demande arrive ici : l'enseignant l'accepte ou la refuse.
 * L'acceptation ouvre immédiatement l'accès aux cours, exercices et tests publiés ; le refus est
 * motivé et reste visible par l'apprenant.</p>
 *
 * <p>Si l'enseignant a activé le bot sur son offre, la demande est déjà acceptée à son arrivée :
 * elle apparaît ici avec le décideur « BOT », pour contrôle, sans qu'aucune décision pédagogique
 * (note, validation) ait été prise par le bot.</p>
 *
 * <p>La liste se relit toute seule toutes les 5 secondes : une nouvelle demande apparaît sans
 * action de l'enseignant.</p>
 */
final class DemandesInscriptionView extends VBox {

    private final TableView<JsonNode> table = new TableView<>();
    private final Label etat = new Label("Chargement des demandes depuis PC1…");
    private final Button accepter = new Button("Accepter la demande");
    private final Button refuser = new Button("Refuser la demande");
    private final Button actualiser = new Button("Actualiser");
    private ActualisationVue<JsonNode> suivi;
    private boolean occupe;

    DemandesInscriptionView() {
        super(10);
        Label titre = new Label("Demandes d'inscription à mes formations");
        titre.getStyleClass().add("titre-bloc");
        Label aide = new Label("Un apprenant s'inscrit lui-même depuis son interface : il saisit son "
                + "nom, la demande arrive ici. Accepter ouvre son accès aux cours, exercices et "
                + "tests que j'ai publiés. Le bot de mon offre peut accepter à ma place si je l'ai "
                + "activé ; ses décisions sont marquées « BOT » et restent contrôlables ici.");
        aide.setWrapText(true);
        aide.getStyleClass().add("texte-secondaire");

        etat.setWrapText(true);
        etat.setFocusTraversable(true);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new EtatVide("Aucune demande reçue pour le moment."));
        table.setPrefHeight(220);
        for (String[] colonne : new String[][] {
                {"nom_demandeur", "Nom saisi"},
                {"apprenant", "Compte apprenant"},
                {"langage", "Langage"},
                {"message", "Message"},
                {"statut", "Statut"},
                {"decide_par", "Décidé par"},
                {"cree_le", "Reçue le"}}) {
            TableColumn<JsonNode, String> c = new TableColumn<>(colonne[1]);
            c.setCellValueFactory(v -> new ReadOnlyStringWrapper(lire(v.getValue(), colonne[0])));
            table.getColumns().add(c);
        }
        table.getSelectionModel().selectedItemProperty().addListener((o, avant, demande) -> {
            boolean enAttente = demande != null && "EN_ATTENTE".equals(demande.path("statut").asText(""));
            accepter.setDisable(!enAttente || occupe);
            refuser.setDisable(!enAttente || occupe);
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        accepter.getStyleClass().add("btn-primary");
        accepter.setDisable(true);
        accepter.setAccessibleText("Accepter la demande d'inscription sélectionnée");
        accepter.setOnAction(e -> decider(true));
        refuser.setDisable(true);
        refuser.setAccessibleText("Refuser la demande d'inscription sélectionnée");
        refuser.setOnAction(e -> decider(false));
        actualiser.setOnAction(e -> {
            if (suivi != null) {
                suivi.actualiser();
            }
        });
        FlowPane actions = new FlowPane(8, 8, accepter, refuser, actualiser);

        getChildren().addAll(titre, aide, etat, table, actions);

        suivi = new ActualisationVue<>(this,
                () -> SuiviPedagogique.appeler(SuiviPedagogique.demande("demandes-recues")),
                this::afficher,
                erreur -> {
                    occupe = false;
                    actualiser.setDisable(false);
                    etat.setText("Demandes indisponibles : " + erreur.getMessage()
                            + " Nouvelle tentative automatique…");
                });
    }

    private void afficher(JsonNode demandes) {
        occupe = false;
        actualiser.setDisable(false);
        JsonNode selection = table.getSelectionModel().getSelectedItem();
        long selectionId = selection == null ? 0 : selection.path("id").asLong();
        table.getItems().clear();
        if (demandes != null) {
            demandes.forEach(table.getItems()::add);
        }
        int attente = 0;
        int inscrits = 0;
        for (JsonNode demande : table.getItems()) {
            if ("EN_ATTENTE".equals(demande.path("statut").asText(""))) {
                attente++;
            }
            if (demande.path("inscrit").asBoolean()) {
                inscrits++;
            }
            if (selectionId != 0 && demande.path("id").asLong() == selectionId) {
                table.getSelectionModel().select(demande);
            }
        }
        JsonNode selectionnee = table.getSelectionModel().getSelectedItem();
        boolean decisionPossible = selectionnee != null
                && "EN_ATTENTE".equals(selectionnee.path("statut").asText(""));
        accepter.setDisable(!decisionPossible || attente == 0 || occupe);
        refuser.setDisable(accepter.isDisable());
        etat.setText(table.getItems().isEmpty()
                ? "Aucune demande reçue. Les apprenants s'inscrivent depuis leur interface ; "
                        + "cette liste se relit toute seule."
                : attente + " demande(s) en attente de votre décision · " + inscrits
                        + " apprenant(s) avec accès ouvert · liste relue toutes les 5 secondes.");
    }

    private void decider(boolean acceptee) {
        if (occupe) {
            return;
        }
        JsonNode demande = table.getSelectionModel().getSelectedItem();
        if (demande == null || !"EN_ATTENTE".equals(demande.path("statut").asText(""))) {
            Dialogue.erreur(getScene() == null ? null : getScene().getWindow(),
                    "Aucune demande en attente", "Sélectionnez une demande en attente de décision.");
            return;
        }
        String nom = demande.path("nom_demandeur").asText("cet apprenant");
        long identifiant = demande.path("id").asLong();
        if (acceptee) {
            if (!Dialogue.confirmer(getScene().getWindow(), "Accepter cette demande ?",
                    "« " + nom + " » recevra l'accès à vos cours, exercices et tests publiés, "
                            + "et ses copies vous seront transmises pour correction.", "Accepter")) {
                return;
            }
            envoyer(identifiant, true, "");
            return;
        }
        TextArea motif = new TextArea();
        motif.setPromptText("Motif du refus, visible par l'apprenant (facultatif, 600 caractères maximum)");
        motif.setWrapText(true);
        motif.setPrefRowCount(3);
        Dialog<ButtonType> dialogue = new Dialog<>();
        dialogue.initOwner(getScene().getWindow());
        dialogue.setTitle("Refuser cette demande");
        dialogue.setHeaderText("Refus de la demande de « " + nom + " »");
        dialogue.getDialogPane().setContent(new VBox(10, new Label("Motif transmis à l'apprenant"), motif));
        dialogue.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button confirmer = dialogue.getDialogPane().lookupButton(ButtonType.OK);
        confirmer.setText("Refuser la demande");
        Dialogue.appliquerTheme(dialogue.getDialogPane(), getScene().getWindow());
        dialogue.showAndWait().filter(b -> b == ButtonType.OK)
                .ifPresent(b -> envoyer(identifiant, false, motif.getText().strip()));
    }

    private void envoyer(long demande, boolean acceptee, String motif) {
        occupe = true;
        accepter.setDisable(true);
        refuser.setDisable(true);
        actualiser.setDisable(true);
        etat.setText(acceptee ? "Ouverture de l'accès aux cours…" : "Enregistrement du refus…");
        ObjectNode requete = SuiviPedagogique.demande("decider-demande")
                .put("demande", demande).put("accepter", acceptee).put("motif", motif);
        TaskRunner.run(() -> SuiviPedagogique.appeler(requete), resultat -> {
            Notifications.afficher(getScene() == null ? null : getScene().getWindow(),
                    Notifications.Type.SUCCES, acceptee
                            ? "Demande acceptée : l'apprenant accède à vos cours publiés."
                            : "Demande refusée : l'apprenant en est informé.");
            if (suivi != null) {
                suivi.actualiser();
            }
        }, erreur -> {
            occupe = false;
            actualiser.setDisable(false);
            etat.setText("Décision impossible : " + erreur.getMessage());
        });
    }

    private static String lire(JsonNode demande, String champ) {
        if (demande == null) {
            return "—";
        }
        JsonNode valeur = demande.path(champ);
        if (valeur.isNull() || valeur.isMissingNode()) {
            return "—";
        }
        String texte = valeur.asText("");
        if (texte.isBlank()) {
            return "—";
        }
        if ("statut".equals(champ)) {
            return switch (texte) {
                case "EN_ATTENTE" -> "En attente";
                case "ACCEPTEE" -> "Acceptée";
                case "REFUSEE" -> "Refusée";
                case "ANNULEE" -> "Retirée";
                default -> texte;
            };
        }
        if ("decide_par".equals(champ)) {
            return switch (texte) {
                case "BOT" -> "Bot de l'offre";
                case "ENSEIGNANT" -> "Enseignant";
                case "APPRENANT" -> "Apprenant";
                default -> texte;
            };
        }
        return texte;
    }
}
