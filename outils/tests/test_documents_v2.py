#!/usr/bin/env python3
"""Contrôles structurels du dossier documentaire v2.0.

Ces tests ne prouvent ni la compilation Java, ni le rendu JavaFX, ni la recette
Windows/NVDA. Ils empêchent surtout une régénération silencieusement incomplète
ou la réapparition d’identifiants en double dans les sources Markdown.
"""

from __future__ import annotations

import re
import struct
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
SOURCES = ROOT / "docs" / "sources-v2"
DIAGRAMMES = ROOT / "docs" / "preuves" / "diagrammes-v2"

FICHIERS_SOURCES = {
    "analyse-des-besoins-v2.md": "Version 2.0",
    "diagrammes-du-projet-v2.md": "Version 2.0",
    "modeles-de-taches-v2.md": "Version 2.0",
    "cahier-des-charges-v1.md": "Version 1.0",
}

PDFS = [
    "analyse-besoins-IHM.pdf",
    "canvas-document-analyse-besoins.pdf",
    "Diagrammes-du-projet.pdf",
    "Modeles-de-taches-du-projet-IHM.pdf",
    "Cahier-des-charges.pdf",
]

PNGS = [
    "01-cas-utilisation.png",
    "02-donnees-identite.png",
    "03-donnees-pedagogie.png",
    "04-etats-transitions.png",
    "05-sequence-inscription-activation.png",
    "06-sequence-connexion-confiance.png",
    "07-sequence-test-correction.png",
    "08-activite-progression.png",
    "09-composants.png",
    "10-sequence-ia-entrainement.png",
    "11-deploiement.png",
    "12-chaine-taches.png",
]


def lire(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def identifiants(texte: str, motif: str) -> set[str]:
    return set(re.findall(motif, texte))


class DocumentsV2Test(unittest.TestCase):
    def test_sources_versionnees_et_datees(self):
        for nom, version in FICHIERS_SOURCES.items():
            with self.subTest(source=nom):
                texte = lire(SOURCES / nom)
                self.assertIn(f"<!-- version: {version} -->", texte)
                self.assertIn("<!-- date: 3 novembre 2026 -->", texte)
                self.assertIn("Application 0.14.4", texte)
                self.assertIn("2ae972b", texte)

    def test_pdfs_generes_et_copie_canvas_alignee(self):
        for nom in PDFS:
            with self.subTest(pdf=nom):
                donnees = (ROOT / "docs" / nom).read_bytes()
                self.assertTrue(donnees.startswith(b"%PDF-"))
                self.assertGreater(len(donnees), 50_000)
        self.assertEqual(
            (ROOT / "docs" / "analyse-besoins-IHM.pdf").read_bytes(),
            (ROOT / "docs" / "canvas-document-analyse-besoins.pdf").read_bytes(),
            "La copie canvas doit rester la même analyse v2.0, pas une ancienne version divergente.",
        )

    def test_diagrammes_png_complets_et_lisibles(self):
        for nom in PNGS:
            with self.subTest(png=nom):
                donnees = (DIAGRAMMES / nom).read_bytes()
                self.assertTrue(donnees.startswith(b"\x89PNG\r\n\x1a\n"))
                largeur, hauteur = struct.unpack(">II", donnees[16:24])
                self.assertGreaterEqual(largeur, 1500)
                self.assertGreaterEqual(hauteur, 1000)
                self.assertGreater(len(donnees), 30_000)

    def test_liens_images_des_sources(self):
        for nom in FICHIERS_SOURCES:
            source = SOURCES / nom
            for relatif in re.findall(r"!\[[^\]]*\]\(([^)]+)\)", lire(source)):
                with self.subTest(source=nom, image=relatif):
                    self.assertTrue((source.parent / relatif).resolve().is_file())

    def test_catalogues_analyse_sans_trou_ni_doublon(self):
        texte = lire(SOURCES / "analyse-des-besoins-v2.md")
        attendus = {
            r"BF-U\d+": 10,
            r"BF-A\d+": 9,
            r"BF-P\d+": 11,
            r"BF-E\d+": 11,
            r"BF-IA\d+": 8,
            r"BF-R\d+": 8,
            r"BNF-\d+": 18,
            r"IHM-\d+": 20,
            r"RG-\d+": 24,
            r"UC-\d+": 14,
        }
        for motif, nombre in attendus.items():
            with self.subTest(motif=motif):
                self.assertEqual(len(identifiants(texte, motif)), nombre)
        self.assertIn("57 besoins fonctionnels", texte)

    def test_catalogues_cahier_des_charges(self):
        texte = lire(SOURCES / "cahier-des-charges-v1.md")
        attendus = {
            r"OBJ-\d+": 9,
            r"EF-\d+": 29,
            r"RM-\d+": 17,
            r"ENF-\d+": 16,
            r"REC-\d+": 16,
        }
        for motif, nombre in attendus.items():
            with self.subTest(motif=motif):
                self.assertEqual(len(identifiants(texte, motif)), nombre)

    def test_decideurs_et_limites_non_regressent_pas(self):
        corpus = "\n".join(lire(SOURCES / nom) for nom in FICHIERS_SOURCES).casefold()
        for phrase in [
            "mot de passe reste exigé",
            "ne note pas",
            "correction humaine",
            "aucun modèle local",
            "recette Windows",
        ]:
            with self.subTest(phrase=phrase):
                self.assertIn(phrase.casefold(), corpus)
        # Les anciennes hypothèses ne peuvent apparaître que comme remplacées/exclues.
        self.assertNotIn("correction automatique est livrée", corpus)
        self.assertIn("aucune certification wcag/rgaa n’est revendiquée", corpus)
        self.assertIn("ne peut être déclarée sans audit", corpus)

    def test_index_et_generateur(self):
        generateur = ROOT / "outils" / "generer_documents_v2.py"
        compile(lire(generateur), str(generateur), "exec")
        index_docs = lire(ROOT / "docs" / "README.md")
        for nom in PDFS:
            with self.subTest(index=nom):
                self.assertIn(nom, index_docs)
        self.assertNotIn("analyse des besoins v1.3", index_docs)


if __name__ == "__main__":
    unittest.main()
