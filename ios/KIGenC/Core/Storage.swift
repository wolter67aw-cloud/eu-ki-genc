import Foundation
import UIKit

/// Einfacher JSON-Speicher im Dokumente-Ordner der App.
enum Store {
    static var dir: URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0]
    }

    static func load<T: Decodable>(_ type: T.Type, _ name: String) -> T? {
        guard let data = try? Data(contentsOf: dir.appendingPathComponent(name)) else { return nil }
        return try? JSONDecoder().decode(T.self, from: data)
    }

    static func save<T: Encodable>(_ value: T, _ name: String) {
        guard let data = try? JSONEncoder().encode(value) else { return }
        try? data.write(to: dir.appendingPathComponent(name), options: .atomic)
    }

    static func deleteAll() {
        let fm = FileManager.default
        for f in (try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil)) ?? [] {
            try? fm.removeItem(at: f)
        }
    }
}

// MARK: Chat-Verlauf

struct ChatMessage: Identifiable, Codable, Equatable {
    var id = UUID()
    var role: String // "user" oder "assistant"
    var text: String
    var images: [String] = [] // Data-URLs
    var isError = false
}

struct Conversation: Identifiable, Codable {
    var id = UUID()
    var title: String
    var updated = Date()
    var messages: [ChatMessage]
    /// Im Verlauf angeheftet (Gruppe „Angeheftet“ ganz oben). Fehlt in älteren Daten → false.
    var pinned = false
    /// Zugehöriges Projekt (fehlt in älteren Daten → kein Projekt).
    var projectID: UUID? = nil

    enum CodingKeys: String, CodingKey { case id, title, updated, messages, pinned, projectID }
}

extension Conversation {
    /// Tolerantes Laden: ältere gespeicherte Chats ohne „pinned“ bleiben lesbar.
    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decodeIfPresent(UUID.self, forKey: .id) ?? UUID()
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? "Unterhaltung"
        updated = try c.decodeIfPresent(Date.self, forKey: .updated) ?? Date()
        messages = try c.decodeIfPresent([ChatMessage].self, forKey: .messages) ?? []
        pinned = try c.decodeIfPresent(Bool.self, forKey: .pinned) ?? false
        projectID = try c.decodeIfPresent(UUID.self, forKey: .projectID)
    }
}

/// Wissens-Eintrag eines Projekts (Text aus Datei oder eigene Notiz).
struct ProjectFile: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    var text: String
}

/// Projekt wie bei Claude: feste Anweisungen und Wissen, das jeder Chat des Projekts kennt.
struct Project: Codable, Identifiable, Equatable {
    var id = UUID()
    var name: String
    var instructions = ""
    var files: [ProjectFile] = []
    var updated = Date()

    /// So viel Projektwissen (Zeichen) geht höchstens pro Anfrage an die KI.
    static let maxContext = 60_000

    /// Systemtext für die KI: Anweisungen + Wissen (gekürzt).
    func systemText() -> String {
        var s = "\n\nDiese Unterhaltung gehört zum Projekt „\(name)“."
        let instr = instructions.trimmingCharacters(in: .whitespacesAndNewlines)
        if !instr.isEmpty { s += "\nAnweisungen des Nutzers für dieses Projekt (immer befolgen):\n" + instr }
        if !files.isEmpty {
            s += "\n\nProjektwissen (nutze es, wenn es zur Frage passt, und nenne die Quelle):"
            var left = Self.maxContext
            for f in files {
                if left <= 0 { s += "\n\n[Weiteres Projektwissen wegen der Länge ausgelassen]"; break }
                let t = String(f.text.prefix(left))
                s += "\n\n--- \(f.name) ---\n" + t
                if t.count < f.text.count { s += "\n[gekürzt]" }
                left -= t.count
            }
        }
        return s
    }
}

/// Ein gemerkter Fakt über den Nutzer (Gedächtnis).
struct MemoryFact: Codable, Identifiable, Equatable {
    var id = UUID()
    var text: String
    var created = Date()
}

/// Gedächtnis wie bei Claude: Fakten über den Nutzer, die in jedem Chat mitgegeben werden. Nur auf dem Gerät.
@MainActor
final class MemoryStore: ObservableObject {
    static let shared = MemoryStore()
    static let maxFacts = 60
    @Published private(set) var facts: [MemoryFact] = Store.load([MemoryFact].self, "memory.json") ?? []

    /// Systemtext für die KI (leer, wenn nichts gemerkt).
    var systemText: String {
        guard !facts.isEmpty else { return "" }
        return "\n\nGedächtnis – was du über den Nutzer weißt (nutze es unaufdringlich, wenn es passt; erwähne es nicht ständig):\n"
            + facts.map { "- \($0.text)" }.joined(separator: "\n")
    }

    @discardableResult
    func add(_ text: String) -> Bool {
        var t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        while t.hasSuffix(".") { t.removeLast() }
        t = String(t.prefix(200))
        guard t.count >= 3, !facts.contains(where: { $0.text.caseInsensitiveCompare(t) == .orderedSame }) else { return false }
        facts.append(MemoryFact(text: t))
        if facts.count > Self.maxFacts { facts.removeFirst(facts.count - Self.maxFacts) }
        save()
        return true
    }

    func update(_ id: UUID, _ text: String) {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let i = facts.firstIndex(where: { $0.id == id }) else { return }
        if t.isEmpty { facts.remove(at: i) } else { facts[i].text = String(t.prefix(200)) }
        save()
    }

    func delete(_ id: UUID) { facts.removeAll { $0.id == id }; save() }
    func clear() { facts = []; save() }
    private func save() { Store.save(facts, "memory.json") }

    /// „Merk dir …“ / „Merke dir, dass …“ / „Bitte merken: …“ am Anfang einer Nachricht.
    static func explicitFact(_ message: String) -> String? {
        let pattern = "^(?:bitte\\s+)?(?:merk(?:e)?\\s+(?:dir|es\\s+dir)|bitte\\s+merken|notier(?:e)?\\s+dir)\\s*[:,]?\\s*(?:dass\\s+)?(.+)$"
        guard let re = try? NSRegularExpression(pattern: pattern, options: [.caseInsensitive, .dotMatchesLineSeparators]) else { return nil }
        let s = message.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let m = re.firstMatch(in: s, range: NSRange(s.startIndex..., in: s)), let r = Range(m.range(at: 1), in: s) else { return nil }
        let fact = s[r].trimmingCharacters(in: .whitespacesAndNewlines)
        return fact.count >= 3 ? fact : nil
    }

    /// Zieht mit einem sparsamen Modell dauerhafte Fakten aus einer Nachricht und speichert neue. Liefert die Anzahl.
    func learn(from message: String, explicit: String?, key: String) async -> Int {
        if explicit == nil && message.count < 15 { return 0 }
        let known = facts.isEmpty ? "(noch nichts)" : facts.map { "- \($0.text)" }.joined(separator: "\n")
        let prompt: String
        if let explicit {
            prompt = "Der Nutzer einer KI-App möchte ausdrücklich, dass du dir Folgendes über ihn merkst:\n\"\"\"\(explicit.prefix(1000))\"\"\"\nBekannte Fakten:\n\(known)\n\nFormuliere es als 1–2 kurze Fakten in dritter Person auf Deutsch (z. B. „Heißt Andy“, „Mag keine Emojis in Antworten“). Antwort als JSON: {\"facts\": [\"…\"]}"
        } else {
            prompt = "Du pflegst ein Gedächtnis über den Nutzer einer KI-App. Bekannte Fakten:\n\(known)\n\nNeue Nachricht des Nutzers:\n\"\"\"\(message.prefix(3000))\"\"\"\n\nGib NUR dauerhafte, persönliche Fakten zurück, die der Nutzer über SICH SELBST klar gesagt hat und die noch nicht bekannt sind (z. B. Name, Wohnort, Beruf, Familie, Haustiere, Vorlieben, Ziele, wie er Antworten haben möchte). KEINE Fragen, Aufgaben, Vermutungen, vorübergehenden Dinge, Gesundheits-, Finanz- oder andere sensible Angaben. Kurz, in dritter Person auf Deutsch (z. B. „Heißt Andy“, „Hat einen Hund namens Bello“). Antwort als JSON: {\"facts\": [\"…\"]} – meist ist die Liste leer."
        }
        var list: [String]?
        if let raw = try? await MistralClient.shared.complete(key: key, model: "mistral-small-latest", messages: [ApiMessage(role: "user", text: prompt)], json: true, temperature: 0),
           let data = raw.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            list = obj["facts"] as? [String]
        }
        var added = 0
        for f in (list ?? []).prefix(3) where add(f) { added += 1 }
        // Ausdrücklicher Wunsch, aber nichts erkannt → wörtlich merken
        if let explicit, added == 0, (list ?? []).isEmpty, add(explicit) { added += 1 }
        return added
    }
}

final class ProjectStore: ObservableObject {
    static let shared = ProjectStore()
    @Published var projects: [Project] = (Store.load([Project].self, "projects.json") ?? []).sorted { $0.updated > $1.updated }

    func byID(_ id: UUID?) -> Project? {
        guard let id else { return nil }
        return projects.first { $0.id == id }
    }

    func save(_ project: Project) {
        var p = project
        p.updated = Date()
        projects.removeAll { $0.id == p.id }
        projects.insert(p, at: 0)
        Store.save(projects, "projects.json")
    }

    func delete(_ id: UUID) {
        projects.removeAll { $0.id == id }
        Store.save(projects, "projects.json")
    }
}

final class ConversationStore: ObservableObject {
    static let shared = ConversationStore()
    @Published var conversations: [Conversation] = Store.load([Conversation].self, "conversations.json") ?? []

    func upsert(_ c: Conversation) {
        guard !c.messages.isEmpty else { return }
        if let i = conversations.firstIndex(where: { $0.id == c.id }) {
            // Anheft-Status bleibt erhalten – er wird nur über setPinned geändert.
            var n = c
            n.pinned = conversations[i].pinned
            conversations[i] = n
        } else {
            conversations.insert(c, at: 0)
        }
        conversations.sort { $0.updated > $1.updated }
        trim()
        Store.save(conversations, "conversations.json")
    }

    /// Unterhaltung im Verlauf anheften oder lösen.
    func setPinned(_ id: UUID, _ pinned: Bool) {
        guard let i = conversations.firstIndex(where: { $0.id == id }) else { return }
        conversations[i].pinned = pinned
        Store.save(conversations, "conversations.json")
    }

    /// Höchstens 100 Unterhaltungen behalten; angeheftete werden dabei nie verdrängt.
    private func trim() {
        guard conversations.count > 100 else { return }
        let pinnedCount = conversations.filter(\.pinned).count
        var keepUnpinned = max(0, 100 - pinnedCount)
        conversations = conversations.filter { c in
            if c.pinned { return true }
            if keepUnpinned > 0 { keepUnpinned -= 1; return true }
            return false
        }
    }

    func delete(_ id: UUID) {
        conversations.removeAll { $0.id == id }
        Store.save(conversations, "conversations.json")
    }

    func clear() {
        conversations = []
        Store.save(conversations, "conversations.json")
    }
}

// MARK: Ergebnisse (gespeicherte Texte und Bilder)

struct SavedResult: Identifiable, Codable {
    var id = UUID()
    var date = Date()
    var kind: String // Tool-Name
    var title: String
    var text: String
    var imageFile: String? = nil
}

final class ResultStore: ObservableObject {
    static let shared = ResultStore()
    @Published var results: [SavedResult] = Store.load([SavedResult].self, "results.json") ?? []

    func add(kind: String, title: String, text: String, image: Data? = nil) {
        var r = SavedResult(kind: kind, title: String(title.prefix(80)), text: text)
        if let image {
            let name = "result-\(r.id.uuidString).jpg"
            try? image.write(to: Store.dir.appendingPathComponent(name))
            r.imageFile = name
        }
        results.insert(r, at: 0)
        Store.save(results, "results.json")
    }

    func image(for r: SavedResult) -> UIImage? {
        guard let f = r.imageFile else { return nil }
        return UIImage(contentsOfFile: Store.dir.appendingPathComponent(f).path)
    }

    func delete(_ r: SavedResult) {
        if let f = r.imageFile { try? FileManager.default.removeItem(at: Store.dir.appendingPathComponent(f)) }
        results.removeAll { $0.id == r.id }
        Store.save(results, "results.json")
    }
}

// MARK: Aufgaben

struct TaskItem: Identifiable, Codable {
    var id = UUID()
    var title: String
    var due: Date?
    var done = false
    var note = ""
}

final class TaskStore: ObservableObject {
    static let shared = TaskStore()
    @Published var tasks: [TaskItem] = Store.load([TaskItem].self, "tasks.json") ?? []

    func save() { Store.save(tasks, "tasks.json") }

    func add(_ t: TaskItem) {
        tasks.append(t)
        tasks.sort { ($0.due ?? .distantFuture) < ($1.due ?? .distantFuture) }
        save()
        Reminders.schedule(t)
    }

    func toggle(_ t: TaskItem) {
        guard let i = tasks.firstIndex(where: { $0.id == t.id }) else { return }
        tasks[i].done.toggle()
        if tasks[i].done { Reminders.cancel(t.id) } else { Reminders.schedule(tasks[i]) }
        save()
    }

    func delete(_ t: TaskItem) {
        Reminders.cancel(t.id)
        tasks.removeAll { $0.id == t.id }
        save()
    }
}

// MARK: Fehlerprotokoll

enum ErrorLog {
    static var url: URL { Store.dir.appendingPathComponent("fehlerprotokoll.txt") }

    static func report(_ error: Error, context: String) {
        let f = ISO8601DateFormatter()
        let line = "[\(f.string(from: Date()))] \(context): \(friendly(error))\n"
        if let h = try? FileHandle(forWritingTo: url) {
            h.seekToEndOfFile()
            h.write(Data(line.utf8))
            try? h.close()
        } else {
            try? Data(line.utf8).write(to: url)
        }
    }

    static var text: String { (try? String(contentsOf: url, encoding: .utf8)) ?? "" }
    static func clear() { try? FileManager.default.removeItem(at: url) }
}
