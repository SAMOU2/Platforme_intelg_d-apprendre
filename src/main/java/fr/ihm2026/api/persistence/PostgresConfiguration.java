package fr.ihm2026.api.persistence;

import java.util.Map;

/** Configuration PostgreSQL lue depuis l'environnement du serveur, jamais intégrée au code. */
public final class PostgresConfiguration {

    private final String url;
    private final String utilisateur;
    private final String motDePasse;

    private PostgresConfiguration(String url, String utilisateur, String motDePasse) {
        this.url = url;
        this.utilisateur = utilisateur;
        this.motDePasse = motDePasse;
    }

    public static PostgresConfiguration depuisEnvironnement() {
        return depuis(System.getenv());
    }

    static PostgresConfiguration depuis(Map<String, String> environnement) {
        String url = obligatoire(environnement, "IHM_DB_URL");
        String utilisateur = obligatoire(environnement, "IHM_DB_USER");
        String motDePasse = obligatoire(environnement, "IHM_DB_PASSWORD", false);
        if (!url.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("IHM_DB_URL doit être une URL JDBC PostgreSQL.");
        }
        return new PostgresConfiguration(url, utilisateur, motDePasse);
    }

    String url() {
        return url;
    }

    String utilisateur() {
        return utilisateur;
    }

    String motDePasse() {
        return motDePasse;
    }

    @Override
    public String toString() {
        return "PostgresConfiguration[url=" + url + ", utilisateur=" + utilisateur
                + ", motDePasse=<masqué>]";
    }

    private static String obligatoire(Map<String, String> environnement, String cle) {
        return obligatoire(environnement, cle, true);
    }

    private static String obligatoire(Map<String, String> environnement, String cle, boolean nettoyer) {
        String valeur = environnement.get(cle);
        if (valeur == null || valeur.isBlank()) {
            throw new IllegalStateException("La variable d'environnement " + cle + " est obligatoire.");
        }
        return nettoyer ? valeur.strip() : valeur;
    }
}
