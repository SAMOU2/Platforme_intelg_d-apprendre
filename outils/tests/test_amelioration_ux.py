"""Contrôles structurels de l'itération UX retours d'état/filtres.

Ces tests ne remplacent ni la compilation Java/JavaFX sous Windows, ni la
recette visuelle, ni les essais NVDA décrits dans la documentation.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/fr/ihm2026'
CSS = ROOT / 'src/main/resources/fr/ihm2026/css/app.css'


class AmeliorationUxTest(unittest.TestCase):
    def test_connexion_affiche_etat_pres_action_principale(self):
        source = (JAVA / 'ui/commun/VueConnexionServeur.java').read_text()
        self.assertIn('memoriserAppareil, ligne, bandeauEtat, actionsCompte, aide', source)
        self.assertNotIn('actionsCompte, etat, aide', source)
        for attendu in [
                'connexion-bandeau-etat', 'ProgressIndicator', 'etat-formulaire',
                'etat-symbole', 'indicateur-chargement', 'rendreEtatVisible',
                'nature + " : " + texte', 'defilementConnexion = defilement']:
            self.assertIn(attendu, source)
        for symbole in ['"↻"', '"✓"', '"⚠"', '"✕"', '"ℹ"']:
            self.assertIn(symbole, source)
        self.assertIn('minuscules.contains("invalide")', source)
        self.assertIn('etat.setText("Activation du compte…")', source)

    def test_connexion_ne_repete_plus_les_notifications_vagues(self):
        source = (JAVA / 'ui/commun/VueConnexionServeur.java').read_text()
        self.assertNotIn('Votre demande a abouti. Consultez les indications du formulaire.', source)
        self.assertNotIn('Vérifiez les champs du formulaire.', source)
        self.assertIn('actualiserPresentationEtat(message)', source)
        self.assertIn('actualiserPresentationReseau(etatDecouverte, message)', source)

    def test_notification_asynchrone_donne_la_cause_sans_doublon(self):
        taches = (JAVA / 'core/TaskRunner.java').read_text()
        self.assertIn('Notifications.erreurActive(task.getException())', taches)
        notifications = (JAVA / 'ui/commun/Notifications.java').read_text()
        self.assertIn('erreurActive(Throwable erreur)', notifications)
        self.assertIn('"L\'action n\'a pas abouti : " + detail', notifications)
        self.assertNotIn("L'action n'a pas abouti. Consultez le message de l'écran", notifications)
        for attendu in ['typeCourant == type', 'message.equals(messageCourant)',
                        'typeCourant = type', 'messageCourant = message',
                        'popup.setOnHidden']:
            self.assertIn(attendu, notifications)

    def test_filtres_admin_automatiques_et_derniere_demande_gagnante(self):
        source = (JAVA / 'ui/admin/ComptesView.java').read_text()
        for attendu in [
                'PauseTransition(Duration.millis(400))',
                'recherche.textProperty().addListener',
                'domaine.textProperty().addListener',
                'filtreStatut.valueProperty().addListener',
                'filtreRole.valueProperty().addListener',
                'filtreActif.valueProperty().addListener',
                'reinitialisationFiltres = true',
                'if (nouvelleScene == null) delaiRecherche.stop()',
                'reprendreChargementPlusRecent(filtre)',
                'rechargementListeEnAttente = true',
                'AdminApi.comptes(filtre)']:
            self.assertIn(attendu, source)

    def test_admin_distigue_chargement_vide_et_donnees_perimees(self):
        source = (JAVA / 'ui/admin/ComptesView.java').read_text()
        for attendu in [
                'comptes-bandeau-etat',
                '↻ Réessayer',
                'Les lignes affichées sont encore celles du chargement précédent.',
                'Les lignes ci-dessous sont celles du chargement précédent.',
                'Aucun compte ne correspond aux filtres.',
                'Aucun compte enregistré pour le moment.',
                'Liste à jour']:
            self.assertIn(attendu, source)
        self.assertNotIn('Dialogue.erreur(fenetre(), "Actualisation impossible"', source)

    def test_styles_etats_partages_par_les_themes(self):
        css = CSS.read_text()
        for classe in ['.etat-formulaire', '.etat-reseau', '.etat-information',
                       '.etat-chargement', '.etat-succes', '.etat-avertissement',
                       '.etat-erreur', '.etat-symbole', '.indicateur-chargement']:
            self.assertIn(classe, css)
        for jeton in ['-color-encours', '-color-valide', '-color-alerte',
                      '-color-danger', '-color-texte']:
            self.assertIn(jeton, css)

    def test_documentation_limites_et_recette_windows(self):
        doc = (ROOT / 'docs/amelioration-ux-retours-filtres.md').read_text()
        for attendu in ['Aucune', 'migration de base', '.\\mvnw.cmd clean test',
                        'Actifs seulement', 'NVDA', 'F8', 'mot de passe toujours demandé',
                        'preuves/amelioration-ux-retours-filtres-schema.png',
                        'pas une capture']:
            self.assertIn(attendu, doc)
        self.assertTrue((ROOT / 'docs/preuves/amelioration-ux-retours-filtres-schema.png').is_file())


if __name__ == '__main__':
    unittest.main()
