"""Contrôles structurels de la file d'inscription scalable.

Ils vérifient la présence des garde-fous dans les sources, mais ne remplacent ni les
tests PostgreSQL, ni les mesures de charge, ni la validation SMTP sous Windows.
"""
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
JAVA = ROOT / 'src/main/java/fr/ihm2026'


class ScalabiliteInscriptionsTest(unittest.TestCase):
    def test_bot_explicable_sans_enseignant_auto(self):
        source = (JAVA / 'api/service/BotInscription.java').read_text()
        for attendu in [
                'IHM_INSCRIPTION_BOT_MODE', 'IHM_INSCRIPTION_DOMAINES_AUTO',
                'IHM_INSCRIPTION_DOMAINES_BLOQUES', 'Mode.DOMAINES',
                'Rôle enseignant privilégié', 'ACCEPTATION_CONSEILLEE']:
            self.assertIn(attendu, source)
        self.assertNotIn('NVIDIA', source.split('public Analyse analyser', 1)[1])

    def test_bot_limite_aux_apprenants_dans_sql(self):
        source = (JAVA / 'api/persistence/PostgresCompteRepository.java').read_text()
        self.assertIn('accepterInscriptionParBot', source)
        self.assertIn("(? = false OR role = 'APPRENANT'::role_utilisateur)", source)
        self.assertIn('ACCEPTATION_BOT_INSCRIPTION', source)
        self.assertIn('q.setNull(5, java.sql.Types.BIGINT)', source)

    def test_liste_admin_filtree_et_paginee_en_sql(self):
        source = (JAVA / 'api/persistence/PostgresCompteRepository.java').read_text()
        for attendu in ['record FiltreComptes', 'record PageComptes', 'LIMIT ? OFFSET ?',
                        "split_part(email, '@', 2)", 'GROUP BY statut_inscription',
                        'cree_le ASC, id ASC']:
            self.assertIn(attendu, source)
        # Regression Windows/javac : dans une regex Java, le point littéral doit être `\\.` dans la source.
        self.assertIn(r'(?:\\.[a-z0-9]', source)
        self.assertNotIn(r'(?:\.[a-z0-9]', source)
        handler = (JAVA / 'api/service/AuthentificationApiHttps.java').read_text()
        self.assertIn('comptes.lister(lireFiltreComptes(exchange))', handler)
        self.assertNotIn('comptes.lister().stream()', handler)

    def test_decisions_par_lot_bornees_et_rapports_email(self):
        handler = (JAVA / 'api/service/AuthentificationApiHttps.java').read_text()
        self.assertIn('"/api/admin/comptes/decisions"', handler)
        self.assertIn('LOT_DECISIONS_MAX = 20', handler)
        self.assertIn('Décision enregistrée, mais e-mail non envoyé.', handler)
        client = (JAVA / 'api/service/ClientAuthentificationHttps.java').read_text()
        self.assertIn('deciderInscriptions(List<String> emails, boolean acceptee)', client)
        self.assertIn('emailsNonEnvoyes', client)

    def test_interface_admin_ne_charge_plus_tout_au_depart(self):
        source = (JAVA / 'ui/admin/ComptesView.java').read_text()
        self.assertNotIn('FXCollections.observableArrayList(AdminApi.comptes())', source)
        for attendu in ['Filtrer', 'Réinitialiser les filtres', 'Page précédente',
                        'Page suivante', 'Accepter la sélection', 'Refuser la sélection',
                        'Avis du bot de tri', 'AdminApi.comptes(filtre)']:
            self.assertIn(attendu, source)

    def test_reponse_inscription_peut_etre_pre_acceptee_inactive(self):
        client = (JAVA / 'api/service/ClientAuthentificationHttps.java').read_text()
        self.assertIn('!"EN_ATTENTE".equals(statut) && !"ACCEPTE".equals(statut)', client)
        self.assertIn('reponse.path("actif").asBoolean(true)', client)
        vue = (JAVA / 'ui/commun/VueConnexionServeur.java').read_text()
        self.assertIn('compte.message()', vue)

    def test_migration_index_non_destructive(self):
        sql = (ROOT / 'db/13-scalabilite-comptes.sql').read_text()
        for index in ['ix_utilisateurs_admin_statut_date',
                      'ix_utilisateurs_admin_role_statut_date',
                      'ix_utilisateurs_admin_domaine']:
            self.assertIn(index, sql)
        for interdit in ['DROP TABLE', 'DELETE FROM utilisateurs', 'TRUNCATE']:
            self.assertNotIn(interdit, sql)
        self.assertIn('13-scalabilite-comptes.sql',
                      (ROOT / 'db/docker-compose.yml').read_text())


if __name__ == '__main__':
    unittest.main()
