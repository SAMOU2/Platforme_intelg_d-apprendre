"""Contrôles de déploiement sans JDK. Ne remplacent pas les tests Java/HTTPS."""
from pathlib import Path
import subprocess
import unittest

ROOT = Path(__file__).resolve().parents[2]


class DeploiementCentralTest(unittest.TestCase):
    def test_connexion_automatique_sans_scan_wifi(self):
        texte = (ROOT / 'src/main/java/fr/ihm2026/ui/commun/VueConnexionServeur.java').read_text()
        self.assertNotIn('ChoixWifi', texte)
        self.assertNotIn('WifiWindows::lire', texte)
        self.assertIn('if (liste.size() == 1)', texte)
        self.assertIn('serveurs.getSelectionModel().selectFirst()', texte)
        self.assertIn('else if (liste.size() > 1)', texte)
        self.assertIn('serveurs.setVisible(false)', texte)
        self.assertIn('if (!avance.isExpanded()) adresseServeur.clear()', texte)
        self.assertIn('WifiWindows.ouvrirParametres()', texte)
        self.assertIn('adresseServeur.isDisabled()', texte)
        self.assertIn('generation != generationDecouverte', texte)

    def test_nvidia_centralise_sur_https(self):
        base = ROOT / 'src/main/java/fr/ihm2026'
        client = (base / 'api/service/ClientAuthentificationHttps.java').read_text()
        serveur = (base / 'api/service/AuthentificationApiHttps.java').read_text()
        fournisseur = (base / 'api/ia/ModeleNvidia.java').read_text()
        panneau = (base / 'ui/ia/PanneauAssistant.java').read_text()
        self.assertIn('/api/ia/aide', client)
        self.assertNotIn('NVIDIA_API_KEY', client)
        self.assertIn('authentification.valider(jeton(exchange))', serveur)
        self.assertIn('placeIa.tryAcquire()', serveur)
        self.assertIn('quotasIa.getOrDefault(id, 0) >= 30', serveur)
        self.assertIn('nvidia/nemotron-3.5-bolt-e4b', fournisseur)
        self.assertIn('put("stream", false)', fournisseur)
        self.assertIn('TaskRunner.run(() -> AideIaApi.demander(contexte)', panneau)

    def test_ui_commune_secret_theme_et_notifications(self):
        base = ROOT / 'src/main/java/fr/ihm2026'
        connexion = (base / 'ui/commun/VueConnexionServeur.java').read_text()
        self.assertIn('ChampSecret.envelopper(motDePasse)', connexion)
        self.assertIn('ChampSecret.envelopper(confirmationMotDePasse)', connexion)
        secret = (base / 'ui/commun/ChampSecret.java').read_text()
        self.assertIn('bindBidirectional(secret.textProperty())', secret)
        self.assertIn('voir.setSelected(false)', secret)
        self.assertIn('appliquerTheme(alert.getDialogPane(), owner)', (base / 'ui/commun/Dialogue.java').read_text())
        self.assertIn('Notifications.erreurActive()', (base / 'core/TaskRunner.java').read_text())
        css = (ROOT / 'src/main/resources/fr/ihm2026/css/theme-dark.css').read_text()
        self.assertIn('-fx-prompt-text-fill: #b8bcc6', css)
        self.assertIn('.combo-box-popup .list-cell', css)

    def test_syntaxe_des_lanceurs_linux(self):
        for script in (ROOT / 'postes').glob('*.sh'):
            with self.subTest(script=script.name):
                resultat = subprocess.run(['bash', '-n', str(script)], capture_output=True, text=True)
                self.assertEqual(0, resultat.returncode, resultat.stderr)

    def test_clients_sans_docker_ni_postgres_local(self):
        for prefix in ('2-', '3-', '4-'):
            for script in (ROOT / 'postes').glob(prefix + '*'):
                texte = script.read_text().lower()
                self.assertNotIn('docker compose', texte)
                self.assertNotIn('ihm_db_password', texte)
                self.assertNotIn('serveurauthentificationhttps', texte)

    def test_ip_partagee_transmise_par_les_clients(self):
        for prefix in ('2-', '3-', '4-'):
            for script in (ROOT / 'postes').glob(prefix + '*'):
                if script.suffix == '.bat':
                    script = script.with_suffix('.cmd')
                texte = script.read_text()
                self.assertIn('IHM_SERVEUR', texte)
                self.assertIn('--serveur=', texte)

    def test_les_trois_parcours_utilisent_le_serveur_choisi(self):
        texte = (ROOT / 'src/main/java/fr/ihm2026/ui/commun/VueConnexionServeur.java').read_text()
        self.assertEqual(3, texte.count('ClientReseau.choisirHote(adresse, IdentitePoste.port())'))
        self.assertIn('adresseServeur.setDisable(true)', texte)

    def test_auth_refuse_configuration_absente(self):
        resultat = subprocess.run(['bash', 'postes/authentification-PC1.sh'], cwd=ROOT,
                                  env={'PATH': '/usr/bin:/bin'}, capture_output=True, text=True)
        self.assertEqual(1, resultat.returncode)
        self.assertIn('IHM_DB_URL', resultat.stderr)
        self.assertNotIn('BUILD', resultat.stdout)

    def test_pas_de_jdk21_force_sur_linux(self):
        for script in (ROOT / 'postes').glob('*.sh'):
            self.assertNotIn('java-21-openjdk', script.read_text())

    def test_postgres_expose_uniquement_sur_pc1(self):
        compose = (ROOT / "db/docker-compose.yml").read_text()
        self.assertIn('127.0.0.1:${IHM_DB_PORT:-5433}:5432', compose)

    def test_wrappers_bat_conservent_role_et_code_retour(self):
        for prefix in ('1-', '2-', '3-', '4-'):
            bat = next((ROOT / 'postes').glob(prefix + '*.bat'))
            texte = bat.read_text()
            cible = bat.with_suffix('.cmd')
            self.assertTrue(cible.is_file())
            self.assertIn('call "%~dp0' + cible.name + '"', texte)
            self.assertIn('exit /b %errorlevel%', texte)

    def test_clients_windows_propagation_echec(self):
        for prefix in ('2-', '3-', '4-'):
            cmd = next((ROOT / 'postes').glob(prefix + '*.cmd'))
            texte = cmd.read_text()
            self.assertIn('pause\n    exit /b 1', texte)
            self.assertTrue(texte.endswith('exit /b 0\n'))

    def test_formulaire_defilant_et_actions_apres_les_champs(self):
        texte = (ROOT / 'src/main/java/fr/ihm2026/ui/commun/VueConnexionServeur.java').read_text()
        self.assertIn('new ScrollPane(contenu)', texte)
        self.assertIn('defilement.setFitToWidth(true)', texte)
        self.assertIn('ligne, actionsCompte, etat, aide', texte)
        self.assertIn('decouverteServeur.setLabelFor(serveurs)', texte)

    def test_bat_auth_diagnostic_sans_affichage_des_secrets(self):
        texte = (ROOT / 'postes/authentification-PC1.bat').read_text()
        self.assertIn('Variable obligatoire absente : %%V', texte)
        self.assertIn('if not exist "%IHM_TLS_KEYSTORE%"', texte)
        self.assertNotIn('-B -q', texte)
        self.assertNotIn('echo %IHM_DB_PASSWORD%', texte)

    def test_configuration_privee_exclue_de_git(self):
        for chemin in ('postes/configuration-PC1.local.cmd', 'postes/secrets/serveur.p12'):
            resultat = subprocess.run(['git', 'check-ignore', '--no-index', chemin],
                                      cwd=ROOT, capture_output=True, text=True)
            self.assertEqual(0, resultat.returncode, chemin)

    def test_configuration_locale_chargee_avant_controles(self):
        texte = (ROOT / 'postes/authentification-PC1.bat').read_text()
        self.assertLess(texte.index('call "%~dp0configuration-PC1.local.cmd"'),
                        texte.index('for %%V in (IHM_DB_URL'))
        self.assertIn('configuration-PC1.exemple.cmd', texte)
        modele = (ROOT / 'postes/configuration-PC1.exemple.cmd').read_text()
        # Pas de secret actif livre dans le modele.
        actives = [l for l in modele.splitlines() if l.lower().startswith('set ')]
        self.assertFalse(any('PASSWORD=' in l for l in actives))
        self.assertFalse(any('IHM_TLS_KEYSTORE=' in l for l in actives))

    def test_ip_affichee_avant_maven(self):
        texte = (ROOT / 'postes/1-serveur-PC1.cmd').read_text()
        self.assertLess(texte.index('call "%~dp0afficher-ip-PC1.cmd"'),
                        texte.index('call "%~dp0environnement.cmd"'))
        aide = (ROOT / 'postes/afficher-ip-PC1.cmd').read_text()
        self.assertIn('afficher-ip-PC1.ps1', aide)
        self.assertIn('ipconfig', aide)
        self.assertIn('sans protocole ni port', aide)

    def test_pin_uniquement_dans_le_mode_activation(self):
        vue = (ROOT / 'src/main/java/fr/ihm2026/ui/commun/VueConnexionServeur.java').read_text()
        self.assertIn('codeInvitation.setVisible(modeActivation)', vue)
        inscription = vue.split('private void sInscrire()', 1)[1].split('private void actualiserModeInscription()', 1)[0]
        self.assertNotIn('String pin', inscription)
        self.assertNotIn('pin.strip()', inscription)
        api = (ROOT / 'src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java').read_text()
        self.assertIn('case "/api/auth/activation"', api)
        self.assertNotIn('texte(corps, "pinInvitation")', api)

    def test_tls_reste_obligatoire(self):
        serveur = (ROOT / 'src/main/java/fr/ihm2026/api/service/AuthentificationApiHttps.java').read_text()
        client = (ROOT / 'src/main/java/fr/ihm2026/api/service/ClientAuthentificationHttps.java').read_text()
        self.assertIn('exchange instanceof HttpsExchange', serveur)
        self.assertIn('HTTPS est obligatoire.', serveur)
        self.assertNotIn('TrustAll', client)
        self.assertNotIn('disableHostnameVerification', client)


if __name__ == '__main__':
    unittest.main(verbosity=2)
