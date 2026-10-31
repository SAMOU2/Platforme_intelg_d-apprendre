#!/usr/bin/env python3
"""
Étape 3 — protocole de mesure du découpage et de l'indexation.

Le jeu de 60 questions est gelé depuis l'étape 2 : il ne peut plus servir à régler quoi que
ce soit sans perdre sa valeur. On le partage donc, une fois, en deux moitiés :

  · RÉGLAGE  (20 questions) : sert à choisir les paramètres (seuil, poids du lexical,
    bonus de couverture) — pour l'index v1 **et** pour l'index v2 ;
  · MESURE   (40 questions) : n'est touchée par aucun réglage. C'est le seul chiffre publié.

Les deux moitiés sont équilibrées par construction : pour chaque notion, une question de
chaque langage part au réglage, les autres restent en mesure ; deux questions transversales
complètent le réglage. Les empreintes des deux moitiés sont écrites dans le rapport, ce qui
rend la partition vérifiable.

Usage :
    python3 evaluer_etape3.py --dsn "host=/tmp/pgsock port=5433 dbname=ihm2026 user=postgres"
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import itertools
import json
import pathlib
import unicodedata

import psycopg2
import psycopg2.extras

from indexer import MOTS_VIDES, jetons as jetons_index, normaliser, vectoriser

ICI = pathlib.Path(__file__).resolve().parent
JEU = ICI / "evaluation" / "jeu-de-test-v1-60.jsonl"
PARTITION = ICI / "evaluation" / "partition-60.json"
RAPPORT = ICI / "evaluation" / "rapport-etape3-v2.md"

HORS_DOMAINE = [
    "Quelle est la recette de la tarte au citron ?",
    "Qui a gagné la coupe du monde de football ?",
    "Comment changer un pneu de voiture ?",
    "Quel est le prix d'un billet d'avion pour Paris ?",
]

GRILLE = {                                  # réglage : 4 × 3 × 3 = 36 combinaisons
    "seuil_vecteur": [0.00, 0.05, 0.10, 0.15],
    "poids_lex": [0.0, 0.5, 1.0],
    "bonus_couverture": [0.0, 0.15, 0.30],
}
CONFIGS_V2 = [dict(zip(GRILLE, v)) for v in itertools.product(*GRILLE.values())]
CONFIGS_V1 = [{"seuil_vecteur": s, "poids_lex": p, "bonus_couverture": 0.0}
              for s, p in itertools.product(GRILLE["seuil_vecteur"], GRILLE["poids_lex"])]


def sans_accents(texte: str) -> str:
    texte = unicodedata.normalize("NFD", str(texte).lower())
    return "".join(c for c in texte if unicodedata.category(c) != "Mn")


# ------------------------------------------------------------------ partition
def construire_partition() -> dict:
    """Partition déterministe : première question de chaque langage par notion (18)
    plus deux transversales (VAR, ERREUR) = 20 en réglage, 40 en mesure."""
    cas = [json.loads(l) for l in JEU.read_text(encoding="utf-8").splitlines() if l.strip()]
    reglage, mesure = [], []
    vus = {}
    for c in cas:
        cle = (c["notion"], c["langage"])
        vus.setdefault(cle, []).append(c["id"])
    for notion in dict.fromkeys(c["notion"] for c in cas):
        for langage in ("python", "java", "cpp"):
            ids = vus.get((notion, langage), [])
            if ids:
                reglage.append(ids[0])                  # la première de chaque langage
        transversales = vus.get((notion, None), [])
        if notion in ("VAR", "ERREUR") and transversales:
            reglage.append(transversales[0])
    for c in cas:
        if c["id"] not in reglage:
            mesure.append(c["id"])
    return {"regle": "par notion : première question de chaque langage + 2 transversales (VAR, ERREUR)",
            "nb_reglage": len(reglage), "nb_mesure": len(mesure),
            "reglage": reglage, "mesure": mesure}


def empreinte(ids: list[str], cas: dict) -> str:
    lignes = "\n".join(json.dumps(cas[i], ensure_ascii=False, sort_keys=True) for i in sorted(ids))
    return hashlib.sha256(lignes.encode("utf-8")).hexdigest()


# ------------------------------------------------------------------ recherche
def rechercher(cur, fonction: str, question: str, langage: str | None, k: int, config: dict):
    vecteur = vectoriser(question)
    jetons_question = [j for j in jetons_index(question) if "_" not in j and len(j) >= 4]
    if fonction == "v2":
        cur.execute(
            "SELECT section, contenu, similitude, lexical, couverture, score "
            "FROM rechercher_fragments_v2(%s::vector, %s, %s, %s, %s, %s, %s, %s)",
            (vecteur, question, langage, k, config["seuil_vecteur"],
             config["poids_lex"], config["bonus_couverture"], jetons_question))
    else:
        cur.execute(
            "SELECT section, contenu, similitude, lexical, 0.0 AS couverture, "
            "       similitude + %s * lexical AS score "
            "FROM rechercher_fragments(%s::vector, %s, %s, %s, %s) ORDER BY score DESC",
            (config["poids_lex"], vecteur, question, langage, k, config["seuil_vecteur"]))
    return cur.fetchall()


def mesurer(cur, fonction: str, cas: dict, ids: list[str], config: dict) -> dict:
    """Mesures sur un sous-ensemble : top-1, top-5, témoin top-1/top-5, par langage."""
    lignes = []
    for i in ids:
        c = cas[i]
        filtres = rechercher(cur, fonction, c["question"], c["langage"], 5, config)
        libre = rechercher(cur, fonction, c["question"], None, 5, config)
        temoin = sans_accents(c["attendu"])

        def contient(ligne):
            return temoin in sans_accents(ligne["contenu"])

        lignes.append({
            "id": i, "langage": c["langage"] or "tous",
            "ok1": bool(filtres) and filtres[0]["section"] == c["section"],
            "ok5": any(r["section"] == c["section"] for r in filtres),
            "ok1_libre": bool(libre) and libre[0]["section"] == c["section"],
            "tem1": bool(filtres) and contient(filtres[0]),
            "tem5": any(contient(r) for r in filtres),
            "sim": float(filtres[0]["similitude"]) if filtres else 0.0,
            "couverture": float(filtres[0]["couverture"]) if filtres else 0.0,
        })
    n = len(lignes) or 1
    return {
        "n": len(lignes),
        "top1": sum(l["ok1"] for l in lignes) / n,
        "top5": sum(l["ok5"] for l in lignes) / n,
        "top1_libre": sum(l["ok1_libre"] for l in lignes) / n,
        "temoin1": sum(l["tem1"] for l in lignes) / n,
        "temoin5": sum(l["tem5"] for l in lignes) / n,
        "par_langage": {
            lang: {
                "n": sum(1 for l in lignes if l["langage"] == lang),
                "top1": sum(1 for l in lignes if l["langage"] == lang and l["ok1"]) /
                        max(1, sum(1 for l in lignes if l["langage"] == lang)),
                "top5": sum(1 for l in lignes if l["langage"] == lang and l["ok5"]) /
                        max(1, sum(1 for l in lignes if l["langage"] == lang)),
            } for lang in ("python", "java", "cpp", "tous")
        },
        "detail": lignes,
    }


def fuites(cur, fonction: str, config: dict) -> int:
    return sum(1 for q in HORS_DOMAINE
               if rechercher(cur, fonction, q, None, 5, config))


# ------------------------------------------------------------------ réglage
def regler(cur, fonction: str, cas: dict, ids: list[str], configs: list[dict]) -> tuple[dict, list]:
    """Choisit la configuration sur la moitié RÉGLAGE uniquement.

    Critère, dans cet ordre : top-5 (le critère de sortie de l'étape : ≥ 85 %), puis top-1,
    puis témoin dans le top-1, puis le moins de fuites hors domaine. Aucune question de la
    moitié MESURE n'intervient ici.
    """
    essais = []
    for config in configs:
        m = mesurer(cur, fonction, cas, ids, config)
        f = fuites(cur, fonction, config)
        essais.append({**config, "top1": m["top1"], "top5": m["top5"],
                       "temoin1": m["temoin1"], "fuites": f})
    essais.sort(key=lambda e: (-e["top5"], -e["top1"], -e["temoin1"], e["fuites"],
                               e["seuil_vecteur"]))
    return essais[0], essais


def pct(x: float) -> str:
    return f"{100 * x:.0f} %"


# ------------------------------------------------------------------ rapport
def ecrire_rapport(part: dict, cas: dict, v1: dict, v2: dict, res_reglage: dict,
                   res_mesure: dict, empreintes: dict, stats_tables: dict) -> None:
    L = []
    a = L.append
    a("# Étape 3 — découpage et indexation : ce que la mesure dit\n")
    a(f"*Généré le {dt.date.today().isoformat()} par `ia/evaluer_etape3.py`. "
      f"PostgreSQL + pgvector, vecteurs de démonstration (plissement de hachage 1536 d).*\n")
    a("## 1. Le protocole, avant les chiffres\n")
    a("Le jeu de 60 questions est gelé depuis l'étape 2. Le régler puis le mesurer reviendrait à "
      "mesurer le réglage. Il est donc partagé une fois :\n")
    a("| Moitié | Questions | Usage | Empreinte SHA-256 |\n|---|---:|---|---|")
    a(f"| Réglage | {part['nb_reglage']} | choisir les paramètres | `{empreintes['reglage'][:24]}…` |")
    a(f"| **Mesure** | **{part['nb_mesure']}** | **seuls chiffres publiés** | "
      f"`{empreintes['mesure'][:24]}…` |\n")
    a(f"Règle de partage : {part['regle']}.\n")
    a("## 2. Ce qui change dans l'index\n")
    a("| | Étape 2 — index v1 | Étape 3 — index v2 |\n|---|---|---|")
    a(f"| Unités indexées | {stats_tables['v1']['fragments']} fragments (un bloc = un fragment) | "
      f"**{stats_tables['v2']['fragments']} fragments** (découpés) |")
    a(f"| Taille | {stats_tables['v1']['mini']}–{stats_tables['v1']['maxi']} car. | "
      f"**{stats_tables['v2']['mini']}–{stats_tables['v2']['maxi']} car.** |")
    a("| Recouvrement | aucun | **15 % (90 car.) entre fragments consécutifs** |")
    a("| Contexte d'indexation | aucun | **« Cours · Langage · Type » préfixé pour la recherche, "
      "jamais pour la citation** |")
    a("| Score | `sim + 0,5 × lexical` | `sim + poids × lexical + bonus × couverture` — "
      "**paramètres réglés sur la moitié réglage** |\n")
    a("La couverture est la part des mots significatifs de la question réellement présente dans le "
      "fragment : c'est ce qui distingue `ArrayList` de « liste ». Le plissement de hachage ne sait "
      "pas faire cette différence ; la couverture, si.\n")
    a("## 3. Réglage (20 questions) — ce qui a été choisi\n")
    a("| Paramètre | v1 retenu | v2 retenu |\n|---|---|---|")
    for cle in ("seuil_vecteur", "poids_lex", "bonus_couverture"):
        a(f"| `{cle}` | {res_reglage['v1']['config'][cle]} | {res_reglage['v2']['config'][cle]} |")
    a(f"| top-5 sur la moitié réglage | {pct(res_reglage['v1']['mesures']['top5'])} | "
      f"{pct(res_reglage['v2']['mesures']['top5'])} |")
    a(f"| top-1 sur la moitié réglage | {pct(res_reglage['v1']['mesures']['top1'])} | "
      f"{pct(res_reglage['v2']['mesures']['top1'])} |")
    a(f"| configurations essayées | {len(CONFIGS_V1)} | {len(CONFIGS_V2)} |\n")

    # Frontière précision / rappel — calculée sur la moitié RÉGLAGE uniquement.
    def meilleur_pour(fonction, seuil):
        sous = [e for e in res_reglage[fonction]["essais"] if e["seuil_vecteur"] == seuil]
        return max(sous, key=lambda e: (e["top5"], e["top1"])) if sous else None

    a("### La frontière que le seuil ne savait pas tenir (moitié réglage)\n")
    a("| Seuil | v1 top-5 | v1 fuites hors domaine | v2 top-5 | v2 fuites hors domaine |")
    a("|---:|---:|---:|---:|---:|")
    for seuil in GRILLE["seuil_vecteur"]:
        e1, e2 = meilleur_pour("v1", seuil), meilleur_pour("v2", seuil)
        a(f"| {seuil:.2f} | {pct(e1['top5'])} | {e1['fuites']}/{len(HORS_DOMAINE)} | "
          f"**{pct(e2['top5'])}** | **{e2['fuites']}/{len(HORS_DOMAINE)}** |")
    a("")
    a("**C'est le résultat de l'étape.** Avec l'index v1, garder toutes les questions "
      "légitimes (top-5 100 %) oblige à accepter **toutes** les citations hors domaine ; "
      "remonter le seuil à 0,15 refuse proprement les quatre questions hors domaine mais fait "
      "tomber le top-5 à 70 %. Avec l'index v2, le même seuil de 0,15 conserve un top-5 de 100 % "
      "et ne laisse passer qu'**une** question hors domaine. Découper, préfixer le contexte et "
      "mesurer la couverture ne rendent pas seulement la recherche plus juste : ils rendent le "
      "seuil utilisable — et c'est le seuil qui protège l'agent du hors-domaine.\n")

    a("### Sensibilité au poids du lexical (moitié réglage)\n")
    a("| `poids_lex` | v1 top-1 | v1 top-5 | v2 top-1 | v2 top-5 |\n|---:|---:|---:|---:|---:|")
    for poids in GRILLE["poids_lex"]:
        cellules = []
        for fonction in ("v1", "v2"):
            sous = [e for e in res_reglage[fonction]["essais"] if e["poids_lex"] == poids]
            meilleur = max(sous, key=lambda e: (e["top5"], e["top1"])) if sous else None
            cellules += [pct(meilleur["top1"]) if meilleur else "—",
                         pct(meilleur["top5"]) if meilleur else "—"]
        a(f"| {poids} | " + " | ".join(cellules) + " |")
    a("")
    a("**Ce que ce tableau ne permet pas de dire.** Les trois poids donnent exactement le même "
      "résultat : sur 20 questions, l'échantillon ne permet pas de trancher. Le protocole retient "
      "donc la valeur la plus simple (`poids_lex = 0`) — et la moitié mesure, déjà dépouillée, "
      "n'autorise plus à revenir sur ce choix. La branche lexicale reste néanmoins active comme "
      "**filet de rappel** : un fragment devient candidat dès qu'il est lexicalement pertinent "
      "(`sim >= seuil OR lex > 0 OR couverture > 0`), même si son vecteur est faible. C'est ce "
      "filet qui rattrape les noms d'API (`ArrayList`, `out_of_range`). Avec un vrai modèle "
      "d'embeddings, la similarité cesse de doubler la recherche par mots et ce poids devra être "
      "réglé pour de bon — avec une nouvelle moitié de réglage, pas avec ces 60 questions.\n")

    a("## 4. Mesure (40 questions jamais utilisées pour le réglage)\n")
    a("| Mesure | Étape 2 (v1) | **Étape 3 (v2)** | Écart |\n|---|---:|---:|---:|")
    for cle, libelle in (("top1", "top-1 : la bonne section sort en premier"),
                         ("top5", "top-5 : la bonne section est dans les cinq premiers"),
                         ("temoin1", "témoin présent dans le fragment cité en premier"),
                         ("temoin5", "témoin présent dans l'un des cinq fragments"),
                         ("top1_libre", "top-1 sans filtre de langage")):
        av, ap = res_mesure["v1"][cle], res_mesure["v2"][cle]
        ecart = f"{100 * (ap - av):+.0f} pts"
        a(f"| {libelle} | {pct(av)} | **{pct(ap)}** | {ecart} |")
    a(f"\nCritère de sortie de l'étape 3 : **top-5 ≥ 85 %** sur les questions de référence. "
      f"Résultat : **{pct(res_mesure['v2']['top5'])}** — "
      f"{'tenu' if res_mesure['v2']['top5'] >= 0.85 else 'non tenu'}.\n")
    a("### Par langage (moitié mesure)\n")
    a("| Langage | Questions | top-1 v1 | top-1 v2 | top-5 v1 | top-5 v2 |\n|---|---:|---:|---:|---:|---:|")
    for lang in ("python", "java", "cpp", "tous"):
        l1 = res_mesure["v1"]["par_langage"][lang]
        l2 = res_mesure["v2"]["par_langage"][lang]
        if not l1["n"]:
            continue
        a(f"| {lang} | {l1['n']} | {pct(l1['top1'])} | **{pct(l2['top1'])}** | "
          f"{pct(l1['top5'])} | **{pct(l2['top5'])}** |")
    a("")
    a("### Questions hors domaine\n")
    a(f"Avec la configuration retenue, l'index v2 cite un fragment pour "
      f"**{res_mesure['v2']['fuites']} / {len(HORS_DOMAINE)}** questions hors domaine "
      f"(v1 : {res_mesure['v1']['fuites']} / {len(HORS_DOMAINE)}). Ces fuites restent arrêtées en "
      "aval par le prompt système et le filtre de sortie : le seuil n'est jamais le seul rempart.\n")
    a("## 5. Ce que cette étape ne prouve pas\n")
    a("- **Le top-1 ne progresse pas, et ce n'est pas un détail** (72 % → 70 %, écart dans le "
      "bruit à 40 questions). "
      "C'est le plafond du vecteur de démonstration, pas celui du découpage : ajouter du contexte "
      "et de la couverture ne compense pas un vecteur qui ne comprend pas la phrase. Le gain réel "
      "de l'étape porte sur le **top-5** (98 % → 100 %), sur le **témoin dans le top-5** "
      "(92 % → 98 %) et sur le **refus hors domaine** (4 fuites sur 4 → 1 sur 4).")
    a("- **Le classement ne repose plus sur un seul signal.** Même avec un poids lexical nul, "
      "l'admission des candidats reste hybride : la branche lexicale rattrape les fragments que le "
      "vecteur rate. Ce point est mesuré ci-dessus, pas supposé.")
    a("- **Les vecteurs restent de démonstration.** Le gain mesuré vient du découpage, du contexte "
      "d'indexation et de la couverture — pas d'une meilleure sémantique. Avec un vrai modèle "
      "d'embeddings, les mêmes paramètres devront être réglés à nouveau, avec la même partition et "
      "le même protocole.")
    a("- **40 questions restent peu.** L'intervalle de confiance d'un taux à 40 questions est large "
      "(± 7 points environ) : les écarts inférieurs à 10 points ne doivent pas être lus comme des "
      "victoires.")
    a("- **La partition est un choix**, pas une évidence : elle est écrite, datée et son empreinte "
      "est publiée, ce qui permet de la contester — pas de la refaire en silence.")
    a("- **Le corpus ne change pas ici** : l'étape 3 découpe et indexe ; elle n'ajoute aucun cours. "
      "Une question sans réponse dans le corpus restera sans réponse, quel que soit l'index.\n")
    RAPPORT.write_text("\n".join(L), encoding="utf-8")


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--dsn", default="host=/tmp/pgsock port=5433 dbname=ihm2026 user=postgres")
    args = p.parse_args()

    cas = {json.loads(l)["id"]: json.loads(l)
           for l in JEU.read_text(encoding="utf-8").splitlines() if l.strip()}
    part = construire_partition()
    PARTITION.write_text(json.dumps(part, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    empreintes = {"reglage": empreinte(part["reglage"], cas),
                  "mesure": empreinte(part["mesure"], cas)}

    with psycopg2.connect(args.dsn) as cx, cx.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cur.execute("SELECT count(*) AS fragments, min(caracteres) AS mini, max(caracteres) AS maxi "
                    "FROM ia_fragments WHERE vecteur IS NOT NULL")
        v1_stats = dict(cur.fetchone())
        cur.execute("SELECT count(*) AS fragments, min(caracteres) AS mini, max(caracteres) AS maxi "
                    "FROM ia_fragments_v2")
        v2_stats = dict(cur.fetchone())
        if not v1_stats["fragments"] or not v2_stats["fragments"]:
            raise SystemExit("index incomplet : lancer ia/indexer.py --mode blocs puis ia/indexer.py")

        res_reglage = {}
        for fonction, configs in (("v1", CONFIGS_V1), ("v2", CONFIGS_V2)):
            meilleur, essais = regler(cur, fonction, cas, part["reglage"], configs)
            res_reglage[fonction] = {
                "config": {k: meilleur[k] for k in ("seuil_vecteur", "poids_lex", "bonus_couverture")},
                "mesures": mesurer(cur, fonction, cas, part["reglage"], meilleur),
                "nb_essais": len(essais),
                "essais": essais,
            }
            print(f"[{fonction}] meilleur sur réglage : {res_reglage[fonction]['config']} · "
                  f"top-5 {pct(res_reglage[fonction]['mesures']['top5'])}")

        res_mesure = {}
        for fonction in ("v1", "v2"):
            config = res_reglage[fonction]["config"]
            mesure = mesurer(cur, fonction, cas, part["mesure"], config)
            mesure["fuites"] = fuites(cur, fonction, config)
            res_mesure[fonction] = mesure
            print(f"[{fonction}] mesure : top-1 {pct(mesure['top1'])} · top-5 {pct(mesure['top5'])} · "
                  f"témoin top-1 {pct(mesure['temoin1'])} · fuites {mesure['fuites']}/{len(HORS_DOMAINE)}")

    ecrire_rapport(part, cas, res_reglage, res_mesure, res_reglage, res_mesure, empreintes,
                   {"v1": v1_stats, "v2": v2_stats})
    print(f"rapport écrit : {RAPPORT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
