package fr.ihm2026.ui.enseignant;

import fr.ihm2026.api.service.AdminApi;
import fr.ihm2026.core.Session;
import fr.ihm2026.model.UtilisateurVM;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.EtatVide;
import fr.ihm2026.api.ia.SyntheseIa;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.KpiCard;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** E2 — suivre les résultats d'un niveau : indicateurs + liste des apprenants (BF-S5). */
public class ResultatsView extends BorderPane {

    public ResultatsView() {
        getStyleClass().add("vue");
        setTop(new EnteteVue("Résultats du niveau 3 — " + Session.langage().label(),
                "Les apprenants en difficulté sont mis en évidence : ce sont eux qui ont besoin d'aide."));

        var lignes = AdminApi.resultats(Session.langage());
        double moyenne = lignes.stream().mapToInt(UtilisateurVM::getNote).average().orElse(0);
        long valides = lignes.stream().filter(u -> u.getNote() >= 12).count();

        HBox kpis = new HBox(12,
                new KpiCard(String.format("%.1f", moyenne), "note moyenne", null),
                new KpiCard(String.valueOf(valides), "apprenants validés", "kpi-succes"),
                new KpiCard(String.valueOf(lignes.size() - valides), "en difficulté", "kpi-echec"));

        TableView<UtilisateurVM> table = new TableView<>(FXCollections.observableArrayList(lignes));
        table.getStyleClass().add("table-resultats");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        table.setPlaceholder(new EtatVide("Aucun apprenant inscrit à ce niveau."));

        TableColumn<UtilisateurVM, String> cNom = new TableColumn<>("Apprenant");
        cNom.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getNom()));
        cNom.setPrefWidth(220);

        TableColumn<UtilisateurVM, String> cEtat = new TableColumn<>("État");
        cEtat.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getRole()));
        cEtat.setPrefWidth(120);

        TableColumn<UtilisateurVM, String> cNote = new TableColumn<>("Note");
        cNote.setCellValueFactory(d -> new ReadOnlyStringWrapper(d.getValue().getNote() + "/20"));
        cNote.setPrefWidth(90);

        TableColumn<UtilisateurVM, String> cTentatives = new TableColumn<>("Tentatives");
        cTentatives.setCellValueFactory(d -> new ReadOnlyStringWrapper(String.valueOf(d.getValue().getTentatives())));
        cTentatives.setPrefWidth(110);

        table.getColumns().addAll(cNom, cEtat, cNote, cTentatives);
        table.setRowFactory(tv -> new TableRow<>() {
            @Override
            protected void updateItem(UtilisateurVM item, boolean empty) {
                super.updateItem(item, empty);
                getStyleClass().removeAll("ligne-difficulte", "ligne-valide");
                if (!empty && item != null) {
                    getStyleClass().add(item.isEnDifficulte() ? "ligne-difficulte" : "ligne-valide");
                }
            }
        });
        VBox.setVgrow(table, Priority.ALWAYS);

        Label legende = new Label("Seuil du niveau : 12/20 · les lignes orangées signalent une note inférieure à 10/20.");
        legende.getStyleClass().add("texte-secondaire");

        // ---- positionnement B : l'agent prépare un brouillon, l'enseignant décide
        Label titreSynthese = new Label("Synthèse assistée (brouillon)");
        titreSynthese.getStyleClass().add("titre-bloc");

        TextArea synthese = new TextArea();
        synthese.setEditable(false);
        synthese.setWrapText(true);
        synthese.setPrefRowCount(5);
        synthese.setPromptText("Aucune synthèse générée. Cliquez sur « Générer une synthèse ».");

        Label statut = new Label("Statut : —");
        statut.getStyleClass().add("mention-ia");

        Button generer = new Button("Générer une synthèse");
        generer.getStyleClass().add("btn-secondaire");
        Button publier = new Button("Publier aux apprenants");
        publier.getStyleClass().add("btn-primary");
        publier.setDisable(true);

        generer.setOnAction(e -> {
            SyntheseIa.Brouillon b = SyntheseIa.synthetiser(lignes, 12);
            synthese.setText(b.corps());
            if (b.estRefus()) {
                // Refus motivé : pas assez de résultats pour une synthèse de groupe.
                statut.setText("Refus : " + b.motif());
                publier.setDisable(true);
                return;
            }
            statut.setText("Statut : " + b.statut() + " · source : " + b.source()
                    + " · aucune publication automatique");
            publier.setDisable(false);        // l'humain peut désormais décider
        });

        publier.setOnAction(e -> {
            SyntheseIa.Brouillon b = SyntheseIa.synthetiser(lignes, 12);
            boolean confirme = Dialogue.confirmer(getScene().getWindow(), "Publier cette synthèse ?",
                    "La synthèse a été générée par l'assistant. En publiant, vous en prenez la "
                            + "responsabilité : elle sera visible des apprenants du niveau.",
                    "Publier");
            // Le contrat B01 est appliqué ici : sans confirmation, la publication est refusée.
            fr.ihm2026.api.ia.MotifRefus decision = SyntheseIa.publier(b, confirme);
            if (decision.estRefus()) {
                statut.setText("Refus : " + decision + " — le brouillon n'a pas été publié");
                return;
            }
            statut.setText("Statut : PUBLIE · publiée par l'enseignant après relecture");
            publier.setDisable(true);
        });

        HBox actionsSynthese = new HBox(10, generer, publier, statut);
        actionsSynthese.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        VBox blocSynthese = new VBox(8, titreSynthese, synthese, actionsSynthese);
        blocSynthese.getStyleClass().addAll("card", "panneau-lateral");

        // ---- partage réseau (v0.13.0) : ce que les postes apprenants ont publié sur le serveur
        VBox blocReseau = new VBox(6, blocResultatsReseau());

        VBox contenu = new VBox(12, kpis, table, legende, blocSynthese, blocReseau);
        contenu.getStyleClass().add("colonne");
        setCenter(contenu);
        BorderPane.setMargin(contenu, new Insets(4, 0, 0, 0));
    }

    /**
     * Résultats publiés par les postes apprenants sur le serveur de la salle.
     *
     * <p>Hors ligne, le bloc n'affiche qu'une phrase : l'écran enseignant doit rester lisible sur
     * un poste qui n'est pas encore relié. Le serveur ne fait que transporter et horodater ; la
     * décision pédagogique reste ici, sur ce poste.</p>
     */
    private VBox blocResultatsReseau() {
        Label titre = new Label("Résultats reçus par le serveur de la salle");
        titre.getStyleClass().add("titre-bloc");

        java.util.List<fr.ihm2026.reseau.EtatReseau.Resultat> recus =
                fr.ihm2026.reseau.Reseau.resultats();

        VBox lignes = new VBox(2);
        if (!fr.ihm2026.reseau.Reseau.connecte()) {
            Label horsLigne = new Label("Poste non relié : lancer ce poste avec son fichier de "
                    + "lancement (postes/poste-enseignant-pc2.cmd) puis saisir l'adresse du serveur.");
            horsLigne.getStyleClass().add("mention-ia");
            horsLigne.setWrapText(true);
            lignes.getChildren().add(horsLigne);
        } else if (recus.isEmpty()) {
            Label vide = new Label("Aucun résultat publié pour l'instant sur "
                    + fr.ihm2026.reseau.Reseau.adresseProposee() + ".");
            vide.getStyleClass().add("mention-ia");
            vide.setWrapText(true);
            lignes.getChildren().add(vide);
        } else {
            for (fr.ihm2026.reseau.EtatReseau.Resultat r : recus) {
                Label ligne = new Label(String.format("%s · %s · niveau %d · %d/20 · %s · %s",
                        r.apprenant(), r.langage(), r.niveau(), r.note(),
                        r.valide() ? "au-dessus du seuil" : "sous le seuil", r.heure()));
                ligne.getStyleClass().add("texte-secondaire");
                lignes.getChildren().add(ligne);
            }
        }

        VBox bloc = new VBox(6, titre, lignes);
        bloc.getStyleClass().addAll("card", "panneau-lateral");
        return bloc;
    }
}
