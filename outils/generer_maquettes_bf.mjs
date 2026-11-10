#!/usr/bin/env node
/**
 * Générateur de maquettes basse fidélité (Croquis papier + Wireframes filaires)
 * en français pour la Plateforme intelligente d'apprentissage (v0.14.4).
 *
 * Produit 12 planches × 2 styles (SVG + PNG) dans :
 *   - docs/maquettes/croquis-papier/
 *   - docs/maquettes/wireframes/
 *
 * Dépendances Node : roughjs, @resvg/resvg-js
 */

import fs from 'node:fs';
import path from 'node:path';
import { createRequire } from 'node:module';
import { fileURLToPath } from 'node:url';

const require = createRequire(import.meta.url);
const __dirname = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(__dirname, '..');

function loadDep(name) {
  const candidates = [
    name,
    path.join('/tmp/wf_tools/node_modules', name),
    path.join(ROOT, 'node_modules', name),
  ];
  for (const c of candidates) {
    try {
      return require(c);
    } catch (_) {
      // try next
    }
  }
  throw new Error(`Dépendance Node introuvable : ${name} (installez roughjs et @resvg/resvg-js)`);
}

const rough = loadDep(
  fs.existsSync('/tmp/wf_tools/node_modules/roughjs/bundled/rough.cjs.js')
    ? '/tmp/wf_tools/node_modules/roughjs/bundled/rough.cjs.js'
    : 'roughjs'
);
const { Resvg } = loadDep('@resvg/resvg-js');

const FONT_FILES = [
  '/tmp/wf_tools/PatrickHand.ttf',
  '/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf',
  '/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf',
  path.join(ROOT, 'src/main/resources/fr/ihm2026/fonts/JetBrainsMono-Regular.ttf'),
  path.join(ROOT, 'src/main/resources/fr/ihm2026/fonts/JetBrainsMono-Bold.ttf'),
].filter((f) => fs.existsSync(f));

const W = 1560;
const H = 1000;

const DIR_SKETCH = path.join(ROOT, 'docs/maquettes/croquis-papier');
const DIR_WIRE = path.join(ROOT, 'docs/maquettes/wireframes');
fs.mkdirSync(DIR_SKETCH, { recursive: true });
fs.mkdirSync(DIR_WIRE, { recursive: true });

// ---------------------------------------------------------------------------
// Utilitaires texte & SVG
// ---------------------------------------------------------------------------
function esc(s) {
  return String(s ?? '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function wrapLines(text, maxPx, fontSize, factor = 0.50) {
  const out = [];
  const maxChars = Math.max(8, Math.floor(maxPx / (fontSize * factor)));
  for (const para of String(text ?? '').split('\n')) {
    const words = para.trim().split(/\s+/).filter(Boolean);
    if (words.length === 0) {
      out.push('');
      continue;
    }
    let cur = words[0];
    for (let i = 1; i < words.length; i++) {
      if ((cur + ' ' + words[i]).length <= maxChars) {
        cur += ' ' + words[i];
      } else {
        out.push(cur);
        cur = words[i];
      }
    }
    out.push(cur);
  }
  return out;
}

// ---------------------------------------------------------------------------
// Constructeur de canevas Dual-Mode (sketch vs wire)
// ---------------------------------------------------------------------------
class Canvas {
  constructor(mode, seed = 42) {
    this.mode = mode; // 'sketch' | 'wire'
    this.isSketch = mode === 'sketch';
    this.gen = rough.generator({
      options: {
        roughness: 1.35,
        bowing: 1.1,
        seed,
        strokeWidth: 1.8,
        disableMultiStroke: false,
      },
    });
    this.els = [];
    this.overlays = [];
  }

  push(s) {
    this.els.push(s);
  }

  overlay(s) {
    this.overlays.push(s);
  }

  roughPaths(drawable, extraAttrs = '') {
    return this.gen
      .toPaths(drawable)
      .map(
        (p) =>
          `<path d="${p.d}" fill="${p.fill || 'none'}" stroke="${p.stroke || 'none'}" stroke-width="${
            p.strokeWidth || 1.6
          }" stroke-linecap="round" stroke-linejoin="round" ${extraAttrs}/>`
      )
      .join('\n');
  }

  // Fond de feuille
  sheet(plateNum, title, subtitle, roleTag, htaTag) {
    if (this.isSketch) {
      this.push(`<rect width="${W}" height="${H}" fill="#FCFBF7"/>`);
      let grid = '';
      for (let gx = 40; gx < W; gx += 40) {
        grid += `<line x1="${gx}" y1="0" x2="${gx}" y2="${H}" stroke="#E9EFF5" stroke-width="1"/>`;
      }
      for (let gy = 40; gy < H; gy += 40) {
        grid += `<line x1="0" y1="${gy}" x2="${W}" y2="${gy}" stroke="#E9EFF5" stroke-width="1"/>`;
      }
      this.push(grid);
      this.push(`<line x1="34" y1="0" x2="34" y2="${H}" stroke="#F1C5C5" stroke-width="1.6"/>`);
      for (const py of [150, 500, 850]) {
        this.push(`<circle cx="17" cy="${py}" r="8" fill="#E5E7EB" stroke="#CBD5E1" stroke-width="1.2"/>`);
      }
      this.push(
        this.roughPaths(
          this.gen.rectangle(48, 16, 198, 40, {
            fill: '#FEF3C7',
            fillStyle: 'hachure',
            hachureAngle: -25,
            hachureGap: 6,
            stroke: '#B45309',
            strokeWidth: 1.8,
          })
        )
      );
      this.text(147, 43, `CROQUIS PAPIER ${plateNum}`, {
        size: 18.5,
        color: '#92400E',
        anchor: 'middle',
      });
      this.text(262, 41, title, { size: 26, color: '#1E293B' });
      this.text(262, 67, subtitle, { size: 16.5, color: '#475569' });
      this.push(
        this.roughPaths(
          this.gen.rectangle(W - 462, 14, 432, 56, {
            fill: '#EFF6FF',
            fillStyle: 'solid',
            stroke: '#1D4ED8',
            strokeWidth: 1.6,
          })
        )
      );
      this.text(W - 448, 37, `Rôle : ${roleTag}`, { size: 16.5, color: '#1E3A8A' });
      this.text(W - 448, 59, `HTA : ${htaTag}`, { size: 15.5, color: '#1D4ED8' });
      this.push(
        this.roughPaths(this.gen.line(48, 82, W - 30, 82, { stroke: '#334155', strokeWidth: 1.8 }))
      );
      this.text(48, H - 15, `Prototype papier basse fidélité · Plateforme d'apprentissage v0.14.4 · Planche ${plateNum}`, {
        size: 15,
        color: '#64748B',
      });
    } else {
      this.push(`<rect width="${W}" height="${H}" fill="#F4F5F7"/>`);
      this.push(`<rect x="18" y="14" width="${W - 36}" height="${H - 28}" rx="8" fill="#FFFFFF" stroke="#CBD5E1" stroke-width="1.5"/>`);
      this.push(`<rect x="18" y="14" width="${W - 36}" height="64" rx="8" fill="#F8FAFC"/>`);
      this.push(`<line x1="18" y1="78" x2="${W - 18}" y2="78" stroke="#CBD5E1" stroke-width="1.5"/>`);
      this.push(`<rect x="34" y="26" width="184" height="38" rx="6" fill="#1E293B"/>`);
      this.text(126, 51, `WIREFRAME ${plateNum}`, {
        size: 14.5,
        bold: true,
        color: '#FFFFFF',
        anchor: 'middle',
      });
      this.text(234, 43, title, { size: 20, bold: true, color: '#0F172A' });
      this.text(234, 65, subtitle, { size: 13.5, color: '#475569' });
      this.push(`<rect x="${W - 462}" y="20" width="430" height="50" rx="6" fill="#F1F5F9" stroke="#CBD5E1" stroke-width="1.2"/>`);
      this.text(W - 448, 40, `Rôle : ${roleTag}`, { size: 12.5, bold: true, color: '#1E293B' });
      this.text(W - 448, 59, `HTA : ${htaTag}`, { size: 12, color: '#475569' });
      this.text(36, H - 22, `Maquette filaire basse fidélité (niveaux de gris) · Plateforme d'apprentissage v0.14.4 · Planche ${plateNum}`, {
        size: 12.5,
        color: '#64748B',
      });
    }
  }

  font(bold = false, mono = false) {
    if (mono) return 'JetBrains Mono, DejaVu Sans Mono, monospace';
    if (this.isSketch) return 'Patrick Hand, DejaVu Sans, sans-serif';
    return 'DejaVu Sans, sans-serif';
  }

  text(x, y, txt, opts = {}) {
    const {
      size = 18,
      bold = false,
      mono = false,
      color = this.isSketch ? '#1E293B' : '#111827',
      anchor = 'start',
      italic = false,
    } = opts;
    const weight = !this.isSketch && bold ? ' font-weight="700"' : '';
    const style = italic ? ' font-style="italic"' : '';
    this.push(
      `<text x="${x}" y="${y}" font-family="${this.font(bold, mono)}" font-size="${size}"${weight}${style} fill="${color}" text-anchor="${anchor}">${esc(
        txt
      )}</text>`
    );
  }

  multiText(x, y, txt, maxW, size = 16, lineH = 21, opts = {}) {
    const factor = opts.mono ? 0.62 : this.isSketch ? 0.45 : opts.bold ? 0.59 : 0.54;
    const lines = wrapLines(txt, maxW, size, factor);
    lines.forEach((ln, idx) => {
      this.text(x, y + idx * lineH, ln, { ...opts, size });
    });
    return lines.length * lineH;
  }

  box(x, y, w, h, opts = {}) {
    const {
      fill = '#FFFFFF',
      stroke = this.isSketch ? '#334155' : '#4B5563',
      strokeWidth = 1.6,
      rx = 6,
      dashed = false,
      hachure = false,
      hachureColor = '#E2E8F0',
      roughness = 1.3,
    } = opts;
    if (this.isSketch) {
      const f = hachure ? hachureColor : fill;
      const fs = hachure ? 'hachure' : fill === 'none' ? undefined : 'solid';
      const d = this.gen.rectangle(x, y, w, h, {
        fill: fs ? f : undefined,
        fillStyle: fs || 'solid',
        hachureGap: 7,
        hachureAngle: -38,
        stroke,
        strokeWidth,
        roughness,
        strokeLineDash: dashed ? [8, 6] : undefined,
      });
      this.push(this.roughPaths(d));
    } else {
      const dashAttr = dashed ? ' stroke-dasharray="6,4"' : '';
      this.push(
        `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="${rx}" fill="${fill}" stroke="${stroke}" stroke-width="${strokeWidth}"${dashAttr}/>`
      );
    }
  }

  line(x1, y1, x2, y2, opts = {}) {
    const { stroke = '#475569', strokeWidth = 1.5, dashed = false } = opts;
    if (this.isSketch) {
      this.push(
        this.roughPaths(
          this.gen.line(x1, y1, x2, y2, {
            stroke,
            strokeWidth,
            strokeLineDash: dashed ? [7, 5] : undefined,
          })
        )
      );
    } else {
      const d = dashed ? ' stroke-dasharray="5,4"' : '';
      this.push(
        `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${stroke}" stroke-width="${strokeWidth}"${d}/>`
      );
    }
  }

  circle(cx, cy, r, opts = {}) {
    const { fill = '#FFFFFF', stroke = '#334155', strokeWidth = 1.5 } = opts;
    if (this.isSketch) {
      this.push(
        this.roughPaths(
          this.gen.circle(cx, cy, r * 2, {
            fill: fill === 'none' ? undefined : fill,
            fillStyle: 'solid',
            stroke,
            strokeWidth,
          })
        )
      );
    } else {
      this.push(
        `<circle cx="${cx}" cy="${cy}" r="${r}" fill="${fill}" stroke="${stroke}" stroke-width="${strokeWidth}"/>`
      );
    }
  }

  crossPlaceholder(x, y, w, h, label = '') {
    this.box(x, y, w, h, {
      fill: this.isSketch ? '#F8FAFC' : '#F3F4F6',
      stroke: '#64748B',
      hachure: this.isSketch,
      hachureColor: '#E2E8F0',
    });
    this.line(x, y, x + w, y + h, { stroke: '#94A3B8', strokeWidth: 1.2 });
    this.line(x + w, y, x, y + h, { stroke: '#94A3B8', strokeWidth: 1.2 });
    if (label) {
      this.text(x + w / 2, y + h / 2 + 5, label, {
        size: this.isSketch ? 15 : 12.5,
        color: '#475569',
        anchor: 'middle',
      });
    }
  }

  input(x, y, w, h, label, placeholder, opts = {}) {
    let topY = y;
    if (label) {
      this.text(x, y + 14, label, {
        size: this.isSketch ? 17.5 : 13.5,
        bold: true,
        color: this.isSketch ? '#1E293B' : '#1F2937',
      });
      topY += 22;
    }
    this.box(x, topY, w, h, {
      fill: '#FFFFFF',
      stroke: this.isSketch ? '#334155' : '#6B7280',
      rx: 6,
    });
    let tx = x + 12;
    if (opts.icon) {
      this.icon(opts.icon, x + 20, topY + h / 2, 16, '#64748B');
      tx = x + 38;
    }
    if (placeholder) {
      this.text(tx, topY + h / 2 + 5, placeholder, {
        size: this.isSketch ? 16.5 : 13,
        color: '#6B7280',
        italic: !this.isSketch,
      });
    }
    if (opts.eye) {
      this.icon('eye', x + w - 24, topY + h / 2, 16, '#475569');
    }
    if (opts.dropdown) {
      this.text(x + w - 16, topY + h / 2 + 5, 'v', {
        size: 12.5,
        bold: true,
        color: '#475569',
        anchor: 'middle',
      });
    }
    return topY + h;
  }

  button(x, y, w, h, label, variant = 'secondary') {
    let fill = '#FFFFFF';
    let stroke = this.isSketch ? '#334155' : '#4B5563';
    let txtColor = this.isSketch ? '#1E293B' : '#111827';
    let hachure = false;
    let hachureColor = '#E2E8F0';
    let dashed = false;

    if (variant === 'primary') {
      if (this.isSketch) {
        fill = '#DBEAFE';
        hachure = true;
        hachureColor = '#BFDBFE';
        stroke = '#1D4ED8';
        txtColor = '#1E3A8A';
      } else {
        fill = '#374151';
        stroke = '#1F2937';
        txtColor = '#FFFFFF';
      }
    } else if (variant === 'danger') {
      if (this.isSketch) {
        fill = '#FEE2E2';
        hachure = true;
        hachureColor = '#FECACA';
        stroke = '#B91C1C';
        txtColor = '#991B1B';
      } else {
        fill = '#F3F4F6';
        stroke = '#111827';
        dashed = true;
      }
    } else if (variant === 'disabled') {
      fill = '#F3F4F6';
      stroke = '#9CA3AF';
      txtColor = '#9CA3AF';
      dashed = true;
    } else if (variant === 'link') {
      const sz = this.isSketch ? 17 : 13;
      this.text(x, y + h / 2 + 5, label, {
        size: sz,
        color: this.isSketch ? '#1D4ED8' : '#374151',
        bold: true,
      });
      const lw = label.length * sz * (this.isSketch ? 0.44 : 0.56);
      this.line(x, y + h / 2 + 9, x + lw, y + h / 2 + 9, {
        stroke: this.isSketch ? '#1D4ED8' : '#6B7280',
        strokeWidth: 1.2,
      });
      return;
    }

    this.box(x, y, w, h, {
      fill,
      stroke,
      hachure,
      hachureColor,
      dashed,
      rx: 6,
      strokeWidth: variant === 'primary' ? 2 : 1.5,
    });
    this.text(x + w / 2, y + h / 2 + 5, label, {
      size: this.isSketch ? 16.5 : 13,
      bold: variant === 'primary',
      color: txtColor,
      anchor: 'middle',
    });
  }

  checkbox(x, y, label, checked = false, sub = '') {
    this.box(x, y, 18, 18, { fill: '#FFFFFF', stroke: '#374151', rx: 3 });
    if (checked) {
      this.line(x + 3, y + 9, x + 7, y + 14, { stroke: '#111827', strokeWidth: 2.2 });
      this.line(x + 7, y + 14, x + 15, y + 4, { stroke: '#111827', strokeWidth: 2.2 });
    }
    this.text(x + 26, y + 15, label, { size: this.isSketch ? 16.5 : 13 });
    if (sub) {
      this.text(x + 26, y + 32, sub, { size: this.isSketch ? 14.5 : 11.5, color: '#64748B' });
    }
  }

  radio(x, y, label, selected = false) {
    this.circle(x + 9, y + 9, 8, { fill: '#FFFFFF', stroke: '#374151', strokeWidth: 1.6 });
    if (selected) {
      this.circle(x + 9, y + 9, 4, { fill: '#1F2937', stroke: '#1F2937' });
    }
    this.text(x + 26, y + 14, label, { size: this.isSketch ? 16 : 13 });
  }

  badge(x, y, w, h, label, kind = 'neutral') {
    let fill = '#F3F4F6';
    let stroke = '#6B7280';
    let color = '#1F2937';
    if (this.isSketch) {
      if (kind === 'accent') { fill = '#DBEAFE'; stroke = '#1D4ED8'; color = '#1E3A8A'; }
      if (kind === 'warn') { fill = '#FEF3C7'; stroke = '#B45309'; color = '#92400E'; }
      if (kind === 'danger') { fill = '#FEE2E2'; stroke = '#B91C1C'; color = '#991B1B'; }
      if (kind === 'ok') { fill = '#DCFCE7'; stroke = '#15803D'; color = '#166534'; }
    } else {
      if (kind === 'accent') { fill = '#E2E8F0'; stroke = '#334155'; color = '#0F172A'; }
      if (kind === 'danger') { fill = '#E5E7EB'; stroke = '#111827'; color = '#111827'; }
    }
    this.box(x, y, w, h, { fill, stroke, rx: 10, strokeWidth: 1.3 });
    this.text(x + w / 2, y + h / 2 + 5, label, {
      size: this.isSketch ? 14 : 11.5,
      bold: true,
      color,
      anchor: 'middle',
    });
  }

  banner(x, y, w, h, kind, text) {
    let fill = '#F8FAFC';
    let stroke = '#475569';
    let sym = 'i';
    if (this.isSketch) {
      if (kind === 'ok') { fill = '#F0FDF4'; stroke = '#15803D'; sym = 'v'; }
      else if (kind === 'warn') { fill = '#FFFBEB'; stroke = '#B45309'; sym = '!'; }
      else if (kind === 'err') { fill = '#FEF2F2'; stroke = '#B91C1C'; sym = 'x'; }
      else if (kind === 'load') { fill = '#EFF6FF'; stroke = '#1D4ED8'; sym = '@'; }
    } else {
      fill = kind === 'err' || kind === 'warn' ? '#F3F4F6' : '#F9FAFB';
      stroke = '#374151';
      sym = kind === 'ok' ? '✓' : kind === 'err' ? '✕' : kind === 'warn' ? '⚠' : kind === 'load' ? '↻' : 'ℹ';
    }
    this.box(x, y, w, h, { fill, stroke, dashed: !this.isSketch && kind !== 'ok', rx: 6, strokeWidth: 1.6 });
    this.circle(x + 19, y + h / 2, 10, { fill: '#FFFFFF', stroke, strokeWidth: 1.4 });
    this.text(x + 19, y + h / 2 + 5, sym, { size: 12.5, bold: true, color: stroke, anchor: 'middle' });
    const sz = this.isSketch ? 14.5 : 12;
    const factor = this.isSketch ? 0.45 : 0.54;
    const lines = wrapLines(text, w - 50, sz, factor);
    if (lines.length <= 1) {
      this.text(x + 36, y + h / 2 + 5, lines[0] ?? '', {
        size: sz,
        color: this.isSketch ? stroke : '#1F2937',
      });
    } else {
      lines.forEach((ln, idx) => {
        this.text(x + 36, y + 17 + idx * 15.5, ln, {
          size: sz,
          color: this.isSketch ? stroke : '#1F2937',
        });
      });
    }
  }

  icon(name, cx, cy, sz = 16, stroke = '#334155') {
    const r = sz / 2;
    const p = (d) =>
      `<path d="${d}" fill="none" stroke="${stroke}" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round"/>`;
    switch (name) {
      case 'wifi':
        this.push(
          p(`M${cx - r} ${cy - 2} Q${cx} ${cy - r - 2} ${cx + r} ${cy - 2}`) +
            p(`M${cx - r * 0.65} ${cy + 2} Q${cx} ${cy - r * 0.45} ${cx + r * 0.65} ${cy + 2}`) +
            `<circle cx="${cx}" cy="${cy + 6}" r="1.6" fill="${stroke}"/>`
        );
        break;
      case 'mail':
        this.push(p(`M${cx - r} ${cy - r * 0.65} h${sz} v${sz * 0.72} h-${sz} Z M${cx - r} ${cy - r * 0.65} L${cx} ${cy + 1} L${cx + r} ${cy - r * 0.65}`));
        break;
      case 'lock':
        this.push(p(`M${cx - r * 0.7} ${cy - 1} h${sz * 0.7} v${sz * 0.55} h-${sz * 0.7} Z M${cx - r * 0.4} ${cy - 1} V${cy - r * 0.6} A4 4 0 0 1 ${cx + r * 0.4} ${cy - r * 0.6} V${cy - 1}`));
        break;
      case 'eye':
        this.push(
          p(`M${cx - r} ${cy} Q${cx} ${cy - r * 0.8} ${cx + r} ${cy} Q${cx} ${cy + r * 0.8} ${cx - r} ${cy} Z`) +
            `<circle cx="${cx}" cy="${cy}" r="2.5" fill="none" stroke="${stroke}" stroke-width="1.5"/>`
        );
        break;
      case 'hamburger':
        this.push(p(`M${cx - r} ${cy - 4} h${sz} M${cx - r} ${cy} h${sz} M${cx - r} ${cy + 4} h${sz}`));
        break;
      case 'shield':
        this.push(p(`M${cx} ${cy - r} L${cx + r * 0.8} ${cy - r * 0.5} V${cy + 1} Q${cx} ${cy + r} ${cx} ${cy + r} Q${cx} ${cy + r} ${cx - r * 0.8} ${cy + 1} V${cy - r * 0.5} Z`));
        break;
      case 'book':
        this.push(p(`M${cx - r} ${cy - r * 0.7} Q${cx - r * 0.4} ${cy - r} ${cx} ${cy - r * 0.5} Q${cx + r * 0.4} ${cy - r} ${cx + r} ${cy - r * 0.7} V${cy + r * 0.7} Q${cx + r * 0.4} ${cy + r * 0.4} ${cx} ${cy + r * 0.8} Q${cx - r * 0.4} ${cy + r * 0.4} ${cx - r} ${cy + r * 0.7} Z M${cx} ${cy - r * 0.5} V${cy + r * 0.8}`));
        break;
      case 'user':
        this.push(
          `<circle cx="${cx}" cy="${cy - r * 0.45}" r="${r * 0.38}" fill="none" stroke="${stroke}" stroke-width="1.6"/>` +
            p(`M${cx - r * 0.75} ${cy + r * 0.85} C${cx - r * 0.75} ${cy + r * 0.1} ${cx + r * 0.75} ${cy + r * 0.1} ${cx + r * 0.75} ${cy + r * 0.85}`)
        );
        break;
      case 'power':
        this.push(p(`M${cx} ${cy - r * 0.85} V${cy} M${cx - r * 0.55} ${cy - r * 0.45} A${r * 0.72} ${r * 0.72} 0 1 0 ${cx + r * 0.55} ${cy - r * 0.45}`));
        break;
      default:
        this.circle(cx, cy, r * 0.7, { fill: 'none', stroke });
    }
  }

  arrow(x1, y1, x2, y2, opts = {}) {
    const { color = this.isSketch ? '#1D4ED8' : '#334155', strokeWidth = 1.8, curved = false, label = '', dashed = false, labelDy = -8 } = opts;
    if (curved) {
      const mx = (x1 + x2) / 2;
      const my = Math.min(y1, y2) - 22;
      this.overlay(`<path d="M${x1} ${y1} Q${mx} ${my} ${x2} ${y2}" fill="none" stroke="${color}" stroke-width="${strokeWidth}" ${dashed ? 'stroke-dasharray="6,4"' : ''}/>`);
    } else if (this.isSketch) {
      this.overlay(this.roughPaths(this.gen.line(x1, y1, x2, y2, { stroke: color, strokeWidth, strokeLineDash: dashed ? [6, 5] : undefined })));
    } else {
      this.overlay(`<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" stroke="${color}" stroke-width="${strokeWidth}" ${dashed ? 'stroke-dasharray="5,4"' : ''}/>`);
    }
    const angle = Math.atan2(y2 - (curved ? Math.min(y1, y2) - 22 : y1), x2 - (curved ? (x1 + x2) / 2 : x1));
    const a1 = angle + Math.PI * 0.82;
    const a2 = angle - Math.PI * 0.82;
    const h = 9;
    this.overlay(
      `<polygon points="${x2},${y2} ${x2 + h * Math.cos(a1)},${y2 + h * Math.sin(a1)} ${x2 + h * Math.cos(a2)},${y2 + h * Math.sin(a2)}" fill="${color}"/>`
    );
    if (label) {
      const lx = (x1 + x2) / 2;
      const ly = (y1 + y2) / 2 + labelDy;
      const tw = label.length * (this.isSketch ? 7.4 : 6.6) + 12;
      this.overlay(`<rect x="${lx - tw / 2}" y="${ly - 13}" width="${tw}" height="18" rx="4" fill="${this.isSketch ? '#FCFBF7' : '#FFFFFF'}" opacity="0.94"/>`);
      this.overlay(
        `<text x="${lx}" y="${ly + 1}" text-anchor="middle" font-family="${this.font(true)}" font-size="${this.isSketch ? 14.5 : 11.5}" fill="${color}">${esc(label)}</text>`
      );
    }
  }

  pin(cx, cy, num, color = null) {
    if (this.isSketch) {
      const c = color || '#B91C1C';
      this.overlay(this.roughPaths(this.gen.circle(cx, cy, 24, { fill: '#FEF08A', fillStyle: 'solid', stroke: c, strokeWidth: 2 })));
      this.overlay(
        `<text x="${cx}" y="${cy + 5.5}" text-anchor="middle" font-family="${this.font(true)}" font-size="16" fill="${c}">${num}</text>`
      );
    } else {
      const c = color || '#1E293B';
      this.overlay(`<circle cx="${cx}" cy="${cy}" r="11.5" fill="${c}" stroke="#FFFFFF" stroke-width="1.8"/>`);
      this.overlay(
        `<text x="${cx}" y="${cy + 4}" text-anchor="middle" font-family="DejaVu Sans" font-weight="700" font-size="11.5" fill="#FFFFFF">${num}</text>`
      );
    }
  }

  table(x, y, w, cols, rows, rowH = 34) {
    const hdrH = 30;
    this.box(x, y, w, hdrH, { fill: '#E5E7EB', stroke: '#4B5563', rx: 4 });
    let cx = x;
    for (const col of cols) {
      this.text(cx + 8, y + 20, col.label, { size: this.isSketch ? 14.5 : 11.5, bold: true });
      cx += col.w;
      if (cx < x + w - 4) this.line(cx, y, cx, y + hdrH + rows.length * rowH, { stroke: '#CBD5E1', strokeWidth: 1 });
    }
    let ry = y + hdrH;
    for (const r of rows) {
      const selected = !!r._selected;
      this.box(x, ry, w, rowH, {
        fill: selected ? (this.isSketch ? '#DBEAFE' : '#E2E8F0') : '#FFFFFF',
        stroke: '#94A3B8',
        rx: 0,
        strokeWidth: 1,
      });
      let cellX = x;
      cols.forEach((col, i) => {
        const val = r.cells[i] ?? '';
        if (val === '[x]' || val === '[ ]') {
          this.box(cellX + 10, ry + 8, 15, 15, { fill: '#FFFFFF', stroke: '#374151', rx: 2 });
          if (val === '[x]') {
            this.line(cellX + 12, ry + 15, cellX + 16, ry + 20, { stroke: '#111827', strokeWidth: 2 });
            this.line(cellX + 16, ry + 20, cellX + 23, ry + 10, { stroke: '#111827', strokeWidth: 2 });
          }
        } else {
          this.text(cellX + 8, ry + 21, val, {
            size: this.isSketch ? 14 : 11.5,
            bold: selected && i === 0,
            color: r._muted ? '#64748B' : '#111827',
          });
        }
        cellX += col.w;
      });
      ry += rowH;
    }
    return ry;
  }

  annotationsPanel(items, meta) {
    const px = 1105;
    const py = 96;
    const pw = 425;

      if (this.isSketch) {
      this.text(px, py + 16, 'Notes papier & Cinématique :', { size: 22, color: '#1E3A8A' });
      let cy = py + 30;
      for (const it of items) {
        const lines = wrapLines(it.desc, pw - 42, 16, 0.49);
        const cardH = Math.max(74, 38 + lines.length * 19.5);
        const bg = it.kind === 'rule' ? '#FEE2E2' : it.kind === 'flow' ? '#DBEAFE' : '#FEF9C3';
        const st = it.kind === 'rule' ? '#B91C1C' : it.kind === 'flow' ? '#1D4ED8' : '#CA8A04';
        this.box(px, cy, pw, cardH, { fill: bg, stroke: st, strokeWidth: 1.6 });
        this.box(px + pw / 2 - 26, cy - 5, 52, 11, { fill: '#FFFFFF', stroke: '#94A3B8', strokeWidth: 1 });
        this.text(px + 14, cy + 24, `${it.num} · ${it.title}`, { size: 17.5, color: st === '#CA8A04' ? '#854D0E' : st });
        this.multiText(px + 14, cy + 45, it.desc, pw - 40, 15.5, 19, { color: '#1E293B' });
        cy += cardH + 11;
      }
      if (cy < 860) {
        const mh = Math.min(126, 956 - cy);
        this.box(px, cy, pw, mh, { fill: '#FFFFFF', stroke: '#475569', dashed: true });
        this.text(px + 12, cy + 23, 'Règles & Transitions :', { size: 16.5, color: '#0F172A' });
        this.multiText(px + 12, cy + 44, `${meta.rules}\n-> ${meta.flow}`, pw - 34, 14.5, 18.5, { color: '#334155' });
      }
    } else {
      this.box(px, py, pw, 860, { fill: '#F8FAFC', stroke: '#CBD5E1', rx: 6 });
      this.box(px, py, pw, 36, { fill: '#1E293B', stroke: '#1E293B', rx: 6 });
      this.text(px + 14, py + 23, 'LÉGENDE IHM & SPÉCIFICATIONS', { size: 13.5, bold: true, color: '#FFFFFF' });

      let cy = py + 54;
      for (const it of items) {
        this.pin(px + 20, cy - 4, it.num);
        this.text(px + 40, cy, it.title, { size: 13.5, bold: true, color: '#0F172A' });
        const hUsed = this.multiText(px + 40, cy + 18, it.desc, pw - 54, 12, 16, { color: '#374151' });
        cy += Math.max(40, 22 + hUsed) + 6;
        this.line(px + 12, cy - 4, px + pw - 12, cy - 4, { stroke: '#E2E8F0', strokeWidth: 1 });
        cy += 6;
      }

      const boxY = Math.max(cy + 4, 728);
      const boxH = 956 - boxY;
      this.box(px + 10, boxY, pw - 20, boxH, { fill: '#FFFFFF', stroke: '#64748B', rx: 4 });
      this.text(px + 22, boxY + 22, 'Exigences IHM & Règles métier :', { size: 12.2, bold: true, color: '#0F172A' });
      const rH = this.multiText(px + 22, boxY + 40, meta.rules, pw - 44, 11.5, 15.5, { color: '#334155' });
      const fy = boxY + 46 + rH;
      if (fy + 30 < 950) {
        this.text(px + 22, fy + 10, 'Cinématique / Transitions :', { size: 12.2, bold: true, color: '#0F172A' });
        this.multiText(px + 22, fy + 28, meta.flow, pw - 44, 11.5, 15.5, { color: '#334155' });
      }
    }
  }

  appFrame(x, y, w, h, screenTitle, role, activeRoute, activeLang = 'Python', opts = {}) {
    const { showSidebar = true, sidebarCollapsed = false } = opts;
    this.box(x, y, w, h, { fill: '#FFFFFF', stroke: this.isSketch ? '#1E293B' : '#334155', strokeWidth: 2, rx: 10 });
    const tbH = 44;
    this.box(x, y, w, tbH, {
      fill: this.isSketch ? '#F1F5F9' : '#F3F4F6',
      stroke: this.isSketch ? '#334155' : '#4B5563',
      rx: 8,
    });
    for (let i = 0; i < 3; i++) {
      this.circle(x + 18 + i * 18, y + 22, 5.5, {
        fill: this.isSketch ? ['#FCA5A5', '#FDE047', '#86EFAC'][i] : '#FFFFFF',
        stroke: '#475569',
        strokeWidth: 1.2,
      });
    }
    let cx = x + 78;
    if (showSidebar) {
      this.box(cx, y + 10, 26, 24, { fill: '#FFFFFF', stroke: '#475569', rx: 4 });
      this.icon('hamburger', cx + 13, y + 22, 13, '#334155');
      cx += 36;
    }
    this.box(cx, y + 10, 24, 24, { fill: '#FFFFFF', stroke: '#94A3B8', rx: 4 });
    this.text(cx + 12, y + 27, '<', { size: 13, bold: true, anchor: 'middle', color: '#475569' });
    this.box(cx + 28, y + 10, 24, 24, { fill: '#FFFFFF', stroke: '#94A3B8', rx: 4 });
    this.text(cx + 40, y + 27, '>', { size: 13, bold: true, anchor: 'middle', color: '#94A3B8' });
    cx += 66;
    this.text(cx, y + 28, screenTitle, { size: this.isSketch ? 18 : 14.5, bold: true });

    this.box(x + w - 186, y + 10, 28, 24, { fill: '#FFFFFF', stroke: '#64748B', rx: 4 });
    this.text(x + w - 172, y + 27, 'R', { size: 12, bold: true, anchor: 'middle', color: '#475569' });
    if (showSidebar) {
      this.box(x + w - 152, y + 10, 28, 24, { fill: '#FFFFFF', stroke: '#64748B', rx: 4 });
      this.text(x + w - 138, y + 27, '◐', { size: 13, anchor: 'middle', color: '#475569' });
    }
    this.box(x + w - 118, y + 10, 104, 24, { fill: '#FFFFFF', stroke: '#475569', rx: 4 });
    this.text(x + w - 66, y + 26, 'Aide (F1)', { size: 12, bold: true, anchor: 'middle', color: '#334155' });

    if (!showSidebar) {
      return { cx: x + 16, cy: y + tbH + 14, cw: w - 32, ch: h - tbH - 28 };
    }

    const sbW = sidebarCollapsed ? 68 : 206;
    const sbY = y + tbH;
    const sbH = h - tbH;
    this.box(x, sbY, sbW, sbH, {
      fill: this.isSketch ? '#F8FAFC' : '#F9FAFB',
      stroke: '#94A3B8',
      hachure: false,
      rx: 0,
    });

    const entries =
      role === 'Apprenant'
        ? [
            { sec: 'APPRENDRE' },
            { id: 'parcours', label: 'Parcours' },
            { id: 'cours', label: 'Cours' },
            { id: 'exercice', label: 'Exercice' },
            { id: 'resultat', label: 'Mes copies' },
            { id: 'bord', label: 'Tableau de bord' },
            { sec: 'PARCOURS' },
            { id: 'lang:Java', label: 'Ja  Java' },
            { id: 'lang:Python', label: 'Py  Python' },
            { id: 'lang:C++', label: 'C++ C++' },
          ]
        : role === 'Enseignant'
        ? [
            { sec: 'ENSEIGNER' },
            { id: 'conditions', label: 'Mes niveaux' },
            { id: 'resultats', label: 'Mes apprenants' },
            { sec: 'PARCOURS' },
            { id: 'lang:Java', label: 'Ja  Java' },
            { id: 'lang:Python', label: 'Py  Python' },
            { id: 'lang:C++', label: 'C++ C++' },
          ]
        : [
            { sec: 'ADMINISTRATION' },
            { id: 'comptes', label: 'Comptes et rôles' },
          ];

    const compact = sidebarCollapsed && h < 450;
    let iy = sbY + (compact ? 10 : 18);
    for (const e of entries) {
      if (e.sec) {
        if (!sidebarCollapsed) {
          this.text(x + 14, iy + 12, e.sec, { size: 11, bold: true, color: '#64748B' });
        }
        iy += sidebarCollapsed ? (compact ? 4 : 10) : 22;
        continue;
      }
      const isLang = e.id.startsWith('lang:');
      const active = e.id === activeRoute || (isLang && e.id === `lang:${activeLang}`);
      const itemW = sbW - 18;
      const itemH = compact ? 24 : 32;
      if (active) {
        this.box(x + 9, iy, itemW, itemH, {
          fill: this.isSketch ? '#DBEAFE' : '#E5E7EB',
          stroke: this.isSketch ? '#1D4ED8' : '#374151',
          rx: 6,
          strokeWidth: 1.6,
        });
      }
      if (sidebarCollapsed) {
        this.box(x + 17, iy + 2, 34, compact ? 20 : 26, { fill: '#FFFFFF', stroke: '#64748B', rx: 5 });
        this.text(x + 34, iy + (compact ? 16 : 21), e.label.slice(0, 2), { size: compact ? 10.5 : 12, bold: true, anchor: 'middle' });
      } else {
        this.text(x + 18, iy + 21, e.label + (isLang && active ? '  ✓' : ''), {
          size: this.isSketch ? 15.5 : 12.5,
          bold: active,
          color: active && this.isSketch ? '#1E3A8A' : '#111827',
        });
      }
      iy += compact ? 27 : 36;
    }

    const py = y + h - 54;
    this.line(x + 8, py - 6, x + sbW - 8, py - 6, { stroke: '#CBD5E1', strokeWidth: 1 });
    this.circle(x + 26, py + 22, 14, { fill: '#E2E8F0', stroke: '#475569' });
    this.text(x + 26, py + 27, role.slice(0, 2).toUpperCase(), { size: 11, bold: true, anchor: 'middle' });
    if (!sidebarCollapsed) {
      this.text(x + 48, py + 18, 'Utilisateur', { size: 13.5, bold: true });
      this.text(x + 48, py + 35, role, { size: 11.5, color: '#64748B' });
      this.icon('power', x + sbW - 22, py + 22, 14, '#B91C1C');
    }

    return {
      cx: x + sbW + 18,
      cy: y + tbH + 14,
      cw: w - sbW - 36,
      ch: h - tbH - 28,
    };
  }

  renderSvg() {
    return `<svg xmlns="http://www.w3.org/2000/svg" width="${W}" height="${H}" viewBox="0 0 ${W} ${H}">
${this.els.join('\n')}
${this.overlays.join('\n')}
</svg>`;
  }

  save(slug) {
    const svg = this.renderSvg();
    const dir = this.isSketch ? DIR_SKETCH : DIR_WIRE;
    const svgPath = path.join(dir, `${slug}.svg`);
    const pngPath = path.join(dir, `${slug}.png`);
    fs.writeFileSync(svgPath, svg, 'utf8');
    const resvg = new Resvg(svg, {
      font: {
        fontFiles: FONT_FILES,
        loadSystemFonts: false,
      },
    });
    fs.writeFileSync(pngPath, resvg.render().asPng());
    return { svgPath, pngPath };
  }
}

// ---------------------------------------------------------------------------
// Définition des 12 planches (00 à 11)
// ---------------------------------------------------------------------------
const PLATES = [
  // 00 — Cinématique globale
  {
    slug: '00-cinematique-navigation',
    num: '00/11',
    title: 'Cinématique globale des écrans (Storyboard)',
    subtitle: 'Flux complet PC1–PC4 : découverte, authentification, pédagogie et correction humaine',
    role: 'Tous (PC1, PC2, PC3, PC4)',
    hta: 'A0->A1->A2/A3/A4 · E1/E2 · AD1 · IA1 · UX1',
    meta: {
      rules: 'RG-01/02 (rôle vérifié par PC1) · RG-04 (PIN != OTP) · RG-19/20 (IA bloquée au test)',
      flow: 'Découverte PC1 -> Connexion/PIN/OTP -> Routage strict par rôle serveur',
    },
    annotations: [
      { num: 1, title: 'Entrée unique PC1 + Réseau', desc: 'Chaque client (--role) découvre PC1 sur le LAN sans saisir d’IP (secours manuel possible).', kind: 'flow' },
      { num: 2, title: 'Cycle Compte (A1 -> A0)', desc: 'Demande sans PIN -> Décision Bot/Admin -> PIN 8 chiffres -> Activation -> Connexion OTP / Confiance 48h.', kind: 'note' },
      { num: 3, title: 'Routage strict par rôle serveur', desc: 'PC1 vérifie le compte : Apprenant (Parcours), Enseignant (Résultats/Niveaux), Admin (Comptes).', kind: 'rule' },
      { num: 4, title: 'Chaîne pédagogique (A2->A3->A4)', desc: 'Cours & Entraînement (+IA) -> Test final (IA bloquée !) -> Soumission -> Correction humaine E2 -> Progression.', kind: 'rule' },
      { num: 5, title: 'Navigation transversale (UX1)', desc: 'Barre latérale réductible (244 <-> 76 px), historique < >, raccourcis F1/F6/F8/Échap.', kind: 'note' },
    ],
    draw(c) {
      const x = 44, y = 98, w = 1040, h = 856;
      c.box(x, y, w, h, { fill: '#FFFFFF', stroke: '#475569', rx: 8 });

      // Bandeau Haut : PC1 & Découverte
      c.box(x + 20, y + 16, w - 40, 84, { fill: c.isSketch ? '#EFF6FF' : '#F3F4F6', stroke: '#334155', dashed: true });
      c.text(x + 36, y + 44, 'POSTE CENTRAL PC1 (Serveur autoritaire : PostgreSQL + HTTPS + Découverte)', { size: c.isSketch ? 18 : 14, bold: true });
      c.text(x + 36, y + 68, 'Annonce IP LAN + Port HTTPS dynamique · Vérifie rôles, sessions, copies, quotas NVIDIA & SMTP', { size: c.isSketch ? 15 : 12, color: '#475569' });
      c.pin(x + w - 40, y + 42, 1);

      // Bloc A0 / A1 / E11
      const ax = x + 24, ay = y + 122, aw = 295, ah = 185;
      c.box(ax, ay, aw, ah, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 2 });
      c.text(ax + 14, ay + 28, 'E01 · Connexion & Réseau', { size: c.isSketch ? 17.5 : 13.5, bold: true });
      c.multiText(ax + 14, ay + 54, 'Découverte PC1 / IP manuelle\nE-mail + Mot de passe\nOTP 6 ch. ou Confiance 48h\nOublier poste / MDP oublié', aw - 28, c.isSketch ? 15 : 12, 22, { color: '#334155' });
      c.pin(ax + aw - 18, ay + 20, 2);

      const bx = x + 372, by = y + 122, bw = 295, bh = 185;
      c.box(bx, by, bw, bh, { fill: '#FFFFFF', stroke: '#475569' });
      c.text(bx + 14, by + 28, 'E02 · Inscription & PIN (A1)', { size: c.isSketch ? 17.5 : 13.5, bold: true });
      c.multiText(bx + 14, by + 54, 'Créer compte (sans PIN)\n-> EN_ATTENTE + Bot / Admin\n-> PIN 8 chiffres (48h)\n-> Activer mon compte', bw - 28, c.isSketch ? 15 : 12, 22, { color: '#334155' });

      const cx2 = x + 704, cy2 = y + 122, cw2 = 312, ch2 = 185;
      c.box(cx2, cy2, cw2, ch2, { fill: c.isSketch ? '#FEF3C7' : '#F9FAFB', stroke: '#64748B', dashed: true });
      c.text(cx2 + 14, cy2 + 28, 'E11 · Transversal & Accessibilité', { size: c.isSketch ? 17.5 : 13.5, bold: true });
      c.multiText(cx2 + 14, cy2 + 54, 'Barre titre (< > ≡ Thème Rétro F1)\nBarre latérale 244 px <-> 76 px\nF6 régions · F8 relire notification\nÉtats : Chargement / Vide / Erreur', cw2 - 28, c.isSketch ? 15 : 12, 22, { color: '#334155' });
      c.pin(cx2 + cw2 - 18, cy2 + 20, 5);

      c.arrow(ax + aw, ay + 68, bx, by + 68, { label: 'Créer', labelDy: -10 });
      c.arrow(bx, by + 132, ax + aw, ay + 132, { label: 'Actif', dashed: true, labelDy: 16 });

      // Ligne de routage rôle
      c.box(x + 24, y + 338, w - 48, 44, { fill: c.isSketch ? '#DBEAFE' : '#E2E8F0', stroke: '#1E293B' });
      c.text(x + w / 2, y + 366, 'ROUTAGE SERVEUR PAR RÔLE (PC1 vérifie le compte) : Apprenant (PC4) | Enseignant (PC3) | Admin (PC2)', {
        size: c.isSketch ? 15.5 : 12.5,
        bold: true,
        anchor: 'middle',
      });
      c.pin(x + 42, y + 360, 3);
      c.arrow(ax + 150, ay + ah, ax + 150, y + 338, { label: 'Session OK' });

      // Colonne 1 : Apprenant
      const lx = x + 24, ly = y + 412, lw = 445, lh = 424;
      c.box(lx, ly, lw, lh, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 1.8 });
      c.box(lx, ly, lw, 34, { fill: c.isSketch ? '#DBEAFE' : '#374151', stroke: '#1E293B' });
      c.text(lx + 14, ly + 23, 'PC4 · PARCOURS APPRENANT (A2 -> A3 -> A4)', {
        size: c.isSketch ? 15.5 : 12.5,
        bold: true,
        color: c.isSketch ? '#1E3A8A' : '#FFFFFF',
      });
      c.pin(lx + lw - 18, ly + 17, 4);

      c.box(lx + 14, ly + 50, 190, 68, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(lx + 24, ly + 72, 'E03 · Parcours', { size: 13.5, bold: true });
      c.multiText(lx + 24, ly + 92, 'Java | Python | C++\nNiveaux publiés', 170, 11.5, 16, { color: '#475569' });

      c.box(lx + 238, ly + 50, 192, 68, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(lx + 248, ly + 72, 'E04 · Cours (+IA)', { size: 13.5, bold: true });
      c.multiText(lx + 248, ly + 92, 'Blocs texte & code\n(lecture seule)', 170, 11.5, 16, { color: '#475569' });

      c.box(lx + 14, ly + 152, 190, 74, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(lx + 24, ly + 174, 'E05 · Entraînement', { size: 13.5, bold: true });
      c.multiText(lx + 24, ly + 194, 'Éditeur local + IA\n(sans exécution)', 170, 11.5, 16, { color: '#475569' });

      c.box(lx + 238, ly + 152, 192, 74, { fill: c.isSketch ? '#FEE2E2' : '#E5E7EB', stroke: '#111827', strokeWidth: 2 });
      c.text(lx + 248, ly + 174, 'E06 · Test final', { size: 13.5, bold: true, color: c.isSketch ? '#991B1B' : '#111827' });
      c.multiText(lx + 248, ly + 194, 'IA BLOQUÉE (RG-20)\nSoumettre / Abandon', 170, 11.5, 16, { color: c.isSketch ? '#991B1B' : '#374151' });

      c.box(lx + 14, ly + 260, 416, 74, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(lx + 24, ly + 283, 'E07 · Mes copies, décisions & Tableau de bord', { size: 13, bold: true });
      c.multiText(lx + 24, ly + 303, 'SOUMISE (attente) -> CORRIGEE (note, décision, commentaire publiés)', 395, 11.5, 16, { color: '#475569' });

      c.box(lx + 14, ly + 350, 416, 56, { fill: c.isSketch ? '#DCFCE7' : '#F3F4F6', stroke: '#475569', dashed: true });
      c.multiText(lx + 24, ly + 373, 'Si VALIDÉ et note >= seuil -> Débloque niveau N+1 (même langage)', 395, 12, 16, { bold: true });

      c.arrow(lx + 204, ly + 84, lx + 238, ly + 84);
      c.arrow(lx + 334, ly + 118, lx + 110, ly + 152);
      c.arrow(lx + 204, ly + 189, lx + 238, ly + 189);
      c.arrow(lx + 334, ly + 226, lx + 334, ly + 260, { label: 'SOUMISE' });

      // Colonne 2 : Enseignant
      const ex = x + 516, ey = y + 412, ew = 248, eh = 424;
      c.box(ex, ey, ew, eh, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 1.8 });
      c.box(ex, ey, ew, 34, { fill: c.isSketch ? '#DCFCE7' : '#374151', stroke: '#1E293B' });
      c.text(ex + 12, ey + 23, 'PC3 · ENSEIGNANT (E1, E2)', {
        size: c.isSketch ? 15.5 : 12.5,
        bold: true,
        color: c.isSketch ? '#166534' : '#FFFFFF',
      });
      c.box(ex + 14, ey + 52, ew - 28, 134, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(ex + 24, ey + 76, 'E08 · Mes niveaux (E1)', { size: 13, bold: true });
      c.multiText(ex + 24, ey + 98, '1. Créer brouillon\n2. Cours & Exercice\n3. Sujet du test\n4. Publier (complet)', ew - 48, 12, 18, { color: '#334155' });

      c.box(ex + 14, ey + 216, ew - 28, 164, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(ex + 24, ey + 240, 'E09 · Corrections (E2)', { size: 13, bold: true });
      c.multiText(ex + 24, ey + 262, 'Copies des affectés\nNote /20 + commentaire\nBrouillon privé\nPublier décision -> A4', ew - 48, 12, 18, { color: '#334155' });
      c.arrow(ex + ew / 2, ey + 186, ex + ew / 2, ey + 216);

      // Colonne 3 : Admin
      const adx = x + 782, ady = y + 412, adw = 234, adh = 424;
      c.box(adx, ady, adw, adh, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 1.8 });
      c.box(adx, ady, adw, 34, { fill: c.isSketch ? '#FEF3C7' : '#374151', stroke: '#1E293B' });
      c.text(adx + 12, ady + 23, 'PC2 · ADMIN (AD1)', {
        size: c.isSketch ? 15.5 : 12.5,
        bold: true,
        color: c.isSketch ? '#92400E' : '#FFFFFF',
      });
      c.box(adx + 12, ady + 52, adw - 24, 202, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(adx + 22, ady + 76, 'E10 · Comptes & rôles', { size: 13, bold: true });
      c.multiText(adx + 22, ady + 98, 'Filtres SQL + page 50\nAvis du Bot de tri\nLots bornés (<= 20)\nCréer / Activer / PIN\nJournal d’audit', adw - 42, 12, 19, { color: '#334155' });

      c.box(adx + 12, ady + 276, adw - 24, 104, { fill: '#F9FAFB', stroke: '#475569' });
      c.text(adx + 22, ady + 300, 'Affectations (BF-A08)', { size: 13, bold: true });
      c.multiText(adx + 22, ady + 322, '1 Apprenant -> 1 Ens.\nBloqué si copie ouverte', adw - 42, 12, 18, { color: '#334155' });

      // Liens inter-rôles (dans le couloir propre x=469..516)
      c.arrow(lx + lw, ly + 272, ex, ey + 272, { label: 'Copie', dashed: true, labelDy: -10 });
      c.arrow(ex, ey + 325, lx + lw, ly + 325, { label: 'Publiée', color: c.isSketch ? '#15803D' : '#111827', labelDy: 16 });
      c.arrow(lx + 150, y + 382, lx + 150, ly);
      c.arrow(ex + 124, y + 382, ex + 124, ey);
      c.arrow(adx + 117, y + 382, adx + 117, ady);
    },
  },

  // 01 — Connexion, découverte PC1, OTP & confiance 48h
  {
    slug: '01-connexion-decouverte-confiance',
    num: '01/11',
    title: 'Écran de connexion, découverte PC1 & confiance 48 h',
    subtitle: 'VueConnexionServeur : authentification par rôle de poste, OTP e-mail et panneau réseau',
    role: 'Apprenant / Enseignant / Admin (selon lanceur)',
    hta: 'A0 (Découvrir PC1 et ouvrir une session)',
    meta: {
      rules: 'IHM-01..05 · IHM-09 · RG-02 (rôle serveur) · RG-06/07/08 (confiance 48h)',
      flow: 'Connexion OK -> Accueil du rôle | Créer compte -> E02 | Activer PIN -> E02',
    },
    annotations: [
      { num: 1, title: 'En-tête & État réseau', desc: 'Rôle fixé par le poste + bouton Wi-Fi ouvrant la découverte PC1 / saisie manuelle IP+Port.', kind: 'flow' },
      { num: 2, title: 'Champs identifiants', desc: 'E-mail + Mot de passe masqué (bouton Afficher/Masquer explicite, IHM-09).', kind: 'note' },
      { num: 3, title: 'OTP (6 chiffres) & Confiance 48 h', desc: 'Code e-mail 10 min si poste non approuvé ; case opt-in 2 jours (mot de passe toujours requis).', kind: 'rule' },
      { num: 4, title: 'Bandeau d’état localisé', desc: 'Sous le bouton principal : icône + texte complet + spinner (IHM-03), auto-scroll sur petit écran.', kind: 'note' },
      { num: 5, title: 'Actions compte & Aide repliable', desc: 'Créer compte (hors admin), Activer PIN, Oublier poste, Mot de passe oublié, TitledPane d’aide.', kind: 'flow' },
    ],
    draw(c) {
      const fx = 44, fy = 98, fw = 1040, fh = 856;
      const inner = c.appFrame(fx, fy, fw, fh, 'Connexion au serveur', 'Poste Apprenant', 'login', '', { showSidebar: false });

      c.box(inner.cx + 10, inner.cy, 28, 28, { fill: '#E2E8F0', stroke: '#334155', rx: 6 });
      c.icon('book', inner.cx + 24, inner.cy + 14, 15, '#1E293B');
      c.text(inner.cx + 48, inner.cy + 20, "Plateforme d'apprentissage", { size: c.isSketch ? 18 : 14.5, bold: true });
      c.icon('shield', inner.cx + inner.cw - 165, inner.cy + 14, 15, '#334155');
      c.text(inner.cx + inner.cw - 148, inner.cy + 19, 'Sécurisé par HTTPS', { size: c.isSketch ? 16 : 13, color: '#475569' });

      const cx = inner.cx + 30, cy = inner.cy + 42, cw = 510, ch = 640;
      c.box(cx, cy, cw, ch, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 2, rx: 12 });
      c.badge(cx + 26, cy + 20, 110, 26, 'BIENVENUE', 'accent');
      c.box(cx + cw - 66, cy + 16, 40, 38, { fill: '#F3F4F6', stroke: '#334155', rx: 8 });
      c.icon('wifi', cx + cw - 46, cy + 35, 18, '#1E293B');
      c.pin(cx + cw - 14, cy + 24, 1);

      c.text(cx + 26, cy + 78, 'Connexion', { size: c.isSketch ? 26 : 22, bold: true });
      c.text(cx + 26, cy + 100, "Retrouvez votre espace d'apprentissage.", { size: c.isSketch ? 16 : 13, color: '#64748B' });

      let fy2 = c.input(cx + 26, cy + 116, cw - 52, 40, 'Adresse e-mail du compte', 'nom@exemple.fr', { icon: 'mail' });
      fy2 = c.input(cx + 26, fy2 + 12, cw - 52, 40, 'Mot de passe', '••••••••••••', { icon: 'lock', eye: true });
      c.pin(cx + cw - 14, cy + 215, 2);

      c.box(cx + 24, fy2 + 12, cw - 48, 66, { fill: '#F9FAFB', stroke: '#64748B', dashed: true, rx: 6 });
      c.text(cx + 34, fy2 + 30, '[Si poste non approuvé] Code reçu par e-mail (OTP 10 min) :', { size: c.isSketch ? 15 : 12, bold: true, color: '#334155' });
      c.box(cx + 34, fy2 + 38, cw - 68, 30, { fill: '#FFFFFF', stroke: '#6B7280', rx: 4 });
      c.text(cx + 46, fy2 + 58, '6 chiffres', { size: 12.5, color: '#6B7280', italic: true });
      c.pin(cx + cw - 14, fy2 + 38, 3);

      c.checkbox(cx + 26, fy2 + 90, 'Faire confiance à ce poste pendant 2 jours', true, 'À éviter sur un ordinateur partagé.');

      c.button(cx + 26, fy2 + 136, cw - 52, 42, 'Se connecter  →', 'primary');
      c.banner(cx + 26, fy2 + 188, cw - 52, 44, 'ok', 'Plateforme détectée (PC1). Saisissez vos identifiants.');
      c.pin(cx + cw - 14, fy2 + 210, 4);

      c.text(cx + 26, fy2 + 258, 'Pas encore de compte ?', { size: 13, color: '#475569' });
      c.button(cx + 185, fy2 + 240, 120, 24, 'Créer un compte', 'link');
      c.text(cx + 26, fy2 + 286, 'Déjà accepté ?', { size: 13, color: '#475569' });
      c.button(cx + 125, fy2 + 268, 150, 24, 'Activer avec le PIN', 'link');
      c.button(cx + 315, fy2 + 268, 130, 24, 'Oublier ce poste', 'link');
      c.button(cx + 26, fy2 + 296, 150, 24, 'Mot de passe oublié ?', 'link');
      c.pin(cx + cw - 14, fy2 + 275, 5);

      c.box(cx, cy + ch + 14, cw, 54, { fill: '#F8FAFC', stroke: '#64748B', dashed: true, rx: 8 });
      c.text(cx + 18, cy + ch + 38, 'ℹ  Activation et poste de confiance [Développer v]', { size: c.isSketch ? 16 : 13, bold: true });
      c.text(cx + 18, cy + ch + 56, 'Comment fonctionnent le PIN (48h) et le code de connexion (10 min) ?', { size: 12, color: '#64748B' });

      c.text(cx, cy + ch + 88, 'Plateforme d’apprentissage · Poste Apprenant · v0.14.4 · ● Plateforme détectée', {
        size: c.isSketch ? 15 : 12.5,
        color: '#475569',
      });

      const px = cx + cw + 48, py = cy + 8, pw = 370, ph = 395;
      c.box(px, py, pw, ph, { fill: '#FFFFFF', stroke: '#1E293B', dashed: true, strokeWidth: 1.8, rx: 10 });
      c.box(px, py, pw, 36, { fill: '#E2E8F0', stroke: '#334155', rx: 8 });
      c.text(px + 14, py + 23, 'POPUP : PARAMÈTRES RÉSEAU', { size: 13, bold: true });
      c.button(px + 16, py + 50, pw - 32, 34, '↻ Réessayer la connexion', 'secondary');
      c.button(px + 16, py + 92, pw - 32, 34, 'Paramètres Wi-Fi Windows', 'secondary');
      c.box(px + 16, py + 138, pw - 32, 165, { fill: '#F9FAFB', stroke: '#94A3B8', rx: 6 });
      c.text(px + 28, py + 158, 'Adresse manuelle (secours) v', { size: 13, bold: true });
      c.input(px + 28, py + 166, pw - 56, 30, 'Adresse serveur', 'ex : 192.168.1.50');
      c.input(px + 28, py + 224, 150, 30, 'Port HTTPS', '8443');
      c.button(px + 190, py + 246, 136, 30, 'Appliquer', 'primary');
      c.banner(px + 16, py + 316, pw - 32, 64, 'ok', 'Plateforme trouvée : 192.168.1.10:8443 (HTTPS strict).');
      c.arrow(cx + cw - 24, cy + 38, px, py + 22, { label: 'Wi-Fi', dashed: true, labelDy: -10 });

      const nx = px, ny = py + ph + 24, nw = pw, nh = 165;
      c.box(nx, ny, nw, nh, { fill: '#F9FAFB', stroke: '#64748B', dashed: true, rx: 8 });
      c.text(nx + 14, ny + 26, 'CAS PARTICULIER : Poste non configuré', { size: 13, bold: true });
      c.multiText(nx + 14, ny + 48, 'Si lancé sans --role=... (LoginView) :\n• Aucun rôle de démo proposé (RG-02)\n• Invite à lancer via postes\\2/3/4-*.bat', nw - 28, 12.5, 18, { color: '#475569' });
    },
  },

  // 02 — Inscription (sans PIN) & Activation par PIN
  {
    slug: '02-inscription-activation-pin',
    num: '02/11',
    title: 'Demande de compte (sans PIN) & Activation par PIN',
    subtitle: 'Modes dynamiques de VueConnexionServeur : création de demande EN_ATTENTE puis activation',
    role: 'Apprenant / Enseignant (hors Admin)',
    hta: 'A1 (Demander un compte et l’activer)',
    meta: {
      rules: 'BF-U01..U04 · RG-03 (pas de PIN initial) · RG-04 (PIN 8 ch. / 48h / 5 essais) · RG-06',
      flow: 'Créer compte -> EN_ATTENTE -> E-mail PIN après acceptation -> Activer -> Connexion (E01)',
    },
    annotations: [
      { num: 1, title: 'Mode Inscription sans PIN (RG-03)', desc: 'Aucun champ PIN à la demande initiale ; le rôle demandé est celui du poste (masqué pour Admin).', kind: 'rule' },
      { num: 2, title: 'Politique mot de passe (RG-06)', desc: '12 caractères min., 72 octets UTF-8 max., confirmation obligatoire, bouton Afficher/Masquer.', kind: 'note' },
      { num: 3, title: 'Transition animée latérale', desc: '« Créer un compte » glisse depuis la droite ; « J’ai déjà un compte » revient à gauche (sans requête).', kind: 'flow' },
      { num: 4, title: 'Activation par PIN (8 chiffres)', desc: 'Après acceptation par Bot ou Admin, saisie E-mail + PIN 8 chiffres (valable 48h, max 5 essais).', kind: 'rule' },
      { num: 5, title: 'Réinitialisation mot de passe', desc: 'Dialogue « Mot de passe oublié ? » : code 8 chiffres par e-mail (10 min) + nouveau secret.', kind: 'note' },
    ],
    draw(c) {
      const fx = 44, fy = 98, fw = 1040, fh = 856;
      const inner = c.appFrame(fx, fy, fw, fh, 'Demande de compte & Activation', 'Poste Apprenant / Enseignant', 'login', '', { showSidebar: false });

      const c1x = inner.cx + 10, c1y = inner.cy + 10, c1w = 475, c1h = 720;
      c.box(c1x, c1y, c1w, c1h, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 2, rx: 10 });
      c.badge(c1x + 22, c1y + 18, 185, 26, 'MODE 1 : INSCRIPTION', 'accent');
      c.text(c1x + 22, c1y + 72, 'Créer un compte', { size: c.isSketch ? 25 : 20, bold: true });
      c.text(c1x + 22, c1y + 95, "Rejoignez votre espace d'apprentissage.", { size: 13, color: '#64748B' });
      c.pin(c1x + c1w - 16, c1y + 28, 1);

      let y1 = c.input(c1x + 22, c1y + 114, c1w - 44, 38, 'Nom et prénom', 'Nom et prénom');
      y1 = c.input(c1x + 22, y1 + 12, c1w - 44, 38, 'Adresse e-mail du compte', 'nom@exemple.fr', { icon: 'mail' });
      y1 = c.input(c1x + 22, y1 + 12, c1w - 44, 38, 'Mot de passe (12 car. min., 72 octets max.)', '••••••••••••', { icon: 'lock', eye: true });
      c.pin(c1x + c1w - 16, y1 - 20, 2);
      y1 = c.input(c1x + 22, y1 + 12, c1w - 44, 38, 'Confirmer le mot de passe', 'Confirmer le mot de passe', { icon: 'lock', eye: true });

      c.box(c1x + 22, y1 + 14, c1w - 44, 38, { fill: c.isSketch ? '#FEF2F2' : '#F3F4F6', stroke: c.isSketch ? '#B91C1C' : '#6B7280', dashed: true });
      c.text(c1x + 34, y1 + 38, 'Aucun champ PIN ici ! Compte créé EN_ATTENTE et inactif.', { size: 12.5, bold: true, color: c.isSketch ? '#991B1B' : '#374151' });

      c.button(c1x + 22, y1 + 66, c1w - 44, 42, 'Envoyer ma demande  →', 'primary');
      c.banner(c1x + 22, y1 + 118, c1w - 44, 62, 'info', 'Demande traitée selon la politique PC1 : pré-acceptation auto possible pour apprenant, sinon décision administrateur.');
      c.button(c1x + 22, y1 + 194, 180, 26, "J'ai déjà un compte", 'link');
      c.pin(c1x + 210, y1 + 206, 3);

      const c2x = c1x + c1w + 42, c2y = inner.cy + 10, c2w = 470, c2h = 460;
      c.box(c2x, c2y, c2w, c2h, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 2, rx: 10 });
      c.badge(c2x + 22, c2y + 18, 185, 26, 'MODE 2 : ACTIVATION', 'ok');
      c.text(c2x + 22, c2y + 72, 'Activer mon compte', { size: c.isSketch ? 25 : 20, bold: true });
      c.text(c2x + 22, c2y + 95, 'Activez votre compte avec le PIN reçu par e-mail.', { size: 13, color: '#64748B' });
      c.pin(c2x + c2w - 16, c2y + 28, 4);

      let y2 = c.input(c2x + 22, c2y + 116, c2w - 44, 38, 'PIN d’activation après acceptation', 'PIN à 8 chiffres (valable 48h, 5 essais)');
      y2 = c.input(c2x + 22, y2 + 12, c2w - 44, 38, 'Adresse e-mail du compte', 'nom@exemple.fr', { icon: 'mail' });
      c.button(c2x + 22, y2 + 20, c2w - 44, 42, 'Activer mon compte  →', 'primary');
      c.banner(c2x + 22, y2 + 74, c2w - 44, 54, 'ok', 'Compte activé. Connectez-vous avec votre e-mail et votre mot de passe.');
      c.button(c2x + 22, y2 + 142, 180, 26, 'Retour à la connexion', 'link');

      c.arrow(c1x + c1w, c1y + 390, c2x, c2y + 165, { label: 'PIN SMTP', labelDy: -12 });

      const c3x = c2x, c3y = c2y + c2h + 24, c3w = c2w, c3h = 236;
      c.box(c3x, c3y, c3w, c3h, { fill: '#F9FAFB', stroke: '#334155', dashed: true, rx: 10 });
      c.text(c3x + 20, c3y + 28, 'DIALOGUE : Mot de passe oublié', { size: 14, bold: true });
      c.pin(c3x + c3w - 16, c3y + 24, 5);
      c.multiText(c3x + 20, c3y + 52, '1. Confirmation : "Envoyer un code" (8 chiffres, 10 min)\n2. Saisie : Code + Nouveau mot de passe + Confirmation\n3. Effet : révoque les postes de confiance du compte.', c3w - 40, 12.5, 19, { color: '#374151' });
      c.button(c3x + c3w - 220, c3y + 178, 95, 34, 'Annuler', 'secondary');
      c.button(c3x + c3w - 115, c3y + 178, 95, 34, 'Modifier', 'primary');
    },
  },

  // 03 — Apprenant : Parcours par niveaux
  {
    slug: '03-apprenant-parcours-niveaux',
    num: '03/11',
    title: 'Apprenant : Mon parcours par niveaux',
    subtitle: 'ParcoursServeurView & frise des niveaux : états, seuils, disponibilité et actualisation auto',
    role: 'Apprenant (PC4)',
    hta: 'A2 / A4 (Parcours, états et progression)',
    meta: {
      rules: 'BF-P01 · BF-P06 · BF-P09 · IHM-06/07 · IHM-10/11 · RG-10/11 (niveaux indépendants)',
      flow: 'Clic Étape 1 -> Cours (E04) | Étape 2 -> Exercice (E05) | Étape 3 -> Test (E06)',
    },
    annotations: [
      { num: 1, title: 'Navigation latérale (244 px)', desc: 'Sections Apprendre + choix explicite du langage (Java/Python/C++ ✓) + compte en bas.', kind: 'note' },
      { num: 2, title: 'En-tête & Règle humaine', desc: 'Rappelle que la validation dépend de la correction publiée par l’enseignant (aucune note auto).', kind: 'rule' },
      { num: 3, title: 'Carte Niveau VALIDÉ', desc: 'Meilleure note /20 et seuil ; boutons 1·Cours et 2·Entraînement actifs, 3·Test disponible.', kind: 'note' },
      { num: 4, title: 'Carte Niveau EN COURS', desc: 'Indique clairement ce qui est prêt vs "en préparation" (IHM-11) sans contenu fictif.', kind: 'flow' },
      { num: 5, title: 'Carte Niveau VERROUILLÉ', desc: 'Actions désactivées + explication du prérequis (« Débloqué après validation niveau 2 »).', kind: 'rule' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Parcours', 'Apprenant', 'parcours', 'Python');
      c.pin(198, 165, 1);

      c.text(inner.cx, inner.cy + 22, 'Mon parcours — Python', { size: c.isSketch ? 25 : 20, bold: true });
      c.text(inner.cx, inner.cy + 44, "L'enseignant corrige vos réponses et publie la décision. Aucune note automatique.", { size: 13, color: '#64748B' });
      c.pin(inner.cx + inner.cw - 16, inner.cy + 22, 2);

      c.button(inner.cx, inner.cy + 58, 175, 34, 'Actualiser le parcours', 'secondary');
      c.button(inner.cx + 186, inner.cy + 58, 195, 34, 'Mes copies et décisions', 'secondary');
      c.badge(inner.cx + inner.cw - 245, inner.cy + 61, 240, 28, '1 validé · 1 en cours · Seuil 12/20', 'accent');

      c.banner(inner.cx, inner.cy + 104, inner.cw, 36, 'ok', 'La validation d’un niveau débloque le suivant après publication (actualisation auto 5 s).');

      const cards = [
        {
          rang: 1,
          titre: 'Variables et types de base',
          etat: 'VALIDE',
          note: 'Meilleure note : 16 / 20 · seuil 12/20',
          dispo: 'Cours disponible · Entraînement disponible · Test prêt',
          btns: [true, true, true],
          pin: 3,
          kind: 'ok',
        },
        {
          rang: 2,
          titre: 'Conditions et boucles',
          etat: 'EN_COURS',
          note: 'Meilleure note : — / 20 · seuil 12/20 · 3 tentatives restantes',
          dispo: 'Cours disponible · Entraînement disponible · Test en préparation',
          btns: [true, true, false],
          pin: 4,
          kind: 'accent',
        },
        {
          rang: 3,
          titre: 'Fonctions et modules',
          etat: 'VERROUILLE',
          note: 'Meilleure note : — / 20 · seuil 14/20',
          dispo: 'Débloqué après validation du niveau 2 par l’enseignant affecté.',
          btns: [false, false, false],
          pin: 5,
          kind: 'warn',
        },
      ];

      let cy = inner.cy + 154;
      for (const lv of cards) {
        const ch = 192;
        c.box(inner.cx, cy, inner.cw, ch, {
          fill: lv.etat === 'VERROUILLE' ? '#F9FAFB' : '#FFFFFF',
          stroke: lv.etat === 'VERROUILLE' ? '#9CA3AF' : '#334155',
          dashed: lv.etat === 'VERROUILLE',
          rx: 8,
        });
        c.text(inner.cx + 16, cy + 28, `Niveau ${lv.rang} — ${lv.titre}`, {
          size: c.isSketch ? 19 : 15,
          bold: true,
          color: lv.etat === 'VERROUILLE' ? '#6B7280' : '#111827',
        });
        c.badge(inner.cx + inner.cw - 132, cy + 12, 114, 26, lv.etat, lv.kind);
        c.pin(inner.cx + inner.cw - 12, cy + 54, lv.pin);

        c.text(inner.cx + 16, cy + 54, lv.note, { size: 13.5, color: '#374151' });
        c.text(inner.cx + 16, cy + 78, lv.dispo, { size: 12.5, color: '#475569' });

        c.button(inner.cx + 16, cy + 96, 215, 36, '1 · Cours et explications', lv.btns[0] ? 'secondary' : 'disabled');
        c.button(inner.cx + 242, cy + 96, 235, 36, '2 · Entraînement avec IA', lv.btns[1] ? 'secondary' : 'disabled');
        c.button(inner.cx + 488, cy + 96, 215, 36, '3 · Test final sans IA', lv.btns[2] ? 'primary' : 'disabled');

        c.text(inner.cx + 16, cy + 158, 'Test -> soumission -> correction de l’enseignant -> note et décision publiées -> niveau suivant si validé.', {
          size: 12,
          color: '#64748B',
        });
        cy += ch + 14;
      }
    },
  },

  // 04 — Apprenant : Cours + Panneau Assistant IA
  {
    slug: '04-apprenant-cours-assistant-ia',
    num: '04/11',
    title: 'Apprenant : Lecture du cours & Assistant IA',
    subtitle: 'ContenuNiveauView (cours) + PanneauAssistant (NVIDIA via PC1 avec citations)',
    role: 'Apprenant (PC4)',
    hta: 'A2 (Étudier un cours) + IA1 (Aide citée et bornée)',
    meta: {
      rules: 'BF-P03 · BF-P07 · BF-IA01..05 · RG-19 (ne note/valide jamais)',
      flow: 'Revenir au parcours -> E03 | Continuer vers entraînement avec IA -> E05',
    },
    annotations: [
      { num: 1, title: 'Contenu publié depuis PC1', desc: 'Blocs texte explicatifs + exemples de code en lecture seule (aucune exécution fictive).', kind: 'note' },
      { num: 2, title: 'Panneau Assistant IA (droite)', desc: 'Relai NVIDIA via PC1 uniquement. Avertissement vie privée + aucune note ni validation.', kind: 'rule' },
      { num: 3, title: 'Bulles & Citations du corpus', desc: 'Historique borné à 3 tours ; chaque réponse cite une section réelle (-> Section / Langage).', kind: 'note' },
      { num: 4, title: 'Raccourcis & Champ question', desc: '3 boutons rapides (Indice, Erreur, Revoir cours) + champ libre + pied quota (30/j, 200/j).', kind: 'flow' },
      { num: 5, title: 'Navigation de progression', desc: 'Revenir au parcours ou Continuer vers entraînement (désactivé si exercice en préparation).', kind: 'flow' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Cours', 'Apprenant', 'cours', 'Python');

      c.text(inner.cx, inner.cy + 22, 'Niveau 2 — Conditions et boucles', { size: c.isSketch ? 24 : 19, bold: true });
      c.text(inner.cx, inner.cy + 44, 'Cours et explications publiés par l’équipe pédagogique.', { size: 13, color: '#64748B' });

      const lw = 485;
      const ly = inner.cy + 62;
      const lh = inner.ch - 144;
      c.box(inner.cx, ly, lw, lh, { fill: '#FFFFFF', stroke: '#475569', rx: 8 });
      c.pin(inner.cx + lw - 16, ly + 22, 1);

      c.text(inner.cx + 16, ly + 28, '1. La structure conditionnelle if / elif / else', { size: 15, bold: true });
      c.multiText(inner.cx + 16, ly + 52, "En Python, l'indentation (4 espaces) délimite le bloc exécuté lorsque la condition est vraie.", lw - 32, c.isSketch ? 15.5 : 12.5, 20, { color: '#334155' });

      c.box(inner.cx + 16, ly + 115, lw - 32, 145, { fill: '#F3F4F6', stroke: '#6B7280', rx: 6 });
      c.text(inner.cx + 26, ly + 135, 'Exemple de code (lecture seule) :', { size: 12, bold: true, color: '#475569' });
      c.multiText(inner.cx + 26, ly + 160, 'note = 14\nif note >= 12:\n    print("Seuil atteint")\nelse:\n    print("A revoir")', lw - 52, 12.5, 20, { mono: true });

      c.text(inner.cx + 16, ly + 290, '2. La boucle for et range()', { size: 15, bold: true });
      c.multiText(inner.cx + 16, ly + 314, "La boucle for parcourt les éléments d'une séquence ou d'un intervalle range(debut, fin).", lw - 32, c.isSketch ? 15.5 : 12.5, 20, { color: '#334155' });

      c.box(inner.cx + 16, ly + 382, lw - 32, 110, { fill: '#F3F4F6', stroke: '#6B7280', rx: 6 });
      c.multiText(inner.cx + 26, ly + 408, 'somme = 0\nfor k in range(1, 5):\n    somme += k', lw - 52, 12.5, 20, { mono: true });
      c.text(inner.cx + 16, ly + lh - 18, '[Défilement vertical si contenu plus long · Aucun code exécuté ici]', { size: 11.5, color: '#64748B', italic: true });

      const ax = inner.cx + lw + 16, aw = inner.cw - lw - 16;
      c.box(ax, ly, aw, lh, { fill: '#F9FAFB', stroke: '#1E293B', dashed: true, strokeWidth: 1.8, rx: 8 });
      c.text(ax + 14, ly + 26, 'Assistant IA', { size: c.isSketch ? 19 : 15.5, bold: true });
      c.pin(ax + aw - 16, ly + 22, 2);
      c.multiText(ax + 14, ly + 46, 'Aide NVIDIA via PC1 · ne note pas, ne valide pas. Aucune donnée privée.', aw - 28, 11.5, 15, { color: '#475569' });

      c.box(ax + 14, ly + 86, aw - 36, 54, { fill: '#FFFFFF', stroke: '#94A3B8', rx: 8 });
      c.multiText(ax + 24, ly + 106, 'IA : Posez une question sur le cours ou demandez un indice.', aw - 56, 12, 16);

      c.box(ax + 38, ly + 150, aw - 52, 42, { fill: '#E2E8F0', stroke: '#475569', rx: 8 });
      c.text(ax + 48, ly + 176, 'Apprenant : À quoi sert elif ?', { size: 12.5, bold: true });

      c.box(ax + 14, ly + 204, aw - 32, 132, { fill: '#FFFFFF', stroke: '#334155', rx: 8 });
      c.multiText(ax + 24, ly + 224, 'IA : elif teste une condition alternative si le if précédent est faux.\n-> Section Conditions (Python)', aw - 52, 12, 17);
      c.pin(ax + aw - 14, ly + 240, 3);

      const bw = Math.floor((aw - 36) / 3);
      c.button(ax + 12, ly + 356, bw, 38, 'Indice', 'secondary');
      c.button(ax + 18 + bw, ly + 356, bw, 38, 'Erreur', 'secondary');
      c.button(ax + 24 + bw * 2, ly + 356, bw, 38, 'Cours', 'secondary');

      c.input(ax + 12, ly + 404, aw - 24, 34, '', 'Poser une question sur le cours…');
      c.pin(ax + aw - 14, ly + 420, 4);
      c.text(ax + 14, ly + lh - 18, 'NVIDIA via PC1 · 30 req/j · quota partagé 200', { size: 11, color: '#64748B' });

      const by = inner.cy + inner.ch - 74;
      c.banner(inner.cx, by, inner.cw, 30, 'info', 'La note et la décision seront publiées par l’enseignant après soumission.');
      c.button(inner.cx, by + 38, 175, 34, 'Revenir au parcours', 'secondary');
      c.button(inner.cx + 188, by + 38, 340, 34, "Continuer vers l'entraînement avec IA ->", 'primary');
      c.pin(inner.cx + 544, by + 54, 5);
    },
  },

  // 05 — Apprenant : Entraînement avec éditeur & Assistant IA
  {
    slug: '05-apprenant-entrainement-ia',
    num: '05/11',
    title: 'Apprenant : Entraînement avec éditeur & Assistant IA',
    subtitle: 'ContenuNiveauView (entraînement) : brouillon local non exécuté et explication IA bornée',
    role: 'Apprenant (PC4)',
    hta: 'A2 (S’entraîner) + IA1 (Expliquer le code courant)',
    meta: {
      rules: 'BF-P09 (brouillon protégé) · BF-IA05/06 · RG-21 (aucune exécution fictive)',
      flow: 'Revenir au parcours -> E03 | Passer au test final — sans IA -> E06',
    },
    annotations: [
      { num: 1, title: 'Énoncé d’entraînement', desc: 'Rappelle explicitement que le travail ici n’est pas une copie d’examen.', kind: 'note' },
      { num: 2, title: 'Éditeur de brouillon local', desc: 'Pré-rempli par code_depart ; le rafraîchissement auto 5 s ne l’écrase jamais si modifié.', kind: 'note' },
      { num: 3, title: 'Aucune exécution fictive (RG-21)', desc: 'Pas de faux bouton « Exécuter » : mention explicite de brouillon local non noté.', kind: 'rule' },
      { num: 4, title: 'Aide IA sur le code courant', desc: 'Transmet question + code courant à PC1/NVIDIA ; refuse de donner la solution complète.', kind: 'rule' },
      { num: 5, title: 'Passage au test final', desc: 'Bouton vers TestServeurView (vérifie que cours, entraînement, sujet et période sont prêts).', kind: 'flow' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Exercice', 'Apprenant', 'exercice', 'Python');

      c.text(inner.cx, inner.cy + 22, 'Niveau 2 — Conditions et boucles', { size: c.isSketch ? 24 : 19, bold: true });
      c.text(inner.cx, inner.cy + 44, "Entraînement libre avec aide IA. Votre travail ici n'est pas une copie d'examen.", { size: 13, color: '#64748B' });

      const lw = 485;
      const ly = inner.cy + 62;
      const lh = inner.ch - 144;

      // Énoncé
      c.box(inner.cx, ly, lw, 110, { fill: '#FFFFFF', stroke: '#475569', rx: 8 });
      c.text(inner.cx + 14, ly + 24, "Énoncé de l'entraînement", { size: 14, bold: true });
      c.multiText(inner.cx + 14, ly + 48, 'Écrire une fonction est_admis(note) qui renvoie True si note >= 12, sinon False. Tester le cas limite 12.', lw - 28, c.isSketch ? 15 : 12.5, 19, { color: '#334155' });
      c.pin(inner.cx + lw - 16, ly + 22, 1);

      // Éditeur de code
      c.box(inner.cx, ly + 124, lw, 300, { fill: '#F9FAFB', stroke: '#1E293B', strokeWidth: 1.8, rx: 8 });
      c.text(inner.cx + 14, ly + 146, 'Votre brouillon de code (non sauvegardé sur PC1) :', { size: 12.5, bold: true, color: '#334155' });
      c.multiText(inner.cx + 16, ly + 178, 'def est_admis(note):\n    if note > 12:\n        return True\n    else:\n        return False\n\n# Question : le cas note == 12 ?', lw - 32, 13, 22, { mono: true });
      c.pin(inner.cx + lw - 16, ly + 170, 2);

      // Avertissement RG-21
      c.banner(inner.cx, ly + 438, lw, 58, 'warn', 'Brouillon local : aucune exécution automatique ni note (RG-21). Ne collez aucune donnée privée.');
      c.pin(inner.cx + lw - 16, ly + 468, 3);

      // Panneau Assistant IA à droite
      const ax = inner.cx + lw + 16, aw = inner.cw - lw - 16;
      c.box(ax, ly, aw, lh, { fill: '#F9FAFB', stroke: '#1E293B', dashed: true, strokeWidth: 1.8, rx: 8 });
      c.text(ax + 14, ly + 26, 'Assistant IA (sur code)', { size: c.isSketch ? 18 : 15, bold: true });
      c.pin(ax + aw - 16, ly + 22, 4);
      c.multiText(ax + 14, ly + 46, 'Aide NVIDIA via PC1 · ne note pas, ne valide pas.', aw - 28, 11.5, 15, { color: '#475569' });

      c.box(ax + 24, ly + 82, aw - 38, 56, { fill: '#E2E8F0', stroke: '#475569', rx: 8 });
      c.multiText(ax + 34, ly + 102, 'Apprenant : Pourquoi note = 12 échoue ?', aw - 56, 12, 16, { bold: true });

      c.box(ax + 14, ly + 148, aw - 32, 145, { fill: '#FFFFFF', stroke: '#334155', rx: 8 });
      c.multiText(ax + 24, ly + 168, "IA : L'opérateur > exclut la borne 12. Vérifiez l'opérateur de comparaison (>=) dans votre condition.\n-> Section Opérateurs (Python)", aw - 52, 12, 17);

      c.box(ax + 14, ly + 302, aw - 32, 54, { fill: c.isSketch ? '#FEE2E2' : '#F3F4F6', stroke: c.isSketch ? '#B91C1C' : '#6B7280', dashed: true, rx: 6 });
      c.multiText(ax + 24, ly + 322, 'Garde-fou : refus si demande de note ou solution complète.', aw - 50, 11.5, 15, { color: c.isSketch ? '#991B1B' : '#374151', bold: true });

      const bw = Math.floor((aw - 36) / 3);
      c.button(ax + 12, ly + 368, bw, 36, 'Indice', 'secondary');
      c.button(ax + 18 + bw, ly + 368, bw, 36, 'Erreur', 'secondary');
      c.button(ax + 24 + bw * 2, ly + 368, bw, 36, 'Cours', 'secondary');
      c.input(ax + 12, ly + 412, aw - 24, 34, '', 'Poser une question sur mon code…');

      const by = inner.cy + inner.ch - 74;
      c.banner(inner.cx, by, inner.cw, 30, 'info', 'La note et la décision seront publiées par l’enseignant après soumission.');
      c.button(inner.cx, by + 38, 175, 34, 'Revenir au parcours', 'secondary');
      c.button(inner.cx + 188, by + 38, 285, 34, 'Passer au test final — sans IA  →', 'primary');
      c.pin(inner.cx + 486, by + 54, 5);
    },
  },

  // 06 — Apprenant : Test final sans IA
  {
    slug: '06-apprenant-test-final-sans-ia',
    num: '06/11',
    title: 'Apprenant : Test final sans IA',
    subtitle: 'TestServeurView : copie EN_COURS, questions photographiées, abandon ou soumission figée',
    role: 'Apprenant (PC4)',
    hta: 'A3 (Passer et soumettre le test final)',
    meta: {
      rules: 'BF-E01..04 · BF-E10 · RG-13/14/15 · RG-20 (IA bloquée côté serveur)',
      flow: 'Soumettre (confirmation) -> SOUMISE -> Mes copies (E07) | Abandonner -> ANNULEE',
    },
    annotations: [
      { num: 1, title: 'Verrouillage strict de l’IA (RG-20)', desc: 'Aucun panneau IA ; toute tentative EN_COURS bloque /api/ia/aide sur PC1 (même 2e fenêtre).', kind: 'rule' },
      { num: 2, title: 'Cycle de vie de la copie', desc: '1 copie EN_COURS max ; quitter l’écran ne la soumet ni ne l’abandonne (RG-14).', kind: 'note' },
      { num: 3, title: 'Questions associées (IHM-17)', desc: 'Sujet et seuil photographiés au démarrage : QCM (radio) et questions libres nommées.', kind: 'note' },
      { num: 4, title: 'Code complémentaire', desc: 'Facultatif, transmis comme texte brut à l’enseignant sans aucune exécution.', kind: 'note' },
      { num: 5, title: 'Actions explicites & Confirmation', desc: 'Soumettre -> SOUMISE (aucune note auto !) ; Abandonner -> ANNULEE après confirmation.', kind: 'flow' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Test de validation', 'Apprenant', 'parcours', 'Python');

      c.text(inner.cx, inner.cy + 22, 'Test — niveau 2 (Conditions et boucles)', { size: c.isSketch ? 24 : 19, bold: true });
      c.text(inner.cx, inner.cy + 44, "Votre travail sera corrigé par votre enseignant. L'IA est bloquée pendant cette tentative.", { size: 13, color: '#64748B' });

      // Alerte blocage IA
      c.banner(inner.cx, inner.cy + 58, inner.cw, 36, 'err', 'IA BLOQUÉE SUR PC1 : tentative réelle en cours. Aucune note automatique calculée.');
      c.pin(inner.cx + inner.cw - 14, inner.cy + 74, 1);

      // Statut copie
      c.banner(inner.cx, inner.cy + 102, inner.cw, 46, 'info', 'Copie #42 (EN_COURS) · Réponses enregistrées uniquement à la soumission. Quitter cet écran permet de reprendre.');
      c.pin(inner.cx + inner.cw - 14, inner.cy + 124, 2);

      // Carte Q1 (QCM)
      let qy = inner.cy + 160;
      c.box(inner.cx, qy, inner.cw, 150, { fill: '#FFFFFF', stroke: '#334155', rx: 8 });
      c.text(inner.cx + 16, qy + 26, '1. [QCM] Que renvoie l’expression (12 >= 12 and 5 < 10) en Python ?', { size: 14, bold: true });
      c.radio(inner.cx + 22, qy + 44, 'True', true);
      c.radio(inner.cx + 22, qy + 74, 'False', false);
      c.radio(inner.cx + 22, qy + 104, 'Erreur de syntaxe', false);
      c.pin(inner.cx + inner.cw - 14, qy + 26, 3);

      // Carte Q2 (Question libre)
      qy += 162;
      c.box(inner.cx, qy, inner.cw, 136, { fill: '#FFFFFF', stroke: '#334155', rx: 8 });
      c.text(inner.cx + 16, qy + 26, '2. [Question libre] Expliquez la différence entre if/elif et deux if successifs.', { size: 14, bold: true });
      c.box(inner.cx + 16, qy + 40, inner.cw - 32, 82, { fill: '#F9FAFB', stroke: '#6B7280', rx: 6 });
      c.multiText(inner.cx + 26, qy + 62, "Avec elif, la seconde condition n'est évaluée que si la première est fausse...", inner.cw - 56, 12.5, 18, { color: '#374151' });

      // Code complémentaire + Vignette modale de confirmation
      qy += 148;
      const codeW = 470;
      c.box(inner.cx, qy, codeW, 138, { fill: '#FFFFFF', stroke: '#475569', rx: 8 });
      c.text(inner.cx + 14, qy + 24, 'Code complémentaire (facultatif, non exécuté)', { size: 13.5, bold: true });
      c.box(inner.cx + 14, qy + 34, codeW - 28, 90, { fill: '#F3F4F6', stroke: '#6B7280', rx: 6 });
      c.multiText(inner.cx + 24, qy + 58, 'def est_admis(note):\n    return note >= 12', codeW - 48, 12.5, 20, { mono: true });
      c.pin(inner.cx + codeW - 14, qy + 24, 4);

      // Vignette modale confirmation soumission
      const mx = inner.cx + codeW + 18, mw = inner.cw - codeW - 18;
      c.box(mx, qy, mw, 138, { fill: '#F8FAFC', stroke: '#1E293B', dashed: true, rx: 8 });
      c.text(mx + 14, qy + 24, 'CONFIRMATION MODALE :', { size: 13, bold: true });
      c.multiText(mx + 14, qy + 44, 'Soumettre la copie ? Après envoi, aucune modification ni note automatique.', mw - 28, 11.5, 16, { color: '#374151' });
      c.button(mx + mw - 200, qy + 94, 86, 32, 'Annuler', 'secondary');
      c.button(mx + mw - 104, qy + 94, 90, 32, 'Soumettre', 'primary');

      // Barre du bas
      const by = inner.cy + inner.ch - 40;
      c.button(inner.cx, by, 130, 36, 'Mes copies', 'secondary');
      c.button(inner.cx + inner.cw - 456, by, 215, 36, 'Abandonner cette tentative', 'danger');
      c.button(inner.cx + inner.cw - 228, by, 228, 36, 'Soumettre à mon enseignant', 'primary');
      c.pin(inner.cx + inner.cw - 14, by + 4, 5);
    },
  },

  // 07 — Apprenant : Mes copies, décisions & Tableau de bord
  {
    slug: '07-apprenant-copies-resultats-tableau-bord',
    num: '07/11',
    title: 'Apprenant : Mes copies, décisions publiées & Tableau de bord',
    subtitle: 'ResultatView (SuiviPedagogique) et TableauBordView : suivi post-soumission et remédiation',
    role: 'Apprenant (PC4)',
    hta: 'A4 (Lire la décision et poursuivre)',
    meta: {
      rules: 'BF-E04 · BF-E08/09 · RG-16 (brouillon enseignant invisible) · RG-18 (validation acquise)',
      flow: 'Revenir au parcours -> E03 | Revoir cours -> E04 | Repasser test -> E06',
    },
    annotations: [
      { num: 1, title: 'Liste des copies réelles', desc: 'Colonnes : apprenant, enseignant, langage, rang, statut, note (note « — » tant que SOUMISE).', kind: 'note' },
      { num: 2, title: 'Confidentialité du brouillon (RG-16)', desc: 'Aucun score ni commentaire brouillon visible avant que l’enseignant ne clique sur Publier.', kind: 'rule' },
      { num: 3, title: 'Détail de la copie publiée', desc: 'Affiche sujet photographié, réponses soumises, Note /20, Décision VALIDÉ/REFUSÉ et correction.', kind: 'note' },
      { num: 4, title: 'Tableau de bord (onglets langages)', desc: 'KPI synthétiques par langage + liste « Ce qui vous bloque » + boutons de remédiation.', kind: 'flow' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Résultat du test', 'Apprenant', 'resultat', 'Python');

      // Vue A : Mes copies et corrections (haut)
      c.text(inner.cx, inner.cy + 20, 'Vue A · Mes copies et corrections (SuiviPedagogique)', { size: c.isSketch ? 20 : 16, bold: true });
      c.button(inner.cx + inner.cw - 130, inner.cy + 2, 130, 30, 'Actualiser', 'secondary');
      c.banner(inner.cx, inner.cy + 36, inner.cw, 44, 'info', 'Les notes ne sont visibles qu’après publication par l’enseignant. Aucune note automatique sur les copies en attente.');

      const cols = [
        { label: 'Apprenant', w: 120 },
        { label: 'Enseignant', w: 120 },
        { label: 'Langage', w: 95 },
        { label: 'Rang', w: 70 },
        { label: 'Statut', w: 145 },
        { label: 'Note', w: inner.cw - 550 },
      ];
      const ty = c.table(
        inner.cx,
        inner.cy + 90,
        inner.cw,
        cols,
        [
          { cells: ['Amina B.', 'Prof. Karim', 'python', '1', 'CORRIGEE (publiée)', '16.0 / 20'], _selected: true },
          { cells: ['Amina B.', 'Prof. Karim', 'python', '2', 'SOUMISE (attente)', '—'], _muted: true },
        ],
        32
      );
      c.pin(inner.cx + inner.cw - 14, inner.cy + 104, 1);
      c.pin(inner.cx + inner.cw - 14, ty - 14, 2);

      // Détail de la copie sélectionnée
      const dy = ty + 10;
      c.box(inner.cx, dy, inner.cw, 152, { fill: '#F9FAFB', stroke: '#475569', rx: 8 });
      c.text(inner.cx + 14, dy + 22, 'Détail de la copie #18 — CORRIGEE (publiée par Prof. Karim)', { size: 13.5, bold: true });
      c.pin(inner.cx + inner.cw - 14, dy + 22, 3);
      c.multiText(
        inner.cx + 14,
        dy + 44,
        'Q1. Quel type pour un entier en Python ?  -> Réponse : int\nCode soumis : x = 42 ; print(type(x))\n-------------------------------------------------------------\nNote : 16.0/20  |  Décision : VALIDÉ (seuil 12/20 atteint -> Niveau 2 débloqué)\nCorrection : Très bien, syntaxe claire et réponse précise.',
        inner.cw - 28,
        12,
        18,
        { color: '#1F2937' }
      );
      c.button(inner.cx, dy + 162, 180, 32, 'Revenir au parcours', 'secondary');

      // Vue B : Tableau de bord (bas)
      const by = dy + 204;
      c.box(inner.cx, by, inner.cw, inner.cy + inner.ch - by, { fill: '#FFFFFF', stroke: '#1E293B', dashed: true, rx: 8 });
      c.text(inner.cx + 14, by + 24, 'Vue B · Mon tableau de bord (TableauBordView)', { size: c.isSketch ? 19 : 15, bold: true });
      c.pin(inner.cx + inner.cw - 14, by + 22, 4);

      // Onglets langages
      c.button(inner.cx + 14, by + 36, 90, 28, 'Java', 'secondary');
      c.button(inner.cx + 110, by + 36, 95, 28, 'Python', 'primary');
      c.button(inner.cx + 211, by + 36, 90, 28, 'C++', 'secondary');

      // 3 cartes KPI
      const kw = 175;
      const kpis = [
        ['1', 'niveaux validés'],
        ['1', 'niveau en cours'],
        ['16/20', 'meilleure note'],
      ];
      kpis.forEach((k, idx) => {
        const kx = inner.cx + 14 + idx * (kw + 12);
        c.box(kx, by + 74, kw, 62, { fill: '#F9FAFB', stroke: '#475569', rx: 6 });
        c.text(kx + 14, by + 102, k[0], { size: 20, bold: true });
        c.text(kx + 14, by + 124, k[1], { size: 11.5, color: '#64748B' });
      });

      // Ce qui bloque + actions
      const bx = inner.cx + 585;
      c.text(bx, by + 84, 'Ce qui vous bloque :', { size: 13, bold: true });
      c.multiText(bx, by + 104, '• Niveau 2 : copie #42 en attente\n• Niveau 3 : verrouillé (prérequis niv. 2)', inner.cw - 595, 11.5, 17, { color: '#475569' });
      c.button(inner.cx + 14, by + 150, 210, 32, 'Revoir le cours du niveau', 'secondary');
      c.button(inner.cx + 236, by + 150, 170, 32, 'Repasser le test', 'primary');

      c.box(inner.cx + 14, by + 194, inner.cw - 28, 72, { fill: '#F9FAFB', stroke: '#CBD5E1', rx: 6 });
      c.text(inner.cx + 26, by + 216, 'Synthèse des niveaux — Python', { size: 13, bold: true });
      c.multiText(inner.cx + 26, by + 236, '• Niveau 1 (Variables) : VALIDÉ · 16.0 / 20   |   • Niveau 2 (Conditions) : Copie #42 SOUMISE   |   • Niveau 3 : VERROUILLÉ', inner.cw - 52, 12, 18, { color: '#374151' });
    },
  },

  // 08 — Enseignant : Mes niveaux et contenus pédagogiques
  {
    slug: '08-enseignant-niveaux-contenus',
    num: '08/11',
    title: 'Enseignant : Mes niveaux et contenus pédagogiques',
    subtitle: 'GestionNiveauxView (E1) + dialogues : création brouillon, cours/exercice, test et publication',
    role: 'Enseignant (PC3)',
    hta: 'E1 (Créer, préparer et publier un niveau)',
    meta: {
      rules: 'BF-P02..05 · BF-P10 · RG-09/10/12 (auteur = session, publication complète requise)',
      flow: '1. Créer brouillon -> 2. Cours/Exercice -> 3. Sujet test -> 4. Publier -> Visible aux affectés',
    },
    annotations: [
      { num: 1, title: 'Workflow en 4 étapes ordonnées', desc: 'L’enseignant crée sans passer par l’admin : Créer -> Contenus -> Test -> Publier.', kind: 'note' },
      { num: 2, title: 'Catalogue propre à l’enseignant', desc: 'Chaque niveau affiche langage, rang auto, titre et statut (BROUILLON invisible / PUBLIE).', kind: 'rule' },
      { num: 3, title: 'Modale 1 : Créer un niveau', desc: 'Langage (Java/Py/C++), Titre, Seuil /20, Tentatives max ; rang attribué côté serveur.', kind: 'flow' },
      { num: 4, title: 'Modales 2 & 3 : Contenus & Test', desc: 'Cours + Énoncé + Code de départ ; Questions libres (1..20) sans corrigé auto.', kind: 'note' },
      { num: 5, title: 'Règle de publication complète', desc: 'PC1 refuse si cours/exercice/test manquent ou si le niveau N-1 n’est pas encore publié.', kind: 'rule' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Mes niveaux et contenus', 'Enseignant', 'conditions', 'Python');

      c.text(inner.cx, inner.cy + 22, 'Mes niveaux et contenus pédagogiques', { size: c.isSketch ? 24 : 19, bold: true });
      c.text(inner.cx, inner.cy + 44, 'Créer -> publier cours et entraînement -> préparer le test -> publier le niveau pour mes apprenants.', { size: 12.5, color: '#64748B' });

      // Barre des 4 étapes + actualiser
      const by = inner.cy + 58;
      c.button(inner.cx, by, 140, 34, '1 · Créer niveau', 'primary');
      c.button(inner.cx + 148, by, 215, 34, '2 · Publier cours & exercice', 'secondary');
      c.button(inner.cx + 371, by, 175, 34, '3 · Préparer test final', 'secondary');
      c.button(inner.cx + 554, by, 155, 34, '4 · Publier niveau', 'primary');
      c.button(inner.cx + 717, by, 54, 34, '↻', 'secondary');
      c.pin(inner.cx + 788, by + 17, 1);

      c.banner(inner.cx, by + 44, inner.cw, 34, 'info', 'Les brouillons restent invisibles aux apprenants jusqu’à publication explicite.');

      // Liste des niveaux
      const ly = by + 88;
      c.box(inner.cx, ly, inner.cw, 175, { fill: '#FFFFFF', stroke: '#475569', rx: 8 });
      c.pin(inner.cx + inner.cw - 14, ly + 22, 2);

      const items = [
        { txt: 'python · Niveau 1 — Variables et types · PUBLIE (seuil 12/20, 3 essais)', sel: false },
        { txt: 'python · Niveau 2 — Conditions et boucles · BROUILLON (seuil 12/20, 3 essais)', sel: true },
        { txt: 'java · Niveau 1 — Classes et objets · BROUILLON (seuil 10/20, 3 essais)', sel: false },
      ];
      items.forEach((it, idx) => {
        const iy = ly + 14 + idx * 48;
        c.box(inner.cx + 14, iy, inner.cw - 28, 38, {
          fill: it.sel ? (c.isSketch ? '#DBEAFE' : '#E2E8F0') : '#F9FAFB',
          stroke: it.sel ? '#1E293B' : '#CBD5E1',
          rx: 6,
        });
        c.text(inner.cx + 26, iy + 24, it.txt, { size: 13, bold: it.sel });
      });

      // 3 Modales représentées en vignettes
      const my = ly + 190;
      const mw = Math.floor((inner.cw - 24) / 3);
      const mh = inner.cy + inner.ch - my;

      // Modale 1 : Créer
      c.box(inner.cx, my, mw, mh, { fill: '#F8FAFC', stroke: '#1E293B', dashed: true, rx: 8 });
      c.text(inner.cx + 12, my + 24, 'MODALE 1 : Créer un niveau', { size: 13, bold: true });
      c.pin(inner.cx + mw - 14, my + 20, 3);
      let fy = c.input(inner.cx + 12, my + 38, mw - 24, 30, 'Langage', 'python', { dropdown: true });
      fy = c.input(inner.cx + 12, fy + 8, mw - 24, 30, 'Titre', 'Conditions et boucles');
      fy = c.input(inner.cx + 12, fy + 8, 105, 30, 'Seuil /20', '12');
      c.input(inner.cx + 132, fy - 52, 105, 30, 'Tentatives', '3');
      c.button(inner.cx + mw - 92, my + mh - 40, 80, 30, 'OK', 'primary');

      // Modale 2 : Cours et Entraînement
      const m2x = inner.cx + mw + 12;
      c.box(m2x, my, mw, mh, { fill: '#F8FAFC', stroke: '#1E293B', dashed: true, rx: 8 });
      c.text(m2x + 12, my + 24, 'MODALE 2 : Cours & Exo', { size: 13, bold: true });
      c.pin(m2x + mw - 14, my + 20, 4);
      let fy2 = c.input(m2x + 12, my + 38, mw - 24, 28, 'Niveau', 'python · Niveau 2', { dropdown: true });
      c.text(m2x + 12, fy2 + 20, 'Cours et explications', { size: 12.5, bold: true });
      c.box(m2x + 12, fy2 + 26, mw - 24, 58, { fill: '#FFFFFF', stroke: '#6B7280', rx: 4 });
      c.text(m2x + 12, fy2 + 104, 'Énoncé d’entraînement + Code', { size: 12.5, bold: true });
      c.box(m2x + 12, fy2 + 110, mw - 24, 56, { fill: '#FFFFFF', stroke: '#6B7280', rx: 4 });
      c.button(m2x + mw - 92, my + mh - 40, 80, 30, 'Publier', 'primary');

      // Modale 3 : Test & Publication finale
      const m3x = m2x + mw + 12;
      c.box(m3x, my, mw, mh, { fill: '#F8FAFC', stroke: '#1E293B', dashed: true, rx: 8 });
      c.text(m3x + 12, my + 24, 'MODALE 3 : Sujet & Publier', { size: 13, bold: true });
      c.pin(m3x + mw - 14, my + 20, 5);
      c.text(m3x + 12, my + 52, 'Questions libres (1 à 20) :', { size: 12.5, bold: true });
      c.box(m3x + 12, my + 60, mw - 24, 108, { fill: '#FFFFFF', stroke: '#6B7280', rx: 4 });
      c.multiText(m3x + 20, my + 80, '1. Expliquez if/elif...\n2. Quel résultat pour...\n(Pas de corrigé auto ici)', mw - 40, 11.5, 18, { color: '#475569' });
      c.banner(m3x + 12, my + 178, mw - 24, 62, 'warn', 'Publication finale exige N-1 publié + cours + exo + test.');
      c.button(m3x + mw - 102, my + mh - 40, 90, 30, 'Confirmer', 'primary');
    },
  },

  // 09 — Enseignant : Mes apprenants et corrections
  {
    slug: '09-enseignant-apprenants-corrections',
    num: '09/11',
    title: 'Enseignant : Mes apprenants et corrections humaines',
    subtitle: 'ResultatsView & SuiviPedagogique(true) : copies affectées, brouillon privé et publication',
    role: 'Enseignant (PC3)',
    hta: 'E2 (Corriger et publier une copie)',
    meta: {
      rules: 'BF-E05..09 · RG-16 (brouillon privé) · RG-17 (note >= seuil pour valider)',
      flow: 'Brouillon -> privé enseignant | Publier (confirmation) -> CORRIGEE visible apprenant (E07)',
    },
    annotations: [
      { num: 1, title: 'Apprenants affectés (BF-E05)', desc: 'L’enseignant ne voit et corrige que les copies des apprenants qui lui sont affectés.', kind: 'rule' },
      { num: 2, title: 'Table des copies reçues', desc: 'Colonnes : apprenant, enseignant, langage, rang, statut (SOUMISE / CORRIGEE), note.', kind: 'note' },
      { num: 3, title: 'Lecture copie photographiée', desc: 'Affiche les questions figées au moment du test, les réponses et le code non exécuté.', kind: 'note' },
      { num: 4, title: 'Correction humaine & Seuil', desc: 'Note /20 + commentaire obligatoire + case Valider (vérifie note >= seuil photographié).', kind: 'rule' },
      { num: 5, title: 'Brouillon privé vs Publication', desc: '« Enregistrer brouillon » reste invisible ; « Publier » demande confirmation définitive.', kind: 'flow' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Mes apprenants et corrections', 'Enseignant', 'resultats', 'Python');

      c.text(inner.cx, inner.cy + 20, 'Mes apprenants et corrections', { size: c.isSketch ? 24 : 19, bold: true });
      c.text(inner.cx, inner.cy + 42, 'Copies réelles reçues sur PC1. La publication est une décision humaine.', { size: 12.5, color: '#64748B' });

      c.button(inner.cx, inner.cy + 54, 240, 30, 'Publier cours et entraînement', 'secondary');
      c.button(inner.cx + 250, inner.cy + 54, 175, 30, 'Préparer le test final', 'secondary');
      c.button(inner.cx + inner.cw - 115, inner.cy + 54, 115, 30, 'Actualiser', 'secondary');

      c.banner(inner.cx, inner.cy + 94, inner.cw, 34, 'info', 'Apprenants affectés : Amina B. · Yacine M. · Sara K.');
      c.pin(inner.cx + inner.cw - 14, inner.cy + 110, 1);

      const cols = [
        { label: 'Apprenant', w: 125 },
        { label: 'Enseignant', w: 120 },
        { label: 'Langage', w: 90 },
        { label: 'Rang', w: 65 },
        { label: 'Statut', w: 130 },
        { label: 'Note', w: inner.cw - 530 },
      ];
      const ty = c.table(
        inner.cx,
        inner.cy + 138,
        inner.cw,
        cols,
        [
          { cells: ['Amina B.', 'Prof. Karim', 'python', '2', 'SOUMISE', '—'], _selected: true },
          { cells: ['Yacine M.', 'Prof. Karim', 'python', '1', 'CORRIGEE', '14.5 / 20'] },
          { cells: ['Sara K.', 'Prof. Karim', 'python', '1', 'CORRIGEE', '9.0 / 20'] },
        ],
        32
      );
      c.pin(inner.cx + inner.cw - 14, inner.cy + 154, 2);

      // Deux colonnes en bas : Détail copie (gauche) + Panneau Correction humaine (droite)
      const by = ty + 14;
      const bh = inner.cy + inner.ch - by;
      const lw = 385;

      c.box(inner.cx, by, lw, bh, { fill: '#F9FAFB', stroke: '#475569', rx: 8 });
      c.text(inner.cx + 14, by + 24, 'Copie #42 — SOUMISE (seuil 12/20)', { size: 14, bold: true });
      c.pin(inner.cx + lw - 14, by + 22, 3);
      c.multiText(
        inner.cx + 14,
        by + 48,
        "1. [QCM] 12 >= 12 and 5 < 10 ?\nRéponse : True\n\n2. [Libre] if/elif vs deux if :\nRéponse : Avec elif la 2e branche est ignorée si la 1re est vraie.\n\nCode complémentaire (non exécuté) :\ndef est_admis(note):\n    return note >= 12",
        lw - 28,
        12,
        19,
        { color: '#1F2937' }
      );

      // Panneau correction humaine à droite
      const rx = inner.cx + lw + 16, rw = inner.cw - lw - 16;
      c.box(rx, by, rw, bh, { fill: '#FFFFFF', stroke: '#1E293B', strokeWidth: 1.8, rx: 8 });
      c.text(rx + 16, by + 26, 'Correction humaine (active si SOUMISE)', { size: 14.5, bold: true });
      c.pin(rx + rw - 16, by + 22, 4);

      let cy2 = c.input(rx + 16, by + 40, 210, 36, 'Note sur 20', '14.5');
      c.text(rx + 16, cy2 + 22, 'Commentaire pour l’apprenant (obligatoire)', { size: 13, bold: true });
      c.box(rx + 16, cy2 + 30, rw - 32, 102, { fill: '#FFFFFF', stroke: '#6B7280', rx: 6 });
      c.multiText(rx + 26, cy2 + 52, 'Bonne maîtrise des conditions et du cas limite >= 12. Pensez à documenter vos fonctions.', rw - 52, 12.5, 19, { color: '#374151' });

      c.checkbox(rx + 16, cy2 + 144, 'Valider le niveau (note >= seuil 12/20)', true);

      c.banner(rx + 16, cy2 + 178, rw - 32, 54, 'warn', 'Brouillon invisible ; la publication est définitive (confirmation requise).');

      c.button(rx + 14, by + bh - 48, 162, 36, 'Enregistrer brouillon', 'secondary');
      c.button(rx + 184, by + bh - 48, rw - 198, 36, 'Publier note et décision ->', 'primary');
      c.pin(rx + rw - 16, by + bh - 62, 5);
    },
  },

  // 10 — Administrateur : Comptes, rôles, filtres & affectations
  {
    slug: '10-administrateur-comptes-affectations',
    num: '10/11',
    title: 'Administrateur : Comptes, rôles, filtres & affectations',
    subtitle: 'ComptesView (AD1) : file SQL paginée, avis du bot, lots bornés et affectations',
    role: 'Administrateur (PC2)',
    hta: 'AD1 (Administrer demandes, comptes et affectations)',
    meta: {
      rules: 'BF-A01..08 · IHM-12/13/14 · RG-01 · RG-05 (bot = apprenants uniquement)',
      flow: 'Filtres -> Lot (<=20) + confirmation -> Rapport SMTP/erreurs | Affecter Apprenant -> Enseignant',
    },
    annotations: [
      { num: 1, title: 'Compteurs & Filtres SQL (IHM-12)', desc: 'Listes déroulantes immédiates ; saisie texte après 400 ms ; pagination 50 lignes/page.', kind: 'note' },
      { num: 2, title: 'Bandeau d’état de liste (IHM-13)', desc: 'Distingue Chargement, À jour, Aucun résultat filtré et Erreur + anciennes lignes + Réessayer.', kind: 'rule' },
      { num: 3, title: 'Table & Avis du bot de tri', desc: 'Cases Lot, État actif/statut, avis explicable du bot (infobulle motif, sans NVIDIA).', kind: 'note' },
      { num: 4, title: 'Décisions par lots bornées (<= 20)', desc: 'Accepter/Refuser/Supprimer avec confirmation ; rapport séparé pour échecs SMTP.', kind: 'rule' },
      { num: 5, title: 'Affectations & Outils compte', desc: 'Créer compte, renvoyer PIN, activer/désactiver, audit et affecter un apprenant (1 prof max).', kind: 'flow' },
    ],
    draw(c) {
      const inner = c.appFrame(44, 98, 1040, 856, 'Comptes et rôles', 'Administrateur', 'comptes', '');

      c.text(inner.cx, inner.cy + 20, 'Comptes et rôles', { size: c.isSketch ? 24 : 19, bold: true });
      c.text(inner.cx, inner.cy + 40, '14 demande(s) en attente · 128 compte(s) accepté(s) · 6 refusé(s) · 148 résultat(s) filtré(s)', { size: 12.5, color: '#475569', bold: true });

      // Barre de filtres
      const fy = inner.cy + 52;
      c.input(inner.cx, fy, 138, 30, '', 'Nom ou e-mail');
      c.input(inner.cx + 144, fy, 108, 30, '', 'ex. univ.dz');
      c.input(inner.cx + 258, fy, 118, 30, '', 'Tous statuts', { dropdown: true });
      c.input(inner.cx + 382, fy, 108, 30, '', 'Tous rôles', { dropdown: true });
      c.input(inner.cx + 496, fy, 112, 30, '', 'Actifs/Inact.', { dropdown: true });
      c.button(inner.cx + 614, fy, 68, 30, 'Filtrer', 'primary');
      c.button(inner.cx + 688, fy, 72, 30, 'Réinit.', 'secondary');
      c.pin(inner.cx + inner.cw - 12, fy + 15, 1);

      // Bandeau état de liste
      c.banner(inner.cx, fy + 38, inner.cw, 36, 'ok', 'Liste à jour · 148 résultat(s) filtré(s), page 1/3. (En cas d’échec : anciennes lignes + ↻ Réessayer)');
      c.pin(inner.cx + inner.cw - 12, fy + 56, 2);

      // Tableau comptes
      const cols = [
        { label: 'Lot', w: 42 },
        { label: 'Nom', w: 112 },
        { label: 'E-mail', w: 158 },
        { label: 'Rôle', w: 100 },
        { label: 'État', w: 120 },
        { label: 'Avis du bot de tri', w: inner.cw - 532 },
      ];
      const ty = c.table(
        inner.cx,
        fy + 84,
        inner.cw,
        cols,
        [
          { cells: ['[x]', 'Amina B.', 'amina@univ.dz', 'Apprenant', 'EN_ATTENTE', 'Pré-acceptation conseillée (domaine)'], _selected: true },
          { cells: ['[ ]', 'Karim D.', 'karim@univ.dz', 'Enseignant', 'EN_ATTENTE', 'Revue humaine requise (enseignant)'] },
          { cells: ['[ ]', 'Yacine M.', 'yacine@univ.dz', 'Apprenant', 'ACCEPTE (Actif)', 'Accepté (bot : domaine autorisé)'] },
          { cells: ['[ ]', 'Nabil R.', 'nabil@ext.org', 'Apprenant', 'ACCEPTE (Inactif)', 'Accepté · en attente d’activation PIN'] },
        ],
        34
      );
      c.pin(inner.cx + inner.cw - 12, fy + 104, 3);

      // Pagination
      c.button(inner.cx, ty + 10, 130, 30, 'Page précédente', 'disabled');
      c.text(inner.cx + 145, ty + 30, 'Page 1/3 · 50 ligne(s) affichée(s)', { size: 12.5, color: '#475569' });
      c.button(inner.cx + 390, ty + 10, 120, 30, 'Page suivante', 'secondary');

      // Barre d'actions unitaire & lots
      const ay = ty + 52;
      c.button(inner.cx, ay, 118, 32, 'Créer compte', 'primary');
      c.button(inner.cx + 126, ay, 115, 32, 'Renvoyer PIN', 'secondary');
      c.button(inner.cx + 249, ay, 85, 32, 'Accepter', 'primary');
      c.button(inner.cx + 342, ay, 80, 32, 'Refuser', 'secondary');
      c.button(inner.cx + 430, ay, 165, 32, 'Accepter sélection (1)', 'primary');
      c.button(inner.cx + 603, ay, 155, 32, 'Refuser sélection (1)', 'secondary');
      c.pin(inner.cx + inner.cw - 12, ay + 16, 4);

      const ay2 = ay + 40;
      c.button(inner.cx, ay2, 170, 32, 'Supprimer sélection (1)', 'danger');
      c.button(inner.cx + 178, ay2, 135, 32, 'Activer / Désact.', 'secondary');
      c.button(inner.cx + 321, ay2, 100, 32, 'Actualiser', 'secondary');
      c.button(inner.cx + 429, ay2, 120, 32, "Journal d'audit", 'secondary');

      // Zone basse : Affectation + vignette dialogue
      const afy = ay2 + 44;
      const afh = inner.cy + inner.ch - afy;
      c.box(inner.cx, afy, inner.cw, afh, { fill: '#F8FAFC', stroke: '#475569', dashed: true, rx: 8 });
      c.text(inner.cx + 14, afy + 24, 'Affectation pédagogique & Actions complémentaires (dialogue modal)', { size: 13.5, bold: true });
      c.pin(inner.cx + inner.cw - 12, afy + 22, 5);

      c.button(inner.cx + 14, afy + 38, 290, 34, 'Affecter un apprenant à un enseignant', 'primary');
      c.button(inner.cx + 314, afy + 38, 225, 34, 'Publier cours / Configurer test', 'secondary');
      c.multiText(inner.cx + 14, afy + 88, "Règle : 1 enseignant responsable par apprenant (bloqué si copie ouverte). Le bot ne pré-accepte jamais les enseignants ; toute décision reste journalisée.", inner.cw - 28, 12, 17, { color: '#374151' });
    },
  },

  // 11 — Transversal : Accessibilité (UX1), Barre latérale réduite & États normalisés
  {
    slug: '11-transversal-accessibilite-etats-sidebar',
    num: '11/11',
    title: 'Transversal : Barre latérale réduite, Accessibilité (UX1) & États',
    subtitle: 'Comportements transversaux : réduction 244<->76 px, F1/F6/F8, thèmes et bandeaux',
    role: 'Tous (Apprenant, Enseignant, Admin)',
    hta: 'UX1 (Clavier, lecteur d’écran et compréhension des états)',
    meta: {
      rules: 'IHM-06..08 · IHM-13 · IHM-15..20 · BNF-15 (accessibilité)',
      flow: 'Bouton menu 244<->76 px | F6 Régions | F1 Aide | F8 Relire',
    },
    annotations: [
      { num: 1, title: 'Barre latérale réductible (IHM-06)', desc: 'Bouton hamburger en haut à gauche aligné sur la barre (244 px <-> 76 px), info-bulles conservées.', kind: 'note' },
      { num: 2, title: 'Régions clavier F6 / Maj+F6', desc: 'Parcours sans souris : BarreTitre -> BarreLaterale -> Contenu principal ; vrais boutons Tab/Entrée.', kind: 'flow' },
      { num: 3, title: '4 états visuels distincts (IHM-13)', desc: 'Chargement, Succès, Avertissement/Vide, Erreur avec cause exacte et bouton Réessayer.', kind: 'rule' },
      { num: 4, title: 'Aide F1, Relecture F8 & Thèmes', desc: 'F1 ouvre le guide clavier ; F8 relit la dernière alerte ; Clair/Sombre/Rétro gardent le même ordre.', kind: 'note' },
    ],
    draw(c) {
      const x = 44, y = 98, w = 1040, h = 856;
      c.box(x, y, w, h, { fill: '#FFFFFF', stroke: '#475569', rx: 8 });

      // Quadrant 1 (Haut gauche) : Barre latérale réduite 76 px
      const q1x = x + 18, q1y = y + 18, q1w = 495, q1h = 435;
      c.box(q1x, q1y, q1w, q1h, { fill: '#F9FAFB', stroke: '#334155', rx: 8 });
      c.text(q1x + 14, q1y + 26, '1 · Barre latérale réduite en rail d’icônes (76 px)', { size: c.isSketch ? 18 : 13.5, bold: true });
      c.pin(q1x + q1w - 16, q1y + 22, 1);
      const innerC = c.appFrame(q1x + 14, q1y + 42, q1w - 28, q1h - 56, 'Parcours', 'Apprenant', 'parcours', 'Python', { sidebarCollapsed: true });
      c.text(innerC.cx, innerC.cy + 24, 'Contenu principal élargi', { size: 15, bold: true });
      c.multiText(innerC.cx, innerC.cy + 52, "• Bouton ≡ en haut à gauche bascule 244 px <-> 76 px\n• Icônes + couleurs + info-bulles + noms accessibles complets\n• La couleur n'est jamais le seul repère (IHM-07)", innerC.cw - 14, 12.5, 19, { color: '#374151' });
      c.box(innerC.cx, innerC.cy + 145, innerC.cw - 12, 85, { fill: '#F3F4F6', stroke: '#6B7280', dashed: true });
      c.text(innerC.cx + 14, innerC.cy + 174, 'Info-bulle au survol / focus :', { size: 12.5, bold: true });
      c.text(innerC.cx + 14, innerC.cy + 198, '« Parcours — écran actuel » / « Parcours Python »', { size: 12, color: '#475569' });

      // Quadrant 2 (Haut droite) : Régions clavier F6
      const q2x = q1x + q1w + 18, q2y = q1y, q2w = w - 54 - q1w, q2h = 435;
      c.box(q2x, q2y, q2w, q2h, { fill: '#F9FAFB', stroke: '#334155', rx: 8 });
      c.text(q2x + 14, q2y + 26, '2 · Régions clavier F6 / Maj+F6 (UX1)', { size: c.isSketch ? 18 : 13.5, bold: true });
      c.pin(q2x + q2w - 16, q2y + 22, 2);

      c.box(q2x + 16, q2y + 46, q2w - 32, 62, { fill: '#FFFFFF', stroke: '#1E293B', dashed: true });
      c.text(q2x + 28, q2y + 72, 'RÉGION 1 : Commandes de fenêtre (BarreTitre)', { size: 13, bold: true });
      c.text(q2x + 28, q2y + 92, 'Pastilles · ≡ · < > · Titre · Style · Thème · Aide (F1)', { size: 12, color: '#475569' });

      c.box(q2x + 16, q2y + 136, 175, 205, { fill: '#FFFFFF', stroke: '#1E293B', dashed: true });
      c.text(q2x + 26, q2y + 164, 'RÉGION 2 :', { size: 13, bold: true });
      c.multiText(q2x + 26, q2y + 186, 'Navigation du rôle (BarreLaterale)\n\nTab / Flèches / Entrée', 150, 12, 18, { color: '#475569' });

      c.box(q2x + 210, q2y + 136, q2w - 226, 205, { fill: '#FFFFFF', stroke: '#1E293B', dashed: true });
      c.text(q2x + 222, q2y + 164, 'RÉGION 3 : Écran métier courant', { size: 13, bold: true });
      c.multiText(q2x + 222, q2y + 186, 'Champs nommés (labelFor), secrets masqués par défaut, tableaux, boutons réels.\n\nPolling auto 5s ne vole JAMAIS le focus.', q2w - 250, 12, 18, { color: '#475569' });

      c.arrow(q2x + 100, q2y + 108, q2x + 100, q2y + 136, { label: 'F6' });
      c.arrow(q2x + 191, q2y + 238, q2x + 210, q2y + 238, { label: 'F6' });
      c.multiText(q2x + 16, q2y + 364, 'Raccourcis : F1 (Aide) · F8 (Relire alerte) · Échap (Fermer)', q2w - 32, 12, 17, { bold: true, color: '#1E293B' });
      c.text(q2x + 16, q2y + 402, 'Animations désactivables via -Dihm.animations=off (IHM-18)', { size: 12, color: '#475569' });

      // Quadrant 3 (Bas gauche) : 4 états normalisés
      const q3x = q1x, q3y = q1y + q1h + 18, q3w = q1w, q3h = h - 54 - q1h;
      c.box(q3x, q3y, q3w, q3h, { fill: '#F9FAFB', stroke: '#334155', rx: 8 });
      c.text(q3x + 14, q3y + 26, '3 · Retours d’état normalisés (IHM-03 / IHM-13)', { size: c.isSketch ? 17.5 : 13.5, bold: true });
      c.pin(q3x + q3w - 16, q3y + 22, 3);
      c.banner(q3x + 14, q3y + 44, q3w - 28, 48, 'load', 'Chargement : Actualisation en cours… (lignes précédentes signalées)');
      c.banner(q3x + 14, q3y + 104, q3w - 28, 48, 'ok', 'Succès : Liste à jour · 148 résultat(s) filtré(s), page 1/3.');
      c.banner(q3x + 14, q3y + 164, q3w - 28, 48, 'warn', 'Vide filtré : Aucun compte ne correspond aux filtres. Modifiez ou réinitialisez.');
      c.banner(q3x + 14, q3y + 224, q3w - 154, 58, 'err', 'Erreur : Service indisponible. Anciennes lignes conservées.');
      c.button(q3x + q3w - 130, q3y + 235, 116, 36, '↻ Réessayer', 'secondary');
      c.text(q3x + 14, q3y + 312, 'Notification toast : cause exacte sans doublon + relecture F8.', { size: 12.5, color: '#475569' });

      // Quadrant 4 (Bas droite) : Dialogue Aide F1 & Thèmes
      const q4x = q2x, q4y = q3y, q4w = q2w, q4h = q3h;
      c.box(q4x, q4y, q4w, q4h, { fill: '#F9FAFB', stroke: '#334155', rx: 8 });
      c.text(q4x + 14, q4y + 26, '4 · Dialogue Aide (F1) & Thèmes indépendants', { size: c.isSketch ? 17.5 : 13.5, bold: true });
      c.pin(q4x + q4w - 16, q4y + 22, 4);

      c.box(q4x + 14, q4y + 44, q4w - 28, 175, { fill: '#FFFFFF', stroke: '#1E293B', rx: 6 });
      c.text(q4x + 26, q4y + 68, 'Aide — Utiliser la plateforme sans souris (F1)', { size: 13, bold: true });
      c.multiText(q4x + 26, q4y + 90, 'Tab / Maj+Tab : commande suivante / précédente\nF6 / Maj+F6 : région suivante / précédente\nF8 : relire la dernière notification\nÉchap : fermer · Limite : recette NVDA Windows à valider.', q4w - 52, 11.8, 17, { color: '#374151' });
      c.button(q4x + q4w - 105, q4y + 180, 75, 28, 'Fermer', 'primary');

      // Thèmes
      c.text(q4x + 14, q4y + 244, 'Thèmes disponibles (même ordre & sens des contrôles) :', { size: 12.5, bold: true });
      const tw = Math.floor((q4w - 44) / 3);
      c.box(q4x + 14, q4y + 256, tw, 64, { fill: '#FFFFFF', stroke: '#475569', rx: 6 });
      c.text(q4x + 14 + tw / 2, q4y + 294, 'Clair', { size: 13, bold: true, anchor: 'middle' });
      c.box(q4x + 22 + tw, q4y + 256, tw, 64, { fill: '#E2E8F0', stroke: '#334155', rx: 6 });
      c.text(q4x + 22 + tw + tw / 2, q4y + 294, 'Sombre', { size: 13, bold: true, anchor: 'middle' });
      c.box(q4x + 30 + tw * 2, q4y + 256, tw, 64, { fill: '#F3F4F6', stroke: '#1E293B', dashed: true, rx: 6 });
      c.text(q4x + 30 + tw * 2 + tw / 2, q4y + 294, 'Rétro (CRT)', { size: 13, bold: true, anchor: 'middle' });
    },
  },
];

// Exécution
const requested = process.argv.slice(2);
for (const plate of PLATES) {
  if (requested.length > 0 && !requested.some((r) => plate.slug.includes(r))) continue;
  for (const mode of ['sketch', 'wire']) {
    const c = new Canvas(mode, 100 + PLATES.indexOf(plate) * 17);
    c.sheet(plate.num, plate.title, plate.subtitle, plate.role, plate.hta);
    plate.draw(c);
    c.annotationsPanel(plate.annotations, plate.meta);
    const res = c.save(plate.slug);
    console.log(`[${mode}] ${path.relative(ROOT, res.pngPath)}`);
  }
}
