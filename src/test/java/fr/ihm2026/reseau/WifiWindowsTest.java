package fr.ihm2026.reseau;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;

class WifiWindowsTest {
    @Test void listeSsidSansBssidNiDoublons() {
        String sortie = "SSID 1 : École\r\n    BSSID 1 : aa:bb:cc\r\nSSID 2 : Salle 2\r\nSSID 3 : École\r\nSSID 4 : \r\n";
        assertEquals(List.of("École", "Salle 2"), WifiWindows.ssids(sortie, true));
    }
    @Test void connexionIndependanteDeLaLangueWindows() {
        String sortie = "État : connecté\n    SSID : Réseau école\n    BSSID : aa:bb\n";
        assertEquals(List.of("Réseau école"), WifiWindows.ssids(sortie, false));
        assertEquals(List.of(), WifiWindows.ssids("State : disconnected", false));
    }
    @Test void caracteresNeSontJamaisDesCommandes() {
        assertEquals(List.of("L'école & $(test)"), WifiWindows.ssids("SSID 1 : L'école & $(test)", true));
    }
}
