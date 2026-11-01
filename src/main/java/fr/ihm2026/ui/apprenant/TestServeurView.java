package fr.ihm2026.ui.apprenant;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import fr.ihm2026.core.*;
import fr.ihm2026.ui.commun.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.util.*;
import java.util.function.Supplier;

/** Capture et transmission des réponses brutes, sans calcul de note sur le client. */
final class TestServeurView extends BorderPane {
    private static final ObjectMapper JSON=new ObjectMapper();
    private final Label etat=new Label("Chargement du sujet depuis PC1…");
    private final VBox liste=new VBox(12);
    private final Button soumettre=new Button("Soumettre à mon enseignant");
    private final Button abandonner=new Button("Abandonner cette tentative");
    private final TextArea code=new TextArea();
    private final Map<String,Supplier<JsonNode>> reponses=new LinkedHashMap<>();
    private long copie;
    private boolean occupe;
    TestServeurView(Router router) {
        setTop(new EnteteVue("Test — niveau "+Session.rangNiveauCourant(),
                "Votre travail sera corrigé par votre enseignant. L'IA est bloquée pendant cette tentative."));
        etat.setWrapText(true);liste.getChildren().add(etat);
        ScrollPane scroll=new ScrollPane(liste);scroll.setFitToWidth(true);setCenter(scroll);
        Button retour=new Button("Mes copies");retour.setOnAction(e -> router.go(Router.RESULTAT));
        setBottom(new HBox(10,retour,abandonner,soumettre));soumettre.setDisable(true);abandonner.setDisable(true);
        occupe=true;
        TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("commencer")
                .put("niveau",Session.niveauServeurId()).put("langage",Session.langage().code()).put("rang",Session.rangNiveauCourant())),sujet -> {
            copie=sujet.path("id").asLong();Session.demarrerEvaluation();
            sujet.path("questions").forEach(q -> {
                Label enonce=new Label(q.path("enonce").asText());enonce.setWrapText(true);
                VBox carte=new VBox(6,enonce);carte.getStyleClass().add("card");
                if("QCM".equals(q.path("type").asText())) {
                    ToggleGroup groupe=new ToggleGroup();
                    q.path("propositions").forEach(p -> {
                        RadioButton choix=new RadioButton(p.path("texte").asText());choix.setWrapText(true);
                        choix.setToggleGroup(groupe);choix.setUserData(p.path("id").asLong());carte.getChildren().add(choix);
                    });
                    reponses.put(q.path("id").asText(),() -> groupe.getSelectedToggle()==null?JSON.nullNode():JSON.getNodeFactory().numberNode((Long)groupe.getSelectedToggle().getUserData()));
                } else {
                    TextArea texte=new TextArea();texte.setPrefRowCount(4);carte.getChildren().add(texte);
                    reponses.put(q.path("id").asText(),() -> JSON.getNodeFactory().textNode(texte.getText()));
                }
                liste.getChildren().add(carte);
            });
            code.setPromptText("Code complémentaire à corriger (facultatif, non exécuté)");code.setPrefRowCount(6);
            liste.getChildren().addAll(new Label("Code complémentaire"),code);
            etat.setText("Copie #"+copie+" · Les réponses ne seront enregistrées qu'à la soumission. Quitter cet écran ne termine pas la tentative : reprenez-la ou abandonnez-la explicitement.");
            occupe=false;soumettre.setDisable(false);abandonner.setDisable(false);
        },erreur -> {occupe=false;etat.setText(erreur.getMessage());});
        soumettre.setOnAction(e -> envoyer(router,false));
        abandonner.setOnAction(e -> envoyer(router,true));
    }
    private void envoyer(Router router,boolean abandon) {
        if(occupe || copie==0)return;
        if(!Dialogue.confirmer(getScene().getWindow(),abandon?"Abandonner ?":"Soumettre la copie ?",
                abandon?"Vos réponses non soumises seront perdues.":"Après envoi, vous ne pourrez plus modifier les réponses. Aucune note ne sera calculée automatiquement.",abandon?"Abandonner":"Soumettre"))return;
        ObjectNode d=SuiviPedagogique.demande(abandon?"abandonner":"soumettre").put("id",copie).put("code",code.getText());
        ObjectNode choix=d.putObject("reponses");reponses.forEach((id,lire) -> choix.set(id,lire.get()));
        occupe=true;soumettre.setDisable(true);abandonner.setDisable(true);liste.setDisable(true);
        TaskRunner.run(() -> SuiviPedagogique.appeler(d),ok -> {
            Session.terminerEvaluation();router.go(Router.RESULTAT);
        },erreur -> {occupe=false;soumettre.setDisable(false);abandonner.setDisable(false);liste.setDisable(false);etat.setText(erreur.getMessage());});
    }
}
