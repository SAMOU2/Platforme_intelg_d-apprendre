package fr.ihm2026.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import fr.ihm2026.core.Config;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Seul point de contact avec le serveur (architecture : ui → service → ApiClient).
 * v0.1 : les services utilisent encore les données simulées.
 */
public final class ApiClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private ApiClient() {
    }

    public static ObjectMapper mapper() {
        return MAPPER;
    }

    public static <T> T get(String path, Class<T> type) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(Config.API_BASE + path))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/json")
                .GET()
                .build();
        return send(request, type);
    }

    public static <T> T post(String path, Object body, Class<T> type) {
        try {
            String json = MAPPER.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder(URI.create(Config.API_BASE + path))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            return send(request, type);
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException("Impossible de préparer la requête : " + e.getMessage(), e);
        }
    }

    private static <T> T send(HttpRequest request, Class<T> type) {
        try {
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() >= 400) {
                throw new ApiException(response.statusCode(),
                        "Le serveur a refusé la demande (" + response.statusCode() + ").");
            }
            return MAPPER.readValue(response.body(), type);
        } catch (ApiException e) {
            throw e;
        } catch (java.net.http.HttpConnectTimeoutException | java.net.ConnectException e) {
            throw new ApiException("Serveur indisponible : réessayez dans un instant.", e);
        } catch (Exception e) {
            throw new ApiException("Communication impossible avec le serveur : " + e.getMessage(), e);
        }
    }
}
