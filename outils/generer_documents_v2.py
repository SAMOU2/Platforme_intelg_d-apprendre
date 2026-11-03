#!/usr/bin/env python3
"""Génère les livrables documentaires v2.0 du projet IHM 2026.

Dépendances (environnement de génération uniquement) :
    python -m pip install reportlab pillow pypdf

Entrées : docs/sources-v2/*.md
Sorties : PDF dans docs/ et PNG dans docs/preuves/diagrammes-v2/.
Les PNG sont des schémas de conception générés, pas des captures JavaFX.
"""
from __future__ import annotations

import html
import re
import sys
import textwrap
from pathlib import Path

try:
    from PIL import Image, ImageDraw, ImageFont
except ImportError as exc:  # pragma: no cover
    raise SystemExit("Pillow est requis : python -m pip install pillow") from exc

try:
    from reportlab.lib import colors
    from reportlab.lib.enums import TA_CENTER, TA_LEFT
    from reportlab.lib.pagesizes import A4
    from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
    from reportlab.lib.units import mm
    from reportlab.pdfbase import pdfmetrics
    from reportlab.pdfbase.ttfonts import TTFont
    from reportlab.platypus import (
        Image as RLImage,
        KeepTogether,
        PageBreak,
        Paragraph,
        Preformatted,
        SimpleDocTemplate,
        Spacer,
        Table,
        TableStyle,
    )
except ImportError as exc:  # pragma: no cover
    raise SystemExit("ReportLab est requis : python -m pip install reportlab") from exc

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "docs" / "sources-v2"
IMG_DIR = ROOT / "docs" / "preuves" / "diagrammes-v2"
FONT_DIR = Path("/usr/share/fonts/truetype/dejavu")
FONT_REG = FONT_DIR / "DejaVuSans.ttf"
FONT_BOLD = FONT_DIR / "DejaVuSans-Bold.ttf"
FONT_MONO = FONT_DIR / "DejaVuSansMono.ttf"

NAVY = (31, 38, 58)
BLUE = (37, 99, 235)
TEAL = (8, 127, 156)
GREEN = (35, 140, 70)
ORANGE = (217, 119, 6)
RED = (205, 45, 45)
PURPLE = (112, 78, 190)
GREY = (91, 101, 117)
LIGHT = (244, 246, 250)
BORDER = (180, 188, 203)
DARK = (28, 34, 44)


# ---------------------------------------------------------------------------
# Diagrammes PNG (Pillow)
# ---------------------------------------------------------------------------

def pil_font(size: int, bold: bool = False):
    path = FONT_BOLD if bold and FONT_BOLD.exists() else FONT_REG
    if path.exists():
        return ImageFont.truetype(str(path), size)
    return ImageFont.load_default()


def wrap(draw: ImageDraw.ImageDraw, text: str, font, max_width: int) -> list[str]:
    lines: list[str] = []
    for paragraph in text.split("\n"):
        words = paragraph.split()
        if not words:
            lines.append("")
            continue
        current = words[0]
        for word in words[1:]:
            candidate = current + " " + word
            if draw.textlength(candidate, font=font) <= max_width:
                current = candidate
            else:
                lines.append(current)
                current = word
        lines.append(current)
    return lines


def canvas(title: str, subtitle: str, size=(1900, 1320)):
    img = Image.new("RGB", size, (248, 250, 252))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((24, 20, size[0] - 24, 112), radius=24, fill=NAVY)
    d.text((52, 66), title, font=pil_font(38, True), fill=(255, 255, 255), anchor="lm")
    d.text((size[0] - 52, 66), subtitle, font=pil_font(21, True), fill=(255, 214, 102), anchor="rm")
    return img, d


def box(d, xy, text, fill=(255, 255, 255), outline=BORDER, size=25, bold=False,
        radius=16, width=3, color=DARK, align="center"):
    x1, y1, x2, y2 = xy
    d.rounded_rectangle(xy, radius=radius, fill=fill, outline=outline, width=width)
    font = pil_font(size, bold)
    lines = wrap(d, text, font, max(60, x2 - x1 - 28))
    line_h = int(size * 1.22)
    total = line_h * len(lines)
    y = y1 + (y2 - y1 - total) / 2 + line_h / 2
    for line in lines:
        if align == "left":
            d.text((x1 + 16, y), line, font=font, fill=color, anchor="lm")
        else:
            d.text(((x1 + x2) / 2, y), line, font=font, fill=color, anchor="mm")
        y += line_h
    return xy


def arrow(d, start, end, label="", color=GREY, width=4, label_size=19, both=False, dy=-12, halo=True):
    x1, y1 = start
    x2, y2 = end
    d.line((x1, y1, x2, y2), fill=color, width=width)
    # Small triangular head, oriented for the dominant direction.
    if abs(x2 - x1) >= abs(y2 - y1):
        s = 1 if x2 >= x1 else -1
        d.polygon([(x2, y2), (x2 - 18 * s, y2 - 9), (x2 - 18 * s, y2 + 9)], fill=color)
        if both:
            d.polygon([(x1, y1), (x1 + 18 * s, y1 - 9), (x1 + 18 * s, y1 + 9)], fill=color)
    else:
        s = 1 if y2 >= y1 else -1
        d.polygon([(x2, y2), (x2 - 9, y2 - 18 * s), (x2 + 9, y2 - 18 * s)], fill=color)
        if both:
            d.polygon([(x1, y1), (x1 - 9, y1 + 18 * s), (x1 + 9, y1 + 18 * s)], fill=color)
    if label:
        mx, my = (x1 + x2) / 2, (y1 + y2) / 2 + dy
        font = pil_font(label_size, False)
        lines = wrap(d, label, font, 330)
        # Optional white halo behind label for readability over lines.
        text_w = max(d.textlength(line, font=font) for line in lines)
        text_h = len(lines) * int(label_size * 1.2)
        if halo:
            d.rounded_rectangle((mx - text_w / 2 - 8, my - text_h / 2 - 4, mx + text_w / 2 + 8, my + text_h / 2 + 4),
                                radius=8, fill=(248, 250, 252))
        yy = my - text_h / 2 + int(label_size * 0.6)
        for line in lines:
            d.text((mx, yy), line, font=font, fill=color, anchor="mm")
            yy += int(label_size * 1.2)


def label_at(d, pos, text, color=GREY, size=19):
    x, y = pos
    font = pil_font(size, False)
    lines = wrap(d, text, font, 330)
    text_w = max(d.textlength(line, font=font) for line in lines)
    text_h = len(lines) * int(size * 1.2)
    d.rounded_rectangle((x - text_w / 2 - 8, y - text_h / 2 - 4, x + text_w / 2 + 8, y + text_h / 2 + 4),
                        radius=8, fill=(248, 250, 252))
    yy = y - text_h / 2 + int(size * 0.6)
    for line in lines:
        d.text((x, yy), line, font=font, fill=color, anchor="mm")
        yy += int(size * 1.2)


def poly_arrow(d, points, label="", color=GREY, width=4, label_pos=None, label_size=19):
    if len(points) > 2:
        d.line(points[:-1], fill=color, width=width, joint="curve")
    arrow(d, points[-2], points[-1], color=color, width=width)
    if label:
        pos = label_pos or points[len(points) // 2]
        label_at(d, pos, label, color=color, size=label_size)


def save(img: Image.Image, name: str):
    IMG_DIR.mkdir(parents=True, exist_ok=True)
    path = IMG_DIR / name
    img.save(path, optimize=True)
    return path


def diagram_use_cases():
    img, d = canvas("Diagramme de cas d’utilisation", "UML · périmètre livré v2.0")
    W, H = img.size
    # System boundary
    d.rounded_rectangle((390, 145, 1510, 1240), radius=26, fill=(255, 255, 255), outline=NAVY, width=4)
    d.text((950, 180), "Plateforme d’apprentissage par niveaux", font=pil_font(30, True), fill=NAVY, anchor="mm")
    actors = {
        "apprenant": (55, 250, 315, 360),
        "enseignant": (55, 760, 315, 870),
        "admin": (1585, 250, 1845, 360),
        "externes": (55, 980, 315, 1120),
    }
    box(d, actors["apprenant"], "Apprenant", fill=(232, 244, 255), outline=BLUE, size=30, bold=True)
    box(d, actors["enseignant"], "Enseignant", fill=(235, 248, 238), outline=GREEN, size=30, bold=True)
    box(d, actors["admin"], "Administrateur", fill=(255, 241, 214), outline=ORANGE, size=28, bold=True)
    box(d, actors["externes"], "SMTP · NVIDIA\nservices externes", fill=(244, 238, 255), outline=PURPLE, size=24, bold=True)
    cases_left = [
        "Demander un compte\npuis activer le PIN",
        "Se connecter\nOTP ou poste 48 h",
        "Choisir un langage\net suivre le parcours",
        "Étudier le cours\ns’entraîner avec IA",
        "Passer le test final\nsans IA",
        "Consulter note,\ndécision et progression",
    ]
    cases_right = [
        "Créer et publier\nniveaux/contenus",
        "Consulter les copies\net corriger",
        "Publier note\net décision",
        "Filtrer et décider\nles inscriptions",
        "Gérer comptes,\nPIN et affectations",
        "Exploiter PC1\net diagnostiquer",
    ]
    y = 225
    left_boxes = []
    right_boxes = []
    for label in cases_left:
        b = (440, y, 900, y + 132)
        box(d, b, label, outline=BLUE, size=24, bold=True, radius=52)
        left_boxes.append(b)
        y += 163
    y = 225
    for label in cases_right:
        b = (1000, y, 1460, y + 132)
        box(d, b, label, outline=TEAL, size=24, bold=True, radius=52)
        right_boxes.append(b)
        y += 163
    for b in left_boxes:
        arrow(d, (315, 305), (b[0], (b[1] + b[3]) // 2), color=BLUE, width=3)
    # L’enseignant demande et active lui aussi un compte avant ses cas métier.
    for b in left_boxes[:2]:
        arrow(d, (315, 815), (b[0], (b[1] + b[3]) // 2), color=GREEN, width=3)
    for b in right_boxes[:3]:
        arrow(d, (315, 815), (b[0], (b[1] + b[3]) // 2), color=GREEN, width=3)
    for b in right_boxes[3:]:
        arrow(d, (1585, 305), (b[2], (b[1] + b[3]) // 2), color=ORANGE, width=3)
    # SMTP intervient pour PIN/OTP ; NVIDIA uniquement pour l’entraînement.
    for idx in (0, 1, 3):
        b = left_boxes[idx]
        arrow(d, (315, 1050), (b[0], (b[1] + b[3]) // 2), color=PURPLE, width=3)
    label_at(d, (382, 920), "via PC1", color=PURPLE, size=17)
    d.text((950, 1280), "Le rôle est toujours revérifié par PC1 ; le lanceur du poste ne constitue pas une autorisation.",
           font=pil_font(22, True), fill=RED, anchor="mm")
    return save(img, "01-cas-utilisation.png")


def entity(d, xy, title, fields, color):
    x1, y1, x2, y2 = xy
    d.rounded_rectangle(xy, radius=14, fill=(255, 255, 255), outline=color, width=3)
    d.rounded_rectangle((x1, y1, x2, y1 + 52), radius=14, fill=color)
    d.rectangle((x1, y1 + 35, x2, y1 + 52), fill=color)
    title_size = 23
    while title_size > 15 and d.textlength(title, font=pil_font(title_size, True)) > (x2 - x1 - 18):
        title_size -= 1
    d.text(((x1 + x2) / 2, y1 + 27), title, font=pil_font(title_size, True), fill=(255, 255, 255), anchor="mm")
    y = y1 + 82
    for field in fields:
        d.text((x1 + 16, y), field, font=pil_font(19), fill=DARK, anchor="lm")
        y += 30


def center(b):
    return ((b[0] + b[2]) // 2, (b[1] + b[3]) // 2)


def diagram_data_identity():
    img, d = canvas("Modèle de données — identité et administration", "PostgreSQL · migrations 01, 07–09, 13–14")
    boxes = {
        "utilisateur": (650, 180, 1250, 430),
        "session": (90, 180, 560, 430),
        "tentative_auth": (1340, 180, 1810, 430),
        "code": (90, 540, 560, 790),
        "confiance": (650, 540, 1250, 790),
        "invitation": (1340, 540, 1810, 790),
        "affectation": (370, 900, 900, 1150),
        "audit": (1010, 900, 1540, 1150),
    }
    # Les connecteurs sont tracés après les entités, avec des points de bord
    # explicites, pour éviter qu’un trait centre-à-centre traverse une autre table.
    entity(d, boxes["utilisateur"], "utilisateurs", ["id PK", "nom, email unique", "mot_de_passe bcrypt", "role, actif", "statut_inscription"], NAVY)
    entity(d, boxes["session"], "sessions", ["id UUID PK", "utilisateur_id FK", "empreinte_jeton SHA-256", "derniere_activite", "expiration_max (8 h)"], BLUE)
    entity(d, boxes["tentative_auth"], "tentatives_authentification", ["email_normalise PK", "empreinte_source PK", "debut_fenetre", "echecs (max. 5 / 15 min)"], RED)
    entity(d, boxes["code"], "codes_connexion", ["identifiant UUID PK", "utilisateur_id FK", "empreinte_code", "expire_le (10 min)", "echecs"], TEAL)
    entity(d, boxes["confiance"], "appareils_confiance", ["id UUID PK", "utilisateur_id FK", "empreinte_jeton SHA-256", "expire_le absolu (48 h)", "revoque_le"], PURPLE)
    entity(d, boxes["invitation"], "invitations_inscription", ["id UUID PK", "email_cible, role_cible", "empreinte_pin bcrypt", "expire_le (48 h)", "echecs, utilise_le"], ORANGE)
    entity(d, boxes["affectation"], "affectations_pedagogiques", ["apprenant_id PK/FK", "enseignant_id FK", "affecte_par FK", "maj_le"], GREEN)
    entity(d, boxes["audit"], "journal_audit", ["id PK", "acteur_id FK", "action, cible", "details JSONB", "cree_le"], GREY)
    # Toutes les relations partent de `utilisateurs` et restent dans les couloirs libres.
    arrow(d, (650, 305), (560, 305), "1 — 0..n", color=GREY, width=3)
    arrow(d, (760, 430), (450, 540), "1 — 0..n", color=GREY, width=3)
    arrow(d, (950, 430), (950, 540), "1 — 0..n", color=GREY, width=3)
    poly_arrow(d, [(700, 430), (635, 485), (635, 900)], "apprenant / enseignant", color=GREY, width=3,
               label_pos=(635, 850), label_size=17)
    poly_arrow(d, [(1250, 365), (1295, 365), (1295, 850), (1275, 850), (1275, 900)], "acteur 1 — 0..n",
               color=GREY, width=3, label_pos=(1295, 850), label_size=17)
    d.text((950, 1275), "Aucun mot de passe, OTP, PIN ou jeton n’est conservé en clair côté serveur.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    return save(img, "02-donnees-identite.png")


def diagram_data_pedagogy():
    img, d = canvas("Modèle de données — pédagogie et progression", "PostgreSQL · migrations 01, 10–12")
    boxes = {
        "langage": (80, 170, 520, 400),
        "parcours": (610, 170, 1050, 400),
        "niveau": (1140, 170, 1580, 430),
        "enseignant_parcours": (1610, 170, 1840, 430),
        "cours": (80, 520, 520, 750),
        "entrainement": (610, 520, 1050, 750),
        "test": (1140, 520, 1580, 770),
        "tentative": (610, 870, 1120, 1170),
        "progression": (1210, 870, 1740, 1170),
        "utilisateur": (80, 870, 520, 1170),
    }
    # Connecteurs bord-à-bord ajoutés après les entités (voir fin de fonction).
    entity(d, boxes["langage"], "langages", ["id PK", "code unique", "libelle, version affichee"], TEAL)
    entity(d, boxes["parcours"], "parcours", ["id PK", "langage_id FK", "libelle, actif"], BLUE)
    entity(d, boxes["niveau"], "niveaux", ["id PK, parcours_id FK", "rang, titre, seuil", "tentatives_max, periode", "statut, cree_par"], NAVY)
    entity(d, boxes["enseignant_parcours"], "parcours_enseignants", ["enseignant_id", "langage_id", "parcours_id"], GREEN)
    entity(d, boxes["cours"], "cours + blocs", ["cours : niveau_id, statut", "blocs : position, type", "langage du code, contenu"], PURPLE)
    entity(d, boxes["entrainement"], "entrainements_niveaux", ["niveau_id FK", "enonce", "code_depart", "publie, cree_par"], ORANGE)
    entity(d, boxes["test"], "tests + questions", ["test : niveau_id unique", "questions actives", "propositions QCM", "sujet photographié en copie"], RED)
    entity(d, boxes["utilisateur"], "utilisateurs", ["id PK", "role unique", "actif / statut", "affectation via table dédiée"], GREY)
    entity(d, boxes["tentative"], "tentatives (copies)", ["apprenant_id, enseignant_id, test_id", "statut EN_COURS/SOUMISE/CORRIGEE", "snapshots sujet + réponses + code", "note, brouillon, publie_le, seuil"], BLUE)
    entity(d, boxes["progression"], "progressions", ["apprenant_id + niveau_id uniques", "etat VERROUILLE/EN_COURS/VALIDE", "meilleure_note", "valide_le, maj_le"], GREEN)
    arrow(d, (520, 285), (610, 285), "1 — n", color=GREY, width=3, label_size=17)
    arrow(d, (1050, 285), (1140, 285), "1 — n", color=GREY, width=3, label_size=17)
    # `parcours_enseignants` relie l’auteur au parcours (pas directement au niveau).
    poly_arrow(d, [(830, 170), (830, 150), (1725, 150), (1725, 170)], "auteur du parcours",
               color=GREY, width=3, label_pos=(1275, 150), label_size=16)
    arrow(d, (1250, 430), (300, 520), "1 — n", color=GREY, width=3, label_size=17)
    arrow(d, (1360, 430), (830, 520), "1 — n", color=GREY, width=3, label_size=17)
    arrow(d, (1470, 430), (1470, 520), "1 — 1", color=GREY, width=3, label_size=17)
    arrow(d, (1250, 770), (865, 870), "1 — n", color=GREY, width=3, label_size=17)
    arrow(d, (520, 1020), (610, 1020), color=GREY, width=3)
    label_at(d, (565, 985), "apprenant /\ncorrecteur", color=GREY, size=15)
    # La progression référence directement le niveau : contournement par le couloir droit.
    poly_arrow(d, [(1580, 350), (1596, 350), (1596, 820), (1500, 820), (1500, 870)], "niveau 1 — n",
               color=GREY, width=3, label_pos=(1548, 820), label_size=16)
    # Et directement l’apprenant : couloir sous les copies.
    poly_arrow(d, [(300, 1170), (300, 1208), (1475, 1208), (1475, 1170)], "apprenant 1 — n",
               color=GREY, width=3, label_pos=(887, 1208), label_size=16)
    d.text((950, 1275), "La progression ne passe à VALIDE qu’après publication humaine d’une décision favorable.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    return save(img, "03-donnees-pedagogie.png")


def diagram_states():
    img, d = canvas("Diagrammes d’états-transitions", "Cycles de vie normatifs v2.0")

    def panel(xy, title, color):
        x1, y1, x2, y2 = xy
        d.rounded_rectangle(xy, radius=20, fill=(255, 255, 255), outline=color, width=3)
        d.text(((x1 + x2) / 2, y1 + 38), title, font=pil_font(27, True), fill=color, anchor="mm")

    def state(xy, text, color, size=20):
        return box(d, xy, text, outline=color, size=size, bold=True, radius=34)

    # Compte : refus depuis EN_ATTENTE, désactivation depuis le compte actif.
    panel((55, 150, 915, 610), "Compte et inscription", ORANGE)
    state((170, 230, 570, 305), "EN_ATTENTE\ninactif", ORANGE)
    state((650, 230, 850, 305), "REFUSE\nterminal", RED)
    arrow(d, (570, 267), (650, 267), color=RED, width=3)
    label_at(d, (610, 235), "refus", color=RED, size=16)
    state((170, 355, 570, 430), "ACCEPTE\ninactif + PIN", ORANGE)
    arrow(d, (370, 305), (370, 355), color=ORANGE, width=3)
    label_at(d, (610, 330), "bot éligible ou admin accepte", color=ORANGE, size=16)
    state((170, 480, 570, 555), "ACCEPTE\nactif", ORANGE)
    arrow(d, (370, 430), (370, 480), color=ORANGE, width=3)
    label_at(d, (610, 455), "PIN valide", color=ORANGE, size=16)
    state((650, 480, 850, 555), "DESACTIVE\naccès révoqués", RED)
    arrow(d, (570, 517), (650, 517), color=RED, width=3)
    label_at(d, (610, 485), "admin", color=RED, size=16)

    # Copie : ANNULEE est une branche d’EN_COURS, jamais une suite de CORRIGEE.
    panel((985, 150, 1845, 610), "Copie de test final", BLUE)
    state((1100, 220, 1500, 290), "EN_COURS", BLUE)
    state((1570, 220, 1810, 290), "ANNULEE\nabandon explicite", RED, size=18)
    arrow(d, (1500, 255), (1570, 255), color=RED, width=3)
    label_at(d, (1535, 225), "abandon", color=RED, size=15)
    state((1100, 325, 1500, 395), "SOUMISE\nsans note publique", BLUE, size=19)
    arrow(d, (1300, 290), (1300, 325), color=BLUE, width=3)
    label_at(d, (1515, 307), "soumettre", color=BLUE, size=16)
    state((1100, 430, 1500, 500), "SOUMISE + brouillon privé\nde correction", BLUE, size=18)
    arrow(d, (1300, 395), (1300, 430), color=BLUE, width=3)
    label_at(d, (1515, 412), "corriger", color=BLUE, size=16)
    state((1100, 525, 1500, 585), "CORRIGEE\nnote + décision publiées", BLUE, size=18)
    arrow(d, (1300, 500), (1300, 525), color=BLUE, width=3)
    label_at(d, (1515, 512), "publier", color=BLUE, size=16)

    # Niveau : le contrôle de complétude peut renvoyer au brouillon.
    panel((55, 665, 915, 1125), "Niveau et contenus", GREEN)
    state((170, 750, 570, 825), "BROUILLON\ninvisible", GREEN)
    state((170, 885, 570, 960), "CONTRÔLE COMPLET\ncours + entraînement + questions", GREEN, size=18)
    arrow(d, (370, 825), (370, 885), color=GREEN, width=3)
    label_at(d, (610, 855), "demander publication", color=GREEN, size=16)
    state((170, 1010, 570, 1085), "PUBLIE\nvisible aux affectés", GREEN)
    arrow(d, (370, 960), (370, 1010), color=GREEN, width=3)
    label_at(d, (610, 985), "complet", color=GREEN, size=16)
    state((650, 885, 850, 960), "INCOMPLET\nreste invisible", RED, size=18)
    arrow(d, (570, 922), (650, 922), color=RED, width=3)
    poly_arrow(d, [(750, 885), (750, 790), (570, 790)], "compléter", color=RED, width=3,
               label_pos=(660, 790), label_size=16)

    # Session et confiance : deux cycles indépendants.
    panel((985, 665, 1845, 1125), "Session et poste de confiance", PURPLE)
    state((1040, 760, 1380, 840), "Session active\n30 min inactif / 8 h max.", PURPLE, size=18)
    state((1040, 925, 1380, 1005), "Expirée ou révoquée", PURPLE, size=19)
    arrow(d, (1210, 840), (1210, 925), color=PURPLE, width=3)
    state((1450, 745, 1790, 810), "Absent ou non consenti", PURPLE, size=18)
    state((1450, 855, 1790, 935), "Créé après OTP\n48 h absolues", PURPLE, size=18)
    state((1450, 980, 1790, 1060), "Expirée, oubliée\nou révoquée", PURPLE, size=18)
    arrow(d, (1620, 810), (1620, 855), color=PURPLE, width=3)
    arrow(d, (1620, 935), (1620, 980), color=PURPLE, width=3)
    d.text((1415, 1092), "Dans les deux cas, le mot de passe est revérifié à l’ouverture.",
           font=pil_font(18, True), fill=RED, anchor="mm")

    d.text((950, 1210), "Une validation publiée ne peut pas être retirée par un changement ultérieur de seuil.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    d.text((950, 1252), "Le jeton de confiance ne remplace jamais le mot de passe.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    return save(img, "04-etats-transitions.png")


def sequence_diagram(name, title, subtitle, actors, messages):
    img, d = canvas(title, subtitle, size=(1900, 1420))
    n = len(actors)
    xs = [210 + i * ((1900 - 420) // max(1, n - 1)) for i in range(n)] if n > 1 else [950]
    colors = [BLUE, TEAL, PURPLE, GREEN, ORANGE, RED, NAVY]
    for i, (x, actor) in enumerate(zip(xs, actors)):
        box(d, (x - 145, 145, x + 145, 230), actor, outline=colors[i % len(colors)], size=22, bold=True, radius=12)
        d.line((x, 230, x, 1325), fill=(190, 198, 210), width=3)
    y = 285
    step = max(58, min(78, 980 // max(1, len(messages))))
    for idx, (a, b, label, kind) in enumerate(messages, 1):
        color = {"req": BLUE, "ok": GREEN, "err": RED, "data": PURPLE, "mail": ORANGE}.get(kind, GREY)
        arrow(d, (xs[a], y), (xs[b], y), f"{idx}. {label}", color=color, width=4, label_size=18, dy=-24, halo=False)
        y += step
    return save(img, name)


def diagram_sequences():
    paths = []
    paths.append(sequence_diagram(
        "05-sequence-inscription-activation.png",
        "Séquence — inscription, décision et activation PIN", "Aucun PIN à la demande initiale",
        ["Demandeur", "Client JavaFX", "PC1 HTTPS", "Bot règles / Admin", "PostgreSQL", "SMTP"],
        [
            (0, 1, "nom, e-mail, mot de passe; rôle du poste", "req"),
            (1, 2, "POST /api/auth/inscription", "req"),
            (2, 4, "créer compte EN_ATTENTE inactif, mot de passe bcrypt", "data"),
            (2, 3, "analyser rôle + domaine ou mettre en file admin", "req"),
            (3, 4, "pré-accepter apprenant éligible ou décision admin", "data"),
            (2, 4, "créer empreinte PIN 8 chiffres (48 h, 5 essais)", "data"),
            (2, 5, "envoyer la décision et le PIN", "mail"),
            (5, 0, "e-mail reçu", "mail"),
            (0, 1, "e-mail + PIN d’activation", "req"),
            (1, 2, "POST /api/auth/activation", "req"),
            (2, 4, "vérifier rôle, expiration, essais; activer le compte", "ok"),
            (2, 1, "compte activé; connexion possible", "ok"),
        ]))
    paths.append(sequence_diagram(
        "06-sequence-connexion-confiance.png",
        "Séquence — connexion, OTP et poste de confiance 48 h", "Mot de passe exigé à chaque ouverture",
        ["Utilisateur", "Client JavaFX", "Découverte LAN", "PC1 HTTPS", "PostgreSQL", "SMTP"],
        [
            (1, 2, "rechercher la plateforme et le port HTTPS", "req"),
            (2, 1, "adresse LAN réelle + port annoncé", "ok"),
            (0, 1, "e-mail + mot de passe (+ jeton local éventuel)", "req"),
            (1, 3, "POST /api/auth/connexion sur HTTPS", "req"),
            (3, 4, "vérifier bcrypt, actif, ACCEPTE et limitation", "data"),
            (3, 4, "si confiance valide : vérifier empreinte et expiration absolue", "data"),
            (3, 4, "sinon créer OTP 6 chiffres haché (10 min)", "data"),
            (3, 5, "envoyer l’OTP si nécessaire", "mail"),
            (0, 1, "saisir l’OTP; choix explicite de confiance", "req"),
            (1, 3, "POST /api/auth/code", "req"),
            (3, 4, "créer session; créer confiance seulement après OTP valide", "ok"),
            (3, 1, "rôle serveur + session (+ jeton 48 h à stocker)", "ok"),
            (1, 0, "ouvrir l’écran du rôle retourné par PC1", "ok"),
        ]))
    paths.append(sequence_diagram(
        "07-sequence-test-correction.png",
        "Séquence — apprentissage, test final et correction humaine", "Aucune note automatique au test final",
        ["Apprenant", "Client JavaFX", "PC1 HTTPS", "PostgreSQL", "Enseignant"],
        [
            (0, 1, "ouvrir un niveau publié et débloqué", "req"),
            (1, 2, "lire cours et entraînement (session requise)", "req"),
            (2, 3, "contrôler affectation, publication et progression", "data"),
            (0, 1, "démarrer ou reprendre le test final", "req"),
            (2, 3, "créer tentative EN_COURS + snapshot sujet/seuil", "data"),
            (2, 1, "IA refusée pendant la tentative réelle", "err"),
            (0, 1, "répondre puis soumettre explicitement", "req"),
            (2, 3, "passer la copie à SOUMISE, sans note", "data"),
            (4, 2, "lire uniquement ses copies affectées", "req"),
            (4, 2, "enregistrer brouillon privé", "data"),
            (4, 2, "publier note /20, commentaire et décision", "ok"),
            (2, 3, "CORRIGEE; si validée et seuil atteint, progression VALIDE", "ok"),
            (1, 0, "afficher décision; débloquer le niveau suivant si validé", "ok"),
        ]))
    paths.append(sequence_diagram(
        "10-sequence-ia-entrainement.png",
        "Séquence — aide IA en entraînement uniquement", "NVIDIA Nemotron via PC1 · aucun modèle local",
        ["Apprenant", "Panneau IA", "PC1 /api/ia/aide", "Garde-fous + corpus", "NVIDIA"],
        [
            (0, 1, "question + code d’entraînement", "req"),
            (1, 2, "POST HTTPS avec session du compte", "req"),
            (2, 3, "vérifier session, tentative EN_COURS, quotas", "data"),
            (3, 2, "refuser si test final ou demande de note/solution", "err"),
            (2, 3, "sélectionner des extraits du corpus du langage", "data"),
            (2, 4, "question, contexte borné, extraits; clé gardée sur PC1", "req"),
            (4, 2, "réponse structurée avec citations", "ok"),
            (2, 3, "valider format, citations et absence de décision", "data"),
            (2, 1, "explication et exemple bornés", "ok"),
            (1, 0, "afficher l’aide; aucun code n’est exécuté", "ok"),
        ]))
    return paths


def diamond(d, cx, cy, w, h, text, color):
    pts = [(cx, cy - h // 2), (cx + w // 2, cy), (cx, cy + h // 2), (cx - w // 2, cy)]
    d.polygon(pts, fill=(255, 248, 220), outline=color, width=3)
    font = pil_font(20, True)
    lines = wrap(d, text, font, w - 120)
    y = cy - (len(lines) - 1) * 13
    for line in lines:
        d.text((cx, y), line, font=font, fill=DARK, anchor="mm")
        y += 26
    return pts


def diagram_activity():
    img, d = canvas("Activité — progression de l’apprenant", "BPMN/UML simplifié · décision humaine au test",
                    size=(1900, 1500))
    cx = 950

    def step(y, label, color):
        box(d, (cx - 330, y, cx + 330, y + 90), label, outline=color, size=23, bold=True)
        return y + 90

    bottom = step(165, "Découvrir PC1 sur le LAN\nou secours manuel", BLUE)
    arrow(d, (cx, bottom), (cx, 285), color=GREY, width=4)
    diamond(d, cx, 350, 560, 130, "Compte accepté et activé ?", ORANGE)
    box(d, (90, 305, 500, 395), "Non : demande sans PIN\n→ décision → PIN → activation\npuis retour au contrôle", outline=RED, size=19, bold=True)
    arrow(d, (cx - 280, 350), (500, 350), "non / retour", color=RED, width=3, both=True)
    arrow(d, (cx, 415), (cx, 455), "oui", color=GREEN, width=4)

    y = 455
    for label, color in [
        ("Se connecter : mot de passe\n+ OTP ou confiance 48 h", PURPLE),
        ("Choisir Java, Python ou C++\nouvrir le niveau débloqué", TEAL),
        ("Lire le cours\npuis s’entraîner (IA autorisée)", GREEN),
        ("Passer le test final\nIA bloquée, réponses non notées", RED),
        ("Soumettre la copie\nétat SOUMISE", BLUE),
        ("Enseignant : corriger\npuis publier note et décision", GREEN),
    ]:
        bottom = step(y, label, color)
        if y != 1065:
            arrow(d, (cx, bottom), (cx, y + 122), color=GREY, width=4)
        y += 122

    # Le losange est entièrement sous la boîte de correction (aucun chevauchement).
    arrow(d, (cx, bottom), (cx, 1215), color=GREY, width=4)
    diamond(d, cx, 1280, 520, 130, "Décision publiée\nfavorable et seuil atteint ?", ORANGE)
    box(d, (90, 1235, 540, 1335), "Non : revoir le cours\npuis nouvelle tentative si autorisée", outline=RED, size=20, bold=True)
    box(d, (1360, 1235, 1810, 1335), "Oui : niveau suivant\ndans le même langage", outline=GREEN, size=21, bold=True)
    arrow(d, (cx - 260, 1280), (540, 1285), "non", color=RED, width=3)
    arrow(d, (cx + 260, 1280), (1360, 1285), "oui", color=GREEN, width=3)
    d.text((950, 1435), "Une copie SOUMISE n’est jamais une réussite : seule la publication de l’enseignant décide.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    return save(img, "08-activite-progression.png")


def diagram_components():
    img, d = canvas("Composants — architecture applicative", "Java 25 · JavaFX · HTTPS · PostgreSQL")
    # Client layer
    d.rounded_rectangle((55, 150, 610, 1180), radius=22, fill=(255, 255, 255), outline=BLUE, width=4)
    d.text((332, 190), "Poste client (rôle fixé)", font=pil_font(29, True), fill=BLUE, anchor="mm")
    comps = [
        ("UI JavaFX\nvues par rôle, CSS, thèmes", 235),
        ("Router, Session, TaskRunner\nnavigation et tâches asynchrones", 400),
        ("Accessibilité\nF1, F6, F8, noms accessibles", 565),
        ("Réseau client\ndécouverte LAN, salle, HTTPS", 730),
        ("Mémoire locale\njeton de confiance par compte/rôle", 895),
    ]
    for label, y in comps:
        box(d, (95, y, 570, y + 125), label, outline=BLUE, size=23, bold=True)
    # Server layer
    d.rounded_rectangle((760, 150, 1350, 1180), radius=22, fill=(255, 255, 255), outline=PURPLE, width=4)
    d.text((1055, 190), "Services PC1", font=pil_font(29, True), fill=PURPLE, anchor="mm")
    comps2 = [
        ("Serveur de salle + découverte\nUDP/TCP 7777, annonce v3", 235),
        ("API HTTPS\n/auth, /admin, /pedagogie, /ia", 400),
        ("Authentification et comptes\nsessions, PIN, OTP, bot règles", 565),
        ("Pédagogie\nniveaux, copies, progression", 730),
        ("Connecteur IA\nquotas, garde-fous, corpus", 895),
        ("SMTP sortant\ndécisions, PIN, OTP", 1030),
    ]
    for label, y in comps2:
        hh = 105 if y == 1030 else 125
        box(d, (800, y, 1310, y + hh), label, outline=PURPLE, size=22, bold=True)
    # Data/external
    d.rounded_rectangle((1435, 150, 1845, 1180), radius=22, fill=(255, 255, 255), outline=GREEN, width=4)
    d.text((1640, 190), "Données / externes", font=pil_font(27, True), fill=GREEN, anchor="mm")
    box(d, (1475, 250, 1805, 430), "PostgreSQL\nDocker sur PC1\nmigrations 01–14", outline=GREEN, size=24, bold=True)
    box(d, (1475, 520, 1805, 680), "Configuration privée\nTLS, SMTP, NVIDIA\njamais dans Git", outline=RED, size=23, bold=True)
    box(d, (1475, 760, 1805, 920), "NVIDIA Nemotron\nappel sortant\nuniquement depuis PC1", outline=PURPLE, size=23, bold=True)
    box(d, (1475, 985, 1805, 1125), "Fournisseur e-mail\nSMTP", outline=ORANGE, size=24, bold=True)
    arrow(d, (610, 790), (760, 300), "découverte", color=TEAL, width=4)
    arrow(d, (610, 790), (760, 460), "HTTPS/JSON", color=BLUE, width=4)
    arrow(d, (1350, 625), (1435, 340), "JDBC", color=GREEN, width=4)
    arrow(d, (1350, 950), (1435, 840), "API", color=PURPLE, width=4)
    arrow(d, (1350, 1080), (1435, 1055), "SMTP", color=ORANGE, width=4)
    d.text((950, 1260), "Les clients ne contiennent ni Docker, ni accès PostgreSQL, ni clé NVIDIA, ni secret SMTP.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    return save(img, "09-composants.png")


def diagram_deployment():
    img, d = canvas("Déploiement — PC1 central et postes par rôle", "Docker uniquement sur PC1 · adresse LAN réelle")
    # PC1
    d.rounded_rectangle((620, 150, 1420, 1120), radius=26, fill=(255, 255, 255), outline=NAVY, width=5)
    d.text((1020, 195), "PC1 — serveur de la plateforme", font=pil_font(32, True), fill=NAVY, anchor="mm")
    box(d, (680, 250, 1360, 390), "Docker Desktop / Compose\nPostgreSQL réservé au service PC1\nvolume et sauvegardes à préserver", outline=GREEN, size=23, bold=True)
    box(d, (680, 440, 1000, 620), "Service HTTPS Java\nport dynamique annoncé\nTLS strict", outline=PURPLE, size=23, bold=True)
    box(d, (1040, 440, 1360, 620), "Serveur de salle\n7777 TCP/UDP\ndécouverte et présence", outline=TEAL, size=23, bold=True)
    box(d, (680, 670, 1000, 850), "Configuration privée locale\nDB, TLS, SMTP, NVIDIA\nbot d’inscription", outline=RED, size=23, bold=True)
    box(d, (1040, 670, 1360, 850), "Lanceurs PC1\nDocker automatique\ninstance HTTPS unique", outline=BLUE, size=23, bold=True)
    box(d, (680, 900, 1360, 1045), "PC1 doit être allumé et joignable pour se connecter et utiliser les données.\nIl n’a pas à rester allumé 24 h/24 hors des périodes d’usage.", outline=ORANGE, size=22, bold=True)
    # clients
    clients = [("PC2\nAdministrateur", 190, ORANGE), ("PC3\nEnseignant", 500, GREEN), ("PC4\nApprenant", 810, BLUE)]
    for label, y, color in clients:
        box(d, (60, y, 430, y + 210), label + "\nJavaFX + JDK 25\nsans Docker", outline=color, size=25, bold=True)
        arrow(d, (430, y + 105), (620, 530), "LAN / HTTPS", color=color, width=4)
    # External
    box(d, (1510, 250, 1840, 500), "Internet sortant\nSMTP\nPIN, décisions, OTP", outline=ORANGE, size=24, bold=True)
    box(d, (1510, 620, 1840, 880), "Internet sortant\nNVIDIA Nemotron\nclé uniquement sur PC1", outline=PURPLE, size=24, bold=True)
    arrow(d, (1420, 530), (1510, 375), "SMTP", color=ORANGE, width=4)
    arrow(d, (1420, 530), (1510, 750), "HTTPS API", color=PURPLE, width=4)
    d.text((950, 1205), "Même Wi-Fi/LAN requis ; Windows gère le Wi-Fi et ses mots de passe. Secours : adresse et port manuels.",
           font=pil_font(23, True), fill=DARK, anchor="mm")
    d.text((950, 1250), "Aucun poste client ne doit recevoir les secrets ou la clé privée TLS de PC1.",
           font=pil_font(23, True), fill=RED, anchor="mm")
    return save(img, "11-deploiement.png")


def diagram_task_chain():
    img, d = canvas("Chaîne globale des tâches", "HTA v2.0 · dépendances entre acteurs", size=(1900, 1250))
    lanes = [
        ("Apprenant", 170, BLUE, ["A0 Découvrir\n+ se connecter", "A1 Compte\n+ activation", "A2 Cours +\nentraînement", "A3 Test final\nsans IA", "A4 Décision +\nprogression"]),
        ("Enseignant", 455, GREEN, ["E1 Créer/préparer\nun niveau", "Publier cours,\nentraînement, test", "E2 Lire les copies\naffectées", "Corriger en\nbrouillon", "Publier note +\ndécision"]),
        ("Administrateur / système", 740, ORANGE, ["AD2 Démarrer\nPC1", "Bot règles ou\nfile admin", "AD1 Filtrer et\ndécider par lot", "Affecter apprenant\nà enseignant", "Superviser comptes\net PIN"]),
        ("IA en entraînement", 1025, PURPLE, ["IA1 Question\n+ code", "Contrôles PC1\nquotas/garde-fous", "NVIDIA\n+ citations", "Explication\nbornée", "Retour à A2\njamais A3"]),
    ]
    for title, y, color, items in lanes:
        box(d, (45, y, 270, y + 170), title, fill=(255, 255, 255), outline=color, size=24, bold=True)
        x = 330
        prev = None
        for item in items:
            b = (x, y, x + 275, y + 170)
            box(d, b, item, outline=color, size=22, bold=True)
            if prev:
                arrow(d, (prev[2], y + 85), (b[0], y + 85), color=color, width=4)
            prev = b
            x += 315
    # Dépendances inter-acteurs routées dans les couloirs entre boîtes.
    # Publication enseignant (colonne 2) -> A2 apprenant (colonne 3).
    poly_arrow(d, [(782, 455), (782, 405), (1055, 405), (1055, 340)], "contenu publié",
               color=GREEN, width=4, label_pos=(918, 405), label_size=17)
    # Copie A3 (colonne 4) -> E2 « Lire les copies » (colonne 3).
    poly_arrow(d, [(1412, 340), (1412, 405), (1097, 405), (1097, 455)], "copie SOUMISE",
               color=BLUE, width=4, label_pos=(1255, 405), label_size=17)
    # Décision publiée -> A4, dans le même couloir vertical.
    arrow(d, (1727, 455), (1727, 340), "décision publiée", color=GREEN, width=4, label_size=17)
    # Décision/activation administrative -> début du travail enseignant E1.
    poly_arrow(d, [(782, 740), (782, 682), (467, 682), (467, 625)], "enseignant activé",
               color=ORANGE, width=4, label_pos=(625, 682), label_size=17)
    # IA -> A2 par le couloir vertical entre colonnes 2 et 3, sans traverser les boîtes.
    poly_arrow(d, [(960, 1110), (940, 1110), (940, 370), (1135, 370), (1135, 340)], "aide A2 uniquement",
               color=PURPLE, width=4, label_pos=(1038, 370), label_size=17)
    return save(img, "12-chaine-taches.png")


def generate_diagrams():
    paths = []
    paths.append(diagram_use_cases())
    paths.append(diagram_data_identity())
    paths.append(diagram_data_pedagogy())
    paths.append(diagram_states())
    paths.extend(diagram_sequences())
    paths.append(diagram_activity())
    paths.append(diagram_components())
    paths.append(diagram_deployment())
    paths.append(diagram_task_chain())
    return paths


# ---------------------------------------------------------------------------
# Rendu Markdown -> PDF (ReportLab)
# ---------------------------------------------------------------------------

RL_NAVY = colors.HexColor("#1f263a")
RL_BLUE = colors.HexColor("#2563eb")
RL_TEAL = colors.HexColor("#087f9c")
RL_LIGHT = colors.HexColor("#eef2f7")
RL_BORDER = colors.HexColor("#cbd5e1")
RL_RED = colors.HexColor("#b42318")


def register_fonts():
    regular = "Helvetica"
    bold = "Helvetica-Bold"
    mono = "Courier"
    if FONT_REG.exists() and FONT_BOLD.exists():
        pdfmetrics.registerFont(TTFont("DocSans", str(FONT_REG)))
        pdfmetrics.registerFont(TTFont("DocSans-Bold", str(FONT_BOLD)))
        pdfmetrics.registerFontFamily("DocSans", normal="DocSans", bold="DocSans-Bold", italic="DocSans", boldItalic="DocSans-Bold")
        regular, bold = "DocSans", "DocSans-Bold"
    if FONT_MONO.exists():
        pdfmetrics.registerFont(TTFont("DocMono", str(FONT_MONO)))
        pdfmetrics.registerFontFamily("DocMono", normal="DocMono", bold="DocMono", italic="DocMono", boldItalic="DocMono")
        mono = "DocMono"
    return regular, bold, mono


FONT, FONT_B, FONT_M = register_fonts()


def inline(text: str) -> str:
    out = html.escape(text, quote=False)
    out = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", out)
    out = re.sub(r"(?<!\*)\*(.+?)\*(?!\*)", r"<i>\1</i>", out)
    out = re.sub(r"`([^`]+)`", rf"<font face='{FONT_M}'>\1</font>", out)
    return out


def styles():
    ss = getSampleStyleSheet()
    return {
        "title": ParagraphStyle("title", parent=ss["Title"], fontName=FONT_B, fontSize=28, leading=34, textColor=RL_NAVY, alignment=TA_LEFT, spaceAfter=10),
        "subtitle": ParagraphStyle("subtitle", parent=ss["Normal"], fontName=FONT, fontSize=14, leading=20, textColor=colors.HexColor("#475569"), spaceAfter=16),
        "h1": ParagraphStyle("h1", parent=ss["Heading1"], fontName=FONT_B, fontSize=20, leading=25, textColor=RL_NAVY, spaceBefore=16, spaceAfter=8, keepWithNext=1),
        "h2": ParagraphStyle("h2", parent=ss["Heading2"], fontName=FONT_B, fontSize=15.5, leading=20, textColor=RL_TEAL, spaceBefore=13, spaceAfter=6, keepWithNext=1),
        "h3": ParagraphStyle("h3", parent=ss["Heading3"], fontName=FONT_B, fontSize=12.5, leading=16, textColor=RL_NAVY, spaceBefore=10, spaceAfter=5, keepWithNext=1),
        "body": ParagraphStyle("body", parent=ss["BodyText"], fontName=FONT, fontSize=9.8, leading=14.2, alignment=TA_LEFT, spaceAfter=6),
        "bullet": ParagraphStyle("bullet", parent=ss["BodyText"], fontName=FONT, fontSize=9.8, leading=14.2, leftIndent=16, firstLineIndent=0, bulletIndent=5, spaceAfter=4),
        "quote": ParagraphStyle("quote", parent=ss["BodyText"], fontName=FONT, fontSize=9.6, leading=14, leftIndent=12, rightIndent=8, borderPadding=(7, 7, 7), backColor=RL_LIGHT, textColor=RL_NAVY, spaceAfter=8),
        "caption": ParagraphStyle("caption", parent=ss["Normal"], fontName=FONT, fontSize=8.2, leading=11, textColor=colors.HexColor("#475569"), alignment=TA_CENTER, spaceBefore=3, spaceAfter=9),
        "cell": ParagraphStyle("cell", parent=ss["Normal"], fontName=FONT, fontSize=7.4, leading=9.6),
        "cellb": ParagraphStyle("cellb", parent=ss["Normal"], fontName=FONT_B, fontSize=7.4, leading=9.6, textColor=colors.white),
        "code": ParagraphStyle("code", parent=ss["Code"], fontName=FONT_M, fontSize=7.2, leading=9.5, leftIndent=4, rightIndent=4, spaceAfter=8),
    }


def parse_metadata(lines):
    meta = {}
    body = []
    for line in lines:
        m = re.match(r"<!--\s*([a-z_-]+):\s*(.*?)\s*-->\s*$", line.strip())
        if m:
            meta[m.group(1)] = m.group(2)
        else:
            body.append(line)
    return meta, body


def is_table_separator(line: str) -> bool:
    cells = [c.strip() for c in line.strip().strip("|").split("|")]
    return bool(cells) and all(re.fullmatch(r":?-{3,}:?", c or "---") for c in cells)


def split_table_row(line: str):
    return [c.strip() for c in line.strip().strip("|").split("|")]


def make_table(rows, st):
    if not rows:
        return Spacer(1, 1)
    cols = max(len(r) for r in rows)
    normalized = [r + [""] * (cols - len(r)) for r in rows]
    available = A4[0] - 36 * mm
    raw = []
    for c in range(cols):
        longest = max(len(re.sub(r"[*`]", "", r[c])) for r in normalized)
        # Compression non linéaire : éviter qu’une colonne de prose écrase les
        # colonnes courtes (ID, opérateur HTA, statut) jusqu’à couper leurs mots.
        raw.append(max(8, longest) ** 0.62)
    total = sum(raw)
    widths = [max(46, available * v / total) for v in raw]
    scale = available / sum(widths)
    widths = [w * scale for w in widths]
    data = []
    for ri, row in enumerate(normalized):
        style = st["cellb"] if ri == 0 else st["cell"]
        data.append([Paragraph(inline(cell), style) for cell in row])
    table = Table(data, colWidths=widths, repeatRows=1, hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), RL_NAVY),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.45, RL_BORDER),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafc")]),
        ("LEFTPADDING", (0, 0), (-1, -1), 4),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4),
        ("TOPPADDING", (0, 0), (-1, -1), 3),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 3),
    ]))
    return table


def header_footer(canvas, doc, meta, first=False):
    canvas.saveState()
    canvas.setFont(FONT, 7.5)
    canvas.setFillColor(colors.HexColor("#64748b"))
    if not first:
        canvas.drawString(18 * mm, A4[1] - 12 * mm, meta.get("short", meta.get("title", "IHM 2026")))
        canvas.drawRightString(A4[0] - 18 * mm, A4[1] - 12 * mm, meta.get("version", ""))
        canvas.setStrokeColor(RL_BORDER)
        canvas.line(18 * mm, A4[1] - 14 * mm, A4[0] - 18 * mm, A4[1] - 14 * mm)
    canvas.drawString(18 * mm, 12 * mm, "Projet d’IHM 2026 · document généré depuis les sources Markdown")
    canvas.drawRightString(A4[0] - 18 * mm, 12 * mm, f"Page {doc.page}")
    canvas.restoreState()


def render_markdown(md_path: Path, pdf_path: Path):
    lines = md_path.read_text(encoding="utf-8").splitlines()
    meta, lines = parse_metadata(lines)
    st = styles()
    story = []

    # Cover
    story.append(Spacer(1, 42 * mm))
    story.append(Paragraph("PROJET D’IHM 2026", ParagraphStyle("kicker", parent=st["body"], fontName=FONT_B, fontSize=11, textColor=RL_TEAL, spaceAfter=12)))
    story.append(Paragraph(inline(meta.get("title", md_path.stem)), st["title"]))
    if meta.get("subtitle"):
        story.append(Paragraph(inline(meta["subtitle"]), st["subtitle"]))
    cover_rows = [
        ["Version", meta.get("version", "")],
        ["Date", meta.get("date", "3 novembre 2026")],
        ["Référence logicielle", meta.get("baseline", "Application 0.14.4 · branche main après PR #31")],
        ["Statut", meta.get("status", "Mise à jour normative")],
    ]
    cover = Table([[Paragraph(f"<b>{inline(a)}</b>", st["cell"]), Paragraph(inline(b), st["cell"])] for a, b in cover_rows],
                  colWidths=[42 * mm, 128 * mm])
    cover.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (0, -1), RL_LIGHT),
        ("GRID", (0, 0), (-1, -1), 0.5, RL_BORDER),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    story.append(cover)
    story.append(Spacer(1, 14 * mm))
    story.append(Paragraph("Document aligné sur le code, les migrations SQL, les lanceurs et les guides de recette du dépôt. Les éléments non prouvés sous Windows sont explicitement marqués comme recette à compléter.", st["quote"]))
    story.append(Spacer(1, 10 * mm))
    story.append(Paragraph("Dossier documentaire coordonné", st["h2"]))
    dossier_rows = [
        ["Document", "Question traitée"],
        ["Analyse des besoins v2.0", "Quels besoins, règles et droits doivent être garantis ?"],
        ["Diagrammes du projet v2.0", "Comment données, états, séquences et déploiement s’organisent-ils ?"],
        ["Modèles de tâches v2.0", "Comment chaque acteur accomplit-il sa tâche et sort-il des boucles d’erreur ?"],
        ["Cahier des charges v1.0", "Quel périmètre, quelles exigences et quels critères permettent d’accepter le livrable ?"],
    ]
    dossier = Table(
        [[Paragraph(inline(cell), st["cellb"] if ri == 0 else st["cell"]) for cell in row] for ri, row in enumerate(dossier_rows)],
        colWidths=[58 * mm, 112 * mm],
    )
    dossier.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, 0), RL_NAVY),
        ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
        ("GRID", (0, 0), (-1, -1), 0.5, RL_BORDER),
        ("ROWBACKGROUNDS", (0, 1), (-1, -1), [colors.white, colors.HexColor("#f8fafc")]),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 7),
        ("RIGHTPADDING", (0, 0), (-1, -1), 7),
        ("TOPPADDING", (0, 0), (-1, -1), 5),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
    ]))
    story.append(dossier)
    story.append(PageBreak())

    # Contents, without page numbers (stable across regenerations).
    headings = []
    for line in lines:
        m = re.match(r"^(#{1,3})\s+(.*)$", line.strip())
        if m:
            headings.append((len(m.group(1)), m.group(2).strip()))
    story.append(Paragraph("Sommaire", st["h1"]))
    for level, heading in headings:
        indent = 0 if level == 1 else (10 if level == 2 else 20)
        style = ParagraphStyle(f"toc{level}", parent=st["body"], leftIndent=indent, fontSize=9.4 if level < 3 else 8.8,
                               leading=11.5, fontName=FONT_B if level == 1 else FONT, spaceBefore=0, spaceAfter=1)
        story.append(Paragraph(inline(heading), style))
    story.append(PageBreak())

    paragraph: list[str] = []
    code: list[str] = []
    in_code = False

    def flush_paragraph():
        nonlocal paragraph
        if paragraph:
            story.append(Paragraph(inline(" ".join(paragraph).strip()), st["body"]))
            paragraph = []

    i = 0
    while i < len(lines):
        raw = lines[i]
        line = raw.strip()
        if line.startswith("```"):
            flush_paragraph()
            if in_code:
                story.append(Preformatted("\n".join(code), st["code"]))
                code = []
                in_code = False
            else:
                in_code = True
            i += 1
            continue
        if in_code:
            code.append(raw)
            i += 1
            continue
        if not line:
            flush_paragraph()
            i += 1
            continue
        if line == "<!-- pagebreak -->":
            flush_paragraph()
            story.append(PageBreak())
            i += 1
            continue
        m_img = re.match(r"!\[(.*?)\]\((.*?)\)$", line)
        if m_img:
            flush_paragraph()
            caption, rel = m_img.groups()
            img_path = (md_path.parent / rel).resolve()
            if not img_path.exists():
                img_path = (ROOT / rel).resolve()
            if img_path.exists():
                with Image.open(img_path) as im:
                    iw, ih = im.size
                max_w = A4[0] - 36 * mm
                max_h = 190 * mm
                scale = min(max_w / iw, max_h / ih)
                story.append(RLImage(str(img_path), width=iw * scale, height=ih * scale))
                if caption:
                    story.append(Paragraph(inline(caption), st["caption"]))
            else:
                story.append(Paragraph(f"[Image manquante : {html.escape(rel)}]", st["quote"]))
            i += 1
            continue
        if line.startswith("|") and i + 1 < len(lines) and is_table_separator(lines[i + 1]):
            flush_paragraph()
            rows = [split_table_row(line)]
            i += 2
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(split_table_row(lines[i]))
                i += 1
            story.append(make_table(rows, st))
            story.append(Spacer(1, 3 * mm))
            continue
        m_h = re.match(r"^(#{1,4})\s+(.*)$", line)
        if m_h:
            flush_paragraph()
            level = len(m_h.group(1))
            style = st["h1"] if level == 1 else st["h2"] if level == 2 else st["h3"]
            story.append(Paragraph(inline(m_h.group(2).strip()), style))
            i += 1
            continue
        if line.startswith("> "):
            flush_paragraph()
            story.append(Paragraph(inline(line[2:].strip()), st["quote"]))
            i += 1
            continue
        m_b = re.match(r"^[-*]\s+(.*)$", line)
        if m_b:
            flush_paragraph()
            story.append(Paragraph(inline(m_b.group(1)), st["bullet"], bulletText="•"))
            i += 1
            continue
        m_n = re.match(r"^(\d+)\.\s+(.*)$", line)
        if m_n:
            flush_paragraph()
            story.append(Paragraph(inline(m_n.group(2)), st["bullet"], bulletText=f"{m_n.group(1)}."))
            i += 1
            continue
        if re.fullmatch(r"-{3,}", line):
            flush_paragraph()
            story.append(Spacer(1, 2 * mm))
            i += 1
            continue
        paragraph.append(line)
        i += 1
    flush_paragraph()
    if code:
        story.append(Preformatted("\n".join(code), st["code"]))

    doc = SimpleDocTemplate(str(pdf_path), pagesize=A4, leftMargin=18 * mm, rightMargin=18 * mm,
                            topMargin=20 * mm, bottomMargin=17 * mm,
                            title=meta.get("title", pdf_path.stem), author="Projet d’IHM 2026")
    doc.build(story, onFirstPage=lambda c, d: header_footer(c, d, meta, first=True),
              onLaterPages=lambda c, d: header_footer(c, d, meta, first=False))
    return pdf_path


DOCUMENTS = [
    ("analyse-des-besoins-v2.md", ROOT / "docs" / "analyse-besoins-IHM.pdf"),
    ("diagrammes-du-projet-v2.md", ROOT / "docs" / "Diagrammes-du-projet.pdf"),
    ("modeles-de-taches-v2.md", ROOT / "docs" / "Modeles-de-taches-du-projet-IHM.pdf"),
    ("cahier-des-charges-v1.md", ROOT / "docs" / "Cahier-des-charges.pdf"),
]


def main():
    generate_diagrams()
    outputs = []
    for source_name, target in DOCUMENTS:
        source = SRC / source_name
        if not source.exists():
            raise SystemExit(f"Source manquante : {source}")
        outputs.append(render_markdown(source, target))
        print(f"PDF : {target.relative_to(ROOT)}")
    # The former canvas rendering of the needs analysis must not remain an
    # obsolete second truth: publish the same v2.0 binary under that name too.
    canvas_target = ROOT / "docs" / "canvas-document-analyse-besoins.pdf"
    canvas_target.write_bytes((ROOT / "docs" / "analyse-besoins-IHM.pdf").read_bytes())
    print(f"PDF : {canvas_target.relative_to(ROOT)} (même contenu v2.0)")


if __name__ == "__main__":
    main()
