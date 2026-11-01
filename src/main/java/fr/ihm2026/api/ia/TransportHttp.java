package fr.ihm2026.api.ia;

import fr.ihm2026.core.Config;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Seul point de sortie réseau du projet — étape 4.
 *
 * <p>Cette classe est la seule du logiciel à ouvrir une connexion. Un test lit les sources pour
 * le vérifier : la règle « aucun appel au fournisseur hors de cette couche » n'est donc pas une
 * consigne de relecture, c'est une propriété testée. Elle découle de la décision de stratégie :
 * le fournisseur est encapsulé, donc remplaçable.</p>
 *
 * <p>La clé d'API n'est jamais journalisée ni affichée ; son absence est traitée comme une
 * indisponibilité, décidée <em>avant</em> tout appel.</p>
 */
public final class TransportHttp implements TransportModele {

    private final HttpClient client;
    private final String cle;

    public TransportHttp() {
        this(Config.CLE_API);
    }

    public TransportHttp(String cle) {
        this.cle = cle == null ? "" : cle.strip();
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(1500))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public boolean clePresente() {
        return !cle.isEmpty();
    }

    @Override
    public Reponse poster(String url, String corpsJson, Duration delai) throws Exception {
        if (!clePresente()) {
            throw new IllegalStateException("aucune clé d'API : appel non tenté");
        }
        HttpRequest requete = HttpRequest.newBuilder(URI.create(url))
                .timeout(delai)
                .header("Content-Type", "application/json; charset=utf-8")
                .header("Accept", "application/json")
                .header("Authorization", "Bearer " + cle)
                .POST(HttpRequest.BodyPublishers.ofString(corpsJson, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> reponse = client.send(requete,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (reponse.body().length() > 256 * 1024) throw new java.io.IOException("Réponse fournisseur trop volumineuse");
        return new Reponse(reponse.statusCode(), reponse.body());
    }
}
