import fr.ihm2026.reseau.ServeurLocal;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.stage.Window;
import javax.imageio.ImageIO;

/**
 * Sonde du parcours d'un poste (v0.13.2) : prouve, sans intervention humaine, le geste décrit
 * dans la notice — « je clique sur le .cmd du poste, je saisis l'adresse, j'obtiens mon interface ».
 *
 * <p>Ce que fait la sonde, dans l'ordre :</p>
 * <ol>
 *   <li>démarre un serveur local sur un port de test ;</li>
 *   <li>lance l'application avec un rôle de poste (PC1 administrateur) ;</li>
 *   <li>remplit le champ d'adresse de l'écran « Connexion au serveur » et appuie sur
 *       « Se connecter », exactement comme un utilisateur ;</li>
 *   <li>attend, puis lit le titre de la fenêtre et enregistre une capture.</li>
 * </ol>
 *
 * <pre>
 * export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
 * ./mvnw -B -o -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
 * xvfb-run -a java -cp "target/classes:$(cat /tmp/cp.txt)" outils/sonde-poste.java
 * </pre>
 *
 * <p>Sortie observée :</p>
 * <pre>
 * [sonde] serveur de test prêt sur 7799
 * [sonde] écran d'entrée : « Connexion au serveur — Plateforme d'apprentissage »
 * [sonde] adresse saisie : 127.0.0.1:7799 → clic sur « Se connecter »
 * [sonde] écran obtenu  : « Comptes et rôles — Plateforme d'apprentissage »
 * [sonde] capture        : /tmp/poste-pc1.png
 * </pre>
 */
public class SondePoste {

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getProperty("sonde.port", "7799"));
        String role = System.getProperty("sonde.role", "administrateur");
        String poste = System.getProperty("sonde.poste", "PC1");
        String capture = System.getProperty("sonde.capture", "/tmp/poste-" + poste + ".png");
        String captureAvant = System.getProperty("sonde.capture.avant", "");
        // -Dsonde.sansclic=true : la sonde n'écrit ni ne clique ; elle vérifie seulement que le
        // poste rejoint la salle tout seul (connexion automatique, v0.14.2).
        boolean sansClic = Boolean.parseBoolean(System.getProperty("sonde.sansclic", "false"));
        String ecranCible = System.getProperty("sonde.ecran", "");   // libellé dans la barre latérale
        boolean publier = Boolean.parseBoolean(System.getProperty("sonde.publier", "false"));

        // Si un serveur écoute déjà sur ce port (cas d'une démonstration à plusieurs postes),
        // la sonde s'y connecte au lieu d'en démarrer un second : c'est le poste que l'on teste,
        // pas le serveur.
        ServeurLocal serveur = null;
        try {
            serveur = new ServeurLocal(port);
            System.out.println("[sonde] serveur de test prêt sur " + port);
        } catch (java.io.IOException dejaLa) {
            System.out.println("[sonde] un serveur écoute déjà sur " + port + " : on l'utilise");
        }

        Thread application = new Thread(() -> fr.ihm2026.Launcher.main(new String[]{
                "--role=" + role, "--poste=" + poste,
                "--serveur=127.0.0.1", "--port=" + port}), "application");
        application.setDaemon(true);
        application.start();

        Stage fenetre = attendreFenetre();
        javafx.geometry.Rectangle2D ecran =
                javafx.stage.Screen.getPrimary().getVisualBounds();
        System.out.println("[sonde] écran utile   : " + (int) ecran.getWidth() + " × "
                + (int) ecran.getHeight());
        System.out.println("[sonde] fenêtre       : " + (int) fenetre.getWidth() + " × "
                + (int) fenetre.getHeight() + "  (x=" + (int) fenetre.getX()
                + ", y=" + (int) fenetre.getY() + ")");
        System.out.println("[sonde] écran d'entrée : « " + fenetre.getTitle() + " »");

        if (!captureAvant.isEmpty()) {
            Platform.runLater(() -> {
                fr.ihm2026.core.Animations.terminerTout();
                enregistrer(fenetre, captureAvant);
            });
            Thread.sleep(600);
        }

        if (sansClic) {
            Thread.sleep(2500);
            System.out.println("[sonde] aucune saisie, aucun clic");
            System.out.println("[sonde] écran obtenu  : « " + fenetre.getTitle() + " »");
            Platform.runLater(() -> {
                fr.ihm2026.core.Animations.terminerTout();
                enregistrer(fenetre, capture);
                Platform.exit();
            });
            Thread.sleep(1500);
            if (serveur != null) {
                serveur.close();
            }
            System.exit(0);
            return;
        }

        AtomicReference<String> adresse = new AtomicReference<>();
        Platform.runLater(() -> {
            Scene scene = fenetre.getScene();
            TextField champ = (TextField) scene.lookup(".champ-adresse");
            Button bouton = (Button) scene.lookup(".btn-primary");
            if (champ == null || bouton == null) {
                System.out.println("[sonde] ECHEC : champ ou bouton introuvable");
                Platform.exit();
                return;
            }
            adresse.set("127.0.0.1:" + port);
            champ.setText(adresse.get());
            bouton.fire();          // même geste qu'un clic sur « Se connecter »
        });

        Thread.sleep(1500);         // la connexion se fait hors du fil d'affichage
        System.out.println("[sonde] adresse saisie : " + adresse.get()
                + " → clic sur « Se connecter »");
        System.out.println("[sonde] écran obtenu  : « " + fenetre.getTitle() + " »");

        if (publier) {
            // Le poste apprenant publie une note, comme le fait l'écran de test.
            String message = fr.ihm2026.reseau.Reseau.publier(
                    new fr.ihm2026.model.ResultatVM(14, 12, java.util.List.of(),
                            java.util.List.of(), 1),
                    fr.ihm2026.core.Session.Langage.PYTHON, 3);
            System.out.println("[sonde] publication    : " + message);
        }

        if (!ecranCible.isEmpty()) {
            AtomicReference<Boolean> trouve = new AtomicReference<>(false);
            Platform.runLater(() -> {
                trouve.set(cliquer(fenetre.getScene().getRoot(), ecranCible));
                System.out.println(trouve.get()
                        ? "[sonde] navigation    : « " + ecranCible + " » → « " + fenetre.getTitle() + " »"
                        : "[sonde] ECHEC : entrée « " + ecranCible + " » introuvable");
            });
            Thread.sleep(1200);
        }

        Platform.runLater(() -> {
            System.out.println("[sonde] capture en cours…");
            System.out.flush();
            fr.ihm2026.core.Animations.terminerTout();
            enregistrer(fenetre, capture);
            if (true) {
                return;
            }
            try {
                javafx.scene.image.WritableImage image = fenetre.getScene().snapshot(null);
                int largeur = (int) image.getWidth();
                int hauteur = (int) image.getHeight();
                java.awt.image.BufferedImage tampon = new java.awt.image.BufferedImage(
                        largeur, hauteur, java.awt.image.BufferedImage.TYPE_INT_ARGB);
                javafx.scene.image.PixelReader pixels = image.getPixelReader();
                for (int y = 0; y < hauteur; y++) {
                    for (int x = 0; x < largeur; x++) {
                        tampon.setRGB(x, y, pixels.getArgb(x, y));
                    }
                }
                ImageIO.write(tampon, "png", new File(capture));
                System.out.println("[sonde] capture        : " + capture);
            } catch (Exception e) {
                System.out.println("[sonde] capture impossible : " + e);
            }
            Platform.exit();
        });
        if (serveur != null) {
            serveur.close();
        }
        Thread.sleep(3000);
        System.out.flush();
        Platform.exit();
        Thread.sleep(200);
        System.exit(0);
    }

    /**
     * Clique l'élément dont le libellé est demandé. Deux formes coexistent dans l'interface : les
     * boutons (un {@code Button} à activer) et les lignes de la barre latérale, qui sont des
     * {@code HBox} portant un libellé et un gestionnaire de clic — il faut donc simuler le clic.
     */
    private static boolean cliquer(javafx.scene.Node noeud, String libelle) {
        if (noeud instanceof Button b && b.getText() != null
                && normaliser(b.getText()).contains(normaliser(libelle))) {
            b.fire();
            return true;
        }
        if (noeud instanceof javafx.scene.Parent parent) {
            if (parent.getOnMouseClicked() != null) {
                for (javafx.scene.Node enfant : parent.getChildrenUnmodifiable()) {
                    if (enfant instanceof javafx.scene.control.Label label
                            && label.getText() != null
                            && normaliser(label.getText()).contains(normaliser(libelle))) {
                        parent.getOnMouseClicked().handle(new javafx.scene.input.MouseEvent(
                                javafx.scene.input.MouseEvent.MOUSE_CLICKED, 0, 0, 0, 0,
                                javafx.scene.input.MouseButton.PRIMARY, 1,
                                false, false, false, false, true, false, false, true, false,
                                false, null));
                        return true;
                    }
                }
            }
            for (javafx.scene.Node enfant : parent.getChildrenUnmodifiable()) {
                if (cliquer(enfant, libelle)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Compare sans accent ni casse : un libellé passé en ligne de commande peut avoir perdu ses
     * accents selon l'encodage de la console, et « Resultats » doit retrouver « Résultats ».
     */
    private static String normaliser(String texte) {
        String sansAccent = java.text.Normalizer.normalize(texte, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return sansAccent.toLowerCase(java.util.Locale.ROOT);
    }

    /** Enregistre une capture de la fenêtre (chemin vide = rien à faire). */
    private static void enregistrer(Stage fenetre, String chemin) {
        if (chemin == null || chemin.isEmpty()) {
            return;
        }
        javafx.scene.image.WritableImage image = fenetre.getScene().snapshot(null);
        int largeur = (int) image.getWidth();
        int hauteur = (int) image.getHeight();
        java.awt.image.BufferedImage tampon = new java.awt.image.BufferedImage(
                largeur, hauteur, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        javafx.scene.image.PixelReader pixels = image.getPixelReader();
        for (int y = 0; y < hauteur; y++) {
            for (int x = 0; x < largeur; x++) {
                tampon.setRGB(x, y, pixels.getArgb(x, y));
            }
        }
        try {
            ImageIO.write(tampon, "png", new File(chemin));
            System.out.println("[sonde] capture        : " + chemin);
        } catch (Exception e) {
            System.out.println("[sonde] capture impossible (" + chemin + ") : " + e);
        }
    }

    private static Stage attendreFenetre() throws InterruptedException {
        for (int i = 0; i < 200; i++) {
            for (Window w : Window.getWindows()) {
                if (w instanceof Stage s && s.getScene() != null) {
                    return s;
                }
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("aucune fenêtre ouverte");
    }
}
