import SwiftUI

struct Template: Identifiable, Hashable {
    let id: String
    let title: String
    let icon: String
    let hint: String
    let instruction: String
}

enum Templates {
    static let all: [Template] = [
        Template(id: "text", title: "Freier Text", icon: "text.alignleft", hint: "Worüber soll ich schreiben?", instruction: "Schreibe einen gut formulierten Text zu folgendem Thema:"),
        Template(id: "mail", title: "E-Mail", icon: "envelope", hint: "An wen, worum geht es?", instruction: "Schreibe eine passende E-Mail mit Betreffzeile zu folgendem Anliegen:"),
        Template(id: "report", title: "Bericht", icon: "doc.richtext", hint: "Thema und Stichpunkte des Berichts", instruction: "Erstelle einen strukturierten Bericht mit Überschriften, Zusammenfassung und Fazit:"),
        Template(id: "summary", title: "Zusammenfassen", icon: "list.bullet.rectangle", hint: "Text einfügen oder Datei anhängen", instruction: "Fasse den folgenden Inhalt präzise zusammen (Kernaussagen als Stichpunkte, dann ein kurzes Fazit):"),
        Template(id: "translate", title: "Übersetzen", icon: "globe", hint: "Text, der übersetzt werden soll", instruction: "Übersetze den folgenden Text natürlich und sinngemäß nach {LANG}. Gib nur die Übersetzung aus:"),
        Template(id: "analyze", title: "Inhalt analysieren", icon: "magnifyingglass", hint: "Text, Dokument, Foto oder Video", instruction: "Analysiere den folgenden Inhalt gründlich: Thema, Kernaussagen, Auffälligkeiten, Stimmung, offene Fragen und Empfehlungen:"),
        Template(id: "social", title: "Social Media", icon: "megaphone", hint: "Worum geht es im Beitrag?", instruction: "Schreibe drei Varianten eines ansprechenden Social-Media-Beitrags mit passenden Hashtags:"),
        Template(id: "letter", title: "Brief / Bewerbung", icon: "signature", hint: "Anlass, Empfänger, wichtige Punkte", instruction: "Schreibe einen formellen Brief nach DIN 5008 zu folgendem Anlass:"),
        Template(id: "learn", title: "Lernhilfe", icon: "graduationcap", hint: "Thema oder Lernstoff", instruction: "Erkläre das folgende Thema verständlich mit Beispielen, Merksätzen und erstelle am Ende 5 Quizfragen mit Lösungen:"),
        Template(id: "pattern", title: "Muster & Prognose", icon: "chart.line.uptrend.xyaxis", hint: "Daten, Verlauf oder Tabelle", instruction: "Erkenne Muster, Trends und Ausreißer in den folgenden Daten und gib eine begründete Prognose mit Unsicherheiten:"),
        Template(id: "plan", title: "Planen", icon: "calendar", hint: "Was soll geplant werden?", instruction: "Erstelle einen konkreten, realistischen Plan mit Zeitachse und Schritten für:"),
        Template(id: "improve", title: "Text verbessern", icon: "sparkles", hint: "Text einfügen", instruction: "Verbessere Rechtschreibung, Grammatik, Stil und Verständlichkeit des folgenden Textes. Gib den verbesserten Text aus und darunter kurz die wichtigsten Änderungen:"),
    ]

    static let tones = ["Neutral", "Freundlich", "Förmlich", "Locker", "Überzeugend", "Kurz & knapp", "Ausführlich"]
    static let languages = ["Englisch", "Französisch", "Spanisch", "Italienisch", "Niederländisch", "Polnisch", "Portugiesisch", "Tschechisch", "Dänisch", "Schwedisch", "Finnisch", "Griechisch", "Ungarisch", "Rumänisch", "Kroatisch", "Bulgarisch", "Slowakisch", "Slowenisch", "Estnisch", "Lettisch", "Litauisch", "Türkisch", "Ukrainisch", "Deutsch"]
}

struct StudioView: View {
    @State private var mode = 0

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Picker("", selection: $mode) {
                    Text("Texte").tag(0)
                    Text("Bilder").tag(1)
                }
                .pickerStyle(.segmented)
                .padding()
                if mode == 0 { TextStudio() } else { ImageStudio() }
            }
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Studio")
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}

struct TextStudio: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var template = Templates.all[0]
    @State private var tone = "Neutral"
    @State private var language = "Englisch"
    @State private var input = ""
    @State private var attachments: [Attachment] = []
    @State private var result = ""
    @State private var error: String?
    @State private var busy = false
    @State private var task: Task<Void, Never>?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                if !settings.hasKey { NeedsKeyView() }
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(Templates.all) { t in
                            Button { template = t } label: {
                                Label(t.title, systemImage: t.icon).font(.callout)
                                    .padding(.horizontal, 12).padding(.vertical, 8)
                                    .background(template == t ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card), in: Capsule())
                                    .foregroundStyle((template == t) ? Theme.onAccent : Theme.text)
                            }
                        }
                    }
                }
                HStack {
                    if template.id == "translate" {
                        Picker("Sprache", selection: $language) { ForEach(Templates.languages, id: \.self) { Text($0) } }
                    } else {
                        Picker("Ton", selection: $tone) { ForEach(Templates.tones, id: \.self) { Text($0) } }
                    }
                    Spacer()
                }
                .tint(Theme.cyan)
                TextField(template.hint, text: $input, axis: .vertical)
                    .lineLimit(4...12)
                    .padding(12)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                AttachmentBar(attachments: $attachments)
                GradientButton(title: busy ? "Wird erstellt …" : "Erstellen", icon: "sparkles", busy: busy) { generate() }
                    .disabled(!settings.hasKey || (input.trimmingCharacters(in: .whitespaces).isEmpty && attachments.isEmpty))
                if busy { Button("Abbrechen", role: .cancel) { task?.cancel(); busy = false }.frame(maxWidth: .infinity) }
                if let error { ErrorCard(message: error) }
                if !result.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        MarkdownView(text: result)
                        if !busy { ResultActions(text: result, kind: template.title) }
                    }
                    .card()
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 30)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    private func generate() {
        let (extra, images) = AttachmentLoader.prompt(for: attachments)
        var prompt = template.instruction.replacingOccurrences(of: "{LANG}", with: language)
        if template.id != "translate" && tone != "Neutral" { prompt += "\n(Tonfall: \(tone))" }
        prompt += "\n\n" + input + extra
        result = ""
        error = nil
        busy = true
        let model = images.isEmpty ? settings.chatModel : "mistral-small-latest"
        task = Task { @MainActor in
            do {
                let msgs = [ApiMessage(role: "system", text: ChatModel.system), ApiMessage(role: "user", text: prompt, images: images)]
                for try await chunk in MistralClient.shared.stream(key: settings.key, model: model, messages: msgs) { result += chunk }
            } catch {
                ErrorLog.report(error, context: "Studio Text")
                if !Task.isCancelled { self.error = friendly(error) }
            }
            busy = false
        }
    }
}

struct ImageStudio: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var prompt = ""
    @State private var style = "Fotorealistisch"
    @State private var aspect = 1.0
    @State private var references: [Attachment] = []
    @State private var image: UIImage?
    @State private var info = ""
    @State private var step = ""
    @State private var error: String?
    @State private var busy = false

    private let styles = ["Fotorealistisch", "Illustration", "Aquarell", "3D-Render", "Comic", "Ölgemälde", "Minimalistisch", "Anime", "Pixel-Art", "Skizze"]

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                TextField("Beschreibe das Bild … z. B. „Ein Leuchtturm bei Sonnenuntergang“", text: $prompt, axis: .vertical)
                    .lineLimit(3...8)
                    .padding(12)
                    .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                HStack {
                    Picker("Stil", selection: $style) { ForEach(styles, id: \.self) { Text($0) } }
                    Spacer()
                    Picker("Format", selection: $aspect) {
                        Text("Quadrat").tag(1.0)
                        Text("Hoch").tag(0.6)
                        Text("Quer").tag(1.7)
                    }
                    .pickerStyle(.segmented).frame(width: 190)
                }
                .tint(Theme.cyan)

                VStack(alignment: .leading, spacing: 6) {
                    Text("Referenzen (Fotos/Videos)").font(.subheadline.bold())
                    Text(references.isEmpty ? "Optional: mehrere Bilder oder Videos als Vorlage." :
                         settings.canEditFaithfully ? "Gesichtstreue Bearbeitung mit FLUX Kontext aktiv." :
                         "Die KI beschreibt die Vorlagen und erzeugt daraus ein neues Bild. Für echte Gesichtstreue den Black-Forest-Labs-Schlüssel eintragen.")
                        .font(.caption).foregroundStyle(Theme.secondaryText)
                    AttachmentBar(attachments: $references, allowFiles: true, maxItems: 6)
                }
                .card()

                GradientButton(title: busy ? "Bild wird erzeugt …" : "Bild erzeugen", icon: "photo.badge.plus", busy: busy) { Task { await generate() } }
                    .disabled(prompt.trimmingCharacters(in: .whitespaces).isEmpty && references.isEmpty)
                if busy && !step.isEmpty { Text(step).font(.callout).foregroundStyle(Theme.cyan) }
                if let error { ErrorCard(message: error) }
                if let image {
                    VStack(alignment: .leading, spacing: 10) {
                        Image(uiImage: image).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12))
                        Text(info).font(.caption).foregroundStyle(Theme.secondaryText)
                        ResultActions(text: prompt, image: image, kind: "Bild")
                    }
                    .card()
                }
            }
            .padding(.horizontal)
            .padding(.bottom, 30)
        }
        .scrollDismissesKeyboard(.interactively)
    }

    @MainActor
    private func generate() async {
        busy = true
        error = nil
        defer { busy = false; step = "" }
        let refImages = references.flatMap { $0.kind == .video ? Array($0.images.prefix(2)) : $0.images }
        let userPrompt = prompt.trimmingCharacters(in: .whitespacesAndNewlines)
        do {
            if !refImages.isEmpty && settings.canEditFaithfully {
                step = "Bearbeite gesichtstreu mit FLUX Kontext …"
                let instruction = (userPrompt.isEmpty ? "Improve the image" : userPrompt) + ", style: \(style)"
                let data = try await ImageService.editFaithfully(key: settings.bflKey, instruction: instruction, images: refImages)
                image = UIImage(data: data)
                info = "Black Forest Labs · FLUX Kontext (gesichtstreu)"
                return
            }
            var full = userPrompt
            var ratio = aspect
            if !refImages.isEmpty {
                guard settings.hasKey else { throw APIError(code: 401, message: "Für Referenzbilder wird der Mistral-Schlüssel benötigt.") }
                step = "Analysiere \(refImages.count) Referenz(en) …"
                let desc = try await MistralClient.shared.describe(
                    key: settings.key, preferredModel: "mistral-small-latest",
                    instruction: "Beschreibe diese Bilder sehr genau auf Englisch als Bild-Prompt: Personen (Alter, Haare, Gesichtszüge, Kleidung), Szene, Licht, Farben, Perspektive. Nur die Beschreibung, max. 120 Wörter.",
                    images: refImages.compactMap { $0.dataURL })
                full = "\(desc). \(userPrompt)"
                if let first = refImages.first { ratio = first.size.width / max(1, first.size.height) }
            }
            step = "Erzeuge Bild …"
            let (data, provider) = try await ImageService.generate(prompt: "\(full), \(style)", aspect: ratio, settings: settings)
            image = UIImage(data: data)
            info = provider
        } catch {
            ErrorLog.report(error, context: "Studio Bild")
            self.error = friendly(error)
        }
    }
}
