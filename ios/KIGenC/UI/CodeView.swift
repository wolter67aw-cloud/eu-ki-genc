import SwiftUI

struct CodeView: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var mode = "Erstellen"
    @State private var language = "Python"
    @State private var input = ""
    @State private var attachments: [Attachment] = []
    @State private var result = ""
    @State private var error: String?
    @State private var busy = false
    @State private var task: Task<Void, Never>?

    private let modes = ["Erstellen", "Erklären", "Fehler finden", "Verbessern", "Automation", "Tests schreiben", "Umwandeln"]
    private let languages = ["Python", "JavaScript", "TypeScript", "Kotlin", "Swift", "Java", "C#", "C++", "Go", "Rust", "PHP", "SQL", "Bash", "PowerShell", "HTML/CSS", "Excel-Formel", "VBA", "Apple Kurzbefehle"]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if !settings.hasKey { NeedsKeyView() }
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack {
                            ForEach(modes, id: \.self) { m in
                                Button { mode = m } label: {
                                    Text(m).font(.callout).padding(.horizontal, 12).padding(.vertical, 8)
                                        .background(mode == m ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card), in: Capsule())
                                        .foregroundStyle(.white)
                                }
                            }
                        }
                    }
                    HStack {
                        Picker(mode == "Umwandeln" ? "Zielsprache" : "Sprache", selection: $language) { ForEach(languages, id: \.self) { Text($0) } }
                        Spacer()
                        Picker("Modell", selection: $settings.codeModel) { ForEach(AppSettings.defaultCodeModels, id: \.self) { Text($0) } }
                    }
                    .tint(Theme.cyan)
                    TextField(mode == "Erstellen" || mode == "Automation" ? "Was soll das Programm tun?" : "Code hier einfügen …", text: $input, axis: .vertical)
                        .lineLimit(5...16)
                        .font(.system(.callout, design: mode == "Erstellen" || mode == "Automation" ? .default : .monospaced))
                        .autocorrectionDisabled()
                        .textInputAutocapitalization(.never)
                        .padding(12).background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                    AttachmentBar(attachments: $attachments)
                    GradientButton(title: busy ? "Arbeitet …" : mode, icon: "chevron.left.forwardslash.chevron.right", busy: busy) { run() }
                        .disabled(input.trimmingCharacters(in: .whitespaces).isEmpty && attachments.isEmpty || !settings.hasKey)
                    if busy { Button("Abbrechen") { task?.cancel(); busy = false }.frame(maxWidth: .infinity) }
                    if let error { ErrorCard(message: error) }
                    if !result.isEmpty {
                        VStack(alignment: .leading, spacing: 10) {
                            MarkdownView(text: result)
                            if !busy { ResultActions(text: result, kind: "Code") }
                        }
                        .card()
                    }
                }
                .padding()
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Code")
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    private func run() {
        let instruction: String
        switch mode {
        case "Erklären": instruction = "Erkläre diesen Code Schritt für Schritt auf Deutsch, verständlich auch für Einsteiger:"
        case "Fehler finden": instruction = "Finde Fehler, Sicherheitsprobleme und Schwachstellen in diesem Code. Erkläre sie und gib den korrigierten Code vollständig aus:"
        case "Verbessern": instruction = "Verbessere Lesbarkeit, Struktur und Performance dieses Codes. Gib den überarbeiteten Code vollständig aus und erkläre die Änderungen kurz:"
        case "Automation": instruction = "Erstelle ein robustes Automations-Skript in \(language) mit Kommentaren, Fehlerbehandlung und kurzer Anleitung zur Einrichtung für:"
        case "Tests schreiben": instruction = "Schreibe sinnvolle, vollständige Unit-Tests für diesen Code:"
        case "Umwandeln": instruction = "Übersetze diesen Code nach \(language), idiomatisch und vollständig:"
        default: instruction = "Schreibe vollständigen, lauffähigen \(language)-Code mit Kommentaren und kurzer Erklärung für:"
        }
        let (extra, images) = AttachmentLoader.prompt(for: attachments)
        result = ""; error = nil; busy = true
        let model = images.isEmpty ? settings.codeModel : "mistral-small-latest"
        task = Task { @MainActor in
            do {
                let msgs = [
                    ApiMessage(role: "system", text: "Du bist ein erfahrener Software-Entwickler. Antworte auf Deutsch, Code immer in Markdown-Codeblöcken mit Sprachangabe."),
                    ApiMessage(role: "user", text: "\(instruction)\n\n\(input)\(extra)", images: images),
                ]
                for try await c in MistralClient.shared.stream(key: settings.key, model: model, messages: msgs, temperature: 0.2) { result += c }
            } catch {
                ErrorLog.report(error, context: "Code")
                if !Task.isCancelled { self.error = friendly(error) }
            }
            busy = false
        }
    }
}
