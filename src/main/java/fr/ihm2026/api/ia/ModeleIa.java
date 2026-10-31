package fr.ihm2026.api.ia;

/**
 * Interface unique d'accès au modèle (étape 4 : décision encapsulée).
 *
 * <p>Changer de fournisseur, de modèle ou passer à un modèle auto-hébergé ne touche que
 * l'implémentation. Le service, les garde-fous et l'interface graphique restent identiques.</p>
 */
public interface ModeleIa {

    /** Identifiant du modèle et version de prompt, journalisés à chaque appel. */
    String identifiant();

    /**
     * Produit une réponse citée, ou un refus motivé. Ne lève jamais d'exception réseau :
     * l'indisponibilité est un {@link MotifRefus#INDISPONIBLE}.
     */
    ReponseIa repondre(ContexteIa contexte);
}
