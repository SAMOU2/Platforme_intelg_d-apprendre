package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.Session;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BotInscriptionTest {

    @Test
    void modeManuelNePreAcceptePersonne() {
        var bot = new BotInscription(Map.of("IHM_INSCRIPTION_BOT_MODE", "MANUEL"));
        var analyse = bot.analyser("amina@univ.dz", Session.Role.APPRENANT, "EN_ATTENTE");
        assertEquals(BotInscription.Recommandation.REVISION_ADMIN, analyse.recommandation());
        assertFalse(analyse.autoAccepter());
    }

    @Test
    void domainesAutorisesEtSousDomainesPourApprenants() {
        var bot = new BotInscription(Map.of(
                "IHM_INSCRIPTION_BOT_MODE", "DOMAINES",
                "IHM_INSCRIPTION_DOMAINES_AUTO", "@univ.dz, ecole.example"));
        assertTrue(bot.analyser("amina@univ.dz", Session.Role.APPRENANT, "EN_ATTENTE")
                .autoAccepter());
        assertTrue(bot.analyser("amina@etu.univ.dz", Session.Role.APPRENANT, "EN_ATTENTE")
                .autoAccepter());
        assertFalse(bot.analyser("amina@autre.example", Session.Role.APPRENANT, "EN_ATTENTE")
                .autoAccepter());
    }

    @Test
    void enseignantJamaisPreAccepteParLeBot() {
        var bot = new BotInscription(Map.of(
                "IHM_INSCRIPTION_BOT_MODE", "APPRENANTS"));
        var analyse = bot.analyser("prof@univ.dz", Session.Role.ENSEIGNANT, "EN_ATTENTE");
        assertEquals(BotInscription.Recommandation.REVISION_ADMIN, analyse.recommandation());
        assertFalse(analyse.autoAccepter());
    }

    @Test
    void domaineBloquePrioritaireSurAutoAcceptation() {
        var bot = new BotInscription(Map.of(
                "IHM_INSCRIPTION_BOT_MODE", "APPRENANTS",
                "IHM_INSCRIPTION_DOMAINES_BLOQUES", "jetable.example"));
        var analyse = bot.analyser("test@jetable.example", Session.Role.APPRENANT, "EN_ATTENTE");
        assertEquals(BotInscription.Recommandation.REFUS_CONSEILLE, analyse.recommandation());
        assertFalse(analyse.autoAccepter());
    }

    @Test
    void demandeDejaTraiteeNonReevaluee() {
        var bot = new BotInscription(Map.of("IHM_INSCRIPTION_BOT_MODE", "APPRENANTS"));
        var analyse = bot.analyser("amina@univ.dz", Session.Role.APPRENANT, "ACCEPTE");
        assertEquals(BotInscription.Recommandation.DEJA_TRAITE, analyse.recommandation());
        assertFalse(analyse.autoAccepter());
    }

    @Test
    void modeDomainesExigeUneListe() {
        assertThrows(IllegalStateException.class,
                () -> new BotInscription(Map.of("IHM_INSCRIPTION_BOT_MODE", "DOMAINES")));
    }
}
