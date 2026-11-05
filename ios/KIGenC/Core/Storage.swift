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
}

final class ConversationStore: ObservableObject {
    static let shared = ConversationStore()
    @Published var conversations: [Conversation] = Store.load([Conversation].self, "conversations.json") ?? []

    func upsert(_ c: Conversation) {
        guard !c.messages.isEmpty else { return }
        if let i = conversations.firstIndex(where: { $0.id == c.id }) { conversations[i] = c } else { conversations.insert(c, at: 0) }
        conversations.sort { $0.updated > $1.updated }
        if conversations.count > 100 { conversations = Array(conversations.prefix(100)) }
        Store.save(conversations, "conversations.json")
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
