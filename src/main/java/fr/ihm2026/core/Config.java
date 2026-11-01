package fr.ihm2026.core;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

/** Constantes de configuration du client. */
public final class Config {

    private Config() {
    }

    public static final String APP_NAME = "Plateforme d'apprentissage";
    public static final String APP_SUB = "Java · Python · C++ — progression par niveaux";
    /**
     * Version affichée à l'écran. Elle n'est <strong>pas</strong> recopiée ici : elle vient du
     * {@code <version>} du {@code pom.xml}, filtré au build dans {@code fr/ihm2026/version.properties}.
     * Une constante en dur finissait par mentir (l'écran affichait 0.1.0 pour la version 0.1.3).
     */
    public static final String VERSION = chargerVersion();

    /**
     * true : données de démonstration pour la couche REST (aucun serveur nécessaire).
     * false : appels réels à l'API REST de la plateforme.
     *
     * <p>Le choix du fournisseur IA est séparé : voir {@link #MODELE}.</p>
     */
    public static final boolean USE_MOCK = true;

    /** Base de l'API REST (surchargeable par -Dihm.api=...). */
    public static final String API_BASE =
            System.getProperty("ihm.api", "http://localhost:8080/api");

    /**
     * Clé du fournisseur de modèle (étape 4). Elle n'est <strong>jamais</strong> journalisée ni
     * affichée ; son absence est traitée comme une indisponibilité décidée avant tout appel.
     * Renseignée par la variable d'environnement {@code IHM_API_KEY} ou {@code -Dihm.cle=...}.
     */
    public static final String CLE_API = chargerCle();

    private static String chargerCle() {
        String propriete = System.getProperty("ihm.cle");
        if (propriete != null && !propriete.isBlank()) {
            return propriete.strip();
        }
        String environnement = System.getenv("IHM_API_KEY");
        return environnement == null ? "" : environnement.strip();
    }

    /** IA désactivée par défaut ; ancien connecteur distant sur choix explicite uniquement. */
    public static final String MODELE = System.getProperty("ihm.modele", "desactive").toLowerCase();

    public static final int WINDOW_W = 1280;
    public static final int WINDOW_H = 800;
    public static final int WINDOW_MIN_W = 1100;
    public static final int WINDOW_MIN_H = 700;

    /**
     * Taille de la fenêtre pour un écran donné (v0.14.1). La fenêtre était ouverte en 1280 x 800
     * quelle que soit la machine : sur un portable 1366 x 768 en mise à l'échelle 125 % (zone utile
     * 1092 x 614), elle dépassait l'écran — barre latérale hors champ à gauche, carte de connexion
     * hors champ à droite, bouton « Se connecter » inatteignable. Retourne [largeur, hauteur].
     */
    public static int[] tailleFenetre(double largeurEcran, double hauteurEcran) {
        int largeur = (int) Math.min(WINDOW_W, Math.max(720, largeurEcran * 0.92));
        int hauteur = (int) Math.min(WINDOW_H, Math.max(520, hauteurEcran * 0.90));
        return new int[]{largeur, hauteur};
    }

    /** Minimum de fenêtre aligné sur la même borne, sinon Windows force la fenêtre hors écran. */
    public static int[] tailleMinFenetre(double largeurEcran, double hauteurEcran) {
        int[] taille = tailleFenetre(largeurEcran, hauteurEcran);
        return new int[]{Math.min(WINDOW_MIN_W, taille[0]), Math.min(WINDOW_MIN_H, taille[1])};
    }

    private static String chargerVersion() {
        try (InputStream in = Config.class.getResourceAsStream("/fr/ihm2026/version.properties")) {
            if (in != null) {
                Properties p = new Properties();
                p.load(new InputStreamReader(in, StandardCharsets.UTF_8));
                String v = p.getProperty("version");
                if (v != null && !v.contains("${")) {
                    return v.trim();
                }
            }
        } catch (Exception ignore) {
            // Absence du fichier : la version de repli ci-dessous suffit à démarrer.
        }
        return "dev";
    }
}
