#!/usr/bin/env python3
"""Lädt die Play-Store-Version (KI-GenC-PlayStore.aab) über die Google Play Developer API in einen Track.

Zugang: Dienstkonto-Schlüssel (JSON) in der Umgebungsvariablen PLAY_SERVICE_ACCOUNT_JSON. Das Dienstkonto muss in
der Play Console unter „Nutzer und Berechtigungen“ für die App eingeladen sein (Releases verwalten).

Tracks: internal (interner Test), alpha (geschlossener Test), beta (offener Test), production.
Solange die App noch nie veröffentlicht wurde, nimmt Google nur Entwürfe an – dann wird der Release automatisch
als Entwurf angelegt und muss in der Play Console einmal mit „Überprüfen und einführen“ bestätigt werden.
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys

from google.auth.transport.requests import AuthorizedSession
from google.oauth2 import service_account

PACKAGE = "eu.kigenc.app"
API = f"https://androidpublisher.googleapis.com/androidpublisher/v3/applications/{PACKAGE}"
UPLOAD = f"https://androidpublisher.googleapis.com/upload/androidpublisher/v3/applications/{PACKAGE}"


def fail(msg: str, resp=None) -> None:
    detail = ""
    if resp is not None:
        try:
            detail = resp.json().get("error", {}).get("message", "")
        except ValueError:
            detail = resp.text[:300]
    print(f"::error::{msg}" + (f" – {detail}" if detail else ""))
    sys.exit(1)


def plain_notes(markdown: str) -> str:
    """Release-Text (Markdown) → schlichter Text für Google Play (max. 500 Zeichen)."""
    lines = []
    for line in markdown.splitlines():
        line = re.sub(r"^#+\s*", "", line).strip()
        line = re.sub(r"^[-*]\s+", "• ", line)
        line = line.replace("**", "")
        if line:
            lines.append(line)
    text = "\n".join(lines) or "Verbesserungen und Fehlerbehebungen."
    return text if len(text) <= 500 else text[:497].rsplit("\n", 1)[0] + "\n…"


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--aab", required=True)
    ap.add_argument("--track", default="alpha")
    ap.add_argument("--notes", help="Markdown-Datei mit den Neuerungen")
    ap.add_argument("--status", default="completed", choices=["completed", "draft"])
    args = ap.parse_args()

    raw = os.environ.get("PLAY_SERVICE_ACCOUNT_JSON", "").strip()
    if not raw:
        fail("PLAY_SERVICE_ACCOUNT_JSON fehlt")
    creds = service_account.Credentials.from_service_account_info(
        json.loads(raw), scopes=["https://www.googleapis.com/auth/androidpublisher"]
    )
    s = AuthorizedSession(creds)

    r = s.post(f"{API}/edits", json={}, timeout=60)
    if r.status_code == 404:
        fail(f"App {PACKAGE} nicht gefunden – ist sie in der Play Console angelegt und das Dienstkonto eingeladen?", r)
    if not r.ok:
        fail("Bearbeitung konnte nicht gestartet werden", r)
    edit = r.json()["id"]

    with open(args.aab, "rb") as f:
        r = s.post(f"{UPLOAD}/edits/{edit}/bundles?uploadType=media", data=f,
                   headers={"Content-Type": "application/octet-stream"}, timeout=900)
    if not r.ok:
        fail("Hochladen der AAB fehlgeschlagen", r)
    version_code = r.json()["versionCode"]
    print(f"AAB hochgeladen: versionCode {version_code}")

    notes = plain_notes(open(args.notes, encoding="utf-8").read()) if args.notes and os.path.exists(args.notes) else \
        "Verbesserungen und Fehlerbehebungen."

    def put_track(status: str):
        body = {"track": args.track, "releases": [{
            "versionCodes": [str(version_code)],
            "status": status,
            "releaseNotes": [{"language": "de-DE", "text": notes}],
        }]}
        return s.put(f"{API}/edits/{edit}/tracks/{args.track}", json=body, timeout=60)

    status = args.status
    r = put_track(status)
    if not r.ok and status != "draft" and "draft" in r.text.lower():
        print("App ist noch nicht veröffentlicht → Release wird als Entwurf angelegt.")
        status = "draft"
        r = put_track(status)
    if not r.ok:
        fail(f"Track „{args.track}“ konnte nicht gesetzt werden", r)

    r = s.post(f"{API}/edits/{edit}:commit", timeout=120)
    if not r.ok and "changesNotSentForReview" in r.text:
        r = s.post(f"{API}/edits/{edit}:commit?changesNotSentForReview=true", timeout=120)
        if r.ok:
            print("::warning::Änderungen sind gespeichert, aber noch nicht zur Prüfung gesendet – "
                  "in der Play Console unter „Veröffentlichungsübersicht“ auf „Zur Überprüfung senden“ tippen.")
    if not r.ok:
        fail("Änderungen konnten nicht übernommen werden", r)

    print(f"Fertig: versionCode {version_code} im Track „{args.track}“ ({status}).")
    if status == "draft":
        print("::notice::Release als Entwurf angelegt – in der Play Console einmal „Überprüfen und einführen“ tippen.")


if __name__ == "__main__":
    main()
