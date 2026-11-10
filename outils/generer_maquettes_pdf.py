#!/usr/bin/env python3
"""Compile docs/sources-v2/maquettes-basse-fidelite.md en docs/Maquettes-basse-fidelite.pdf.

Réutilise la charte et les polices DejaVu de outils/generer_documents_v2.py tout en
ajustant le format des planches (croquis papier + wireframe) pour qu'une planche
complète (tableau, commentaire et deux figures) tienne lisiblement sur une page A4.
"""
from __future__ import annotations

import html
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "outils"))

from PIL import Image
from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.lib.units import mm
from reportlab.platypus import (
    Image as RLImage,
    PageBreak,
    Paragraph,
    Preformatted,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

import generer_documents_v2 as doc2


def build_maquettes_pdf(
    md_path: Path = ROOT / "docs" / "sources-v2" / "maquettes-basse-fidelite.md",
    pdf_path: Path = ROOT / "docs" / "Maquettes-basse-fidelite.pdf",
) -> Path:
    lines = md_path.read_text(encoding="utf-8").splitlines()
    meta, lines = doc2.parse_metadata(lines)
    st = doc2.styles()

    # Styles légèrement resserrés pour caser Fiche + Croquis + Wireframe sur 1 page A4
    st["h1"] = ParagraphStyle(
        "mh1",
        parent=st["h1"],
        fontSize=12.2,
        leading=15.0,
        spaceBefore=2,
        spaceAfter=2.5,
    )
    st["body"] = ParagraphStyle(
        "mbody",
        parent=st["body"],
        fontSize=8.4,
        leading=11.0,
        spaceAfter=3.0,
    )
    st["caption"] = ParagraphStyle(
        "mcaption",
        parent=st["caption"],
        fontSize=7.6,
        leading=9.4,
        spaceBefore=1.0,
        spaceAfter=2.5,
        alignment=TA_CENTER,
    )

    story = []

    # Couverture alignée sur les livrables v2.0
    story.append(Spacer(1, 36 * mm))
    story.append(
        Paragraph(
            "PROJET D’IHM 2026",
            ParagraphStyle(
                "kicker",
                parent=st["body"],
                fontName=doc2.FONT_B,
                fontSize=11,
                textColor=doc2.RL_TEAL,
                spaceAfter=10,
            ),
        )
    )
    story.append(Paragraph(doc2.inline(meta.get("title", "Maquettes basse fidélité")), st["title"]))
    if meta.get("subtitle"):
        story.append(Paragraph(doc2.inline(meta["subtitle"]), st["subtitle"]))

    cover_rows = [
        ["Version", meta.get("version", "Version 2.0")],
        ["Date", meta.get("date", "10 novembre 2026")],
        ["Référence logicielle", meta.get("baseline", "Application 0.14.4 · branche main après PR #31")],
        ["Statut", meta.get("status", "Livrable basse fidélité (croquis papier + wireframes)")],
        ["Portée", "12 planches × 2 styles (24 SVG + 24 PNG haute résolution 1560 × 1000 px)"],
    ]
    cover = Table(
        [
            [Paragraph(f"<b>{doc2.inline(a)}</b>", st["cell"]), Paragraph(doc2.inline(b), st["cell"])]
            for a, b in cover_rows
        ],
        colWidths=[44 * mm, 128 * mm],
    )
    cover.setStyle(
        TableStyle(
            [
                ("BACKGROUND", (0, 0), (0, -1), doc2.RL_LIGHT),
                ("GRID", (0, 0), (-1, -1), 0.5, doc2.RL_BORDER),
                ("VALIGN", (0, 0), (-1, -1), "TOP"),
                ("LEFTPADDING", (0, 0), (-1, -1), 7),
                ("RIGHTPADDING", (0, 0), (-1, -1), 7),
                ("TOPPADDING", (0, 0), (-1, -1), 5),
                ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
            ]
        )
    )
    story.append(cover)
    story.append(Spacer(1, 10 * mm))
    story.append(
        Paragraph(
            "Dossier de maquettes basse fidélité en français (croquis papier + wireframes filaires) "
            "aligné sur l’analyse des besoins v2.0, les modèles de tâches HTA (A0–UX1) et les vues JavaFX de la version 0.14.4.",
            st["quote"],
        )
    )
    story.append(Spacer(1, 8 * mm))

    # Sommaire
    headings = []
    for line in lines:
        m = re.match(r"^(#{1,3})\s+(.*)$", line.strip())
        if m:
            headings.append((len(m.group(1)), m.group(2).strip()))
    story.append(Paragraph("Sommaire des planches", st["h2"]))
    for level, heading in headings:
        indent = 0 if level == 1 else (10 if level == 2 else 18)
        style = ParagraphStyle(
            f"mtoc{level}",
            parent=st["body"],
            leftIndent=indent,
            fontSize=8.8 if level == 1 else 8.2,
            leading=11.0,
            fontName=doc2.FONT_B if level == 1 else doc2.FONT,
            spaceBefore=0,
            spaceAfter=1,
        )
        story.append(Paragraph(doc2.inline(heading), style))
    story.append(PageBreak())

    paragraph: list[str] = []
    code: list[str] = []
    in_code = False

    def flush_paragraph():
        nonlocal paragraph
        if paragraph:
            story.append(Paragraph(doc2.inline(" ".join(paragraph).strip()), st["body"]))
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
                # Calibré à 146 mm de large (~93.5 mm de haut) pour faire tenir
                # les 2 variantes (croquis + wireframe) + notice sur 1 seule page A4
                max_w = 146 * mm
                max_h = 93 * mm
                scale = min(max_w / iw, max_h / ih)
                rl_img = RLImage(str(img_path), width=iw * scale, height=ih * scale)
                rl_img.hAlign = "CENTER"
                story.append(rl_img)
                if caption:
                    story.append(Paragraph(doc2.inline(caption), st["caption"]))
            else:
                story.append(Paragraph(f"[Image manquante : {html.escape(rel)}]", st["quote"]))
            i += 1
            continue
        if line.startswith("|") and i + 1 < len(lines) and doc2.is_table_separator(lines[i + 1]):
            flush_paragraph()
            rows = [doc2.split_table_row(line)]
            i += 2
            while i < len(lines) and lines[i].strip().startswith("|"):
                rows.append(doc2.split_table_row(lines[i]))
                i += 1
            story.append(doc2.make_table(rows, st))
            story.append(Spacer(1, 2 * mm))
            continue
        m_h = re.match(r"^(#{1,4})\s+(.*)$", line)
        if m_h:
            flush_paragraph()
            level = len(m_h.group(1))
            style = st["h1"] if level == 1 else st["h2"] if level == 2 else st["h3"]
            story.append(Paragraph(doc2.inline(m_h.group(2).strip()), style))
            i += 1
            continue
        if line.startswith("> "):
            flush_paragraph()
            story.append(Paragraph(doc2.inline(line[2:].strip()), st["quote"]))
            i += 1
            continue
        m_b = re.match(r"^[-*]\s+(.*)$", line)
        if m_b:
            flush_paragraph()
            story.append(Paragraph(doc2.inline(m_b.group(1)), st["bullet"], bulletText="•"))
            i += 1
            continue
        m_n = re.match(r"^(\d+)\.\s+(.*)$", line)
        if m_n:
            flush_paragraph()
            story.append(Paragraph(doc2.inline(m_n.group(2)), st["bullet"], bulletText=f"{m_n.group(1)}."))
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

    doc = SimpleDocTemplate(
        str(pdf_path),
        pagesize=A4,
        leftMargin=18 * mm,
        rightMargin=18 * mm,
        topMargin=17 * mm,
        bottomMargin=15 * mm,
        title=meta.get("title", pdf_path.stem),
        author="Projet d’IHM 2026",
    )
    doc.build(
        story,
        onFirstPage=lambda c, d: doc2.header_footer(c, d, meta, first=True),
        onLaterPages=lambda c, d: doc2.header_footer(c, d, meta, first=False),
    )
    return pdf_path


if __name__ == "__main__":
    out = build_maquettes_pdf()
    print(f"PDF : {out.relative_to(ROOT)} ({out.stat().st_size} octets)")
