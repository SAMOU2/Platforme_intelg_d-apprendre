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
        Button enseignants=new Button("Enseignants et inscription");
        enseignants.setAccessibleText("Ouvrir le catalogue des enseignants disponibles");
        enseignants.setOnAction(e -> router.go(Router.OFFRES));
        Button copies=new Button("Mes copies et décisions");copies.setOnAction(e -> router.go(Router.RESULTAT));
        VBox niveaux=new VBox(10);ScrollPane scroll=new ScrollPane(niveaux);scroll.setFitToWidth(true);
        getChildren().addAll(new EnteteVue("Mon parcours — "+Session.langage().label(),
                "Inscrivez-vous auprès d'un enseignant du catalogue, puis suivez cours → exercices → test dans la même fenêtre. Seule la décision publiée par l'enseignant compte : aucune note automatique."),new HBox(8,actualiser,enseignants,copies),etat,scroll);
        VBox.setVgrow(scroll,Priority.ALWAYS);
        String langage=Session.langage().code();
        ActualisationVue<com.fasterxml.jackson.databind.JsonNode> suivi=new ActualisationVue<>(this,
                () -> SuiviPedagogique.appeler(SuiviPedagogique.demande("niveaux").put("langage",langage)),liste -> {
                niveaux.getChildren().clear();actualiser.setDisable(false);
                // L'accès vient de la demande acceptée par l'enseignant (ou par son bot),
                // jamais d'une affectation décidée par l'administrateur.
                boolean inscrit=false;
                for(com.fasterxml.jackson.databind.JsonNode n:liste)if(n.path("affecte").asBoolean())inscrit=true;
                if(!inscrit) {
                    Label manque=new Label("Vous n'êtes inscrit auprès d'aucun enseignant : le test final reste fermé. Ouvrez le catalogue des enseignants disponibles, cliquez sur « S'inscrire » et saisissez votre nom. La demande est transmise à l'enseignant, qui l'accepte — ou dont le bot l'accepte immédiatement — puis vos cours s'ouvrent.");
                    manque.setWrapText(true);
                    Button choisir=new Button("Voir les enseignants disponibles");
                    choisir.getStyleClass().add("btn-primary");
                    choisir.setOnAction(e -> router.go(Router.OFFRES));
                    VBox bandeau=new VBox(8,manque,choisir);bandeau.getStyleClass().addAll("card","panneau-lateral");
                    niveaux.getChildren().add(bandeau);
                }
                etat.setText(liste.isEmpty()
                        ?"Aucun niveau publié pour ce langage. Inscrivez-vous auprès d'un enseignant du catalogue : ses niveaux publiés apparaîtront ici."
                        :inscrit?"La validation d'un niveau débloque le suivant après publication.":"Inscription requise auprès d'un enseignant pour ouvrir le test final.");
                liste.forEach(n -> {
                    Label titre=new Label("Niveau "+n.path("rang").asInt()+" — "+n.path("titre").asText()+" · "+n.path("etat").asText());titre.setWrapText(true);
                    Label note=new Label("Meilleure note : "+n.path("meilleure_note").asText("—")+"/20 · seuil "+n.path("seuil").asInt());
                    boolean verrouille="VERROUILLE".equals(n.path("etat").asText());
                    Button cours=new Button("1 · Cours et explications");
                    Button exercices=new Button("2 · Entraînement avec IA");
                    Button test=new Button("3 · Test final sans IA");
                    cours.setDisable(verrouille || !n.path("cours_pret").asBoolean());
                    exercices.setDisable(verrouille || !n.path("exercice_pret").asBoolean());
                    test.setDisable(verrouille || !n.path("cours_pret").asBoolean() || !n.path("exercice_pret").asBoolean() || !n.path("test_pret").asBoolean()
                            || !n.path("periode_ouverte").asBoolean() || !n.path("affecte").asBoolean());
                    // Les trois étapes s'ouvrent dans la même fenêtre (atelier du niveau) :
                    // plus de changement d'écran entre le cours, l'exercice et le test.
                    java.util.function.Consumer<Session.EtapeAtelier> ouvrir=etape -> {
                        Session.setNiveauServeurId(n.path("id").asLong());
                        Session.setRangNiveauCourant(n.path("rang").asInt());
                        Session.setEtapeAtelier(etape);
                        router.go(Router.ATELIER);
                    };
                    cours.setOnAction(e -> ouvrir.accept(Session.EtapeAtelier.COURS));
                    exercices.setOnAction(e -> ouvrir.accept(Session.EtapeAtelier.EXERCICES));
                    test.setOnAction(e -> ouvrir.accept(Session.EtapeAtelier.TEST));
                    Label disponibilite=new Label(
                            (n.path("cours_pret").asBoolean()?"Cours disponible":"Cours en préparation")+" · "
                            +(n.path("exercice_pret").asBoolean()?"Entraînement disponible":"Entraînement en préparation")+" · "
                            +(n.path("test_pret").asBoolean()?"Test prêt":"Test en préparation")
                            +(!n.path("periode_ouverte").asBoolean()?" · Hors période":"")
                            +(!n.path("affecte").asBoolean()?" · Inscription à un enseignant requise":""));
                    disponibilite.setWrapText(true);
                    Label suite=new Label("Cours → exercices → test final dans la même fenêtre, puis soumission → correction de l'enseignant → note et décision publiées → niveau suivant si validé.");suite.setWrapText(true);
                    javafx.scene.layout.FlowPane actions=new javafx.scene.layout.FlowPane(8,8,cours,exercices,test);
                    VBox carte=new VBox(8,titre,note,disponibilite,actions,suite);carte.getStyleClass().add("card");niveaux.getChildren().add(carte);
                });
            },erreur -> etat.setText("Connexion interrompue. Nouvelle tentative automatique… " + erreur.getMessage()));
        actualiser.setOnAction(e -> suivi.actualiser());
    }
}
