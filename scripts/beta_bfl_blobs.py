#!/usr/bin/env python3
"""Verschlüsselt den Black-Forest-Labs-Schlüssel der Testversion je Zugangscode (PBKDF2-SHA256 120.000 Runden +
AES-256-GCM, Salt „kigenc-beta-bfl-v1“ – gleiches Verfahren wie der Mistral-Test-Schlüssel) und trägt die Blobs in
gradle.properties (betaBflBlobs) und ios/KIGenC/Core/BetaBflBlobs.swift ein.

Weder Codes noch Schlüssel landen im Repository. Aufruf (Werte nur über Umgebungsvariablen, nie als Datei einchecken):
    BFL_KEY=... BETA_CODES="KIG-AAAA-1111,KIG-BBBB-2222,..." python3 scripts/beta_bfl_blobs.py
Codes, deren Prüfsumme nicht in betaCodeHashes steht, werden abgelehnt.
"""
import base64, hashlib, os, re, sys
from pathlib import Path
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

ROOT = Path(__file__).resolve().parent.parent
PROPS = ROOT / "gradle.properties"
SWIFT = ROOT / "ios/KIGenC/Core/BetaBflBlobs.swift"


def normalize(code: str) -> str:
    raw = "".join(c for c in code.upper() if c.isascii() and c.isalnum())
    if not 8 <= len(raw) <= 11:
        return code.strip().upper()
    body = raw[-8:]
    return f"KIG-{body[:4]}-{body[4:]}"


def main() -> None:
    key = os.environ.get("BFL_KEY", "").strip()
    codes = [normalize(c) for c in re.split(r"[,\s]+", os.environ.get("BETA_CODES", "")) if c.strip()]
    if not key or not codes:
        sys.exit("BFL_KEY und BETA_CODES setzen.")
    props = PROPS.read_text()
    hashes = set(re.search(r"^betaCodeHashes=(.*)$", props, re.M).group(1).split(","))
    bad = [c for c in codes if hashlib.sha256(f"kigenc-beta:{c}".encode()).hexdigest() not in hashes]
    if bad:
        sys.exit(f"{len(bad)} Code(s) passen nicht zu betaCodeHashes – abgebrochen.")
    blobs = []
    for c in codes:
        k = hashlib.pbkdf2_hmac("sha256", c.encode(), b"kigenc-beta-bfl-v1", 120_000, 32)
        nonce = os.urandom(12)
        blob = nonce + AESGCM(k).encrypt(nonce, key.encode(), None)
        assert AESGCM(k).decrypt(blob[:12], blob[12:], None).decode() == key
        blobs.append(base64.b64encode(blob).decode())
    value = ",".join(blobs)
    line = f"betaBflBlobs={value}"
    if re.search(r"^betaBflBlobs=.*$", props, re.M):
        props = re.sub(r"^betaBflBlobs=.*$", line, props, flags=re.M)
    else:
        props = props.rstrip("\n") + "\n# Black-Forest-Labs-Schlüssel der Testversion, je Zugangscode verschlüsselt (scripts/beta_bfl_blobs.py)\n" + line + "\n"
    PROPS.write_text(props)
    swift = SWIFT.read_text()
    swift = re.sub(r'static let value = ".*"', f'static let value = "{value}"', swift)
    SWIFT.write_text(swift)
    print(f"{len(blobs)} Blobs geschrieben (gradle.properties, BetaBflBlobs.swift).")


if __name__ == "__main__":
    main()
