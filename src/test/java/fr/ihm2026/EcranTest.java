package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Config;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * La fenêtre doit tenir sur l'écran du poste (v0.14.1).
 *
 * <p>Défaut constaté en salle : sur un portable 1366 x 768 réglé en 125 %, la zone utile ne fait
 * que 1092 x 614 pixels logiques. Ouverte en 1280 x 800, la fenêtre dépassait l'écran — barre
 * latérale hors champ à gauche, carte « Connexion au serveur » hors champ à droite, bouton
 * « Se connecter » inatteignable.</p>
 */
class EcranTest {

    @Test
    @DisplayName("Sur un petit portable (1092 x 614 utiles), la fenêtre tient entièrement")
    void petitPortable() {
        int[] taille = Config.tailleFenetre(1092, 614);
        assertTrue(taille[0] <= 1092, "Largeur " + taille[0] + " > écran 1092");
        assertTrue(taille[1] <= 614, "Hauteur " + taille[1] + " > écran 614");
        assertTrue(taille[0] >= 900, "Réduction excessive : " + taille[0]);
        assertTrue(taille[1] >= 520, "Hauteur trop faible : " + taille[1]);
    }

    @Test
    @DisplayName("La taille minimale ne dépasse jamais l'écran")
    void minimumTientSurLEcran() {
        int[] minimum = Config.tailleMinFenetre(1092, 614);
        assertTrue(minimum[0] <= 1092, "Minimum de largeur " + minimum[0] + " > écran");
        assertTrue(minimum[1] <= 614, "Minimum de hauteur " + minimum[1] + " > écran");
        assertTrue(minimum[0] < Config.WINDOW_MIN_W, "Le minimum de 1100 px doit être abaissé");
    }

    @Test
    @DisplayName("Sur un grand écran, rien ne change : 1280 x 800")
    void grandEcranInchange() {
        int[] taille = Config.tailleFenetre(2560, 1400);
        assertEquals(Config.WINDOW_W, taille[0]);
        assertEquals(Config.WINDOW_H, taille[1]);
        int[] minimum = Config.tailleMinFenetre(2560, 1400);
        assertEquals(Config.WINDOW_MIN_W, minimum[0]);
        assertEquals(Config.WINDOW_MIN_H, minimum[1]);
    }

    @Test
    @DisplayName("Un écran minuscule ne produit jamais une fenêtre inutilisable")
    void ecranMinuscule() {
        int[] taille = Config.tailleFenetre(800, 600);
        assertTrue(taille[0] >= 720 && taille[1] >= 520,
                "Plancher de lisibilité non tenu : " + taille[0] + " x " + taille[1]);
    }
}
