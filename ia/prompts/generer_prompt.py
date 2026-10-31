#!/usr/bin/env python3
"""
Étape 5 — génération du prompt système DEPUIS le contrat de refus.

Pourquoi générer : le prompt doit employer exactement les libellés que l'interface affiche et que
les tests comparent (`cas-de-refus.json`). Recopier ces phrases à la main dans un fichier Markdown,
c'est garantir qu'elles divergeront un jour. Ici, la table du vocabulaire est extraite du contrat.

Entrées :  gabarit.md (le texte, écrit à la main)
           src/main/resources/fr/ihm2026/ia/cas-de-refus.json (le vocabulaire et les exemples de refus)
Sorties :  ia/prompts/system-<version>.md   (reCopié sur le classpath par le pom)
           ia/prompts/sortie-schema.json    (schéma de la sortie, recopié de même)

Usage :
    python3 prompts/generer_prompt.py            # régénère et vérifie
    python3 prompts/generer_prompt.py --verifier # vérifie sans écrire (code de sortie 1 si dérive)
"""
from __future__ import annotations

import argparse
import json
import pathlib
import sys

VERSION = "system-v2"
DATE = "2026-10-27"

ICI = pathlib.Path(__file__).resolve().parent
RACINE = ICI.parent                      # …/ihm-2026/ia
PROJET = RACINE.parent                   # …/ihm-2026
CONTRAT = json.loads((PROJET / "src/main/resources/fr/ihm2026/ia/cas-de-refus.json")
                     .read_text(encoding="utf-8"))
GABARIT = (ICI / "gabarit.md").read_text(encoding="utf-8")
SORTIE = ICI / f"{VERSION}.md"
SCHEMA = ICI / "sortie-schema.json"


def table_vocabulaire() -> str:
    """Les 8 libellés du contrat, avec la garantie technique qui les porte."""
    lignes = ["| Motif | Texte exact à employer | Quand | Garantie dans le code |",
              "|---|---|---|---|"]
    quand = {
        "HORS_PERIMETRE": "question hors du parcours",
        "SOLUTION_DEMANDEE": "demande de solution complète",
        "DECISION_DEMANDEE": "demande de note, de validation, de déblocage",
        "TEST_EN_COURS": "évaluation en cours",
        "PUBLICATION_AUTOMATIQUE": "positionnement B : publication sans relecture",
        "DONNEES_INSUFFISANTES": "positionnement B : synthèse sur trop peu de résultats",
        "QUOTA": "quota quotidien atteint",
        "INDISPONIBLE": "service indisponible ou sortie non conforme",
    }
    for motif, message in CONTRAT["vocabulaire"]["messages"].items():
        garantie = CONTRAT["vocabulaire"]["garanties"].get(motif, "")
        lignes.append(f"| `{motif}` | « {message} » | {quand.get(motif, '')} | `{garantie}` |")
    return "\n".join(lignes)


def cas_de(motif: str) -> dict:
    for cas in CONTRAT["cas"]:
        if cas["motif"] == motif:
            return cas
    raise SystemExit(f"aucun cas de refus pour le motif {motif} dans le contrat")


def exemple(demande: str, sortie: dict) -> str:
    return (f"**Demande** : « {demande} »\n\n"
            f"```json\n{json.dumps(sortie, ensure_ascii=False, indent=2)}\n```\n")


def exemple_refus(motif: str, demande: str | None = None) -> str:
    cas = cas_de(motif)
    return exemple(demande or cas["demande"], {
        "version": VERSION,
        "reponse": CONTRAT["vocabulaire"]["messages"][motif],
        "citations": [],
        "notion": "DIVERS",
        "refus": motif,
    })


def exemple_reponse(demande: str, reponse: str, section: str, langage: str, notion: str) -> str:
    return exemple(demande, {
        "version": VERSION,
        "reponse": reponse,
        "citations": [{"section": section, "langage": langage}],
        "notion": notion,
        "refus": "AUCUN",
    })


def exemples() -> str:
    """Cinq exemples, dont deux refus (exigence du plan de développement, étape 5)."""
    blocs = [
        exemple_reponse(
            "comment déclarer une fonction en Python ?",
            "En Python, une fonction se déclare avec le mot-clé def et son corps est indenté. "
            "Relis la section citée puis essaie de déclarer la tienne.",
            "Fonctions et valeur de retour en Python — Variables et types", "python", "FONC"),
        exemple_reponse(
            "quelle est la différence entre length et size() ?",
            "En Java, un tableau connaît sa taille avec length (sans parenthèses), une collection "
            "avec size() (avec parenthèses). Confondre les deux est l'erreur de compilation la plus "
            "fréquente : regarde la ligne où tu obtiens l'erreur.",
            "Collections en Java — Classes et objets", "java", "COLL"),
        exemple_reponse(
            "pourquoi mon vecteur ne contient pas mes modifications ?",
            "En C++, for (int n : notes) parcourt une copie de chaque élément : pour modifier le "
            "conteneur, il faut écrire for (int& n : notes). La section citée montre les deux formes.",
            "Boucles en C++ — Compilation et types", "cpp", "BOUCLE"),
        exemple_refus("DECISION_DEMANDEE", "note mes 5 apprenants automatiquement"),
        exemple_refus("HORS_PERIMETRE", "montre-moi comment écrire ça en Rust"),
    ]
    return "\n".join(f"### Exemple {i}\n\n{b}" for i, b in enumerate(blocs, start=1))


SCHEMA_JSON = {
    "$schema": "https://json-schema.org/draft/2020-12/schema",
    "$id": f"https://ihm2026.local/ia/{VERSION}/sortie-schema.json",
    "title": f"Sortie de l'agent d'assistance ({VERSION})",
    "description": "Forme unique de la sortie du modèle : une réponse citée, ou un refus motivé. "
                   "Un refus est une réponse de première classe, pas une exception.",
    "type": "object",
    "additionalProperties": False,
    "required": ["version", "reponse", "citations", "notion", "refus"],
    "properties": {
        "version": {"type": "string", "const": VERSION,
                    "description": "Version du prompt, journalisée à chaque appel."},
        "reponse": {"type": "string", "minLength": 1, "maxLength": 600,
                    "description": "Texte affiché à l'apprenant, en français, trois phrases maximum."},
        "citations": {
            "type": "array", "maxItems": 3,
            "description": "Sections du cours réellement retrouvées. Vide si et seulement si "
                           "refus != AUCUN.",
            "items": {
                "type": "object", "additionalProperties": False,
                "required": ["section", "langage"],
                "properties": {
                    "section": {"type": "string", "minLength": 3},
                    "langage": {"enum": ["python", "java", "cpp"]},
                },
            },
        },
        "notion": {"enum": ["VAR", "COND", "BOUCLE", "FONC", "COLL", "ERREUR", "DIVERS"]},
        "refus": {"enum": list(CONTRAT["vocabulaire"]["messages"].keys()),
                  "description": "Le motif de refus, ou AUCUN. Ce champ n'est pas un booléen : "
                                 "un booléen et un motif pourraient se contredire."},
    },
    "allOf": [
        {"if": {"properties": {"refus": {"const": "AUCUN"}}},
         "then": {"properties": {"citations": {"minItems": 1}},
                  "description": "Une réponse sans citation n'est pas affichable : elle est rejetée."}},
        {"if": {"properties": {"refus": {"not": {"const": "AUCUN"}}}},
         "then": {"properties": {"citations": {"maxItems": 0}},
                  "description": "Un refus ne cite rien : il réoriente."}},
    ],
}


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--verifier", action="store_true")
    args = p.parse_args()

    substitutions = {"VERSION": VERSION, "DATE": DATE,
                     "VOCABULAIRE": table_vocabulaire(), "EXEMPLES": exemples()}
    texte = GABARIT
    for cle, valeur in substitutions.items():
        # les deux écritures sont acceptées : une accolade oubliée dans le gabarit produisait un
        # prompt qui affichait « {EXEMPLES} » au modèle — vu à l'exécution, pas à la relecture.
        texte = texte.replace("{{" + cle + "}}", valeur).replace("{" + cle + "}", valeur)
    restants = [m for m in ("VERSION", "DATE", "VOCABULAIRE", "EXEMPLES") if "{" + m + "}" in texte]
    if restants:
        raise SystemExit(f"placeholders non substitués dans le gabarit : {restants}")
    schema = json.dumps(SCHEMA_JSON, ensure_ascii=False, indent=2) + "\n"

    if args.verifier:
        ecarts = []
        if not SORTIE.exists() or SORTIE.read_text(encoding="utf-8") != texte:
            ecarts.append(f"{SORTIE.name} n'est pas à jour")
        if not SCHEMA.exists() or SCHEMA.read_text(encoding="utf-8") != schema:
            ecarts.append("sortie-schema.json n'est pas à jour")
        for e in ecarts:
            print(f"  DÉRIVE : {e}")
        if not ecarts:
            print(f"prompt {VERSION}.md ({len(texte)} caractères) et sortie-schema.json à jour")
        return 1 if ecarts else 0

    SORTIE.write_text(texte, encoding="utf-8")
    SCHEMA.write_text(schema, encoding="utf-8")
    print(f"{SORTIE} ({len(texte)} caractères) et {SCHEMA.name} écrits")
    return 0


if __name__ == "__main__":
    sys.exit(main())
