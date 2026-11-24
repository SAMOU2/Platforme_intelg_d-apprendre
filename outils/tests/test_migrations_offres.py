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
        # Le message renvoie vers la commande a lancer, pas vers des numeros de migration.
        self.assertIn('appliquer-migrations-PC1.cmd', api)
        self.assertNotIn('migrations 10, 11, 12 et 16', api,
                         "un numero de migration fige devient faux a la migration suivante")
        ps = lire('postes/appliquer-migrations-PC1.ps1')
        for migration in ('10-correction-enseignant.sql', '11-contenus-niveaux.sql',
                          '12-parcours-enseignants.sql', '16-offres-et-demandes-inscription.sql'):
            with self.subTest(migration=migration):
                self.assertIn(migration, ps)

    def test_mise_a_niveau_automatique_au_demarrage_du_service(self):
        """Le service applique seul les migrations absentes : plus de 503 au premier lancement."""
        auth = lire('postes/authentification-PC1.bat')
        ps = lire('postes/appliquer-migrations-PC1.ps1')
        self.assertIn('appliquer-migrations-PC1.ps1" -Auto', auth.replace("'", '"'))
        # Le controle a lieu apres le demarrage de Docker et avant la compilation.
        self.assertLess(auth.index('demarrer-docker-PC1.bat'), auth.index('appliquer-migrations-PC1.ps1'))
        self.assertLess(auth.index('appliquer-migrations-PC1.ps1'), auth.index('"%MVN%"'))
        # Desactivable, et une base injoignable ne bloque pas le demarrage.
        self.assertIn('IHM_MIGRATIONS_AUTO', auth)
        self.assertIn('[switch]$Auto', ps)
        self.assertIn('exit $CodeBaseInjoignable', ps)
        self.assertIn('$CodeBaseInjoignable = 2', ps)
        # Le mode silencieux n'applique jamais les offres de demonstration.
        self.assertIn("$estDemonstration = ($migration -eq '17-offres-demonstration.sql')", ps)
        self.assertIn("if (-not $estDemonstration -or $AvecDemonstration)", ps)
        # Aucune destruction, y compris dans le nouveau mode.
        for interdit in ('down -v', 'volume rm', 'DROP TABLE', 'TRUNCATE', 'DELETE FROM'):
            with self.subTest(interdit=interdit):
                self.assertNotIn(interdit, ps)
        self.assertNotIn('down -v', auth)

    def test_conteneur_arrete_est_redemarre(self):
        """« container ... is not running » : le conteneur doit etre relance, donnees conservees."""
        ps = lire('postes/appliquer-migrations-PC1.ps1')
        # Lecture de l'etat par le code retour de la commande, pas seulement par le texte.
        self.assertIn("$inspection = Invoke-CommandeNative $dockerExe @('inspect', '--format', '{{.State.Running}}', $conteneur)", ps)
        self.assertIn("if ($inspection.Sortie -notmatch 'true')", ps)
        self.assertIn("@('start', $conteneur)", ps)
        # Attente que PostgreSQL accepte les connexions apres le redemarrage.
        self.assertIn('$essai = 1; $essai -le 30', ps)
        self.assertIn("Start-Sleep -Seconds 1", ps)
        # Aucun risque pour les donnees : jamais de suppression de conteneur ni de volume.
        for interdit in ('rm -f', 'rm ', 'down -v', 'volume rm', 'container prune'):
            with self.subTest(interdit=interdit):
                self.assertNotIn(interdit, ps)

    def test_aucun_echappement_java_invalide_dans_les_messages(self):
        """postes\\appliquer... : une seule barre oblique dans le source ne compile pas."""
        api = lire('src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java')
        valides = set('btnfr"\'\\')
        problemes = []
        i = 0
        while i < len(api):
            if api[i] == '"':
                i += 1
                while i < len(api) and api[i] != '"':
                    if api[i] == '\\':
                        if i + 1 < len(api) and api[i + 1] not in valides:
                            problemes.append((api[:i].count('\n') + 1, api[i:i + 2]))
                        i += 2
                        continue
                    i += 1
            i += 1
        self.assertEqual(problemes, [], "echappements Java invalides : %s" % problemes)
        # Un chemin Windows affiche s'ecrit avec deux barres obliques dans le source :
        # une seule barre devant a (postes\appliquer) est refusee par javac.
        # Deux barres obliques consecutives : dans le source comme a l'affichage.
        motif = 'postes' + chr(92) * 2
        self.assertIn(motif + 'appliquer-migrations-PC1.cmd', api)
        self.assertIn(motif + 'authentification-PC1.bat', api)
        self.assertNotIn('postes' + chr(92) + 'appliquer', api)


    def test_sonde_impossible_ne_bloque_pas_les_migrations(self):
        """« relation offres_enseignants does not exist » ne doit pas arreter le script."""
        ps = lire('postes/appliquer-migrations-PC1.ps1')
        # Les commandes natives sont capturees avec leur sortie d'erreur, jamais laissees
        # remonter comme erreur bloquante de PowerShell.
        self.assertIn('function Invoke-CommandeNative', ps)
        self.assertIn('function Invoke-PsqlFichier', ps)
        self.assertIn("2>&1", ps)
        self.assertIn("$ErrorActionPreference = 'Continue'", ps)
        # Une seule question distingue « base injoignable » d'une sonde impossible.
        self.assertIn("'SELECT 1;'", ps)
        # Une sonde en echec sur une table absente vaut « migration absente », pas « echec ».
        self.assertIn("$appliquee = ($sonde.Code -eq 0 -and $sonde.Sortie -eq 't')", ps)
        # Le message de PostgreSQL est repris tel quel en cas d'echec reel d'application.
        self.assertIn('$application.Sortie', ps)

    def test_sauvegardes_locales_jamais_committees(self):
        """Le pg_dump contient des donnees de comptes : il ne doit jamais partir dans Git."""
        self.assertIn('sauvegardes/', lire('.gitignore'))
        self.assertIn("'sauvegardes'", lire('postes/appliquer-migrations-PC1.ps1'))

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
        # parcours_id est UNIQUE : un ON CONFLICT (enseignant_id, langage_id) seul
        # laisserait echouer l'insertion si un autre enseignant tient deja le parcours.
        self.assertIn('WHERE NOT EXISTS (SELECT 1 FROM parcours_enseignants pe WHERE pe.parcours_id = p.id)', sql)
        self.assertIn('ON CONFLICT DO NOTHING', sql)
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
