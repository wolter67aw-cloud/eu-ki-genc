import SwiftUI

/// „Was ist neu“: Die Liste wird beim Build aus der Git-Historie erzeugt (scripts/whatsnew.py → whatsnew.json)
/// und mit der App ausgeliefert. Nach einem Update zeigt die App einmal alle Neuerungen seit der zuletzt gesehenen Version.
struct WhatsNewEntry: Codable, Identifiable {
    let code: Int
    let version: String
    let date: String
    let items: [String]
    var id: Int { code }
}

enum WhatsNew {
    private static let seenKey = "whatsNewSeenBuild"

    static var currentBuild: Int {
        Int(Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "") ?? 0
    }

    static func all() -> [WhatsNewEntry] {
        struct File: Codable { let entries: [WhatsNewEntry] }
        guard let url = Bundle.main.url(forResource: "whatsnew", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let file = try? JSONDecoder().decode(File.self, from: data) else { return [] }
        return file.entries.filter { $0.code <= currentBuild }
    }

    /// Neuerungen seit dem letzten Öffnen; bei einer Neuinstallation (Einführung noch nicht gesehen) leer.
    static func pending(freshInstall: Bool) -> [WhatsNewEntry] {
        let d = UserDefaults.standard
        let current = currentBuild
        if d.object(forKey: seenKey) == nil {
            if freshInstall { markSeen(); return [] }
            d.set(current - 1, forKey: seenKey) // Update von einer Version ohne diese Funktion
        }
        let seen = d.integer(forKey: seenKey)
        guard seen < current else { return [] }
        return all().filter { $0.code > seen && $0.code <= current }
    }

    static func markSeen() {
        UserDefaults.standard.set(currentBuild, forKey: seenKey)
    }
}

struct WhatsNewView: View {
    let entries: [WhatsNewEntry]
    let onDone: () -> Void

    var body: some View {
        NavigationStack {
            List {
                if entries.isEmpty {
                    Text("Für diese Version liegt keine Liste der Neuerungen vor.")
                        .foregroundStyle(Theme.secondaryText)
                        .listRowBackground(Theme.card)
                }
                ForEach(entries) { e in
                    Section {
                        ForEach(e.items, id: \.self) { item in
                            Label(item, systemImage: "sparkle")
                                .foregroundStyle(Theme.text)
                        }
                    } header: {
                        Text(entries.count == 1 ? e.date : "Version \(e.version) · \(e.date)")
                    }
                    .listRowBackground(Theme.card)
                }
            }
            .clearListBackground()
            .navigationTitle(entries.count == 1 ? "Neu in \(entries[0].version)" : "Das ist neu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Alles klar", action: onDone) }
            }
        }
    }
}
