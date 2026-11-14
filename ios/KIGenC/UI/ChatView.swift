import SwiftUI

@MainActor
final class ChatModel: ObservableObject {
    @Published var conversation = Conversation(title: "Neuer Chat", messages: [])
    @Published var input = ""
    @Published var attachments: [Attachment] = []
    @Published var busy = false
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
        conversation.messages.append(ChatMessage(role: "user", text: shown + (extra.isEmpty ? "" : "\n\n📎 " + attachments.map(\.name).joined(separator: ", ")), images: images))
        if conversation.messages.count == 1 { conversation.title = String(shown.prefix(50)) }
        var apiMessages = [ApiMessage(role: "system", text: Self.system)]
        let history = conversation.messages.dropLast().suffix(20)
        for m in history where !m.isError { apiMessages.append(ApiMessage(role: m.role, text: m.text, images: m.role == "user" ? m.images : [])) }
        apiMessages.append(ApiMessage(role: "user", text: shown + extra, images: images))
        input = ""
        attachments = []
        conversation.messages.append(ChatMessage(role: "assistant", text: ""))
        let index = conversation.messages.count - 1
        busy = true
        let model = images.isEmpty ? settings.chatModel : (settings.chatModel.contains("ministral") || settings.chatModel.contains("magistral") ? "mistral-small-latest" : settings.chatModel)
        task = Task {
            do {
                for try await chunk in MistralClient.shared.stream(key: settings.key, model: model, messages: apiMessages) {
                    conversation.messages[index].text += chunk
                }
                if conversation.messages[index].text.isEmpty { conversation.messages[index].text = "(keine Antwort)" }
                if settings.autoSpeak { Speaker.shared.speak(conversation.messages[index].text) }
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

    func stop() {
        task?.cancel()
        busy = false
    }

    func newChat() {
        stop()
        if !conversation.messages.isEmpty { ConversationStore.shared.upsert(conversation) }
        conversation = Conversation(title: "Neuer Chat", messages: [])
        input = ""
        attachments = []
    }
}

struct ChatView: View {
    @EnvironmentObject private var settings: AppSettings
    @StateObject private var model = ChatModel()
    @StateObject private var dictation = Dictation()
    @ObservedObject private var store = ConversationStore.shared
    @State private var showHistory = false
    @State private var historyQuery = ""
    /// Sprung aus der Verlaufssuche: Ziel-Nachricht und kurz hervorgehobene Nachricht.
    @State private var jumpTo: UUID?
    @State private var flashID: UUID?
    @State private var showAttach = false
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
                inputBar
            }
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle(model.conversation.messages.isEmpty ? "KI-GenC" : model.conversation.title)
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
                    Button { model.newChat() } label: { Image(systemName: "square.and.pencil") }
                }
            }
            .sheet(isPresented: $showHistory) { history }
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
                ForEach(["Erkläre mir Quantencomputer in einfachen Worten", "Schreibe eine freundliche Absage-E-Mail", "Plane mir eine Woche gesunde Ernährung", "Was ist auf diesem Foto zu sehen?"], id: \.self) { s in
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
                        MessageRow(message: m, busy: model.busy && m.id == model.conversation.messages.last?.id)
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
            if let e = dictation.error { Text(e).font(.caption).foregroundStyle(.orange) }
            HStack(alignment: .bottom, spacing: 10) {
                Button { withAnimation { showAttach.toggle() } } label: {
                    Image(systemName: showAttach ? "xmark.circle" : "plus.circle").font(.title2)
                }
                TextField("Nachricht an KI-GenC …", text: $model.input, axis: .vertical)
                    .lineLimit(1...6)
                    .focused($focused)
                    .padding(.horizontal, 12).padding(.vertical, 9)
                    .background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 20))
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
                ForEach(list) { c in
                    Button {
                        model.stop()
                        // Bei aktiver Suche direkt zur ersten Fundstelle springen
                        jumpTo = q.isEmpty ? nil : c.messages.first(where: { $0.text.localizedCaseInsensitiveContains(q) })?.id
                        model.conversation = c
                        showHistory = false
                    } label: {
                        VStack(alignment: .leading, spacing: 3) {
                            Text(c.title).lineLimit(1).foregroundStyle(Theme.text)
                            Text(c.updated.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(Theme.secondaryText)
                            if let hit = Self.snippet(in: c, query: q) {
                                Text(hit).font(.caption).foregroundStyle(Theme.secondaryText).lineLimit(2)
                            }
                        }
                    }
                    .listRowBackground(Theme.card)
                }
                .onDelete { idx in idx.map { list[$0].id }.forEach(store.delete) }
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

struct MessageRow: View {
    let message: ChatMessage
    var busy = false

    var body: some View {
        HStack {
            if message.role == "user" { Spacer(minLength: 40) }
            VStack(alignment: .leading, spacing: 8) {
                if !message.images.isEmpty {
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
                    } else {
                        MarkdownView(text: message.text)
                        if !busy { ResultActions(text: message.text, kind: "Chat") }
                    }
                } else {
                    Text(message.text).textSelection(.enabled)
                }
            }
            .padding(12)
            .background(message.role == "user" ? Theme.userBubble : Theme.card, in: RoundedRectangle(cornerRadius: 16))
            if message.role == "assistant" { Spacer(minLength: 0) }
        }
    }

    static func image(from dataURL: String) -> UIImage? {
        guard let comma = dataURL.firstIndex(of: ","), let d = Data(base64Encoded: String(dataURL[dataURL.index(after: comma)...])) else { return nil }
        return UIImage(data: d)
    }
}
