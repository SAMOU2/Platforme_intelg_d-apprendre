package fr.ihm2026.ui.commun;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.ihm2026.core.*;
import fr.ihm2026.reseau.Reseau;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.collections.FXCollections;
import javafx.beans.property.ReadOnlyStringWrapper;

/** Écrans communs de copies réelles ; jamais de données de démonstration. */
public final class SuiviPedagogique extends VBox {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final TableView<JsonNode> table = new TableView<>();
    private final TextArea detail = new TextArea();
    private final Label etat = new Label();
    private final Button actualiser = new Button("Actualiser");
    private final TextField note = new TextField();
    private final TextArea commentaire = new TextArea();
    private final CheckBox valide = new CheckBox("Valider le niveau (note au moins égale au seuil)");
    private final VBox correction = new VBox(8);
    private final Button enregistrer = new Button("Enregistrer le brouillon");
    private final Button publier = new Button("Publier la note et la décision");
    private boolean charge;
    public static ObjectNode demande(String action) { return JSON.createObjectNode().put("action",action); }
    public static JsonNode appeler(JsonNode d) { return Reseau.sessionAuthentification().pedagogie(d); }
    public SuiviPedagogique(boolean enseignant) {
        super(10);
        getChildren().add(new Label(enseignant ? "Mes apprenants et leurs copies" : "Mes copies et décisions publiées"));
        etat.setWrapText(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        for(String cle:new String[]{"apprenant","enseignant","langage","rang","statut","note"}) {
            TableColumn<JsonNode,String> col=new TableColumn<>(cle);
            col.setCellValueFactory(v -> new ReadOnlyStringWrapper(v.getValue().path(cle).isNull()?"—":v.getValue().path(cle).asText("—")));
            table.getColumns().add(col);
        }
        table.setPlaceholder(new Label("Aucune copie. Cliquez sur Actualiser."));
        table.setPrefHeight(220);
        detail.setEditable(false);detail.setWrapText(true);detail.setPrefRowCount(10);
        note.setPromptText("Note sur 20 (ex. 14.5)");
        commentaire.setPromptText("Correction et commentaire pour l'apprenant (obligatoire)");
        commentaire.setPrefRowCount(4);commentaire.setWrapText(true);
        correction.getChildren().addAll(new Label("Correction humaine"),note,commentaire,valide,new HBox(8,enregistrer,publier));
        correction.setVisible(enseignant);correction.setManaged(enseignant);correction.setDisable(true);
        table.getSelectionModel().selectedItemProperty().addListener((o,a,copie) -> {
            if(copie==null){detail.clear();correction.setDisable(true);return;}
            detail.setText(details(copie));
            correction.setDisable(charge || !"SOUMISE".equals(copie.path("statut").asText()));
            note.setText(copie.path("note_brouillon").isNull()?"":copie.path("note_brouillon").asText(""));
            commentaire.setText(copie.path("commentaire_brouillon").asText(""));
            valide.setSelected(copie.path("decision_brouillon").asBoolean());
        });
        actualiser.setOnAction(e -> charger(enseignant));
        enregistrer.setOnAction(e -> corriger(false,enseignant));
        publier.setOnAction(e -> corriger(true,enseignant));
        getChildren().addAll(actualiser,etat,table,detail,correction);
        VBox.setVgrow(detail,Priority.ALWAYS);
        charger(enseignant);
    }
    private void occupe(boolean oui) {
        charge=oui;actualiser.setDisable(oui);table.setDisable(oui);
        var copie=table.getSelectionModel().getSelectedItem();
        correction.setDisable(oui || copie==null || !"SOUMISE".equals(copie.path("statut").asText()));
    }
    private void charger(boolean enseignant) {
        if(charge)return;occupe(true);etat.setText("Chargement depuis PC1…");
        TaskRunner.run(() -> {
            JsonNode copies=appeler(demande("copies"));
            JsonNode eleves=enseignant?appeler(demande("mes-apprenants")):JSON.createArrayNode();
            return new JsonNode[]{copies,eleves};
        }, resultat -> {
            table.getItems().clear();resultat[0].forEach(table.getItems()::add);
            StringBuilder noms=new StringBuilder();resultat[1].forEach(u -> noms.append(u.path("nom").asText()).append(" · "));
            etat.setText(enseignant ? "Apprenants affectés : " + (noms.isEmpty()?"aucun — demander une affectation à l'administrateur":noms)
                    : "Les notes ne sont visibles qu'après publication par l'enseignant. Les copies en attente ne sont pas notées automatiquement.");
            detail.clear();occupe(false);
        }, erreur -> {occupe(false);etat.setText(erreur.getMessage());});
    }
    private void corriger(boolean publication,boolean enseignant) {
        if(charge)return;
        JsonNode copie=table.getSelectionModel().getSelectedItem();if(copie==null)return;
        java.math.BigDecimal valeur;
        try {valeur=new java.math.BigDecimal(note.getText().strip().replace(',','.'));}
        catch(NumberFormatException ex){etat.setText("Saisissez une note numérique de 0 à 20.");return;}
        if(valeur.signum()<0 || valeur.compareTo(java.math.BigDecimal.valueOf(20))>0 || valeur.scale()>2
                || commentaire.getText().isBlank() || (valide.isSelected() && valeur.doubleValue()<copie.path("seuil").asDouble())) {
            etat.setText("Vérifiez la note (0–20), le commentaire et le seuil pour valider.");return;
        }
        if(publication && !Dialogue.confirmer(getScene().getWindow(),"Publier la correction ?",
                "La note, le commentaire et la décision seront visibles par l'apprenant. Cette publication est définitive.","Publier"))return;
        ObjectNode d=demande(publication?"publier":"corriger").put("id",copie.path("id").asLong()).put("note",valeur)
                .put("commentaire",commentaire.getText()).put("valide",valide.isSelected());
        occupe(true);
        TaskRunner.run(() -> appeler(d), ok -> {
            occupe(false);Notifications.afficher(getScene().getWindow(),Notifications.Type.SUCCES,
                    publication?"Correction publiée.":"Brouillon enregistré (invisible pour l'apprenant).");charger(enseignant);
        },erreur -> {occupe(false);etat.setText(erreur.getMessage());});
    }
    private static String details(JsonNode copie) {
        StringBuilder b=new StringBuilder("Copie #"+copie.path("id").asText()+" — "+copie.path("statut").asText()+"\n");
        try {
            JsonNode sujet=JSON.readTree(copie.path("sujet").asText("[]"));
            JsonNode reponses=JSON.readTree(copie.path("reponses").asText("{}"));
            for(JsonNode q:sujet) {
                b.append("\n").append(q.path("enonce").asText()).append("\nRéponse : ");
                JsonNode choix=reponses.get(q.path("id").asText());
                if(choix==null || choix.isNull())b.append("Non renseignée");
                else if("QCM".equals(q.path("type").asText())) {
                    for(JsonNode p:q.path("propositions")) if(p.path("id").asLong()==choix.asLong())b.append(p.path("texte").asText());
                } else b.append(choix.asText());
                b.append("\n");
            }
        }catch(Exception ex){b.append("\nAucune réponse soumise à afficher.\n");}
        b.append("\nCode :\n").append(copie.path("code_source").asText(""));
        if(!copie.path("publie_le").isNull()) b.append("\n\nNote : ").append(copie.path("note").asText())
                .append("/20\nDécision : ").append(copie.path("valide").asBoolean()?"VALIDÉ":"NON VALIDÉ")
                .append("\nCorrection : ").append(copie.path("commentaire").asText());
        return b.toString();
    }
    public static void preparerTest(javafx.stage.Window owner) {
        TaskRunner.run(() -> appeler(demande("catalogue-tests")), niveaux -> {
            ComboBox<JsonNode> choix=new ComboBox<>();niveaux.forEach(choix.getItems()::add);
            choix.setConverter(new javafx.util.StringConverter<>() {
                public String toString(JsonNode n){return n==null?"":n.path("langage").asText()+" · Niveau "+n.path("rang").asText()+" · "+n.path("titre").asText();}
                public JsonNode fromString(String s){return null;}
            });
            TextArea questions=new TextArea();questions.setPromptText("Une question à réponse libre par ligne (1 à 20). Pas de corrigé ici.");questions.setPrefRowCount(8);questions.setWrapText(true);
            Label aide=new Label("Ce sujet remplacera les questions actives du niveau pour les futures tentatives. Les anciennes copies restent conservées. Correction manuelle par l'enseignant affecté.");aide.setWrapText(true);
            Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Configurer un sujet de test");
            dialog.getDialogPane().setContent(new VBox(10,aide,choix,questions));dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
            Dialogue.appliquerTheme(dialog.getDialogPane(),owner);
            dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(choix.valueProperty().isNull().or(questions.textProperty().isEmpty()));
            dialog.showAndWait().filter(b -> b==ButtonType.OK).ifPresent(b -> {
                ObjectNode d=demande("preparer-test").put("niveau",choix.getValue().path("id").asLong());
                ArrayNode liste=d.putArray("questions");questions.getText().lines().filter(l -> !l.isBlank()).forEach(liste::add);
                TaskRunner.run(() -> appeler(d),ok -> Notifications.afficher(owner,Notifications.Type.SUCCES,"Sujet enregistré pour les prochaines tentatives."),
                        erreur -> Dialogue.erreur(owner,"Sujet non enregistré",erreur.getMessage()));
            });
        },erreur -> Dialogue.erreur(owner,"Catalogue inaccessible",erreur.getMessage()));
    }

    public static void affecter(javafx.stage.Window owner) {
        TaskRunner.run(() -> appeler(demande("affectations")), lignes -> {
            ComboBox<JsonNode> apprenants=new ComboBox<>(),enseignants=new ComboBox<>();
            javafx.util.StringConverter<JsonNode> convertisseur=new javafx.util.StringConverter<>() {
                public String toString(JsonNode n){return n==null?"":n.path("nom").asText()+" (#"+n.path("id").asText()+")";}
                public JsonNode fromString(String s){return null;}
            };
            apprenants.setConverter(convertisseur);enseignants.setConverter(convertisseur);
            lignes.forEach(u -> {if("APPRENANT".equals(u.path("role").asText()))apprenants.getItems().add(u);else enseignants.getItems().add(u);});
            Dialog<ButtonType> dialog=new Dialog<>();dialog.initOwner(owner);dialog.setTitle("Affecter un apprenant à un enseignant");
            Label instruction=new Label("Un enseignant responsable par apprenant. Les copies en attente bloquent une réaffectation.");instruction.setWrapText(true);
            Label responsable=new Label();
            apprenants.setOnAction(e -> {
                var a=apprenants.getValue();if(a==null)return;
                responsable.setText("Enseignant actuel : " + a.path("enseignant_id").asText("aucun"));
            });
            dialog.getDialogPane().setContent(new VBox(10,instruction,new Label("Apprenant"),apprenants,responsable,new Label("Enseignant"),enseignants));
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK,ButtonType.CANCEL);
            Dialogue.appliquerTheme(dialog.getDialogPane(),owner);
            var ok=dialog.getDialogPane().lookupButton(ButtonType.OK);
            ok.disableProperty().bind(apprenants.valueProperty().isNull().or(enseignants.valueProperty().isNull()));
            dialog.showAndWait().filter(b -> b==ButtonType.OK).ifPresent(b -> {
                var d=demande("affecter").put("apprenant",apprenants.getValue().path("id").asLong()).put("enseignant",enseignants.getValue().path("id").asLong());
                TaskRunner.run(() -> appeler(d),r -> Notifications.afficher(owner,Notifications.Type.SUCCES,"Affectation enregistrée. Actualisez le suivi enseignant."),
                        erreur -> Dialogue.erreur(owner,"Affectation impossible",erreur.getMessage()));
            });
        },erreur -> Dialogue.erreur(owner,"Chargement impossible",erreur.getMessage()));
    }
}
