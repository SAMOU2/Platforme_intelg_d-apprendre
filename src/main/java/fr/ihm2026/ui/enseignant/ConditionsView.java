package fr.ihm2026.ui.enseignant;

import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.EnteteVue;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

/**
 * E1 — créer un niveau et fixer ses conditions de passage.
 * Les trois conditions sont en UNORD : on les règle dans l'ordre voulu, un récapitulatif suit.
 */
public class ConditionsView extends BorderPane {

    private final Spinner<Integer> seuil = new Spinner<>(0, 20, 12);
    private final Spinner<Integer> tentatives = new Spinner<>(1, 10, 3);
    private final DatePicker finPeriode = new DatePicker(LocalDate.now().plusWeeks(6));
    private final CheckBox limiteActive = new CheckBox("Limiter la période de formation");
    private final Label recapitulatif = new Label();
    private final Label autosave = new Label("Brouillon — aucun enregistrement encore");

    public ConditionsView(Router router) {
        getStyleClass().add("vue");
        setTop(new EnteteVue("Créer un niveau et fixer ses conditions",
                "Parcours " + Session.langage().label() + " · les conditions sont enregistrées comme des données, "
                        + "jamais codées en dur (BNF-09)."));

        TextField titre = new TextField("Niveau 4 — Structures de données");
        titre.getStyleClass().add("champ");

        GridPane form = new GridPane();
        form.getStyleClass().addAll("card", "formulaire");
        form.setHgap(14);
        form.setVgap(12);
        form.setPadding(new Insets(16));
        HBox periode = new HBox(10, limiteActive, finPeriode);
        form.addRow(0, etiquette("Titre du niveau"), titre);
        form.addRow(1, etiquette("Seuil de réussite (/20)"), seuil);
        form.addRow(2, etiquette("Nombre de tentatives"), tentatives);
        form.addRow(3, etiquette("Période de formation"), periode);
        form.addRow(4, etiquette("Récapitulatif"), recapitulatif);
        GridPane.setColumnSpan(titre, 2);
        GridPane.setColumnSpan(seuil, 2);
        GridPane.setColumnSpan(tentatives, 2);
        GridPane.setColumnSpan(periode, 2);
        GridPane.setColumnSpan(recapitulatif, 2);

        seuil.valueProperty().addListener((o, a, b) -> rafraichir());
        tentatives.valueProperty().addListener((o, a, b) -> rafraichir());
        limiteActive.selectedProperty().addListener((o, a, b) -> {
            finPeriode.setDisable(!b);
            rafraichir();
        });
        finPeriode.setDisable(true);

        Button publier = new Button("Publier le niveau");
        publier.getStyleClass().add("btn-primary");
        publier.setOnAction(e -> {
            boolean ok = Dialogue.confirmer(getScene() == null ? null : getScene().getWindow(),
                    "Publier le niveau ?",
                    "Le niveau devient visible des apprenants avec ces conditions.\n"
                            + "Un seuil modifié plus tard ne changera pas les validations déjà acquises.",
                    "Publier");
            if (ok) {
                Dialogue.info(getScene().getWindow(), "Niveau publié",
                        "Les conditions ont été enregistrées et le niveau est publié.");
            }
        });

        Button apercu = new Button("Prévisualiser");
        apercu.setOnAction(e -> Dialogue.info(getScene().getWindow(), "Aperçu",
                "Aperçu « vu par l'apprenant » : titre, contenu, test, seuil " + seuil.getValue() + "/20."));

        autosave.getStyleClass().add("autosave");
        HBox barre = new HBox(10, apercu, publier, autosave);
        barre.getStyleClass().add("barre-actions");

        VBox contenu = new VBox(14, form, barre);
        contenu.getStyleClass().add("colonne");
        setCenter(contenu);
        BorderPane.setMargin(contenu, new Insets(4, 0, 0, 0));

        rafraichir();
    }

    private Label etiquette(String texte) {
        Label l = new Label(texte);
        l.getStyleClass().add("etiquette-champ");
        return l;
    }

    private void rafraichir() {
        String periode = limiteActive.isSelected()
                ? "jusqu'au " + (finPeriode.getValue() == null ? "—" : finPeriode.getValue())
                : "sans limite";
        recapitulatif.setText("Réussite à " + seuil.getValue() + "/20 · "
                + tentatives.getValue() + " tentative(s) · " + periode);
        recapitulatif.getStyleClass().add("recapitulatif");
        autosave.setText("Brouillon enregistré à " + LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")));
    }
}
