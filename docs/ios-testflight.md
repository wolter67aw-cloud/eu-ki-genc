# KI-GenC für iPhone – Einrichtung TestFlight

Einmalig nötig. Danach baut GitHub jede neue Version automatisch und lädt sie zu TestFlight hoch.

## 1. Apple Developer Program (99 €/Jahr)
1. https://developer.apple.com/programs/enroll/ öffnen (am besten in der App **Apple Developer** auf dem iPhone oder am Mac).
2. Mit deiner Apple-ID anmelden, als **Einzelperson** registrieren, bezahlen.
3. Freischaltung abwarten (meist 1–2 Tage, E-Mail von Apple).

## 2. Team-ID notieren
https://developer.apple.com/account → **Mitgliedschaftsdetails** → **Team-ID** (10 Zeichen, z. B. `A1B2C3D4E5`).

## 3. App in App Store Connect anlegen
1. https://appstoreconnect.apple.com → **Apps** → **+** → **Neue App**.
2. Plattform **iOS**, Name **KI-GenC** (falls vergeben: „KI-GenC Assistent“), Sprache **Deutsch**.
3. Bundle-ID: über den Link **„Neue Bundle-ID registrieren“** die ID **`eu.kigenc.app`** anlegen (Typ *App ID*, keine Zusatzfunktionen nötig), dann auswählen.
4. SKU: `kigenc`, Zugriff: **Vollzugriff** → **Erstellen**.

## 4. API-Schlüssel erstellen
1. App Store Connect → **Benutzer und Zugriff** → **Integrationen** → **App Store Connect API** → **Team-Schlüssel** → **+**.
2. Name `GitHub`, Zugriff **Admin** (nötig, damit GitHub die Zertifikate automatisch erstellen kann).
3. **Schlüssel-ID** und oben die **Aussteller-ID (Issuer ID)** notieren.
4. **API-Schlüssel laden** → Datei `AuthKey_XXXXXXXXXX.p8` (nur einmal möglich!). Mit einem Texteditor öffnen.

## 5. Secrets bei GitHub eintragen
https://github.com/wolter67aw-cloud/eu-ki-genc/settings/secrets/actions → **New repository secret**, vier Stück:

| Name | Inhalt |
|---|---|
| `APPLE_TEAM_ID` | Team-ID aus Schritt 2 |
| `APP_STORE_CONNECT_KEY_ID` | Schlüssel-ID aus Schritt 4 |
| `APP_STORE_CONNECT_ISSUER_ID` | Aussteller-ID aus Schritt 4 |
| `APP_STORE_CONNECT_KEY_P8` | kompletter Inhalt der .p8-Datei inkl. `-----BEGIN PRIVATE KEY-----` und `-----END PRIVATE KEY-----` |

Danach unter **Actions → iOS-App bauen → Run workflow** (Branch `main`) starten. Nach ca. 10 Minuten plus 5–30 Minuten Verarbeitung erscheint der Build in App Store Connect unter **TestFlight**.

## 6. Testgruppe für 12 Personen
1. App Store Connect → App **KI-GenC** → **TestFlight**.
2. Beim Build ggf. **Exportkonformität** bestätigen (ist bereits voreingestellt: keine eigene Verschlüsselung).
3. Links **Externe Tests** → **+** → Gruppe `Tester`.
4. Build hinzufügen, **Testinformationen** ausfüllen (Beschreibung, E-Mail, Hinweis für Apple: *„Zugangscode für die Prüfung: KIG-…“* – einen der 12 Codes angeben, sonst kann Apple die App nicht prüfen).
5. Zur **Beta-App-Prüfung** einreichen (meist < 24 h).
6. Nach Freigabe: **Öffentlichen Link aktivieren**, Tester-Limit **12** → Link an die Tester schicken.

## Für die Tester
1. App **TestFlight** aus dem App Store laden.
2. Einladungslink öffnen → **Annehmen** → **Installieren**.
3. KI-GenC öffnen, persönlichen Zugangscode eingeben, Mistral-Schlüssel unter „Optionen“ eintragen.
4. Updates kommen über TestFlight (in TestFlight → KI-GenC → **Automatische Updates** einschalten).

Die Testversion sperrt sich am **05.12.2026** automatisch.
