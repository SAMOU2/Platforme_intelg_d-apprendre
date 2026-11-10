#!/usr/bin/env python3
"""Contrôles structurels des maquettes basse fidélité (croquis papier + wireframes)."""

from __future__ import annotations

import re
import struct
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CROQUIS_DIR = ROOT / "docs" / "maquettes" / "croquis-papier"
WIRE_DIR = ROOT / "docs" / "maquettes" / "wireframes"
SOURCE_MD = ROOT / "docs" / "sources-v2" / "maquettes-basse-fidelite.md"
PDF_PATH = ROOT / "docs" / "Maquettes-basse-fidelite.pdf"

SLUGS = [
    "00-cinematique-navigation",
    "01-connexion-decouverte-confiance",
    "02-inscription-activation-pin",
    "03-apprenant-parcours-niveaux",
    "04-apprenant-cours-assistant-ia",
    "05-apprenant-entrainement-ia",
    "06-apprenant-test-final-sans-ia",
    "07-apprenant-copies-resultats-tableau-bord",
    "08-enseignant-niveaux-contenus",
    "09-enseignant-apprenants-corrections",
    "10-administrateur-comptes-affectations",
    "11-transversal-accessibilite-etats-sidebar",
]


class MaquettesBasseFideliteTest(unittest.TestCase):
    def test_24_planches_svg_et_png_presentes_et_valides(self):
        for dossier in (CROQUIS_DIR, WIRE_DIR):
            for slug in SLUGS:
                with self.subTest(dossier=dossier.name, slug=slug):
                    svg = (dossier / f"{slug}.svg").read_text(encoding="utf-8")
                    self.assertIn("<svg ", svg)
                    self.assertIn('width="1560"', svg)
                    self.assertIn('height="1000"', svg)

                    png = (dossier / f"{slug}.png").read_bytes()
                    self.assertTrue(png.startswith(b"\x89PNG\r\n\x1a\n"))
                    w, h = struct.unpack(">II", png[16:24])
                    self.assertEqual((w, h), (1560, 1000))
                    self.assertGreater(len(png), 50_000)

    def test_source_markdown_reference_les_24_images_sans_lien_casse(self):
        texte = SOURCE_MD.read_text(encoding="utf-8")
        liens = re.findall(r"!\[[^\]]*\]\(([^)]+)\)", texte)
        self.assertEqual(len(liens), 24)
        for rel in liens:
            with self.subTest(image=rel):
                self.assertTrue((SOURCE_MD.parent / rel).resolve().is_file())

    def test_pdf_compile_et_index_documentes(self):
        pdf = PDF_PATH.read_bytes()
        self.assertTrue(pdf.startswith(b"%PDF-"))
        self.assertGreater(len(pdf), 500_000)

        gen_pdf = ROOT / "outils" / "generer_maquettes_pdf.py"
        compile(gen_pdf.read_text(encoding="utf-8"), str(gen_pdf), "exec")

        docs_readme = (ROOT / "docs" / "README.md").read_text(encoding="utf-8")
        maq_readme = (ROOT / "docs" / "maquettes" / "README.md").read_text(encoding="utf-8")
        self.assertIn("Maquettes-basse-fidelite.pdf", docs_readme)
        self.assertIn("croquis-papier/", maq_readme)
        self.assertIn("wireframes/", maq_readme)
        self.assertIn("Maquettes-basse-fidelite.pdf", maq_readme)


if __name__ == "__main__":
    unittest.main()
