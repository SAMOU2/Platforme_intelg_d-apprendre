package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DelaisAuthentificationHttpsTest {
    @Test
    void clientUtiliseUnDelaiDeConnexionPlusLongMaisBorne() {
        assertEquals(Duration.ofSeconds(30),
                ClientAuthentificationHttps.construireClient().connectTimeout().orElseThrow());
        assertTrue(ClientAuthentificationHttps.DELAI_REQUETE_AUTH
                .compareTo(ClientAuthentificationHttps.DELAI_CONNEXION) > 0,
                "Les routes d'authentification doivent laisser le temps de se connecter et de répondre");
    }
}
