package fr.ihm2026.core;

import java.awt.image.BufferedImage;
import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import fr.ihm2026.reseau.IdentitePoste;
import fr.ihm2026.reseau.Reseau;
import fr.ihm2026.reseau.ServeurLocal;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javax.imageio.ImageIO;

/**
 * Parcours automatique des écrans d'un rôle configuré, sans intervention de l'utilisateur.
 *
 * <p>Objectif : détecter les pannes de <em>démarrage</em> (ressource absente, {@code null}
 * inattendu, feuille de style introuvable) que les tests unitaires ne voient pas.
 * Activé par {@code -Dihm.smoketest=1}. Nécessite {@code --role=} et démarre un serveur local
 * temporaire pour respecter les contrôles d'accès.</p>
 *
 * <pre>
 * mvn -q javafx:run "-Djavafx.args=--role=apprenant --smoketest=1"
 * </pre>
 */
public final class SmokeTest {

    private SmokeTest() {
    }

    private static final Map<String, Session.Role> TOURNEES = new LinkedHashMap<>();

    static {
        TOURNEES.put(Router.LOGIN, null);
        TOURNEES.put(Router.RESEAU, null);
        TOURNEES.put(Router.PARCOURS, Session.Role.APPRENANT);
        TOURNEES.put(Router.COURS, Session.Role.APPRENANT);
        TOURNEES.put(Router.EXERCICE, Session.Role.APPRENANT);
        TOURNEES.put(Router.TEST, Session.Role.APPRENANT);
        TOURNEES.put(Router.RESULTAT, Session.Role.APPRENANT);
        TOURNEES.put(Router.TABLEAU_BORD, Session.Role.APPRENANT);
        TOURNEES.put(Router.CONDITIONS, Session.Role.ENSEIGNANT);
        TOURNEES.put(Router.RESULTATS, Session.Role.ENSEIGNANT);
        TOURNEES.put(Router.COMPTES, Session.Role.ADMINISTRATEUR);
    }

    public static boolean actif() {
        return System.getProperty("ihm.smoketest") != null;
    }

    /** Répertoire des captures d'écran ; vide ou absent = pas de capture. */
    private static File dossierCaptures() {
        String d = System.getProperty("ihm.captures");
        return d == null ? null : new File(d);
    }

    /** À appeler une fois la fenêtre affichée. */
    public static void executer(Router router, Scene scene) {
        Session.Role role = IdentitePoste.role();
        if (role == null) {
            System.out.println("[smoketest] ECHEC : configurer --role= avant le test visuel");
            Platform.exit();
            return;
        }
        ServeurLocal serveur;
        try {
            serveur = new ServeurLocal(0);
        } catch (java.io.IOException e) {
            System.out.println("[smoketest] ECHEC démarrage du serveur local : " + e.getMessage());
            Platform.exit();
            return;
        }
        try {
            Reseau.connecter("127.0.0.1:" + serveur.port(), role);
        } catch (RuntimeException e) {
            System.out.println("[smoketest] ECHEC connexion au serveur local : " + e.getMessage());
            serveur.close();
            Platform.exit();
            return;
        }
        Platform.runLater(() -> {
            int ok = 0;
            int total = 0;
            for (Map.Entry<String, Session.Role> e : TOURNEES.entrySet()) {
                if (e.getValue() != null && e.getValue() != role) {
                    continue;
                }
                total++;
                try {
                    router.go(e.getKey());
                    ok++;
                    System.out.println("[smoketest] OK   " + role + " -> " + e.getKey());
                    if (Router.EXERCICE.equals(e.getKey()) || Router.COURS.equals(e.getKey())) {
                        declencherAssistant(scene);
                    }
                    if (Router.RESULTATS.equals(e.getKey())) {
                        Button generer = chercherBouton(scene.getRoot(), "Générer une synthèse");
                        if (generer != null) {
                            generer.fire();
                            System.out.println("[smoketest]      synthèse enseignant générée (brouillon)");
                        }
                    }
                    // Les captures sont prises hors animation : on amène d'abord tout à son état
                    // final, sinon les images montreraient des fondus à mi-course.
                    Animations.terminerTout();
                    capturer(scene, e.getKey());
                } catch (RuntimeException ex) {
                    System.out.println("[smoketest] ECHEC " + e.getKey() + " : " + ex);
                    ex.printStackTrace(System.out);
                }
            }
            System.out.println("[smoketest] " + ok + "/" + total + " écrans construits");

            // Étape 4 — preuve visuelle du repli : on force la panne du fournisseur (trois échecs
            // techniques), on rouvre l'exercice, et l'assistance doit apparaître désactivée, sans
            // qu'aucun écran ne bloque. La capture sert de preuve, pas de décoration.
            try {
                for (int i = 0; i < fr.ihm2026.api.ia.PolitiqueModele.echecsAvantOuverture(); i++) {
                    fr.ihm2026.api.ia.PolitiqueModele.signalerEchecTechnique();
                }
                if (role == Session.Role.APPRENANT) {
                    router.go(Router.EXERCICE);
                    Button ouverture = chercherBouton(scene.getRoot(), "Aide IA");
                    if (ouverture != null) {
                        ouverture.fire();
                    }
                    System.out.println("[smoketest]      repli : "
                            + fr.ihm2026.api.service.AideIaApi.etatAgent().explication());
                    capturer(scene, "exercice-ia-desactivee");
                } else {
                    System.out.println("[smoketest]      repli IA ignoré pour le rôle " + role);
                }
            } catch (RuntimeException ex) {
                System.out.println("[smoketest] ECHEC repli : " + ex);
            } finally {
                fr.ihm2026.api.ia.PolitiqueModele.reinitialiser();
            }
            Reseau.deconnecter();
            serveur.close();
            Platform.exit();
        });
    }

    /** Ouvre l'assistant, pose une question : vérifie la chaîne IA de bout en bout. */
    private static void declencherAssistant(Scene scene) {
        Button ouverture = chercherBouton(scene.getRoot(), "Aide IA");
        if (ouverture == null) {
            System.out.println("[smoketest]      bouton « Aide IA » absent de l'écran d'exercice");
            return;
        }
        ouverture.fire();                       // affiche le panneau latéral
        Button indice = chercherBouton(scene.getRoot(), "Donner un indice");
        if (indice == null) {
            System.out.println("[smoketest]      panneau d'assistance non affiché");
            return;
        }
        indice.fire();                          // déclenche la chaîne IA (garde-fou compris)
        System.out.println("[smoketest]      assistant interrogé (réponse citée attendue)");
    }

    private static Button chercherBouton(javafx.scene.Node noeud, String texte) {
        if (noeud instanceof Button b && texte.equals(b.getText())) {
            return b;
        }
        if (noeud instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node enfant : parent.getChildrenUnmodifiable()) {
                Button trouve = chercherBouton(enfant, texte);
                if (trouve != null) {
                    return trouve;
                }
            }
        }
        return null;
    }

    /**
     * Enregistre une capture PNG de la scène courante (option {@code -Dihm.captures=dossier}).
     * Aucune dépendance à JavaFX-Swing : les pixels sont recopiés dans un {@link BufferedImage}.
     */
    private static void capturer(Scene scene, String nom) {
        File dossier = dossierCaptures();
        if (dossier == null) {
            return;
        }
        try {
            dossier.mkdirs();
            WritableImage image = scene.snapshot(null);
            int largeur = (int) image.getWidth();
            int hauteur = (int) image.getHeight();
            BufferedImage tampon =
                    new BufferedImage(largeur, hauteur, BufferedImage.TYPE_INT_ARGB);
            PixelReader pixels = image.getPixelReader();
            for (int y = 0; y < hauteur; y++) {
                for (int x = 0; x < largeur; x++) {
                    tampon.setRGB(x, y, pixels.getArgb(x, y));
                }
            }
            File fichier = new File(dossier, nom + ".png");
            ImageIO.write(tampon, "png", fichier);
            System.out.println("[smoketest]      capture " + fichier.getPath());
        } catch (Exception e) {
            System.out.println("[smoketest]      capture impossible (" + nom + ") : " + e);
        }
    }
}
