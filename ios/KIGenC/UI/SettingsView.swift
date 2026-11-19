import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @ObservedObject private var music = ChartMusic.shared
    @ObservedObject private var memory = MemoryStore.shared
    @State private var keyInput = ""
    @State private var bflInput = ""
    @State private var testStatus: String?
    @State private var testing = false
    @State private var showLog = false
    @State private var showWhatsNew = false
    @State private var showRevoke = false
    @State private var confirmDelete = false

    private var version: String {
        let v = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0"
        let b = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "1"
        return "\(v) (\(b))"
    }

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    if settings.usesTestKey {
                        Text("✓ Test-Schlüssel aktiv – für die Testversion ist bereits ein Mistral-Schlüssel hinterlegt. Du musst nichts eintragen. Optional kannst du deinen eigenen verwenden.")
                            .font(.callout).foregroundStyle(Theme.cyan)
                    }
                    SecureField(!settings.apiKey.isEmpty ? "•••••••• (gespeichert)" : "Eigenen Mistral-API-Schlüssel einfügen", text: $keyInput)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    HStack {
                        Button("Speichern") { settings.apiKey = keyInput; keyInput = ""; Task { await test() } }
                            .disabled(keyInput.trimmingCharacters(in: .whitespaces).isEmpty)
                        Spacer()
                        Button { Task { await test() } } label: { HStack { if testing { ProgressView() }; Text("Testen") } }
                            .disabled(!settings.hasKey || testing)
                    }
                    if let testStatus { Text(testStatus).font(.caption).foregroundStyle(testStatus.hasPrefix("✅") ? .green : .orange) }
                    Link("Kostenlosen Schlüssel holen (console.mistral.ai)", destination: URL(string: "https://console.mistral.ai/api-keys")!).font(.callout)
                    if !settings.apiKey.isEmpty { Button("Schlüssel entfernen", role: .destructive) { settings.apiKey = "" } }
                } header: { Text("Mistral AI") } footer: { Text("Der Schlüssel wird nur im iOS-Schlüsselbund dieses Geräts gespeichert.") }
                .listRowBackground(Theme.card)

                Section {
                    DesignPicker(selected: $settings.design)
                        .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                } header: { Text("Design") } footer: { Text("Wähle eine Oberfläche – wie die Skins eines Synthesizers. Die Änderung gilt sofort.") }
                .listRowBackground(Theme.card)

                Section {
                    AppIconPicker()
                        .listRowInsets(EdgeInsets(top: 8, leading: 12, bottom: 8, trailing: 12))
                } header: { Text("App-Symbol") } footer: { Text("Symbol auf dem Home-Bildschirm. iOS bestätigt den Wechsel kurz mit einer Meldung.") }
                .listRowBackground(Theme.card)

                Section("Modelle") {
                    Picker("Chat", selection: $settings.chatModel) { ForEach(models(AppSettings.defaultChatModels, settings.chatModel), id: \.self) { Text($0) } }
                    Picker("Code", selection: $settings.codeModel) { ForEach(models(AppSettings.defaultCodeModels, settings.codeModel), id: \.self) { Text($0) } }
                    Toggle("Antworten automatisch vorlesen", isOn: $settings.autoSpeak)
                }
                .listRowBackground(Theme.card)

                Section {
                    Toggle("Gedächtnis nutzen", isOn: $settings.memoryEnabled)
                    if settings.memoryEnabled {
                        Toggle("Automatisch dazulernen", isOn: $settings.memoryAuto)
                    }
                    NavigationLink {
                        MemoryView()
                    } label: {
                        LabeledContent("Gemerktes ansehen", value: memory.facts.isEmpty ? "leer" : "\(memory.facts.count)")
                    }
                } header: { Text("Gedächtnis") } footer: {
                    Text("Die KI kennt in jedem Chat, was sie sich über dich gemerkt hat – z. B. nach „Merk dir, dass …“. Gespeichert nur auf diesem Gerät, bei Anfragen an Mistral mitgeschickt. Keine sensiblen Angaben beim automatischen Lernen.")
                }
                .listRowBackground(Theme.card)

                Section {
                    Toggle("Nr. 1 der Charts beim Start", isOn: $settings.startMusic)
                    if settings.startMusic {
                        Picker("Genre", selection: $settings.musicGenre) {
                            ForEach(ChartMusic.genres, id: \.id) { Text($0.name).tag($0.id) }
                        }
                        if music.track != nil || music.loading {
                            Button { music.stop() } label: { Label("Stoppen", systemImage: "stop.circle") }
                        } else {
                            Button { music.play(genre: settings.musicGenre) } label: { Label("Jetzt anhören", systemImage: "music.note") }
                        }
                        if let e = music.error { Text(e).font(.caption).foregroundStyle(.orange) }
                    }
                } header: { Text("Musik beim Start") } footer: {
                    Text("Beim Öffnen der App läuft eine 30-Sekunden-Hörprobe der aktuellen Nr. 1 (Deezer) – nicht bei Lautlos-Schalter und nicht, wenn schon Musik läuft. Dazu verbindet sich die App mit Deezer.")
                }
                .listRowBackground(Theme.card)

                Section {
                    Picker("Bildgenerator", selection: $settings.imageProvider) { ForEach(ImageProvider.allCases) { Text($0.title).tag($0) } }
                    Text(settings.imageProvider.detail).font(.caption).foregroundStyle(Theme.secondaryText)
                    SecureField(settings.canEditFaithfully ? "•••••••• (gespeichert)" : "Black-Forest-Labs-Schlüssel (optional)", text: $bflInput)
                        .textInputAutocapitalization(.never).autocorrectionDisabled()
                    HStack {
                        Button("Speichern") { settings.bflKey = bflInput; bflInput = "" }.disabled(bflInput.isEmpty)
                        Spacer()
                        if settings.canEditFaithfully { Button("Entfernen", role: .destructive) { settings.bflKey = "" } }
                    }
                    Link("Schlüssel holen (dashboard.bfl.ai)", destination: URL(string: "https://dashboard.bfl.ai")!).font(.callout)
                } header: { Text("Bilder") } footer: { Text("Mit Black-Forest-Labs-Schlüssel werden Fotos gesichtstreu bearbeitet (FLUX Kontext).") }
                .listRowBackground(Theme.card)

                if Beta.isBeta {
                    Section("Testversion") {
                        LabeledContent("Gültig bis", value: Beta.expiryText)
                        LabeledContent("Verbleibend", value: "\(Beta.daysLeft) Tage")
                        if let c = Beta.storedCode { LabeledContent("Zugangscode", value: c) }
                        ShareLink(item: feedbackText) { Label("Feedback senden", systemImage: "paperplane") }
                        Text("Updates kommen automatisch über die TestFlight-App.").font(.caption).foregroundStyle(Theme.secondaryText)
                    }
                    .listRowBackground(Theme.card)
                }

                Section("Hilfe & Daten") {
                    Button { showLog = true } label: { Label("Fehlerprotokoll", systemImage: "ladybug") }
                    Link(destination: URL(string: "https://github.com/wolter67aw-cloud/eu-ki-genc/blob/main/docs/datenschutz.md")!) { Label("Datenschutz", systemImage: "hand.raised") }
                    Button { showRevoke = true } label: { Label("Zustimmung zur KI-Nutzung widerrufen", systemImage: "lock.shield") }
                    Button { settings.onboardingDone = false } label: { Label("Einführung erneut zeigen (beim nächsten Start)", systemImage: "questionmark.circle") }
                    Button(role: .destructive) { confirmDelete = true } label: { Label("Alle Chats & Ergebnisse löschen", systemImage: "trash") }
                }
                .listRowBackground(Theme.card)

                Section {
                    LabeledContent("Version", value: version)
                    Button { showWhatsNew = true } label: { Label("Was ist neu?", systemImage: "sparkles") }
                    LabeledContent("KI-Anbieter", value: "Mistral AI (Paris, EU)")
                }
                .listRowBackground(Theme.card)
            }
            .clearListBackground()
            .navigationTitle("Optionen")
            .confirmationDialog("Wirklich alle Chats, Aufgaben und Ergebnisse löschen?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Löschen", role: .destructive) {
                    Store.deleteAll()
                    ConversationStore.shared.conversations = []
                    ResultStore.shared.results = []
                    TaskStore.shared.tasks = []
                }
            }
            .sheet(isPresented: $showLog) { LogView() }
            .sheet(isPresented: $showWhatsNew) { WhatsNewView(entries: WhatsNew.all()) { showWhatsNew = false } }
            .confirmationDialog("Zustimmung widerrufen?", isPresented: $showRevoke, titleVisibility: .visible) {
                Button("Widerrufen", role: .destructive) { settings.aiConsent = false }
                Button("Abbrechen", role: .cancel) {}
            } message: {
                Text("Ohne deine Zustimmung sendet KI-GenC keine Daten mehr an KI-Dienste. Der Datenschutz-Hinweis erscheint sofort erneut; deine gespeicherten Daten bleiben erhalten.")
            }
        }
    }

    private var feedbackText: String {
        "Feedback zu KI-GenC (iOS \(version), Code \(Beta.storedCode ?? "-")):\n\n"
    }

    private func models(_ defaults: [String], _ current: String) -> [String] {
        var list = settings.cachedModels.isEmpty ? defaults : settings.cachedModels
        if !list.contains(current) { list.insert(current, at: 0) }
        return list
    }

    @MainActor
    private func test() async {
        testing = true
        defer { testing = false }
        do {
            let models = try await MistralClient.shared.listModels(key: settings.key)
            settings.cachedModels = models
            testStatus = "✅ Schlüssel funktioniert – \(models.count) Modelle verfügbar."
        } catch {
            testStatus = "⚠️ " + friendly(error)
        }
    }
}

struct LogView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var text = ErrorLog.text

    var body: some View {
        NavigationStack {
            ScrollView {
                Text(text.isEmpty ? "Keine Fehler protokolliert. 🎉" : text)
                    .font(.system(.footnote, design: .monospaced)).textSelection(.enabled)
                    .frame(maxWidth: .infinity, alignment: .leading).padding()
            }
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Fehlerprotokoll")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } }
                ToolbarItem(placement: .topBarLeading) { ShareLink(item: text) { Image(systemName: "square.and.arrow.up") }.disabled(text.isEmpty) }
                ToolbarItem(placement: .destructiveAction) { Button("Leeren") { ErrorLog.clear(); text = "" } }
            }
        }
        .preferredColorScheme(Theme.current.dark ? .dark : .light)
    }
}

/// Gedächtnis ansehen, bearbeiten, ergänzen und löschen.
struct MemoryView: View {
    @ObservedObject private var memory = MemoryStore.shared
    @State private var newFact = ""
    @State private var editing: MemoryFact?
    @State private var editText = ""
    @State private var confirmClear = false

    var body: some View {
        List {
            Section {
                Text("Das weiß die KI über dich. Im Chat kannst du sagen „Merk dir, dass …“.")
                    .font(.caption).foregroundStyle(Theme.secondaryText)
            }
            .listRowBackground(Color.clear)
            Section {
                if memory.facts.isEmpty { Text("Noch nichts gemerkt.").foregroundStyle(Theme.secondaryText) }
                ForEach(memory.facts) { f in
                    Button { editing = f; editText = f.text } label: { Text(f.text).foregroundStyle(Theme.text) }
                }
                .onDelete { idx in idx.map { memory.facts[$0].id }.forEach(memory.delete) }
            }
            .listRowBackground(Theme.card)
            Section("Selbst hinzufügen") {
                HStack {
                    TextField("z. B. „Heißt Andy und wohnt in Köln“", text: $newFact)
                    Button("Merken") { memory.add(newFact); newFact = "" }
                        .disabled(newFact.trimmingCharacters(in: .whitespaces).isEmpty)
                }
            }
            .listRowBackground(Theme.card)
        }
        .clearListBackground()
        .navigationTitle("Gedächtnis")
        .toolbar {
            if !memory.facts.isEmpty {
                ToolbarItem(placement: .destructiveAction) { Button("Alles vergessen", role: .destructive) { confirmClear = true } }
            }
        }
        .alert("Bearbeiten", isPresented: Binding(get: { editing != nil }, set: { if !$0 { editing = nil } })) {
            TextField("Eintrag", text: $editText)
            Button("Speichern") { if let e = editing { memory.update(e.id, editText) }; editing = nil }
            Button("Abbrechen", role: .cancel) { editing = nil }
        }
        .confirmationDialog("Alle \(memory.facts.count) Einträge vergessen?", isPresented: $confirmClear, titleVisibility: .visible) {
            Button("Alles vergessen", role: .destructive) { memory.clear() }
        }
    }
}
