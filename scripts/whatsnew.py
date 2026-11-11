#!/usr/bin/env python3
"""Stellt aus der Git-Historie zusammen, was in jeder Version neu ist ("Was ist neu").

Quelle sind die Commit-Betreffzeilen, die die jeweilige App betreffen (Android: app/, iOS: ios/).
Versionen werden über Tags abgegrenzt: Android v1.0.N, iOS ios-N.

Ausgaben:
  --json      alle (bis zu 10) Versionen für die App, neueste zuerst
  --markdown  nur die aktuelle Version als "- Punkt"-Liste (GitHub-Release)
  --plain     nur die aktuelle Version als "• Punkt"-Liste (TestFlight „Was testen“)

Commit-Betreffs mit [intern] oder [skip-notes] tauchen nicht auf.
"""
from __future__ import annotations

import argparse
import datetime as dt
import json
import re
import subprocess

PLATFORMS = {
    "android": {"tag": re.compile(r"^v1\.0\.(\d+)$"), "paths": ["app/"], "name": lambda n: f"1.0.{n}"},
    "ios": {"tag": re.compile(r"^ios-(\d+)$"), "paths": ["ios/"], "name": lambda n: f"1.0 ({n})"},
}

SKIP = re.compile(r"^(Merge |Revert |Logbuch|iOS-Workflow|Workflow|CI[: ]|Docs?[: ]|Doku|README)|\[(intern|skip-notes)\]", re.I)
DEPS = re.compile(r"^(Bump |build\(deps|chore\(deps|deps[: ])", re.I)
SUFFIX = re.compile(r"\s*\((?:Android|iOS|iPhone)[^)]*\)\s*$")
PREFIX = re.compile(r"^(?:iOS|Android|iPhone)(?::\s*|\s+|-(?=\w))")
FALLBACK = "Kleine Verbesserungen und Fehlerbehebungen"


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def items_between(old: str | None, new: str, paths: list[str]) -> tuple[list[str], str]:
    rng = f"{old}..{new}" if old else new
    extra = [] if old else ["-n", "15"]
    out = git("log", "--no-merges", "--format=%s%x1f%cs", *extra, rng, "--", *paths)
    items, newest = [], ""
    for line in out.splitlines():
        if "\x1f" not in line:
            continue
        subject, date = line.split("\x1f", 1)
        newest = newest or date
        subject = subject.strip()
        if not subject or SKIP.search(subject):
            continue
        if DEPS.search(subject):
            subject = "Technische Bibliotheken aktualisiert"
        subject = PREFIX.sub("", SUFFIX.sub("", subject)).rstrip(" .")
        subject = subject[:1].upper() + subject[1:]
        if subject and subject not in items:
            items.append(subject)
    return items[:12], newest


def german(date: str) -> str:
    try:
        return dt.date.fromisoformat(date).strftime("%d.%m.%Y")
    except ValueError:
        return dt.date.today().strftime("%d.%m.%Y")


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--platform", choices=PLATFORMS, required=True)
    ap.add_argument("--code", type=int, required=True, help="Build-/Versionsnummer, die gerade gebaut wird")
    ap.add_argument("--json")
    ap.add_argument("--markdown")
    ap.add_argument("--plain")
    ap.add_argument("--limit", type=int, default=10)
    a = ap.parse_args()
    p = PLATFORMS[a.platform]

    tags = []
    for t in git("tag").split():
        m = p["tag"].match(t)
        if m and int(m.group(1)) < a.code:
            tags.append((int(m.group(1)), t))
    tags.sort(reverse=True)

    entries = []
    # Aktuelle Version: alles seit dem letzten veröffentlichten Tag
    bounds = [(a.code, "HEAD")] + tags
    for i, (code, ref) in enumerate(bounds[: a.limit]):
        older = bounds[i + 1][1] if i + 1 < len(bounds) else None
        if older is None and i > 0:
            break  # ältester Tag ohne Vorgänger: Umfang unklar, weglassen
        items, date = items_between(older, ref, p["paths"])
        if not items:
            items = [FALLBACK]
        entries.append({"code": code, "version": p["name"](code), "date": german(date), "items": items})

    current = entries[0]["items"] if entries else [FALLBACK]
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump({"entries": entries}, f, ensure_ascii=False, indent=1)
    if a.markdown:
        with open(a.markdown, "w", encoding="utf-8") as f:
            f.write("**Neu in dieser Version**\n\n" + "\n".join(f"- {x}" for x in current) + "\n")
    if a.plain:
        with open(a.plain, "w", encoding="utf-8") as f:
            f.write("Neu in dieser Version:\n" + "\n".join(f"• {x}" for x in current) + "\n")
    print("\n".join(f"• {x}" for x in current))


if __name__ == "__main__":
    main()
