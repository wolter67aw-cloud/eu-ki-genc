import SwiftUI

// MARK: Kategorien

struct OptionGroup {
    let key: String
    let label: String
    let options: [String]
    var defaultValue: String? = nil
}

struct ExtraField {
    let key: String
    let label: String
    let hint: String
}

enum StartMode { case text, research, image, tool, code }

enum StartTool { case photo, transcribe, ideas, data }

struct StartCategory: Identifiable, Equatable {
    let id: String
    let emoji: String
    let title: String
    let subtitle: String
    let mode: StartMode
    var contentLabel = ""
    var contentHint = ""
    var groups: [OptionGroup] = []
    var extras: [ExtraField] = []
    var instruction = ""
    var tool: StartTool? = nil

    static func == (a: StartCategory, b: StartCategory) -> Bool { a.id == b.id }
}

private let lengthGroup = OptionGroup(key: "length", label: "Länge", options: ["Kurz", "Mittel", "Ausführlich"], defaultValue: "Mittel")
private let languageGroup = OptionGroup(key: "language", label: "Sprache", options: ["Deutsch", "Englisch", "Französisch", "Spanisch", "Italienisch", "Niederländisch", "Polnisch", "Türkisch"], defaultValue: "Deutsch")

let startCategories: [StartCategory] = [
    StartCategory(id: "write", emoji: "✍️", title: "Schreiben", subtitle: "E-Mail, Brief, Bericht, Post …", mode: .text,
                  contentLabel: "Worum geht es?", contentHint: "z. B. Absage für den Termin am Freitag, neuen Termin vorschlagen",
                  groups: [
                    OptionGroup(key: "type", label: "Art", options: ["E-Mail", "Brief", "Bericht", "Social-Media-Post", "Bewerbung", "Geschichte", "Rede", "Produkttext", "Einladung", "Gedicht"], defaultValue: "E-Mail"),
                    OptionGroup(key: "tone", label: "Ton", options: ["Neutral", "Freundlich", "Förmlich", "Locker", "Überzeugend", "Humorvoll", "Emotional"], defaultValue: "Freundlich"),
                    lengthGroup, languageGroup,
                  ],
                  extras: [ExtraField(key: "audience", label: "Empfänger / Zielgruppe", hint: "z. B. mein Vermieter, Kunden"),
                           ExtraField(key: "must", label: "Muss enthalten", hint: "z. B. Datum, Frist, Ansprechpartner")],
                  instruction: "Schreibe den gewünschten Text fertig zum Verwenden. Bei E-Mails mit Betreffzeile, bei Briefen nach DIN 5008."),
    StartCategory(id: "translate", emoji: "🌍", title: "Übersetzen", subtitle: "In 20+ Sprachen", mode: .text,
                  contentLabel: "Text", contentHint: "Text, der übersetzt werden soll",
                  groups: [
                    OptionGroup(key: "target", label: "Zielsprache", options: ["Englisch", "Französisch", "Spanisch", "Italienisch", "Niederländisch", "Polnisch", "Portugiesisch", "Türkisch", "Ukrainisch", "Griechisch", "Tschechisch", "Deutsch"], defaultValue: "Englisch"),
                    OptionGroup(key: "style", label: "Stil", options: ["Natürlich", "Wörtlich", "Förmlich", "Locker"], defaultValue: "Natürlich"),
                  ],
                  instruction: "Übersetze den Text. Gib nur die Übersetzung aus, danach (falls sinnvoll) kurze Hinweise zu Begriffen."),
    StartCategory(id: "analyze", emoji: "🔎", title: "Analysieren", subtitle: "Text, Vertrag, Argumente", mode: .text,
                  contentLabel: "Inhalt", contentHint: "Text hier einfügen",
                  groups: [
                    OptionGroup(key: "what", label: "Was ist es?", options: ["Text", "Vertrag / Dokument", "E-Mail", "Argumentation", "Zahlen / Daten", "Angebot"], defaultValue: "Text"),
                    OptionGroup(key: "focus", label: "Fokus", options: ["Zusammenfassung", "Kernaussagen", "Stärken & Schwächen", "Risiken & Fallstricke", "Faktencheck", "Antwortvorschlag"], defaultValue: "Zusammenfassung"),
                    OptionGroup(key: "format", label: "Format", options: ["Stichpunkte", "Fließtext", "Tabelle"], defaultValue: "Stichpunkte"),
                  ],
                  extras: [ExtraField(key: "question", label: "Konkrete Frage (optional)", hint: "z. B. Kann ich vorzeitig kündigen?")],
                  instruction: "Analysiere den Inhalt gründlich und gezielt nach dem gewählten Fokus."),
    StartCategory(id: "research", emoji: "🌐", title: "Recherchieren", subtitle: "Aktuell im Web", mode: .research,
                  contentLabel: "Frage", contentHint: "z. B. Welche E-Autos unter 30.000 € gibt es aktuell?",
                  groups: [
                    OptionGroup(key: "depth", label: "Tiefe", options: ["Kurzüberblick", "Gründlich"], defaultValue: "Gründlich"),
                    OptionGroup(key: "format", label: "Format", options: ["Stichpunkte", "Fließtext", "Vergleichstabelle"], defaultValue: "Stichpunkte"),
                  ]),
    StartCategory(id: "plan", emoji: "🗓️", title: "Planen", subtitle: "Projekt, Reise, Event", mode: .text,
                  contentLabel: "Was soll geplant werden?", contentHint: "z. B. Geburtstagsfeier für 20 Personen im Garten",
                  groups: [
                    OptionGroup(key: "type", label: "Art", options: ["Projekt", "Reise", "Event / Feier", "Wochenplan", "Lernplan", "Trainingsplan", "Umzug", "Essensplan"], defaultValue: "Projekt"),
                    OptionGroup(key: "period", label: "Zeitraum", options: ["1 Tag", "1 Woche", "1 Monat", "3 Monate", "Offen"], defaultValue: "1 Woche"),
                    OptionGroup(key: "format", label: "Format", options: ["Checkliste", "Zeitplan", "Tabelle"], defaultValue: "Checkliste"),
                  ],
                  extras: [ExtraField(key: "constraints", label: "Rahmenbedingungen", hint: "z. B. Budget 300 €, 2 Kinder, ab 14 Uhr")],
                  instruction: "Erstelle einen konkreten, realistischen Plan mit Schritten, Zeiten und Hinweisen."),
    StartCategory(id: "learn", emoji: "🎓", title: "Lernen", subtitle: "Erklären, Quiz, Karteikarten", mode: .text,
                  contentLabel: "Thema", contentHint: "z. B. Photosynthese, Bruchrechnen",
                  groups: [
                    OptionGroup(key: "level", label: "Niveau", options: ["Kind (10 J.)", "Einsteiger", "Fortgeschritten", "Experte"], defaultValue: "Einsteiger"),
                    OptionGroup(key: "form", label: "Form", options: ["Erklärung", "Zusammenfassung", "Quiz mit Lösungen", "Karteikarten", "Übungsaufgaben", "Eselsbrücken"], defaultValue: "Erklärung"),
                    lengthGroup,
                  ],
                  instruction: "Erkläre bzw. bereite den Lernstoff didaktisch gut auf, mit Beispielen."),
    StartCategory(id: "code", emoji: "💻", title: "Programmieren", subtitle: "Code schreiben & erklären", mode: .code,
                  contentLabel: "Aufgabe", contentHint: "z. B. Skript, das Fotos nach Datum umbenennt",
                  groups: [
                    OptionGroup(key: "lang", label: "Sprache", options: ["Python", "JavaScript", "Kotlin", "Swift", "Java", "C#", "SQL", "Bash", "HTML/CSS", "Excel-Formel"], defaultValue: "Python"),
                    OptionGroup(key: "task", label: "Aufgabe", options: ["Neu schreiben", "Erklären", "Fehler finden", "Verbessern", "Tests schreiben"], defaultValue: "Neu schreiben"),
                  ],
                  instruction: "Liefere vollständigen, lauffähigen Code in Markdown-Codeblöcken mit kurzer Erklärung."),
    StartCategory(id: "image", emoji: "🎨", title: "Bild erstellen", subtitle: "Aus einer Beschreibung", mode: .image,
                  contentLabel: "Was soll zu sehen sein?", contentHint: "z. B. Leuchtturm an der Nordsee bei Sonnenuntergang",
                  groups: [
                    OptionGroup(key: "style", label: "Stil", options: ["Fotorealistisch", "Illustration", "Aquarell", "3D-Render", "Comic", "Ölgemälde", "Minimalistisch", "Anime"], defaultValue: "Fotorealistisch"),
                    OptionGroup(key: "format", label: "Format", options: ["Quadrat", "Hochformat", "Querformat"], defaultValue: "Quadrat"),
                    OptionGroup(key: "mood", label: "Stimmung", options: ["Hell & freundlich", "Dramatisch", "Mystisch", "Warm", "Kühl", "Verträumt"], defaultValue: "Hell & freundlich"),
                  ]),
    StartCategory(id: "photo", emoji: "🖼️", title: "Foto bearbeiten", subtitle: "Filter & KI", mode: .tool, tool: .photo),
    StartCategory(id: "transcribe", emoji: "🎙️", title: "Transkribieren", subtitle: "Audio zu Text", mode: .tool, tool: .transcribe),
    StartCategory(id: "ideas", emoji: "💡", title: "Idee festhalten", subtitle: "Moment, Memo, Skizze", mode: .tool, tool: .ideas),
    StartCategory(id: "data", emoji: "📊", title: "Daten auswerten", subtitle: "Tabellen & Statistik", mode: .tool, tool: .data),
]

// MARK: Zuletzt genutzt

/// Ein zuletzt ausgeführter Auftrag der Startseite (zum schnellen Wiederverwenden).
struct RecentStart: Codable, Identifiable, Equatable {
    var id = UUID()
    let categoryId: String
    let options: [String: String]
    let content: String
    let extras: [String: String]
    let customPrompt: String?
    var time = Date()
    /// Angeheftet (Favorit): bleibt oben und wird weder verdrängt noch von „Leeren“ entfernt.
    /// Optional, damit ältere gespeicherte Einträge ohne dieses Feld weiter lesbar sind.
    var pinned: Bool? = nil
    /// Eigener Name (z. B. für Favoriten); nil = erste Zeile des Inhalts wird angezeigt.
    /// Optional, damit ältere gespeicherte Einträge ohne dieses Feld weiter lesbar sind.
    var name: String? = nil

    var isPinned: Bool { pinned == true }

    func sameAs(_ o: RecentStart) -> Bool {
        categoryId == o.categoryId && options == o.options && extras == o.extras && customPrompt == o.customPrompt &&
            content.trimmingCharacters(in: .whitespacesAndNewlines) == o.content.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

/// Speichert die letzten Aufträge (höchstens 6) in einem eigenen, neuen UserDefaults-Eintrag.
enum RecentStartStore {
    private static let key = "recentStarts"
    static let max = 6
    static let maxName = 60

    static func load() -> [RecentStart] {
        guard let d = UserDefaults.standard.data(forKey: key) else { return [] }
        return (try? JSONDecoder().decode([RecentStart].self, from: d)) ?? []
    }

    static func save(_ list: [RecentStart]) {
        if let d = try? JSONEncoder().encode(list) { UserDefaults.standard.set(d, forKey: key) }
    }
}

// MARK: Modell

@MainActor
final class StartModel: ObservableObject {
    @Published var wish = ""
    @Published var defining = false
    @Published var note = ""
    @Published var category: StartCategory?
    @Published var options: [String: String] = [:]
    @Published var content = ""
    @Published var extras: [String: String] = [:]
    @Published var customPrompt: String?
    @Published var result = ""
    @Published var image: UIImage?
    @Published var imageInfo = ""
    @Published var sources: [(String, String)] = []
    @Published var busy = false
    @Published var status = ""
    @Published var error: String?
    @Published var recent: [RecentStart] = RecentStartStore.load()
    private var task: Task<Void, Never>?

    /// Übernimmt einen früheren Auftrag: Kategorie, Auswahl und Felder werden wiederhergestellt.
    func useRecent(_ r: RecentStart) {
        guard let c = startCategories.first(where: { $0.id == r.categoryId }) else { return }
        choose(c)
        for g in c.groups { if let v = r.options[g.key], g.options.contains(v) { options[g.key] = v } }
        for f in c.extras { if let v = r.extras[f.key], !v.isEmpty { extras[f.key] = v } }
        content = r.content
        customPrompt = r.customPrompt
        note = ""
    }

    func removeRecent(_ r: RecentStart) {
        recent.removeAll { $0.sameAs(r) }
        RecentStartStore.save(recent)
    }

    func clearRecent() {
        recent = recent.filter { $0.isPinned }
        RecentStartStore.save(recent)
    }

    /// Heftet einen Auftrag an bzw. löst ihn wieder (dann wird er wieder normal einsortiert).
    func togglePinRecent(_ r: RecentStart) {
        guard var current = recent.first(where: { $0.sameAs(r) }) else { return }
        let rest = recent.filter { !$0.sameAs(r) }
        let pinnedOnes = rest.filter { $0.isPinned }
        let others = rest.filter { !$0.isPinned }
        if current.isPinned {
            current.pinned = nil
            recent = pinnedOnes + Array(([current] + others).sorted { $0.time > $1.time }.prefix(RecentStartStore.max))
        } else {
            current.pinned = true
            recent = pinnedOnes + [current] + others
        }
        RecentStartStore.save(recent)
    }

    /// Gibt einem Auftrag einen eigenen Namen; ein leerer Name stellt die automatische Anzeige wieder her.
    func renameRecent(_ r: RecentStart, to name: String) {
        let clean = String(name.trimmingCharacters(in: .whitespacesAndNewlines).prefix(RecentStartStore.maxName))
        for i in recent.indices where recent[i].sameAs(r) { recent[i].name = clean.isEmpty ? nil : clean }
        RecentStartStore.save(recent)
    }

    private func rememberRun(_ c: StartCategory) {
        guard c.mode != .tool else { return }
        var entry = RecentStart(categoryId: c.id, options: options, content: content, extras: extras.filter { !$0.value.isEmpty }, customPrompt: customPrompt)
        if recent.contains(where: { $0.isPinned && $0.sameAs(entry) }) { return }
        entry.name = recent.first(where: { $0.sameAs(entry) })?.name
        let pinnedOnes = recent.filter { $0.isPinned }
        let others = recent.filter { !$0.isPinned && !$0.sameAs(entry) }
        recent = pinnedOnes + Array(([entry] + others).prefix(RecentStartStore.max))
        RecentStartStore.save(recent)
    }

    func choose(_ c: StartCategory?) {
        category = c
        var o: [String: String] = [:]
        for g in c?.groups ?? [] { if let d = g.defaultValue { o[g.key] = d } }
        options = o
        extras = [:]
        customPrompt = nil
        result = ""; image = nil; sources = []; error = nil
        if c != nil && content.isEmpty { content = wish }
    }

    func reset() { choose(nil); wish = ""; content = ""; note = "" }

    var builtPrompt: String {
        if let customPrompt { return customPrompt }
        guard let c = category else { return wish }
        let o = options
        var s: String
        switch c.id {
        case "write": s = "Schreibe: \(o["type"] ?? "Text")"
        case "translate": s = "Übersetze nach \(o["target"] ?? "Englisch") (Stil: \(o["style"] ?? "natürlich"))"
        case "analyze": s = "Analysiere (\(o["what"] ?? "Text")) mit Fokus: \(o["focus"] ?? "Zusammenfassung")"
        case "research": s = "Recherchiere (\(o["depth"] ?? "gründlich"))"
        case "plan": s = "Plane: \(o["type"] ?? "Projekt")"
        case "learn": s = "Lernhilfe als \(o["form"] ?? "Erklärung")"
        case "code": s = "\(o["task"] ?? "Neu schreiben") – Programmiersprache \(o["lang"] ?? "Python")"
        case "image": s = "Bild"
        default: s = c.title
        }
        s += "\n\n\(c.contentLabel.isEmpty ? "Inhalt" : c.contentLabel): \(content.trimmingCharacters(in: .whitespacesAndNewlines))"
        let skip: Set<String> = ["type", "target", "focus", "what", "form", "task", "depth"]
        for g in c.groups where !skip.contains(g.key) { if let v = o[g.key] { s += "\n\(g.label): \(v)" } }
        for f in c.extras { if let v = extras[f.key], !v.isEmpty { s += "\n\(f.label): \(v)" } }
        return s
    }

    func define(settings: AppSettings) {
        let text = wish.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !text.isEmpty, !defining, settings.hasKey else { if !settings.hasKey { error = "Bitte zuerst den Mistral-Schlüssel unter „Optionen“ eintragen." }; return }
        defining = true
        note = ""
        Task {
            defer { defining = false }
            let catalog = startCategories.map { c in
                "- \(c.id) (\(c.title))" + c.groups.map { "; \($0.key): \($0.options.joined(separator: "|"))" }.joined() + c.extras.map { "; Feld \($0.key): \($0.label)" }.joined()
            }.joined(separator: "\n")
            let prompt = "Ein Nutzer beschreibt, was er mit einer KI-App machen möchte. Ordne den Wunsch der passendsten Kategorie zu und fülle die Auswahl so genau wie möglich aus (nur Werte aus der Liste, sonst weglassen).\n\nKategorien:\n\(catalog)\n\nAntworte nur als JSON: {\"category\":\"id\",\"options\":{\"key\":\"Wert\"},\"content\":\"der eigentliche Inhalt bzw. das Thema, präzisiert\",\"fields\":{\"feldkey\":\"Wert\"},\"note\":\"ein Satz auf Deutsch: was du verstanden hast und was der Nutzer ggf. noch ergänzen sollte\"}\n\nWunsch: \(text)"
            do {
                let json = try await MistralClient.shared.complete(key: settings.key, model: MistralClient.fallbackModel, messages: [ApiMessage(role: "user", text: prompt)], json: true, temperature: 0.1)
                guard let d = json.data(using: .utf8), let o = try JSONSerialization.jsonObject(with: d) as? [String: Any] else { throw APIError(code: 0, message: "Ungültige Antwort") }
                let c = startCategories.first { $0.id == (o["category"] as? String) } ?? startCategories[0]
                choose(c)
                let opts = o["options"] as? [String: Any] ?? [:]
                for g in c.groups {
                    if let v = opts[g.key] as? String, let m = g.options.first(where: { $0.caseInsensitiveCompare(v) == .orderedSame }) { options[g.key] = m }
                }
                content = (o["content"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? text
                let fields = o["fields"] as? [String: Any] ?? [:]
                for f in c.extras { if let v = fields[f.key] as? String, !v.isEmpty { extras[f.key] = v } }
                note = o["note"] as? String ?? ""
            } catch {
                ErrorLog.report(error, context: "Startseite")
                choose(startCategories[0])
                note = "Die KI konnte den Wunsch nicht automatisch zuordnen – wähle die Auswahl bitte selbst."
            }
        }
    }

    func run(settings: AppSettings) {
        guard let c = category, !busy else { return }
        guard settings.hasKey || c.mode == .image else { error = "Bitte zuerst den Mistral-Schlüssel unter „Optionen“ eintragen."; return }
        guard !content.trimmingCharacters(in: .whitespaces).isEmpty || customPrompt != nil else { error = "Bitte zuerst „\(c.contentLabel)“ ausfüllen."; return }
        let prompt = builtPrompt
        rememberRun(c)
        result = ""; image = nil; sources = []; error = nil
        busy = true
        task = Task {
            defer { busy = false; status = "" }
            do {
                switch c.mode {
                case .text, .code:
                    let system = "Du bist KI-GenC, ein präziser Assistent. \(c.instruction) Halte dich genau an alle Vorgaben (Art, Ton, Länge, Sprache, Format, Zielgruppe). Antworte in Markdown."
                    for try await chunk in MistralClient.shared.stream(key: settings.key, model: c.mode == .code ? settings.codeModel : settings.chatModel,
                        messages: [ApiMessage(role: "system", text: system), ApiMessage(role: "user", text: prompt)], temperature: c.id == "write" ? 0.7 : 0.3) {
                        result += chunk
                    }
                    ResultStore.shared.add(kind: "Start: \(c.title)", title: content, text: result)
                case .research:
                    status = "Recherchiere im Web …"
                    let (text, src) = try await MistralClient.shared.webSearch(key: settings.key, model: "mistral-medium-latest", question: prompt)
                    result = text
                    sources = src
                    ResultStore.shared.add(kind: "Start: Recherche", title: content, text: text + src.map { "\n- [\($0.0)](\($0.1))" }.joined())
                case .image:
                    status = "Bild wird erzeugt … ca. 10–40 Sekunden"
                    let aspect: Double = options["format"] == "Hochformat" ? 0.6 : (options["format"] == "Querformat" ? 1.7 : 1)
                    let desc = customPrompt ?? "\(content), Stil: \(options["style"] ?? ""), Stimmung: \(options["mood"] ?? "")"
                    let (data, provider) = try await ImageService.generate(prompt: desc, aspect: aspect, settings: settings)
                    image = UIImage(data: data)
                    imageInfo = "Erzeugt mit \(provider)"
                case .tool:
                    break
                }
            } catch {
                if !Task.isCancelled {
                    ErrorLog.report(error, context: "Startseite")
                    self.error = friendly(error)
                }
            }
        }
    }

    func stop() { task?.cancel(); busy = false }
}

// MARK: Ansicht

struct StartView: View {
    var openTab: (Int) -> Void = { _ in }
    @EnvironmentObject private var settings: AppSettings
    @StateObject private var model = StartModel()
    @StateObject private var dictation = Dictation()
    @State private var showPrompt = false
    @State private var renaming: RecentStart?
    @State private var renameText = ""

    var body: some View {
        NavigationStack {
            ScrollView {
                if let c = model.category { define(c) } else { home }
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .alert("Favorit umbenennen", isPresented: Binding(get: { renaming != nil }, set: { if !$0 { renaming = nil } })) {
                TextField("Name", text: $renameText)
                Button("Speichern") {
                    if let r = renaming { model.renameRecent(r, to: renameText) }
                    renaming = nil
                }
                Button("Abbrechen", role: .cancel) { renaming = nil }
            } message: {
                Text("Leer lassen, um wieder die erste Zeile des Auftrags anzuzeigen.")
            }
            .navigationTitle(model.category.map { "\($0.emoji) \($0.title)" } ?? "Start")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                if model.category != nil {
                    ToolbarItem(placement: .topBarLeading) { Button { model.choose(nil) } label: { Image(systemName: "chevron.left") } }
                    ToolbarItem(placement: .topBarTrailing) { Button("Neu") { model.reset() } }
                }
            }
        }
    }

    private var home: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack(spacing: 12) {
                Image(Theme.current.symbol ?? "Logo").resizable().scaledToFit().frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 14))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Was möchtest du machen?").font(.title3.bold())
                    Text("Beschreib es in eigenen Worten – oder wähle unten Schritt für Schritt.").font(.caption).foregroundStyle(Theme.secondaryText)
                }
            }
            if !settings.hasKey { NeedsKeyView() }
            HStack(alignment: .bottom) {
                TextField("z. B. „Eine freundliche E-Mail an meinen Vermieter, dass die Heizung seit Montag ausgefallen ist“", text: $model.wish, axis: .vertical)
                    .lineLimit(3...8)
                Button {
                    dictation.toggle { t in model.wish = model.wish.isEmpty ? t : model.wish + " " + t }
                } label: {
                    Image(systemName: dictation.listening ? "mic.fill" : "mic").font(.title3).foregroundStyle(dictation.listening ? .red : Theme.cyan)
                }
            }
            .padding(12).background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
            if dictation.listening { Text(dictation.transcript.isEmpty ? "Ich höre zu …" : dictation.transcript).font(.callout).foregroundStyle(Theme.cyan) }
            GradientButton(title: model.defining ? "Die KI ordnet deinen Wunsch ein …" : "Genau definieren", icon: "sparkles", busy: model.defining) {
                model.define(settings: settings)
            }
            .disabled(model.wish.trimmingCharacters(in: .whitespaces).isEmpty)
            if let e = model.error { ErrorCard(message: e) }
            if !model.recent.isEmpty { recentSection }
            Text("Oder Schritt für Schritt").font(.subheadline.bold()).foregroundStyle(Theme.cyan).padding(.top, 4)
            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 10) {
                ForEach(startCategories) { c in
                    if c.mode == .tool {
                        NavigationLink { toolView(c) } label: { tile(c) }
                    } else {
                        Button { model.choose(c) } label: { tile(c) }
                    }
                }
            }
        }
        .padding()
    }

    private var recentSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("Zuletzt genutzt", systemImage: "clock.arrow.circlepath").font(.subheadline.bold()).foregroundStyle(Theme.cyan)
                Spacer()
                if model.recent.contains(where: { !$0.isPinned }) {
                    Button("Leeren") { withAnimation { model.clearRecent() } }.font(.caption)
                }
            }
            .padding(.top, 4)
            ForEach(model.recent) { r in
                if let c = startCategories.first(where: { $0.id == r.categoryId }) {
                    recentRow(r, c)
                }
            }
        }
    }

    private func recentRow(_ r: RecentStart, _ c: StartCategory) -> some View {
        let firstLine = { (t: String?) -> String in t?.split(separator: "\n").map { $0.trimmingCharacters(in: .whitespaces) }.first { !$0.isEmpty } ?? "" }
        let summary = firstLine(r.content).isEmpty ? firstLine(r.customPrompt) : firstLine(r.content)
        let detail = c.groups.compactMap { r.options[$0.key] }.prefix(2).joined(separator: " · ")
        return HStack(spacing: 10) {
            Button { model.useRecent(r) } label: {
                HStack(spacing: 10) {
                    Text(c.emoji).font(.title3)
                    VStack(alignment: .leading, spacing: 2) {
                        if let name = r.name {
                            Text(name).font(.callout.bold()).foregroundStyle(Theme.text).lineLimit(1)
                            Text(c.title + (detail.isEmpty ? "" : " · \(detail)")).font(.caption).foregroundStyle(Theme.secondaryText).lineLimit(1)
                        } else {
                            Text(c.title + (detail.isEmpty ? "" : " · \(detail)")).font(.callout.bold()).foregroundStyle(Theme.text).lineLimit(1)
                            if !summary.isEmpty { Text(summary).font(.caption).foregroundStyle(Theme.secondaryText).lineLimit(1) }
                        }
                    }
                    Spacer(minLength: 0)
                }
                .contentShape(Rectangle())
            }
            .accessibilityHint("Auftrag übernehmen")
            if r.isPinned {
                Button {
                    renameText = r.name ?? String(summary.prefix(RecentStartStore.maxName))
                    renaming = r
                } label: {
                    Image(systemName: "pencil").font(.caption).foregroundStyle(Theme.secondaryText).padding(6)
                }
                .accessibilityLabel("Favorit umbenennen")
            }
            Button { withAnimation { model.togglePinRecent(r) } } label: {
                Image(systemName: r.isPinned ? "pin.fill" : "pin").font(.caption)
                    .foregroundStyle(r.isPinned ? Theme.cyan : Theme.secondaryText).padding(6)
            }
            .accessibilityLabel(r.isPinned ? "Lösen (nicht mehr angeheftet)" : "Als Favorit anheften")
            Button { withAnimation { model.removeRecent(r) } } label: {
                Image(systemName: "xmark").font(.caption).foregroundStyle(Theme.secondaryText).padding(6)
            }
            .accessibilityLabel("Aus „Zuletzt genutzt“ entfernen")
        }
        .padding(.horizontal, 12).padding(.vertical, 8)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
    }

    private func tile(_ c: StartCategory) -> some View {
        VStack(spacing: 4) {
            Text(c.emoji).font(.title)
            Text(c.title).font(.caption.bold()).foregroundStyle(Theme.text).lineLimit(1).minimumScaleFactor(0.8)
            Text(c.subtitle).font(.caption2).foregroundStyle(Theme.secondaryText).lineLimit(2).multilineTextAlignment(.center)
        }
        .frame(maxWidth: .infinity, minHeight: 92)
        .padding(6)
        .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
    }

    @ViewBuilder
    private func toolView(_ c: StartCategory) -> some View {
        Group {
            switch c.tool {
            case .photo: PhotoEditorView()
            case .transcribe: TranscribeView()
            case .ideas: IdeasView()
            default: DataToolView()
            }
        }
        .navigationTitle(c.title).navigationBarTitleDisplayMode(.inline)
        .background(Theme.backgroundGradient.ignoresSafeArea())
    }

    private func define(_ c: StartCategory) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            if !model.note.isEmpty {
                HStack(alignment: .top, spacing: 8) {
                    Image(systemName: "sparkles").foregroundStyle(Theme.cyan)
                    Text(model.note).font(.callout)
                }
                .padding(12).frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.blue.opacity(0.25), in: RoundedRectangle(cornerRadius: 12))
            }
            Text("Kategorie").font(.subheadline.bold())
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(startCategories.filter { $0.mode != .tool }) { k in
                        chip("\(k.emoji) \(k.title)", selected: k.id == c.id) { if k.id != c.id { model.choose(k) } }
                    }
                }
            }
            TextField(c.contentHint, text: Binding(get: { model.content }, set: { model.content = $0; model.customPrompt = nil }), axis: .vertical)
                .lineLimit(3...12)
                .padding(12).background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                .overlay(alignment: .topLeading) {
                    Text(c.contentLabel).font(.caption2).foregroundStyle(Theme.cyan).padding(.horizontal, 6).offset(x: 8, y: -8)
                }
            ForEach(c.groups, id: \.key) { g in
                VStack(alignment: .leading, spacing: 6) {
                    Text(g.label).font(.subheadline.bold())
                    FlowChips(options: g.options, selected: model.options[g.key]) { v in model.options[g.key] = v; model.customPrompt = nil }
                }
            }
            ForEach(c.extras, id: \.key) { f in
                VStack(alignment: .leading, spacing: 4) {
                    Text(f.label).font(.subheadline.bold())
                    TextField(f.hint, text: Binding(get: { model.extras[f.key] ?? "" }, set: { model.extras[f.key] = $0; model.customPrompt = nil }), axis: .vertical)
                        .lineLimit(1...3)
                        .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                }
            }
            VStack(alignment: .leading, spacing: 6) {
                Button { withAnimation { showPrompt.toggle() } } label: {
                    HStack {
                        Image(systemName: "doc.text")
                        Text("Dein Auftrag an die KI" + (model.customPrompt != nil ? " (bearbeitet)" : "")).font(.subheadline.bold())
                        Spacer()
                        Image(systemName: showPrompt ? "chevron.up" : "chevron.down")
                    }
                    .foregroundStyle(Theme.text)
                }
                if showPrompt {
                    TextEditor(text: Binding(get: { model.builtPrompt }, set: { model.customPrompt = $0 }))
                        .font(.footnote).frame(minHeight: 120).scrollContentBackground(.hidden)
                        .padding(6).background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 10))
                    if model.customPrompt != nil { Button("Aus der Auswahl neu erstellen") { model.customPrompt = nil }.font(.caption) }
                } else {
                    Text(model.builtPrompt.split(separator: "\n").filter { !$0.isEmpty }.joined(separator: " · "))
                        .font(.caption).foregroundStyle(Theme.secondaryText).lineLimit(3)
                }
            }
            .card()
            GradientButton(title: model.busy ? (model.status.isEmpty ? "Wird erstellt …" : model.status) : "Ausführen", icon: "play.fill", busy: model.busy) {
                model.run(settings: settings)
            }
            if model.busy { Button("Abbrechen") { model.stop() }.frame(maxWidth: .infinity) }
            if let e = model.error { ErrorCard(message: e) }
            if let img = model.image {
                VStack(alignment: .leading, spacing: 8) {
                    Image(uiImage: img).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12))
                    Text(model.imageInfo).font(.caption).foregroundStyle(Theme.secondaryText)
                    ResultActions(text: model.content, image: img, kind: "Bild")
                }
                .card()
            }
            if !model.result.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    MarkdownView(text: model.result)
                    if !model.sources.isEmpty {
                        Text("Quellen").font(.headline)
                        ForEach(model.sources, id: \.1) { t, u in if let url = URL(string: u) { Link(t, destination: url).font(.callout).lineLimit(2) } }
                    }
                    if !model.busy { ResultActions(text: model.result, kind: c.title) }
                }
                .card()
                if !model.busy {
                    Text("Gespeichert unter Werkzeuge → Ergebnisse. Ändere die Auswahl und tippe erneut auf „Ausführen“, um das Ergebnis anzupassen.")
                        .font(.caption2).foregroundStyle(Theme.secondaryText)
                }
            }
        }
        .padding()
    }

    private func chip(_ title: String, selected: Bool, _ action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title).font(.callout).padding(.horizontal, 12).padding(.vertical, 7)
                .background(selected ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card), in: Capsule())
                .foregroundStyle((selected) ? Theme.onAccent : Theme.text)
        }
    }
}

/// Auswahl-Chips, die in mehrere Zeilen umbrechen.
struct FlowChips: View {
    let options: [String]
    let selected: String?
    let onSelect: (String) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 104), spacing: 6)], alignment: .leading, spacing: 6) {
            ForEach(options, id: \.self) { o in
                Button { onSelect(o) } label: {
                    Text(o).font(.caption).lineLimit(1).minimumScaleFactor(0.8)
                        .frame(maxWidth: .infinity).padding(.vertical, 7).padding(.horizontal, 4)
                        .background(selected == o ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card), in: Capsule())
                        .foregroundStyle((selected == o) ? Theme.onAccent : Theme.text)
                }
            }
        }
    }
}
