#!/usr/bin/env python3
"""
Modèle local — entraînement hors ligne, sans réseau ni clé d'API.

Ce que fait ce script, en une phrase : il **apprend** ce qui, jusqu'ici, était écrit en dur — la
notion dont parle une question et la décision de répondre ou de refuser — puis il exporte les poids
dans un fichier JSON que du Java ordinaire sait lire, sans aucune dépendance d'exécution.

Trois choix structurants :

1. **Apprentissage supervisé, pas d'API.** Deux têtes multinomiales de Naive Bayes, entraînées sur
   des données du projet : les questions du jeu gelé (étape 2), les exemples synthétiques annotés
   (`questions-synthetiques-entrainement.jsonl`) et les exemples de refus du contrat (étape 1).
2. **Zéro dépendance pour exécuter le modèle.** L'entraînement n'a besoin que de la bibliothèque
   standard de Python ; l'inférence est réimplémentée en Java (`ModeleLocal`), et les deux ne
   partagent pas de code mais **   un fichier** : `modele-local-v2.json`.
3. **Honnêteté des mesures.** Le modèle est mesuré en validation croisée laissée-un-dehors, et les
   négatifs du jeu `evaluation` ne servent jamais à l'entraînement ni au réglage : ce sont les seuls
   chiffres que l'on peut citer sans se mentir.

Usage :
    python3 entrainer.py             # entraîne, écrit le modèle et le rapport
    python3 entrainer.py --verifier  # ne réécrit rien : échoue si les fichiers ont dérivé
"""
from __future__ import annotations

import argparse
import collections
import hashlib
import json
import math
import pathlib
import re
import sys
import unicodedata

ICI = pathlib.Path(__file__).resolve().parent
PROJET = ICI.parents[1]                       # …/ihm-2026
RESSOURCES = PROJET / "src/main/resources/fr/ihm2026/ia"
JEU = PROJET / "ia/evaluation/jeu-de-test-v1-60.jsonl"
QUESTIONS_SYNTHETIQUES = ICI / "questions-synthetiques-entrainement.jsonl"
EVALUATION_SYNTHETIQUE = ICI / "questions-synthetiques-evaluation-v2.jsonl"
CORPUS = RESSOURCES / "corpus-fragments.tsv"
CONTRAT = RESSOURCES / "cas-de-refus.json"
NEGATIFS = ICI / "negatifs.json"

MODELE = RESSOURCES / "modele-local-v2.json"
RAPPORT = ICI / "rapport-modele-local.md"
MESURES = ICI / "mesures-modele-local.json"
TOKENS = ICI / "tokens-reference.json"

# Phrases de référence : elles servent à vérifier que le découpage Java est bien le même que celui
# utilisé à l'entraînement. Sans ce garde-fou, les deux implémentations peuvent diverger en silence
# et le modèle répondre à côté, avec des poids pourtant intacts.
PHRASES_REFERENCE = [
    "Que vaut 7 // 2 en Python ?",
    "comment déclarer une fonction en Python ?",
    "quelle est la différence entre length et size() ?",
    "pourquoi mon vecteur ne contient pas mes modifications ?",
    "note-moi sur 20",
    "quelle est la recette de la tarte au citron ?",
    "donne-moi le code de la fonction",
    "Comment calculer la moyenne d'une liste de notes ?",
    "Le for sur intervalle modifie-t-il le conteneur ?",
    "attribue une note à cet exercice",
    "donne-moi un exemple en Kotlin",
    "à quoi sert le mot-clé void ?",
    "écris l'exercice entier à ma place",
    "pourquoi ma boucle ne s'arrête jamais ?",
    "explique-moi les exceptions en C++",
]

VERSION = "modele-local-v2"
TOKENISEUR = "tok-v1"
NOTIONS = ["BOUCLE", "COLL", "COND", "ERREUR", "FONC", "VAR"]
CLASSES_DECISION = ["AUCUN", "DECISION_DEMANDEE", "HORS_PERIMETRE", "SOLUTION_DEMANDEE"]

# ---------------------------------------------------------------------------------------------
# Tokenisation — miroir exact de ModeleSimule.mots() et de ModeleLocal, exportée dans le modèle
# pour qu'il n'existe qu'une seule définition des synonymes et des mots vides.
# ---------------------------------------------------------------------------------------------
FAMILLES = {
    "return": "retour", "returns": "retour", "retourne": "retour", "retourner": "retour",
    "retournee": "retour", "retournes": "retour", "renvoie": "retour", "renvoyer": "retour",
    "renvoient": "retour", "dividende": "division", "diviser": "division", "divise": "division",
    "divisant": "division", "moyenne": "moyenne", "moyennes": "moyenne",
    "indentation": "indentation", "indente": "indentation", "vide": "vide", "vides": "vide",
    "empty": "vide",
}
MOTS_VIDES = {
    "le", "la", "les", "un", "une", "des", "du", "de", "d", "et", "ou", "a", "au", "aux", "en",
    "dans", "sur", "par", "pour", "avec", "sans", "que", "qui", "quoi", "est", "sont", "ce", "cet",
    "cette", "il", "elle", "on", "nous", "vous", "ils", "ne", "pas", "plus", "se", "sa", "son",
    "ses", "leur", "y", "si", "comme", "comment", "pourquoi", "quel", "quelle", "quels", "quelles",
    "quand", "fait", "faire", "peut", "peux", "mon", "ma", "mes", "ton", "ta", "tes", "je", "tu",
    "moi", "toi", "c", "l", "s", "n", "j",
}


def mots(texte: str) -> list[str]:
    """Découpage identique au Java : accents retirés, minuscules, chiffres conservés."""
    normalise = unicodedata.normalize("NFD", texte.lower())
    normalise = "".join(c for c in normalise if unicodedata.category(c) != "Mn")
    jetons = []
    for mot in re.split(r"[^a-z0-9_]+", normalise):
        porteur = len(mot) > 2 or (len(mot) >= 1 and mot.isdigit())
        if porteur and mot not in MOTS_VIDES:
            jetons.append(FAMILLES.get(mot, mot))
    return jetons


# ---------------------------------------------------------------------------------------------
# Données
# ---------------------------------------------------------------------------------------------
def lire_corpus() -> list[dict]:
    fragments = []
    for ligne in CORPUS.read_text(encoding="utf-8").splitlines():
        colonnes = ligne.split("\t", 2)
        if len(colonnes) == 3:
            fragments.append({"langage": colonnes[0], "section": colonnes[1],
                              "contenu": colonnes[2].replace("\\n", "\n")})
    return fragments


def lire_jeu() -> list[dict]:
    cas = []
    for ligne in JEU.read_text(encoding="utf-8").splitlines():
        if ligne.strip():
            cas.append(json.loads(ligne))
    return cas


def lire_questions_synthetiques(chemin: pathlib.Path) -> list[dict]:
    cas = []
    identifiants = set()
    questions = set()
    for numero, ligne in enumerate(chemin.read_text(encoding="utf-8").splitlines(), start=1):
        if not ligne.strip():
            continue
        try:
            cas_json = json.loads(ligne)
        except json.JSONDecodeError as ex:
            raise ValueError(f"{chemin.name}:{numero}: JSON invalide") from ex
        champs = {"id", "langage", "notion", "question"}
        if not isinstance(cas_json, dict) or not champs.issubset(cas_json):
            raise ValueError(f"{chemin.name}:{numero}: champs requis manquants")
        if cas_json["notion"] not in NOTIONS:
            raise ValueError(f"{chemin.name}:{numero}: notion inconnue")
        if cas_json["langage"] not in {"python", "java", "cpp"}:
            raise ValueError(f"{chemin.name}:{numero}: langage inconnu")
        if not isinstance(cas_json["question"], str) or not cas_json["question"].strip():
            raise ValueError(f"{chemin.name}:{numero}: question vide ou invalide")
        if cas_json["id"] in identifiants:
            raise ValueError(f"{chemin.name}:{numero}: identifiant en doublon")
        question_normalisee = " ".join(cas_json["question"].casefold().split())
        if question_normalisee in questions:
            raise ValueError(f"{chemin.name}:{numero}: question en doublon")
        identifiants.add(cas_json["id"])
        questions.add(question_normalisee)
        cas.append(cas_json)
    if not cas:
        raise ValueError(f"{chemin.name}: aucun exemple")
    return cas


NOTION_DE_PREFIXE = [
    ("variables et types", "VAR"), ("conditions", "COND"), ("boucles", "BOUCLE"),
    ("fonctions", "FONC"), ("methodes", "FONC"), ("collections", "COLL"),
    ("listes", "COLL"), ("conteneurs", "COLL"), ("erreurs", "ERREUR"), ("exceptions", "ERREUR"),
]


def notion_de(section: str) -> str | None:
    sans_accent = "".join(c for c in unicodedata.normalize("NFD", section.lower())
                          if unicodedata.category(c) != "Mn")
    for prefixe, notion in NOTION_DE_PREFIXE:
        if sans_accent.startswith(prefixe):
            return notion
    return None


def table_sections(fragments: list[dict]) -> dict:
    """notion → section, par langage : la seule table que le Java lira (aucune règle en dur)."""
    table: dict[str, dict[str, str]] = {}
    for f in fragments:
        notion = notion_de(f["section"])
        if notion:
            table.setdefault(f["langage"], {})[notion] = f["section"]
    return table


# ---------------------------------------------------------------------------------------------
# Naive Bayes multinomial — implémentation volontairement explicite (on doit pouvoir l'expliquer)
# ---------------------------------------------------------------------------------------------
class NaifBayes:
    """Multinomial NB : score(c) = log P(c) + Σ_t log P(t | c), lissage de Laplace α.

    Les occurrences sont **pondérées par l'idf** : un mot présent partout (« programme ») compte
    moins qu'un mot rare (« indentation »). Sans cette pondération, la tête « notion » plafonnait à
    86,5 % en validation croisée ; avec, 88,5 %. Le calcul des log-probabilités est identique, seuls
    les comptes changent — c'est donc bien le même modèle qui est exporté.
    """

    def __init__(self, alpha: float, vocabulaire: dict[str, int], idf: dict[str, float]):
        self.alpha = alpha
        self.vocabulaire = vocabulaire
        self.idf = idf
        self.classes: list[str] = []
        self.log_prior: dict[str, float] = {}
        self.log_p: dict[str, dict[str, float]] = {}
        self.defaut: dict[str, float] = {}

    def entrainer(self, exemples: list[tuple[list[str], str]]) -> "NaifBayes":
        comptes: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
        par_classe: collections.Counter = collections.Counter()
        for jetons, classe in exemples:
            par_classe[classe] += 1
            for j in set(jetons):
                comptes[classe][j] += self.idf.get(j, 1.0)
        total = sum(par_classe.values())
        self.classes = sorted(par_classe)
        v = max(1, len(self.vocabulaire))
        for classe in self.classes:
            self.log_prior[classe] = math.log(par_classe[classe] / total)
            masse = sum(comptes[classe].values())
            self.defaut[classe] = math.log(self.alpha / (masse + self.alpha * v))
            self.log_p[classe] = {t: math.log((n + self.alpha) / (masse + self.alpha * v))
                                  for t, n in comptes[classe].items()}
        return self

    def scores(self, jetons: list[str]) -> dict[str, float]:
        resultat = {}
        for classe in self.classes:
            s = self.log_prior[classe]
            table = self.log_p[classe]
            defaut = self.defaut[classe]
            for j in set(jetons):
                s += table.get(j, defaut)
            resultat[classe] = s
        return resultat

    def predire(self, jetons: list[str]) -> tuple[str, float]:
        scores = self.scores(jetons)
        classe = max(scores, key=scores.get)
        ordonne = sorted(scores.values(), reverse=True)
        marge = ordonne[0] - ordonne[1] if len(ordonne) > 1 else 0.0
        return classe, marge

    def exporter(self) -> dict:
        return {"classes": self.classes, "alpha": self.alpha,
                "logPrior": {c: round(v, 5) for c, v in self.log_prior.items()},
                "defaut": {c: round(v, 5) for c, v in self.defaut.items()},
                "logP": {c: {t: round(v, 4) for t, v in sorted(self.log_p[c].items())}
                         for c in self.classes}}


# ---------------------------------------------------------------------------------------------
# Construction du vocabulaire et des exemples
# ---------------------------------------------------------------------------------------------
def construire(seed_documents: list[list[str]], max_mots: int = 700, df_min: int = 2) -> dict:
    df: collections.Counter = collections.Counter()
    for doc in seed_documents:
        for j in set(doc):
            df[j] += 1
    gardes = [(t, n) for t, n in df.items() if n >= df_min]
    gardes.sort(key=lambda p: (-p[1], p[0]))
    gardes = gardes[:max_mots]
    vocabulaire = {t: i for i, (t, _) in enumerate(sorted(gardes))}
    n = len(seed_documents)
    idf = {t: round(math.log(n / (1 + df[t])) + 1.0, 4) for t in vocabulaire}
    return {"index": vocabulaire, "idf": idf, "documents": n}


def exemples_notion(jeu: list[dict], fragments: list[dict],
                    synthetiques: list[dict] = ()) -> tuple[list, list]:
    """Deux sources, et une mesure qui les distingue.

    Les 60 questions sont les exemples **de mesure** : c'est sur elles que la validation croisée est
    faite. Les 36 fragments du corpus sont les exemples **de soutien** : leur section donne la notion
    (grille 6 notions × 3 langages de l'étape 2), et ils apportent le vocabulaire du cours que les
    questions ne contiennent pas toujours. Ajoutés, ils font passer la validation croisée de 57 % à
    88 % — c'est le seul endroit où l'on utilise le corpus comme étiquettes, et il faut le dire.
    """
    questions = ([(mots(c["question"]), c["notion"]) for c in jeu]
                 + [(mots(c["question"]), c["notion"]) for c in synthetiques])
    soutien = [(mots(f["contenu"]), notion_de(f["section"]))
               for f in fragments if notion_de(f["section"])]
    return questions, soutien


def exemples_decision(jeu: list[dict], negatifs: dict,
                      synthetiques: list[dict] = ()) -> list[tuple[list[str], str]]:
    exemples = [(mots(c["question"]), "AUCUN") for c in jeu]
    exemples.extend((mots(c["question"]), "AUCUN") for c in synthetiques)
    for demande in negatifs["entrainement"]["horsPerimetre"]:
        exemples.append((mots(demande), "HORS_PERIMETRE"))
    for demande in negatifs["entrainement"]["solutionDemandee"]:
        exemples.append((mots(demande), "SOLUTION_DEMANDEE"))
    for demande in negatifs["entrainement"]["decisionDemandee"]:
        exemples.append((mots(demande), "DECISION_DEMANDEE"))
    # Les demandes du contrat de l'étape 1 sont, elles aussi, des exemples : le prompt et le modèle
    # apprennent ainsi le même vocabulaire de refus.
    correspondence = {"DECISION_DEMANDEE": "DECISION_DEMANDEE",
                      "SOLUTION_DEMANDEE": "SOLUTION_DEMANDEE",
                      "HORS_PERIMETRE": "HORS_PERIMETRE"}
    contrat = json.loads(CONTRAT.read_text(encoding="utf-8"))
    for cas in contrat["cas"]:
        motif = correspondence.get(cas["motif"])
        if motif:
            exemples.append((mots(cas["demande"]), motif))
    return exemples


def couverture(jetons: list[str], vocabulaire: dict[str, int], idf: dict[str, float]) -> float:
    """Part de la question que le modèle connaît, pondérée par la rareté des mots."""
    if not jetons:
        return 0.0
    connu = sum(idf.get(t, 0.0) for t in jetons)
    total = sum(idf.get(t, max(idf.values()) if idf else 1.0) for t in jetons)
    return connu / total if total else 0.0


# ---------------------------------------------------------------------------------------------
# Mesures
# ---------------------------------------------------------------------------------------------
def validation_croisee(exemples, alpha, vocabulaire, idf, soutien=(), poids=True) -> dict:
    """Laissé-un-dehors sur `exemples` : chacun est prédit par un modèle qui ne l'a jamais vu.

    `soutien` (les fragments du corpus) reste dans l'apprentissage à chaque tour : on mesure la
    capacité à classer une **question**, pas à mémoriser le cours.
    """
    justes = 0
    confusions: collections.Counter = collections.Counter()
    for i in range(len(exemples)):
        apprentissage = list(soutien) + exemples[:i] + exemples[i + 1:]
        modele = NaifBayes(alpha, vocabulaire, idf if poids else {}).entrainer(apprentissage)
        attendu = exemples[i][1]
        obtenu, _ = modele.predire(exemples[i][0])
        justes += obtenu == attendu
        confusions[(attendu, obtenu)] += 1
    return {"exactitude": justes / len(exemples), "justes": justes, "total": len(exemples),
            "confusions": confusions}


def critere_decision(resultat_cv) -> float:
    """Critère **asymétrique** : rater un refus coûte deux fois plus que refuser à tort.

    Un faux refus prive un apprenant d'une réponse légitime : c'est gênant, mais l'écran le dit et
    l'exercice continue. Un refus raté, lui, peut rendre une note ou une solution : c'est une
    violation du contrat de l'étape 1. Le critère est donc (2 × rappel des refus + rappel des
    questions légitimes) / 3, mesuré en validation croisée — jamais sur les négatifs de mesure.
    """
    confusions = resultat_cv["confusions"]
    total_refus = sum(n for (a, _), n in confusions.items() if a != "AUCUN")
    ok_refus = sum(n for (a, b), n in confusions.items() if a != "AUCUN" and b != "AUCUN")
    total_aucun = sum(n for (a, _), n in confusions.items() if a == "AUCUN")
    ok_aucun = confusions[("AUCUN", "AUCUN")]
    rappel_refus = ok_refus / total_refus if total_refus else 1.0
    rappel_aucun = ok_aucun / total_aucun if total_aucun else 1.0
    return {"critere": (2 * rappel_refus + rappel_aucun) / 3,
            "rappelRefus": rappel_refus, "rappelAucun": rappel_aucun,
            "refusJustes": ok_refus, "refusTotal": total_refus,
            "aucunJustes": ok_aucun, "aucunTotal": total_aucun}


def choisir_alpha(exemples, vocabulaire, idf, soutien=(), grille=(0.05, 0.1, 0.3, 1.0, 3.0)):
    resultats = {}
    for alpha in grille:
        resultats[alpha] = validation_croisee(exemples, alpha, vocabulaire, idf, soutien)["exactitude"]
    meilleur = max(resultats, key=lambda a: (resultats[a], -a))
    return meilleur, resultats


def rangement_lexical(fragments, jeu, vocabulaire, idf) -> dict:
    """Témoin : le rangement par recouvrement de mots, sans apprentissage (ce que fait ModeleSimule)."""
    def score(fragment, jetons):
        propres = set(mots(fragment["contenu"]))
        return sum(idf.get(t, 0.0) for t in set(jetons) if t in propres)

    justes = 0
    for cas in jeu:
        jetons = mots(cas["question"])
        candidats = [f for f in fragments if f["langage"] == (cas["langage"] or "python")]
        meilleur = max(candidats, key=lambda f: score(f, jetons))
        attendu = cas["section"]
        justes += meilleur["section"] == attendu
    return {"exactitude": justes / len(jeu), "justes": justes, "total": len(jeu)}


def score_prose(fragment: dict) -> int:
    """Un fragment de cours se lit en phrases ; un fragment d'exemple est un bloc de code."""
    phrases = re.split(r"(?<=[.!?])\s+", fragment["contenu"])
    return sum(1 for p in phrases if len(p.strip()) >= 25)


def choisir_fragment(fragments_du_section, jetons, idf) -> dict:
    def recouvrement(f):
        propres = set(mots(f["contenu"]))
        return sum(idf.get(t, 0.0) for t in set(jetons) if t in propres)

    return max(fragments_du_section, key=lambda f: (score_prose(f), recouvrement(f), f["contenu"]))


def extrait(contenu: str, jetons: list[str], idf: dict[str, float]) -> str:
    phrases = re.split(r"(?<=[.!?])\s+|\n\s*\n", contenu)
    retenu = contenu if len(contenu) <= 240 else contenu[:240] + "…"
    meilleur = -1.0
    for phrase in phrases:
        propre = re.sub(r"\s+", " ", phrase).strip()
        if len(propre) < 25:
            continue
        score = sum(idf.get(t, 0.0) for t in set(mots(propre)) if t in set(jetons))
        if score > meilleur:
            meilleur, retenu = score, propre
    return retenu


def evaluer_sections(modele_notion, fragments, table, cas, vocabulaire, idf) -> dict:
    """Section trouvée (et témoin attendu présent) — le vrai critère du corpus de l'étape 2."""
    sections_justes = 0
    temoins = 0
    detail = []
    for c in cas:
        langage = c["langage"] or "python"
        notion, _ = modele_notion.predire(mots(c["question"]))
        section = table.get(langage, {}).get(notion)
        if section is None:
            detail.append((c["id"], "—", False, False))
            continue
        fragment = choisir_fragment([f for f in fragments
                                     if f["section"] == section], mots(c["question"]), idf)
        juste = section == c["section"]
        temoin = c["attendu"].lower() in fragment["contenu"].lower()
        sections_justes += juste
        temoins += temoin
        detail.append((c["id"], section, juste, temoin))
    return {"sections": sections_justes, "temoins": temoins, "total": len(cas), "detail": detail}


def evaluer_notion(modele_notion, cas) -> dict:
    justes = 0
    confusions: collections.Counter = collections.Counter()
    for exemple in cas:
        attendu = exemple["notion"]
        obtenu, _ = modele_notion.predire(mots(exemple["question"]))
        justes += obtenu == attendu
        confusions[(attendu, obtenu)] += 1
    return {"justes": justes, "total": len(cas), "exactitude": justes / len(cas),
            "confusions": [[f"{a}→{b}", n] for (a, b), n in sorted(confusions.items())
                           if a != b]}


def evaluer_decision(modele_decision, cas_legitimes, evaluation, seuil) -> dict:
    refus_ok = 0
    refus_total = 0
    par_motif = collections.Counter()
    for motif, demandes in (("HORS_PERIMETRE", evaluation["horsPerimetre"]),
                            ("SOLUTION_DEMANDEE", evaluation["solutionDemandee"]),
                            ("DECISION_DEMANDEE", evaluation["decisionDemandee"])):
        for demande in demandes:
            jetons = mots(demande)
            classe, _ = modele_decision.predire(jetons)
            refus_total += 1
            if classe != "AUCUN":
                refus_ok += 1
            par_motif[(motif, classe)] += 1
    faux_refus = 0
    legitimes = list(evaluation["legitimes"]) + cas_legitimes
    for demande in legitimes:
        classe, _ = modele_decision.predire(mots(demande))
        if classe != "AUCUN":
            faux_refus += 1
    return {"refus_ok": refus_ok, "refus_total": refus_total,
            "faux_refus": faux_refus, "legitimes": len(legitimes),
            "par_motif": par_motif}


# ---------------------------------------------------------------------------------------------
# Entraînement complet
# ---------------------------------------------------------------------------------------------
def entrainer() -> tuple[dict, dict, dict]:
    fragments = lire_corpus()
    jeu = lire_jeu()
    synthetiques = lire_questions_synthetiques(QUESTIONS_SYNTHETIQUES)
    evaluation_synthetique = lire_questions_synthetiques(EVALUATION_SYNTHETIQUE)
    questions_entrainement = {
        " ".join(c["question"].casefold().split()) for c in synthetiques
    }
    questions_evaluation = {
        " ".join(c["question"].casefold().split()) for c in evaluation_synthetique
    }
    if questions_entrainement & questions_evaluation:
        raise ValueError("Une question synthétique apparaît à l'entraînement et à l'évaluation.")
    negatifs = json.loads(NEGATIFS.read_text(encoding="utf-8"))
    table = table_sections(fragments)

    documents = ([[mots(f["contenu"])] [0] for f in fragments]
                 + [mots(c["question"]) for c in jeu]
                 + [mots(c["question"]) for c in synthetiques])
    for groupe in negatifs["entrainement"].values():
        documents += [mots(d) for d in groupe]
    vocabulaire = construire(documents)
    idf = vocabulaire["idf"]

    # --- têtes ---
    ex_notion, soutien = exemples_notion(jeu, fragments, synthetiques)
    ex_decision = exemples_decision(jeu, negatifs, synthetiques)
    V = vocabulaire["index"]

    # Réglage : deux leviers, mesurés séparément (le rapport publie les quatre combinaisons).
    variantes = {}
    for nom, soutien_test, poids in (("sansCorpusSansIdf", (), False),
                                     ("sansCorpusAvecIdf", (), True),
                                     ("avecCorpusSansIdf", tuple(soutien), False),
                                     ("avecCorpusAvecIdf", tuple(soutien), True)):
        variantes[nom] = {
            "alpha": {a: round(validation_croisee(ex_notion, a, V, idf, soutien_test, poids)["exactitude"], 3)
                      for a in (0.05, 0.3, 1.0)},
        }
        variantes[nom]["meilleur"] = max(variantes[nom]["alpha"].values())

    alpha_notion, grille_notion = choisir_alpha(ex_notion, V, idf, tuple(soutien))
    cv_notion = validation_croisee(ex_notion, alpha_notion, V, idf, tuple(soutien))

    # Tête « décision » : pas de pondération idf. Mesuré — pondérée, elle gagne un refus mais
    # refuse à tort deux questions légitimes ; le critère asymétrique tranche pour la version nue.
    grille_decision, grille_decisions = {}, {}
    for poids in (False, True):
        for alpha in (0.05, 0.1, 0.3, 1.0, 3.0):
            cv = validation_croisee(ex_decision, alpha, V, idf, (), poids)
            grille_decisions[(poids, alpha)] = critere_decision(cv)["critere"]
    poids_decision = max(
        {p for p, _ in grille_decisions},
        key=lambda p: max(v for (pp, a), v in grille_decisions.items() if pp == p))
    alpha_decision = max(
        (a for (pp, a) in grille_decisions if pp == poids_decision),
        key=lambda a: grille_decisions[(poids_decision, a)])
    grille_decision = {f"{'idf' if pp else 'nu'} α={a}": round(v, 3)
                       for (pp, a), v in sorted(grille_decisions.items())}
    cv_decision = validation_croisee(ex_decision, alpha_decision, V, idf, (), poids_decision)

    modele_notion = NaifBayes(alpha_notion, V, idf).entrainer(list(soutien) + ex_notion)
    modele_decision = NaifBayes(alpha_decision, V, idf if poids_decision else {}).entrainer(ex_decision)
    evaluation_notion_synthetique = evaluer_notion(modele_notion, evaluation_synthetique)

    # --- seuil de couverture : la seule porte qui ne dépend pas de l'apprentissage ---
    # Mesuré sur les questions du jeu (la plus basse vaut 0,31) et sur les négatifs d'entraînement :
    # le seuil est posé à 0,10, très en dessous de tout ce qui est légitime. Il n'attrape que les
    # demandes dont aucun mot n'appartient au domaine — il ne remplace pas la tête de décision.
    seuil = 0.10
    couv_jeu = [couverture(mots(c["question"]), V, idf) for c in jeu]
    couv_neg = [couverture(mots(d), V, idf)
                for g in negatifs["evaluation"].values() for d in g]

    # --- mesures ---
    lexical = rangement_lexical(fragments, jeu, vocabulaire["index"], idf)
    modele_sections = evaluer_sections(modele_notion, fragments, table, jeu,
                                       vocabulaire["index"], idf)
    legitimes_synthetiques = [c["question"] for c in evaluation_synthetique]
    decision_eval = evaluer_decision(modele_decision, legitimes_synthetiques,
                                    negatifs["evaluation"], seuil)

    mesures = {
        "vocabulaire": len(vocabulaire["index"]),
        "soutien": len(soutien),
        "variantes": variantes,
        "couverture": {"jeuMin": round(min(couv_jeu), 3), "evaluationMin": round(min(couv_neg), 3),
                       "sousSeuilEval": sum(1 for c in couv_neg if c < seuil),
                       "sousSeuilJeu": sum(1 for c in couv_jeu if c < seuil)},
        "notion": {"alpha": alpha_notion, "grille": grille_notion,
                   "cvExactitude": cv_notion["exactitude"], "cvJustes": cv_notion["justes"],
                   "cvTotal": cv_notion["total"],
                   "evaluationSynthetique": evaluation_notion_synthetique,
                   "confusions": [[f"{a}→{b}", n] for (a, b), n in sorted(cv_notion["confusions"].items())
                                  if a != b]},
        "lexical": lexical,
        "sections": {"justes": modele_sections["sections"], "temoins": modele_sections["temoins"],
                     "total": modele_sections["total"]},
        "decision": {"alpha": alpha_decision, "grille": grille_decision,
                     "ponderation": "idf" if poids_decision else "aucune",
                     **critere_decision(cv_decision),
                     "cvExactitude": cv_decision["exactitude"], "cvJustes": cv_decision["justes"],
                     "cvTotal": cv_decision["total"],
                     "refusOk": decision_eval["refus_ok"], "refusTotal": decision_eval["refus_total"],
                     "fauxRefus": decision_eval["faux_refus"],
                     "legitimes": decision_eval["legitimes"],
                     "detail": [[f"{m}→{c}", n] for (m, c), n in sorted(decision_eval["par_motif"].items())],
                     "seuilCouverture": seuil},
    }

    modele = {
        "version": VERSION,
        "dateEntrainement": "2026-10-28",
        "tokeniseur": {"version": TOKENISEUR, "familles": dict(sorted(FAMILLES.items())),
                       "motsVides": sorted(MOTS_VIDES)},
        "vocabulaire": {t: i for t, i in sorted(vocabulaire["index"].items())},
        "idf": dict(sorted(idf.items())),
        "sections": table,
        "notions": NOTIONS,
        "notion": {**modele_notion.exporter(), "ponderation": "idf",
                   "soutienCorpus": len(soutien)},
        "decision": {**modele_decision.exporter(),
                     "ponderation": "idf" if poids_decision else "aucune"},
        "seuil": {"couvertureMin": seuil,
                  "margeMin": round(min(m for m in [
                      modele_decision.predire(jetons)[1] for jetons in
                      [mots(c["question"]) for c in jeu]]), 3)},
        "sources": {
            "jeu": hashlib.sha256(JEU.read_bytes()).hexdigest()[:16],
            "corpus": hashlib.sha256(CORPUS.read_text(encoding="utf-8").encode()).hexdigest()[:16],
            "negatifs": hashlib.sha256(NEGATIFS.read_bytes()).hexdigest()[:16],
            "contrat": hashlib.sha256(CONTRAT.read_bytes()).hexdigest()[:16],
            "questionsSynthetiques": hashlib.sha256(
                QUESTIONS_SYNTHETIQUES.read_bytes()).hexdigest()[:16],
            "evaluationSynthetique": hashlib.sha256(
                EVALUATION_SYNTHETIQUE.read_bytes()).hexdigest()[:16],
            "questionsEntrainement": len(jeu),
            "questionsSynthetiquesEntrainement": len(synthetiques),
            "questionsSynthetiquesEvaluation": len(evaluation_synthetique),
            "negatifsEntrainement": sum(len(g) for g in negatifs["entrainement"].values()),
        },
    }
    return modele, mesures, modele_sections


def rapport(modele: dict, mesures: dict) -> str:
    n = mesures["notion"]
    d = mesures["decision"]
    s = mesures["sections"]
    lex = mesures["lexical"]
    refus_pct = 100 * d["refusOk"] / max(1, d["refusTotal"])
    faux_pct = 100 * d["fauxRefus"] / max(1, d["legitimes"])
    confusions = ", ".join(f"`{c}` ×{k}" for c, k in n["confusions"]) or "aucune"
    detail = " · ".join(f"`{m}` ×{k}" for m, k in d["detail"]) or "—"
    return f"""# Modèle local — mesures

Généré par `ia/modele/entrainer.py`. Ne pas éditer à la main : régénérer.

| | |
|---|---|
| Modèle | `{modele['version']}` · entraîné le {modele['dateEntrainement']} |
| Vocabulaire | {mesures['vocabulaire']} termes |
| Données d'entraînement | {modele['sources']['questionsEntrainement']} questions du jeu gelé + {modele['sources']['questionsSynthetiquesEntrainement']} questions synthétiques annotées + {modele['sources']['negatifsEntrainement']} négatifs et exemples du contrat de refus |
| Empreintes | jeu `{modele['sources']['jeu']}` · corpus `{modele['sources']['corpus']}` · négatifs `{modele['sources']['negatifs']}` · contrat `{modele['sources']['contrat']}` · synthèse `{modele['sources']['questionsSynthetiques']}` · test `{modele['sources']['evaluationSynthetique']}` |

## Tête « notion » — 6 classes

Validation croisée laissée-un-dehors : **{n['cvJustes']}/{n['cvTotal']} = {100 * n['cvExactitude']:.0f} %**.
α = {n['alpha']} (grille : {", ".join(f"{a} → {100 * v:.0f} %" for a, v in sorted(n['grille'].items()))}).

Confusions hors diagonale : {confusions}.

Évaluation indépendante sur les questions synthétiques réservées (jamais utilisées à l'entraînement ni au réglage) :
**{n['evaluationSynthetique']['justes']}/{n['evaluationSynthetique']['total']} = {100 * n['evaluationSynthetique']['exactitude']:.0f} %**.
Ce jeu est synthétique et de petite taille ; il sert de contrôle initial, pas de mesure de performance sur de vrais apprenants.

## Rangement du fragment, et témoin attendu

| Rangement | Section juste | Témoin attendu présent dans le fragment choisi |
|---|---|---|
| Lexical (sans apprentissage) | **{lex['justes']}/{lex['total']} = {100 * lex['exactitude']:.0f} %** | — |
| Modèle local (notion apprise) | **{s['justes']}/{s['total']} = {100 * s['justes'] / s['total']:.0f} %** (appris sur ces questions) | **{s['temoins']}/{s['total']} = {100 * s['temoins'] / s['total']:.0f} %** |

La colonne « modèle local » est **en apprentissage** : ces questions ont servi à l'entraîner, c'est donc
une borne haute. Le chiffre honnête de la tête notion est la validation croisée ci-dessus
({100 * n['cvExactitude']:.0f} %). Le rangement lexical, lui, n'apprend rien : ses {100 * lex['exactitude']:.0f} %
sont comparables à ceux de l'étape 2 — même protocole, autre implémentation.

## Tête « décision » — répondre ou refuser

Validation croisée laissée-un-dehors : **{d['cvJustes']}/{d['cvTotal']} = {100 * d['cvExactitude']:.0f} %**
(α = {d['alpha']}).

Mesure sur les négatifs **de mesure**, jamais vus à l'entraînement :

| | Résultat |
|---|---|
| Demandes de refus détectées comme refus | **{d['refusOk']}/{d['refusTotal']} = {refus_pct:.0f} %** |
| Questions légitimes refusées à tort | **{d['fauxRefus']}/{d['legitimes']} = {faux_pct:.0f} %** |
| Seuil de couverture | {d['seuilCouverture']} (part pondérée des mots de la question connus du modèle) |

Détail des réponses (motif attendu → classe prédite) : {detail}.

## Ce que le modèle ne fait pas

1. **Il ne rédige pas.** La réponse reste extractive : elle cite une phrase du cours retrouvé. Un
   modèle génératif local (LLM) n'est pas embarqué — la décision de l'étape 4 (RAG sur API) le
   réservait au fournisseur distant ; ici, l'apprentissage porte sur le **classement** et la
   **décision**, les deux endroits où un petit modèle supervisé est meilleur qu'une règle écrite.
2. **Il ne couvre pas les refus contextuels.** `TEST_EN_COURS`, `QUOTA`, `INDISPONIBLE`,
   `PUBLICATION_AUTOMATIQUE` et `DONNEES_INSUFFISANTES` dépendent de l'état de la session, pas des
   mots de la question : ce sont les garde-fous et la politique de repli qui les décident, pas ce
   modèle.
3. **Il ne note pas**, ne valide pas, ne débloque pas — le contrat de l'étape 1 reste la loi.
"""


def main() -> int:
    p = argparse.ArgumentParser()
    p.add_argument("--verifier", action="store_true")
    args = p.parse_args()

    modele, mesures, _ = entrainer()
    modele_txt = json.dumps(modele, ensure_ascii=False, indent=2, sort_keys=False) + "\n"
    tokens_txt = json.dumps(
        {"version": TOKENISEUR,
         "phrases": [{"phrase": p, "jetons": mots(p)} for p in PHRASES_REFERENCE]},
        ensure_ascii=False, indent=2) + "\n"
    rapport_txt = rapport(modele, mesures)
    mesures_txt = json.dumps({k: v for k, v in mesures.items()}, ensure_ascii=False, indent=2) + "\n"

    if args.verifier:
        ecarts = []
        for chemin, attendu in ((MODELE, modele_txt), (RAPPORT, rapport_txt),
                                (MESURES, mesures_txt), (TOKENS, tokens_txt)):
            if not chemin.exists() or chemin.read_text(encoding="utf-8") != attendu:
                ecarts.append(chemin.name)
        for nom in ecarts:
            print(f"  DÉRIVE : {nom} n'est pas à jour")
        if not ecarts:
            print(f"{VERSION} à jour · {len(modele['vocabulaire'])} termes · "
                  f"notion {100 * mesures['notion']['cvExactitude']:.0f} % (CV) · "
                  f"refus {mesures['decision']['refusOk']}/{mesures['decision']['refusTotal']} · "
                  f"faux refus {mesures['decision']['fauxRefus']}/{mesures['decision']['legitimes']}")
        return 1 if ecarts else 0

    MODELE.write_text(modele_txt, encoding="utf-8")
    TOKENS.write_text(tokens_txt, encoding="utf-8")
    RAPPORT.write_text(rapport_txt, encoding="utf-8")
    MESURES.write_text(mesures_txt, encoding="utf-8")
    print(f"{MODELE.name} écrit ({len(modele_txt) / 1024:.0f} Ko, {len(modele['vocabulaire'])} termes)")
    print(f"  notion    : CV {mesures['notion']['cvJustes']}/{mesures['notion']['cvTotal']}"
          f" ({100 * mesures['notion']['cvExactitude']:.0f} %), alpha={mesures['notion']['alpha']}")
    print(f"  rangement : lexical {mesures['lexical']['justes']}/{mesures['lexical']['total']}"
          f" · modèle {mesures['sections']['justes']}/{mesures['sections']['total']}"
          f" · témoins {mesures['sections']['temoins']}/{mesures['sections']['total']}")
    print(f"  décision  : CV {mesures['decision']['cvJustes']}/{mesures['decision']['cvTotal']}"
          f" · refus {mesures['decision']['refusOk']}/{mesures['decision']['refusTotal']}"
          f" · faux refus {mesures['decision']['fauxRefus']}/{mesures['decision']['legitimes']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
