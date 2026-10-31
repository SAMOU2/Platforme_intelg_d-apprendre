package fr.ihm2026.api.ia;

/**
 * État de disponibilité de l'assistance — étape 4, politique de repli.
 *
 * <p>Une panne n'est pas une exception à attraper : c'est un état que l'interface affiche.
 * L'écran de cours et l'éditeur continuent de fonctionner ; seul le panneau d'assistance passe
 * en « désactivé », avec la raison en clair.</p>
 */
public record EtatAgent(boolean disponible, MotifRefus motif, String explication) {

    public static EtatAgent actif() {
        return new EtatAgent(true, MotifRefus.AUCUN, "");
    }

    public static EtatAgent desactive(MotifRefus motif, String explication) {
        return new EtatAgent(false, motif, explication);
    }
}
