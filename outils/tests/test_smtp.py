"""Contrôles de la configuration SMTP sans JDK : cohérence des scripts et du modèle."""
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]


def lire(chemin: str) -> str:
    return (ROOT / chemin).read_text(encoding='utf-8')


class SmtpTest(unittest.TestCase):
    def test_modele_declare_tous_les_parametres_smtp(self):
        modele = lire('postes/configuration-PC1.exemple.cmd')
        for variable in ('IHM_SMTP_HOST', 'IHM_SMTP_FROM', 'IHM_SMTP_USER',
                         'IHM_SMTP_PASSWORD', 'IHM_SMTP_PORT', 'IHM_SMTP_SSL'):
            with self.subTest(variable=variable):
                self.assertIn(variable, modele)

    def test_script_de_test_smtp_present_et_coherent(self):
        cmd = lire('postes/tester-smtp.cmd')
        java = lire('postes/TesterSmtp.java')
        self.assertIn('configuration-PC1.local.cmd', cmd)
        self.assertIn('IHM_SMTP_HOST', cmd)
        self.assertIn('IHM_SMTP_PASSWORD', cmd, "un utilisateur sans mot de passe doit être refusé")
        self.assertIn('environnement.cmd', cmd)
        self.assertIn('ServiceEmail', java)
        self.assertIn('System.exit(1)', java, "un échec d'envoi doit sortir en erreur")
        # La compilation doit être explicite : « java Fichier.java » n'exécute pas avec les
        # jars du classpath (NoClassDefFoundError jakarta/mail/MessagingException constaté).
        self.assertIn('javac.exe', cmd, "compiler avec javac, puis exécuter avec le classpath complet")
        self.assertIn('target\\smtp-test-classes', cmd)
        self.assertNotIn('"%~dp0TesterSmtp.java" "%~1"', cmd,
                         "le mode source-file de java n'apporte pas les dépendances au runtime")
        self.assertIn('TesterSmtp "%~1"', cmd, "exécuter la classe compilée, pas le fichier source")

    def test_service_email_ne_perd_pas_la_cause(self):
        service = lire('src/main/java/fr/ihm2026/api/service/ServiceEmail.java')
        # SMTPS (port 465) et STARTTLS (port 587) doivent tous deux être possibles.
        self.assertIn('mail.smtp.ssl.enable', service)
        self.assertIn('mail.smtp.starttls.required', service)
        self.assertIn('IHM_SMTP_SSL', service)
        # La cause réelle de l'échec doit apparaître dans le message (diagnostic sans journal).
        self.assertIn('ex.getMessage()', service)

    def test_reinitialisation_journalise_la_cause(self):
        api = lire('src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java')
        self.assertIn('envoi du courriel impossible', api)
        self.assertIn('15-reinitialisation-mot-de-passe.sql', api,
                      "une migration manquante doit être nommée explicitement")
        self.assertIn('[smtp] Configuration', api,
                      "le service doit annoncer au démarrage si les codes peuvent partir")
        self.assertIn('messageExplicite', api)

    def test_documentation_de_depannage_presente(self):
        doc = lire('docs/recuperation-et-suppression-comptes.md')
        for attendu in ('Aucun code ne part', '[smtp]', 'IHM_SMTP_SSL', 'tester-smtp.cmd'):
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, doc)
        readme = lire('README.md')
        self.assertIn('tester-smtp.cmd', readme)

    def test_script_poste_existe_avec_fins_de_ligne_windows(self):
        binaire = (ROOT / 'postes/tester-smtp.cmd').read_bytes()
        self.assertIn(b'\r\n', binaire,
                      "un .cmd en LF ne fonctionne pas de façon fiable sous cmd.exe")


if __name__ == '__main__':
    unittest.main()
