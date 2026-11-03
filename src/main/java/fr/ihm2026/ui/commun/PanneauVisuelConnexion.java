package fr.ihm2026.ui.commun;

import fr.ihm2026.core.Animations;
import fr.ihm2026.core.Session;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * Panneau visuel des postes enseignant et apprenant.
 *
 * <p>L’illustration fournie est une image fixe : le mouvement est donc produit
 * par un panoramique très lent dans son cadre, accompagné d’un zoom léger.
 * Le déplacement reste décoratif, ne prend jamais le focus et s’arrête lorsque
 * l’écran quitte la scène ou lorsque les animations sont désactivées avec
 * {@code -Dihm.animations=off}.</p>
 */
final class PanneauVisuelConnexion extends StackPane {

    private static final String RESSOURCE_IMAGE =
            "/fr/ihm2026/images/connexion-programmation.jpg";
    private static final Duration DUREE_PANORAMIQUE = Duration.seconds(24);

    private final Image image;
    private final ImageView vueImage = new ImageView();
    private final DoubleProperty progression = new SimpleDoubleProperty(0.18);
    private final Timeline panoramique;
    private boolean entreeAffichee;

    PanneauVisuelConnexion(Session.Role role) {
        getStyleClass().addAll("login-visuel",
                "login-visuel--" + role.name().toLowerCase(Locale.ROOT));
        setId("connexion-visuel");
        setFocusTraversable(false);
        setMouseTransparent(true);
        setMinWidth(0);
        setMaxHeight(Double.MAX_VALUE);

        Rectangle cadre = new Rectangle();
        cadre.widthProperty().bind(widthProperty());
        cadre.heightProperty().bind(heightProperty());
        setClip(cadre);

        image = chargerImage();
        if (image != null) {
            vueImage.setImage(image);
            vueImage.setPreserveRatio(false);
            vueImage.setSmooth(true);
            vueImage.setCache(true);
            vueImage.setFocusTraversable(false);
            vueImage.fitWidthProperty().bind(widthProperty());
            vueImage.fitHeightProperty().bind(heightProperty());
            getChildren().add(vueImage);
        } else {
            getStyleClass().add("login-visuel--sans-image");
        }

        Region voile = new Region();
        voile.getStyleClass().add("login-visuel-voile");
        voile.setFocusTraversable(false);
        voile.setMouseTransparent(true);

        VBox legende = creerLegende(role);
        getChildren().addAll(voile, legende);
        StackPane.setAlignment(legende, Pos.BOTTOM_LEFT);

        widthProperty().addListener((o, ancienne, nouvelle) -> actualiserCadre());
        heightProperty().addListener((o, ancienne, nouvelle) -> actualiserCadre());
        progression.addListener((o, ancienne, nouvelle) -> actualiserCadre());

        if (image != null && Animations.actives()) {
            panoramique = new Timeline(
                    new KeyFrame(Duration.ZERO,
                            new KeyValue(progression, 0.0, Interpolator.EASE_BOTH)),
                    new KeyFrame(DUREE_PANORAMIQUE,
                            new KeyValue(progression, 1.0, Interpolator.EASE_BOTH)));
            panoramique.setAutoReverse(true);
            panoramique.setCycleCount(Timeline.INDEFINITE);
            Animations.suivre(panoramique);
            Animations.aTerminer(() -> {
                panoramique.stop();
                progression.set(0.28);
            });
        } else {
            progression.set(0.28);
            panoramique = null;
        }

        visibleProperty().addListener((o, ancienne, visible) -> {
            if (panoramique == null) {
                return;
            }
            if (visible && getScene() != null) {
                panoramique.play();
            } else {
                panoramique.pause();
            }
        });
        sceneProperty().addListener((o, ancienne, nouvelle) -> {
            if (nouvelle == null) {
                if (panoramique != null) {
                    panoramique.pause();
                }
                return;
            }
            actualiserCadre();
            if (!entreeAffichee) {
                entreeAffichee = true;
                Animations.apparaitreBulle(legende);
            }
            if (panoramique != null && isVisible()) {
                panoramique.play();
            }
        });
        actualiserCadre();
    }

    static boolean roleEligible(Session.Role role) {
        return role == Session.Role.APPRENANT || role == Session.Role.ENSEIGNANT;
    }

    static String ressourceImage() {
        return RESSOURCE_IMAGE;
    }

    /**
     * Cadre de lecture de l’image pour un panneau donné. Le cadre conserve
     * le ratio du panneau (aucune déformation), zoome légèrement et se déplace
     * du personnage vers les écrans lorsque la progression augmente.
     */
    static Rectangle2D cadreImage(double largeurImage, double hauteurImage,
            double largeurPanneau, double hauteurPanneau, double progressionDemandee) {
        if (largeurImage <= 0 || hauteurImage <= 0
                || largeurPanneau <= 0 || hauteurPanneau <= 0) {
            return Rectangle2D.EMPTY;
        }
        double progression = borner(progressionDemandee, 0, 1);
        double ratioImage = largeurImage / hauteurImage;
        double ratioPanneau = largeurPanneau / hauteurPanneau;

        double largeurMaximale;
        double hauteurMaximale;
        if (ratioImage > ratioPanneau) {
            hauteurMaximale = hauteurImage;
            largeurMaximale = hauteurImage * ratioPanneau;
        } else {
            largeurMaximale = largeurImage;
            hauteurMaximale = largeurImage / ratioPanneau;
        }

        double zoom = 0.94 - 0.10 * progression;
        double largeurCadre = largeurMaximale * zoom;
        double hauteurCadre = hauteurMaximale * zoom;
        double centreX = interpoler(0.39, 0.59, progression) * largeurImage;
        double centreY = interpoler(0.47, 0.53, progression) * hauteurImage;
        double x = borner(centreX - largeurCadre / 2, 0, largeurImage - largeurCadre);
        double y = borner(centreY - hauteurCadre / 2, 0, hauteurImage - hauteurCadre);
        return new Rectangle2D(x, y, largeurCadre, hauteurCadre);
    }

    private VBox creerLegende(Session.Role role) {
        Label marque = new Label("IHM 2026 · " + role.label().toUpperCase(Locale.ROOT));
        marque.getStyleClass().add("login-visuel-marque");
        marque.setFocusTraversable(false);

        Label titre = new Label(role == Session.Role.ENSEIGNANT
                ? "Créer, accompagner,\névaluer."
                : "Apprendre, pratiquer,\nprogresser.");
        titre.getStyleClass().add("login-visuel-titre");
        titre.setWrapText(true);
        titre.setFocusTraversable(false);

        Label texte = new Label(role == Session.Role.ENSEIGNANT
                ? "Préparez vos parcours et suivez les progrès de vos apprenants."
                : "Retrouvez vos niveaux et avancez étape par étape.");
        texte.getStyleClass().add("login-visuel-texte");
        texte.setWrapText(true);
        texte.setFocusTraversable(false);

        HBox langages = new HBox(8, puce("Java"), puce("Python"), puce("C++"));
        langages.setAlignment(Pos.CENTER_LEFT);
        langages.setFocusTraversable(false);

        VBox legende = new VBox(10, marque, titre, texte, langages);
        legende.getStyleClass().add("login-visuel-legende");
        legende.setAlignment(Pos.BOTTOM_LEFT);
        legende.setPadding(new Insets(30));
        legende.setMaxWidth(450);
        legende.setMaxHeight(Region.USE_PREF_SIZE);
        legende.setMouseTransparent(true);
        legende.setFocusTraversable(false);
        return legende;
    }

    private static Label puce(String texte) {
        Label puce = new Label(texte);
        puce.getStyleClass().add("login-visuel-puce");
        puce.setFocusTraversable(false);
        return puce;
    }

    private void actualiserCadre() {
        if (image == null || getWidth() <= 0 || getHeight() <= 0) {
            return;
        }
        vueImage.setViewport(cadreImage(image.getWidth(), image.getHeight(),
                getWidth(), getHeight(), progression.get()));
    }

    private static Image chargerImage() {
        try (InputStream flux = PanneauVisuelConnexion.class.getResourceAsStream(RESSOURCE_IMAGE)) {
            if (flux == null) {
                return null;
            }
            Image chargee = new Image(flux);
            return chargee.isError() ? null : chargee;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private static double interpoler(double debut, double fin, double progression) {
        return debut + (fin - debut) * progression;
    }

    private static double borner(double valeur, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, valeur));
    }
}
