package fr.ihm2026.ui.enseignant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.Notifications;
import fr.ihm2026.ui.commun.SuiviPedagogique;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.util.StringConverter;

/**
 * E0 — l'offre de formation publiée par l'enseignant.
 *
 * <p>C'est ce que l'apprenant lit avant de s'inscrire : le catalogue de son interface n'affiche
 * que les enseignants actifs qui proposent réellement une formation (offre publiée ou au moins un
 * niveau publié), avec ce titre, cette présentation,
 * le nombre de niveaux publiés et le réglage du bot.</p>
 *
 * <p>Le bot d'acceptation est un choix de l'enseignant, pas une obligation : activé, il accorde
 * l'accès dès la réception de la demande (décision tracée « BOT » et visible dans les demandes
 * reçues) ; désactivé, chaque demande attend la décision humaine. Le bot n'évalue jamais un
 * travail et n'accorde aucune note.</p>
 */
final class OffreEnseignantView extends VBox {

    private final ComboBox<JsonNode> langues = new ComboBox<>();
    private final TextField titre = new TextField();
    private final TextArea presentation = new TextArea();
    private final CheckBox bot = new CheckBox("Le bot accepte automatiquement les demandes d'inscription");
    private final CheckBox ouverte = new CheckBox("Offre ouverte aux nouvelles demandes");
    private final Label etat = new Label();
    private final Button publier = new Button("Publier mon offre");
    private final Button actualiser = new Button("Actualiser");
    private boolean occupe;

    OffreEnseignantView() {
        super(8);
        Label entete = new Label("Ce que les apprenants voient avant de s'inscrire");
        entete.getStyleClass().add("titre-bloc");
        Label aide = new Label("Une offre par langage : titre, présentation, ouverture aux "
                + "nouvelles demandes et bot d'acceptation. Elle peut être publiée avant vos "
                + "niveaux. Les apprenants s'inscrivent depuis leur propre interface ; vous "
                + "retrouvez leurs demandes dans « Mes apprenants et copies ».");
        aide.setWrapText(true);
        aide.getStyleClass().add("texte-secondaire");
        etat.setWrapText(true);

        langues.setPromptText("Langage de l'offre");
        langues.setMaxWidth(Double.MAX_VALUE);
        langues.setConverter(new StringConverter<>() {
            @Override
            public String toString(JsonNode offre) {
                if (offre == null) {
                    return "";
                }
                String texte = offre.path("langage").asText("") + " · "
                        + offre.path("niveaux_publies").asInt() + " niveau(x) publié(s) · "
                        + offre.path("demandes_attente").asInt() + " demande(s) en attente · "
                        + offre.path("apprenants_inscrits").asInt() + " apprenant(s) avec accès";
                return offre.path("titre").isNull() ? texte + " · offre non rédigée" : texte;
            }

            @Override
            public JsonNode fromString(String saisie) {
                return null;
            }
        });
        langues.valueProperty().addListener((o, avant, offre) -> remplir(offre));

        titre.setPromptText("Exemple : Python du débutant aux fonctions (5 niveaux)");
        titre.setAccessibleText("Titre de l'offre visible par les apprenants");
        presentation.setPromptText("Présentez ce que l'apprenant trouvera : niveaux publiés, "
                + "rythme, ce que vous corrigez vous-même…");
        presentation.setWrapText(true);
        presentation.setPrefRowCount(4);
        presentation.setAccessibleText("Présentation de l'offre visible par les apprenants");
        bot.setAccessibleText("Activer l'acceptation automatique des demandes par le bot");
        Label noteBot = new Label("Bot activé : l'accès aux cours est accordé dès la réception de la "
                + "demande, sans attendre votre décision. Chaque acceptation du bot reste listée et "
                + "contrôlable. Bot désactivé : vous décidez de chaque demande.");
        noteBot.setWrapText(true);
        noteBot.getStyleClass().add("mention-ia");

        publier.getStyleClass().add("btn-primary");
        publier.setOnAction(e -> publierOffre());
        actualiser.setOnAction(e -> charger());
        FlowPane actions = new FlowPane(8, 8, publier, actualiser);

        getChildren().addAll(entete, aide, new Label("Langage"), langues, new Label("Titre de l'offre"),
                titre, new Label("Présentation"), presentation, bot, noteBot, ouverte, actions, etat);
        charger();
    }

    private void remplir(JsonNode offre) {
        if (offre == null) {
            titre.clear();
            presentation.clear();
            bot.setSelected(false);
            ouverte.setSelected(true);
            return;
        }
        titre.setText(offre.path("titre").isNull() ? "" : offre.path("titre").asText(""));
        presentation.setText(offre.path("presentation").isNull() ? "" : offre.path("presentation").asText(""));
        bot.setSelected(offre.path("auto_acceptation").asBoolean());
        ouverte.setSelected(offre.path("ouverte").asBoolean(true));
    }

    private void charger() {
        if (occupe) {
            return;
        }
        occupe = true;
        actualiser.setDisable(true);
        publier.setDisable(true);
        etat.setText("Chargement de vos offres depuis PC1…");
        TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("mon-offre")), offres -> {
            occupe = false;
            actualiser.setDisable(false);
            publier.setDisable(false);
            String langageCourant = Session.langage().code();
            langues.getItems().clear();
            if (offres != null) {
                offres.forEach(langues.getItems()::add);
            }
            if (langues.getItems().isEmpty()) {
                // Le serveur liste un langage par ligne, publiée ou non : une liste vide signifie
                // que les langages ne sont pas initialisés sur PC1 (migration 01).
                etat.setText("Aucun langage n'est initialisé sur PC1 : les offres ne peuvent pas "
                        + "être publiées. Vérifiez que la migration 01 a bien été appliquée sur le "
                        + "serveur central.");
                remplir(null);
                return;
            }
            JsonNode courant = null;
            for (JsonNode offre : langues.getItems()) {
                if (offre.path("langage").asText("").equals(langageCourant)) {
                    courant = offre;
                }
            }
            langues.getSelectionModel().select(courant == null ? 0 : langues.getItems().indexOf(courant));
            JsonNode choisie = langues.getSelectionModel().getSelectedItem();
            remplir(choisie);
            etat.setText(choisie != null && !choisie.path("titre").isNull()
                    ? "Offre enregistrée sur PC1 : elle est lue par le catalogue des apprenants. "
                            + "Le bot n'accepte que si vous l'activez ici."
                    : "Aucune offre publiée pour ce langage : rédigez le titre et la présentation, "
                            + "puis publiez. Vous pouvez publier avant vos niveaux — les apprenants "
                            + "vous voient dans leur catalogue dès qu'une offre ou un niveau est "
                            + "publié, et leurs demandes arrivent dans « Mes apprenants et copies ».");
        }, erreur -> {
            occupe = false;
            actualiser.setDisable(false);
            publier.setDisable(false);
            etat.setText("Offres indisponibles : " + erreur.getMessage());
        });
    }

    private void publierOffre() {
        if (occupe) {
            return;
        }
        JsonNode offre = langues.getSelectionModel().getSelectedItem();
        if (offre == null) {
            Dialogue.erreur(getScene() == null ? null : getScene().getWindow(), "Langage manquant",
                    "Sélectionnez le langage de l'offre à publier.");
            return;
        }
        String texteTitre = titre.getText().strip();
        String textePresentation = presentation.getText().strip();
        if (texteTitre.length() < 3 || textePresentation.length() < 3) {
            Dialogue.erreur(getScene() == null ? null : getScene().getWindow(), "Offre incomplète",
                    "Un titre et une présentation d'au moins 3 caractères sont obligatoires : "
                            + "ce sont les informations lues par les apprenants avant de s'inscrire.");
            return;
        }
        if (bot.isSelected() && !Dialogue.confirmer(getScene().getWindow(),
                "Activer l'acceptation automatique ?",
                "Le bot accordera l'accès à vos cours publiés dès la réception d'une demande, sans "
                        + "attente de votre décision. Vous gardez la liste de ces acceptations et "
                        + "pouvez désactiver le bot à tout moment.", "Activer le bot")) {
            return;
        }
        occupe = true;
        publier.setDisable(true);
        actualiser.setDisable(true);
        etat.setText("Publication de l'offre…");
        ObjectNode requete = SuiviPedagogique.demande("publier-offre")
                .put("langage", offre.path("langage").asText(""))
                .put("titre", texteTitre)
                .put("presentation", textePresentation)
                .put("auto_acceptation", bot.isSelected())
                .put("ouverte", ouverte.isSelected());
        TaskRunner.run(() -> SuiviPedagogique.appeler(requete), resultat -> {
            Notifications.afficher(getScene() == null ? null : getScene().getWindow(),
                    Notifications.Type.SUCCES, bot.isSelected()
                            ? "Offre publiée : le bot accepte désormais les demandes immédiatement."
                            : "Offre publiée : les demandes attendront votre décision.");
            charger();
        }, erreur -> {
            occupe = false;
            publier.setDisable(false);
            actualiser.setDisable(false);
            etat.setText("Publication impossible : " + erreur.getMessage());
        });
    }
}
