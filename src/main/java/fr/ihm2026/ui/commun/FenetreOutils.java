package fr.ihm2026.ui.commun;

import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/**
 * Fenêtre sans décoration système, à la manière de macOS (v0.10.0).
 *
 * <p>Pour avoir les pastilles, une barre unifiée et des coins arrondis, la fenêtre doit perdre son
 * cadre natif ({@link StageStyle#UNDECORATED}). Ce fichier rend alors les <b>gestes</b> qu'un cadre
 * natif offrait, sans quoi l'interface serait plus jolie et moins utilisable :</p>
 * <ul>
 *   <li><b>déplacer</b> : glisser la barre de titre ;</li>
 *   <li><b>agrandir</b> : double-clic sur la barre de titre (comme macOS) ;</li>
 *   <li><b>redimensionner</b> : huit zones fines sur les bords et les coins, avec le curseur
 *       qui change au survol.</li>
 * </ul>
 *
 * <p>Le rayon des coins est obtenu par une marge transparente autour de la scène : la fenêtre
 * garde ainsi son ombre portée, comme une vraie fenêtre macOS.</p>
 *
 * <p><b>Défaut corrigé en v0.14.4 — le curseur en double flèche au milieu de la fenêtre.</b>
 * Les huit zones de redimensionnement étaient des {@link Region} nues : une {@code Region} n'a pas
 * de largeur maximale, donc le {@link StackPane} les étirait chacune sur <b>toute</b> la fenêtre au
 * lieu de les laisser sur le bord. La dernière ajoutée — le coin bas-droit, curseur
 * {@link Cursor#SE_RESIZE}, cette double flèche en diagonale — recouvrait donc l'écran entier :
 * le curseur devenait anormal partout et, surtout, <b>aucun clic n'atteignait les boutons ni les
 * champs</b> (d'où « je ne peux pas écrire dans la fenêtre »). Les zones ont maintenant une taille
 * maximale : elles restent des bandes de 6 px et des coins de 12 px.</p>
 */
public final class FenetreOutils {

    /** Marge transparente autour du contenu : c'est elle qui dessine l'ombre et les coins. */
    public static final double MARGE = 10;

    /** Épaisseur des bandes de redimensionnement sur les bords. */
    private static final double BORD = 6;

    /** Épaisseur des coins : le double, pour attraper un coin sans viser au pixel près. */
    private static final double COIN = 14;

    private FenetreOutils() {
    }

    /** Prépare la fenêtre et branche le déplacement par la barre de titre. */
    public static void installer(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);
    }

    /** Rend une barre déplaçable (glisser) et « zoomable » (double-clic). */
    public static void rendreDeplacable(Region barre, Stage stage) {
        final double[] decalage = new double[2];
        barre.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            decalage[0] = stage.getX() - e.getScreenX();
            decalage[1] = stage.getY() - e.getScreenY();
        });
        barre.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (stage.isMaximized()) {
                return;   // une fenêtre agrandie ne se déplace pas, comme sur macOS
            }
            stage.setX(e.getScreenX() + decalage[0]);
            stage.setY(e.getScreenY() + decalage[1]);
        });
        barre.addEventHandler(MouseEvent.MOUSE_CLICKED, e -> {
            if (e.getClickCount() == 2 && e.getButton() == javafx.scene.input.MouseButton.PRIMARY) {
                basculerAgrandissement(stage);
            }
        });
    }

    public static void basculerAgrandissement(Stage stage) {
        if (stage.isMaximized()) {
            stage.setMaximized(false);
            return;
        }
        Rectangle2D ecran = ecranDe(stage);
        stage.setMaximized(true);
        stage.setX(ecran.getMinX());
        stage.setY(ecran.getMinY());
        stage.setWidth(ecran.getWidth());
        stage.setHeight(ecran.getHeight());
    }

    private static Rectangle2D ecranDe(Stage stage) {
        Screen ecran = Screen.getScreensForRectangle(stage.getX(), stage.getY(),
                Math.max(1, stage.getWidth()), Math.max(1, stage.getHeight()))
                .stream().findFirst().orElse(Screen.getPrimary());
        return ecran.getVisualBounds();
    }

    /**
     * Ajoute les huit zones de redimensionnement au-dessus du contenu.
     * À appeler une fois la scène construite.
     *
     * <p>Les zones sont posées dans la marge transparente : elles ne recouvrent jamais le contenu,
     * sauf les 6 derniers pixels du bord, comme le faisaient les fenêtres macOS.</p>
     */
    public static void installerRedimensionnement(Stage stage, Scene scene, StackPane calque) {
        Region haut = zone(Pos.TOP_LEFT, false, true, Cursor.N_RESIZE, stage, scene, calque, BORD, BORD);
        Region bas = zone(Pos.BOTTOM_LEFT, false, true, Cursor.S_RESIZE, stage, scene, calque, BORD, BORD);
        Region gauche = zone(Pos.TOP_LEFT, true, false, Cursor.W_RESIZE, stage, scene, calque, BORD, BORD);
        Region droite = zone(Pos.TOP_RIGHT, true, false, Cursor.E_RESIZE, stage, scene, calque, BORD, BORD);
        Region hautGauche = zone(Pos.TOP_LEFT, false, false, Cursor.NW_RESIZE, stage, scene, calque, COIN, COIN);
        Region hautDroit = zone(Pos.TOP_RIGHT, false, false, Cursor.NE_RESIZE, stage, scene, calque, COIN, COIN);
        Region basGauche = zone(Pos.BOTTOM_LEFT, false, false, Cursor.SW_RESIZE, stage, scene, calque, COIN, COIN);
        Region basDroit = zone(Pos.BOTTOM_RIGHT, false, false, Cursor.SE_RESIZE, stage, scene, calque, COIN, COIN);
        calque.getChildren().addAll(haut, bas, gauche, droite,
                hautGauche, hautDroit, basGauche, basDroit);
    }

    /**
     * Fabrique une zone de redimensionnement.
     *
     * <p>{@code etireLargeur} / {@code etireHauteur} disent axe le long duquel la zone suit la
     * taille de la fenêtre (une bande de bord) ; l'autre axe garde la taille demandée. La taille
     * <b>maximale</b> est fixée elle aussi : c'est elle qui empêchait la zone de s'étaler sur toute
     * la fenêtre.</p>
     */
    private static Region zone(Pos position, boolean etireLargeur, boolean etireHauteur,
                               Cursor curseur, Stage stage, Scene scene, StackPane calque,
                               double largeur, double hauteur) {
        Region zone = new Region();
        zone.setCursor(curseur);
        zone.setPickOnBounds(true);
        zone.setStyle("-fx-background-color: transparent;");
        zone.setMinSize(largeur, hauteur);
        zone.setPrefSize(largeur, hauteur);
        zone.setMaxSize(largeur, hauteur);   // sans cela, la zone remplissait toute la fenêtre
        if (etireLargeur) {
            zone.prefWidthProperty().bind(scene.widthProperty());
            zone.maxWidthProperty().bind(scene.widthProperty());
        }
        if (etireHauteur) {
            zone.prefHeightProperty().bind(scene.heightProperty());
            zone.maxHeightProperty().bind(scene.heightProperty());
        }
        StackPane.setAlignment(zone, position);

        final double[] origine = new double[6];
        zone.addEventHandler(MouseEvent.MOUSE_PRESSED, e -> {
            origine[0] = e.getScreenX();
            origine[1] = e.getScreenY();
            origine[2] = stage.getWidth();
            origine[3] = stage.getHeight();
            origine[4] = stage.getX();
            origine[5] = stage.getY();
        });
        zone.addEventHandler(MouseEvent.MOUSE_DRAGGED, e -> {
            if (stage.isMaximized()) {
                return;
            }
            Rectangle2D ecran = ecranDe(stage);
            double dx = e.getScreenX() - origine[0];
            double dy = e.getScreenY() - origine[1];
            double largeurMin = Math.min(900, ecran.getWidth());
            double hauteurMin = Math.min(600, ecran.getHeight());

            boolean bordDroit = curseur == Cursor.E_RESIZE || curseur == Cursor.SE_RESIZE
                    || curseur == Cursor.NE_RESIZE;
            boolean bordGauche = curseur == Cursor.W_RESIZE || curseur == Cursor.SW_RESIZE
                    || curseur == Cursor.NW_RESIZE;
            boolean bordBas = curseur == Cursor.S_RESIZE || curseur == Cursor.SE_RESIZE
                    || curseur == Cursor.SW_RESIZE;
            boolean bordHaut = curseur == Cursor.N_RESIZE || curseur == Cursor.NW_RESIZE
                    || curseur == Cursor.NE_RESIZE;

            if (bordDroit) {
                stage.setWidth(Math.max(largeurMin, Math.min(ecran.getWidth(), origine[2] + dx)));
            }
            if (bordBas) {
                stage.setHeight(Math.max(hauteurMin, Math.min(ecran.getHeight(), origine[3] + dy)));
            }
            if (bordGauche) {
                double nouvelle = Math.max(largeurMin, Math.min(ecran.getWidth(), origine[2] - dx));
                stage.setX(origine[4] + (origine[2] - nouvelle));
                stage.setWidth(nouvelle);
            }
            if (bordHaut) {
                double nouvelle = Math.max(hauteurMin, Math.min(ecran.getHeight(), origine[3] - dy));
                stage.setY(origine[5] + (origine[3] - nouvelle));
                stage.setHeight(nouvelle);
            }

            // La fenêtre ne sort jamais de l'écran : on ne redimensionne pas pour la perdre.
            if (stage.getX() < ecran.getMinX()) {
                stage.setX(ecran.getMinX());
            }
            if (stage.getY() < ecran.getMinY()) {
                stage.setY(ecran.getMinY());
            }
            if (stage.getX() + stage.getWidth() > ecran.getMaxX()) {
                stage.setX(Math.max(ecran.getMinX(), ecran.getMaxX() - stage.getWidth()));
            }
            if (stage.getY() + stage.getHeight() > ecran.getMaxY()) {
                stage.setY(Math.max(ecran.getMinY(), ecran.getMaxY() - stage.getHeight()));
            }
        });

        // Trace de diagnostic (-Dihm.trace.souris=1) : dit quelle zone reçoit la souris.
        if (Boolean.getBoolean("ihm.trace.souris")) {
            zone.addEventHandler(MouseEvent.MOUSE_MOVED, e -> System.out.println(
                    "[souris] zone de bord " + String.valueOf(curseur) + " à "
                            + (int) e.getSceneX() + "," + (int) e.getSceneY()));
        }
        return zone;
    }

    /** Le nœud réellement sous le curseur, pour les traces de diagnostic. */
    public static String decrire(Node noeud) {
        if (noeud == null) {
            return "rien";
        }
        String curseur = noeud.getCursor() == null ? "-" : String.valueOf(noeud.getCursor());
        String texte = "";
        if (noeud instanceof javafx.scene.control.Labeled etiquete) {
            texte = " « " + etiquete.getText() + " »";
        } else if (noeud instanceof javafx.scene.text.Text t) {
            texte = " « " + t.getText() + " »";
        }
        return noeud.getClass().getSimpleName() + texte + " curseur=" + curseur;
    }
}
