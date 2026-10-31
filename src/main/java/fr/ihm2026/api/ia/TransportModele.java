package fr.ihm2026.api.ia;

import java.time.Duration;

/**
 * Transport HTTP du modèle distant — étape 4, décision de stratégie.
 *
 * <p>Cette interface existe pour une seule raison : rendre la panne testable. Un test peut
 * injecter un transport qui répond vite, qui répond lentement, ou qui échoue, sans réseau et
 * sans clé d'API. Sans elle, la politique de repli ne serait vérifiable qu'en débranchant un
 * vrai fournisseur — c'est-à-dire jamais au bon moment.</p>
 */
@FunctionalInterface
public interface TransportModele {

    /** Réponse brute : le code HTTP et le corps, tels que reçus. */
    record Reponse(int code, String corps) {
    }

    Reponse poster(String url, String corpsJson, Duration delai) throws Exception;
}
