"""Garde-fous statiques : la recette HTTPS et PostgreSQL reste indispensable."""
import unittest
from pathlib import Path

R = Path(__file__).resolve().parents[2]

def lire(p):
    return (R / p).read_text(encoding="utf-8")

class GestionComptesTest(unittest.TestCase):
    def test_reset_codes_quotas_et_revocation(self):
        sql = lire('db/15-reinitialisation-mot-de-passe.sql')
        auth = lire('src/main/java/fr/ihm2026/api/persistence/PostgresAuthentificationService.java')
        for attendu in ('empreinte_code', 'ON DELETE CASCADE', 'quotas_reinitialisation'):
            self.assertIn(attendu, sql)
        for attendu in ("gen_salt('bf', 12)", 'INTERVAL \'10 minutes\'', 'r.echecs < 5',
                        'quotaReinitialisation(c, "e:"', 'quotaReinitialisation(c, "s:"',
                        'DELETE FROM sessions WHERE utilisateur_id = ?',
                        'DELETE FROM appareils_confiance WHERE utilisateur_id = ?',
                        'DELETE FROM codes_connexion WHERE utilisateur_id = ?'):
            self.assertIn(attendu, auth)
        self.assertIn('15-reinitialisation-mot-de-passe.sql', lire('db/docker-compose.yml'))

    def test_suppression_authentifiee_atomique_et_interface(self):
        api = lire('src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java')
        debut = api.index('private void supprimerComptes(')
        fin = api.index('private void definirCompteActif(', debut)
        self.assertIn('sessionAdministrateur(exchange)', api[debut:fin])
        dep = lire('src/main/java/fr/ihm2026/api/persistence/PostgresCompteRepository.java')
        debut = dep.index('public int supprimerComptes(')
        fin = dep.index('public Compte definirActif(', debut)
        suppression = dep[debut:fin]
        for attendu in ('pg_advisory_xact_lock(20261029, 1)', 'acteurId',
                        'nombreAdministrateursActifs(c)', 'c.rollback()',
                        'DELETE FROM parcours', 'SUPPRESSION_COMPTE'):
            self.assertIn(attendu, suppression)
        ui = lire('src/main/java/fr/ihm2026/ui/admin/ComptesView.java')
        self.assertIn('Dialogue.confirmer', ui[ui.index('private void supprimerSelection()'):])
        self.assertIn('motDePasseOublie', lire('src/main/java/fr/ihm2026/ui/commun/VueConnexionServeur.java'))
