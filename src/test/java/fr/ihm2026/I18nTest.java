package fr.ihm2026;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fr.ihm2026.core.i18n.UiText;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Garde-fou sur les ressources : la panne « Can't find bundle » a réellement bloqué
 * le démarrage de l'application sur un poste en locale anglaise (README §5).
 */
class I18nTest {

    private static final String BASE = "fr.ihm2026.i18n.messages";

    @Test
    @DisplayName("Le fichier racine messages.properties existe et contient les libellés")
    void bundleRacineExiste() {
        assertTrue(UiText.disponible(), "Le paquet de ressources doit être chargeable");
        assertEquals("IHM 2026", UiText.get("app.marque"));
    }

    @Test
    @DisplayName("Le paquet se charge quelle que soit la locale du poste (en_US, de_DE, ar_DZ)")
    void toutesLesLocalesTrouventLePaquet() {
        for (Locale locale : new Locale[] {Locale.US, Locale.GERMANY, new Locale("ar", "DZ"),
                                           Locale.FRANCE, Locale.ROOT}) {
            ResourceBundle bundle = ResourceBundle.getBundle(BASE, locale,
                    I18nTest.class.getClassLoader());
            assertNotNull(bundle, "Paquet introuvable pour " + locale);
            assertEquals("IHM 2026", bundle.getString("app.marque"),
                    "Libellé absent pour " + locale);
        }
    }

    @Test
    @DisplayName("Le français est retenu même si le système est en anglais")
    void francaisForce() {
        Locale precedent = Locale.getDefault();
        try {
            Locale.setDefault(Locale.US);
            assertEquals("Mon parcours — Python", UiText.get("parcours.titre", "Python"));
        } finally {
            Locale.setDefault(precedent);
        }
    }

    @Test
    @DisplayName("Une clé absente ne fait pas tomber l'application")
    void cleAbsenteSignalee() {
        assertEquals("«cle.inexistante»", UiText.get("cle.inexistante"));
        assertFalse(UiText.get("parcours.sousTitre").isEmpty());
    }

    @Test
    @DisplayName("Une apostrophe s'affiche normalement, avec ou sans argument")
    void apostropheAffichee() {
        assertEquals("Faire l'exercice", UiText.get("action.exercice"));
        assertEquals("N'oubliez pas l'objet", UiText.get("test.apostrophe", "l'objet"));
    }

    @Test
    @DisplayName("Un nom de base erroné est bien la cause de l'ancienne panne")
    void nomDeBaseErroneRejette() {
        boolean absent = false;
        try {
            ResourceBundle.getBundle("fr.ihm2026.i18n.message", Locale.FRENCH);
        } catch (MissingResourceException e) {
            absent = true;
        }
        assertTrue(absent, "Le test documente l'erreur d'origine : base name faux = exception");
    }
}
