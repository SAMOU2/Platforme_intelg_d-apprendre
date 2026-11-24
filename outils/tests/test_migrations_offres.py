"""Contrôles statiques : script d'application des migrations et offres de démonstration."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def lire(chemin: str) -> str:
    return (ROOT / chemin).read_text(encoding='utf-8')


class ApplicationMigrationsTest(unittest.TestCase):
    def test_script_present_et_sans_destruction(self):
        cmd = lire('postes/appliquer-migrations-PC1.cmd')
        ps = lire('postes/appliquer-migrations-PC1.ps1')
        self.assertIn('appliquer-migrations-PC1.ps1', cmd)
        self.assertIn('configuration-PC1.local.cmd', cmd)
        # Aucune suppression : ni volume, ni table, ni donnée.
        for interdit in ('down -v', 'volume rm', 'DROP TABLE', 'TRUNCATE', 'DELETE FROM'):
            with self.subTest(interdit=interdit):
                self.assertNotIn(interdit, ps)
        # Sauvegarde avant écriture et simulation possible.
        self.assertIn('pg_dump', ps)
        self.assertIn('Simulation', ps)
        self.assertIn('-v ON_ERROR_STOP=1', ps)

    def test_verifie_les_migrations_citees_par_le_message_d_erreur(self):
        api = lire('src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java')
        self.assertIn('migrations 10, 11, 12 et 16', api)
        ps = lire('postes/appliquer-migrations-PC1.ps1')
        for migration in ('10-correction-enseignant.sql', '11-contenus-niveaux.sql',
                          '12-parcours-enseignants.sql', '16-offres-et-demandes-inscription.sql'):
            with self.subTest(migration=migration):
                self.assertIn(migration, ps)

    def test_documentation_cite_le_script(self):
        for chemin in ('README.md', 'db/README-db.md'):
            with self.subTest(fichier=chemin):
                self.assertIn('appliquer-migrations-PC1', lire(chemin))


class OffresDemonstrationTest(unittest.TestCase):
    def test_migration_17_non_destructive_et_idempotente(self):
        sql = lire('db/17-offres-demonstration.sql')
        self.assertIn('INSERT INTO offres_enseignants', sql)
        self.assertIn('INSERT INTO parcours_enseignants', sql)
        self.assertIn('ON CONFLICT', sql, "relancer le script ne doit pas créer de doublon")
        self.assertIn('BEGIN;', sql)
        self.assertIn('COMMIT;', sql)
        for interdit in ('DROP TABLE', 'TRUNCATE', 'DELETE FROM', 'UPDATE '):
            with self.subTest(interdit=interdit):
                self.assertNotIn(interdit, sql)
        # Les trois langages du projet sont couverts.
        for langage in ("'python'", "'java'", "'cpp'"):
            with self.subTest(langage=langage):
                self.assertIn(langage, sql)

    def test_migration_17_referencee_comme_les_autres(self):
        compose = lire('db/docker-compose.yml')
        self.assertIn('17-offres-demonstration.sql', compose)
        # L'ordre des montages Docker compte : 17 après 16.
        self.assertLess(compose.index('16-offres-et-demandes-inscription.sql'),
                        compose.index('17-offres-demonstration.sql'))
        self.assertIn('17-offres-demonstration.sql', lire('db/README-db.md'))

    def test_les_offres_ne_sont_pas_creees_pour_un_enseignant_inexistant(self):
        sql = lire('db/17-offres-demonstration.sql')
        self.assertIn("u.role = 'ENSEIGNANT'", sql,
                      "sans ce filtre, un compte apprenant porterait une offre")


if __name__ == '__main__':
    unittest.main()
