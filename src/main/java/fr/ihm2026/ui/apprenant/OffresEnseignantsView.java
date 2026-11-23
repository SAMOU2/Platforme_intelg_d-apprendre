package fr.ihm2026.ui.apprenant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.ihm2026.core.Router;
import fr.ihm2026.core.Session;
import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.reseau.Reseau;
import fr.ihm2026.ui.commun.ActualisationVue;
import fr.ihm2026.ui.commun.Dialogue;
import fr.ihm2026.ui.commun.EnteteVue;
import fr.ihm2026.ui.commun.EtatVide;
import fr.ihm2026.ui.commun.Notifications;
import fr.ihm2026.ui.commun.SuiviPedagogique;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

/**
 * A1 — le catalogue des enseignants, côté apprenant.
 *
 * <p>L'administrateur n'affecte plus personne : c'est l'apprenant qui choisit. Cet écran liste
 * les enseignants actifs qui <b>proposent réellement une formation</b> dans le parcours affiché
 * (une offre publiée ou au moins un niveau publié), avec les informations disponibles — titre de
 * l'offre, présentation, niveaux publiés, acceptation automatique par le bot — et l'état de sa
 * propre demande.</p>
 *
 * <p>Le parcours d'inscription tient en trois temps, sans quitter l'application :</p>
 * <ol>
 *   <li><b>S'inscrire</b> : l'apprenant saisit son nom (et un message facultatif) ;</li>
 *   <li>la demande est <b>transmise à l'enseignant</b>, qui l'accepte ou la refuse ; le bot de
 *       l'offre peut l'accepter immédiatement si l'enseignant l'a activé ;</li>
 *   <li>dès l'acceptation, <b>l'accès aux cours publiés est ouvert</b> : cours, exercices puis
 *       test final dans la même fenêtre (route {@link Router#ATELIER}).</li>
 * </ol>
 *
 * <p>La liste se relit toute seule toutes les 5 secondes : une acceptation apparaît sans que
 * l'apprenant ait à deviner quand rafraîchir. Aucune donnée de démonstration n'est inventée.</p>
 */
public class OffresEnseignantsView extends BorderPane {

    private final Router router;
    private final Label etat = new Label();
    private final VBox cartes = new VBox(10);
    private final Button actualiser = new Button("Actualiser le catalogue");
    private final Button historique = new Button("Historique de mes demandes");
    private ActualisationVue<JsonNode> suivi;

    public OffresEnseignantsView(Router router) {
        this.router = router;
        getStyleClass().add("vue");
        setTop(new EnteteVue("Enseignants disponibles — " + Session.langage().label(),
                "Choisissez un enseignant, cliquez sur « S'inscrire » et saisissez votre nom. "
                        + "La demande lui est transmise ; son bot peut l'accepter immédiatement. "
                        + "Dès l'acceptation, ses cours publiés vous sont ouverts."));

        if (!Reseau.sessionCompteInstallee()) {
            EtatVide vide = new EtatVide("Le catalogue des enseignants est publié par le serveur "
                    + "central PC1. Connectez ce poste apprenant au serveur de l'établissement "
                    + "(écran « Connexion au serveur ») pour voir les formations proposées et vous "
                    + "inscrire. Aucun enseignant fictif n'est affiché hors connexion.");
            VBox contenu = new VBox(12, vide);
            contenu.getStyleClass().add("colonne");
            setCenter(contenu);
            BorderPane.setMargin(contenu, new javafx.geometry.Insets(4, 0, 0, 0));
            return;
        }

        etat.setWrapText(true);
        etat.setFocusTraversable(true);
        etat.getStyleClass().add("texte-secondaire");
        etat.setText("Chargement du catalogue depuis PC1…");
        cartes.getStyleClass().add("colonne");

        ScrollPane scroll = new ScrollPane(cartes);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("scroll-vue");

        actualiser.setOnAction(e -> {
            if (suivi != null) {
                suivi.actualiser();
            }
        });
        historique.setOnAction(e -> afficherHistorique());
        HBox barre = new HBox(10, actualiser, historique);
        barre.getStyleClass().add("barre-actions");

        VBox bas = new VBox(8, etat, barre);
        setCenter(scroll);
        setBottom(bas);
        BorderPane.setMargin(scroll, new javafx.geometry.Insets(4, 0, 0, 0));

        String langage = Session.langage().code();
        suivi = new ActualisationVue<>(this,
                () -> SuiviPedagogique.appeler(SuiviPedagogique.demande("offres").put("langage", langage)),
                this::afficherOffres,
                erreur -> etat.setText("Catalogue indisponible pour le moment : "
                        + message(erreur) + " Nouvelle tentative automatique…"));
    }

    // ------------------------------------------------------------------ affichage
    private void afficherOffres(JsonNode offres) {
        cartes.getChildren().clear();
        if (offres == null || offres.isEmpty()) {
            EtatVide vide = new EtatVide("Aucun enseignant ne propose encore de cours publié en "
                    + Session.langage().label() + ". Changez de parcours dans la barre latérale, "
                    + "ou revenez plus tard : ce catalogue se relit tout seul.");
            cartes.getChildren().add(vide);
            etat.setText("Aucune offre publiée pour ce parcours.");
            return;
        }
        int acces = 0;
        int attente = 0;
        for (JsonNode offre : offres) {
            cartes.getChildren().add(carte(offre));
            if (offre.path("inscrit").asBoolean() || "ACCEPTEE".equals(offre.path("statut_demande").asText(""))) {
                acces++;
            } else if ("EN_ATTENTE".equals(offre.path("statut_demande").asText(""))) {
                attente++;
            }
        }
        etat.setText(offres.size() + " enseignant(s) disponible(s) en " + Session.langage().label()
                + " · " + acces + " accès accordé(s) · " + attente + " demande(s) en attente."
                + " Liste relue automatiquement toutes les 5 secondes.");
    }

    private VBox carte(JsonNode offre) {
        String enseignant = offre.path("enseignant").asText("Enseignant");
        String langageCode = offre.path("langage").asText(Session.langage().code());
        boolean inscrit = offre.path("inscrit").asBoolean();
        String statut = offre.path("statut_demande").asText("");

        Label titre = new Label(enseignant + " — " + offre.path("titre").asText("Formation"));
        titre.getStyleClass().add("titre-bloc");
        titre.setWrapText(true);

        String presentation = offre.path("presentation").asText("");
        Label texteOffre = new Label(presentation.isBlank()
                ? "Cet enseignant n'a pas encore rédigé de présentation : ses niveaux publiés sont listés ci-dessous."
                : presentation);
        texteOffre.setWrapText(true);

        Label niveaux = new Label(offre.path("niveaux_publies").asInt() + " niveau(x) publié(s)"
                + (offre.path("niveaux_titres").asText("").isBlank()
                        ? "" : " : " + offre.path("niveaux_titres").asText("")));
        niveaux.setWrapText(true);
        niveaux.getStyleClass().add("texte-secondaire");

        Label bot = new Label(offre.path("acceptation_bot").asBoolean()
                ? "Bot d'acceptation activé : votre accès peut être accordé immédiatement."
                : "Décision de l'enseignant : votre demande lui est transmise.");
        bot.setWrapText(true);
        bot.getStyleClass().add("mention-ia");

        Label demande = new Label(libelleStatut(statut, offre.path("decide_par").asText(""),
                offre.path("motif_demande").asText("")));
        demande.setWrapText(true);
        demande.getStyleClass().add("texte-secondaire");

        Button ouvrir = new Button("Ouvrir mes cours");
        ouvrir.getStyleClass().add("btn-primary");
        ouvrir.setAccessibleText("Ouvrir les cours de " + enseignant);
        ouvrir.setOnAction(e -> ouvrirParcours(langageCode));

        Button inscrire = new Button("S'inscrire");
        inscrire.getStyleClass().add("btn-primary");
        inscrire.setAccessibleText("S'inscrire à la formation de " + enseignant);
        inscrire.setOnAction(e -> demanderInscription(offre));

        Button retirer = new Button("Retirer ma demande");
        retirer.setAccessibleText("Retirer la demande envoyée à " + enseignant);
        retirer.setOnAction(e -> annulerDemande(offre));

        FlowPane actions = new FlowPane(8, 8);
        if (inscrit || "ACCEPTEE".equals(statut)) {
            actions.getChildren().add(ouvrir);
            if (!inscrit) {
                Label enAttenteAcces = new Label("Accès accepté : ouvrez votre parcours pour voir les niveaux.");
                enAttenteAcces.setWrapText(true);
                enAttenteAcces.getStyleClass().add("texte-secondaire");
                actions.getChildren().add(enAttenteAcces);
            }
        } else if ("EN_ATTENTE".equals(statut)) {
            actions.getChildren().add(retirer);
        } else {
            actions.getChildren().add(inscrire);
        }

        VBox carte = new VBox(8, titre, texteOffre, niveaux, bot, demande, actions);
        carte.getStyleClass().add("card");
        return carte;
    }

    /** Libellé lisible d'un état de demande, identique côté catalogue et côté historique. */
    static String libelleStatut(String statut, String decidePar, String motif) {
        String decideur = "BOT".equals(decidePar) ? "le bot de l'offre"
                : "ENSEIGNANT".equals(decidePar) ? "l'enseignant"
                : "APPRENANT".equals(decidePar) ? "vous" : "";
        String texte = switch (statut == null ? "" : statut) {
            case "EN_ATTENTE" -> "Demande en attente — transmise à l'enseignant.";
            case "ACCEPTEE" -> "Demande acceptée par " + (decideur.isEmpty() ? "l'enseignant" : decideur)
                    + " : accès aux cours accordé.";
            case "REFUSEE" -> "Demande refusée par " + (decideur.isEmpty() ? "l'enseignant" : decideur) + ".";
            case "ANNULEE" -> "Demande retirée.";
            default -> "Aucune demande envoyée à cet enseignant.";
        };
        return motif == null || motif.isBlank() ? texte : texte + " Motif : " + motif;
    }

    // ------------------------------------------------------------------ actions
    private void demanderInscription(JsonNode offre) {
        Window owner = fenetre();
        Label aide = new Label("Votre demande part vers " + offre.path("enseignant").asText("cet enseignant")
                + ". Il l'accepte lui-même, ou le bot de son offre l'accepte immédiatement. "
                + "Dès l'acceptation, ses cours, exercices et tests publiés vous sont accessibles.");
        aide.setWrapText(true);
        TextField nom = new TextField(Session.utilisateur());
        nom.setPromptText("Nom et prénom");
        nom.setAccessibleText("Votre nom, transmis à l'enseignant avec la demande");
        TextArea message = new TextArea();
        message.setPromptText("Message facultatif pour l'enseignant (600 caractères maximum)");
        message.setWrapText(true);
        message.setPrefRowCount(3);
        message.setAccessibleText("Message facultatif envoyé à l'enseignant");

        Dialog<ButtonType> dialogue = new Dialog<>();
        dialogue.initOwner(owner);
        dialogue.setTitle("S'inscrire à cette formation");
        dialogue.setHeaderText(offre.path("titre").asText("Formation proposée"));
        dialogue.getDialogPane().setContent(new VBox(10, aide, new Label("Votre nom"), nom,
                new Label("Message (facultatif)"), message));
        dialogue.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        Button envoyer = Dialogue.bouton(dialogue.getDialogPane(), ButtonType.OK);
        envoyer.setText("Envoyer ma demande");
        envoyer.disableProperty().bind(nom.textProperty().isEmpty());
        Dialogue.appliquerTheme(dialogue.getDialogPane(), owner);
        dialogue.showAndWait().filter(b -> b == ButtonType.OK).ifPresent(b -> {
            ObjectNode demande = SuiviPedagogique.demande("inscrire-offre")
                    .put("enseignant", offre.path("enseignant_id").asLong())
                    .put("langage", offre.path("langage").asText(Session.langage().code()))
                    .put("nom", nom.getText().strip())
                    .put("message", message.getText().strip());
            envoyerDemande(demande);
        });
    }

    private void envoyerDemande(ObjectNode demande) {
        actualiser.setDisable(true);
        etat.setText("Envoi de la demande à l'enseignant…");
        TaskRunner.run(() -> SuiviPedagogique.appeler(demande), resultat -> {
            actualiser.setDisable(false);
            String texte = resultat.path("message").asText("Demande enregistrée.");
            boolean acceptee = "ACCEPTEE".equals(resultat.path("statut").asText(""));
            Notifications.afficher(fenetre(),
                    acceptee ? Notifications.Type.SUCCES : Notifications.Type.INFORMATION, texte);
            etat.setText(texte);
            if (suivi != null) {
                suivi.actualiser();
            }
        }, erreur -> {
            actualiser.setDisable(false);
            etat.setText("Demande non transmise : " + message(erreur));
        });
    }

    private void annulerDemande(JsonNode offre) {
        long demandeId = offre.path("demande_id").asLong();
        if (demandeId < 1) {
            etat.setText("Demande introuvable : actualisez le catalogue.");
            return;
        }
        if (!Dialogue.confirmer(fenetre(), "Retirer cette demande ?",
                "L'enseignant ne verra plus votre demande. Vous pourrez en envoyer une nouvelle plus tard.",
                "Retirer")) {
            return;
        }
        actualiser.setDisable(true);
        TaskRunner.run(() -> SuiviPedagogique.appeler(
                        SuiviPedagogique.demande("annuler-demande").put("demande", demandeId)),
                ok -> {
                    actualiser.setDisable(false);
                    etat.setText("Demande retirée.");
                    if (suivi != null) {
                        suivi.actualiser();
                    }
                },
                erreur -> {
                    actualiser.setDisable(false);
                    etat.setText("Retrait impossible : " + message(erreur));
                });
    }

    private void afficherHistorique() {
        historique.setDisable(true);
        TaskRunner.run(() -> SuiviPedagogique.appeler(SuiviPedagogique.demande("mes-demandes")), liste -> {
            historique.setDisable(false);
            StringBuilder texte = new StringBuilder();
            if (liste == null || liste.isEmpty()) {
                texte.append("Aucune demande envoyée pour le moment.");
            } else {
                for (JsonNode demande : liste) {
                    texte.append("• ").append(demande.path("enseignant").asText("Enseignant"))
                            .append(" — ").append(demande.path("langage").asText(""))
                            .append(" · nom saisi : ").append(demande.path("nom_demandeur").asText(""))
                            .append("\n   ")
                            .append(libelleStatut(demande.path("statut").asText(""),
                                    demande.path("decide_par").asText(""), demande.path("motif").asText("")))
                            .append("\n");
                }
            }
            Dialogue.info(fenetre(), "Mes demandes d'inscription", texte.toString());
        }, erreur -> {
            historique.setDisable(false);
            etat.setText("Historique indisponible : " + message(erreur));
        });
    }

    private void ouvrirParcours(String langageCode) {
        Session.Langage cible = langage(langageCode);
        if (cible != null && Session.langage() != cible) {
            Session.setLangage(cible);
        }
        router.go(Router.PARCOURS);
    }

    private static Session.Langage langage(String code) {
        for (Session.Langage langage : Session.Langage.values()) {
            if (langage.code().equals(code)) {
                return langage;
            }
        }
        return null;
    }

    private Window fenetre() {
        return getScene() == null ? null : getScene().getWindow();
    }

    private static String message(Throwable erreur) {
        return erreur == null || erreur.getMessage() == null || erreur.getMessage().isBlank()
                ? "service momentanément inaccessible."
                : erreur.getMessage();
    }
}
