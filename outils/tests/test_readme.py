import re
import unittest
from pathlib import Path


RACINE = Path(__file__).resolve().parents[2]
README = RACINE / "README.md"


class ReadmeTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.texte = README.read_text(encoding="utf-8")

    def test_quatre_sections_seulement(self):
        titres = re.findall(r"^## (.+)$", self.texte, flags=re.MULTILINE)
        self.assertEqual(
            titres,
            [
                "1. L’idée de l’application",
                "2. Architecture",
                "3. Outils nécessaires sur le PC",
                "4. Comment lancer la plateforme",
            ],
        )

    def test_format_court_pour_un_nouveau_lecteur(self):
        self.assertLessEqual(len(self.texte.splitlines()), 180)
        self.assertLessEqual(len(self.texte.split()), 1400)
        for ancien_sujet in (
            "Notes de version",
            "SmokeTest",
            "MockBackend",
            "Documents de conception associés",
            "Direction visuelle macOS",
        ):
            with self.subTest(ancien_sujet=ancien_sujet):
                self.assertNotIn(ancien_sujet, self.texte)

    def test_outils_et_scripts_indispensables_sont_nommes(self):
        for attendu in (
            "JDK 25 LTS complet",
            "Docker Desktop",
            "mvnw.cmd",
            "postes\\configuration-PC1.exemple.cmd",
            "postes\\configuration-PC1.local.cmd",
            "postes\\authentification-PC1.bat",
            "postes\\1-serveur-PC1.bat",
            "postes\\2-administrateur-PC2.bat",
            "postes\\3-enseignant-PC3.bat",
            "postes\\4-apprenant-PC4.bat",
            "postes\\verifier.cmd",
        ):
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, self.texte)

    def test_scripts_de_lancement_cites_existent(self):
        for chemin in (
            "postes/configuration-PC1.exemple.cmd",
            "postes/preparer-identite-reseau-PC1.bat",
            "postes/approuver-autorite-client.bat",
            "postes/authentification-PC1.bat",
            "postes/1-serveur-PC1.bat",
            "postes/2-administrateur-PC2.bat",
            "postes/3-enseignant-PC3.bat",
            "postes/4-apprenant-PC4.bat",
            "postes/verifier.cmd",
        ):
            with self.subTest(chemin=chemin):
                self.assertTrue((RACINE / chemin).is_file())


if __name__ == "__main__":
    unittest.main()
