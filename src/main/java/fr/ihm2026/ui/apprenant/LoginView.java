package fr.ihm2026.ui.apprenant;

import fr.ihm2026.core.Config;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Écran de refus de démarrage quand aucun rôle de poste n'a été configuré. */
public class LoginView extends StackPane {

    public LoginView() {
        getStyleClass().add("login-view");

        Label marque = new Label("IHM 2026");
        marque.getStyleClass().add("login-marque");
        Label titre = new Label("Poste non configuré");
        titre.getStyleClass().add("login-titre");
        Label sousTitre = new Label("Aucun rôle n'est associé à ce poste.");
        sousTitre.getStyleClass().add("login-soustitre");
        Label aide = new Label("Lancez ce poste depuis le script correspondant dans le dossier "
                + "postes (administrateur, enseignant ou apprenant). Une connexion au serveur "
                + "est obligatoire avant d'accéder aux écrans.");
        aide.setWrapText(true);
        aide.setMaxWidth(520);
        aide.setAlignment(Pos.CENTER);
        Label mode = new Label(Config.APP_NAME + " · version " + Config.VERSION);
        mode.getStyleClass().add("login-mode");

        VBox carte = new VBox(10, marque, titre, sousTitre, aide, mode);
        carte.getStyleClass().addAll("card", "login-card");
        carte.setAlignment(Pos.CENTER);
        carte.setMaxWidth(600);
        carte.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);

        getChildren().add(carte);
        setAlignment(Pos.CENTER);
    }
}
