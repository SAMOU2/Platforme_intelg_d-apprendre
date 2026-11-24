"""Contrôles statiques de l'inscription aux offres (sans JDK, comme les autres tests Python).

Règle vérifiée : l'administrateur n'affecte plus les apprenants aux enseignants. L'apprenant
choisit un enseignant publié dans son catalogue, saisit son nom, l'enseignant (ou le bot de son
offre) accepte, et l'accès aux cours s'ouvre. Cours, exercices et test final s'enchaînent dans
la même fenêtre.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / "src/main/java/fr/ihm2026"


def lire(*chemin):
    return (ROOT.joinpath(*chemin)).read_text(encoding="utf-8")


class InscriptionAuxOffresTest(unittest.TestCase):
    def test_admin_ne_affecte_ni_publie_ni_prepare(self):
        ui = lire('src/main/java/fr/ihm2026/ui/admin/ComptesView.java')
        for interdit in [
                'SuiviPedagogique.affecter',
                'SuiviPedagogique.publierContenus',
                'SuiviPedagogique.preparerTest',
                '"Affecter un apprenant à un enseignant"',
                '"Publier le cours et l\'entraînement d\'un niveau"',
                '"Configurer le sujet d\'un test"',
        ]:
            with self.subTest(interdit=interdit):
                self.assertNotIn(interdit, ui)
        # Le périmètre reste expliqué à l'administrateur, pas seulement supprimé.
        for attendu in ["Ce que l'administrateur ne fait plus",
                        "s'inscrit lui-même auprès d'un enseignant"]:
            self.assertIn(attendu, ui)
        # La gestion des comptes, elle, ne bouge pas.
        for attendu in ['Créer un compte', 'Accepter la sélection', 'Refuser la sélection',
                        'Renvoyer le PIN d\'activation', 'Journal d\'audit']:
            self.assertIn(attendu, ui)

    def test_serveur_refuse_affectation_pour_tous_les_roles(self):
        repo = lire('src/main/java/fr/ihm2026/api/persistence/PostgresPedagogieRepository.java')
        self.assertIn('case "affectations", "affecter" -> throw new AccesRefuse(', repo)
        self.assertNotIn('private static JsonNode affecter(', repo)
        self.assertNotIn('private static JsonNode affectations(', repo)
        self.assertIn("n'affecte plus les apprenants", repo)
        # Publier un cours, un entraînement ou un sujet de test redevient un acte d'enseignant :
        # l'administrateur ne garde la main que sur un niveau sans auteur (continuité).
        self.assertIn('WHERE id=? AND (cree_par=? OR (cree_par IS NULL AND ?))', repo)
        self.assertNotIn('WHERE id=? AND (? OR cree_par=?)', repo)

    def test_nouvelles_actions_et_leurs_gardes_de_role(self):
        repo = lire('src/main/java/fr/ihm2026/api/persistence/PostgresPedagogieRepository.java')
        for action, garde in [
                ('offres', 'APPRENANT'),
                ('inscrire-offre', 'APPRENANT'),
                ('mes-demandes', 'APPRENANT'),
                ('annuler-demande', 'APPRENANT'),
                ('mon-offre', 'ENSEIGNANT'),
                ('publier-offre', 'ENSEIGNANT'),
                ('demandes-recues', 'ENSEIGNANT'),
                ('decider-demande', 'ENSEIGNANT'),
        ]:
            with self.subTest(action=action):
                self.assertIn(f'case "{action}" -> {{ exiger(role==Session.Role.{garde})', repo)
        # L'accès aux cours n'est écrit que par la décision (enseignant ou bot), jamais par un tiers.
        self.assertIn('private static void accorderAcces(', repo)
        self.assertIn('INSERT INTO affectations_pedagogiques(apprenant_id,enseignant_id,affecte_par)',
                      repo)
        self.assertIn("WHERE id=? AND enseignant_id=? FOR UPDATE", repo)
        self.assertIn('Une copie reste à terminer ou à corriger', repo)
        self.assertIn("Aucun enseignant ne vous a encore accepté", repo)

    def test_bot_demandes_explicable_et_utilise_par_le_serveur(self):
        bot = lire('src/main/java/fr/ihm2026/api/service/BotDemandes.java')
        for attendu in ['MANUEL', 'OFFRES', 'AUTO', 'IHM_DEMANDE_BOT_MODE',
                        'record Decision(boolean autoAccepter, String motif)',
                        'Offre fermée', 'DECIDEUR_BOT']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, bot)
        self.assertNotIn('ModeleIa', bot)
        repo = lire('src/main/java/fr/ihm2026/api/persistence/PostgresPedagogieRepository.java')
        self.assertIn('BotDemandes.depuisEnvironnement().analyser(nom,ouverte,acceptationBot)', repo)
        self.assertIn("'BOT'", repo)
        test = lire('src/test/java/fr/ihm2026/api/service/BotDemandesTest.java')
        self.assertIn('defautLaisseLaDecisionALEnseignant', test)
        self.assertIn('offreFermeeBloqueLeBot', test)

    def test_catalogue_apprenant_inscription_par_le_nom(self):
        vue = lire('src/main/java/fr/ihm2026/ui/apprenant/OffresEnseignantsView.java')
        for attendu in ['"offres"', '"inscrire-offre"', '"mes-demandes"', '"annuler-demande"',
                        '"S\'inscrire"', 'Votre nom', 'ActualisationVue',
                        'Envoyer ma demande', 'Ouvrir mes cours',
                        'Aucun enseignant ne propose encore de cours publié']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, vue)
        # Aucune donnée de démonstration inventée hors connexion.
        self.assertIn('Reseau.sessionCompteInstallee()', vue)
        self.assertNotIn('MockBackend', vue)

    def test_parcours_et_atelier_dans_la_meme_fenetre(self):
        parcours = lire('src/main/java/fr/ihm2026/ui/apprenant/ParcoursServeurView.java')
        self.assertIn('Router.OFFRES', parcours)
        self.assertIn("Voir les enseignants disponibles", parcours)
        self.assertIn('Session.EtapeAtelier.COURS', parcours)
        self.assertIn('Session.EtapeAtelier.EXERCICES', parcours)
        self.assertIn('Session.EtapeAtelier.TEST', parcours)
        self.assertIn('Router.ATELIER', parcours)
        atelier = lire('src/main/java/fr/ihm2026/ui/apprenant/AtelierNiveauView.java')
        for attendu in ['1 · Cours et explications', '2 · Entraînement avec IA',
                        '3 · Test final sans IA', 'TestServeurView', 'etape.suivante()',
                        'PanneauAssistant', 'setRight(null)', 'ActualisationVue']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, atelier)
        self.assertNotIn('MockBackend', atelier)
        session = lire('src/main/java/fr/ihm2026/core/Session.java')
        self.assertIn('enum EtapeAtelier', session)
        self.assertIn('etapeAtelier = EtapeAtelier.COURS', session)

    def test_enseignant_decide_des_demandes_et_regle_son_bot(self):
        demandes = lire('src/main/java/fr/ihm2026/ui/enseignant/DemandesInscriptionView.java')
        for attendu in ['"demandes-recues"', '"decider-demande"', 'Accepter la demande',
                        'Refuser la demande', 'EN_ATTENTE', 'Bot de l\'offre', 'ActualisationVue']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, demandes)
        offre = lire('src/main/java/fr/ihm2026/ui/enseignant/OffreEnseignantView.java')
        for attendu in ['"mon-offre"', '"publier-offre"', 'auto_acceptation',
                        'Le bot accepte automatiquement les demandes d\'inscription',
                        'Offre ouverte aux nouvelles demandes',
                        'Activer l\'acceptation automatique ?']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, offre)
        resultats = lire('src/main/java/fr/ihm2026/ui/enseignant/ResultatsView.java')
        self.assertIn('new DemandesInscriptionView()', resultats)
        self.assertIn('Demandes d\'inscription reçues', resultats)
        niveaux = lire('src/main/java/fr/ihm2026/ui/enseignant/GestionNiveauxView.java')
        self.assertIn('new OffreEnseignantView()', niveaux)
        suivi = lire('src/main/java/fr/ihm2026/ui/commun/SuiviPedagogique.java')
        self.assertNotIn('public static void affecter(', suivi)
        self.assertNotIn("demander une affectation à l'administrateur", suivi)
        self.assertIn('Apprenants inscrits à vos cours', suivi)

    def test_routes_et_navigation(self):
        routeur = lire('src/main/java/fr/ihm2026/core/Router.java')
        for attendu in ['String OFFRES = "offres"', 'String ATELIER = "atelier"',
                        'Map.entry(OFFRES, "Enseignants et inscriptions")',
                        'Map.entry(ATELIER, "Cours, exercices et test")',
                        'case PARCOURS, OFFRES, ATELIER, COURS, EXERCICE, TEST, RESULTAT, TABLEAU_BORD -> true']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, routeur)
        app = lire('src/main/java/fr/ihm2026/App.java')
        self.assertIn('Router.OFFRES, () -> new fr.ihm2026.ui.apprenant.OffresEnseignantsView(router)', app)
        self.assertIn('Router.ATELIER, () -> new fr.ihm2026.ui.apprenant.AtelierNiveauView(router)', app)
        laterale = lire('src/main/java/fr/ihm2026/ui/commun/BarreLaterale.java')
        self.assertIn('Enseignants et inscription', laterale)
        self.assertIn('ICONE_OFFRES', laterale)
        css = lire('src/main/resources/fr/ihm2026/css/app.css')
        self.assertIn('.nav-offres', css)
        for theme in ['theme-dark.css', 'theme-light.css', 'retro.css']:
            self.assertIn('-color-nav-offres',
                          lire('src/main/resources/fr/ihm2026/css/' + theme))
        test = lire('src/test/java/fr/ihm2026/RouterAuthorizationTest.java')
        self.assertIn('Router.OFFRES', test)
        self.assertIn('Router.ATELIER', test)

    def test_migration_16_non_destructive_et_montee(self):
        sql = lire('db/16-offres-et-demandes-inscription.sql')
        for attendu in ['CREATE TABLE IF NOT EXISTS offres_enseignants',
                        'CREATE TABLE IF NOT EXISTS demandes_inscription',
                        'ux_demandes_inscription_attente',
                        "CHECK (statut IN ('EN_ATTENTE', 'ACCEPTEE', 'REFUSEE', 'ANNULEE'))",
                        "decide_par IN ('BOT', 'ENSEIGNANT', 'APPRENANT')",
                        'BEGIN;', 'COMMIT;']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, sql)
        for interdit in ['DROP TABLE', 'TRUNCATE', 'DELETE FROM utilisateurs',
                         'DELETE FROM affectations_pedagogiques']:
            self.assertNotIn(interdit, sql)
        self.assertIn('16-offres-et-demandes-inscription.sql', lire('db/docker-compose.yml'))
        self.assertIn('16-offres-et-demandes-inscription.sql', lire('db/README-db.md'))

    def test_api_explicite_les_refus_et_les_migrations_manquantes(self):
        api = lire('src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java')
        # Le refus d'affectation et les saisies invalides sont expliqués à la personne concernée.
        self.assertIn('envoyerJson(exchange, 403, erreur(ex.getMessage()))', api)
        self.assertIn('"/api/pedagogie".equals(chemin) && !detail.isEmpty()', api)
        # Le message nomme la commande a lancer. Le nombre de barres obliques est verifie
        # dans test_migrations_offres : dans le source Java, deux barres obliques donnent
        # une seule barre affichee, et une seule barre est un echappement illégal.
        self.assertIn('appliquer-migrations-PC1.cmd', api,
                      "le message d'erreur doit nommer la commande a lancer sur PC1")
        client = lire('src/main/java/fr/ihm2026/api/service/ClientAuthentificationHttps.java')
        self.assertIn('throw new ErreurAuthentification(message)', client)

    def test_atelier_ouvre_letape_demandee_apres_lecture_du_niveau(self):
        atelier = lire('src/main/java/fr/ihm2026/ui/apprenant/AtelierNiveauView.java')
        # L'étape demandée depuis le parcours (« 3 · Test final ») ne peut être ouverte qu'après
        # la lecture du niveau : c'est elle qui dit si le test est prêt et si l'accès est accordé.
        self.assertIn('etapeDemandee', atelier)
        self.assertIn('if (etape != etapeDemandee) {', atelier)
        self.assertIn('afficherEtape(etapeDemandee);', atelier)
        # Une copie ouverte suspend l'assistance IA dans la fenêtre, comme le fait le serveur.
        self.assertIn('if (Session.evaluationEnCours()) {', atelier)
        self.assertIn('setRight(null)', atelier)
        session = lire('src/main/java/fr/ihm2026/core/Session.java')
        self.assertIn('public static EtapeAtelier etapeAtelier()', session)
        self.assertIn('public static void setEtapeAtelier(EtapeAtelier etape)', session)

    def test_documentation_et_readme_alignes(self):
        doc = lire('docs/inscription-aux-offres.md')
        for attendu in ["Ce que l'administrateur ne fait plus", "S'inscrire",
                        'IHM_DEMANDE_BOT_MODE', "decide_par = 'BOT'", 'Recette',
                        'dans la même fenêtre']:
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, doc)
        readme = lire('README.md')
        self.assertNotIn('affecte chaque apprenant à un enseignant', readme)
        self.assertIn('Il n’affecte plus les apprenants aux enseignants', readme)
        self.assertIn('S’inscrire', readme)
        correction = lire('docs/parcours-correction-enseignant.md')
        self.assertNotIn('Admin affecte Samou', correction)
        self.assertIn('Enseignants et inscription', correction)


if __name__ == "__main__":
    unittest.main()
