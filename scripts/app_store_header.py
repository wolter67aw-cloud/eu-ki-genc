#!/usr/bin/env python3
"""Erzeugt App-Store-Bilder „Kopfzeile der Produktseite“ (3840x1646, 21:9) und
„Suchergebnisse“ (3840x2560, 3:2) für KI-GenC. Wichtiges liegt im sicheren Bereich laut Apple."""
import base64, pathlib
from playwright.sync_api import sync_playwright
from PIL import Image

REPO = pathlib.Path(__file__).resolve().parent.parent
OUT = REPO / "docs/app-store"
OUT.mkdir(parents=True, exist_ok=True)
ICON = "data:image/png;base64," + base64.b64encode((REPO / "docs/play-store-icon-512.png").read_bytes()).decode()


def page(w, h, safe, scale, k):
    sx, sy, sw, sh0 = safe
    sh = sh0 * k  # Schriftgrößen/Logo relativ zur Höhe des sicheren Bereichs
    # Hintergrund: Logo-Farben, Leuchten und Netz-Linien (wie im Symbol) über die ganze Fläche
    lines = []
    import random
    random.seed(7)
    nodes = [(random.uniform(0, w), random.uniform(0, h)) for _ in range(46)]
    for i, (x1, y1) in enumerate(nodes):
        for x2, y2 in nodes[i + 1:]:
            d = ((x1 - x2) ** 2 + (y1 - y2) ** 2) ** 0.5
            if d < w * 0.13:
                lines.append(f'<line x1="{x1:.0f}" y1="{y1:.0f}" x2="{x2:.0f}" y2="{y2:.0f}" stroke="url(#g)" stroke-opacity="{0.35 - d / (w * 0.13) * 0.3:.2f}" stroke-width="{3 * scale:.1f}"/>')
    dots = "".join(f'<circle cx="{x:.0f}" cy="{y:.0f}" r="{random.uniform(5, 11) * scale:.1f}" fill="{random.choice(["#22C3F5", "#3B7BF2", "#8B5CF6"])}" fill-opacity="0.75"/>' for x, y in nodes)
    svg = f'<svg width="{w}" height="{h}" style="position:absolute;inset:0"><defs><linearGradient id="g" x1="0" x2="1"><stop offset="0" stop-color="#22C3F5"/><stop offset="1" stop-color="#8B5CF6"/></linearGradient></defs>{"".join(lines)}{dots}</svg>'
    logo = sh * 0.62
    return f"""<!doctype html><html><head><meta charset="utf-8"><style>
*{{margin:0;padding:0;box-sizing:border-box}}
body{{width:{w}px;height:{h}px;overflow:hidden;position:relative;font-family:Roboto,sans-serif;color:#E6EAF5;
background:radial-gradient(45% 60% at 50% 50%,rgba(34,195,245,.22) 0%,transparent 70%),
radial-gradient(60% 80% at 10% 0%,#1d2d86 0%,transparent 60%),radial-gradient(60% 80% at 95% 100%,#43208f 0%,transparent 62%),
linear-gradient(160deg,#00081B,#0b0a2e)}}
.fade{{position:absolute;background:radial-gradient(closest-side at 50% 50%,rgba(0,8,27,.92) 0%,rgba(0,8,27,.75) 55%,transparent 100%);
 width:{sw * 1.5}px;height:{sh0 * 1.7}px;left:{sx - sw * 0.25}px;top:{sy - sh0 * 0.35}px}}
.safe{{position:absolute;left:{sx}px;top:{sy}px;width:{sw}px;height:{sh0}px;display:flex;align-items:center;justify-content:center;gap:{sh * 0.09}px}}
.safe img{{width:{logo}px;height:{logo}px;border-radius:{logo * 0.22}px;box-shadow:0 0 0 {4 * scale}px #2a3570,0 0 {120 * scale}px rgba(34,195,245,.45)}}
h1{{font-family:Poppins,sans-serif;font-weight:700;font-size:{sh * 0.3}px;line-height:1;letter-spacing:{-sh * 0.006}px;white-space:nowrap}}
h1 span{{background:linear-gradient(90deg,#22C3F5,#8B5CF6);-webkit-background-clip:text;background-clip:text;color:transparent}}
p{{font-size:{sh * 0.078}px;margin-top:{sh * 0.06}px;line-height:1.25;color:#dfe5f7;white-space:nowrap}}
.tags{{margin-top:{sh * 0.07}px;display:flex;gap:{sh * 0.03}px}}
.tags b{{font-weight:500;font-size:{sh * 0.05}px;border:{3 * scale}px solid #3a4a90;border-radius:{sh * 0.06}px;padding:{sh * 0.018}px {sh * 0.045}px;color:#cfd7ef;background:rgba(10,18,48,.6);white-space:nowrap}}
</style></head><body>{svg}<div class="fade"></div>
<div class="safe"><img src="{ICON}"><div><h1>KI-<span>GenC</span></h1>
<p>Europäische KI für Chat, Texte und Bilder</p>
<div class="tags"><b>Mistral AI</b><b>Ohne Werbung</b><b>Ohne Tracking</b></div></div></div>
</body></html>"""


JOBS = [
    # Name, Breite, Höhe, sicherer Bereich (x, y, b, h) laut App Store Connect
    ("kopfzeile-3840x1646.png", 3840, 1646, (1097, 493, 1646, 661), 1.6, 1.06),
    ("suchergebnisse-3840x2560.png", 3840, 2560, (836, 765, 2168, 1030), 2.0, 0.82),
]

with sync_playwright() as p:
    b = p.chromium.launch()
    for name, w, h, safe, scale, k in JOBS:
        pg = b.new_page(viewport={"width": w, "height": h}, device_scale_factor=1)
        pg.set_content(page(w, h, safe, scale, k)); pg.wait_for_timeout(300)
        out = OUT / name
        pg.screenshot(path=str(out))
        Image.open(out).convert("RGB").save(out, optimize=True)  # ohne Alphakanal (Apple-Vorgabe)
        pg.close()
    b.close()
print("ok")
