package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import fr.ihm2026.reseau.ClientReseau;
import fr.ihm2026.reseau.EtatReseau;
import fr.ihm2026.reseau.IdentitePoste;
import fr.ihm2026.reseau.Reseau;
import fr.ihm2026.reseau.ServeurLocal;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests du réseau local de la salle (v0.13.0).
 *
 * <p>Ce que l'on veut prouver, et pas seulement décrire :</p>
 * <ul>
 *   <li>le poste serveur répond, et dit quelle version il parle ;</li>
 *   <li>un poste qui s'annonce est vu par les autres postes ;</li>
 *   <li>un rôle inconnu est <b>refusé</b> (un poste mal configuré doit le voir) ;</li>
 *   <li>une note publiée par le poste apprenant arrive sur le poste enseignant ;</li>
 *   <li>le serveur ne recopie pas « validé » : il le recalcule à partir du seuil ;</li>
 *   <li>sans serveur, l'erreur est un message en français, pas une trace de pile.</li>
 * </ul>
 *
 * <p>Les tests démarrent un vrai serveur sur un port éphémère ({@code 0}) et s'y connectent par
 * HTTP : ce qui est vérifié est le comportement réel, pas une doublure.</p>
 */
class ReseauTest {

    private ServeurLocal serveur;

    @AfterEach
    void arreter() {
        if (serveur != null) {
            serveur.close();
            serveur = null;
        }
        System.clearProperty("ihm.role");
        System.clearProperty("ihm.poste");
        System.clearProperty("ihm.serveur");
        System.clearProperty("ihm.port");
        Reseau.deconnecter();
    }

    private ClientReseau demarrer() throws IOException {
        serveur = new ServeurLocal(0, "0.13.0");
        return new ClientReseau("127.0.0.1", serveur.port());
    }

    @Test
    @DisplayName("Le poste serveur répond à la sonde et annonce sa version")
    void santeRepond() throws IOException {
        ClientReseau client = demarrer();
        EtatReseau.Sante sante = client.sante();
        assertEquals("ihm-2026", sante.produit());
        assertEquals("serveur", sante.role());
        assertEquals("0.13.0", sante.version());
        assertEquals(0, sante.postes());
        assertNotNull(sante.heure());
    }

    @Test
    @DisplayName("Un poste qui s'annonce apparaît dans la liste des postes connectés")
    void connexionPuisListeDesPostes() throws IOException {
        ClientReseau client = demarrer();
        EtatReseau.Reponse accueil = client.connecter("PC3", "APPRENANT");
        assertTrue(accueil.ok(), "Le serveur doit accepter un poste apprenant");
        assertTrue(accueil.message().contains("PC3"), "L'accueil doit nommer le poste : "
                + accueil.message());

        List<EtatReseau.Poste> postes = client.postes();
        assertEquals(1, postes.size());
        assertEquals("PC3", postes.get(0).poste());
        assertEquals("APPRENANT", postes.get(0).role());
        assertEquals("127.0.0.1", postes.get(0).adresse());

        // Une reconnexion remplace l'ancienne entrée au lieu de dupliquer le poste.
        client.connecter("PC3", "APPRENANT");
        assertEquals(1, client.postes().size());
    }

    @Test
    @DisplayName("Un rôle inconnu est refusé par le serveur, avec un message lisible")
    void roleInconnuRefuse() throws IOException {
        ClientReseau client = demarrer();
        ClientReseau.ErreurReseau erreur = assertThrows(ClientReseau.ErreurReseau.class,
                () -> client.connecter("PC1", "DIRECTEUR"));
        assertTrue(erreur.getMessage().contains("Rôle inconnu"),
                "Le refus doit nommer la cause : " + erreur.getMessage());
        assertTrue(client.postes().isEmpty(), "Un poste refusé ne doit pas être enregistré");
    }

    @Test
    @DisplayName("Une note publiée par PC3 arrive sur PC2, et « validé » est recalculé")
    void resultatPartage() throws IOException {
        ClientReseau client = demarrer();
        assertTrue(client.publierResultat(new EtatReseau.Resultat("Amina", "Python", 3, 14, 12,
                false, "")).ok(), "Un client qui ment sur « validé » doit être corrigé, pas refusé");

        List<EtatReseau.Resultat> recus = client.resultats();
        assertEquals(1, recus.size());
        assertEquals("Amina", recus.get(0).apprenant());
        assertEquals(14, recus.get(0).note());
        assertTrue(recus.get(0).valide(), "14/20 avec un seuil de 12 est validé");
        assertFalse(recus.get(0).heure().isBlank(), "Le serveur horodate ce qu'il reçoit");

        client.publierResultat(new EtatReseau.Resultat("Karim", "Python", 3, 8, 12, true, ""));
        assertEquals(2, client.resultats().size());
        // Envoyé avec valide=true : le serveur recalcule à partir du seuil et dit faux.
        assertEquals("Karim", client.resultats().get(1).apprenant());
        assertFalse(client.resultats().get(1).valide(),
                "8/20 ne peut pas être validé : le serveur recalcule, il ne recopie pas le client");
    }

    @Test
    @DisplayName("Un poste déconnecté disparaît de la liste")
    void deconnexionRetireLePoste() throws IOException {
        ClientReseau client = demarrer();
        client.connecter("PC1", "ADMINISTRATEUR");
        EtatReseau.Reponse reponse = client.deconnecter("PC1");
        assertTrue(reponse.ok());
        assertTrue(client.postes().isEmpty());
    }

    @Test
    @DisplayName("La déconnexion de la session efface l'état local et retire le poste du serveur")
    void deconnexionSessionEffaceEtatEtPresence() throws IOException {
        ClientReseau sonde = demarrer();
        System.setProperty("ihm.poste", "PC1");
        Reseau.connecter("127.0.0.1:" + serveur.port(), Session.Role.APPRENANT);
        Session.setUtilisateur("PC1 — Apprenant");
        Session.setDernierResultat(fr.ihm2026.api.mock.MockBackend.corriger(2, 3, 10));
        Session.demarrerEvaluation();

        assertTrue(Reseau.connecte());
        assertEquals(1, sonde.postes().size());

        Reseau.deconnecter();

        assertFalse(Reseau.connecte());
        assertEquals("Poste non connecté", Session.utilisateur());
        assertNull(Session.dernierResultat());
        assertFalse(Session.evaluationEnCours());
        assertTrue(sonde.postes().isEmpty());
    }

    @Test
    @DisplayName("L'adresse saisie est décodée : IP seule, IP:port, ou URL complète")
    void decoderAdresse() {
        assertEquals("192.168.1.10", Reseau.decoderAdresse("192.168.1.10")[0]);
        assertEquals(String.valueOf(IdentitePoste.PORT_DEFAUT), Reseau.decoderAdresse("192.168.1.10")[1]);
        assertEquals("192.168.1.10", Reseau.decoderAdresse(" 192.168.1.10:9000 ")[0]);
        assertEquals("9000", Reseau.decoderAdresse(" 192.168.1.10:9000 ")[1]);
        assertEquals("192.168.1.10", Reseau.decoderAdresse("http://192.168.1.10:7777/")[0]);
        assertEquals("7777", Reseau.decoderAdresse("http://192.168.1.10:7777/")[1]);
        assertThrows(ClientReseau.ErreurReseau.class, () -> Reseau.decoderAdresse("   "));
        assertThrows(ClientReseau.ErreurReseau.class, () -> Reseau.decoderAdresse("192.168.1.10:ab"));
    }

    @Test
    @DisplayName("Un port de découverte invalide est refusé sans tentative réseau")
    void portDecouverteInvalideRefuse() {
        ClientReseau.ErreurReseau erreur = assertThrows(ClientReseau.ErreurReseau.class,
                () -> ClientReseau.decouvrirServeur(0));
        assertTrue(erreur.getMessage().contains("port"));
    }

    @Test
    @DisplayName("La découverte LAN répond sans PIN partagé")
    void decouverteLanSansPin() throws IOException {
        demarrer();
        try (DatagramSocket client = new DatagramSocket()) {
            client.setSoTimeout(2000);
            byte[] demande = "IHM2026:DISCOVER".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            client.send(new DatagramPacket(demande, demande.length,
                    InetAddress.getByName("127.0.0.1"), serveur.port()));
            byte[] tampon = new byte[32];
            DatagramPacket reponse = new DatagramPacket(tampon, tampon.length);
            client.receive(reponse);
            assertEquals("IHM2026:OK", new String(reponse.getData(), reponse.getOffset(),
                    reponse.getLength(), java.nio.charset.StandardCharsets.US_ASCII));
        } catch (SocketTimeoutException ex) {
            throw new AssertionError("Le serveur n'a pas répondu à la diffusion de découverte.", ex);
        }
    }

    @Test
    @DisplayName("Sans serveur, le message dit quoi faire (et non « Connection refused »)")
    void serveurAbsent() throws IOException {
        int portLibre;
        try (ServerSocket sonde = new ServerSocket(0)) {
            portLibre = sonde.getLocalPort();   // ouvert puis refermé : port libre et fermé
        }
        ClientReseau client = new ClientReseau("127.0.0.1", portLibre);
        ClientReseau.ErreurReseau erreur = assertThrows(ClientReseau.ErreurReseau.class,
                client::sante);
        assertTrue(erreur.getMessage().contains("PC4"),
                "Le message doit renvoyer vers le poste serveur : " + erreur.getMessage());
        // Et la façade hors ligne ne fait jamais échouer un écran.
        assertTrue(Reseau.postes().isEmpty());
        assertTrue(Reseau.resultats().isEmpty());
        assertNull(Reseau.publier(null, Session.Langage.PYTHON, 3));
    }

    @Test
    @DisplayName("Le rôle du poste vient des fichiers .cmd, et « serveur » ne s'appelle pas un rôle")
    void roleDuPoste() {
        System.setProperty("ihm.role", "administrateur");
        assertEquals(Session.Role.ADMINISTRATEUR, IdentitePoste.role());
        assertFalse(IdentitePoste.estServeur());

        System.setProperty("ihm.role", "enseignant");
        assertEquals(Session.Role.ENSEIGNANT, IdentitePoste.role());

        System.setProperty("ihm.role", "apprenant");
        System.setProperty("ihm.poste", "PC3");
        assertEquals(Session.Role.APPRENANT, IdentitePoste.role());
        assertEquals("PC3 · Apprenant", IdentitePoste.libelle());

        System.setProperty("ihm.role", "serveur");
        assertTrue(IdentitePoste.estServeur(), "Le poste PC4 est le serveur, pas un rôle humain");
        assertNull(IdentitePoste.role(), "Un serveur n'ouvre aucune fenêtre, donc aucun rôle");

        System.clearProperty("ihm.role");
        System.clearProperty("ihm.poste");
        assertNull(IdentitePoste.role(), "Sans option, aucun rôle n'est configuré");
        assertEquals("PC1 · poste non configuré", IdentitePoste.libelle());
    }

    @Test
    @DisplayName("La connexion automatique est active par défaut et se désactive par propriété")
    void connexionAutomatique() {
        System.clearProperty("ihm.connexion.auto");
        assertTrue(Reseau.connexionAuto(),
                "Un poste de salle doit rejoindre la salle sans rien saisir");
        System.setProperty("ihm.connexion.auto", "false");
        assertFalse(Reseau.connexionAuto(),
                "-Dihm.connexion.auto=false doit rendre l'écran de saisie");
        System.clearProperty("ihm.connexion.auto");
    }

    @Test
    @DisplayName("Une adresse inconnue du serveur renvoie 404 avec un message explicite")
    void adresseInconnue() throws IOException {
        ClientReseau client = demarrer();
        // Le client ne connaît pas /api/inconnu : on vérifie le contrat par la lecture JSON brute.
        ClientReseau.ErreurReseau erreur = assertThrows(ClientReseau.ErreurReseau.class, () -> {
            var http = java.net.http.HttpClient.newHttpClient();
            var requete = java.net.http.HttpRequest.newBuilder(
                            java.net.URI.create("http://127.0.0.1:" + serveur.port() + "/api/inconnu"))
                    .GET().build();
            var reponse = http.send(requete,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertEquals(404, reponse.statusCode());
            throw new ClientReseau.ErreurReseau(reponse.body());
        });
        assertTrue(erreur.getMessage().contains("inconnu"), erreur.getMessage());
    }
}
