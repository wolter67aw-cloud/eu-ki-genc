import SwiftUI
import UniformTypeIdentifiers

/// Liste aller Projekte: anlegen, bearbeiten, löschen, in neuem Chat verwenden.
struct ProjectsView: View {
    var onUse: (Project) -> Void
    @ObservedObject private var store = ProjectStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var creating = false

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Ein Projekt hat feste Anweisungen und Wissen (Dateien, Notizen). Jeder Chat im Projekt kennt beides automatisch.")
                        .font(.caption).foregroundStyle(Theme.secondaryText)
                }
                .listRowBackground(Color.clear)
                if store.projects.isEmpty {
                    Text("Noch keine Projekte.").foregroundStyle(Theme.secondaryText)
                }
                ForEach(store.projects) { p in
                    NavigationLink {
                        ProjectEditView(project: p, onUse: onUse)
                    } label: {
                        VStack(alignment: .leading, spacing: 2) {
                            Label(p.name, systemImage: "folder").foregroundStyle(Theme.text)
                            Text("\(p.files.count == 1 ? "1 Wissens-Eintrag" : "\(p.files.count) Wissens-Einträge")\(p.instructions.isEmpty ? "" : " · mit Anweisungen")")
                                .font(.caption).foregroundStyle(Theme.secondaryText)
                        }
                    }
                    .swipeActions(edge: .leading) {
                        Button { onUse(p) } label: { Label("Neuer Chat", systemImage: "bubble.left") }.tint(Theme.cyan)
                    }
                    .listRowBackground(Theme.card)
                }
                .onDelete { idx in idx.map { store.projects[$0].id }.forEach(store.delete) }
            }
            .clearListBackground()
            .navigationTitle("Projekte")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } }
                ToolbarItem(placement: .topBarLeading) {
                    Button { creating = true } label: { Label("Neues Projekt", systemImage: "plus") }
                }
            }
            .navigationDestination(isPresented: $creating) {
                ProjectEditView(project: nil, onUse: onUse)
            }
        }
        .preferredColorScheme(Theme.current.dark ? .dark : .light)
    }
}

/// Projekt anlegen oder bearbeiten: Name, Anweisungen, Wissen.
struct ProjectEditView: View {
    let project: Project?
    var onUse: (Project) -> Void
    @EnvironmentObject private var settings: AppSettings
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var instructions = ""
    @State private var files: [ProjectFile] = []
    @State private var loaded = false
    @State private var showFiles = false
    @State private var showNote = false
    @State private var noteName = ""
    @State private var noteText = ""
    @State private var busy = false
    @State private var status: String?

    private var total: Int { files.reduce(0) { $0 + $1.text.count } }

    var body: some View {
        Form {
            Section("Name") {
                TextField("z. B. Verein, Bewerbung, Hausbau", text: $name)
            }
            .listRowBackground(Theme.card)
            Section {
                TextEditor(text: $instructions).frame(minHeight: 110)
            } header: { Text("Anweisungen (optional)") } footer: {
                Text("z. B. „Antworte immer kurz und förmlich. Ich bin Kassenwart des TSV Musterstadt.“")
            }
            .listRowBackground(Theme.card)
            Section {
                ForEach(files) { f in
                    VStack(alignment: .leading, spacing: 2) {
                        Label(f.name, systemImage: "doc.text").lineLimit(1)
                        Text("\(f.text.count) Zeichen").font(.caption).foregroundStyle(Theme.secondaryText)
                    }
                }
                .onDelete { files.remove(atOffsets: $0) }
                HStack {
                    Button { showFiles = true } label: { Label("Dateien", systemImage: "paperclip") }.buttonStyle(.bordered)
                    Button { noteName = ""; noteText = ""; showNote = true } label: { Label("Notiz", systemImage: "note.text.badge.plus") }.buttonStyle(.bordered)
                    if busy { ProgressView() }
                }
                if let status { Text(status).font(.caption).foregroundStyle(Theme.secondaryText) }
                if total > Project.maxContext {
                    Text("Das Wissen ist länger als \(Project.maxContext / 1000).000 Zeichen – der Rest wird pro Anfrage weggelassen.")
                        .font(.caption).foregroundStyle(.orange)
                }
            } header: { Text("Wissen") } footer: {
                Text("Texte, PDFs, Word-Dateien oder Notizen – die KI kennt sie in jedem Chat dieses Projekts. Fotos werden einmalig von der KI abgeschrieben.")
            }
            .listRowBackground(Theme.card)
            if let project {
                Section {
                    Button { onUse(project) } label: { Label("Neuen Chat in diesem Projekt starten", systemImage: "bubble.left.and.bubble.right") }
                }
                .listRowBackground(Theme.card)
            }
        }
        .scrollContentBackground(.hidden)
        .background(Theme.backgroundGradient.ignoresSafeArea())
        .navigationTitle(project == nil ? "Neues Projekt" : "Projekt bearbeiten")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button("Speichern") { save() }
                    .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty || busy)
            }
        }
        .onAppear {
            guard !loaded else { return }
            loaded = true
            name = project?.name ?? ""
            instructions = project?.instructions ?? ""
            files = project?.files ?? []
        }
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.pdf, .plainText, .commaSeparatedText, .json, .image, .data, .item], allowsMultipleSelection: true) { result in
            if case .success(let urls) = result { Task { await importFiles(urls) } }
        }
        .sheet(isPresented: $showNote) {
            NavigationStack {
                Form {
                    TextField("Titel", text: $noteName)
                    TextEditor(text: $noteText).frame(minHeight: 180)
                }
                .navigationTitle("Notiz hinzufügen")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { showNote = false } }
                    ToolbarItem(placement: .confirmationAction) {
                        Button("Hinzufügen") {
                            let t = noteText.trimmingCharacters(in: .whitespacesAndNewlines)
                            let n = noteName.trimmingCharacters(in: .whitespaces)
                            files.append(ProjectFile(name: n.isEmpty ? "Notiz" : n, text: t))
                            showNote = false
                        }
                        .disabled(noteText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                    }
                }
            }
        }
    }

    private func save() {
        var p = project ?? Project(name: name)
        p.name = String(name.trimmingCharacters(in: .whitespaces).prefix(60))
        p.instructions = instructions.trimmingCharacters(in: .whitespacesAndNewlines)
        p.files = files
        ProjectStore.shared.save(p)
        dismiss()
    }

    @MainActor
    private func importFiles(_ urls: [URL]) async {
        busy = true
        var ok = 0
        var failed: String?
        for url in urls {
            status = "Lese „\(url.lastPathComponent)“ …"
            do {
                let a = try AttachmentLoader.file(url)
                if a.kind == .file, !a.text.isEmpty {
                    files.append(ProjectFile(name: a.name, text: a.text))
                    ok += 1
                } else if a.kind == .image, !a.images.isEmpty {
                    guard settings.hasKey else { throw APIError(code: 0, message: "Für Fotos wird der Mistral-Schlüssel gebraucht.") }
                    let text = try await MistralClient.shared.complete(
                        key: settings.key, model: "mistral-small-latest",
                        messages: [ApiMessage(role: "user", text: "Gib den gesamten Text dieses Bildes vollständig und wortgetreu wieder (Tabellen als Markdown). Beschreibe Abbildungen ohne Text in einem kurzen Satz. Keine Einleitung.", images: a.images.compactMap { $0.dataURL })],
                        temperature: 0)
                    let t = text.trimmingCharacters(in: .whitespacesAndNewlines)
                    if t.isEmpty { throw APIError(code: 0, message: "„\(a.name)“ konnte nicht gelesen werden.") }
                    files.append(ProjectFile(name: a.name, text: t))
                    ok += 1
                } else {
                    throw APIError(code: 0, message: "„\(a.name)“: dieser Dateityp wird für Projektwissen nicht unterstützt.")
                }
            } catch {
                failed = friendly(error)
                ErrorLog.report(error, context: "Projektwissen")
            }
        }
        busy = false
        var parts: [String] = []
        if ok > 0 { parts.append("\(ok) hinzugefügt.") }
        if let failed { parts.append("Nicht gelesen: \(failed)") }
        status = parts.isEmpty ? nil : parts.joined(separator: " ")
    }
}
