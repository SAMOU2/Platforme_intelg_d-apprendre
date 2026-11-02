"""Contrôles structurels de l'appareil de confiance de 48 heures.

Ils protègent les invariants visibles dans les sources. Ils ne remplacent ni la
compilation Maven, ni le test PostgreSQL d'intégration, ni la recette Windows.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/fr/ihm2026'


class AppareilConfiance48hTest(unittest.TestCase):
    def test_mot_de_passe_verifie_avant_la_confiance(self):
        source = (JAVA / 'api/persistence/PostgresAuthentificationService.java').read_text()
        debut = source.split('public DebutConnexion commencerConnexion', 1)[1]
        debut = debut.split('public ResultatConnexion confirmerCodeConnexion', 1)[0]
        self.assertLess(debut.index('verifierCompte(emailNormalise, motDePasse)'),
                        debut.index('ouvrirSessionAvecAppareilConfiance'))
        self.assertIn("u.statut_inscription = 'ACCEPTE'", source)
        self.assertIn('AND u.actif', source)
        self.assertIn("now() + INTERVAL '48 hours'", source)

    def test_utilisation_ne_prolonge_pas_expiration(self):
        source = (JAVA / 'api/persistence/PostgresAuthentificationService.java').read_text()
        utilisation = source.split(
            'private Optional<ResultatConnexion> ouvrirSessionAvecAppareilConfiance', 1)[1]
        utilisation = utilisation.split('/** Révoque un appareil de confiance', 1)[0]
        self.assertIn('SET derniere_utilisation = now()', utilisation)
        self.assertNotIn('expire_le =', utilisation)
        self.assertIn('SET revoque_le = now()', source)
        comptes = (JAVA / 'api/persistence/PostgresCompteRepository.java').read_text()
        self.assertIn('revoquerAppareilsConfiance(connexion, actualise.id())', comptes)
        self.assertIn('WHERE utilisateur_id = ? AND revoque_le IS NULL', comptes)

    def test_routes_https_et_reponses(self):
        handler = (JAVA / 'api/service/AuthentificationApiHttps.java').read_text()
        for attendu in ['commencerConnexion', 'jetonConfiance', 'memoriserAppareil',
                        'libelleAppareil', '/api/auth/appareil-confiance/oublier',
                        'confianceExpireLe']:
            self.assertIn(attendu, handler)
        client = (JAVA / 'api/service/ClientAuthentificationHttps.java').read_text()
        for attendu in ['record DebutConnexion', 'record AppareilConfiance',
                        'oublierAppareilConfiance', 'reponse.path("jeton").isTextual()']:
            self.assertIn(attendu, client)

    def test_stockage_local_sans_mot_de_passe(self):
        memoire = (JAVA / 'reseau/MemoireAppareilConfiance.java').read_text()
        self.assertIn('appareils-confiance', memoire)
        self.assertIn('Session.Role role', memoire)
        self.assertNotIn('motDePasse', memoire)
        self.assertNotIn('codeConnexion', memoire)
        deux_barres = chr(92) * 2
        self.assertIn('split("' + deux_barres + '|"', memoire)
        service = (JAVA / 'api/persistence/PostgresAuthentificationService.java').read_text()
        self.assertIn('replaceAll("[' + deux_barres + 'p{Cntrl}]"', service)

    def test_interface_explicite_et_accessible(self):
        vue = (JAVA / 'ui/commun/VueConnexionServeur.java').read_text()
        for attendu in ['Faire confiance à ce poste pendant 2 jours',
                        'Oublier ce poste de confiance',
                        'connexion-appareil-confiance',
                        'Ne cochez pas cette case sur un poste partagé',
                        'MemoireAppareilConfiance.lire',
                        'MemoireAppareilConfiance.ecrire']:
            self.assertIn(attendu, vue)

    def test_migration_non_destructive_et_documentation(self):
        sql = (ROOT / 'db/14-appareils-confiance-48h.sql').read_text()
        for attendu in ['CREATE TABLE IF NOT EXISTS appareils_confiance',
                        'empreinte_jeton       TEXT NOT NULL UNIQUE',
                        'expire_le             TIMESTAMPTZ NOT NULL',
                        'revoque_le']:
            self.assertIn(attendu, sql)
        for interdit in ['DROP TABLE', 'DELETE FROM utilisateurs', 'TRUNCATE']:
            self.assertNotIn(interdit, sql)
        self.assertIn('14-appareils-confiance-48h.sql',
                      (ROOT / 'db/docker-compose.yml').read_text())
        doc = (ROOT / 'docs/appareil-confiance-48h.md').read_text()
        self.assertIn('le mot de passe est revérifié à chaque ouverture', doc)
        self.assertIn('expiration\n**absolue** à 48 heures', doc)


if __name__ == '__main__':
    unittest.main()
