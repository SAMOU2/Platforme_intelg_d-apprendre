package fr.ihm2026.ui.apprenant;

import fr.ihm2026.core.*;
import fr.ihm2026.ui.commun.*;
import fr.ihm2026.ui.ia.PanneauAssistant;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Contenus publiés du niveau sélectionné ; aucun cours de démonstration en mode connecté. */
final class ContenuNiveauView extends BorderPane {
    ContenuNiveauView(Router router,boolean entrainement) {
        Label etat=new Label("Chargement du niveau…");etat.setWrapText(true);setCenter(etat);
        Button parcours=new Button("Revenir au parcours");parcours.setOnAction(e -> router.go(Router.PARCOURS));setBottom(parcours);
        long niveau=Session.niveauServeurId();String langage=Session.langage().code();
        if(niveau<1){etat.setText("Sélectionnez d'abord un niveau dans Parcours.");return;}
        TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("contenus").put("niveau",niveau).put("langage",langage)),contenu -> {
            var n=contenu.path("niveau");
            setTop(new EnteteVue("Niveau "+n.path("rang").asInt()+" — "+n.path("titre").asText(),
                    entrainement?"Entraînement libre avec aide IA. Votre travail ici n'est pas une copie d'examen.":"Cours et explications publiés par l'équipe pédagogique."));
            VBox corps=new VBox(12);TextArea code=new TextArea();code.setPromptText("Votre brouillon d'entraînement (non sauvegardé)");code.setPrefRowCount(12);
            if(entrainement) {
                if(contenu.path("exercices").isEmpty())corps.getChildren().add(new Label("Entraînement en préparation."));
                contenu.path("exercices").forEach(e -> {
                    Label enonce=new Label(e.path("enonce").asText());enonce.setWrapText(true);corps.getChildren().add(enonce);
                    if(code.getText().isEmpty())code.setText(e.path("code_depart").asText(""));
                });
                if(!contenu.path("exercices").isEmpty()) {
                    corps.getChildren().addAll(code,new Label("Brouillon local : aucune exécution automatique ni note. Ne collez aucune donnée privée."));
                    setRight(new PanneauAssistant(code::getText));
                }
            } else {
                if(contenu.path("blocs").isEmpty())corps.getChildren().add(new Label("Cours en préparation."));
                contenu.path("blocs").forEach(b -> {
                    if("CODE".equals(b.path("type").asText())) {
                        TextArea exemple=new TextArea(b.path("contenu").asText());exemple.setEditable(false);exemple.setPrefRowCount(5);corps.getChildren().add(exemple);
                    } else {
                        Label texte=new Label(b.path("contenu").asText());texte.setWrapText(true);corps.getChildren().add(texte);
                    }
                });
                if(!contenu.path("blocs").isEmpty())setRight(new PanneauAssistant());
            }
            ScrollPane scroll=new ScrollPane(corps);scroll.setFitToWidth(true);setCenter(scroll);
            Button suivant=new Button(entrainement?"Passer au test final — sans IA":"Continuer vers l'entraînement avec IA");
            boolean disponible=entrainement?n.path("cours_pret").asBoolean() && n.path("exercice_pret").asBoolean() && n.path("test_pret").asBoolean() && n.path("periode_ouverte").asBoolean() && n.path("affecte").asBoolean():n.path("exercice_pret").asBoolean();
            suivant.setDisable(!disponible);
            suivant.setOnAction(e -> router.go(entrainement?Router.TEST:Router.EXERCICE));
            Label indication=new Label(disponible?"La note et la décision seront publiées par l'enseignant après soumission.":"Étape suivante indisponible : revenez au parcours pour consulter les prérequis.");indication.setWrapText(true);
            setBottom(new VBox(8,indication,new HBox(8,parcours,suivant)));
        },erreur -> etat.setText(erreur.getMessage()));
    }
}
