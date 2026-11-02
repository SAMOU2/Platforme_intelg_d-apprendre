"""Contrôles structurels uniquement : ne prouvent ni l'usage clavier ni la lecture NVDA."""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
UI = ROOT / 'src/main/java/fr/ihm2026/ui'


class AccessibiliteTest(unittest.TestCase):
    def test_navigation_utilise_de_vrais_boutons(self):
        source = (UI / 'commun/BarreLaterale.java').read_text()
        self.assertIn('Map<String, Button>', source)
        self.assertIn('new Button(nom, contenu)', source)
        self.assertIn('bouton.setOnAction', source)
        self.assertNotIn('setOnMouseClicked', source)
        self.assertIn('écran actuel', source)
        self.assertIn('parcours sélectionné', source)

    def test_regions_et_raccourcis_sans_actions_metier(self):
        source = (UI / 'commun/Accessibilite.java').read_text()
        for code in ['KeyCode.F1', 'KeyCode.F6', 'KeyCode.F8', 'e.isShiftDown()',
                     'e.isAltDown()', 'e.isControlDown()', 'e.isMetaDown()',
                     'n.getScene() == scene', 'requestFocus()', 'Math.floorMod']:
            self.assertIn(code, source)
        for interdit in ['soumettre(', 'demanderIa(', 'demarrerEvaluation(', 'terminerEvaluation(']:
            self.assertNotIn(interdit, source)
        routeur = (ROOT / 'src/main/java/fr/ihm2026/core/Router.java').read_text()
        self.assertEqual(1, routeur.count('Accessibilite.installer(scene'))
        self.assertIn('root.getLeft() == barreLaterale && lateraleVisible', routeur)
        self.assertIn('Notifications.effacer(stage)', routeur)

    def test_cartes_activables_par_technologie_assistance(self):
        source = (UI / 'commun/CarteNiveau.java').read_text()
        for attendu in ['AccessibleRole.BUTTON', 'setFocusTraversable(true)', 'KeyCode.ENTER',
                        'KeyCode.SPACE', 'AccessibleAction.FIRE', '!isDisabled()',
                        'niveau.getEtat().label()', 'niveau.getTentativesRestantes()']:
            self.assertIn(attendu, source)

    def test_notification_relecture_et_effacement(self):
        source = (UI / 'commun/Notifications.java').read_text()
        self.assertIn('getProperties().put(DERNIERE_NOTIFICATION', source)
        self.assertIn('public static void relire(Window owner)', source)
        self.assertIn('getProperties().remove(DERNIERE_NOTIFICATION)', source)
        self.assertIn('courant = null', source)
        self.assertNotIn('java.nio.file', source)

    def test_formulaires_et_secrets_nommes(self):
        source = (UI / 'commun/VueConnexionServeur.java').read_text()
        for champ in ['email', 'nom', 'motDePasse', 'confirmationMotDePasse',
                      'codeConnexion', 'codeInvitation', 'portHttpsManuel', 'serveurs']:
            self.assertIn(champ + '.setAccessibleText(', source)
        secret = (UI / 'commun/ChampSecret.java').read_text()
        self.assertIn('secret.accessibleTextProperty()', secret)
        self.assertNotIn('setAccessibleText(secret.getText()', secret)
        self.assertIn('clair.visibleProperty().bind(voir.selectedProperty())', secret)
        test = (UI / 'apprenant/TestServeurView.java').read_text()
        self.assertIn('texte.setAccessibleText("Votre réponse : " + enonce.getText())', test)
        self.assertIn('choix.setAccessibleText(enonce.getText()', test)

    def test_focus_visible_dans_les_deux_themes(self):
        css = (ROOT / 'src/main/resources/fr/ihm2026/css/app.css').read_text()
        self.assertIn('.ligne-laterale.button:focused, .card-niveau:focused', css)
        self.assertIn('-fx-border-style: segments(4, 3)', css)
        for theme in ['theme-light', 'theme-dark']:
            self.assertIn('-color-texte:', (ROOT / f'src/main/resources/fr/ihm2026/css/{theme}.css').read_text())


if __name__ == '__main__':
    unittest.main()
