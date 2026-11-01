package fr.ihm2026.reseau;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

class DecouverteServeursTest {
    @Test void annoncesValidees() {
        var serveur = DecouverteServeurs.lireAnnonce("IHM2026:SERVER:2:Salle principale", "192.168.1.20");
        assertNotNull(serveur);
        assertEquals("Salle principale", serveur.nom());
        assertEquals("192.168.1.20", serveur.hote());
        assertNull(DecouverteServeurs.lireAnnonce("IHM2026:OK", "127.0.0.1"));
        assertNull(DecouverteServeurs.lireAnnonce("IHM2026:SERVER:2: ", "127.0.0.1"));
        assertNull(DecouverteServeurs.lireAnnonce("IHM2026:SERVER:2:nom\ncache", "127.0.0.1"));
        assertNull(DecouverteServeurs.lireAnnonce("IHM2026:SERVER:2:" + "x".repeat(81), "127.0.0.1"));
    }

    @Test void serveurRepondAuxDeuxVersions() throws Exception {
        try (ServeurLocal serveur = new ServeurLocal(0, "test"); DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2000);
            assertEquals("IHM2026:OK", demander(client, serveur.port(), "IHM2026:DISCOVER"));
            assertEquals("IHM2026:SERVER:2:" + DecouverteServeurs.nomLocal(),
                    demander(client, serveur.port(), "IHM2026:DISCOVER:2"));
        }
    }

    private static String demander(DatagramSocket socket, int port, String demande) throws Exception {
        byte[] octets = demande.getBytes(StandardCharsets.UTF_8);
        socket.send(new DatagramPacket(octets, octets.length, InetAddress.getLoopbackAddress(), port));
        DatagramPacket reponse = new DatagramPacket(new byte[512], 512);
        socket.receive(reponse);
        return new String(reponse.getData(), reponse.getOffset(), reponse.getLength(), StandardCharsets.UTF_8);
    }
}
