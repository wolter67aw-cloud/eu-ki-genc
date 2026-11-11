# KI-GenC – Verbesserungs-Logbuch

Dieses Logbuch führt die tägliche automatische Verbesserung (geplante Aufgabe) fort.
Jede Verbesserung bekommt einen Eintrag: Datum, Version, was geändert wurde, warum.
**Vor jeder neuen Verbesserung lesen**, damit nichts doppelt gebaut oder zurückgedreht wird.

**Commit-Betreff = Text für „Was ist neu“.** Die Betreffzeilen der Commits unter app/ bzw. ios/ erscheinen
nach dem Update in der App, im GitHub-Release und in TestFlight („Was testen“). Deshalb: kurz, deutsch,
für Nutzer verständlich („Startseite: Aufträge als Favorit anheften“), keine Technik-Details. Rein interne
Commits mit „[intern]“ im Betreff kennzeichnen – sie werden nicht angezeigt.

## Bereits vorhanden (Stand 09.11.2026, v1.0.46)
- Startseite: Wunsch in eigenen Worten oder Schritt für Schritt (12 Kategorien), KI ordnet zu, Auftrag ausführen
- Chat mit Verlauf, Anhängen (Foto/Video/Datei), Spracheingabe, Vorlesen
- Studio: Textvorlagen, Übersetzung (23 Sprachen), Bilder mit mehreren Referenzen, FLUX-Kontext-Gesichtstreue
- Werkzeuge: Ideen-Sammlung (Moment, Sprachmemo, Skizze, Zeitkapsel, Ideen-Kreuzung, KI-Schlagwörter),
  Foto-Editor (mehrere Fotos, Filter, KI-Bearbeitung), Video (Analyse, Live-Aufnahme, Diashow),
  Transkription (Voxtral), Recherche mit Quellen, Daten & Statistik, Aufgaben mit Erinnerung, Ergebnisse, Code-Assistent
- Optionen: Schlüssel, Modelle, Bildgenerator (Mistral/Pollinations/BFL), Backup, Fehlerprotokoll, Updates
- Testversion (12 Codes, bis 05.12.2026) mit hinterlegtem, je Code verschlüsseltem Mistral-Schlüssel
- Automatik: Dependabot-Sammel-PR montags → Test-Build → Auto-Merge → Release; Hintergrund-Installation;
  wöchentliche Modellliste
- iOS-App (SwiftUI) mit denselben Funktionen; TestFlight-Upload sobald Apple-Secrets gesetzt sind

## Ideen-Speicher (noch offen)
- iOS-Chat: Antwort neu erzeugen / eigene Nachricht bearbeiten (Android hat es bereits)
- Widgets (Android-Startbildschirm) für Schnellerfassung
- Barrierefreiheit: größere Schrift, TalkBack/VoiceOver-Beschriftungen prüfen
- Angeheftete Aufträge per Ziehen umsortieren bzw. umbenennen (eigener Name statt erster Zeile)
- Android: App-Shortcuts (langes Drücken aufs Symbol) für angeheftete Aufträge

## Logbuch
- 09.11.2026 (v1.0.48): 9 App-Designs als „Synthesizer-Skins“ mit Live-Vorschau unter Optionen → Design (Original, Neon Synthwave, Analog Vintage, Studio Graphite, Retro Terminal, Sunset Wave, Midnight Gold, Deep Ocean, Arctic Glass hell) – Android & iOS. Wunsch des Nutzers.
- 09.11.2026 (v1.0.50): 6 weitere Designs mit eigenen Symbolen – GenC Hologramm, Vielfalt, Vegan, Pride, Zweisamkeit, Klassisch (hell) – und wählbares App-Symbol (Android: activity-alias, .MainActivity bleibt Standard; iOS: alternative App-Icons). Wunsch des Nutzers.
- 10.11.2026 (v1.0.52): Startseite „Zuletzt genutzt“ – die letzten 6 ausgeführten Aufträge (Kategorie, Auswahl, Inhalt, Zusatzfelder, ggf. bearbeiteter Auftrag) als Schnellwahl; Antippen stellt alles wieder her, einzeln entfernen oder „Leeren“. Eigener neuer Speicher (Android: SharedPreferences „recent_starts“, iOS: UserDefaults „recentStarts“), bestehende Daten unberührt. Android & iOS (iOS-Build geprüft). Aus dem Ideen-Speicher.
- 11.11.2026 (Build 1.0.54, **nicht veröffentlicht**): „Zuletzt genutzt“-Aufträge lassen sich per Pinnadel als Favorit anheften – angeheftete stehen oben, werden nicht verdrängt und von „Leeren“ nicht entfernt; erneutes Tippen löst sie. Speicher abwärtskompatibel (neues optionales Feld „pinned“). Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher. ⚠️ Der Release-Lauf auf main war grün, aber ohne Release: Secret KEYSTORE_PASSWORD fehlt in den Repository-Secrets („Signatur prüfen“ → signed=false). Nach Wiederherstellen des Secrets build.yml auf main erneut starten.
- 11.11.2026: „Was ist neu“ – nach jedem Update zeigt die App einmal die Neuerungen (alle Versionen seit der zuletzt gesehenen), jederzeit erneut unter Optionen → „Was ist neu?“. Quelle: Commit-Betreffs (scripts/whatsnew.py, beim Build in die App gepackt). Android: auch im Update-Dialog und in der Update-Mitteilung; iOS: zusätzlich „Was testen“ in TestFlight, Build wird automatisch der Gruppe „Tester“ hinzugefügt und zur Beta-Prüfung eingereicht (scripts/testflight_publish.py), Tag ios-N je Build. Wunsch des Nutzers.
- 11.11.2026 (v1.0.57): Secret KEYSTORE_PASSWORD wiederhergestellt – Release 1.0.57 veröffentlicht (enthält Favoriten und „Was ist neu“).
