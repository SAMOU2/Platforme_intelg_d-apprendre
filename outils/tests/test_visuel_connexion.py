import struct
import unittest
from pathlib import Path


RACINE = Path(__file__).resolve().parents[2]
IMAGE = RACINE / "src/main/resources/fr/ihm2026/images/connexion-programmation.jpg"
COMPOSANT = RACINE / "src/main/java/fr/ihm2026/ui/commun/PanneauVisuelConnexion.java"
VUE = RACINE / "src/main/java/fr/ihm2026/ui/commun/VueConnexionServeur.java"
CSS = RACINE / "src/main/resources/fr/ihm2026/css/app.css"


def dimensions_jpeg(donnees):
    index = 2
    while index + 9 < len(donnees):
        if donnees[index] != 0xFF:
            index += 1
            continue
        marqueur = donnees[index + 1]
        if marqueur in {0xC0, 0xC1, 0xC2}:
            hauteur, largeur = struct.unpack(">HH", donnees[index + 5:index + 9])
            return largeur, hauteur
        if marqueur in {0xD8, 0xD9} or 0xD0 <= marqueur <= 0xD7:
            index += 2
            continue
        longueur = struct.unpack(">H", donnees[index + 2:index + 4])[0]
        index += 2 + longueur
    raise AssertionError("Dimensions JPEG introuvables")


class VisuelConnexionTest(unittest.TestCase):
    def test_image_nettoyee_et_legere(self):
        donnees = IMAGE.read_bytes()
        self.assertEqual(b"\xff\xd8", donnees[:2])
        self.assertEqual((998, 470), dimensions_jpeg(donnees))
        self.assertLessEqual(len(donnees), 150_000)

    def test_seuls_enseignant_et_apprenant_sont_eligibles(self):
        source = COMPOSANT.read_text(encoding="utf-8")
        self.assertIn(
            "role == Session.Role.APPRENANT || role == Session.Role.ENSEIGNANT",
            source,
        )
        self.assertNotIn("role == Session.Role.ADMINISTRATEUR", source)

    def test_animation_lente_suivie_et_arretee_hors_scene(self):
        source = COMPOSANT.read_text(encoding="utf-8")
        for attendu in (
            "Duration.seconds(24)",
            "Animations.actives()",
            "Animations.suivre(panoramique)",
            "Animations.aTerminer",
            "setAutoReverse(true)",
            "panoramique.pause()",
            "setMouseTransparent(true)",
        ):
            with self.subTest(attendu=attendu):
                self.assertIn(attendu, source)

    def test_integration_responsive_et_styles(self):
        vue = VUE.read_text(encoding="utf-8")
        composant = COMPOSANT.read_text(encoding="utf-8")
        css = CSS.read_text(encoding="utf-8")
        self.assertIn("PanneauVisuelConnexion.roleEligible(role)", vue)
        self.assertIn("visuel.setManaged(false)", vue)
        self.assertIn("getChildren().addAll(visuel, defilement)", vue)
        self.assertIn("visuel.resizeRelocate", vue)
        self.assertNotIn("new HBox(0, visuel, defilement)", vue)
        self.assertNotIn("prefWidthProperty().bind(widthProperty()", vue)
        self.assertIn("setPrefSize(0, 0)", composant)
        self.assertIn("protected double computePrefWidth", composant)
        self.assertIn("protected double computePrefHeight", composant)
        for classe in (
            ".login-visuel",
            ".login-visuel-voile",
            ".login-visuel-titre",
            ".login-visuel-puce",
        ):
            with self.subTest(classe=classe):
                self.assertIn(classe, css)

    def test_couleurs_du_visuel_declarees_en_jetons_dans_les_deux_themes(self):
        css = CSS.read_text(encoding="utf-8")
        section = css[css.index(".login-visuel {"):css.index(".login-card {")]
        self.assertNotRegex(section, r"#[0-9a-fA-F]{6}\b")
        for nom, chemin in (
            ("clair", RACINE / "src/main/resources/fr/ihm2026/css/theme-light.css"),
            ("sombre", RACINE / "src/main/resources/fr/ihm2026/css/theme-dark.css"),
        ):
            theme = chemin.read_text(encoding="utf-8")
            for jeton in (
                "-color-login-visuel-nuit",
                "-color-login-visuel-mer",
                "-color-login-visuel-ciel",
                "-color-login-visuel-texte",
                "-color-login-visuel-accent-apprenant",
                "-color-login-visuel-accent-enseignant",
            ):
                with self.subTest(theme=nom, jeton=jeton):
                    self.assertIn(jeton, theme)


if __name__ == "__main__":
    unittest.main()
