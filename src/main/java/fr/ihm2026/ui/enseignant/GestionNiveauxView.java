package fr.ihm2026.ui.enseignant;

import com.fasterxml.jackson.databind.JsonNode;
import fr.ihm2026.core.*;
import fr.ihm2026.ui.commun.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Gestion connectée des niveaux réels de l'enseignant, sans accès aux contenus des collègues. */
final class GestionNiveauxView extends VBox {
    private final ListView<JsonNode> niveaux=new ListView<>();
    private final Label etat=new Label();
    private final Button actualiser=new Button("Actualiser");
    GestionNiveauxView() {
        super(12);
        getChildren().add(new EnteteVue("Mes niveaux et contenus pédagogiques",
                "Créer → publier cours et entraînement → préparer le test → publier le niveau pour mes apprenants."));
        etat.setWrapText(true);
        niveaux.setCellFactory(v -> new ListCell<>() {
            @Override protected void updateItem(JsonNode n,boolean vide){super.updateItem(n,vide);
                setText(vide || n==null?null:n.path("langage").asText()+" · Niveau "+n.path("rang").asInt()+" — "+n.path("titre").asText()+" · "+n.path("statut").asText());}
        });
        Button creer=new Button("1 · Créer un niveau");creer.setOnAction(e -> creer());
        Button contenus=new Button("2 · Publier cours et entraînement");
        contenus.setOnAction(e -> SuiviPedagogique.publierContenus(getScene().getWindow()));
        Button test=new Button("3 · Préparer le test final");test.setOnAction(e -> SuiviPedagogique.preparerTest(getScene().getWindow()));
        Button publier=new Button("4 · Publier le niveau sélectionné");
        publier.disableProperty().bind(niveaux.getSelectionModel().selectedItemProperty().isNull());
        publier.setOnAction(e -> {
            JsonNode choix=niveaux.getSelectionModel().getSelectedItem();
            Alert confirmation=new Alert(Alert.AlertType.CONFIRMATION,
                    "Ce niveau sera visible par vos apprenants affectés. À la première publication de ce langage, votre parcours remplacera les niveaux historiques dans leur parcours actif. Les anciennes notes/copies restent conservées, mais ne valident pas vos nouveaux niveaux. Continuer ?",
                    ButtonType.OK,ButtonType.CANCEL);
            confirmation.initOwner(getScene().getWindow());Dialogue.appliquerTheme(confirmation.getDialogPane(),getScene().getWindow());
            confirmation.showAndWait().filter(b -> b==ButtonType.OK).ifPresent(b -> {
                publier.disableProperty().unbind();publier.setDisable(true);
                TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("publier-niveau").put("niveau",choix.path("id").asLong())),
                    ok -> {publier.disableProperty().unbind();publier.disableProperty().bind(niveaux.getSelectionModel().selectedItemProperty().isNull());charger();},
                    erreur -> {publier.disableProperty().unbind();publier.disableProperty().bind(niveaux.getSelectionModel().selectedItemProperty().isNull());etat.setText(erreur.getMessage());});
            });
        });
        actualiser.setOnAction(e -> charger());
        FlowPane actions=new FlowPane(8,8,creer,contenus,test,publier,actualiser);
        getChildren().addAll(actions,etat,niveaux);VBox.setVgrow(niveaux,Priority.ALWAYS);charger();
    }
    private void charger(){
        actualiser.setDisable(true);
        TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("catalogue-tests")),liste -> {
            niveaux.getItems().clear();liste.forEach(niveaux.getItems()::add);actualiser.setDisable(false);
            etat.setText(liste.isEmpty()?"Vous n'avez pas encore de niveau. Créez votre niveau 1 dans le langage souhaité.":"Les brouillons restent invisibles aux apprenants. Les éditeurs de contenu permettent de choisir parmi vos niveaux.");
        },erreur -> {actualiser.setDisable(false);etat.setText(erreur.getMessage());});
    }
    private void creer(){
        Dialog<ButtonType> dialogue=new Dialog<>();dialogue.initOwner(getScene().getWindow());dialogue.setTitle("Créer un niveau réel");
        ComboBox<String> langue=new ComboBox<>();langue.getItems().addAll("python","java","cpp");langue.setValue(Session.langage().code());
        TextField titre=new TextField();titre.setPromptText("Exemple : Variables et types");
        Spinner<Integer> seuil=new Spinner<>(0,20,12),tentatives=new Spinner<>(1,10,3);
        Label aide=new Label("Le rang suivant est attribué automatiquement dans votre parcours de ce langage. Le niveau reste en brouillon jusqu'à publication explicite.");aide.setWrapText(true);aide.setMaxWidth(460);
        dialogue.getDialogPane().setContent(new VBox(10,aide,new Label("Langage"),langue,new Label("Titre"),titre,new Label("Seuil /20"),seuil,new Label("Tentatives maximum"),tentatives));
        dialogue.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
        dialogue.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(titre.textProperty().isEmpty());
        Dialogue.appliquerTheme(dialogue.getDialogPane(),getScene().getWindow());
        dialogue.showAndWait().filter(b -> b==ButtonType.OK).ifPresent(b -> {
            var d=SuiviPedagogique.demande("creer-niveau").put("langage",langue.getValue()).put("titre",titre.getText()).put("seuil",seuil.getValue()).put("tentatives",tentatives.getValue());
            TaskRunner.run(() -> SuiviPedagogique.appeler(d),ok -> charger(),erreur -> etat.setText(erreur.getMessage()));
        });
    }
}
