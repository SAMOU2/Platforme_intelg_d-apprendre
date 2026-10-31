"""Rend les contrôles exécutés et un schéma. Pas de captures JavaFX simulées."""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'docs/preuves'
FONTS = Path('/usr/share/fonts/truetype/dejavu')


def font(size, bold=False):
    return ImageFont.truetype(str(FONTS / ('DejaVuSans-Bold.ttf' if bold else 'DejaVuSans.ttf')), size)


def base(title, subtitle, height=1050):
    im = Image.new('RGB', (1600, height), '#101827')
    d = ImageDraw.Draw(im)
    d.text((65, 55), title, font=font(43, True), fill='#f0f6ff')
    d.text((65, 125), subtitle, font=font(22), fill='#a9bdd5')
    d.text((65, height - 60), '31 octobre 2026  •  Plateforme IHM  •  Vérification partielle, limites documentées',
           font=font(19), fill='#a9bdd5')
    return im, d


def tests():
    raw = (OUT / 'tests-deploiement.txt').read_text()
    im, d = base('Contrôles de déploiement exécutés', 'Rendu du journal réel — ce PNG n’est pas une capture JavaFX', height=1350)
    d.rounded_rectangle((65, 185, 1535, 1000), radius=18, fill='#18263a')
    lines = [l for l in raw.splitlines() if l.startswith('test_')]
    for i, line in enumerate(lines):
        name = line.split(' (', 1)[0]
        passed = line.endswith(' ... ok')
        d.text((95, 215 + i * 59), 'OK' if passed else 'ÉCHEC', font=font(23, True), fill='#71e5b2' if passed else '#ff8989')
        d.text((220, 215 + i * 59), name, font=font(22), fill='#eef4ff')
    summary = next((l for l in raw.splitlines() if l.startswith('Ran ')), '')
    d.text((95, 955), summary + (' — OK' if raw.rstrip().endswith('OK') else ' — vérifier le journal'), font=font(22, True), fill='#71e5b2')
    d.text((65, 1045), 'Non exécutés : JUnit, Docker/PostgreSQL, SMTP, affichage JavaFX.', font=font(25, True), fill='#ffc67f')
    d.text((65, 1100), 'JDK et Docker absents ; téléchargement du JDK et accès Maven bloqués.', font=font(23), fill='#d4deed')
    d.text((65, 1150), 'Source vérifiable : docs/preuves/tests-deploiement.txt', font=font(23), fill='#d4deed')
    im.save(OUT / 'tests-deploiement.png')


def architecture():
    im, d = base('Un seul serveur, plusieurs postes', 'Schéma explicatif de configuration — pas une preuve de connexion réseau exécutée')
    d.rounded_rectangle((390, 200, 1210, 480), radius=20, fill='#183c4b', outline='#71e5b2', width=2)
    for y, text, size in [(225, 'PC1 — Serveur local', 32), (285, 'Exemple IP : 192.168.1.10', 25),
                          (330, 'Docker + PostgreSQL : 127.0.0.1:5433', 25),
                          (375, 'Authentification HTTPS : 8443', 25), (420, 'Serveur de salle : 7777 TCP / UDP', 25)]:
        d.text((430, y), text, font=font(size, y == 225), fill='#eef4ff')
    d.line([(800, 480), (800, 555)], fill='#71e5b2', width=4)
    d.line([(300, 555), (1300, 555)], fill='#71e5b2', width=4)
    for x, title in [(65, 'PC2 — Admin'), (565, 'PC3 — Enseignant'), (1065, 'PC4… — Apprenants')]:
        d.line([(x + 235, 555), (x + 235, 620)], fill='#71e5b2', width=4)
        d.rounded_rectangle((x, 620, x + 470, 800), radius=16, fill='#18263a')
        d.text((x + 25, 645), title, font=font(27, True), fill='#eef4ff')
        d.text((x + 25, 700), 'Application JavaFX + JDK 25', font=font(22), fill='#d4deed')
        d.text((x + 25, 745), 'Sans Docker / base locale', font=font(22), fill='#71e5b2')
    d.text((65, 855), 'Adresse IP saisie dans le client ou préremplie avec IHM_SERVEUR.', font=font(25), fill='#eef4ff')
    d.text((65, 905), 'Certificat TLS approuvé côté client ; secrets DB et SMTP sur PC1 seulement.', font=font(23), fill='#a9bdd5')
    im.save(OUT / 'architecture-serveur-central.png')


if __name__ == '__main__':
    tests()
    architecture()
