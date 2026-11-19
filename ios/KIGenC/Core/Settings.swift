import Foundation
import Security

/// Speichert Geheimnisse (API-Schlüssel) sicher im iOS-Schlüsselbund.
enum Keychain {
    static func set(_ value: String, for key: String) {
        let data = Data(value.utf8)
        let query: [String: Any] = [kSecClass as String: kSecClassGenericPassword, kSecAttrAccount as String: key]
        SecItemDelete(query as CFDictionary)
        guard !value.isEmpty else { return }
        var add = query
        add[kSecValueData as String] = data
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    static func get(_ key: String) -> String {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrAccount as String: key,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var item: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &item) == errSecSuccess, let data = item as? Data else { return "" }
        return String(data: data, encoding: .utf8) ?? ""
    }
}

enum ImageProvider: String, CaseIterable, Identifiable {
    case auto, mistral, pollinations, bfl
    var id: String { rawValue }
    var title: String {
        switch self {
        case .auto: return "Automatisch (empfohlen)"
        case .mistral: return "Mistral AI"
        case .pollinations: return "Pollinations (kostenlos)"
        case .bfl: return "Black Forest Labs (FLUX)"
        }
    }
    var detail: String {
        switch self {
        case .auto: return "Erst Mistral – klappt das nicht, kostenlos über Pollinations."
        case .mistral: return "FLUX über Mistral (EU). Im Gratis-Tarif oft nicht verfügbar."
        case .pollinations: return "Ohne Schlüssel, etwa 1 Bild pro 15 Sekunden. Nur der Bildtext wird übertragen."
        case .bfl: return "Deutscher Anbieter (Freiburg), eigener Schlüssel, Abrechnung pro Bild."
        }
    }
}

/// App-Einstellungen. Schlüssel liegen im Schlüsselbund, der Rest in den UserDefaults.
final class AppSettings: ObservableObject {
    static let shared = AppSettings()
    private let d = UserDefaults.standard

    @Published var apiKey: String { didSet { Keychain.set(apiKey.trimmingCharacters(in: .whitespacesAndNewlines), for: "mistral_key") } }
    @Published var bflKey: String { didSet { Keychain.set(bflKey.trimmingCharacters(in: .whitespacesAndNewlines), for: "bfl_key") } }
    @Published var chatModel: String { didSet { d.set(chatModel, forKey: "chat_model") } }
    @Published var codeModel: String { didSet { d.set(codeModel, forKey: "code_model") } }
    @Published var autoSpeak: Bool { didSet { d.set(autoSpeak, forKey: "auto_speak") } }
    @Published var imageProvider: ImageProvider { didSet { d.set(imageProvider.rawValue, forKey: "image_provider") } }
    @Published var imageAgentID: String { didSet { d.set(imageAgentID, forKey: "image_agent") } }
    @Published var cachedModels: [String] { didSet { d.set(cachedModels, forKey: "cached_models") } }
    @Published var onboardingDone: Bool { didSet { d.set(onboardingDone, forKey: "onboarding_done") } }
    /// Zustimmung zur Übertragung an KI-Dienste (Datenschutz-Hinweis vor der ersten Nutzung, widerrufbar).
    @Published var aiConsent: Bool { didSet { d.set(aiConsent, forKey: "ai_consent") } }
    /// Gewähltes App-Design (Synthesizer-Skin).
    @Published var design: String { didSet { d.set(design, forKey: "design"); Theme.current = AppDesign.byID(design) } }
    /// Beim App-Start die Nr. 1 der Charts (Hörprobe) abspielen.
    @Published var startMusic: Bool { didSet { d.set(startMusic, forKey: "start_music") } }
    /// Deezer-Genre für die Start-Musik (0 = alle Charts).
    @Published var musicGenre: Int { didSet { d.set(musicGenre, forKey: "music_genre") } }
    /// Gedächtnis: gemerkte Fakten in jedem Chat mitgeben.
    @Published var memoryEnabled: Bool { didSet { d.set(memoryEnabled, forKey: "memory_enabled") } }
    /// Gedächtnis: aus Nachrichten automatisch dazulernen.
    @Published var memoryAuto: Bool { didSet { d.set(memoryAuto, forKey: "memory_auto") } }
    /// Antwortstil im Chat (siehe `answerStyles`).
    @Published var answerStyle: String { didSet { d.set(answerStyle, forKey: "answer_style") } }

    var hasKey: Bool { !key.isEmpty }
    /// Eigener Schlüssel, sonst der Test-Schlüssel der Testversion.
    var key: String {
        let own = apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        return own.isEmpty ? (Beta.sharedKey ?? "") : own
    }
    var usesTestKey: Bool { apiKey.trimmingCharacters(in: .whitespaces).isEmpty && Beta.sharedKey != nil }
    var canEditFaithfully: Bool { !bflKey.trimmingCharacters(in: .whitespaces).isEmpty }

    private init() {
        apiKey = Keychain.get("mistral_key")
        bflKey = Keychain.get("bfl_key")
        chatModel = d.string(forKey: "chat_model") ?? "mistral-small-latest"
        codeModel = d.string(forKey: "code_model") ?? "codestral-latest"
        autoSpeak = d.bool(forKey: "auto_speak")
        imageProvider = ImageProvider(rawValue: d.string(forKey: "image_provider") ?? "") ?? .auto
        imageAgentID = d.string(forKey: "image_agent") ?? ""
        cachedModels = d.stringArray(forKey: "cached_models") ?? []
        onboardingDone = d.bool(forKey: "onboarding_done")
        aiConsent = d.bool(forKey: "ai_consent")
        design = d.string(forKey: "design") ?? "original"
        startMusic = d.bool(forKey: "start_music")
        musicGenre = d.integer(forKey: "music_genre")
        memoryEnabled = d.object(forKey: "memory_enabled") as? Bool ?? true
        memoryAuto = d.object(forKey: "memory_auto") as? Bool ?? true
        answerStyle = d.string(forKey: "answer_style") ?? "normal"
    }

    /// Antwortstile wie bei Claude: Kennung, Name, Anweisung an die KI.
    static let answerStyles: [(id: String, name: String, text: String)] = [
        ("normal", "Normal", ""),
        ("knapp", "Knapp", "Antworte so kurz wie möglich: wenige Sätze, nur das Wesentliche, keine Einleitung."),
        ("ausfuehrlich", "Ausführlich", "Antworte ausführlich und gründlich, mit Erklärungen, Beispielen und klarer Gliederung."),
        ("foermlich", "Förmlich", "Antworte sachlich und förmlich in der Sie-Form, professionell und präzise."),
        ("locker", "Locker", "Antworte locker und freundlich in der Du-Form, gern mit einem Augenzwinkern."),
        ("einfach", "Einfach erklärt", "Erkläre alles in einfacher Sprache: kurze Sätze, keine Fachbegriffe (oder sofort erklärt), wie für Einsteiger."),
    ]

    /// Zusatz für den Systemtext zum gewählten Antwortstil (leer bei „Normal“).
    static func styleText(_ id: String) -> String {
        guard let t = answerStyles.first(where: { $0.id == id })?.text, !t.isEmpty else { return "" }
        return "\n\nAntwortstil: " + t
    }

    static let defaultChatModels = ["mistral-small-latest", "mistral-medium-latest", "mistral-large-latest", "magistral-medium-latest", "ministral-8b-latest"]
    static let defaultCodeModels = ["codestral-latest", "devstral-medium-latest", "devstral-small-latest", "mistral-medium-latest"]
}
