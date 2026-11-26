# Store-Eintrag – KI-GenC

**App-Name (max. 30 Zeichen):** KI-GenC – Europäische KI

**Kurzbeschreibung (max. 80 Zeichen):**
Europäische KI für Chat, Texte, Bilder, Sprache und Code – ohne Werbung.

**Ausführliche Beschreibung (ohne Emojis, Stand 26.11.2026):**

```
KI-GenC ist deine vielseitige KI-Assistenz mit Mistral AI aus Europa. Sag einfach in eigenen Worten, was du brauchst – oder wähle Schritt für Schritt aus 12 Bereichen.

CHAT
• Fragen stellen, Fotos (auch direkt mit der Kamera) und Dateien mitschicken, per Sprache eingeben, Antworten vorlesen lassen
• Websuche mit Quellenangaben für aktuelle Themen
• „Gründlich nachdenken“ für knifflige Fragen
• Code ausführen: genaue Berechnungen und Diagramme mit echtem Python-Code
• Antwortstil wählen: knapp, ausführlich, förmlich, locker oder einfach erklärt
• Projekte mit eigenen Anweisungen und Wissen (Texte, PDF, Word)
• Gedächtnis: Die KI merkt sich, was du ihr sagst – jederzeit ansehen und löschen
• Antworten als Word, PDF oder Excel speichern und teilen
• Live-Vorschau für Webseiten, Grafiken und Diagramme aus Antworten
• Bilder erzeugen direkt im Chat: einfach „Erstelle ein Bild von …“ schreiben

STUDIO
• E-Mails, Briefe, Berichte, Zusammenfassungen und Social-Media-Texte
• Übersetzen in 23 Sprachen – auch per Mikrofon
• Bilder erzeugen in vielen Stilen, mit Referenzfotos

WERKZEUGE
• Foto-Editor: verbessern, Filter, mehrere Fotos zusammenfügen, KI-Bearbeitung
• Video: analysieren und Diashows aus deinen Fotos erstellen
• Werbevideo: kurze Clips für Reels, TikTok und Status aus Fotos, Videos und Dateien – mit KI-Werbetexten, Musik und auf Wunsch KI-animierten Fotos (Runway)
• Audio-Transkription: Sprache in Text, Protokoll und Zusammenfassung
• Daten-Analyse: Tabellen (CSV) auswerten, Statistiken und Prognosen
• Aufgaben und Termine mit Erinnerung
• Recherche mit Quellen
• Ideen-Sammlung mit Sprachmemo, Skizze und Zeitkapsel
• Code-Assistent: Programme schreiben, erklären und Fehler finden

DESIGN
15 Designs, mehrere App-Symbole und einstellbare Schriftgröße.

DATENSCHUTZ
Keine Werbung, kein Tracking, kein Benutzerkonto. Unterhaltungen, Bilder und Einstellungen bleiben auf deinem Gerät. Deine Anfragen gehen an Mistral AI (Rechenzentren in der EU). Du nutzt deinen eigenen Mistral-Schlüssel – der Tarif „Experiment“ ist kostenlos und in wenigen Minuten eingerichtet.

KI-Antworten können Fehler enthalten. Unangemessene KI-Inhalte kannst du direkt in der App melden.

KI-GenC ist ein unabhängiges Angebot und steht in keiner Verbindung zu Mistral AI.
```

**Kategorie:** Produktivität
**Zielgruppe:** 18 Jahre und älter
**Datenschutzerklärung (URL):** https://github.com/wolter67aw-cloud/eu-ki-genc/blob/main/docs/datenschutz.md

## Grafiken
- App-Symbol 512 × 512: `docs/play-store-icon-512.png`
- Vorstellungsgrafik 1024 × 500: `docs/play-store/vorstellungsgrafik-1024x500.png`
- 6 Screenshots 1080 × 1920: `docs/play-store/screenshot-*.png` (Nachbauten der echten Ansichten,
  neu erzeugen mit `python3 scripts/play_store_grafiken.py`)
- 6 Screenshots für Foldables (aufgeklappt) 1840 × 2208: `docs/play-store/foldable-*.png`
  (`python3 scripts/play_store_foldable.py`)

## Checkliste
1. ✅ Datenschutzerklärung mit Name, Anschrift und E-Mail
2. ✅ `reportEmail` in `gradle.properties` (Meldungen von KI-Inhalten per E-Mail)
3. ✅ Ziel-API 37 (Google verlangt seit 31.08.2026 mindestens 36)
4. App in der Play Console anlegen, Store-Eintrag und App-Inhalte ausfüllen
5. **KI-GenC-PlayStore.aab** aus dem neuesten GitHub-Release im geschlossenen Test hochladen
6. Geschlossener Test: mindestens 12 Tester, 14 Tage ohne Unterbrechung
7. Produktionszugang beantragen
8. Automatischer Upload: einmalig Dienstkonto einrichten und als Secret `PLAY_SERVICE_ACCOUNT_JSON` hinterlegen – danach lädt der Workflow „Google Play hochladen“ (`.github/workflows/play.yml`, `scripts/play_upload.py`) jede neue Version in den geschlossenen Test (Track per Repository-Variable `PLAY_TRACK`, Standard `alpha`)

Ausführliche Schritt-für-Schritt-Anleitung mit allen Formular-Antworten: Claude-Dokument
„KI-GenC im Google Play Store – Schritt für Schritt“ (19.11.2026).
