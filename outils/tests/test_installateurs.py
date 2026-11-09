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

    def test_installation_par_defaut_sans_repertoire_intermediaire(self):
        # Le chemin personnalisé Programs/<rôle> introduisait un répertoire utilisateur
        # sans RemoveFolder dans les fragments jpackage et faisait échouer ICE64.
        debut = self.script.index("Executer $jpackage @(")
        fin = self.script.index("'--win-upgrade-uuid', $r.UUID)", debut)
        arguments = self.script[debut:fin]
        self.assertIn("'--win-per-user-install'", arguments)
        self.assertIn("'--verbose'", arguments)
        self.assertNotIn("'--install-dir'", arguments)
        self.assertNotIn("-sice:ICE64", arguments)
        test_msi = (ROOT / "outils/installateur/tester-installateurs.ps1").read_text(encoding="utf-8")
        self.assertIn("Join-Path $env:LOCALAPPDATA $r.Nom", test_msi)
        self.assertIn("Test-Path -LiteralPath $exe -PathType Leaf", test_msi)
        self.assertIn("Poste deja present", test_msi)

    def test_confiance_fondee_sur_ca_publique_verifiee(self):
        for garde in ("CertificateAuthority", "HasPrivateKey", "SHA256", "$attendue -ne $empreinte",
                      "plateforme-ca-", "Join-Path $runtime 'lib\\security\\cacerts'"):
            self.assertIn(garde, self.script)
        self.assertNotIn("configuration-PC1.local.cmd", self.script)
        self.assertNotIn("IHM_TLS_CA_PASSWORD", self.script)

    def test_fabrication_assistee_et_integration_windows(self):
        self.assertIn("WiX Toolset v3*", self.script)
        self.assertIn("postes\\secrets\\autorite-ihm.crt", self.script)
        self.assertIn("SHA256SUMS.txt", self.script)
        workflow = (ROOT / ".github/workflows/installateurs-windows.yml").read_text(encoding="utf-8")
        self.assertIn("workflow_dispatch:", workflow)
        self.assertIn("pull_request:", workflow)
        self.assertIn("github.event_name == 'workflow_dispatch'", workflow)
        self.assertIn("tester-installateurs.ps1", workflow)
        self.assertIn("empreinte_sha256", workflow)
        test_msi = (ROOT / "outils/installateur/tester-installateurs.ps1").read_text(encoding="utf-8")
        for verification in ("msiexec.exe", "'/i'", "'/x'", "plateforme-ca-", "javafx-graphics-*-win.jar", "--role="):
            self.assertIn(verification, test_msi)

    def test_wix_integre_verifie_avant_execution(self):
        url = "https://github.com/wixtoolset/wix3/releases/download/wix3141rtm/wix314-binaries.zip"
        self.assertIn(url, self.script)
        self.assertIn("6ac824e1642d6f7277d0ed7ea09411a508f6116ba6fae0aa5f2c7daa2ff43d31", self.script)
        self.assertIn("SansTelechargementWiX", self.script)
        self.assertIn("ForcerWiXIntegre", self.script)
        self.assertIn("Get-FileHash -LiteralPath $archive -Algorithm SHA256", self.script)
        self.assertLess(self.script.index("if ($hashWix -ne $wixSha256)"), self.script.index("Expand-Archive"))
        self.assertLess(self.script.index("Expand-Archive"), self.script.index("$env:PATH = $wixChoisi"))
        self.assertIn("Remove-Item -LiteralPath $dossierTempWix", self.script)
        self.assertIn("/outils/installateur/.wix/", (ROOT / ".gitignore").read_text())
        workflow = (ROOT / ".github/workflows/installateurs-windows.yml").read_text(encoding="utf-8")
        self.assertIn("Refuser un cache WiX corrompu", workflow)
        self.assertIn("-ForcerWiXIntegre", workflow)

    def test_publication_release_msi_sans_executables_ni_secrets(self):
        publication = (ROOT / "outils/installateur/publier-msi.ps1").read_text(encoding="utf-8")
        for attendu in ("SAMOU2/Platforme_intelg_d-apprendre", "Plateforme $role-$version.msi",
                        "D0-CF-11-E0-A1-B1-1A-E1", "Get-FileHash", "SHA256SUMS.txt",
                        "gh release create", "--draft", "--target $commit", "repos/$depot/commits/$commit",
                        "git ls-remote --exit-code --refs", "if ($codeTag -ne 2)"):
            self.assertIn(attendu, publication)
        self.assertNotIn("--clobber", publication)
        self.assertNotIn("IHM_TLS_CA_PASSWORD", publication)
        self.assertTrue((ROOT / "outils/installateur/publier-msi.cmd").is_file())
        self.assertIn("*.msi", (ROOT / ".gitignore").read_text(encoding="utf-8"))

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
