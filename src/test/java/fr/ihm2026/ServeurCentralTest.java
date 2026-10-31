package fr.ihm2026;

import fr.ihm2026.reseau.ClientReseau;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ServeurCentralTest {
    @Test
    void adresseExpliciteNeDependPasDeLaDecouverteUdp() {
        // Aucune machine ne doit écouter pour que ce choix réussisse.
        assertEquals("192.168.1.10", ClientReseau.choisirHote(" 192.168.1.10 ", 7777));
        assertEquals("serveur-salle", ClientReseau.choisirHote("serveur-salle", 7777));
    }

    @Test
    void refuseLesUrlsEtPortsDansLeChampHote() {
        for (String adresse : new String[]{"https://192.168.1.10", "192.168.1.10:7777",
                "admin@serveur", "serveur/chemin", "serveur#fragment", "serveur?query"}) {
            assertThrows(ClientReseau.ErreurReseau.class,
                    () -> ClientReseau.choisirHote(adresse, 7777), adresse);
        }
    }
}
