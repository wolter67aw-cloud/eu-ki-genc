# KI-GenC

Europäische KI-App für Android – mit **Mistral AI** (Paris, Server in der EU, DSGVO).
Die App aktualisiert sich selbst über GitHub Releases.

## Funktionen

| Bereich | Was es kann |
|---|---|
| **Chat** | KI-Chat mit Live-Antworten (Streaming), Markdown, Verlauf, Teilen, Kopieren |
| **Bilder analysieren** | Foto aufnehmen oder aus der Galerie wählen und Fragen dazu stellen (Vision-Modelle) |
| **Sprache** | Spracheingabe per Mikrofon, Antworten vorlesen lassen (auch automatisch) |
| **Studio – Texte** | Vorlagen für E-Mail, Zusammenfassen, Übersetzen, Verbessern, Social Media, Ideen, Bild-Prompts |
| **Studio – Bilder** | Bilder erzeugen (FLUX über Mistral), Stile wählen, in Galerie speichern, teilen |
| **Code** | Code-Assistent mit Codestral: Code schreiben, erklären, Fehler finden, Tests erstellen |
| **Teilen-Menü** | Text oder Bilder aus anderen Apps direkt an KI-GenC schicken |

## Erste Schritte

1. APK aus dem neuesten [Release](../../releases/latest) herunterladen und installieren
   (beim ersten Mal „Installation aus unbekannten Quellen“ erlauben).
2. Auf [console.mistral.ai](https://console.mistral.ai/api-keys) kostenlos registrieren und einen API-Schlüssel erstellen.
3. In der App unter **Einstellungen** den Schlüssel einfügen → **Speichern & testen**.

## So aktualisiert sich die App selbst

- **App-Updates:** Jeder Push auf `main` baut per GitHub Actions eine signierte APK und veröffentlicht sie als Release.
  Die App prüft beim Start und alle 6 Stunden im Hintergrund auf neue Versionen, lädt sie herunter und installiert sie
  (Android fragt einmal zur Bestätigung).
- **KI-Modelle:** Die App nutzt `-latest`-Modelle von Mistral und lädt die Modellliste live – neue Modelle sind ohne App-Update sofort verfügbar.
- **Bibliotheken:** Dependabot schlägt wöchentlich neue Versionen der Abhängigkeiten vor.

## Technik

Kotlin 2.4 · Jetpack Compose (BOM 2026.09) · Material 3 · Android Gradle Plugin 9.4 · Gradle 9.6 ·
DataStore · WorkManager · Android Keystore (AES-256-GCM) · PackageInstaller-API · minSdk 26 / targetSdk 37.
Keine Tracking- oder Werbe-Bibliotheken, keine Netzwerk-Bibliotheken von Drittanbietern.

## Selbst bauen

```bash
./gradlew assembleRelease
```

Signierte Builds: Der Keystore liegt passwortgeschützt unter `signing/release.jks`.
Das Passwort steht im Repository-Secret `KEYSTORE_PASSWORD` (Settings → Secrets and variables → Actions).
Ohne dieses Secret entsteht nur ein Test-Build (Artefakt), aber kein Release.

## Datenschutz

Anfragen gehen ausschließlich an `api.mistral.ai`. Der API-Schlüssel wird verschlüsselt im Android-Keystore abgelegt,
Unterhaltungen und Bilder bleiben auf dem Gerät. Update-Prüfungen gehen an `api.github.com`.
