package fr.ihm2026.ui.apprenant;

import fr.ihm2026.core.*;
import fr.ihm2026.ui.commun.*;
import fr.ihm2026.ui.ia.PanneauAssistant;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Contenus publiés du niveau sélectionné ; aucun cours de démonstration en mode connecté. */
final class ContenuNiveauView extends BorderPane {
    ContenuNiveauView(Router router,boolean entrainement) {
        Label etat=new Label();etat.setWrapText(true);setCenter(new Label("Chargement du niveau…"));
        Button parcours=new Button("Revenir au parcours");parcours.setOnAction(e -> router.go(Router.PARCOURS));
        Button suivant=new Button(entrainement?"Passer au test final — sans IA":"Continuer vers l'entraînement avec IA");
        suivant.setDisable(true);suivant.setOnAction(e -> router.go(entrainement?Router.TEST:Router.EXERCICE));
        setBottom(new VBox(8,etat,new HBox(8,parcours,suivant)));
        TextArea code=new TextArea();code.setPromptText("Votre brouillon d'entraînement (non sauvegardé)");code.setPrefRowCount(12);
        boolean[] modifie={false},remplissage={false};
        code.textProperty().addListener((o,a,b) -> {if(!remplissage[0])modifie[0]=true;});
        PanneauAssistant[] assistant={null};
        long niveau=Session.niveauServeurId();String langage=Session.langage().code();
        new ActualisationVue<com.fasterxml.jackson.databind.JsonNode>(this, () -> {
            var niveaux=SuiviPedagogique.appeler(SuiviPedagogique.demande("niveaux").put("langage",langage));
            boolean accessible=false;
            for(var n:niveaux)if(n.path("id").asLong()==niveau && !"VERROUILLE".equals(n.path("etat").asText()))accessible=true;
            if(!accessible)return SuiviPedagogique.demande("niveau-obsolete");
            return SuiviPedagogique.appeler(SuiviPedagogique.demande("contenus").put("niveau",niveau).put("langage",langage));
        },contenu -> {
            if("niveau-obsolete".equals(contenu.path("action").asText())) {
                suivant.setDisable(true);setRight(null);
                if(modifie[0]) {
                    etat.setText("Votre parcours a changé. Votre brouillon reste ici : copiez-le avant de revenir au parcours à jour.");
                } else {
                    Session.setNiveauServeurId(0);router.go(Router.PARCOURS);
                }
                return;
            }
            var n=contenu.path("niveau");
            etat.setText("");
            setTop(new EnteteVue("Niveau "+n.path("rang").asInt()+" — "+n.path("titre").asText(),
                    entrainement?"Entraînement libre avec aide IA. Votre travail ici n'est pas une copie d'examen.":"Cours et explications publiés par l'équipe pédagogique."));
            VBox corps=new VBox(12);
            if(!modifie[0]) {
                remplissage[0]=true;
                code.setText(contenu.path("exercices").path(0).path("code_depart").asText(""));
                remplissage[0]=false;
            }
            if(entrainement) {
                if(contenu.path("exercices").isEmpty())corps.getChildren().add(new Label("Entraînement en préparation."));
                contenu.path("exercices").forEach(e -> {
                    Label enonce=new Label(e.path("enonce").asText());enonce.setWrapText(true);corps.getChildren().add(enonce);
                });
                if(!contenu.path("exercices").isEmpty() || modifie[0]) {
                    corps.getChildren().addAll(code,new Label("Brouillon local : aucune exécution automatique ni note. Ne collez aucune donnée privée."));
                    if(assistant[0]==null)assistant[0]=new PanneauAssistant(code::getText);
                    setRight(assistant[0]);
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
                if(!contenu.path("blocs").isEmpty()) {
                    if(assistant[0]==null)assistant[0]=new PanneauAssistant();
                    setRight(assistant[0]);
                }
            }
            ScrollPane scroll=new ScrollPane(corps);scroll.setFitToWidth(true);setCenter(scroll);
            boolean disponible=entrainement?n.path("cours_pret").asBoolean() && n.path("exercice_pret").asBoolean() && n.path("test_pret").asBoolean() && n.path("periode_ouverte").asBoolean() && n.path("affecte").asBoolean():n.path("exercice_pret").asBoolean();
            suivant.setDisable(!disponible);
            etat.setText(disponible?"La note et la décision seront publiées par l'enseignant après soumission.":"Étape suivante indisponible. Vérification automatique des publications toutes les 5 secondes.");
        },erreur -> {
            suivant.setDisable(true);
            etat.setText("Connexion interrompue. Nouvelle tentative automatique… " + erreur.getMessage());
        });
    }
}
