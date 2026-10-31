#!/usr/bin/env python3
"""
Étape 3 — découpage et indexation du corpus (RAG).

Ce que le script fait, et pourquoi dans cet ordre :

  1. **Découpage** — un fragment indexé doit tenir entre 300 et 600 caractères, avec un
     recouvrement de 15 % entre deux fragments consécutifs du même cours. En dessous de
     300, le fragment n'a pas assez de contexte pour être cité ; au-dessus de 600, il noie
     la phrase utile. Le recouvrement évite qu'une réponse à cheval sur deux fragments
     devienne introuvable.
  2. **Préfixe contextuel** — chaque fragment est indexé avec son contexte
     (« Cours … · Langage … · Type … »). Le texte cité reste inchangé : seul ce qui sert à
     *retrouver* le fragment porte le contexte.
  3. **Vectorisation** — vecteurs de démonstration (plissement de hachage 1536 d, hors ligne).
     Ils valident la chaîne PostgreSQL + pgvector sans clé d'API ; la sémantique viendra avec
     un vrai modèle d'embeddings, la colonne et les index ne changeront pas.
  4. **Indexation** — deux tables coexistent pour permettre la comparaison :
       · `ia_fragments`    : un bloc = un fragment (étape 2, témoin de départ) ;
       · `ia_fragments_v2` : fragments découpés 300-600 avec recouvrement (étape 3).

Usage :
    python3 indexer.py                       # découpe le corpus et remplit ia_fragments_v2
    python3 indexer.py --mode blocs          # remplit ia_fragments (comportement de l'étape 2)
    python3 indexer.py --dsn "host=/tmp/pgsock port=5433 dbname=ihm2026 user=postgres"
"""
from __future__ import annotations

import argparse
import hashlib
import math
import re
import unicodedata

import psycopg2
import psycopg2.extras

DIMENSIONS = 1536
TAILLE_MIN, TAILLE_MAX = 300, 600
RECOUVREMENT = 0.15                      # 15 % de TAILLE_MAX, soit 90 caractères
RECOUVREMENT_CAR = int(TAILLE_MAX * RECOUVREMENT)

MOTS_VIDES = {
    "le", "la", "les", "un", "une", "des", "du", "de", "d", "et", "ou", "a", "au",
    "aux", "en", "dans", "sur", "par", "pour", "avec", "sans", "que", "qui", "quoi",
    "est", "sont", "ce", "cet", "cette", "il", "elle", "on", "nous", "vous", "ils",
    "ne", "pas", "plus", "se", "sa", "son", "ses", "leur", "y", "si", "comme",
}


# ------------------------------------------------------------------ texte
def normaliser(texte: str) -> str:
    """Minuscules sans accents : « retournée » et « retournee » se rejoignent."""
    texte = unicodedata.normalize("NFD", texte.lower())
    return "".join(c for c in texte if unicodedata.category(c) != "Mn")


def jetons(texte: str) -> list[str]:
    """Mots significatifs + bigrammes, ce qui capture « valeur de retour »."""
    mots = [m for m in re.findall(r"[a-z0-9_:]+", normaliser(texte)) if len(m) > 1]
    mots = [m for m in mots if m not in MOTS_VIDES]
    bigrammes = [f"{a}_{b}" for a, b in zip(mots, mots[1:])]
    return mots + bigrammes


def vectoriser(texte: str) -> list[float]:
    """Vecteur L2-normalisé par plissement de hachage (démonstration hors ligne)."""
    vecteur = [0.0] * DIMENSIONS
    for jeton in jetons(texte):
        indice = int(hashlib.md5(jeton.encode()).hexdigest(), 16) % DIMENSIONS
        signe = 1.0 if int(hashlib.sha1(jeton.encode()).hexdigest(), 16) % 2 else -1.0
        poids = 0.7 if "_" in jeton else 1.0
        vecteur[indice] += signe * poids
    norme = math.sqrt(sum(v * v for v in vecteur)) or 1.0
    return [v / norme for v in vecteur]


# ------------------------------------------------------------------ découpage
def phrases(texte: str) -> list[str]:
    """Découpe aux frontières de phrases — jamais au milieu d'un mot."""
    return [p for p in re.split(r"(?<=[.!?;:])\s+", texte) if p.strip()]


def queue_de_recouvrement(texte: str, taille: int = RECOUVREMENT_CAR) -> str:
    """Fin d'un fragment, pour amorcer le suivant : environ 15 % du fragment précédent.

    La coupe tombe sur une frontière de phrase si cela laisse au moins 60 caractères de
    recouvrement ; sinon, sur une frontière de mot. Prendre systématiquement la dernière
    phrase produit un recouvrement trop court (constaté : 40 caractères au lieu de 90 sur
    le cours « Boucles en C++ ») — un recouvrement trop maigre ne sert à rien.
    """
    if len(texte) <= taille:
        return texte
    reste = texte[-taille:]
    mini = int(taille * 0.66)
    for borne in re.finditer(r"[.!?;:]\s+", reste):
        if len(reste) - borne.end() >= mini:
            return reste[borne.end():]
    return reste.split(" ", 1)[-1]


def decouper_cours(blocs: list[dict]) -> list[dict]:
    """Découpe un cours (suite de blocs ordonnés) en fragments de 300 à 600 caractères.

    Les blocs consécutifs d'un même cours sont regroupés tant que la taille le permet :
    un TEXTE suivi de son CODE forme ainsi un fragment citable complet. Un bloc plus long
    que 600 caractères est découpé par phrases.
    """
    unites: list[tuple[str, str]] = []          # (type, texte)
    for bloc in blocs:
        contenu = bloc["contenu"].strip()
        if len(contenu) <= TAILLE_MAX:
            unites.append((bloc["type"], contenu))
            continue
        courant = ""
        for phrase in phrases(contenu):
            candidat = (courant + " " + phrase).strip() if courant else phrase
            if len(candidat) > TAILLE_MAX and courant:
                unites.append((bloc["type"], courant))
                courant = queue_de_recouvrement(courant) + " " + phrase
                courant = courant.strip()
            else:
                courant = candidat
        if courant:
            unites.append((bloc["type"], courant))

    fragments: list[dict] = []
    courant, types, precedent = "", [], ""
    for type_bloc, texte in unites:
        candidat = (courant + "\n\n" + texte) if courant else texte
        if len(candidat) > TAILLE_MAX and courant:
            fragments.append({"contenu": courant.strip(), "types": "+".join(dict.fromkeys(types))})
            amorce = queue_de_recouvrement(courant.strip())
            courant = (amorce + "\n\n" + texte).strip()
            types = [type_bloc]
        else:
            courant = candidat
            if type_bloc not in types:
                types.append(type_bloc)
        precedent = texte
    if courant.strip():
        fragments.append({"contenu": courant.strip(), "types": "+".join(dict.fromkeys(types))})

    # Un dernier fragment trop court est fusionné avec le précédent quand la place le permet :
    # un fragment de 40 caractères n'a pas de contexte citable.
    if len(fragments) >= 2 and len(fragments[-1]["contenu"]) < TAILLE_MIN:
        fusion = fragments[-2]["contenu"] + "\n\n" + fragments[-1]["contenu"]
        if len(fusion) <= TAILLE_MAX:
            fragments[-2]["contenu"] = fusion
            fragments[-2]["types"] = "+".join(dict.fromkeys(
                (fragments[-2]["types"] + "+" + fragments[-1]["types"]).split("+")))
            fragments.pop()

    for i, f in enumerate(fragments, start=1):
        f["ordre"] = i
        f["caracteres"] = len(f["contenu"])
    return fragments


def prefixe(section: str, langage: str, types: str) -> str:
    """Contexte d'indexation : il sert à retrouver le fragment, jamais à le citer."""
    return f"Cours : {section} · Langage : {langage} · Type : {types}"


# ------------------------------------------------------------------ indexation
def lire_blocs(cur) -> list[dict]:
    cur.execute("""
        SELECT c.id AS cours_id, n.id AS niveau_id, c.titre AS cours, n.titre AS niveau,
               COALESCE(b.langage, l.code) AS langage, b.position, b.type, b.contenu
          FROM blocs b
          JOIN cours c    ON c.id = b.cours_id
          JOIN niveaux n  ON n.id = c.niveau_id
          JOIN parcours p ON p.id = n.parcours_id
          JOIN langages l ON l.id = p.langage_id
         WHERE c.statut = 'PUBLIE'
         ORDER BY c.id, b.position
    """)
    return [dict(r) for r in cur.fetchall()]


def grouper(blocs: list[dict]) -> list[dict]:
    cours: dict[int, dict] = {}
    for b in blocs:
        c = cours.setdefault(b["cours_id"], {"cours_id": b["cours_id"], "niveau_id": b["niveau_id"],
                                             "section": f"{b['cours']} — {b['niveau']}",
                                             "langage": b["langage"], "blocs": []})
        c["blocs"].append({"position": b["position"], "type": b["type"], "contenu": b["contenu"]})
    return list(cours.values())


def indexer_blocs(dsn: str) -> dict:
    """Mode étape 2 : un bloc = un fragment, table `ia_fragments`."""
    with psycopg2.connect(dsn) as cx, cx.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        blocs = lire_blocs(cur)
        ecrits = 0
        for b in blocs:
            section = f"{b['cours']} — {b['niveau']}"
            cur.execute("""
                UPDATE ia_fragments
                   SET vecteur = %s, contenu = %s, caracteres = %s
                 WHERE cours_id = %s AND ordre = %s
            """, (vectoriser(b["contenu"]), b["contenu"], len(b["contenu"]),
                  b["cours_id"], b["position"]))
            ecrits += cur.rowcount
        cx.commit()
        cur.execute("""SELECT langage, count(*) AS fragments, min(caracteres) AS mini,
                              max(caracteres) AS maxi, round(avg(caracteres)) AS moyen
                         FROM ia_fragments WHERE vecteur IS NOT NULL GROUP BY langage ORDER BY langage""")
        stats = [dict(r) for r in cur.fetchall()]
    return {"mode": "blocs", "ecrits": ecrits, "stats": stats}


def indexer_fragments(dsn: str) -> dict:
    """Mode étape 3 : fragments 300-600 avec recouvrement, table `ia_fragments_v2`."""
    with psycopg2.connect(dsn) as cx, cx.cursor(cursor_factory=psycopg2.extras.RealDictCursor) as cur:
        cours = grouper(lire_blocs(cur))
        cur.execute("TRUNCATE ia_fragments_v2 RESTART IDENTITY")
        ecrits = 0
        for c in cours:
            for f in decouper_cours(c["blocs"]):
                contexte = prefixe(c["section"], c["langage"], f["types"])
                cur.execute("""
                    INSERT INTO ia_fragments_v2
                        (niveau_id, cours_id, langage, section, notion, ordre, type_bloc,
                         contexte, contenu, caracteres, vecteur)
                    VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """, (c["niveau_id"], c["cours_id"], c["langage"], c["section"],
                      notion_de(c["section"]), f["ordre"], f["types"], contexte,
                      f["contenu"], f["caracteres"],
                      vectoriser(contexte + "\n" + f["contenu"])))
                ecrits += 1
        cx.commit()
        cur.execute("""SELECT langage, count(*) AS fragments, min(caracteres) AS mini,
                              max(caracteres) AS maxi, round(avg(caracteres)) AS moyen
                         FROM ia_fragments_v2 GROUP BY langage ORDER BY langage""")
        stats = [dict(r) for r in cur.fetchall()]
        cur.execute("SELECT count(*) AS n FROM ia_fragments_v2 WHERE caracteres > %s", (TAILLE_MAX,))
        debordements = cur.fetchone()["n"]
    return {"mode": "fragments", "ecrits": ecrits, "stats": stats, "debordements": debordements}


NOTIONS = {  # rattachement lisible : la section porte le titre pédagogique du cours
    "Variables et types": "VAR", "Conditions": "COND", "Boucles": "BOUCLE",
    "Fonctions": "FONC", "Méthodes": "FONC", "Collections": "COLL", "Listes": "COLL",
    "Conteneurs": "COLL", "Erreurs": "ERREUR", "Exceptions": "ERREUR",
}


def notion_de(section: str) -> str:
    for prefixe_titre, code in NOTIONS.items():
        if section.startswith(prefixe_titre):
            return code
    return "DIVERS"


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--dsn", default="host=/tmp/pgsock port=5433 dbname=ihm2026 user=postgres")
    p.add_argument("--mode", choices=["fragments", "blocs"], default="fragments")
    args = p.parse_args()

    res = indexer_fragments(args.dsn) if args.mode == "fragments" else indexer_blocs(args.dsn)
    if res["mode"] == "blocs":
        print(f"fragments indexés : {res['ecrits']} (un bloc = un fragment)")
    else:
        print(f"fragments indexés : {res['ecrits']} (découpage {TAILLE_MIN}-{TAILLE_MAX}, "
              f"recouvrement {int(RECOUVREMENT * 100)} %) · hors bornes : {res['debordements']}")
    for s in res["stats"]:
        print(f"  {s['langage']:7s} {s['fragments']:3d} fragments · "
              f"{s['mini']}-{s['maxi']} car. (moyenne {s['moyen']})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
