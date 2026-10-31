#!/usr/bin/env python3
"""
Contrôle du découpage (étape 3) — sans base de données, sans réseau.

Ces tests vérifient des propriétés que le rapport de mesure ne peut pas voir : un découpage qui
produit de bons chiffres mais coupe les phrases en deux reste un mauvais découpage, et cela ne
se lit pas dans un taux de rappel.

Exécution :
    python3 -m unittest test_decoupage -v      (depuis ihm-2026/ia)
"""
from __future__ import annotations

import json
import pathlib
import re
import unittest

from indexer import (RECOUVREMENT_CAR, TAILLE_MAX, TAILLE_MIN, decouper_cours, notion_de,
                     prefixe)

CORPUS = pathlib.Path(__file__).resolve().parent / "corpus" / "corpus-v1.json"


def blocs_du_corpus() -> dict[str, list[dict]]:
    """Les blocs réels du corpus v1, groupés par cours, prêts pour le découpage."""
    corpus = json.loads(CORPUS.read_text(encoding="utf-8"))
    cours = {}
    for c in corpus["cours"]:
        cours[c["section"]] = [{"position": b["ordre"], "type": b["type"], "contenu": b["contenu"]}
                               for b in c["blocs"]]
    return cours


class DecoupageTest(unittest.TestCase):

    @classmethod
    def setUpClass(cls):
        cls.cours = blocs_du_corpus()
        cls.fragments = {section: decouper_cours(blocs) for section, blocs in cls.cours.items()}

    def test_taille_bornee(self):
        """Aucun fragment ne dépasse 600 caractères : la contrainte est aussi dans le schéma SQL."""
        for section, fragments in self.fragments.items():
            for f in fragments:
                self.assertLessEqual(f["caracteres"], TAILLE_MAX,
                                     f"{section} : fragment de {f['caracteres']} caractères")

    def test_taille_minimale_sauf_impossibilite(self):
        """Un fragment court n'est admis que si le cours entier tient dedans."""
        for section, fragments in self.fragments.items():
            total = len(fragments[0]["contenu"]) if len(fragments) == 1 else None
            for f in fragments:
                if f["caracteres"] < TAILLE_MIN:
                    self.assertEqual(1, len(fragments),
                                     f"{section} : fragment de {f['caracteres']} caractères "
                                     f"alors que le cours en compte {len(fragments)}")
            if total is not None:
                total = None

    def test_recouvrement(self):
        """Deux fragments consécutifs d'un même cours se recouvrent d'environ 15 %."""
        recouvres = 0
        attendus = 0
        for section, fragments in self.fragments.items():
            for precedent, suivant in zip(fragments, fragments[1:]):
                attendus += 1
                queue = precedent["contenu"].strip()[-RECOUVREMENT_CAR:]
                amorce = suivant["contenu"].strip()[:RECOUVREMENT_CAR]
                # on compare sur les mots : le recouvrement est aligné sur une frontière de phrase
                mots_queue = {m for m in queue.split() if len(m) > 3}
                mots_amorce = {m for m in amorce.split() if len(m) > 3}
                if mots_queue and len(mots_queue & mots_amorce) >= max(3, len(mots_queue) // 2):
                    recouvres += 1
        self.assertGreater(attendus, 0, "Le corpus doit produire des cours découpés en plusieurs fragments")
        self.assertEqual(attendus, recouvres,
                         "Chaque frontière de découpage doit porter un recouvrement")

    def test_pas_de_coupe_en_plein_mot(self):
        """Le premier mot d'un fragment existe tel quel dans le cours : la coupe est propre."""
        for section, fragments in self.fragments.items():
            source = " ".join(bloc["contenu"] for bloc in self.cours[section])
            for f in fragments[1:]:
                premier = f["contenu"].lstrip().split(" ", 1)[0].strip("\n")
                if not premier:
                    continue
                self.assertRegex(source, r"(?<![\w])" + re.escape(premier) + r"(?![\w])",
                                 f"{section} : « {premier[:24]}… » n'est pas un mot du cours — "
                                 f"le fragment commence au milieu d'un mot")

    def test_tout_le_corpus_est_indexe(self):
        """Aucun bloc n'est perdu : son début se retrouve dans un fragment du même cours."""
        for section, blocs in self.cours.items():
            texte = "\n\n".join(f["contenu"] for f in self.fragments[section])
            compact = " ".join(texte.split())
            for bloc in blocs:
                debut = " ".join(bloc["contenu"].split())[:60]
                self.assertIn(debut, compact,
                              f"{section} : un bloc du corpus n'apparaît dans aucun fragment")

    def test_corpus_reel_produit_les_fragments_attendus(self):
        """Le corpus v1 (36 blocs) doit produire 25 fragments : chiffre publié dans le rapport."""
        total = sum(len(f) for f in self.fragments.values())
        self.assertEqual(25, total, f"{total} fragments produits au lieu de 25")

    def test_prefixe_contextuel(self):
        """Le contexte d'indexation porte le cours, le langage et le type — pas le texte cité."""
        contexte = prefixe("Variables et types en Python — Variables et types", "python", "TEXTE+CODE")
        self.assertIn("Cours : Variables et types en Python", contexte)
        self.assertIn("Langage : python", contexte)
        self.assertIn("Type : TEXTE+CODE", contexte)

    def test_notion_deduite_de_la_section(self):
        self.assertEqual("VAR", notion_de("Variables et types en Java — Classes et objets"))
        self.assertEqual("COLL", notion_de("Listes et dictionnaires en Python — Variables et types"))
        self.assertEqual("ERREUR", notion_de("Exceptions en C++ — Compilation et types"))
        self.assertEqual("DIVERS", notion_de("Une section sans notion connue"))


if __name__ == "__main__":
    unittest.main(verbosity=2)
