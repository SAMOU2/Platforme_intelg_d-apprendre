"""Garde-fous sur le cycle de vie du nom affiché dans la barre latérale.

Une recette graphique reste nécessaire pour vérifier les connexions réelles.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/fr/ihm2026'


class IdentiteSessionTest(unittest.TestCase):
    def test_nom_authentifie_avant_navigation_pour_les_deux_connexions(self):
        vue = (JAVA / 'ui/commun/VueConnexionServeur.java').read_text(encoding='utf-8')
        reseau = (JAVA / 'reseau/Reseau.java').read_text(encoding='utf-8')
        terminer = vue.split('private void terminerConnexion(', 1)[1]
        terminer = terminer.split('private void commencerReinitialisation(', 1)[0]
        self.assertLess(terminer.index('Reseau.installerAuthentification(session)'),
                        terminer.index('router.go(ecranDuRole())'))
        installer = reseau.split('public static void installerAuthentification(', 1)[1]
        installer = installer.split('/** Compatibilité', 1)[0]
        self.assertIn('Session.setUtilisateur(session.compte().nom())', installer)
        self.assertGreaterEqual(vue.count('terminerConnexion('), 3)

    def test_profil_precedent_non_reutilise_apres_retour_a_la_connexion(self):
        routeur = (JAVA / 'core/Router.java').read_text(encoding='utf-8')
        public = routeur.split('if (public_) {', 1)[1].split('} else {', 1)[0]
        self.assertIn('root.setLeft(null)', public)
        self.assertIn('barreLaterale = null', public)
        self.assertIn('barreLaterale == null || barreLaterale.role() != Session.role()', routeur)
        laterale = (JAVA / 'ui/commun/BarreLaterale.java').read_text(encoding='utf-8')
        self.assertIn('new Label(Session.utilisateur())', laterale)
        self.assertIn('initiales(Session.utilisateur())', laterale)
        self.assertIn('new Tooltip(Session.utilisateur()', laterale)
        reseau = (JAVA / 'reseau/Reseau.java').read_text(encoding='utf-8')
        self.assertIn('Session.setUtilisateur("Poste non connecté")', reseau)


if __name__ == '__main__':
    unittest.main()
