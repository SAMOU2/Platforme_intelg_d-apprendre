package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Le bot d'acceptation des demandes d'inscription : des règles explicables, jamais une
 * décision pédagogique. L'apprenant s'inscrit auprès d'un enseignant ; le bot ne peut
 * accorder l'accès que si la politique de PC1 et l'offre de l'enseignant le prévoient.
 */
class BotDemandesTest {

    @Test
    @DisplayName("Par défaut, seul l'enseignant décide : le bot n'accepte rien")
    void defautLaisseLaDecisionALEnseignant() {
        var bot = new BotDemandes(Map.of());
        assertEquals(BotDemandes.Mode.OFFRES, bot.mode());
        var decision = bot.analyser("Amina Benali", true, false);
        assertFalse(decision.autoAccepter());
        assertTrue(decision.motif().contains("l'enseignant décide"));
    }

    @Test
    @DisplayName("Le bot accepte immédiatement si l'enseignant a activé son offre")
    void offreAvecAcceptationAutomatique() {
        var bot = new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "OFFRES"));
        var decision = bot.analyser("Amina Benali", true, true);
        assertTrue(decision.autoAccepter());
        assertTrue(decision.motif().contains("Acceptation automatique activée sur l'offre"));
    }

    @Test
    @DisplayName("Mode manuel : même une offre automatique n'accepte pas sans l'enseignant")
    void modeManuelRefuseToujours() {
        var bot = new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "MANUEL"));
        assertFalse(bot.analyser("Amina Benali", true, true).autoAccepter());
        assertEquals(BotDemandes.Mode.MANUEL, bot.mode());
    }

    @Test
    @DisplayName("Mode AUTO du serveur : toute demande lisible est acceptée par le bot")
    void modeAutoAccepteLesDemandesLisibles() {
        var bot = new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "AUTO"));
        assertTrue(bot.analyser("Amina Benali", true, false).autoAccepter());
        assertTrue(bot.analyser("  Karim  ", true, false).autoAccepter());
    }

    @Test
    @DisplayName("Une offre fermée n'est jamais acceptée automatiquement")
    void offreFermeeBloqueLeBot() {
        var bot = new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "AUTO"));
        var decision = bot.analyser("Amina Benali", false, true);
        assertFalse(decision.autoAccepter());
        assertTrue(decision.motif().contains("Offre fermée"));
    }

    @Test
    @DisplayName("Un nom illisible reste à trancher par l'enseignant")
    void nomIllisibleResteHumain() {
        var bot = new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "AUTO"));
        assertFalse(bot.analyser(null, true, true).autoAccepter());
        assertFalse(bot.analyser("   ", true, true).autoAccepter());
        assertFalse(bot.analyser("A", true, true).autoAccepter());
        assertFalse(bot.analyser("12345", true, true).autoAccepter());
        assertTrue(bot.analyser("a".repeat(BotDemandes.NOM_MAX), true, true).autoAccepter());
        assertFalse(bot.analyser("a".repeat(BotDemandes.NOM_MAX + 1), true, true).autoAccepter());
    }

    @Test
    @DisplayName("Une politique inconnue est refusée au démarrage, pas devinée")
    void politiqueInconnueRefusee() {
        var erreur = assertThrows(IllegalStateException.class,
                () -> new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "TOUT_ACCEPTER")));
        assertTrue(erreur.getMessage().contains("IHM_DEMANDE_BOT_MODE"));
    }

    @Test
    @DisplayName("La casse et les espaces de la variable d'environnement sont tolérés")
    void politiqueNormalisee() {
        assertEquals(BotDemandes.Mode.AUTO, new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", " auto ")).mode());
        assertEquals(BotDemandes.Mode.OFFRES,
                new BotDemandes(Map.of("IHM_DEMANDE_BOT_MODE", "")).mode());
    }

    @Test
    @DisplayName("Le décideur tracé pour une acceptation automatique est le bot")
    void decideurTracé() {
        assertEquals("BOT", BotDemandes.DECIDEUR_BOT);
    }
}
