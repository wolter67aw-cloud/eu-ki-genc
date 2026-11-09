import SwiftUI
import PhotosUI
import PencilKit
import AVFoundation

// MARK: Übersicht

struct IdeasView: View {
    @EnvironmentObject private var settings: AppSettings
    @ObservedObject private var store = IdeaStore.shared
    @State private var query = ""
    @State private var tagFilter: String?
    @State private var editing: IdeaKind?
    @State private var showCross = false
    @State private var lockedInfo: String?

    private var topTags: [String] {
        var counts: [String: Int] = [:]
        for i in store.ideas where !i.locked { for t in i.tags { counts[t, default: 0] += 1 } }
        return counts.sorted { $0.value > $1.value }.prefix(15).map(\.key)
    }

    private var shown: [Idea] {
        store.ideas.filter { idea in
            (tagFilter == nil || idea.tags.contains(tagFilter!)) &&
            (query.trimmingCharacters(in: .whitespaces).isEmpty ||
             query.split(separator: " ").allSatisfy { idea.searchable.localizedCaseInsensitiveContains($0) })
        }
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Halte Gedanken fest – als Foto, Sprachmemo, Skizze oder Zeitkapsel. Die KI vergibt passende Schlagwörter, damit du alles wiederfindest.")
                    .font(.callout).foregroundStyle(Theme.secondaryText)
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                    action("Moment", "camera") { editing = .moment }
                    action("Sprachmemo", "mic") { editing = .memo }
                    action("Skizze", "pencil.tip") { editing = .sketch }
                    action("Zeitkapsel", "hourglass") { editing = .capsule }
                    action("Notiz", "note.text") { editing = .note }
                    action("Kreuzen", "shuffle") { showCross = true }.disabled(store.available.count < 2).opacity(store.available.count < 2 ? 0.4 : 1)
                }
                HStack {
                    Image(systemName: "magnifyingglass").foregroundStyle(Theme.secondaryText)
                    TextField("Suchen in Texten, Transkripten, Schlagwörtern …", text: $query)
                    if !query.isEmpty { Button { query = "" } label: { Image(systemName: "xmark.circle.fill") } }
                }
                .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                if !topTags.isEmpty {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 6) {
                            ForEach(topTags, id: \.self) { t in
                                Button { tagFilter = tagFilter == t ? nil : t } label: {
                                    Text("#\(t)").font(.caption).padding(.horizontal, 10).padding(.vertical, 6)
                                        .background(tagFilter == t ? AnyShapeStyle(Theme.accentGradient) : AnyShapeStyle(Theme.card), in: Capsule())
                                        .foregroundStyle((tagFilter == t) ? Theme.onAccent : Theme.text)
                                }
                            }
                        }
                    }
                }
                if let lockedInfo { Text(lockedInfo).font(.callout).foregroundStyle(Theme.cyan) }
                if shown.isEmpty {
                    Text(store.ideas.isEmpty ? "Noch keine Ideen gespeichert." : "Nichts gefunden.")
                        .foregroundStyle(Theme.secondaryText).padding(.vertical, 20)
                }
                ForEach(shown) { idea in
                    if idea.locked {
                        Button { lockedInfo = "Diese Zeitkapsel öffnet sich am \(idea.openAt!.formatted(date: .long, time: .shortened))." } label: { IdeaRow(idea: idea) }
                    } else {
                        NavigationLink { IdeaDetailView(id: idea.id) } label: { IdeaRow(idea: idea) }
                    }
                }
            }
            .padding()
        }
        .sheet(item: $editing) { kind in
            NavigationStack { IdeaEditorView(kind: kind) }.preferredColorScheme(Theme.current.dark ? .dark : .light)
        }
        .sheet(isPresented: $showCross) {
            NavigationStack { IdeaCrossView() }.preferredColorScheme(Theme.current.dark ? .dark : .light)
        }
    }

    private func action(_ title: String, _ icon: String, _ run: @escaping () -> Void) -> some View {
        Button(action: run) {
            VStack(spacing: 6) {
                Image(systemName: icon).font(.title3).foregroundStyle(Theme.accentGradient)
                Text(title).font(.caption).foregroundStyle(Theme.text)
            }
            .frame(maxWidth: .infinity, minHeight: 64)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
        }
    }
}

extension IdeaKind: Identifiable { var id: String { rawValue } }

struct IdeaRow: View {
    let idea: Idea
    @ObservedObject private var store = IdeaStore.shared

    var body: some View {
        HStack(spacing: 12) {
            if !idea.locked, let img = IdeaStore.image(idea) {
                Image(uiImage: img).resizable().scaledToFill().frame(width: 56, height: 56).clipShape(RoundedRectangle(cornerRadius: 10))
            } else {
                Text(idea.locked ? "🔒" : idea.kind.emoji).font(.title2).frame(width: 56, height: 56).background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 10))
            }
            VStack(alignment: .leading, spacing: 3) {
                Text(idea.title).font(.headline).lineLimit(1).foregroundStyle(Theme.text)
                Text(idea.locked ? "Verschlossen bis \(idea.openAt!.formatted(date: .abbreviated, time: .shortened))"
                     : "\(idea.kind.label) · \(idea.createdAt.formatted(date: .abbreviated, time: .shortened))\(idea.mood.isEmpty ? "" : " · \(idea.mood)")")
                    .font(.caption).foregroundStyle(Theme.secondaryText)
                if !idea.locked && !idea.text.isEmpty { Text(idea.text).font(.caption).lineLimit(2).foregroundStyle(Theme.text.opacity(0.85)) }
                if store.tagging.contains(idea.id) {
                    Text("Schlagwörter werden erstellt …").font(.caption2).foregroundStyle(Theme.cyan)
                } else if !idea.locked && !idea.tags.isEmpty {
                    Text(idea.tags.map { "#\($0)" }.joined(separator: " ")).font(.caption2).foregroundStyle(Theme.cyan).lineLimit(1)
                }
            }
            Spacer(minLength: 0)
        }
        .multilineTextAlignment(.leading)
        .card()
    }
}

// MARK: Erfassen

struct IdeaEditorView: View {
    let kind: IdeaKind
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var session: SessionController
    @Environment(\.dismiss) private var dismiss
    @StateObject private var recorder = AudioRecorder()
    @State private var title = ""
    @State private var text = ""
    @State private var mood = ""
    @State private var image: UIImage?
    @State private var pick: PhotosPickerItem?
    @State private var showCamera = false
    @State private var audioName: String?
    @State private var transcribing = false
    @State private var openAt = Calendar.current.date(byAdding: .year, value: 1, to: Date())!
    @State private var drawing = PKDrawing()
    @State private var error: String?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text(intro).font(.callout).foregroundStyle(Theme.secondaryText)
                if kind == .sketch {
                    SketchCanvas(drawing: $drawing).frame(height: 420).clipShape(RoundedRectangle(cornerRadius: 12))
                    HStack {
                        Text("Werkzeuge erscheinen unten – Stift, Farben, Radierer.").font(.caption).foregroundStyle(Theme.secondaryText)
                        Spacer()
                        Button("Leeren") { drawing = PKDrawing() }.font(.callout)
                    }
                }
                if kind == .memo {
                    HStack(spacing: 12) {
                        Button {
                            recorder.toggle()
                        } label: {
                            Label(recorder.recording ? "Stopp (\(recorder.seconds) s)" : (audioName == nil ? "Aufnehmen" : "Neu aufnehmen"),
                                  systemImage: recorder.recording ? "stop.circle.fill" : "mic.circle")
                        }
                        .buttonStyle(.borderedProminent).tint(recorder.recording ? .red : Theme.blue)
                        if transcribing { ProgressView(); Text("Wird transkribiert …").font(.callout) }
                        else if audioName != nil && !recorder.recording { Text("✓ Aufnahme bereit").foregroundStyle(Theme.cyan) }
                    }
                    .onChange(of: recorder.recording) { _, rec in
                        if !rec && recorder.seconds > 0 { Task { await finishRecording() } }
                    }
                }
                if [.moment, .capsule, .note].contains(kind) {
                    HStack {
                        PhotosPicker(selection: $pick, matching: .images) { Label("Bild/Screenshot", systemImage: "photo") }
                        Button { session.externalPickerActive = true; showCamera = true } label: { Label("Kamera", systemImage: "camera") }
                    }
                    .buttonStyle(.bordered).tint(Theme.cyan)
                    if let image { Image(uiImage: image).resizable().scaledToFit().frame(maxHeight: 260).clipShape(RoundedRectangle(cornerRadius: 12)) }
                }
                TextField("Titel (optional)", text: $title)
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                TextField(kind == .memo ? "Transkript" : (kind == .sketch ? "Notiz zur Skizze (optional)" : "Text"), text: $text, axis: .vertical)
                    .lineLimit(kind == .sketch ? 2...5 : 4...12)
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                if [.moment, .note, .memo].contains(kind) {
                    Text("Stimmung").font(.subheadline.bold())
                    HStack(spacing: 6) {
                        ForEach(moods, id: \.self) { m in
                            Button { mood = mood == m ? "" : m } label: {
                                Text(m).font(.title2).padding(6)
                                    .background(mood == m ? Theme.blue.opacity(0.6) : Theme.card, in: RoundedRectangle(cornerRadius: 10))
                            }
                        }
                    }
                }
                if kind == .capsule {
                    DatePicker("Öffnen am", selection: $openAt, in: Date().addingTimeInterval(3600)..., displayedComponents: [.date, .hourAndMinute])
                    HStack {
                        ForEach([("1 Woche", 7), ("1 Monat", 30), ("6 Monate", 182), ("1 Jahr", 365)], id: \.0) { label, days in
                            Button(label) { openAt = Date().addingTimeInterval(Double(days) * 86_400) }.font(.caption).buttonStyle(.bordered)
                        }
                    }
                    .tint(Theme.cyan)
                }
                if let error { ErrorCard(message: error) }
                GradientButton(title: kind == .capsule ? "Zeitkapsel verschließen" : "Speichern", icon: kind == .capsule ? "lock" : "square.and.arrow.down") { save() }
                    .disabled(recorder.recording || transcribing)
            }
            .padding()
        }
        .background(Theme.backgroundGradient.ignoresSafeArea())
        .navigationTitle(kind.label)
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Abbrechen") { if recorder.recording { recorder.stop() }; dismiss() } } }
        .onChange(of: pick) { _, item in
            Task { if let d = try? await item?.loadTransferable(type: Data.self), let img = UIImage(data: d) { image = img } }
        }
        .fullScreenCover(isPresented: $showCamera, onDismiss: { session.externalPickerActive = false }) {
            CameraPicker { img in if let img { image = img } }.ignoresSafeArea()
        }
    }

    private var intro: String {
        switch kind {
        case .moment: return "Halte einen Moment fest: Foto oder Screenshot, ein paar Worte und deine Stimmung. Datum und Uhrzeit werden automatisch gespeichert."
        case .memo: return "Sprich deine Idee ein – sie wird automatisch in Text umgewandelt und ist danach durchsuchbar. Die Aufnahme bleibt erhalten."
        case .sketch: return "Zeichne mit dem Finger eine Skizze oder Mindmap. Die KI kann sie danach auswerten und in Text übertragen."
        case .capsule: return "Schreib eine Nachricht an dein zukünftiges Ich. Sie bleibt bis zum gewählten Datum verschlossen – dann erinnert dich die App."
        default: return "Schreib einen Gedanken auf."
        }
    }

    @MainActor
    private func finishRecording() async {
        if let old = audioName, let u = IdeaStore.url(old) { try? FileManager.default.removeItem(at: u) }
        audioName = IdeaStore.saveAudio(from: recorder.url)
        guard settings.hasKey, let name = audioName, let u = IdeaStore.url(name), let data = try? Data(contentsOf: u) else {
            if !settings.hasKey { error = "Ohne Mistral-Schlüssel wird nur der Ton gespeichert (keine Transkription)." }
            return
        }
        transcribing = true
        defer { transcribing = false }
        do {
            let t = try await MistralClient.shared.transcribe(key: settings.key, data: data, fileName: name, mime: "audio/mp4", language: nil)
            text = [text, t].filter { !$0.isEmpty }.joined(separator: "\n")
            if title.isEmpty { title = String(t.split(separator: "\n").first?.prefix(60) ?? "") }
        } catch {
            self.error = "Transkription fehlgeschlagen: \(friendly(error))"
        }
    }

    private func save() {
        var imageFile: String?
        if kind == .sketch {
            guard !drawing.strokes.isEmpty else { error = "Die Skizze ist noch leer."; return }
            let bounds = drawing.bounds.insetBy(dx: -40, dy: -40)
            let rendered = UIGraphicsImageRenderer(size: bounds.size).image { ctx in
                UIColor.white.setFill()
                ctx.fill(CGRect(origin: .zero, size: bounds.size))
                drawing.image(from: bounds, scale: 2).draw(in: CGRect(origin: .zero, size: bounds.size))
            }
            imageFile = IdeaStore.saveImage(rendered)
        } else if let image {
            imageFile = IdeaStore.saveImage(image)
        }
        let body = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !body.isEmpty || imageFile != nil || audioName != nil else { error = "Bitte etwas eingeben."; return }
        var t = title.trimmingCharacters(in: .whitespaces)
        if t.isEmpty { t = body.split(separator: "\n").first.map { String($0.prefix(60)) } ?? "\(kind.label) \(Date().formatted(date: .abbreviated, time: .shortened))" }
        let idea = Idea(kind: kind, title: t, text: body, mood: mood, imageFile: imageFile, audioFile: audioName, openAt: kind == .capsule ? openAt : nil)
        IdeaStore.shared.add(idea, settings: settings)
        dismiss()
    }
}

/// Zeichenfläche mit PencilKit (Finger oder Apple Pencil).
struct SketchCanvas: UIViewRepresentable {
    @Binding var drawing: PKDrawing

    func makeUIView(context: Context) -> PKCanvasView {
        let v = PKCanvasView()
        v.drawingPolicy = .anyInput
        v.backgroundColor = .white
        v.overrideUserInterfaceStyle = .light
        v.tool = PKInkingTool(.pen, color: .black, width: 5)
        v.delegate = context.coordinator
        v.drawing = drawing
        context.coordinator.picker.setVisible(true, forFirstResponder: v)
        context.coordinator.picker.addObserver(v)
        DispatchQueue.main.async { v.becomeFirstResponder() }
        return v
    }

    func updateUIView(_ v: PKCanvasView, context: Context) {
        if v.drawing != drawing { v.drawing = drawing }
    }

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, PKCanvasViewDelegate {
        let parent: SketchCanvas
        let picker = PKToolPicker()
        init(_ p: SketchCanvas) { parent = p }
        func canvasViewDrawingDidChange(_ canvasView: PKCanvasView) { parent.drawing = canvasView.drawing }
    }
}

// MARK: Detail

struct IdeaDetailView: View {
    let id: UUID
    @EnvironmentObject private var settings: AppSettings
    @ObservedObject private var store = IdeaStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var player: AVAudioPlayer?
    @State private var playing = false
    @State private var newTag = ""
    @State private var analysis = ""
    @State private var busy = false
    @State private var error: String?
    @State private var showCross = false
    @State private var confirmDelete = false

    var body: some View {
        if let idea = store.ideas.first(where: { $0.id == id }) {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    Text("\(idea.kind.emoji) \(idea.title)").font(.title2.bold())
                    Text("\(idea.kind.label) · \(idea.createdAt.formatted(date: .long, time: .shortened))\(idea.mood.isEmpty ? "" : " · Stimmung \(idea.mood)")")
                        .font(.caption).foregroundStyle(Theme.secondaryText)
                    if let img = IdeaStore.image(idea) {
                        Image(uiImage: img).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    if let u = IdeaStore.url(idea.audioFile) {
                        Button { togglePlay(u) } label: { Label(playing ? "Stopp" : "Aufnahme abspielen", systemImage: playing ? "stop.circle" : "play.circle") }
                            .buttonStyle(.bordered).tint(Theme.cyan)
                    }
                    if !idea.text.isEmpty { MarkdownView(text: idea.text) }

                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Schlagwörter").font(.subheadline.bold())
                            Spacer()
                            if store.tagging.contains(idea.id) { ProgressView() } else {
                                Button { var c = idea; c.tags = []; store.upsert(c); store.autoTag(c, settings: settings) } label: { Label("KI-Vorschläge", systemImage: "sparkles").font(.caption) }
                            }
                        }
                        FlowTags(tags: idea.tags) { t in var c = idea; c.tags.removeAll { $0 == t }; store.upsert(c) }
                        HStack {
                            TextField("Eigenes Schlagwort", text: $newTag).textInputAutocapitalization(.never)
                            Button {
                                let t = newTag.trimmingCharacters(in: .whitespaces).lowercased().replacingOccurrences(of: "#", with: "")
                                if !t.isEmpty && !idea.tags.contains(t) { var c = idea; c.tags.append(t); store.upsert(c) }
                                newTag = ""
                            } label: { Image(systemName: "plus.circle.fill") }
                        }
                        .padding(8).background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 10))
                    }
                    .card()

                    HStack(spacing: 10) {
                        if IdeaStore.image(idea) != nil {
                            Button { Task { await analyze(idea) } } label: { Label(idea.kind == .sketch ? "Skizze auswerten" : "KI auswerten", systemImage: "sparkles") }
                                .buttonStyle(.borderedProminent).tint(Theme.blue).disabled(busy || !settings.hasKey)
                        }
                        Button { showCross = true } label: { Label("Kreuzen", systemImage: "shuffle") }.buttonStyle(.bordered).disabled(store.available.count < 2)
                        ShareLink(item: "\(idea.title)\n\n\(idea.text)\n\n\(idea.tags.map { "#\($0)" }.joined(separator: " "))") { Image(systemName: "square.and.arrow.up") }
                            .buttonStyle(.bordered)
                        Button(role: .destructive) { confirmDelete = true } label: { Image(systemName: "trash") }.buttonStyle(.bordered)
                    }
                    .tint(Theme.cyan)
                    if busy && analysis.isEmpty { ProgressView("KI wertet aus …") }
                    if let error { ErrorCard(message: error) }
                    if !analysis.isEmpty {
                        VStack(alignment: .leading) { MarkdownView(text: analysis); if !busy { ResultActions(text: analysis, kind: "Ideen") } }.card()
                    }
                }
                .padding()
            }
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Idee")
            .navigationBarTitleDisplayMode(.inline)
            .onDisappear { player?.stop() }
            .sheet(isPresented: $showCross) { NavigationStack { IdeaCrossView(first: idea) }.preferredColorScheme(Theme.current.dark ? .dark : .light) }
            .confirmationDialog("Idee löschen?", isPresented: $confirmDelete, titleVisibility: .visible) {
                Button("Löschen", role: .destructive) { player?.stop(); store.delete(idea); dismiss() }
            }
        } else {
            Text("Nicht mehr vorhanden.").foregroundStyle(Theme.secondaryText)
        }
    }

    private func togglePlay(_ url: URL) {
        if playing { player?.stop(); playing = false; return }
        try? AVAudioSession.sharedInstance().setCategory(.playback)
        try? AVAudioSession.sharedInstance().setActive(true)
        player = try? AVAudioPlayer(contentsOf: url)
        player?.play()
        playing = player != nil
        if let d = player?.duration { DispatchQueue.main.asyncAfter(deadline: .now() + d + 0.2) { playing = false } }
    }

    @MainActor
    private func analyze(_ idea: Idea) async {
        guard let img = IdeaStore.image(idea)?.dataURL else { return }
        busy = true; error = nil; analysis = ""
        defer { busy = false }
        let instruction = idea.kind == .sketch
            ? "Das ist eine Handskizze (z. B. Mindmap, Entwurf, Diagramm). Beschreibe, was dargestellt ist, übertrage Text und Struktur sauber in Markdown (Mindmaps als verschachtelte Liste) und gib 3 Ideen, wie man sie weiterentwickeln kann."
            : "Werte diese Momentaufnahme aus: Was ist zu sehen bzw. worum geht es, welche Idee steckt darin, und welche 3 konkreten nächsten Schritte bieten sich an?"
        do {
            for try await c in MistralClient.shared.stream(key: settings.key, model: MistralClient.fallbackModel, messages: [
                ApiMessage(role: "system", text: "Du bist ein kreativer Assistent. Antworte auf Deutsch in Markdown."),
                ApiMessage(role: "user", text: "\(instruction)\n\nTitel: \(idea.title)\nNotiz: \(idea.text)\nStimmung: \(idea.mood)", images: [img]),
            ]) { analysis += c }
        } catch {
            ErrorLog.report(error, context: "Ideen auswerten")
            self.error = friendly(error)
        }
    }
}

struct FlowTags: View {
    let tags: [String]
    let onRemove: (String) -> Void

    var body: some View {
        LazyVGrid(columns: [GridItem(.adaptive(minimum: 90), spacing: 6)], alignment: .leading, spacing: 6) {
            ForEach(tags, id: \.self) { t in
                Button { onRemove(t) } label: {
                    HStack(spacing: 4) { Text("#\(t)").lineLimit(1); Image(systemName: "xmark").font(.caption2) }
                        .font(.caption).padding(.horizontal, 8).padding(.vertical, 5)
                        .background(Theme.blue.opacity(0.35), in: Capsule()).foregroundStyle(Theme.text)
                }
            }
        }
    }
}

// MARK: Ideen-Kreuzung

struct IdeaCrossView: View {
    var first: Idea? = nil
    @EnvironmentObject private var settings: AppSettings
    @ObservedObject private var store = IdeaStore.shared
    @Environment(\.dismiss) private var dismiss
    @State private var a: Idea?
    @State private var b: Idea?
    @State private var result = ""
    @State private var busy = false
    @State private var error: String?
    @State private var saved = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 12) {
                Text("Zwei Ideen treffen aufeinander – die KI entwickelt daraus neue Ansätze. Ideal fürs Brainstorming.")
                    .font(.callout).foregroundStyle(Theme.secondaryText)
                slot("A", $a)
                Text("×").font(.largeTitle).frame(maxWidth: .infinity)
                slot("B", $b)
                HStack {
                    Button { random() } label: { Label("Zufällig", systemImage: "shuffle") }.buttonStyle(.bordered).tint(Theme.cyan)
                    GradientButton(title: "Kreuzen", icon: "sparkles", busy: busy) { Task { await cross() } }
                        .disabled(a == nil || b == nil || a?.id == b?.id || !settings.hasKey)
                }
                if let error { ErrorCard(message: error) }
                if !result.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        MarkdownView(text: result)
                        if !busy {
                            ResultActions(text: result, kind: "Ideen-Kreuzung")
                            Button { saveResult() } label: { Label(saved ? "Gespeichert" : "Als neue Idee speichern", systemImage: saved ? "checkmark" : "square.and.arrow.down") }
                                .disabled(saved)
                        }
                    }
                    .card()
                }
            }
            .padding()
        }
        .background(Theme.backgroundGradient.ignoresSafeArea())
        .navigationTitle("Ideen-Kreuzung")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
        .onAppear {
            if let first { a = first; b = store.available.filter { $0.id != first.id }.randomElement() } else { random() }
        }
    }

    private func slot(_ label: String, _ binding: Binding<Idea?>) -> some View {
        Menu {
            ForEach(store.available) { i in Button("\(i.kind.emoji) \(i.title)") { binding.wrappedValue = i; result = ""; saved = false } }
        } label: {
            VStack(alignment: .leading, spacing: 4) {
                Text("Idee \(label) · antippen zum Wechseln").font(.caption2).foregroundStyle(Theme.cyan)
                Text(binding.wrappedValue.map { "\($0.kind.emoji) \($0.title)" } ?? "Bitte wählen").font(.headline).foregroundStyle(Theme.text)
                if let t = binding.wrappedValue?.text, !t.isEmpty { Text(t).font(.caption).lineLimit(3).foregroundStyle(Theme.secondaryText) }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .multilineTextAlignment(.leading)
            .card()
        }
    }

    private func random() {
        let pool = store.available.shuffled()
        a = pool.first
        b = pool.dropFirst().first
        result = ""; saved = false
    }

    @MainActor
    private func cross() async {
        guard let a, let b else { return }
        busy = true; error = nil; result = ""; saved = false
        defer { busy = false }
        let prompt = "Kreuze diese zwei Ideen kreativ miteinander. Entwickle daraus 3 neue, überraschende, aber konkret umsetzbare Ansätze (je: kurzer Titel, 2–3 Sätze, erster Schritt). Zum Schluss: welcher Ansatz am vielversprechendsten ist und warum.\n\nIdee A – \(a.title):\n\(a.text.prefix(3000))\n\nIdee B – \(b.title):\n\(b.text.prefix(3000))"
        do {
            for try await c in MistralClient.shared.stream(key: settings.key, model: settings.chatModel, messages: [
                ApiMessage(role: "system", text: "Du bist ein Ideen-Generator für Brainstorming. Antworte auf Deutsch in Markdown."),
                ApiMessage(role: "user", text: prompt),
            ], temperature: 0.9) { result += c }
        } catch {
            ErrorLog.report(error, context: "Ideen-Kreuzung")
            self.error = friendly(error)
        }
    }

    private func saveResult() {
        guard let a, let b else { return }
        store.add(Idea(kind: .cross, title: "\(a.title.prefix(28)) × \(b.title.prefix(28))", text: result), settings: settings)
        saved = true
    }
}
