package fr.ihm2026.ui.apprenant;

import fr.ihm2026.core.*;
import fr.ihm2026.ui.commun.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** La progression affichée vient de la décision publiée, jamais du calcul local. */
final class ParcoursServeurView extends VBox {
    ParcoursServeurView(Router router) {
        super(12);
        Label etat=new Label("Chargement des niveaux publiés sur PC1…");etat.setWrapText(true);
        Button actualiser=new Button("Actualiser le parcours");
        Button copies=new Button("Mes copies et décisions");copies.setOnAction(e -> router.go(Router.RESULTAT));
        VBox niveaux=new VBox(10);ScrollPane scroll=new ScrollPane(niveaux);scroll.setFitToWidth(true);
        getChildren().addAll(new EnteteVue("Mon parcours — "+Session.langage().label(),
                "L'enseignant corrige vos réponses et publie la décision. Aucune note automatique."),new HBox(8,actualiser,copies),etat,scroll);
        VBox.setVgrow(scroll,Priority.ALWAYS);
        Runnable charger=() -> {
            actualiser.setDisable(true);
            TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("niveaux").put("langage",Session.langage().code())),liste -> {
                niveaux.getChildren().clear();actualiser.setDisable(false);
                etat.setText(liste.isEmpty()?"Aucun niveau publié pour ce langage. Contactez votre enseignant/administrateur.":"La validation d'un niveau débloque le suivant après publication.");
                liste.forEach(n -> {
                    Label titre=new Label("Niveau "+n.path("rang").asInt()+" — "+n.path("titre").asText()+" · "+n.path("etat").asText());titre.setWrapText(true);
                    Label note=new Label("Meilleure note : "+n.path("meilleure_note").asText("—")+" /20 · seuil "+n.path("seuil").asInt());
                    Button test=new Button("Passer / reprendre le test");
                    test.setDisable("VERROUILLE".equals(n.path("etat").asText()));
                    test.setOnAction(e -> {Session.setNiveauServeurId(n.path("id").asLong());Session.setRangNiveauCourant(n.path("rang").asInt());router.go(Router.TEST);});
                    VBox carte=new VBox(8,titre,note,test);carte.getStyleClass().add("card");niveaux.getChildren().add(carte);
                });
            },erreur -> {actualiser.setDisable(false);etat.setText(erreur.getMessage());});
        };
        actualiser.setOnAction(e -> charger.run());charger.run();
    }
}
