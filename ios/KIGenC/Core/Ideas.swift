import Foundation
import UIKit
import UserNotifications

enum IdeaKind: String, Codable, CaseIterable {
    case note, moment, memo, sketch, capsule, cross

    var label: String {
        switch self {
        case .note: return "Notiz"
        case .moment: return "Momentaufnahme"
        case .memo: return "Sprachmemo"
        case .sketch: return "Skizze"
        case .capsule: return "Zeitkapsel"
        case .cross: return "Ideen-Kreuzung"
        }
    }

    var emoji: String {
        switch self {
        case .note: return "📝"
        case .moment: return "📸"
        case .memo: return "🎙️"
        case .sketch: return "✏️"
        case .capsule: return "⏳"
        case .cross: return "🔀"
        }
    }
}

struct Idea: Identifiable, Codable, Equatable {
    var id = UUID()
    var kind: IdeaKind
    var title: String
    var text: String
    var tags: [String] = []
    var mood = ""
    var imageFile: String? = nil
    var audioFile: String? = nil
    var createdAt = Date()
    var openAt: Date? = nil

    var locked: Bool { if let openAt { return openAt > Date() } else { return false } }

    var searchable: String {
        locked ? "\(title) \(kind.label)" : "\(title) \(text) \(tags.joined(separator: " ")) \(mood) \(kind.label)"
    }
}

let moods = ["😊", "🤩", "🤔", "😐", "😴", "😔", "😤", "❤️"]

/// Ideen-Sammlung mit KI-Schlagwörtern. Bilder und Töne liegen im Ordner „ideas“.
@MainActor
final class IdeaStore: ObservableObject {
    static let shared = IdeaStore()
    @Published var ideas: [Idea] = Store.load([Idea].self, "ideas.json") ?? []
    @Published var tagging: Set<UUID> = []

    static var dir: URL {
        let d = Store.dir.appendingPathComponent("ideas", isDirectory: true)
        try? FileManager.default.createDirectory(at: d, withIntermediateDirectories: true)
        return d
    }

    static func url(_ name: String?) -> URL? {
        guard let name else { return nil }
        let u = dir.appendingPathComponent(name)
        return FileManager.default.fileExists(atPath: u.path) ? u : nil
    }

    static func image(_ idea: Idea) -> UIImage? {
        url(idea.imageFile).flatMap { UIImage(contentsOfFile: $0.path) }
    }

    static func saveImage(_ image: UIImage) -> String? {
        let name = "\(UUID().uuidString).jpg"
        guard let d = image.scaled(maxSide: 1800).jpegData(compressionQuality: 0.85) else { return nil }
        try? d.write(to: dir.appendingPathComponent(name))
        return name
    }

    static func saveAudio(from url: URL) -> String? {
        let name = "\(UUID().uuidString).m4a"
        do { try FileManager.default.copyItem(at: url, to: dir.appendingPathComponent(name)) } catch { return nil }
        return name
    }

    var available: [Idea] { ideas.filter { !$0.locked } }

    private func persist() {
        ideas.sort { $0.createdAt > $1.createdAt }
        Store.save(ideas, "ideas.json")
    }

    func upsert(_ idea: Idea) {
        if let i = ideas.firstIndex(where: { $0.id == idea.id }) { ideas[i] = idea } else { ideas.insert(idea, at: 0) }
        persist()
    }

    func delete(_ idea: Idea) {
        for f in [idea.imageFile, idea.audioFile].compactMap({ $0 }) {
            try? FileManager.default.removeItem(at: Self.dir.appendingPathComponent(f))
        }
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: ["capsule-\(idea.id.uuidString)"])
        ideas.removeAll { $0.id == idea.id }
        persist()
    }

    func add(_ idea: Idea, settings: AppSettings) {
        var idea = idea
        if idea.locked { idea.tags = ["zeitkapsel"] }
        upsert(idea)
        if idea.kind == .capsule { scheduleCapsule(idea) }
        if !idea.locked { autoTag(idea, settings: settings) }
    }

    private func scheduleCapsule(_ idea: Idea) {
        guard let at = idea.openAt, at > Date() else { return }
        Reminders.requestPermission()
        let content = UNMutableNotificationContent()
        content.title = "⏳ Deine Zeitkapsel ist offen"
        content.body = idea.title
        content.sound = .default
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: at)
        UNUserNotificationCenter.current().add(UNNotificationRequest(
            identifier: "capsule-\(idea.id.uuidString)", content: content,
            trigger: UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)))
    }

    /// KI schlägt 3–5 Schlagwörter vor (Text und ggf. Bild).
    func autoTag(_ idea: Idea, settings: AppSettings) {
        guard settings.hasKey, !idea.locked else { return }
        tagging.insert(idea.id)
        Task {
            defer { tagging.remove(idea.id) }
            let prompt = "Schlage 3 bis 5 kurze, treffende Schlagwörter (deutsch, kleingeschrieben, je 1–2 Wörter) für diesen Eintrag vor, damit man ihn später wiederfindet. Antworte nur als JSON: {\"tags\":[\"…\"]}\n\nArt: \(idea.kind.label)\nTitel: \(idea.title)\nInhalt: \(idea.text.prefix(4000))"
            let images = [Self.image(idea)?.dataURL].compactMap { $0 }
            do {
                let json = try await MistralClient.shared.complete(key: settings.key, model: MistralClient.fallbackModel,
                    messages: [ApiMessage(role: "user", text: prompt, images: images)], json: true, temperature: 0.2)
                guard let d = json.data(using: .utf8), let o = try JSONSerialization.jsonObject(with: d) as? [String: Any],
                      let arr = o["tags"] as? [String] else { return }
                let tags = arr.map { $0.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "#", with: "") }
                    .filter { !$0.isEmpty && $0.count <= 30 }
                guard var current = ideas.first(where: { $0.id == idea.id }), !tags.isEmpty else { return }
                for t in tags.prefix(5) where !current.tags.contains(t) { current.tags.append(t) }
                upsert(current)
            } catch {
                ErrorLog.report(error, context: "KI-Tags")
            }
        }
    }
}
