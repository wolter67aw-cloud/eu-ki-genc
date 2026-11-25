import SwiftUI

@MainActor
final class ChatModel: ObservableObject {
    @Published var conversation = Conversation(title: "Neuer Chat", messages: [])
    @Published var input = ""
    @Published var attachments: [Attachment] = []
    @Published var busy = false
    /// Aktives Projekt für neue bzw. geöffnete Unterhaltung (nil = ohne Projekt).
    @Published var project: Project?
    /// Kurzer Hinweis (z. B. „Gemerkt: …“).
    @Published var notice: String?
    /// Websuche für die nächsten Fragen (Antwort mit aktuellen Quellen).
    @Published var webSearch = false
    /// Code ausführen: die KI rechnet mit echtem Python-Code (Mistral Code-Interpreter).
    @Published var codeRun = false
    /// Gründlich nachdenken: Reasoning-Modell (Magistral) statt des normalen Chat-Modells.
    @Published var thinking = false
    /// Bild erzeugen: die KI malt zur nächsten Nachricht ein Bild (sonst automatisch bei „Erstelle ein Bild …“).
    @Published var imageGen = false
    private var task: Task<Void, Never>?

    static let system = """
    Du bist KI-GenC, ein hilfreicher, präziser KI-Assistent aus Europa. Antworte standardmäßig auf Deutsch \
    (außer der Nutzer schreibt in einer anderen Sprache), klar gegliedert in Markdown. Code immer in Codeblöcken mit Sprachangabe.
    """

    func send(settings: AppSettings) {
        let text = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard (!text.isEmpty || !attachments.isEmpty), !busy, settings.hasKey else { return }
        let (extra, images) = AttachmentLoader.prompt(for: attachments)
        let shown = text.isEmpty ? "Bitte analysiere den Anhang." : text
        let display = shown + (extra.isEmpty ? "" : "\n\n📎 " + attachments.map(\.name).joined(separator: ", "))
        input = ""
        attachments = []
        ask(display: display, question: shown, extra: extra, images: images, learnText: text, setTitle: true, settings: settings)
    }

    /// Letzte Antwort neu erzeugen (auch nach einem Fehler): ab der letzten eigenen Nachricht erneut fragen.
    func regenerate(settings: AppSettings) {
        guard !busy, settings.hasKey, let idx = conversation.messages.lastIndex(where: { $0.role == "user" }) else { return }
        let user = conversation.messages[idx]
        conversation.messages.removeSubrange(idx...)
        if user.text.contains(Self.clip) { notice = "Hinweis: Dateiinhalte werden nicht erneut mitgeschickt – bei Bedarf neu anhängen." }
        // Gedächtnis wurde bei der ersten Antwort schon gepflegt → nicht doppelt lernen.
        ask(display: user.text, question: user.text, extra: "", images: user.images, learnText: "", setTitle: false, settings: settings)
    }

    /// Eigene Nachricht bearbeiten und ab dort neu senden (spätere Nachrichten entfallen, Fotos bleiben).
    func editAndResend(id: UUID, newText: String, settings: AppSettings) {
        let body = newText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !busy, settings.hasKey, !body.isEmpty, let idx = conversation.messages.firstIndex(where: { $0.id == id }) else { return }
        let original = conversation.messages[idx]
        let suffix = Self.attachmentSuffix(of: original.text)
        conversation.messages.removeSubrange(idx...)
        if !suffix.isEmpty { notice = "Hinweis: Dateiinhalte werden nicht erneut mitgeschickt – bei Bedarf neu anhängen." }
        ask(display: body + suffix, question: body + suffix, extra: "", images: original.images, learnText: body, setTitle: idx == 0, settings: settings)
    }

    private static let clip = "\n\n📎 "

    /// Eigentlicher Text einer Nachricht ohne die Zeile „📎 Anhänge“.
    static func editableText(of text: String) -> String {
        guard let r = text.range(of: clip, options: .backwards) else { return text }
        return String(text[..<r.lowerBound])
    }

    /// Angehängte Zeile „📎 Anhänge“ (oder leer).
    static func attachmentSuffix(of text: String) -> String {
        guard let r = text.range(of: clip, options: .backwards) else { return "" }
        return String(text[r.lowerBound...])
    }

    /// Frage stellen: eigene Nachricht anzeigen, Antwort holen (Chat, Websuche oder Code), Verlauf speichern.
    private func ask(display: String, question shown: String, extra: String, images: [String], learnText text: String, setTitle: Bool, settings: AppSettings) {
        conversation.messages.append(ChatMessage(role: "user", text: display, images: images))
        if setTitle && conversation.messages.count == 1 { conversation.title = String(Self.editableText(of: shown).prefix(50)) }
        let projectText = ProjectStore.shared.byID(conversation.projectID)?.systemText() ?? ""
        let memoryText = settings.memoryEnabled ? MemoryStore.shared.systemText : ""
        let style = AppSettings.styleText(settings.answerStyle)
        var apiMessages = [ApiMessage(role: "system", text: Self.system + style + memoryText + projectText)]
        let history = conversation.messages.dropLast().suffix(20)
        for m in history where !m.isError { apiMessages.append(ApiMessage(role: m.role, text: m.text, images: m.role == "user" ? m.images : [])) }
        apiMessages.append(ApiMessage(role: "user", text: shown + extra, images: images))
        conversation.messages.append(ChatMessage(role: "assistant", text: ""))
        let index = conversation.messages.count - 1
        busy = true
        let baseModel = images.isEmpty ? settings.chatModel : (settings.chatModel.contains("ministral") || settings.chatModel.contains("magistral") ? "mistral-small-latest" : settings.chatModel)
        let model = thinking && !codeRun && !webSearch && images.isEmpty ? "magistral-medium-latest" : baseModel
        let useImage = !codeRun && !webSearch && (imageGen || ImageRequest.matches(shown))
        let imageContext = Array(history)
        let useCode = codeRun && images.isEmpty
        let useWeb = !useCode && webSearch && images.isEmpty && extra.isEmpty
        let codeInstructions = "Du bist KI-GenC. Antworte auf Deutsch in Markdown. Nutze für Berechnungen, Statistik, Datenanalyse und Diagramme IMMER das Werkzeug code_interpreter (Python) und erkläre das Ergebnis verständlich. Diagramme mit matplotlib, Beschriftungen auf Deutsch." + style + memoryText + String(projectText.prefix(8_000))
        let codeInput = useCode ? Self.webQuestion(history: Array(history), question: shown + extra, extras: "") : ""
        let webQuestion = useWeb ? Self.webQuestion(history: Array(history), question: shown, extras: style + memoryText + String(projectText.prefix(8_000))) : ""
        task = Task {
            do {
                if useImage {
                    // Bild erzeugen: Mistral formuliert aus Wunsch + Verlauf einen Bild-Prompt, dann malt der Bildgenerator
                    let (t, img) = try await Self.createImage(wish: shown, history: imageContext, images: images, settings: settings) { status in
                        self.conversation.messages[index].text = status
                    }
                    conversation.messages[index].text = t
                    conversation.messages[index].images = [img]
                } else if useCode {
                    // Code-Interpreter: echte Berechnung in Python, Ergebnis + Code + Diagramme
                    var run: MistralClient.CodeRun
                    do {
                        run = try await MistralClient.shared.runCode(key: settings.key, model: model, instructions: codeInstructions, input: codeInput)
                    } catch let e as APIError where e.code != 401 && e.code != 429 && model != "mistral-medium-latest" {
                        run = try await MistralClient.shared.runCode(key: settings.key, model: "mistral-medium-latest", instructions: codeInstructions, input: codeInput)
                    }
                    conversation.messages[index].text = run.markdown
                    conversation.messages[index].images = run.images.map { "data:image/png;base64," + $0.base64EncodedString() }
                } else if useWeb {
                    // Websuche: Mistral sucht selbst im Web und liefert Quellen mit
                    // Zeitlimit 90 s; klappt die Websuche nicht, antwortet das Modell ohne aktuelle Quellen (mit Hinweis)
                    let (answer, sources) = try await MistralClient.shared.webSearchOrKnowledge(key: settings.key, model: model, question: webQuestion,
                                                                                                fallbackModel: model, fallback: apiMessages)
                    var t = answer.isEmpty ? "(keine Antwort)" : answer
                    if !sources.isEmpty {
                        t += "\n\n**Quellen:**\n" + sources.enumerated().map { i, s in
                            "\(i + 1). [\(s.0.replacingOccurrences(of: "[", with: "").replacingOccurrences(of: "]", with: ""))](\(s.1))"
                        }.joined(separator: "\n")
                    }
                    conversation.messages[index].text = t
                } else {
                    for try await chunk in MistralClient.shared.stream(key: settings.key, model: model, messages: apiMessages) {
                        conversation.messages[index].text += chunk
                    }
                }
                if conversation.messages[index].text.isEmpty { conversation.messages[index].text = "(keine Antwort)" }
                if settings.autoSpeak { Speaker.shared.speak(conversation.messages[index].text) }
                if settings.memoryEnabled && !text.isEmpty { learn(text, key: settings.key, auto: settings.memoryAuto) }
            } catch {
                ErrorLog.report(error, context: "Chat")
                conversation.messages[index].text = "⚠️ " + friendly(error)
                conversation.messages[index].isError = true
            }
            busy = false
            conversation.updated = Date()
            ConversationStore.shared.upsert(conversation)
        }
    }

    /// Erzeugt ein Bild zur Nachricht. Liefert Antworttext (Bildunterschrift, Prompt, Anbieter) und das Bild als Data-URL.
    /// Mit angehängtem Foto: gesichtstreue Bearbeitung (FLUX Kontext, falls BFL-Schlüssel) oder Foto als Vorlage beschreiben.
    private static func createImage(wish: String, history: [ChatMessage], images: [String], settings: AppSettings,
                                    progress: @escaping (String) -> Void) async throws -> (String, String) {
        progress("_Bildidee wird ausgearbeitet …_")
        let photos = images.compactMap { MessageRow.image(from: $0) }
        let faithful = !photos.isEmpty && settings.canEditFaithfully
        var vorlage = ""
        if !photos.isEmpty && !faithful {
            progress("_Die KI sieht sich dein Foto an …_")
            vorlage = (try? await MistralClient.shared.complete(key: settings.key, model: "mistral-small-latest", messages: [
                ApiMessage(role: "user", text: "Beschreibe dieses Bild präzise als Bild-Prompt auf Englisch: Motive, bei Personen Gesicht, Haare, Alter, Kleidung, Pose; Umgebung, Licht, Farben, Stil. Nur die Beschreibung.",
                           images: Array(images.prefix(4))),
            ])) ?? ""
        }
        let context = history.filter { !$0.isError }.suffix(6).map { ($0.role == "user" ? "Nutzer: " : "KI: ") + String($0.text.prefix(800)) }.joined(separator: "\n")
        var instruction = """
        Du erstellst Bild-Prompts für einen Bildgenerator (FLUX). Antworte NUR als JSON-Objekt mit den Feldern \
        "prompt" (detaillierter englischer Bild-Prompt: Motiv, Stil, Licht, Farben, Perspektive, Komposition; max. 120 Wörter; \
        Texte im Bild nur wenn ausdrücklich gewünscht, dann wörtlich in Anführungszeichen), \
        "caption" (ein kurzer deutscher Satz, was das Bild zeigt) und \
        "aspect" (eines von "1:1", "3:4", "4:3", "9:16", "16:9" – passend zum Wunsch, z. B. Hochformat fürs Handy, Querformat für Landschaften). \
        Bezieht sich der Wunsch auf ein vorheriges Bild (z. B. „mach es blauer“, „jetzt im Comic-Stil“), übernimm dessen Prompt aus dem Verlauf und ändere nur das Gewünschte. \
        Keine realen, bekannten Personen und keine geschützten Figuren oder Markenlogos nachbilden.
        """
        if !context.isEmpty { instruction += "\n\nBisheriger Verlauf:\n" + context }
        if !vorlage.isEmpty { instruction += "\n\nAngehängtes Foto als Vorlage (Beschreibung): " + vorlage }
        instruction += "\n\nWunsch des Nutzers: " + wish
        var prompt = wish, caption = "", aspect = 1.0
        let planModel = settings.chatModel.contains("magistral") || settings.chatModel.contains("codestral") ? MistralClient.fallbackModel : settings.chatModel
        if let raw = try? await MistralClient.shared.complete(key: settings.key, model: planModel, messages: [ApiMessage(role: "user", text: instruction)], json: true, temperature: 0.6),
           let a = raw.firstIndex(of: "{"), let b = raw.lastIndex(of: "}"),
           let o = try? JSONSerialization.jsonObject(with: Data(raw[a...b].utf8)) as? [String: Any] {
            if let p = o["prompt"] as? String, !p.trimmingCharacters(in: .whitespaces).isEmpty { prompt = p }
            caption = (o["caption"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
            let parts = (o["aspect"] as? String ?? "1:1").split(separator: ":").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
            if parts.count == 2, parts[1] > 0 { aspect = parts[0] / parts[1] }
        }
        try Task.checkCancellation()
        let data: Data, provider: String
        if faithful {
            progress("_Dein Foto wird gesichtstreu bearbeitet (FLUX Kontext) …_")
            data = try await ImageService.editFaithfully(key: settings.effectiveBflKey, instruction: wish, images: photos)
            provider = "FLUX Kontext (gesichtstreu)"
        } else {
            progress("_Das Bild wird gemalt …_")
            let r = try await ImageService.generate(prompt: prompt, aspect: aspect, settings: settings)
            data = r.0
            provider = r.1
        }
        guard let url = UIImage(data: data)?.dataURL else { throw APIError(code: 0, message: "Es wurde kein Bild erzeugt.") }
        var text = caption.isEmpty ? "Hier ist dein Bild." : caption
        if !faithful { text += "\n\n_Prompt: " + prompt.replacingOccurrences(of: "_", with: " ") + "_" }
        text += "\n\n_Erzeugt mit \(provider) – lange drücken zum Teilen oder Sichern._"
        return (text, url)
    }

    func stop() {
        task?.cancel()
        busy = false
    }

    /// Frage für die Websuche: Hinweise + letzte Nachrichten als Zusammenhang + aktuelle Frage.
    static func webQuestion(history: [ChatMessage], question: String, extras: String) -> String {
        let f = DateFormatter(); f.locale = Locale(identifier: "de_DE"); f.dateFormat = "d. MMMM yyyy"
        var s = "Heute ist \(f.string(from: Date()))." + extras
        let previous = history.filter { !$0.isError }.suffix(8)
        if !previous.isEmpty {
            s += "\n\nBisheriger Gesprächsverlauf (nur als Zusammenhang):\n"
            for m in previous { s += (m.role == "user" ? "Nutzer: " : "KI: ") + String(m.text.prefix(1500)) + "\n" }
        }
        return s + "\n\nAktuelle Frage des Nutzers:\n" + question
    }

    /// Gedächtnis pflegen: „Merk dir …“ immer, sonst (falls eingeschaltet) automatisch dazulernen.
    private func learn(_ text: String, key: String, auto: Bool) {
        let explicit = MemoryStore.explicitFact(text)
        guard explicit != nil || auto else { return }
        Task {
            let added = await MemoryStore.shared.learn(from: text, explicit: explicit, key: key)
            if added == 1, let last = MemoryStore.shared.facts.last { notice = "Gemerkt: \(last.text)" }
            else if added > 1 { notice = "\(added) Dinge gemerkt (Optionen → Gedächtnis)" }
        }
    }

    /// Ist die geöffnete Unterhaltung im Verlauf angeheftet? (Maßgeblich ist der gespeicherte Stand.)
    var isPinned: Bool {
        ConversationStore.shared.conversations.first { $0.id == conversation.id }?.pinned ?? conversation.pinned
    }

    /// Geöffnete Unterhaltung direkt aus dem Chat anheften bzw. lösen.
    func togglePin() {
        guard !conversation.messages.isEmpty else { return }
        let pinned = !isPinned
        conversation.pinned = pinned
        let store = ConversationStore.shared
        if store.conversations.contains(where: { $0.id == conversation.id }) {
            store.setPinned(conversation.id, pinned)
        } else {
            // Noch nicht gespeichert (z. B. erste Antwort läuft noch) → mit Anheft-Status anlegen.
            store.upsert(conversation)
        }
    }

    /// Gespeicherte Unterhaltung öffnen (inkl. ihres Projekts).
    func open(_ c: Conversation) {
        stop()
        conversation = c
        project = ProjectStore.shared.byID(c.projectID)
    }

    /// Projekt für die nächste neue Unterhaltung wählen; eine laufende wird gespeichert und neu begonnen.
    func selectProject(_ p: Project?) {
        if !conversation.messages.isEmpty { newChat() }
        project = p
        conversation.projectID = p?.id
    }

    func newChat() {
        stop()
        if !conversation.messages.isEmpty { ConversationStore.shared.upsert(conversation) }
        conversation = Conversation(title: "Neuer Chat", messages: [])
        conversation.projectID = project?.id
        input = ""
        attachments = []
    }
}

struct ChatView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var session: SessionController
    @StateObject private var model = ChatModel()
    @StateObject private var dictation = Dictation()
    @ObservedObject private var store = ConversationStore.shared
    @State private var showHistory = false
    @State private var showProjects = false
    @ObservedObject private var projects = ProjectStore.shared
    @State private var historyQuery = ""
    /// Sprung aus der Verlaufssuche: Ziel-Nachricht und kurz hervorgehobene Nachricht.
    @State private var jumpTo: UUID?
    @State private var flashID: UUID?
    @State private var showAttach = false
    /// Kamera direkt aus der Eingabezeile (Foto wird als Anhang mitgeschickt).
    @State private var showCamera = false
    /// Eigene Nachricht, die gerade bearbeitet wird (Blatt „Nachricht bearbeiten“).
    @State private var editing: ChatMessage?
    @State private var editText = ""
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if !settings.hasKey {
                    Spacer(); NeedsKeyView(); Spacer()
                } else if model.conversation.messages.isEmpty {
                    emptyState
                } else {
                    messages
                }
                if let n = model.notice {
                    Label(n, systemImage: "brain").font(.caption).foregroundStyle(Theme.cyan)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Theme.card, in: Capsule())
                        .padding(.bottom, 4)
                        .accessibilityAddTraits(.updatesFrequently)
                        .task(id: n) {
                            try? await Task.sleep(nanoseconds: 3_500_000_000)
                            if model.notice == n { model.notice = nil }
                        }
                }
                inputBar
            }
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle(model.conversation.messages.isEmpty ? (model.project?.name ?? "KI-GenC") : model.conversation.title)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button { showHistory = true } label: { Image(systemName: "clock.arrow.circlepath") }
                }
                ToolbarItemGroup(placement: .topBarTrailing) {
                    Menu {
                        Picker("Modell", selection: $settings.chatModel) {
                            ForEach(chatModels, id: \.self) { Text($0).tag($0) }
                        }
                        Toggle("Antworten vorlesen", isOn: $settings.autoSpeak)
                    } label: { Image(systemName: "cpu") }
                    if !model.conversation.messages.isEmpty {
                        Button { model.togglePin() } label: {
                            Image(systemName: model.isPinned ? "pin.fill" : "pin")
                        }
                        .accessibilityLabel(model.isPinned ? "Unterhaltung lösen (nicht mehr angeheftet)" : "Unterhaltung anheften")
                    }
                    Button { model.newChat() } label: { Image(systemName: "square.and.pencil") }
                        .accessibilityLabel("Neue Unterhaltung")
                }
            }
            .sheet(isPresented: $showHistory) { history }
            .fullScreenCover(isPresented: $showCamera, onDismiss: { session.externalPickerActive = false }) {
                CameraPicker { img in
                    if let img { model.attachments.append(Attachment(kind: .image, name: "Kamera", images: [img])) }
                }
                .ignoresSafeArea()
            }
            .sheet(item: $editing) { m in editSheet(m) }
            .sheet(isPresented: $showProjects) {
                ProjectsView { p in model.selectProject(p); showProjects = false }
                    .environmentObject(settings)
            }
            .onChange(of: projects.projects) { _, list in
                // Bearbeitetes/gelöschtes Projekt übernehmen
                if let id = model.project?.id { model.project = list.first { $0.id == id } }
            }
        }
    }

    private var chatModels: [String] {
        let list = settings.cachedModels.isEmpty ? AppSettings.defaultChatModels : settings.cachedModels
        return list.contains(settings.chatModel) ? list : [settings.chatModel] + list
    }

    private var emptyState: some View {
        ScrollView {
            VStack(spacing: 16) {
                Image(Theme.current.symbol ?? "Logo").resizable().scaledToFit().frame(width: 96, height: 96).clipShape(RoundedRectangle(cornerRadius: 22)).padding(.top, 30)
                Text("Wie kann ich helfen?").font(.title2.bold())
                // Projekt wählen (Anweisungen + Wissen für den neuen Chat)
                Menu {
                    Button { model.selectProject(nil) } label: {
                        if model.project == nil { Label("Ohne Projekt", systemImage: "checkmark") } else { Text("Ohne Projekt") }
                    }
                    ForEach(projects.projects) { p in
                        Button { model.selectProject(p) } label: { Label(p.name, systemImage: model.project?.id == p.id ? "checkmark" : "folder") }
                    }
                    Divider()
                    Button { showProjects = true } label: { Label(projects.projects.isEmpty ? "Projekt anlegen …" : "Projekte verwalten …", systemImage: "pencil") }
                } label: {
                    Label(model.project.map { "Projekt: \($0.name)" } ?? "Ohne Projekt", systemImage: "folder")
                        .font(.callout).lineLimit(1)
                        .padding(.horizontal, 12).padding(.vertical, 6)
                        .background(Theme.card, in: Capsule())
                }
                .accessibilityLabel("Projekt wählen, aktuell: \(model.project?.name ?? "ohne Projekt")")
                // Schnellwahl: angeheftete Unterhaltungen direkt fortsetzen (zuletzt genutzt zuerst)
                let pinned = store.conversations.filter { $0.pinned && !$0.messages.isEmpty }
                if !pinned.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Label("Angeheftet", systemImage: "pin.fill")
                            .font(.subheadline.weight(.semibold)).foregroundStyle(Theme.cyan)
                            .accessibilityAddTraits(.isHeader)
                        ForEach(pinned.prefix(6)) { c in
                            Button {
                                model.open(c)
                            } label: {
                                HStack(spacing: 8) {
                                    Image(systemName: "bubble.left.and.bubble.right").foregroundStyle(Theme.cyan)
                                    Text(c.title).lineLimit(1).foregroundStyle(Theme.text)
                                    Spacer(minLength: 0)
                                    Image(systemName: "chevron.right").font(.caption).foregroundStyle(Theme.secondaryText)
                                }
                            }
                            .card()
                            .accessibilityLabel("Angeheftete Unterhaltung öffnen: \(c.title)")
                        }
                        if pinned.count > 6 {
                            Text("Weitere \(pinned.count - 6) im Verlauf").font(.caption).foregroundStyle(Theme.secondaryText)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text("Vorschläge").font(.subheadline.weight(.semibold)).foregroundStyle(Theme.secondaryText)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .accessibilityAddTraits(.isHeader)
                }
                ForEach(["Erkläre mir Quantencomputer in einfachen Worten", "Schreibe eine freundliche Absage-E-Mail", "Plane mir eine Woche gesunde Ernährung", "Erstelle ein Bild von einem Leuchtturm bei Sonnenuntergang im Aquarell-Stil", "Was ist auf diesem Foto zu sehen?"], id: \.self) { s in
                    Button { model.input = s; focused = true } label: {
                        Text(s).frame(maxWidth: .infinity, alignment: .leading).foregroundStyle(Theme.text)
                    }
                    .card()
                }
            }
            .padding()
        }
    }

    private var messages: some View {
        ScrollViewReader { proxy in
            ScrollView {
                LazyVStack(spacing: 12) {
                    ForEach(model.conversation.messages) { m in
                        let isLast = m.id == model.conversation.messages.last?.id
                        MessageRow(
                            message: m,
                            busy: model.busy && isLast,
                            onRegenerate: !model.busy && isLast && m.role == "assistant" ? { model.regenerate(settings: settings) } : nil,
                            onEdit: !model.busy && m.role == "user" ? { editing = m; editText = ChatModel.editableText(of: m.text) } : nil
                        )
                            .background(flashID == m.id ? Theme.cyan.opacity(0.25) : Color.clear, in: RoundedRectangle(cornerRadius: 18))
                            .animation(.easeInOut(duration: 0.5), value: flashID)
                            .id(m.id)
                    }
                    Color.clear.frame(height: 1).id("bottom")
                }
                .padding()
            }
            .scrollDismissesKeyboard(.interactively)
            .onChange(of: model.conversation.messages.last?.text) { _, _ in
                guard jumpTo == nil else { return }
                proxy.scrollTo("bottom", anchor: .bottom)
            }
            .task(id: jumpTo) {
                // Gefundene Nachricht aus der Verlaufssuche anspringen und kurz hervorheben
                guard let target = jumpTo else { return }
                try? await Task.sleep(nanoseconds: 350_000_000)
                withAnimation { proxy.scrollTo(target, anchor: .top) }
                flashID = target
                jumpTo = nil
            }
            .task(id: flashID) {
                guard flashID != nil else { return }
                try? await Task.sleep(nanoseconds: 2_500_000_000)
                if !Task.isCancelled { flashID = nil }
            }
        }
    }

    private var inputBar: some View {
        VStack(spacing: 8) {
            if showAttach || !model.attachments.isEmpty {
                AttachmentBar(attachments: $model.attachments)
            }
            if dictation.listening {
                Text(dictation.transcript.isEmpty ? "Ich höre zu …" : dictation.transcript).font(.callout).foregroundStyle(Theme.cyan)
            }
            if model.webSearch || model.codeRun || model.thinking || model.imageGen {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 6) {
                        if model.webSearch { modeChip("Websuche", "globe") { model.webSearch = false } }
                        if model.codeRun { modeChip("Code ausführen", "function") { model.codeRun = false } }
                        if model.thinking { modeChip("Gründlich nachdenken", "brain") { model.thinking = false } }
                        if model.imageGen { modeChip("Bild erzeugen", "paintbrush") { model.imageGen = false } }
                    }
                }
            }
            if let e = dictation.error { Text(e).font(.caption).foregroundStyle(.orange) }
            HStack(alignment: .bottom, spacing: 10) {
                Button { withAnimation { showAttach.toggle() } } label: {
                    Image(systemName: showAttach ? "xmark.circle" : "plus.circle").font(.title2)
                }
                // Werkzeuge wie bei Claude: Websuche, Code ausführen, Gründlich nachdenken, Antwortstil
                Menu {
                    Toggle(isOn: Binding(get: { model.webSearch }, set: { on in
                        model.webSearch = on; if on { model.codeRun = false; model.imageGen = false }
                        model.notice = on ? "Websuche an – Antworten mit aktuellen Quellen" : "Websuche aus"
                    })) { Label("Websuche", systemImage: "globe") }
                    Toggle(isOn: Binding(get: { model.codeRun }, set: { on in
                        model.codeRun = on; if on { model.webSearch = false; model.imageGen = false }
                        model.notice = on ? "Code ausführen an – die KI rechnet mit echtem Python-Code (auch Diagramme)" : "Code ausführen aus"
                    })) { Label("Code ausführen", systemImage: "function") }
                    Toggle(isOn: Binding(get: { model.thinking }, set: { on in
                        model.thinking = on
                        model.notice = on ? "Gründlich nachdenken an – Antworten dauern etwas länger" : "Gründlich nachdenken aus"
                    })) { Label("Gründlich nachdenken", systemImage: "brain") }
                    Toggle(isOn: Binding(get: { model.imageGen }, set: { on in
                        model.imageGen = on; if on { model.webSearch = false; model.codeRun = false }
                        model.notice = on ? "Bild erzeugen an – beschreibe, was die KI malen soll" : "Bild erzeugen aus"
                    })) { Label("Bild erzeugen", systemImage: "paintbrush") }
                    Picker(selection: Binding(get: { settings.answerStyle }, set: { id in
                        settings.answerStyle = id
                        model.notice = "Antwortstil: \(AppSettings.answerStyles.first { $0.id == id }?.name ?? "Normal")"
                    })) {
                        ForEach(AppSettings.answerStyles, id: \.id) { Text($0.name).tag($0.id) }
                    } label: { Label("Antwortstil", systemImage: "textformat") }
                    .pickerStyle(.menu)
                } label: {
                    Image(systemName: "slider.horizontal.3").font(.title2)
                        .foregroundStyle(model.webSearch || model.codeRun || model.thinking || model.imageGen || settings.answerStyle != "normal" ? Theme.cyan : Theme.secondaryText)
                }
                .accessibilityLabel("Werkzeuge: Websuche, Code ausführen, Nachdenken, Bild erzeugen, Antwortstil")
                TextField(model.imageGen ? "Beschreibe dein Bild …" : model.webSearch ? "Frag mit Websuche …" : model.codeRun ? "Rechnen, auswerten, Diagramm …" : "Nachricht an KI-GenC …", text: $model.input, axis: .vertical)
                    .lineLimit(1...6)
                    .focused($focused)
                    .padding(.leading, 12).padding(.trailing, model.input.isEmpty ? 40 : 12).padding(.vertical, 9)
                    .background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 20))
                    .overlay(alignment: .trailing) {
                        // Kamera direkt im Eingabefeld (wie bei Messengern), solange noch nichts getippt ist
                        if model.input.isEmpty {
                            Button { session.externalPickerActive = true; showCamera = true } label: {
                                Image(systemName: "camera").font(.title3).foregroundStyle(Theme.cyan)
                            }
                            .padding(.trailing, 10)
                            .accessibilityLabel("Foto aufnehmen")
                        }
                    }
                Button {
                    dictation.toggle { text in model.input = (model.input.isEmpty ? "" : model.input + " ") + text }
                } label: {
                    Image(systemName: dictation.listening ? "mic.fill" : "mic").font(.title2)
                        .foregroundStyle(dictation.listening ? .red : Theme.cyan)
                }
                if model.busy {
                    Button { model.stop() } label: { Image(systemName: "stop.circle.fill").font(.title) }
                } else {
                    Button { focused = false; model.send(settings: settings) } label: {
                        Image(systemName: "arrow.up.circle.fill").font(.title)
                    }
                    .disabled(model.input.trimmingCharacters(in: .whitespaces).isEmpty && model.attachments.isEmpty)
                }
            }
        }
        .padding(.horizontal).padding(.vertical, 10)
        .background(Theme.navy.opacity(0.9))
        .disabled(!settings.hasKey)
    }

    /// Eigene Nachricht bearbeiten und ab dort neu senden (wie Android).
    private func editSheet(_ m: ChatMessage) -> some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 10) {
                TextEditor(text: $editText)
                    .scrollContentBackground(.hidden)
                    .padding(8)
                    .background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 14))
                    .accessibilityLabel("Text der Nachricht")
                Text("Die KI antwortet neu; spätere Nachrichten dieser Unterhaltung werden ersetzt.")
                    .font(.caption).foregroundStyle(Theme.secondaryText)
            }
            .padding()
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Nachricht bearbeiten")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Abbrechen") { editing = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Senden") {
                        model.editAndResend(id: m.id, newText: editText, settings: settings)
                        editing = nil
                    }
                    .disabled(editText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .presentationDetents([.medium, .large])
    }

    /// Aktiver Modus über dem Eingabefeld – Antippen schaltet ihn aus.
    private func modeChip(_ title: String, _ icon: String, off: @escaping () -> Void) -> some View {
        Button(action: off) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                Text(title)
                Image(systemName: "xmark")
            }
            .font(.caption).padding(.horizontal, 10).padding(.vertical, 5)
            .background(Theme.card, in: Capsule())
            .foregroundStyle(Theme.cyan)
        }
        .accessibilityLabel("\(title) ausschalten")
    }

    /// Volltextsuche über Titel und alle Nachrichten der gespeicherten Chats.
    private var filteredHistory: [Conversation] {
        let q = historyQuery.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !q.isEmpty else { return store.conversations }
        return store.conversations.filter { c in
            c.title.localizedCaseInsensitiveContains(q) || c.messages.contains { $0.text.localizedCaseInsensitiveContains(q) }
        }
    }

    private var history: some View {
        NavigationStack {
            List {
                let list = filteredHistory
                let q = historyQuery.trimmingCharacters(in: .whitespacesAndNewlines)
                if store.conversations.isEmpty {
                    Text("Noch keine gespeicherten Chats.").foregroundStyle(Theme.secondaryText)
                } else if list.isEmpty {
                    Text("Nichts gefunden.").foregroundStyle(Theme.secondaryText)
                } else if !q.isEmpty {
                    Text(list.count == 1 ? "1 Unterhaltung gefunden" : "\(list.count) Unterhaltungen gefunden")
                        .font(.caption).foregroundStyle(Theme.secondaryText)
                        .listRowBackground(Color.clear)
                }
                // Nach Zeitraum gruppiert (Liste ist bereits nach letzter Änderung absteigend sortiert)
                ForEach(Self.grouped(list)) { group in
                    Section {
                        ForEach(group.items) { c in
                            Button {
                                model.stop()
                                // Bei aktiver Suche direkt zur ersten Fundstelle springen
                                jumpTo = q.isEmpty ? nil : c.messages.first(where: { $0.text.localizedCaseInsensitiveContains(q) })?.id
                                model.open(c)
                                showHistory = false
                            } label: {
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 4) {
                                        if c.pinned {
                                            Image(systemName: "pin.fill").font(.caption).foregroundStyle(Theme.cyan)
                                                .accessibilityLabel("Angeheftet")
                                        }
                                        Text(c.title).lineLimit(1).foregroundStyle(Theme.text)
                                    }
                                    Text((projects.byID(c.projectID).map { "Projekt „\($0.name)“ · " } ?? "") + c.updated.formatted(date: .abbreviated, time: .shortened))
                                        .font(.caption).foregroundStyle(Theme.secondaryText)
                                    if let hit = Self.snippet(in: c, query: q) {
                                        Text(hit).font(.caption).foregroundStyle(Theme.secondaryText).lineLimit(2)
                                    }
                                }
                            }
                            .listRowBackground(Theme.card)
                            .swipeActions(edge: .leading) {
                                Button {
                                    store.setPinned(c.id, !c.pinned)
                                } label: {
                                    Label(c.pinned ? "Lösen" : "Anheften", systemImage: c.pinned ? "pin.slash" : "pin")
                                }
                                .tint(Theme.cyan)
                            }
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) { store.delete(c.id) } label: {
                                    Label("Löschen", systemImage: "trash")
                                }
                            }
                            .contextMenu {
                                Button {
                                    store.setPinned(c.id, !c.pinned)
                                } label: {
                                    Label(c.pinned ? "Lösen (nicht mehr angeheftet)" : "Anheften", systemImage: c.pinned ? "pin.slash" : "pin")
                                }
                                Button(role: .destructive) { store.delete(c.id) } label: {
                                    Label("Löschen", systemImage: "trash")
                                }
                            }
                        }
                    } header: {
                        Text(group.title).accessibilityAddTraits(.isHeader)
                    }
                }
            }
            .clearListBackground()
            .searchable(text: $historyQuery, placement: .navigationBarDrawer(displayMode: .always), prompt: "Verlauf durchsuchen …")
            .navigationTitle("Verlauf")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Fertig") { showHistory = false } }
                ToolbarItem(placement: .destructiveAction) { Button("Alle löschen", role: .destructive) { store.clear() }.disabled(store.conversations.isEmpty) }
            }
        }
        .preferredColorScheme(Theme.current.dark ? .dark : .light)
    }

    /// Zeitraum-Überschrift: Heute, Gestern, Letzte 7 Tage, Letzte 30 Tage, Älter (Kalendertage).
    static func period(of date: Date, now: Date = Date()) -> String {
        let cal = Calendar.current
        let days = cal.dateComponents([.day], from: cal.startOfDay(for: date), to: cal.startOfDay(for: now)).day ?? 0
        switch days {
        case ...0: return "Heute"
        case 1: return "Gestern"
        case 2..<7: return "Letzte 7 Tage"
        case 7..<30: return "Letzte 30 Tage"
        default: return "Älter"
        }
    }

    /// Angeheftete zuerst („Angeheftet“), dann Zeitraum-Gruppen; Reihenfolge der Liste bleibt erhalten.
    static func grouped(_ list: [Conversation]) -> [HistoryGroup] {
        let now = Date()
        var out: [HistoryGroup] = []
        let pinned = list.filter(\.pinned)
        if !pinned.isEmpty { out.append(HistoryGroup(title: "Angeheftet", items: pinned)) }
        for c in list where !c.pinned {
            let t = period(of: c.updated, now: now)
            if let i = out.firstIndex(where: { $0.title == t }) { out[i].items.append(c) } else { out.append(HistoryGroup(title: t, items: [c])) }
        }
        return out
    }

    /// Ausschnitt rund um den ersten Treffer in den Nachrichten, Suchbegriff hervorgehoben.
    static func snippet(in c: Conversation, query: String) -> AttributedString? {
        guard !query.isEmpty,
              let msg = c.messages.first(where: { $0.text.localizedCaseInsensitiveContains(query) }) else { return nil }
        let text = msg.text.split(whereSeparator: { $0.isWhitespace || $0.isNewline }).joined(separator: " ")
        guard let r = text.range(of: query, options: [.caseInsensitive, .diacriticInsensitive]) else { return nil }
        let start = text.index(r.lowerBound, offsetBy: -40, limitedBy: text.startIndex) ?? text.startIndex
        let end = text.index(r.upperBound, offsetBy: 80, limitedBy: text.endIndex) ?? text.endIndex
        var hits = 0
        for m in c.messages {
            var from = m.text.startIndex
            while let f = m.text.range(of: query, options: [.caseInsensitive, .diacriticInsensitive], range: from..<m.text.endIndex) {
                hits += 1
                from = f.upperBound
            }
        }
        var out = AttributedString((msg.role == "user" ? "Du: " : "KI: ") + (start > text.startIndex ? "…" : ""))
        out += AttributedString(String(text[start..<r.lowerBound]))
        var match = AttributedString(String(text[r]))
        match.font = .caption.bold()
        match.foregroundColor = Theme.cyan
        out += match
        out += AttributedString(String(text[r.upperBound..<end]) + (end < text.endIndex ? "…" : ""))
        if hits > 1 { out += AttributedString("  (\(hits) Treffer)") }
        return out
    }
}

/// Abschnitt im Chat-Verlauf (z. B. „Heute“) mit seinen Unterhaltungen.
struct HistoryGroup: Identifiable {
    let title: String
    var items: [Conversation]
    var id: String { title }
}

struct MessageRow: View {
    let message: ChatMessage
    var busy = false
    /// „Neu erzeugen“ – nur bei der letzten Antwort, wenn nichts läuft.
    var onRegenerate: (() -> Void)? = nil
    /// „Bearbeiten“ – nur bei eigenen Nachrichten, wenn nichts läuft.
    var onEdit: (() -> Void)? = nil

    var body: some View {
        VStack(alignment: .trailing, spacing: 4) {
            HStack {
                if message.role == "user" { Spacer(minLength: 40) }
                VStack(alignment: .leading, spacing: 8) {
                    if !message.images.isEmpty && message.role == "assistant" {
                        // Von der KI erzeugte Bilder (Bild erzeugen, Diagramme) groß anzeigen
                        ForEach(Array(message.images.enumerated()), id: \.offset) { i, url in
                            if let img = Self.image(from: url) {
                                Image(uiImage: img).resizable().scaledToFit()
                                    .frame(maxWidth: 420)
                                    .clipShape(RoundedRectangle(cornerRadius: 12))
                                    .accessibilityLabel("Bild \(i + 1) – lange drücken zum Teilen oder Sichern")
                                    .contextMenu {
                                        ShareLink(item: Image(uiImage: img), preview: SharePreview("KI-GenC Bild", image: Image(uiImage: img))) {
                                            Label("Teilen", systemImage: "square.and.arrow.up")
                                        }
                                        Button { UIImageWriteToSavedPhotosAlbum(img, nil, nil, nil) } label: {
                                            Label("In Fotos sichern", systemImage: "square.and.arrow.down")
                                        }
                                    }
                            }
                        }
                    } else if !message.images.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack {
                                ForEach(Array(message.images.enumerated()), id: \.offset) { _, url in
                                    if let img = Self.image(from: url) {
                                        Image(uiImage: img).resizable().scaledToFill().frame(width: 90, height: 90).clipShape(RoundedRectangle(cornerRadius: 10))
                                    }
                                }
                            }
                        }
                    }
                    if message.role == "assistant" {
                        if message.text.isEmpty && busy {
                            HStack(spacing: 8) { ProgressView(); Text("denkt nach …").foregroundStyle(Theme.secondaryText) }
                        } else if message.isError {
                            ErrorCard(message: message.text)
                            if let onRegenerate {
                                Button(action: onRegenerate) { Label("Erneut versuchen", systemImage: "arrow.clockwise") }
                                    .font(.callout).foregroundStyle(Theme.cyan)
                            }
                        } else {
                            MarkdownView(text: message.text)
                            if !busy {
                                HStack(spacing: 18) {
                                    ResultActions(text: message.text, kind: "Chat")
                                    if let onRegenerate {
                                        Button(action: onRegenerate) { Image(systemName: "arrow.clockwise") }
                                            .font(.callout).foregroundStyle(Theme.cyan)
                                            .accessibilityLabel("Antwort neu erzeugen")
                                    }
                                }
                            }
                        }
                    } else {
                        Text(message.text).textSelection(.enabled)
                    }
                }
                .padding(12)
                .background(message.role == "user" ? Theme.userBubble : Theme.card, in: RoundedRectangle(cornerRadius: 16))
                if message.role == "assistant" { Spacer(minLength: 0) }
            }
            if message.role == "user", let onEdit {
                HStack {
                    Spacer()
                    Button(action: onEdit) { Label("Bearbeiten", systemImage: "pencil") }
                        .font(.caption).foregroundStyle(Theme.cyan)
                        .accessibilityLabel("Eigene Nachricht bearbeiten und neu senden")
                }
            }
        }
    }

    static func image(from dataURL: String) -> UIImage? {
        guard let comma = dataURL.firstIndex(of: ","), let d = Data(base64Encoded: String(dataURL[dataURL.index(after: comma)...])) else { return nil }
        return UIImage(data: d)
    }
}
