#!/usr/bin/env python3
"""
Étape 2 — construction du corpus pédagogique de l'agent (RAG).

Entrée  : corpus/source-corpus.json   (grille 6 notions × 3 langages = 18 cours / 36 blocs,
                                       tous marqués role = COURS)
Sorties : corpus/corpus-v1.json       corpus indexé (cours → fragments prêts pour l'indexation)
          corpus/corpus-v1.tsv        même contenu au format d'ingestion (langage, section, contenu)
          evaluation/jeu-de-test-v1-60.jsonl   jeu de 60 questions FIGÉ
          corpus/MANIFESTE.json       empreintes SHA-256 (preuve d'antériorité du gel)
          evaluation/rapport-couverture-v1.md  rapport de couverture
          ../db/05-corpus-v1.sql      insertion idempotente des 18 cours / 36 blocs

Quatre passes de nettoyage, dans cet ordre et pour une raison :
  1. normalisation        — sinon deux écritures du même texte échappent au dédoublonnage ;
  2. filtre de rôle       — les corrigés (role = CORRIGE) sont retirés : l'agent cite un cours,
                            il ne diffuse jamais une correction d'exercice noté ;
  3. longueur minimale    — un fragment trop court n'a pas de contexte citable ;
  4. dédoublonnage        — empreinte exacte puis proximité (Jaccard sur les mots ≥ 0,85).

Le jeu de questions est écrit AVANT toute mesure : aucune évaluation postérieure au réglage
n'est admise, donc l'empreinte du fichier est enregistrée dans le manifeste au moment du gel.

Usage :
    python3 corpus/construire.py            # écrit les sorties et affiche le bilan
    python3 corpus/construire.py --verifier # contrôle seulement, n'écrit rien
"""
from __future__ import annotations

import argparse
import datetime as dt
import hashlib
import json
import pathlib
import re
import sys
import unicodedata

RACINE = pathlib.Path(__file__).resolve().parent.parent          # …/ihm-2026/ia
PROJET = RACINE.parent                                           # …/ihm-2026
SOURCE = RACINE / "corpus" / "source-corpus.json"
QUESTIONS = RACINE / "evaluation" / "questions-60.json"

LONGUEUR_MIN = {"TEXTE": 150, "CODE": 40}
TAILLE_MAX_FRAGMENT = 600
SEUIL_JACCARD = 0.85
ROLES_ADMIS = {"COURS"}
# Convention de la base de démonstration (db/02-seed.sql) : un seul niveau par parcours,
# de rang 1. La section citée par l'agent est construite comme dans db/03-corpus-ia.sql —
# « titre du cours — titre du niveau ». Les cours de la grille sont tous rattachés au
# rang 1 ; leur répartition par niveau relève de l'enseignant, pas du corpus.
NIVEAU_RANG_1 = {"python": "Variables et types",
                 "java": "Classes et objets",
                 "cpp": "Compilation et types"}
NB_QUESTIONS_ATTENDU = 60


# ------------------------------------------------------------------ passes
def normaliser(texte: str, type_bloc: str = "TEXTE") -> str:
    """NFC, espaces insécables, apostrophes typographiques, fins de ligne.

    Un bloc CODE garde son indentation : réduire ses espaces multiples casserait
    le programme affiché (l'indentation est significative en Python et lisible
    partout ailleurs). Seul un bloc TEXTE voit ses espaces multiples réduits.
    """
    texte = unicodedata.normalize("NFC", texte)
    texte = texte.replace("\u00a0", " ").replace("\u202f", " ")
    texte = texte.replace("\u2019", "'").replace("\u2018", "'")
    texte = texte.replace("\u201c", '"').replace("\u201d", '"')
    texte = texte.replace("\\n", "\n")            # le TSV hérité stockait des \n littéraux
    texte = texte.replace("\r\n", "\n").replace("\r", "\n")
    texte = "\n".join(ligne.rstrip() for ligne in texte.split("\n"))
    if type_bloc != "CODE":
        texte = re.sub(r"[ \t]{2,}", " ", texte)
    return texte.strip("\n")


def sans_accents(texte: str) -> str:
    return "".join(c for c in unicodedata.normalize("NFD", texte.lower())
                   if unicodedata.category(c) != "Mn")


def empreinte(texte: str) -> str:
    """Empreinte exacte : accents, casse, ponctuation et espaces neutralisés."""
    plat = re.sub(r"[^a-z0-9]+", " ", sans_accents(texte)).strip()
    return hashlib.sha256(plat.encode("utf-8")).hexdigest()


def mots(texte: str) -> set[str]:
    return set(plat.split()) if (plat := re.sub(r"[^a-z0-9]+", " ", sans_accents(texte))) else set()


def proches(a: set[str], b: set[str]) -> float:
    """Jaccard : deux textes qui se recouvrent à 85 % disent la même chose."""
    if not a or not b:
        return 0.0
    return len(a & b) / len(a | b)


def decouper(contenu: str) -> list[str]:
    """Découpe aux frontières de phrases : un fragment ne coupe jamais un mot."""
    if len(contenu) <= TAILLE_MAX_FRAGMENT:
        return [contenu]
    phrases = re.split(r"(?<=[.!?;:])\s+", contenu)
    fragments, courant = [], ""
    for phrase in phrases:
        candidat = (courant + " " + phrase).strip() if courant else phrase
        if len(candidat) > TAILLE_MAX_FRAGMENT and courant:
            fragments.append(courant.strip())
            courant = phrase
        else:
            courant = candidat
    if courant:
        fragments.append(courant.strip())
    return fragments


# ------------------------------------------------------------------ corpus
def construire(source: dict) -> tuple[list[dict], dict]:
    cours, journal = [], {"roles_retires": 0, "trop_courts": [], "doublons_exacts": [],
                          "doublons_proches": [], "fragments": 0}
    vus: list[tuple[str, str, set[str]]] = []          # (empreinte, libellé, mots)

    for notion in source["notions"]:
        for entree in notion["entrees"]:
            section = f"{entree['titre']} — {NIVEAU_RANG_1[entree['langage']]}"
            blocs = []
            for i, bloc in enumerate(entree["blocs"], start=1):
                role = str(bloc.get("role", "")).upper()
                if role not in ROLES_ADMIS:
                    journal["roles_retires"] += 1
                    continue
                contenu = normaliser(bloc["contenu"], bloc["type"])
                type_ = bloc["type"]
                if len(contenu) < LONGUEUR_MIN[type_]:
                    journal["trop_courts"].append(
                        {"section": section, "type": type_, "caracteres": len(contenu)})
                    continue
                emp, m = empreinte(contenu), mots(contenu)
                libelle = f"{section} [{type_} {i}]"
                if any(emp == e for e, _, _ in vus):
                    journal["doublons_exacts"].append(libelle)
                    continue
                jumeau = next((lab for _, lab, mo in vus if proches(m, mo) >= SEUIL_JACCARD), None)
                if jumeau:
                    journal["doublons_proches"].append({"garde": libelle, "retire": jumeau})
                    continue
                vus.append((emp, libelle, m))
                for k, morceau in enumerate(decouper(contenu), start=1):
                    blocs.append({
                        "id": f"{notion['id']}-{entree['langage']}-{type_[:1]}{i}-{k}",
                        "notion": notion["id"],
                        "notion_titre": notion["titre"],
                        "langage": entree["langage"],
                        "type": type_,
                        "role": role,
                        "section": section,
                        "ordre": i * 10 + k,
                        "caracteres": len(morceau),
                        "contenu": morceau,
                    })
                journal["fragments"] += len(decouper(contenu))
            cours.append({
                "section": section,
                "notion": notion["id"],
                "notion_titre": notion["titre"],
                "objectif": notion["objectif"],
                "titre": entree["titre"],
                "langage": entree["langage"],
                "blocs": blocs,
            })
    return cours, journal


def statistiques(cours: list[dict]) -> dict:
    fragments = [b for c in cours for b in c["blocs"]]
    par_langage, par_type, par_notion = {}, {}, {}
    for f in fragments:
        par_langage[f["langage"]] = par_langage.get(f["langage"], 0) + 1
        par_type[f["type"]] = par_type.get(f["type"], 0) + 1
        par_notion[f["notion"]] = par_notion.get(f["notion"], 0) + 1
    tailles = [f["caracteres"] for f in fragments]
    return {
        "cours": len(cours),
        "fragments": len(fragments),
        "caracteres_total": sum(tailles),
        "caracteres_moyen": round(sum(tailles) / len(tailles)) if tailles else 0,
        "caracteres_min": min(tailles) if tailles else 0,
        "caracteres_max": max(tailles) if tailles else 0,
        "par_langage": dict(sorted(par_langage.items())),
        "par_type": dict(sorted(par_type.items())),
        "par_notion": dict(sorted(par_notion.items())),
    }


# ------------------------------------------------------------------ questions
def charger_questions(cours: list[dict]) -> list[dict]:
    """Charge le jeu figé et vérifie qu'il est adossé au corpus (aucune question orpheline)."""
    brut = json.loads(QUESTIONS.read_text(encoding="utf-8"))
    par_section = {c["section"]: sans_accents(" ".join(b["contenu"] for b in c["blocs"]))
                   for c in cours}
    par_notion = {}
    for c in cours:
        par_notion.setdefault(c["notion"], []).append(c)

    problemes = []
    for q in brut:
        candidats = ([c for c in par_notion[q["notion"]] if c["langage"] == q["langage"]]
                     if q["langage"] else par_notion[q["notion"]])
        if not candidats:
            problemes.append(f"{q['id']} : aucun cours {q['notion']}/{q['langage']}")
            continue
        # la section attendue est la première du cours de ce langage (ou du groupe, si transversale)
        q["section"] = candidats[0]["section"]
        textes = " ".join(par_section[c["section"]] for c in candidats)
        if sans_accents(q["attendu"]) not in textes:
            problemes.append(f"{q['id']} : le témoin « {q['attendu']} » est absent du cours attendu")
    if len(brut) != NB_QUESTIONS_ATTENDU:
        problemes.append(f"{len(brut)} questions au lieu de {NB_QUESTIONS_ATTENDU}")
    ids = [q["id"] for q in brut]
    if len(set(ids)) != len(ids):
        problemes.append("identifiants de questions en double")
    if problemes:
        raise SystemExit("jeu de questions incohérent avec le corpus :\n  - " + "\n  - ".join(problemes))
    return brut


# ------------------------------------------------------------------ sorties
def ecrire_json(chemin: pathlib.Path, contenu) -> str:
    chemin.parent.mkdir(parents=True, exist_ok=True)
    texte = json.dumps(contenu, ensure_ascii=False, indent=2) + "\n"
    chemin.write_text(texte, encoding="utf-8")
    return hashlib.sha256(texte.encode("utf-8")).hexdigest()


def ecrire_tsv(chemin: pathlib.Path, cours: list[dict]) -> str:
    lignes = []
    for c in cours:
        for b in c["blocs"]:
            contenu = b["contenu"].replace("\n", "\\n")      # convention du corpus hérité
            lignes.append(f"{b['langage']}\t{c['section']}\t{contenu}")
    texte = "\n".join(lignes) + "\n"
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(texte, encoding="utf-8")
    return hashlib.sha256(texte.encode("utf-8")).hexdigest()


def echapper_sql(texte: str) -> str:
    return texte.replace("'", "''")


def ecrire_sql(chemin: pathlib.Path, cours: list[dict], version: str) -> str:
    """Insertion idempotente des cours de la grille, puis rafraîchissement des fragments.

    Trois précautions :
      · un cours déjà présent n'est ni dupliqué ni réécrit (`NOT EXISTS` sur le titre) ;
      · un bloc déjà présent à la même position n'est pas réinséré ;
      · les cours de démonstration que la grille remplace sont repassés en BROUILLON,
        jamais supprimés — et l'extraction des fragments (`ia_fragments`) est rejouée,
        sinon l'index continuerait de citer d'anciens cours en doublon.
    """
    morceaux = [f"""-- =====================================================================
--  Corpus pédagogique v1 — généré par ia/corpus/construire.py ({version})
--  6 notions x 3 langages = 18 cours, {sum(len(c['blocs']) for c in cours)} fragments citables.
--  Idempotent : relancer le script ne duplique ni un cours ni un bloc.
--  Aucun corrigé n'est inséré : seuls les blocs role = COURS entrent dans ce fichier.
--
--  Ordre d'application : 01-schema.sql, 02-seed.sql, 03-corpus-ia.sql,
--                        puis CE FICHIER, puis 04-ia-index.sql.
-- =====================================================================

BEGIN;
"""]
    for c in cours:
        langage = c["langage"]
        blocs = ",\n".join(
            "    ({}, '{}', {}, '{}')".format(
                b["ordre"], b["type"],
                "NULL" if b["type"] == "TEXTE" else f"'{langage}'",
                echapper_sql(b["contenu"]))
            for b in c["blocs"])
        titre = echapper_sql(c["titre"])
        morceaux.append(f"""
-- {c['notion']} / {langage} — {c['titre']}
WITH niveau AS (
    SELECT n.id FROM niveaux n
    JOIN parcours p ON p.id = n.parcours_id
    JOIN langages l ON l.id = p.langage_id AND l.code = '{langage}'
    WHERE n.rang = 1
), c AS (
    INSERT INTO cours (niveau_id, titre, statut)
    SELECT id, '{titre}', 'PUBLIE' FROM niveau
    WHERE NOT EXISTS (SELECT 1 FROM cours WHERE titre = '{titre}')
    RETURNING id
), cible AS (
    SELECT id FROM c
    UNION ALL
    SELECT id FROM cours WHERE titre = '{titre}'
), insere AS (
    INSERT INTO blocs (cours_id, position, type, langage, contenu)
    SELECT cible.id, v.position, v.type, v.langage, v.contenu
    FROM cible
    JOIN (VALUES
{blocs}
    ) AS v(position, type, langage, contenu) ON TRUE
    WHERE NOT EXISTS (
        SELECT 1 FROM blocs b WHERE b.cours_id = cible.id AND b.position = v.position)
    RETURNING 1
)
SELECT count(*) AS blocs_inseres FROM insere;
""")
    morceaux.append("""
-- ---------------------------------------------------------------- retrait
-- Les cours de démonstration antérieurs redisent une partie de la grille
-- (fonctions et valeur de retour). Les laisser PUBLIE ferait citer deux fois
-- la même notion par l'agent : ils repassent en BROUILLON, sans être supprimés.
UPDATE cours
   SET statut = 'BROUILLON', maj_le = now()
 WHERE statut = 'PUBLIE'
   AND titre IN ('Fonctions et modules',
                 'Méthodes et valeur de retour',
                 'Fonctions, retour et surcharge');

-- ------------------------------------------------ fragments indexables
-- Même formule de section que db/03-corpus-ia.sql : « cours — niveau ».
-- Rejoué ici pour que l'index reflète la grille dès l'application de ce fichier.
DO $$
BEGIN
    IF to_regclass('public.ia_fragments') IS NOT NULL THEN
        DELETE FROM ia_fragments;
        INSERT INTO ia_fragments (niveau_id, cours_id, langage, section, ordre, contenu, caracteres)
        SELECT n.id, c.id, COALESCE(b.langage, l.code),
               c.titre || ' — ' || n.titre, b.position, b.contenu, length(b.contenu)
          FROM blocs b
          JOIN cours c    ON c.id = b.cours_id
          JOIN niveaux n  ON n.id = c.niveau_id
          JOIN parcours p ON p.id = n.parcours_id
          JOIN langages l ON l.id = p.langage_id
         WHERE c.statut = 'PUBLIE';
    END IF;
END $$;

COMMIT;
""")
    texte = "".join(morceaux)
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(texte, encoding="utf-8")
    return hashlib.sha256(texte.encode("utf-8")).hexdigest()


def rapport(cours: list[dict], stats: dict, journal: dict, questions: list[dict],
            empreintes: dict, quand: str) -> str:
    l = []
    a = l.append
    a("# Rapport de couverture du corpus — v1\n")
    a(f"*Généré le {quand} par `ia/corpus/construire.py`. Source : "
      f"`ia/corpus/source-corpus.json` ({empreintes['source'][:16]}…).*\n")
    a("## 1. Ce que contient le corpus\n")
    a(f"- **{stats['cours']} cours** (6 notions × 3 langages) ;")
    a(f"- **{stats['fragments']} fragments citables** ;")
    a(f"- {stats['caracteres_total']} caractères, "
      f"moyenne {stats['caracteres_moyen']} par fragment "
      f"(min {stats['caracteres_min']}, max {stats['caracteres_max']}).\n")
    a("| Langage | Fragments | Part |\n|---|---:|---:|")
    for lang, n in stats["par_langage"].items():
        a(f"| {lang} | {n} | {100 * n / stats['fragments']:.1f} % |")
    a("")
    a("| Notion | Fragments |\n|---|---:|")
    notion_titre = {c["notion"]: c["notion_titre"] for c in cours}
    for notion, n in stats["par_notion"].items():
        a(f"| {notion} — {notion_titre[notion]} | {n} |")
    a("")
    a("## 2. Équilibre entre les trois langages\n")
    parts = stats["par_langage"].values()
    ecart = max(parts) - min(parts) if parts else 0
    if ecart == 0:
        a("**Le corpus est exactement équilibré** : chaque langage porte le même nombre de "
          "fragments, et chaque notion existe dans les trois langages. C'est la correction "
          "directe du déséquilibre relevé en v0.3.3 (5 fragments Python, 3 Java, 3 C++).\n")
    else:
        a(f"**Écart de {ecart} fragments** entre le langage le plus fourni et le moins fourni : "
          "à corriger avant toute mesure de qualité.\n")
    a("## 3. Passes de nettoyage\n")
    a(f"- rôles non `COURS` retirés : **{journal['roles_retires']}** ;")
    a(f"- blocs trop courts retirés (< 150 car. pour un TEXTE, < 40 pour un CODE) : "
      f"**{len(journal['trop_courts'])}** ;")
    a(f"- doublons exacts retirés : **{len(journal['doublons_exacts'])}** ;")
    a(f"- quasi-doublons retirés (Jaccard ≥ {SEUIL_JACCARD}) : "
      f"**{len(journal['doublons_proches'])}**.")
    for d in journal["doublons_proches"]:
        a(f"  - gardé `{d['garde']}`, retiré `{d['retire']}`")
    a("\nLes corrigés d'exercices notés n'entrent pas dans le corpus : l'agent cite un cours, "
      "il ne diffuse pas une correction. C'est le même principe que le contrat de refus "
      "(étape 1).\n")
    a("## 4. Jeu de 60 questions, figé avant tout réglage\n")
    a(f"- fichier : `ia/evaluation/jeu-de-test-v1-60.jsonl` ;")
    a(f"- **{len(questions)} questions**, empreinte SHA-256 `{empreintes['questions'][:32]}…` ;")
    a("- répartition : 10 questions par notion (3 Python, 3 Java, 3 C++, 1 transversale).\n")
    a("| Notion | Python | Java | C++ | Transversale | Total |\n|---|---:|---:|---:|---:|---:|")
    for notion in dict.fromkeys(q["notion"] for q in questions):
        qs = [q for q in questions if q["notion"] == notion]
        py = sum(1 for q in qs if q["langage"] == "python")
        ja = sum(1 for q in qs if q["langage"] == "java")
        cp = sum(1 for q in qs if q["langage"] == "cpp")
        tr = sum(1 for q in qs if q["langage"] is None)
        a(f"| {notion} | {py} | {ja} | {cp} | {tr} | {len(qs)} |")
    a("")
    a("Chaque question porte un **témoin** (`attendu`) : le mot que doit contenir le fragment "
      "cité pour que la réponse soit juste. `construire.py` refuse de geler le jeu si un témoin "
      "est absent du cours attendu — même discipline que les tests du contrat de refus.\n")
    a("## 5. Où le corpus est consommé\n")
    a("| Consommateur | Fichier | Rôle |\n|---|---|---|")
    a("| base PostgreSQL | `db/05-corpus-v1.sql` | insère les 18 cours / 36 blocs (idempotent), "
      "retire les cours de démonstration remplacés, rejoue l'extraction de `ia_fragments` |")
    a("| indexation | `ia/indexer.py` | découpe et vectorise les fragments de `ia_fragments` |")
    a("| évaluation | `ia/evaluer.py --jeu evaluation/jeu-de-test-v1-60.jsonl` | mesure "
      "top-1, top-5, témoin et hors-domaine |")
    a("| application JavaFX | `src/main/resources/fr/ihm2026/ia/corpus-fragments.tsv` | copie "
      "de `corpus-v1.tsv` : ce que `ModeleSimule` cite hors ligne |\n")
    a("## 6. Ce que ce rapport ne prouve pas\n")
    a("- Il ne mesure aucune performance par lui-même : les mesures de recherche sont dans "
      "`ia/evaluation/rapport-v1-60.md`, produit **après** le gel du jeu, et elles ne peuvent "
      "plus servir à ajuster les questions.")
    a("- Les vecteurs de démonstration (plissement de hachage) restent inchangés : ils valident "
      "la chaîne, pas la sémantique.")
    a("- L'équilibre porte sur le **nombre** de fragments, pas sur leur difficulté : la "
      "répartition par niveau reste à valider par l'enseignant — tous les cours sont rattachés "
      "au rang 1 de la base de démonstration.")
    a("- Le corpus sert un modèle **simulé** : `ModeleSimule` cite, il ne rédige pas.\n")
    return "\n".join(l)


# ------------------------------------------------------------------ main
def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--verifier", action="store_true", help="contrôle sans écrire")
    args = p.parse_args()

    source_texte = SOURCE.read_text(encoding="utf-8")
    source = json.loads(source_texte)
    cours, journal = construire(source)
    stats = statistiques(cours)
    questions = charger_questions(cours)
    quand = dt.datetime.now().replace(microsecond=0).isoformat()

    print(f"{stats['cours']} cours · {stats['fragments']} fragments · "
          f"{stats['par_langage']} · {len(questions)} questions vérifiées contre le corpus")
    if args.verifier:
        return 0

    corpus = {
        "version": "corpus-v1",
        "genere": quand,
        "source": {"fichier": "corpus/source-corpus.json",
                   "sha256": hashlib.sha256(source_texte.encode("utf-8")).hexdigest()},
        "regles": {"roles_admis": sorted(ROLES_ADMIS), "longueur_min": LONGUEUR_MIN,
                   "taille_max_fragment": TAILLE_MAX_FRAGMENT,
                   "seuil_jaccard": SEUIL_JACCARD},
        "statistiques": stats,
        "nettoyage": journal,
        "cours": cours,
    }
    e_corpus = ecrire_json(RACINE / "corpus" / "corpus-v1.json", corpus)
    e_tsv = ecrire_tsv(RACINE / "corpus" / "corpus-v1.tsv", cours)

    lignes = []
    for q in questions:
        ligne = {"id": q["id"], "langage": q["langage"], "notion": q["notion"],
                 "section": q["section"], "question": q["question"],
                 "attendu": q["attendu"], "type": q["type"]}
        lignes.append(json.dumps(ligne, ensure_ascii=False))
    texte_questions = "\n".join(lignes) + "\n"
    chemin_questions = RACINE / "evaluation" / "jeu-de-test-v1-60.jsonl"
    chemin_questions.write_text(texte_questions, encoding="utf-8")
    e_questions = hashlib.sha256(texte_questions.encode("utf-8")).hexdigest()
    # Signature INVARIANTE du jeu : énoncé, notion, langage, témoin et type — sans le libellé
    # de section. Corriger une faute dans un titre de cours change le fichier, pas le jeu :
    # cette signature permet de le prouver au lieu de l'affirmer.
    invariantes = "\n".join(sorted(
        json.dumps({k: q[k] for k in ("id", "notion", "langage", "question", "attendu", "type")},
                   ensure_ascii=False, sort_keys=True) for q in questions))
    e_invariantes = hashlib.sha256(invariantes.encode("utf-8")).hexdigest()

    e_sql = ecrire_sql(PROJET / "db" / "05-corpus-v1.sql", cours, corpus["version"])

    empreintes = {"source": corpus["source"]["sha256"], "corpus": e_corpus,
                  "tsv": e_tsv, "questions": e_questions,
                  "questions_invariantes": e_invariantes, "sql": e_sql}
    titre = rapport(cours, stats, journal, questions, empreintes, quand)
    (RACINE / "evaluation" / "rapport-couverture-v1.md").write_text(titre, encoding="utf-8")

    # Le gel est un engagement : si le jeu de questions n'a pas changé, la date de gel
    # d'origine est conservée — sinon, toute régénération ferait croire à un gel postérieur
    # à la première mesure.
    chemin_manifeste = RACINE / "corpus" / "MANIFESTE.json"
    gele = quand
    if chemin_manifeste.exists():
        try:
            ancien = json.loads(chemin_manifeste.read_text(encoding="utf-8"))
            anciennes = ancien.get("empreintes_sha256", {})
            # Le gel tient dès lors que le JEU est inchangé (signature invariante) ; corriger un
            # titre de cours ne remet pas le jeu en cause, mais la correction est consignée.
            if (anciennes.get("questions_invariantes", anciennes.get("questions")) == e_invariantes):
                gele = ancien.get("gele", quand)
        except (json.JSONDecodeError, OSError):
            pass

    manifeste = {
        "version": "corpus-v1",
        "gele": gele,
        "regenere": quand,
        "principe": "le jeu de 60 questions est figé AVANT la première mesure : "
                    "aucune évaluation postérieure au réglage n'est admise",
        "empreintes_sha256": empreintes,
        "corrections": ["2026-10-27 : titre du cours Java « Methodes… » corrigé en "
                        "« Méthodes… » ; énoncés, témoins et types des 60 questions inchangés "
                        "(signature invariante identique)"],
        "compteurs": {"cours": stats["cours"], "fragments": stats["fragments"],
                      "questions": len(questions)},
    }
    ecrire_json(chemin_manifeste, manifeste)

    print(f"corpus/corpus-v1.json         sha256 {e_corpus[:16]}…")
    print(f"corpus/corpus-v1.tsv          sha256 {e_tsv[:16]}…")
    print(f"evaluation/jeu-de-test-v1-60  sha256 {e_questions[:16]}…  (FIGÉ)")
    print(f"db/05-corpus-v1.sql           sha256 {e_sql[:16]}…")
    print("evaluation/rapport-couverture-v1.md écrit")
    return 0


if __name__ == "__main__":
    sys.exit(main())
