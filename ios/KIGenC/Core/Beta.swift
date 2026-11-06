import Foundation
import CryptoKit
import CommonCrypto

/// Testversion: 12 persönliche Zugangscodes, gültig bis einschließlich 05.12.2026.
enum Beta {
    /// Testphase aktiv? (Für die spätere App-Store-Version auf `false` setzen.)
    static let isBeta = true

    static let expiresAt: Date = {
        var c = DateComponents()
        c.year = 2026; c.month = 12; c.day = 5; c.hour = 23; c.minute = 59; c.second = 59
        c.timeZone = TimeZone(identifier: "Europe/Berlin")
        return Calendar(identifier: .gregorian).date(from: c) ?? Date.distantFuture
    }()

    /// SHA-256-Prüfsummen der Codes (die Codes selbst stehen nicht im Quelltext).
    static let codeHashes: Set<String> = Set(BetaHashes.value.split(separator: ",").map(String.init))

    static var isExpired: Bool { isBeta && Date() > expiresAt }
    static var daysLeft: Int { max(0, Int(expiresAt.timeIntervalSinceNow / 86_400)) }
    static var expiryText: String {
        let f = DateFormatter()
        f.locale = Locale(identifier: "de_DE")
        f.dateStyle = .long
        return f.string(from: expiresAt)
    }

    static func normalize(_ input: String) -> String {
        let raw = input.uppercased().filter { $0.isLetter || $0.isNumber }
        var body = raw
        if body.hasPrefix("KIG") { body.removeFirst(3) }
        guard body.count == 8 else { return input.trimmingCharacters(in: .whitespaces).uppercased() }
        return "KIG-\(body.prefix(4))-\(body.suffix(4))"
    }

    static func isValid(_ code: String) -> Bool {
        let digest = SHA256.hash(data: Data("kigenc-beta:\(normalize(code))".utf8))
        let hex = digest.map { String(format: "%02x", $0) }.joined()
        return codeHashes.contains(hex)
    }

    static var storedCode: String? {
        guard let c = UserDefaults.standard.string(forKey: "beta_code"), isValid(c) else { return nil }
        return c
    }

    static func store(_ code: String) {
        UserDefaults.standard.set(normalize(code), forKey: "beta_code")
    }

    /// Gemeinsamer Mistral-Schlüssel der Testversion (nur im Arbeitsspeicher).
    static var sharedKey: String?

    static var hasSharedKey: Bool { isBeta && !BetaHashes.keyBlobs.isEmpty }

    /// Entschlüsselt den Test-Schlüssel mit dem Zugangscode. Ohne gültigen Code nicht lesbar.
    static func unlockSharedKey(_ code: String) -> String? {
        if let sharedKey { return sharedKey }
        guard hasSharedKey else { return nil }
        let password = Array(normalize(code).utf8).map { Int8(bitPattern: $0) }
        let salt = Array("kigenc-beta-key-v1".utf8)
        var derived = [UInt8](repeating: 0, count: 32)
        let status = CCKeyDerivationPBKDF(CCPBKDFAlgorithm(kCCPBKDF2), password, password.count, salt, salt.count,
                                          CCPseudoRandomAlgorithm(kCCPRFHmacAlgSHA256), 120_000, &derived, derived.count)
        guard status == kCCSuccess else { return nil }
        let key = SymmetricKey(data: Data(derived))
        for blob in BetaHashes.keyBlobs.split(separator: ",") {
            guard let raw = Data(base64Encoded: String(blob)),
                  let box = try? AES.GCM.SealedBox(combined: raw),
                  let plain = try? AES.GCM.open(box, using: key),
                  let text = String(data: plain, encoding: .utf8) else { continue }
            sharedKey = text
            return text
        }
        return nil
    }
}
