package fr.ihm2026.api.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;

/** Vérifie la configuration SMTP : STARTTLS, SMTPS implicite et erreurs de configuration. */
class ServiceEmailTest {

    private static Map<String, String> environnement(String... paires) {
        Map<String, String> valeurs = new HashMap<>();
        valeurs.put("IHM_SMTP_HOST", "smtp.exemple.org");
        valeurs.put("IHM_SMTP_FROM", "plateforme@exemple.org");
        for (int i = 0; i < paires.length; i += 2) {
            valeurs.put(paires[i], paires[i + 1]);
        }
        return valeurs;
    }

    @Test
    void port587UtiliseStarttlsObligatoire() {
        Properties p = new ServiceEmail(environnement("IHM_SMTP_PORT", "587")).proprietes();
        assertEquals("true", p.getProperty("mail.smtp.starttls.enable"));
        assertEquals("true", p.getProperty("mail.smtp.starttls.required"));
        assertEquals("false", p.getProperty("mail.smtp.ssl.enable"));
    }

    @Test
    void port465UtiliseTlsImpliciteSansStarttls() {
        Properties p = new ServiceEmail(environnement("IHM_SMTP_PORT", "465")).proprietes();
        assertEquals("true", p.getProperty("mail.smtp.ssl.enable"));
        assertEquals("false", p.getProperty("mail.smtp.starttls.enable"),
                "Starttls obligatoire en SMTPS ferait échouer la négociation");
        assertEquals("false", p.getProperty("mail.smtp.starttls.required"));
        assertEquals("true", p.getProperty("mail.smtp.ssl.checkserveridentity"));
    }

    @Test
    void modeTlsPeutEtreForceOuDesactive() {
        assertTrue("true".equals(new ServiceEmail(environnement("IHM_SMTP_PORT", "587", "IHM_SMTP_SSL", "1"))
                .proprietes().getProperty("mail.smtp.ssl.enable")));
        assertTrue("false".equals(new ServiceEmail(environnement("IHM_SMTP_PORT", "465", "IHM_SMTP_SSL", "0"))
                .proprietes().getProperty("mail.smtp.ssl.enable")));
        assertThrows(IllegalStateException.class,
                () -> new ServiceEmail(environnement("IHM_SMTP_SSL", "peut-etre")));
    }

    @Test
    void utilisateurSansMotDePasseEstRefuse() {
        IllegalStateException erreur = assertThrows(IllegalStateException.class,
                () -> new ServiceEmail(environnement("IHM_SMTP_USER", "compte")));
        assertTrue(erreur.getMessage().contains("IHM_SMTP_PASSWORD"));
    }

    @Test
    void portInvalideEstRefuse() {
        assertThrows(IllegalStateException.class,
                () -> new ServiceEmail(environnement("IHM_SMTP_PORT", "70000")));
        assertThrows(IllegalStateException.class,
                () -> new ServiceEmail(environnement("IHM_SMTP_PORT", "smtp")));
    }

    @Test
    void resumeNafficheJamaisLeMotDePasse() {
        Map<String, String> valeurs = environnement("IHM_SMTP_USER", "compte",
                "IHM_SMTP_PASSWORD", "secret-tres-prive", "IHM_SMTP_PORT", "465");
        String resume = new ServiceEmail(valeurs).resume();
        assertTrue(resume.contains("smtp.exemple.org:465"), resume);
        assertTrue(resume.contains("SMTPS"), resume);
        assertTrue(!resume.contains("secret-tres-prive"), "Le résumé doit rester publiable : " + resume);
    }

    @Test
    void resumeSignaleUneConfigurationIncomplete() {
        String resume = new ServiceEmail(Map.of()).resume();
        assertTrue(resume.contains("non configuré"), resume);
        assertTrue(resume.contains("IHM_SMTP_HOST"), resume);
    }
}
