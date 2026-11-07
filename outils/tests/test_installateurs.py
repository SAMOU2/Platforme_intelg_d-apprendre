"""Contrats statiques du générateur ; ne remplacent pas une recette Windows des MSI."""
import re
import unittest
from pathlib import Path
from xml.etree import ElementTree

ROOT = Path(__file__).resolve().parents[2]
SCRIPT = ROOT / "outils/installateur/creer-installateurs.ps1"


class InstallateursTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.script = SCRIPT.read_text(encoding="utf-8")

    def test_trois_roles_et_produits_independants(self):
        for role, poste in [("apprenant", "PC4"), ("enseignant", "PC3"),
                            ("administrateur", "PC2")]:
            self.assertRegex(self.script, rf"Role='{role}'; Poste='{poste}'")
        uuids = re.findall(r"UUID='([0-9a-f-]{36})'", self.script)
        self.assertEqual(len(set(uuids)), 3)
        self.assertIn("--win-upgrade-uuid", self.script)
        self.assertIn("--win-per-user-install", self.script)
        self.assertIn("--runtime-image", self.script)

    def test_confiance_fondee_sur_ca_publique_verifiee(self):
        for garde in ("CertificateAuthority", "HasPrivateKey", "SHA256", "$attendue -ne $empreinte",
                      "plateforme-ca-", "Join-Path $runtime 'lib\\security\\cacerts'"):
            self.assertIn(garde, self.script)
        self.assertNotIn("configuration-PC1.local.cmd", self.script)
        self.assertNotIn("IHM_TLS_CA_PASSWORD", self.script)

    def test_lanceur_et_dependances(self):
        pom = ElementTree.parse(ROOT / "pom.xml").getroot()
        ns = {"m": "http://maven.apache.org/POM/4.0.0"}
        plugins = pom.findall("./m:build/m:plugins/m:plugin", ns)
        jar = next(p for p in plugins if p.findtext("m:artifactId", namespaces=ns) == "maven-jar-plugin")
        self.assertEqual(jar.findtext(".//m:addClasspath", namespaces=ns), "true")
        self.assertEqual(jar.findtext(".//m:mainClass", namespaces=ns), "fr.ihm2026.Launcher")
        for attendu in ("dependency:copy-dependencies", "-DincludeScope=runtime", "--main-jar",
                        "--arguments", "javafx-graphics-*-win.jar"):
            self.assertIn(attendu, self.script)
        self.assertTrue((ROOT / "outils/installateur/creer-installateurs.cmd").is_file())


if __name__ == "__main__":
    unittest.main()
