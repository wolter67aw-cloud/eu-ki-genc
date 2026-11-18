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
**Reihenfolge verbindlich (Wunsch des Nutzers, 18.11.2026): immer die OBERSTE offene Idee als Nächstes umsetzen,
eine pro Lauf; erledigte Ideen hier streichen.** Claude-Funktionen werden mit Mistral nachgebaut (nicht Claude selbst).

Claude-Funktionen (Wunsch des Nutzers, in dieser Reihenfolge):
- Gedächtnis: App merkt sich Fakten über den Nutzer (Name, Beruf, Vorlieben) chatübergreifend – Liste unter Optionen einsehbar/bearbeitbar/löschbar, Schalter an/aus, „Merk dir …“ im Chat, Fakten gehen als Systemtext mit
- Dateien erzeugen: Antwort als Word (.docx), PDF oder Excel/CSV speichern und teilen (ohne neue Bibliothek: DOCX/XLSX als ZIP+XML, PDF über PdfDocument bzw. UIGraphicsPDFRenderer)
- Live-Vorschau (Artefakte): HTML/Web-Apps/Mermaid-Diagramme aus Antworten in der App ansehen (WebView/WKWebView, ohne Netz-Zugriff für Fremdinhalte)
- Websuche im Chat: Schalter „Web“ im Chat – bei aktuellen Fragen Mistral-Websuche mit Quellenangabe (wie Recherche-Werkzeug)
- Code ausführen & Datenanalyse: Mistral Code-Interpreter (Agents/Conversations-API) im Chat und im Daten-Werkzeug
- Nachdenk-Modus & Stile: Schalter „Gründlich nachdenken“ (Magistral) und Antwortstil (Normal, Knapp, Ausführlich, Förmlich, Locker)

Weitere Ideen:
- iOS-Chat: Antwort neu erzeugen / eigene Nachricht bearbeiten (Android hat es bereits)
- Widgets (Android-Startbildschirm) für Schnellerfassung
- Barrierefreiheit: größere Schrift, TalkBack/VoiceOver-Beschriftungen prüfen
- Angeheftete Aufträge per Ziehen umsortieren
- Android: App-Shortcuts (langes Drücken aufs Symbol) für angeheftete Aufträge
- Im geöffneten Chat suchen (Lupe oben, mit „nächster/vorheriger Treffer“)
- Chat-Verlauf: Gruppen ein-/ausklappen, wenn „Älter“ sehr lang wird
- Android-Chat: auch Dateien (PDF, Word, Text) direkt anhängen (bisher nur im Studio)
- iOS-Verlauf: Unterhaltungen umbenennen (Android hat es bereits)
- Angeheftete Unterhaltungen im Verlauf per Ziehen umsortieren
- Angeheftete Unterhaltungen: Schnellwahl auch auf der App-Startseite (neben „Zuletzt genutzt“)
- Chat: „Weiter, wo du aufgehört hast“ – zuletzt bearbeitete Unterhaltung als Hinweis auf der leeren Chat-Ansicht

## Logbuch
- 09.11.2026 (v1.0.48): 9 App-Designs als „Synthesizer-Skins“ mit Live-Vorschau unter Optionen → Design (Original, Neon Synthwave, Analog Vintage, Studio Graphite, Retro Terminal, Sunset Wave, Midnight Gold, Deep Ocean, Arctic Glass hell) – Android & iOS. Wunsch des Nutzers.
- 09.11.2026 (v1.0.50): 6 weitere Designs mit eigenen Symbolen – GenC Hologramm, Vielfalt, Vegan, Pride, Zweisamkeit, Klassisch (hell) – und wählbares App-Symbol (Android: activity-alias, .MainActivity bleibt Standard; iOS: alternative App-Icons). Wunsch des Nutzers.
- 10.11.2026 (v1.0.52): Startseite „Zuletzt genutzt“ – die letzten 6 ausgeführten Aufträge (Kategorie, Auswahl, Inhalt, Zusatzfelder, ggf. bearbeiteter Auftrag) als Schnellwahl; Antippen stellt alles wieder her, einzeln entfernen oder „Leeren“. Eigener neuer Speicher (Android: SharedPreferences „recent_starts“, iOS: UserDefaults „recentStarts“), bestehende Daten unberührt. Android & iOS (iOS-Build geprüft). Aus dem Ideen-Speicher.
- 11.11.2026 (Build 1.0.54, **nicht veröffentlicht**): „Zuletzt genutzt“-Aufträge lassen sich per Pinnadel als Favorit anheften – angeheftete stehen oben, werden nicht verdrängt und von „Leeren“ nicht entfernt; erneutes Tippen löst sie. Speicher abwärtskompatibel (neues optionales Feld „pinned“). Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher. ⚠️ Der Release-Lauf auf main war grün, aber ohne Release: Secret KEYSTORE_PASSWORD fehlt in den Repository-Secrets („Signatur prüfen“ → signed=false). Nach Wiederherstellen des Secrets build.yml auf main erneut starten.
- 11.11.2026: „Was ist neu“ – nach jedem Update zeigt die App einmal die Neuerungen (alle Versionen seit der zuletzt gesehenen), jederzeit erneut unter Optionen → „Was ist neu?“. Quelle: Commit-Betreffs (scripts/whatsnew.py, beim Build in die App gepackt). Android: auch im Update-Dialog und in der Update-Mitteilung; iOS: zusätzlich „Was testen“ in TestFlight, Build wird automatisch der Gruppe „Tester“ hinzugefügt und zur Beta-Prüfung eingereicht (scripts/testflight_publish.py), Tag ios-N je Build. Wunsch des Nutzers.
- 11.11.2026 (v1.0.57): Secret KEYSTORE_PASSWORD wiederhergestellt – Release 1.0.57 veröffentlicht (enthält Favoriten und „Was ist neu“).
- 12.11.2026 (v1.0.61): Angeheftete Favoriten auf der Startseite lassen sich umbenennen (Stift-Symbol → eigener Name, max. 60 Zeichen; leer = wieder erste Zeile des Auftrags). Der Name bleibt beim Lösen/erneuten Ausführen erhalten. Speicher abwärtskompatibel (neues optionales Feld „name“ in recent_starts bzw. recentStarts). Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 12.11.2026: Zugangscode – iOS-Tester konnten das I in „KIG“ nicht eintippen. „KIG-“ jetzt fest vorgegeben, nur 8 Zeichen eingeben; Präfix auf beiden Plattformen optional/tolerant (KG, K1G …). Build 56. Neuer Workflow „TestFlight verteilen“ (manuell, Build-Nr.) und Wiederholung beim Zuordnen zur Gruppe.
- 13.11.2026 (v1.0.66): Chat-Verlauf durchsuchen – Treffer zeigen jetzt die Fundstelle (Ausschnitt mit hervorgehobenem Suchbegriff, „Du:“/„KI:“, Trefferanzahl) und oben „N Unterhaltungen gefunden“. iOS bekommt die Volltextsuche im Verlauf neu (Titel + alle Nachrichten; Wischen-Löschen arbeitet auf der gefilterten Liste). Keine Datenformat-Änderung. Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 14.11.2026 (v1.0.68): Chat-Verlauf – Antippen eines Suchtreffers öffnet die Unterhaltung direkt an der ersten Fundstelle (statt am Ende) und hebt die gefundene Nachricht ca. 2,5 s farbig hervor. Ohne Suchbegriff unverändert. Keine Datenformat-Änderung. Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 15.11.2026 (v1.0.70): Chat-Verlauf nach Zeitraum gruppiert – Überschriften „Heute“, „Gestern“, „Letzte 7 Tage“, „Letzte 30 Tage“, „Älter“ (Kalendertage, Zeitumstellung berücksichtigt), auch bei aktiver Suche; Überschriften für TalkBack/VoiceOver als Überschrift markiert. iOS: Abschnitte in der Liste, Wischen-Löschen je Abschnitt. Keine Datenformat-Änderung. Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 16.11.2026 (v1.0.72): Chat-Verlauf – Unterhaltungen anheften. Angeheftete stehen in eigener Gruppe „Angeheftet“ über den Zeiträumen (auch bei Suche). Android: Pinnadel-Knopf in jeder Zeile; iOS: nach rechts wischen bzw. langes Drücken, Pinnadel vor dem Titel, Löschen jetzt als Wisch-Aktion/Kontextmenü; angeheftete werden von der 100er-Grenze nicht verdrängt. Speichern einer Antwort setzt den Anheft-Status nicht zurück. Datenformat abwärtskompatibel (neues optionales Feld „pinned“; iOS lädt ältere Chats tolerant). Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 17.11.2026 (v1.0.75): Chat – geöffnete Unterhaltung direkt oben in der Leiste per Pinnadel anheften/lösen (ohne Umweg über den Verlauf); Knopf nur sichtbar, wenn der Chat Nachrichten hat, gefüllte Pinnadel = angeheftet. Android: kurze Rückmeldung als Snackbar; Anheft-Status bleibt erhalten, auch wenn er während einer laufenden Antwort geändert wird. iOS: Status kommt aus dem gespeicherten Verlauf, noch nicht gespeicherte Chats werden beim Anheften angelegt; VoiceOver-Beschriftungen für Pinnadel und „Neue Unterhaltung“. Keine Datenformat-Änderung. Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 18.11.2026 (v1.0.77): Chat – angeheftete Unterhaltungen erscheinen als Schnellwahl „Angeheftet“ auf der leeren Chat-Startansicht (max. 6, zuletzt genutzt zuerst, „Weitere N im Verlauf“); Antippen setzt die Unterhaltung fort. Darunter Überschrift „Vorschläge“; Überschriften für TalkBack/VoiceOver markiert, Schnellwahl mit eigener Beschriftung. Android: leere Ansicht jetzt scrollbar (kein Abschneiden auf kleinen Bildschirmen), Chat- und Code-Modus getrennt. Keine Datenformat-Änderung. Android & iOS, beide Builds grün (iOS geprüft). Aus dem Ideen-Speicher.
- 18.11.2026 (iOS-Build 71): Apple-Hinweis ITMS-90725 behoben – iOS wird jetzt auf dem GitHub-Runner „xcode-27“ (Xcode 27.2, iOS-SDK 27.2) statt macos-15-intel (Xcode 26.3, SDK 26.2) gebaut; ab April 2027 ist das iOS-27-SDK Pflicht. Workflow zeigt das SDK an und warnt bei SDK < 27. Mindest-iOS (17.0) unverändert. Build grün, an TestFlight übergeben. Auf Wunsch des Nutzers.
- 18.11.2026 (v1.0.79, iOS-Build 73): Übersetzen mit Mikrofon – im Übersetzen-Formular (Startseite) und in der Text-Werkstatt lässt sich der Text einsprechen; darunter „Mikrofon spricht: …“ zur Wahl der Sprechsprache (14 Sprachen, Standard Deutsch), z. B. Englisch sprechen und ins Deutsche übersetzen. Mikrofon zusätzlich in allen übrigen Inhaltsfeldern (Gerätesprache bzw. Deutsch). Android: System-Spracherkennung, keine neue Berechtigung; iOS: Diktat mit wählbarer Sprache. Keine Datenformat-Änderung. Android & iOS, beide Builds grün (iOS geprüft). Wunsch des Nutzers.
- 18.11.2026 (v1.0.81, iOS-Build 75): Musik beim Start – Optionen → „Musik beim Start“: beim Öffnen der App läuft die aktuelle Nr. 1 der Charts als 30-Sekunden-Hörprobe (öffentliche Deezer-API, ohne Schlüssel), Genre wählbar (15 Genres, leere Genre-Charts → allgemeine Charts), „Jetzt anhören“ zum Ausprobieren. Standardmäßig AUS. Spielt höchstens alle 10 Min., nicht im Lautlos-/Vibrationsmodus (iOS: Lautlos-Schalter), nicht wenn schon Musik läuft; leise eingeblendet, stoppt beim Verlassen der App. Leiste über der Navigation mit Titel, „Ganzen Song öffnen“ (Deezer-Link) und Stopp. Ganze Songs aus Lizenzgründen nicht. Datenschutzerklärung um Deezer ergänzt. Neue Einstellungen start_music/music_genre (abwärtskompatibel). Keine neue Bibliothek/Berechtigung. Android & iOS, beide Builds grün (iOS geprüft). Wunsch des Nutzers.
- 18.11.2026 (v1.0.83, iOS-Build 77): Mehrere Bilder/Dateien gemeinsam verarbeiten (Nutzer-Meldung). Foto-Editor: „Zu einem Bild zusammenfügen“ (Nebeneinander/Untereinander/Raster, ohne KI, Gesichter unverändert) → neues Foto, das gefiltert/gespeichert/mit KI bearbeitet werden kann; Hinweistext zur KI-Kombination (echt nur mit BFL-Schlüssel). Studio/Chat-Anhänge: Bilder reihum auf alle Anhänge verteilt (max. 8, je Anhang gleichmäßig gestreut), jeder Anhang benannt mit Bildnummern + Anweisung, alle gemeinsam auszuwerten – iOS nahm vorher nur die ersten 8 Bilder (spätere Fotos/PDF-Seiten fielen weg). Android-Chat: bis zu 6 Fotos pro Nachricht (neues optionales Feld imagePaths, imagePath bleibt erstes Bild → abwärtskompatibel). Android & iOS, beide Builds grün (iOS geprüft).
- 18.11.2026 (v1.0.85, iOS-Build 79): Projekte wie bei Claude – im Chat „Projekt: … ▾“ (leere Chat-Ansicht) bzw. Verlauf → „Projekte“: Projekte anlegen/bearbeiten/löschen mit Name, Anweisungen und Wissen (Text-, Word-, PDF-Dateien, Notizen; Android: PDF-Seiten/Fotos werden einmalig per Vision-Modell abgeschrieben, iOS: PDF-Text direkt, Fotos per KI). Jeder Chat im Projekt bekommt Anweisungen + Wissen (max. 60.000 Zeichen) als Systemtext; neuer Chat bleibt im Projekt; Verlauf zeigt Projektnamen. Datenformat abwärtskompatibel (Conversation.projectId/projectID optional, neue Datei projects.json; Android-Sicherung enthält projects.json). Android & iOS, beide Builds grün (iOS geprüft). Wunsch des Nutzers (1. von 7 Claude-Funktionen).
