#!/usr/bin/env python3
"""Nach dem TestFlight-Upload: „Was testen“ eintragen und den Build an die externe Gruppe verteilen.

1. wartet, bis Apple den Build verarbeitet hat (bis ca. 40 Min.)
2. trägt die Neuerungen (notes-Datei) als „Was testen“ ein (de-DE und vorhandene Sprachen)
3. fügt den Build der Gruppe „Tester“ hinzu (öffentlicher Link) und reicht ihn zur Beta-Prüfung ein

Umgebung: ASC_KEY_ID, ASC_ISSUER_ID, ASC_KEY_P8. Argumente: --build N --notes datei [--group Tester]
Jeder Schritt meldet Fehler, bricht aber den Build nicht ab.
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import time
import urllib.error
import urllib.request

import jwt

API = "https://api.appstoreconnect.apple.com/v1"
BUNDLE_ID = "eu.kigenc.app"


def token() -> str:
    now = int(time.time())
    return jwt.encode(
        {"iss": os.environ["ASC_ISSUER_ID"].strip(), "iat": now, "exp": now + 1100, "aud": "appstoreconnect-v1"},
        os.environ["ASC_KEY_P8"].strip(),
        algorithm="ES256",
        headers={"kid": os.environ["ASC_KEY_ID"].strip(), "typ": "JWT"},
    )


def call(method: str, path: str, body: dict | None = None) -> dict:
    url = path if path.startswith("http") else API + path
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method, headers={
        "Authorization": f"Bearer {token()}", "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            raw = r.read()
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="ignore")
        try:
            detail = "; ".join(f"{x.get('code')}: {x.get('detail')}" for x in json.loads(detail).get("errors", []))
        except Exception:
            pass
        raise RuntimeError(f"{method} {path.split('?')[0]} → HTTP {e.code}: {detail[:300]}") from None


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--build", required=True)
    ap.add_argument("--notes", required=True)
    ap.add_argument("--group", default="Tester")
    a = ap.parse_args()
    notes = open(a.notes, encoding="utf-8").read().strip()[:3900]

    app = call("GET", f"/apps?filter[bundleId]={BUNDLE_ID}")["data"][0]["id"]

    build = None
    for attempt in range(80):  # 80 × 30 s ≈ 40 Min.
        res = call("GET", f"/builds?filter[app]={app}&filter[version]={a.build}&limit=1")
        if res.get("data"):
            build = res["data"][0]
            state = build["attributes"].get("processingState")
            if state == "VALID":
                break
            if state in ("FAILED", "INVALID"):
                print(f"❌ Apple meldet Build {a.build} als {state}.")
                return 1
        if attempt % 4 == 0:
            print(f"… warte auf Verarbeitung von Build {a.build}")
        time.sleep(30)
    else:
        print(f"⚠️ Build {a.build} nach 40 Min. noch nicht verarbeitet – „Was testen“ bitte in App Store Connect eintragen.")
        return 1
    bid = build["id"]
    print(f"✅ Build {a.build} verarbeitet.")

    ok = True
    # 2. „Was testen“
    try:
        locs = call("GET", f"/builds/{bid}/betaBuildLocalizations").get("data", [])
        if locs:
            for loc in locs:
                call("PATCH", f"/betaBuildLocalizations/{loc['id']}", {"data": {
                    "type": "betaBuildLocalizations", "id": loc["id"], "attributes": {"whatsNew": notes}}})
        else:
            call("POST", "/betaBuildLocalizations", {"data": {
                "type": "betaBuildLocalizations",
                "attributes": {"whatsNew": notes, "locale": "de-DE"},
                "relationships": {"build": {"data": {"type": "builds", "id": bid}}}}})
        print("✅ „Was testen“ eingetragen:\n" + notes)
    except Exception as e:
        ok = False
        print("⚠️ „Was testen“ nicht eingetragen:", e)

    # 3. Gruppe + Beta-Prüfung
    try:
        groups = call("GET", f"/apps/{app}/betaGroups?limit=50").get("data", [])
        group = next((g for g in groups if g["attributes"].get("name") == a.group), None)
        if group is None:
            print(f"ℹ️ Gruppe „{a.group}“ nicht gefunden – Build nicht verteilt.")
        else:
            # Direkt nach der Verarbeitung kennt die Gruppen-Schnittstelle den Build manchmal noch nicht (404) – nochmal versuchen
            for attempt in range(12):
                try:
                    call("POST", f"/betaGroups/{group['id']}/relationships/builds",
                         {"data": [{"type": "builds", "id": bid}]})
                    break
                except RuntimeError as e:
                    if "HTTP 404" not in str(e) or attempt == 11:
                        raise
                    print("… Build für die Gruppe noch nicht bereit, warte 30 s")
                    time.sleep(30)
            print(f"✅ Build der Gruppe „{a.group}“ hinzugefügt.")
            if not group["attributes"].get("isInternalGroup"):
                try:
                    call("POST", "/betaAppReviewSubmissions", {"data": {
                        "type": "betaAppReviewSubmissions",
                        "relationships": {"build": {"data": {"type": "builds", "id": bid}}}}})
                    print("✅ Zur Beta-Prüfung eingereicht – die Tester erhalten den Build nach Apples Freigabe.")
                except RuntimeError as e:
                    # z. B. ein früherer Build ist noch in Prüfung – dann reicht Apple automatisch nach bzw. später erneut
                    print("ℹ️ Beta-Prüfung nicht eingereicht:", e)
    except Exception as e:
        ok = False
        print("⚠️ Verteilung an die Tester fehlgeschlagen:", e)
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
