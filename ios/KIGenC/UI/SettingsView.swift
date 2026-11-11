import SwiftUI

struct SettingsView: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var keyInput = ""
    @State private var bflInput = ""
    @State private var testStatus: String?
    @State private var testing = false
    @State private var showLog = false
    @State private var showWhatsNew = false
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
