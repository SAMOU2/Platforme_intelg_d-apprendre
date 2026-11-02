"""Contrôles structurels de la barre latérale réduite/agrandie.

Ces contrôles protègent le positionnement du bouton hamburger, le maintien
d'une navigation en icônes plutôt qu'un masquage complet, et les couleurs
sémantiques. Ils ne remplacent ni la compilation JavaFX ni la recette
clavier/NVDA sous Windows.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/fr/ihm2026'
CSS = ROOT / 'src/main/resources/fr/ihm2026/css'


class BarreLateraleReductibleTest(unittest.TestCase):
    def test_bouton_chez_les_commandes_de_gauche(self):
        titre = (JAVA / 'ui/commun/BarreTitre.java').read_text()
        self.assertIn('getChildren().addAll(feux, boutonLaterale(), retourButton(), avanceButton(), titre,',
                      titre)
        self.assertLess(titre.index('boutonLaterale()'), titre.index('retourButton()'))
        self.assertLess(titre.index('boutonLaterale()'), titre.index('espace, style, theme, aide'))
        for attendu in ['Réduire la barre latérale en icônes',
                        'Agrandir la barre latérale avec les libellés',
                        'icone-bouton-laterale']:
            self.assertIn(attendu, titre)

    def test_reduction_sans_disparition_de_la_navigation(self):
        routeur = (JAVA / 'core/Router.java').read_text()
        animations = (JAVA / 'core/Animations.java').read_text()
        laterale = (JAVA / 'ui/commun/BarreLaterale.java').read_text()
        self.assertIn('LATERALE_LARGEUR_REDUITE = 76', animations)
        self.assertIn('lateraleReduite = !lateraleReduite', routeur)
        self.assertIn('barreLaterale.setReduite(lateraleReduite)', routeur)
        self.assertIn('public void setReduite(boolean reduite)', laterale)
        for attendu in ['entete.setManaged(!reduite)', 'libelle.setManaged(!reduite)',
                        'bouton.setTooltip(new Tooltip(nom))',
                        'Pos.CENTER : Pos.CENTER_LEFT']:
            self.assertIn(attendu, laterale)
        reduction = animations.split('public static void basculerLaterale', 1)[1]
        reduction = reduction.split('/** Chevron retour', 1)[0]
        self.assertNotIn('setManaged(false)', reduction)
        self.assertNotIn('setVisible(false)', reduction)

    def test_couleurs_semantiques_pour_chaque_fonction(self):
        laterale = (JAVA / 'ui/commun/BarreLaterale.java').read_text()
        app = (CSS / 'app.css').read_text()
        for classe in ['nav-parcours', 'nav-cours', 'nav-exercice', 'nav-resultats',
                       'nav-bord', 'nav-enseigner', 'nav-comptes']:
            self.assertIn(classe, laterale)
            self.assertIn('.' + classe, app)
        for theme in ['theme-light.css', 'theme-dark.css']:
            palette = (CSS / theme).read_text()
            for jeton in ['-color-nav-parcours:', '-color-nav-cours:', '-color-nav-exercice:',
                          '-color-nav-resultats:', '-color-nav-bord:', '-color-nav-enseigner:',
                          '-color-nav-comptes:']:
                self.assertIn(jeton, palette)
        for attendu in ['tuile-icone-laterale', 'barre-laterale-reduite',
                        '-color-nav-entree', 'monogramme-parcours']:
            self.assertIn(attendu, app)


if __name__ == '__main__':
    unittest.main()
