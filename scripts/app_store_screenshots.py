#!/usr/bin/env python3
"""Erzeugt App-Store-Bildschirmfotos für das iPhone (1320x2868 und 1206x2622) im Stil der iOS-App."""
import base64, pathlib, sys
from playwright.sync_api import sync_playwright

REPO = pathlib.Path("/home/claude/eu-ki-genc")
OUT = REPO / "docs/app-store"
OUT.mkdir(parents=True, exist_ok=True)
ICON = "data:image/png;base64," + base64.b64encode((REPO / "docs/play-store-icon-512.png").read_bytes()).decode()

I = {
 "home": "M10 20v-6h4v6h5v-8h3L12 3 2 12h3v8z",
 "chat": "M20 2H4c-1.1 0-2 .9-2 2v18l4-4h14c1.1 0 2-.9 2-2V4c0-1.1-.9-2-2-2z",
 "spark": "M19 9l1.25-2.75L23 5l-2.75-1.25L19 1l-1.25 2.75L15 5l2.75 1.25L19 9zm-7.5.5L9 4 6.5 9.5 1 12l5.5 2.5L9 20l2.5-5.5L17 12l-5.5-2.5zM19 15l-1.25 2.75L15 19l2.75 1.25L19 23l1.25-2.75L23 19l-2.75-1.25L19 15z",
 "apps": "M4 8h4V4H4v4zm6 12h4v-4h-4v4zm-6 0h4v-4H4v4zm0-6h4v-4H4v4zm6 0h4v-4h-4v4zm6-10v4h4V4h-4zm-6 4h4V4h-4v4zm6 6h4v-4h-4v4zm0 6h4v-4h-4v4z",
 "code": "M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z",
 "gear": "M19.4 13a7.5 7.5 0 000-2l2.1-1.6-2-3.5-2.5 1a7.6 7.6 0 00-1.7-1L15 3.3h-4l-.4 2.6a7.6 7.6 0 00-1.7 1l-2.5-1-2 3.5L6.6 11a7.5 7.5 0 000 2l-2.1 1.6 2 3.5 2.5-1c.5.4 1.1.7 1.7 1l.3 2.6h4l.4-2.6c.6-.3 1.2-.6 1.7-1l2.5 1 2-3.5L19.4 13zM12 15.5a3.5 3.5 0 110-7 3.5 3.5 0 010 7z",
 "mic": "M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3zm5.3-3c0 3-2.54 5.1-5.3 5.1S6.7 14 6.7 11H5c0 3.41 2.72 6.23 6 6.72V21h2v-3.28c3.28-.48 6-3.3 6-6.72h-1.7z",
 "send": "M2.01 21L23 12 2.01 3 2 10l15 2-15 2z",
 "plus": "M19 13h-6v6h-2v-6H5v-2h6V5h2v6h6v2z",
 "tune": "M3 17v2h6v-2H3zM3 5v2h10V5H3zm10 16v-2h8v-2h-8v-2h-2v6h2zM7 9v2H3v2h4v2h2V9H7zm14 4v-2H11v2h10zm-6-4h2V7h4V5h-4V3h-2v6z",
 "globe": "M12 2a10 10 0 100 20 10 10 0 000-20zm6.9 6h-2.9a15.7 15.7 0 00-1.4-3.6A8 8 0 0118.9 8zM12 4c.8 1.2 1.5 2.5 1.9 4h-3.8c.4-1.4 1.1-2.8 1.9-4zM4.3 14a8.2 8.2 0 010-4h3.4a16.5 16.5 0 000 4H4.3zm.8 2h3a15.7 15.7 0 001.4 3.6A8 8 0 015.1 16zm3-8h-3a8 8 0 014.3-3.6C8.9 5.5 8.4 6.7 8.1 8zM12 20c-.8-1.2-1.5-2.5-1.9-4h3.8c-.4 1.4-1.1 2.8-1.9 4zm2.3-6H9.7a14.7 14.7 0 010-4h4.6a14.7 14.7 0 010 4zm.3 5.6c.6-1.1 1.1-2.3 1.4-3.6h3a8 8 0 01-4.4 3.6zm1.8-5.6a16.5 16.5 0 000-4h3.4a8.2 8.2 0 010 4h-3.4z",
 "copy": "M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z",
 "speak": "M3 9v6h4l5 5V4L7 9H3zm13.5 3A4.5 4.5 0 0014 8v8a4.5 4.5 0 002.5-4z",
 "share": "M18 16.1c-.8 0-1.4.3-2 .8l-7.1-4.2c.1-.2.1-.5.1-.7s0-.5-.1-.7L16 7.2c.5.5 1.2.8 2 .8 1.7 0 3-1.3 3-3s-1.3-3-3-3-3 1.3-3 3c0 .2 0 .5.1.7L8 9.8C7.5 9.3 6.8 9 6 9c-1.7 0-3 1.3-3 3s1.3 3 3 3c.8 0 1.5-.3 2-.8l7.1 4.2c-.1.2-.1.4-.1.6 0 1.6 1.3 2.9 2.9 2.9s2.9-1.3 2.9-2.9-1.2-2.9-2.8-2.9z",
 "file": "M14 2H6c-1.1 0-2 .9-2 2v16c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V8l-6-6zm4 18H6V4h7v5h5v11zM8 15h8v2H8zm0-4h8v2H8z",
 "refresh": "M17.65 6.35A7.96 7.96 0 0012 4a8 8 0 108 8h-2a6 6 0 11-1.76-4.24L13 11h7V4l-2.35 2.35z",
 "photo": "M21 19V5c0-1.1-.9-2-2-2H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2zM8.5 13.5l2.5 3 3.5-4.5 4.5 6H5l3.5-4.5z",
 "movie": "M18 4l2 4h-3l-2-4h-2l2 4h-3l-2-4H8l2 4H7L5 4H4c-1.1 0-2 .9-2 2v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V4h-4z",
 "wave": "M7 18h2V6H7v12zm4 4h2V2h-2v20zm-8-8h2v-4H3v4zm12 4h2V6h-2v12zm4-8v4h2v-4h-2z",
 "insights": "M21 8c-1.45 0-2.26 1.44-1.93 2.51l-3.55 3.56c-.3-.09-.74-.09-1.04 0l-2.55-2.55C12.27 10.45 11.46 9 10 9c-1.45 0-2.27 1.44-1.93 2.52l-4.56 4.55C2.44 15.74 1 16.55 1 18c0 1.1.9 2 2 2 1.45 0 2.26-1.44 1.93-2.51l4.55-4.56c.3.09.74.09 1.04 0l2.55 2.55C12.73 16.55 13.54 18 15 18c1.45 0 2.27-1.44 1.93-2.52l3.56-3.55c1.07.33 2.51-.48 2.51-1.93 0-1.1-.9-2-2-2z",
 "check": "M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z",
 "search": "M15.5 14h-.79l-.28-.27A6.47 6.47 0 0016 9.5 6.5 6.5 0 109.5 16c1.61 0 3.09-.59 4.23-1.57l.27.28v.79l5 4.99L20.49 19l-4.99-5zm-6 0C7.01 14 5 11.99 5 9.5S7.01 5 9.5 5 14 7.01 14 9.5 11.99 14 9.5 14z",
 "bulb": "M9 21c0 .55.45 1 1 1h4c.55 0 1-.45 1-1v-1H9v1zm3-19C8.14 2 5 5.14 5 9c0 2.38 1.19 4.47 3 5.74V17c0 .55.45 1 1 1h6c.55 0 1-.45 1-1v-2.26c1.81-1.27 3-3.36 3-5.74 0-3.86-3.14-7-7-7z",
 "lock": "M18 8h-1V6c0-2.76-2.24-5-5-5S7 3.24 7 6v2H6c-1.1 0-2 .9-2 2v10c0 1.1.9 2 2 2h12c1.1 0 2-.9 2-2V10c0-1.1-.9-2-2-2zm-6 9c-1.1 0-2-.9-2-2s.9-2 2-2 2 .9 2 2-.9 2-2 2zm3.1-9H8.9V6c0-1.71 1.39-3.1 3.1-3.1 1.71 0 3.1 1.39 3.1 3.1v2z",
 "pin": "M16 12V4h1V2H7v2h1v8l-2 2v2h5.2v6h1.6v-6H18v-2l-2-2z",
 "play": "M8 5v14l11-7z",
}

def ic(name, size=22, color="currentColor"):
    return f'<svg width="{size}" height="{size}" viewBox="0 0 24 24" fill="{color}"><path d="{I[name]}"/></svg>'

CSS = """
*{box-sizing:border-box;margin:0;padding:0}
:root{--bg0:#00081B;--bg1:#06103A;--bg2:#150B3E;--cyan:#22C3F5;--violet:#A78BFA;--blue:#3B7BF2;--card:#0A1230;--card2:#121c45;--text:#E6EAF5;--muted:#A9B3D1}
body{width:440px;height:956px;overflow:hidden;font-family:Roboto,sans-serif;color:var(--text);
 background:radial-gradient(120% 70% at 20% 0%,#1b2a7a 0%,transparent 55%),radial-gradient(90% 60% at 100% 100%,#3a1c86 0%,transparent 60%),linear-gradient(180deg,#00081B,#0b0a2e)}
.cap{position:absolute;top:0;left:0;right:0;height:210px;padding:44px 22px 0;text-align:center}
.cap h1{font-family:Poppins,sans-serif;font-weight:600;font-size:27px;line-height:1.15;letter-spacing:-.3px}
.cap h1 b{background:linear-gradient(90deg,#22C3F5,#8B5CF6);-webkit-background-clip:text;background-clip:text;color:transparent;font-weight:600}
.cap p{margin-top:10px;font-size:16px;color:var(--muted);line-height:1.35}
.phone{position:absolute;left:50%;top:196px;transform:translateX(-50%);width:368px;height:742px;border-radius:54px;
 background:#05070f;padding:10px;box-shadow:0 0 0 2px #2a3570,0 30px 80px rgba(0,0,0,.6),0 0 60px rgba(34,195,245,.18)}
.scr{position:relative;width:100%;height:100%;border-radius:46px;overflow:hidden;
 background:linear-gradient(180deg,#00081B 0%,#06103A 55%,#150B3E 100%);font-size:14px}
.sb{height:44px;display:flex;justify-content:space-between;align-items:center;padding:6px 30px 0;font-size:14px;font-weight:600;color:var(--text);position:relative}.island{position:absolute;left:50%;top:9px;transform:translateX(-50%);width:104px;height:30px;border-radius:15px;background:#000}
.top{height:44px;display:flex;align-items:center;justify-content:center;padding:0 16px;gap:12px;font-size:16px;font-weight:600;position:relative}.top .r{position:absolute;right:14px;top:11px;display:flex;gap:14px;color:var(--cyan)}.top .l{position:absolute;left:14px;top:11px;color:var(--cyan)}
.top .sp{flex:1}
.body{padding:4px 14px;position:absolute;top:92px;bottom:82px;left:0;right:0;overflow:hidden}
.nav{position:absolute;bottom:0;left:0;right:0;height:82px;background:#0A1230;display:flex;justify-content:space-around;align-items:flex-start;padding-top:8px;border-top:1px solid #16214f}.homebar{position:absolute;bottom:8px;left:50%;transform:translateX(-50%);width:130px;height:5px;border-radius:3px;background:#E6EAF5}
.nav div{display:flex;flex-direction:column;align-items:center;gap:3px;font-size:10.5px;color:var(--muted);width:66px}
.nav .on{color:var(--text)}
.nav .on{color:var(--cyan)}
.nav span.pill{display:flex;justify-content:center;align-items:center;width:52px;height:28px;border-radius:14px}
.card{background:var(--card);border-radius:16px;padding:12px 14px;border:1px solid #17224e}
.muted{color:var(--muted)}
.h{font-size:17px;font-weight:500;margin:6px 0 4px}
.t{font-size:12.5px;color:var(--muted)}
.lbl{color:var(--cyan);font-size:13px;font-weight:500;margin:14px 0 8px}
.chip{display:inline-flex;align-items:center;gap:5px;border:1px solid #2a3770;border-radius:9px;padding:6px 10px;font-size:12.5px;margin:0 6px 7px 0;color:var(--text)}
.chip.on{background:#173a5c;border-color:#22C3F5}
.btn{display:flex;justify-content:center;align-items:center;gap:8px;height:42px;border-radius:21px;background:var(--cyan);color:#00142a;font-weight:500;font-size:14px}
.ubub{margin-left:auto;max-width:78%;background:#123b66;border-radius:20px 20px 6px 20px;padding:10px 14px;line-height:1.38}
.arow{display:flex;gap:10px;margin-top:14px}
.av{flex:none;width:30px;height:30px;border-radius:15px;background:#2b2a6b;color:var(--violet);display:flex;align-items:center;justify-content:center}
.ans{line-height:1.45;font-size:13.6px}
.ans h4{font-size:14.5px;margin:8px 0 4px;color:#fff}
.ans ul{padding-left:18px}
.ans li{margin:2px 0}
.acts{display:flex;gap:18px;color:var(--muted);margin-top:10px}
.inp{position:absolute;left:0;right:0;bottom:82px;padding:8px 12px 10px;display:flex;align-items:center;gap:10px;color:var(--cyan)}
.field{flex:1;height:42px;border-radius:21px;background:#121c45;color:var(--muted);display:flex;align-items:center;padding:0 14px;font-size:13.5px}
.row{display:flex;align-items:center;gap:12px}
.tool{display:flex;gap:12px;align-items:center;padding:11px 12px;margin-bottom:8px}
.tool .ico{width:40px;height:40px;border-radius:12px;background:#152a5a;color:var(--cyan);display:flex;align-items:center;justify-content:center;flex:none}
.tool b{font-weight:500;font-size:14.5px}
.src a{color:var(--cyan);text-decoration:none}
table.tb{border-collapse:collapse;width:100%;font-size:12.5px;margin-top:6px}
table.tb td,table.tb th{border:1px solid #26336b;padding:4px 6px;text-align:left}
table.tb th{background:#121c45}
"""

def nav(active):
    tabs = [("home","Start"),("chat","Chat"),("spark","Studio"),("apps","Werkzeuge"),("gear","Optionen")]
    return '<div class="nav">' + "".join(
        f'<div class="{"on" if l==active else ""}"><span class="pill">{ic(i,24)}</span>{l}</div>' for i,l in tabs) + '</div><div class="homebar"></div>'

def phone(title, body, active, extra="", top_icons=""):
    return f'''<div class="phone"><div class="scr">
<div class="sb"><span>9:41</span><span class="island"></span><span>▮▮▮ 86 %</span></div>
<div class="top"><span class="l">{ic("refresh",20) if active=="Chat" else ""}</span>{title}<span class="r">{top_icons}</span></div>
<div class="body">{body}</div>{extra}{nav(active)}</div></div>'''

def page(cap, sub, ph):
    return f'<!doctype html><html><head><meta charset="utf-8"><style>{CSS}</style></head><body><div class="cap"><h1>{cap}</h1><p>{sub}</p></div>{ph}</body></html>'

INPUT = lambda ph: f'<div class="inp">{ic("plus",26)}{ic("tune",24)}<div class="field">{ph}</div>{ic("mic",24)}<span style="width:38px;height:38px;border-radius:19px;background:var(--cyan);color:#00142a;display:flex;align-items:center;justify-content:center">{ic("send",20)}</span></div>'

shots = {}

# 1 Start
shots["1-start"] = page("Sag einfach, <b>was du willst</b>",
 "Die KI ordnet deinen Wunsch ein und erledigt ihn",
 phone("Start", f'''
<div class="h">Was möchtest du machen?</div>
<div class="t">Beschreib es in eigenen Worten – oder wähle unten Schritt für Schritt.</div>
<div class="card" style="margin-top:10px;min-height:96px;line-height:1.4">Eine freundliche E-Mail an meinen Vermieter, dass die Heizung seit Montag ausgefallen ist<div style="text-align:right;color:var(--cyan);margin-top:6px">{ic("mic",20)}</div></div>
<div class="btn" style="margin-top:12px">{ic("spark",18)} Genau definieren</div>
<div class="lbl">Zuletzt genutzt</div>
<div class="card row" style="margin-bottom:7px;padding:10px 12px"><span style="color:var(--cyan)">{ic("pin",18)}</span><span style="flex:1">Wochenplan gesund essen</span><span class="t">Planung</span></div>
<div class="card row" style="padding:10px 12px"><span class="muted">{ic("refresh",18)}</span><span style="flex:1">Übersetzen: Englisch → Deutsch</span><span class="t">Sprache</span></div>
<div class="lbl">Oder Schritt für Schritt</div>
<div><span class="chip">✍️ Schreiben</span><span class="chip">🌍 Übersetzen</span><span class="chip">🔎 Analysieren</span><span class="chip">🌐 Recherchieren</span><span class="chip">🗓️ Planen</span><span class="chip">🎓 Lernen</span><span class="chip">🎨 Bild erstellen</span><span class="chip">💻 Programmieren</span><span class="chip">🖼️ Foto bearbeiten</span><span class="chip">💡 Idee festhalten</span></div>
''', "Start"))

# 2 Chat mit Websuche
shots["2-chat"] = page("Chatten mit <b>europäischer KI</b>",
 "Mit Websuche, Quellen, Spracheingabe und Vorlesen",
 phone("Ausflug am Wochenende", f'''
<div class="ubub">Wie wird das Wetter am Samstag in der Oberpfalz – lohnt sich eine Wanderung?</div>
<div class="arow"><div class="av">{ic("spark",18)}</div><div class="ans">
<h4>Samstag: gute Wanderbedingungen</h4>
<ul><li>Vormittags trocken, 6–9 °C</li><li>Ab 15 Uhr leichter Regen möglich</li><li>Wind schwach aus West</li></ul>
<p style="margin-top:6px">Tipp: früh starten und eine Rundtour unter 4 Stunden wählen.</p>
<p class="src" style="margin-top:8px"><b>Quellen:</b><br>1. <a>Wettervorhersage Oberpfalz</a><br>2. <a>Regionaler Wetterbericht</a></p>
<div class="acts">{ic("copy",18)}{ic("speak",18)}{ic("share",18)}{ic("file",18)}{ic("refresh",18)}</div>
</div></div>''', "Chat",
 extra='<div style="position:absolute;left:12px;bottom:134px"><span class="chip on" style="color:var(--cyan)">' + ic("globe",15) + ' Websuche</span></div>' + INPUT("Frag mit Websuche …"),
 top_icons=f'<span style="color:var(--cyan)">{ic("pin",22)}</span>'))

# 3 Studio Übersetzen
shots["3-studio"] = page("Texte schreiben und <b>übersetzen</b>",
 "E-Mails, Berichte, Zusammenfassungen – in 23 Sprachen",
 phone("Studio", f'''
<div style="display:flex;background:#121c45;border-radius:9px;padding:2px;margin-bottom:6px"><span style="flex:1;text-align:center;padding:5px;font-size:13px;background:#2a3b7a;border-radius:7px">Text</span><span style="flex:1;text-align:center;padding:5px;font-size:13px">Bild</span></div><div class="lbl" style="margin-top:4px">Vorlage</div><div><span class="chip">E-Mail</span><span class="chip on">Übersetzen</span><span class="chip">Zusammenfassen</span><span class="chip">Bericht</span></div>
<div class="lbl" style="margin-top:4px">Sprache</div>
<div><span class="chip">Englisch</span><span class="chip on">Französisch</span><span class="chip">Italienisch</span><span class="chip">Spanisch</span></div>
<div class="card" style="margin-top:6px;line-height:1.4">Vielen Dank für Ihre schnelle Antwort. Wir freuen uns auf das Treffen am Donnerstag.<div style="text-align:right;color:var(--cyan);margin-top:4px">{ic("mic",20)}</div></div>
<div class="t" style="margin:6px 2px">Mikrofon spricht: Deutsch ▾</div>
<div class="btn">{ic("spark",18)} Übersetzen</div>
<div class="card" style="margin-top:12px;line-height:1.45;border-color:#2b4a8a">
<div class="t" style="color:var(--violet);margin-bottom:4px">Französisch</div>
Merci beaucoup pour votre réponse rapide. Nous nous réjouissons de la réunion de jeudi.
<div class="acts">{ic("copy",18)}{ic("speak",18)}{ic("share",18)}{ic("file",18)}</div></div>
''', "Studio"))

# 4 Werkzeuge
tools = [("code","Code-Assistent","Programme & Automationen"),("bulb","Ideen-Sammlung","Moment, Sprachmemo, Skizze, Zeitkapsel"),("photo","Foto-Editor","Filter, Licht, KI-Bearbeitung"),
 ("movie","Video","Analysieren, aufnehmen, Diashow"),("wave","Transkription","Audio & Video zu Text"),("globe","Recherche","Websuche mit Quellen"),
 ("insights","Daten & Statistik","CSV analysieren"),("check","Aufgaben","Planen mit Erinnerung")]
shots["4-werkzeuge"] = page("Viele Werkzeuge <b>in einer App</b>",
 "Fotos, Videos, Audio, Daten, Aufgaben und Recherche",
 phone("Werkzeuge", "".join(f'<div class="card tool"><div class="ico">{ic(i,22)}</div><div><b>{n}</b><div class="t">{d}</div></div></div>' for i,n,d in tools), "Werkzeuge"))

# 5 Code ausführen + Datei
bars = [("Jan",42),("Feb",55),("Mär",61),("Apr",48),("Mai",73),("Jun",88)]
svg = '<svg viewBox="0 0 300 150" width="100%" height="130" preserveAspectRatio="xMidYMid meet" style="background:#fff;border-radius:10px;margin-top:8px">' + \
 '<text x="150" y="16" font-size="11" text-anchor="middle" fill="#222" font-family="Roboto">Umsatz je Monat (Tsd. €)</text>' + \
 "".join(f'<rect x="{30+i*44}" y="{130-v}" width="28" height="{v}" fill="#3B7BF2"/><text x="{44+i*44}" y="144" font-size="10" text-anchor="middle" fill="#333" font-family="Roboto">{m}</text><text x="{44+i*44}" y="{126-v}" font-size="9" text-anchor="middle" fill="#333" font-family="Roboto">{v}</text>' for i,(m,v) in enumerate(bars)) + \
 '<line x1="22" y1="130" x2="290" y2="130" stroke="#999"/></svg>'
shots["5-code"] = page("Rechnen, Diagramme, <b>Word &amp; PDF</b>",
 "Echter Python-Code für genaue Ergebnisse – als Datei teilen",
 phone("Halbjahr auswerten", f'''
<div class="ubub">Werte meine Umsätze Jan–Jun aus und mach ein Diagramm.</div>
<div class="arow"><div class="av">{ic("spark",18)}</div><div class="ans" style="flex:1">
<h4>Ergebnis</h4>
<table class="tb"><tr><th>Kennzahl</th><th>Wert</th></tr><tr><td>Summe</td><td>367 Tsd. €</td></tr><tr><td>Wachstum Jan→Jun</td><td>+109,5 %</td></tr></table>
{svg}
<div class="card" style="margin-top:8px;padding:8px 10px;font-size:12.5px">
<div class="t" style="margin-bottom:4px">Als Datei</div>
<span class="chip" style="margin-bottom:2px">Word (.docx)</span><span class="chip" style="margin-bottom:2px">PDF</span><span class="chip" style="margin-bottom:2px">Excel (.xlsx)</span></div>
</div></div>''', "Chat",
 extra='<div style="position:absolute;left:12px;bottom:134px"><span class="chip on" style="color:var(--cyan)">ƒ Code ausführen</span></div>' + INPUT("Rechnen, auswerten, Diagramm …")))

# 6 Datenschutz & Designs
themes = [("Original","#00081B","#22C3F5"),("Neon Synthwave","#0D0221","#FF2E97"),("Deep Ocean","#001219","#2EC4B6"),("Midnight Gold","#050505","#E5C07B"),("Sunset Wave","#1A0B2E","#FF7A45"),("Klassisch","#FFFFFF","#3F51B5")]
sw = "".join(f'<div style="width:110px;margin:0 7px 8px 0;display:inline-block"><div style="height:54px;border-radius:12px;background:{b};border:2px solid {"#22C3F5" if n=="Original" else "#26336b"};display:flex;align-items:flex-end;padding:7px"><span style="width:36px;height:8px;border-radius:4px;background:{c}"></span></div><div class="t" style="margin-top:4px;color:var(--text)">{n}</div></div>' for n,b,c in themes)
shots["6-datenschutz"] = page("Keine Werbung. <b>Kein Tracking.</b>",
 "Deine Chats bleiben auf deinem Gerät – KI aus Europa",
 phone("Optionen", f'''
<div class="lbl" style="margin-top:2px">Design</div>
<div>{sw}</div>
<div class="lbl" style="margin-top:4px">Gedächtnis</div>
<div class="card row"><div style="flex:1"><div>Gedächtnis nutzen</div><div class="t">Die KI kennt in jedem Chat, was sie sich über dich gemerkt hat. Sag z. B. „Merk dir, dass …“</div></div><span style="width:44px;height:24px;border-radius:12px;background:var(--cyan);position:relative;flex:none"><span style="position:absolute;right:3px;top:3px;width:18px;height:18px;border-radius:9px;background:#00142a"></span></span></div>
<div class="lbl">Info</div>
<div class="card" style="padding:4px 14px">
<div class="row" style="padding:10px 0;border-bottom:1px solid #1b2758"><span style="color:var(--cyan)">{ic("lock",20)}</span><span style="flex:1">Datenschutz</span><span class="muted">›</span></div>
<div class="row" style="padding:10px 0;border-bottom:1px solid #1b2758"><span style="color:var(--cyan)">{ic("spark",20)}</span><span style="flex:1">Was ist neu?</span><span class="muted">›</span></div>
<div class="row" style="padding:10px 0"><span style="color:var(--cyan)">{ic("check",20)}</span><span style="flex:1">Zustimmung zur KI-Nutzung widerrufen</span></div></div>
''', "Optionen"))

from PIL import Image
with sync_playwright() as p:
    b = p.chromium.launch()
    pg = b.new_page(viewport={"width": 440, "height": 956}, device_scale_factor=3)
    for name, html in shots.items():
        pg.set_content(html); pg.wait_for_timeout(150)
        big = OUT / f"iphone-6.9-{name}.png"
        pg.screenshot(path=str(big))
        im = Image.open(big).convert("RGB")  # ohne Alphakanal (Apple-Vorgabe)
        im.save(big, optimize=True)
        im.resize((1206, 2622), Image.LANCZOS).save(OUT / f"iphone-6.3-{name}.png", optimize=True)
    b.close()
print("ok")
