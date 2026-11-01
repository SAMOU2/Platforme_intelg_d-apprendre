package fr.ihm2026.ui.apprenant;

import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.ResultatVM;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.KpiCard;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/** A3 → résultat : note, seuil, points manquants, cours à revoir (IHM-03, BF-R3, BF-R4). */
public class ResultatView extends BorderPane {

    public ResultatView(Router router) {
        getStyleClass().add("vue");
        if (fr.ihm2026.reseau.Reseau.sessionCompteInstallee()) {
            setTop(new EnteteVue("Mes copies et corrections", "En attente de correction ou décision publiée par votre enseignant."));
            var suivi = new javafx.scene.control.ScrollPane(new fr.ihm2026.ui.commun.SuiviPedagogique(false));
            suivi.setFitToWidth(true); setCenter(suivi);
            Button retour = new Button("Revenir au parcours");retour.setOnAction(e -> router.go(Router.PARCOURS));setBottom(retour);
            return;
        }
        ResultatVM resultat = Session.dernierResultat();

        if (resultat == null) {
            setTop(new EnteteVue("Aucun résultat", "Passez un test pour voir votre résultat ici."));
            Button retour = new Button("Revenir au parcours");
            retour.getStyleClass().add("btn-primary");
            retour.setOnAction(e -> router.go(Router.PARCOURS));
            setCenter(retour);
            return;
        }

        String titre = resultat.isReussi() ? "Niveau validé" : "Niveau non validé";
        String sousTitre = resultat.isReussi()
                ? "Le niveau suivant est débloqué."
                : "Il manque " + resultat.getEcart() + " point(s) pour atteindre le seuil.";
        setTop(new EnteteVue(titre, sousTitre));

        HBox kpis = new HBox(12,
                new KpiCard(resultat.getNote() + "/20", "note obtenue",
                        resultat.isReussi() ? "kpi-succes" : "kpi-echec"),
                new KpiCard(String.valueOf(resultat.getSeuil()), "seuil de validation", null),
                new KpiCard(String.valueOf(resultat.getTentativesRestantes()), "tentative(s) restante(s)",
                        "kpi-accent"));

        Label titreManquants = new Label("Points manquants");
        titreManquants.getStyleClass().add("titre-bloc");
        VBox manquants = new VBox(4);
        if (resultat.getPointsManquants().isEmpty()) {
            manquants.getChildren().add(label("Aucun point manquant : toutes les conditions sont remplies."));
        } else {
            resultat.getPointsManquants().forEach(p -> manquants.getChildren().add(label("• " + p)));
        }

        Label titreRevoir = new Label("Cours et exercices à revoir");
        titreRevoir.getStyleClass().add("titre-bloc");
        VBox aRevoir = new VBox(4);
        if (resultat.getCoursARevoir().isEmpty()) {
            aRevoir.getChildren().add(label("Rien à revoir : continuez au niveau suivant."));
        } else {
            resultat.getCoursARevoir().forEach(c -> aRevoir.getChildren().add(label("→ " + c)));
        }

        Button revoir = new Button("Revoir le cours");
        revoir.setOnAction(e -> router.go(Router.COURS));
        Button refaire = new Button("Refaire l'exercice");
        refaire.setOnAction(e -> router.go(Router.EXERCICE));
        Button suivant = new Button("Voir mon parcours");
        suivant.getStyleClass().add("btn-primary");
        suivant.setOnAction(e -> router.go(Router.PARCOURS));

        HBox actions = new HBox(10, revoir, refaire, suivant);
        actions.getStyleClass().add("barre-actions");

        VBox contenu = new VBox(12, kpis, titreManquants, manquants, titreRevoir, aRevoir, actions);
        contenu.getStyleClass().add("colonne");
        setCenter(contenu);
        BorderPane.setMargin(contenu, new Insets(4, 0, 0, 0));
    }

    private Label label(String texte) {
        Label l = new Label(texte);
        l.setWrapText(true);
        l.getStyleClass().add("texte-secondaire");
        return l;
    }
}
