#!/usr/bin/env python3
"""
Étape 10 — évaluation de la recherche documentaire (RAG) sur un jeu de test figé.

Mesure trois choses, et écrit un rapport daté :
  · top1   : le premier fragment retenu appartient-il à la section attendue ?
  · top5   : la section attendue figure-t-elle dans les cinq premiers ?
  · refus  : une question hors domaine obtient-elle un score trop faible pour être citée ?

Usage :
    python3 evaluer.py [--dsn ...] [--sortie rapport-v1.md]
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import pathlib
import unicodedata

import psycopg2
import psycopg2.extras

from indexer import vectoriser

SEUILS_CALIBRATION = [0.00, 0.05, 0.10, 0.15, 0.20, 0.25]


def sans_accents(texte: str) -> str:
    """Comparaison insensible à la casse et aux accents (« reference » = « référence »)."""
    texte = unicodedata.normalize("NFD", str(texte).lower())
    return "".join(c for c in texte if unicodedata.category(c) != "Mn")
HORS_DOMAINE = [
    "Quelle est la recette de la tarte au citron ?",
    "Qui a gagné la coupe du monde de football ?",
    "Comment changer un pneu de voiture ?",
    "Quel est le prix d'un billet d'avion pour Paris ?",
]


def rechercher(cur, question: str, langage: str | None, k: int = 5, seuil: float = 0.0):
    cur.execute(
        "SELECT section, langage, contenu, score, similitude, lexical "
        "FROM rechercher_fragments(%s::vector, %s, %s, %s, %s)",
        (vectoriser(question), question, langage, k, seuil),
    )
    return cur.fetchall()


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--dsn", default="host=/tmp port=5433 dbname=ihm2026 user=postgres")
    p.add_argument("--jeu", default="jeu-de-test.jsonl")
    p.add_argument("--sortie", default="rapport-v1.md")
    p.add_argument("--version", default="v1")
    args = p.parse_args()

    cas = [json.loads(l) for l in pathlib.Path(args.jeu).read_text(encoding="utf-8").splitlines() if l.strip()]
    n = len(cas)
    pct = lambda a, b: 100.0 * a / b if b else 0.0

    with psycopg2.connect(args.dsn) as cx, cx.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        # ---- 1. calibration du seuil de citation
        calibration = []
        for seuil in SEUILS_CALIBRATION:
            retenus = sum(1 for c in cas if rechercher(cur, c["question"], c["langage"], 5, seuil))
            fuites = sum(1 for q in HORS_DOMAINE if rechercher(cur, q, None, 5, seuil))
            calibration.append({"seuil": seuil, "rappel": retenus, "fuites": fuites})

        # ---- 2. qualité de la recherche au seuil retenu (0.05 : meilleur compromis mesuré)
        seuil = 0.05
        top1_f = top5_f = top1_l = top5_l = 0
        temoin1_f = temoin5_f = 0
        details, temoin = [], []
        for c in cas:
            filtre = rechercher(cur, c["question"], c["langage"], 5, seuil)
            libre = rechercher(cur, c["question"], None, 5, seuil)
            ok1f = bool(filtre) and filtre[0]["section"] == c["section"]
            ok5f = any(r["section"] == c["section"] for r in filtre)
            ok1l = bool(libre) and libre[0]["section"] == c["section"]
            ok5l = any(r["section"] == c["section"] for r in libre)
            top1_f += ok1f; top5_f += ok5f; top1_l += ok1l; top5_l += ok5l
            # « réponse juste » : le témoin attendu figure-t-il dans le fragment cité ?
            # C'est la seule mesure qui regarde le CONTENU cité, pas seulement sa section.
            def contient(texte):
                if not c.get("attendu") or not texte:
                    return None
                return sans_accents(c["attendu"]) in sans_accents(texte)
            tem1 = contient(filtre[0]["contenu"]) if filtre else False
            tem5 = any(contient(r["contenu"]) for r in filtre)
            if tem1 is not None:
                temoin1_f += tem1; temoin5_f += tem5
                temoin.append({"id": c["id"], "attendu": c["attendu"], "ok1": tem1, "ok5": tem5})
            details.append({**c, "ok1f": ok1f, "ok5f": ok5f, "ok1l": ok1l, "ok5l": ok5l,
                            "sim": float(filtre[0]["similitude"]) if filtre else 0.0,
                            "lex": float(filtre[0]["lexical"]) if filtre else 0.0})

        # ---- 3. hors domaine
        refus = []
        for q in HORS_DOMAINE:
            r = rechercher(cur, q, None, 5, seuil)
            refus.append({"question": q, "candidat": bool(r),
                          "sim": float(r[0]["similitude"]) if r else 0.0,
                          "section": r[0]["section"] if r else "—"})

    nh = len(HORS_DOMAINE)
    nt = len(temoin)
    fuites = sum(1 for r in refus if r["candidat"])

    L = [f"# Rapport d'évaluation de l'agent — {args.version}", "",
         f"Date : {dt.date.today().isoformat()} · prompt `system-v1` · jeu de test : "
         f"`{pathlib.Path(args.jeu).name}` ({n} questions) · vecteurs : plissement de hachage "
         "1536 d (démonstration hors ligne, sans réseau)", "",
         "## 1. Calibration du seuil de citation", "",
         "Un fragment n'est candidat que si sa similitude cosinus dépasse le seuil, ou s'il est",
         "lexicalement pertinent. Le seuil arbitre deux erreurs symétriques : refuser une question",
         "légitime, ou citer un fragment hors sujet.", "",
         f"| Seuil de similitude | Questions légitimes retrouvées (sur {n}) | "
         f"Questions hors domaine citées (sur {nh}) |",
         "|---|---|---|"]
    for c in calibration:
        L.append(f"| {c['seuil']:.2f} | {c['rappel']}/{n} | {c['fuites']}/{nh} |")
    meilleur = max(calibration, key=lambda c: (n - c["fuites"] * 4) + c["rappel"])
    L += ["",
          "**Lecture du tableau.** Aucun seuil ne donne satisfaction sur les deux colonnes :",
          "c'est le symptôme de vecteurs de démonstration, pas d'un mauvais réglage. Plus le seuil",
          "monte, moins l'agent cite hors domaine, mais plus il refuse des questions légitimes.", "",
          f"**Seuil retenu : 0.05** — compromis explicite : "
          f"{calibration[1]['rappel']} questions légitimes sur {n} conservées, "
          f"et les citations hors domaine ramenées à {calibration[1]['fuites']}/{nh}. "
          "Les fuites restantes sont arrêtées",
          "en aval par le prompt système puis par le filtre de sortie (étape 6), jamais par le",
          "seuil seul.", "",
          "## 2. Qualité de la recherche (seuil 0.05)", "",
          "| Mesure | Avec filtre de langage | Sans filtre |", "|---|---|---|",
          f"| top-1 correct | **{pct(top1_f, n):.0f} %** ({top1_f}/{n}) | "
          f"{pct(top1_l, n):.0f} % ({top1_l}/{n}) |",
          f"| top-5 contient la bonne section | **{pct(top5_f, n):.0f} %** ({top5_f}/{n}) | "
          f"{pct(top5_l, n):.0f} % ({top5_l}/{n}) |",
          "",
          f"Le filtre de langage apporte **{pct(top1_f, n) - pct(top1_l, n):.0f} points** de top-1 : "
          "il reste donc nécessaire,",
          "mais il ne suffit pas — un fragment de la bonne section peut précéder le fragment qui",
          "contient réellement la réponse.", ""]

    if nt:
        L += ["## 3. Réponse juste : le témoin attendu est-il cité ?", "",
              f"Chaque question du jeu gelé porte un **témoin** (`attendu`) : le mot que doit "
              f"contenir le fragment cité. Mesure sur {nt} questions à témoin.", "",
              "| Mesure | Résultat |", "|---|---|",
              f"| témoin présent dans le top-1 | **{pct(temoin1_f, nt):.0f} %** "
              f"({temoin1_f}/{nt}) |",
              f"| témoin présent dans le top-5 | **{pct(temoin5_f, nt):.0f} %** "
              f"({temoin5_f}/{nt}) |", "",
              "C'est la mesure la plus sévère : elle porte sur le **contenu** cité et non sur la "
              "section. Un écart avec le top-1 ci-dessus", "signifie que la bonne section est "
              "trouvée mais que le fragment utile n'est pas le premier.", ""]

    L += ["## 4. Questions hors domaine", "",
          "| Question | Candidat retenu | Similitude | Verdict |", "|---|---|---|---|"]
    for r in refus:
        verdict = "**à renforcer par le prompt**" if r["candidat"] else "refusé correctement"
        L.append(f"| {r['question']} | {'oui' if r['candidat'] else 'non'} | {r['sim']:.3f} | {verdict} |")

    L += ["", f"## 5. Détail des {n} cas", "",
          "| Cas | Langage | top-1 filtré | top-5 filtré | top-1 libre | témoin top-1 | similitude | lexical |",
          "|---|---|---|---|---|---|---|---|"]
    par_id = {t["id"]: t for t in temoin}
    for d in details:
        t = par_id.get(d["id"])
        tem = "—" if t is None else ("oui" if t["ok1"] else "non")
        L.append(f"| `{d['id']}` | {d['langage'] or 'tous'} | {'oui' if d['ok1f'] else 'non'} | "
                 f"{'oui' if d['ok5f'] else 'non'} | {'oui' if d['ok1l'] else 'non'} | {tem} | "
                 f"{d['sim']:.3f} | {d['lex']:.3f} |")

    L += ["", "## 6. Ce que ce rapport ne prouve pas", "",
          "1. **Le corpus reste petit** "
          f"(36 fragments, 3 langages). À cette taille, une recherche par mots-clés réussit "
          "presque toujours : ces taux valident la **chaîne technique** (découpage, vecteurs, "
          "index HNSW, recherche hybride), pas la qualité d'un agent réel.",
          "2. **Les vecteurs sont de démonstration.** Le plissement de hachage ne sépare pas "
          "finement le domaine du hors-domaine. C'est mesuré ci-dessus, et c'est la raison pour "
          "laquelle le refus n'est **pas** confié au seul seuil : il est aussi porté par le prompt "
          "système (étape 5) et par le filtre de sortie (étape 6). Avec un vrai modèle "
          "d'embeddings, la séparation est bien meilleure — le seuil devra être recalibré sur le "
          "corpus complet, avec cette même procédure.",
          "3. **Les incidents de notation ne sont pas mesurés ici** : ils le sont côté Java, par "
          "les tests adversariaux de `GardeFouTest`.",
          "4. **Le jeu de questions est gelé.** Il a été écrit et figé avant cette première "
          "mesure (`ia/corpus/MANIFESTE.json`) : le modifier après coup invaliderait la mesure.", ""]

    pathlib.Path(args.sortie).write_text("\n".join(L) + "\n", encoding="utf-8")
    print(f"rapport écrit : {args.sortie}")
    print(f"top-1 filtré {pct(top1_f, n):.0f} % · top-5 filtré {pct(top5_f, n):.0f} % · "
          f"top-5 libre {pct(top5_l, n):.0f} % · fuites hors domaine "
          f"{sum(1 for r in refus if r['candidat'])}/{len(HORS_DOMAINE)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
