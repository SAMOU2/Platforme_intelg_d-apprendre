package fr.ihm2026.ui.commun;

import fr.ihm2026.core.TaskRunner;
import fr.ihm2026.reseau.WifiWindows;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Windows effectue la connexion ; cette vue ne manipule aucun mot de passe Wi-Fi. */
final class ChoixWifi extends VBox {
    private final ComboBox<String> reseaux = new ComboBox<>();
    private final Label message = new Label();
    private final Button actualiser = new Button("Actualiser les Wi-Fi");
    private final Button choisir = new Button("Utiliser ce Wi-Fi");
    private final Timeline surveillance;
    private final Runnable rechercher;
    private final Runnable invalider;
    private String attendu;
    private boolean lecture;
    private int essais;

    ChoixWifi(Runnable rechercher, Runnable invalider) {
        super(8);
        this.rechercher = rechercher;
        this.invalider = invalider;
        reseaux.setPromptText("Choisir le Wi-Fi du serveur…");
        reseaux.setId("wifi-disponibles");
        reseaux.setMaxWidth(Double.MAX_VALUE);
        message.setWrapText(true);
        getChildren().addAll(new Label("1. Choisir le réseau Wi-Fi — connexion gérée par Windows"),
                reseaux, actualiser, choisir, message);
        surveillance = new Timeline(new KeyFrame(Duration.seconds(4), e -> actualiser()));
        surveillance.setCycleCount(30);
        surveillance.setOnFinished(e -> {
            attendu = null;
            message.setText("Attente terminée. Connectez-vous dans Windows puis cliquez sur Utiliser ce Wi-Fi.");
        });
        actualiser.setOnAction(e -> actualiser());
        choisir.setOnAction(e -> {
            if (reseaux.getValue() == null) { message.setText("Choisissez un Wi-Fi dans la liste."); return; }
            surveillance.stop();
            attendu = reseaux.getValue();
            essais = 0;
            invalider.run();
            actualiser();
        });
        disabledProperty().addListener((o, ancien, bloque) -> {
            if (bloque) { surveillance.stop(); attendu = null; }
        });
        sceneProperty().addListener((o, ancienne, nouvelle) -> {
            if (nouvelle == null) { surveillance.stop(); attendu = null; }
        });
        if (WifiWindows.disponible()) actualiser();
        else {
            reseaux.setDisable(true); actualiser.setDisable(true); choisir.setDisable(true);
            message.setText("Choix Wi-Fi intégré disponible sur Windows. Utilisez le réseau actuel ou la connexion avancée.");
        }
    }

    private void actualiser() {
        if (lecture) return;
        lecture = true;
        actualiser.setDisable(true);
        choisir.setDisable(true);
        TaskRunner.run(WifiWindows::lire, etat -> {
            lecture = false;
            actualiser.setDisable(false); choisir.setDisable(false);
            String selection = reseaux.getValue();
            reseaux.getItems().setAll(etat.disponibles());
            // Un SSID connecté peut être absent du dernier scan Windows.
            for (String nom : etat.connectes()) if (!reseaux.getItems().contains(nom)) reseaux.getItems().add(nom);
            if (selection != null && reseaux.getItems().contains(selection)) reseaux.setValue(selection);
            if (attendu != null && etat.connectes().contains(attendu)) {
                surveillance.stop();
                message.setText("Connecté au Wi-Fi « " + attendu + " ». Recherche du serveur…");
                attendu = null;
                rechercher.run();
            } else if (attendu != null) {
                message.setText("Dans Windows, connectez-vous à « " + attendu + " ». Vérification automatique en cours…");
                if (essais++ == 0) {
                    TaskRunner.run(() -> { WifiWindows.ouvrirParametres(); return true; },
                            ok -> { if (attendu != null) surveillance.playFromStart(); },
                            erreur -> { attendu = null; message.setText("Ouvrez les paramètres Wi-Fi Windows, puis réessayez."); });
                }
            } else {
                message.setText(etat.disponibles().isEmpty() && etat.connectes().isEmpty()
                        ? "Aucun Wi-Fi visible. Vérifiez Windows (Wi-Fi, localisation). Le réseau actuel reste utilisable."
                        : "Choisissez le Wi-Fi du serveur. Aucun mot de passe Wi-Fi n'est demandé ici.");
            }
        }, erreur -> {
            lecture = false;
            actualiser.setDisable(false); choisir.setDisable(false);
            surveillance.stop(); attendu = null;
            message.setText(erreur.getMessage() + " Utilisez Windows ou le réseau actuel.");
        });
    }
}
