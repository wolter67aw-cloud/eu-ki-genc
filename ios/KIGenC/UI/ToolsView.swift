import SwiftUI
import PhotosUI
import CoreImage
import CoreImage.CIFilterBuiltins
import AVFoundation
import AVKit

struct ToolsView: View {
    private let tools: [(String, String, String, AnyView)] = [
        ("Code-Assistent", "chevron.left.forwardslash.chevron.right", "Programme & Automationen", AnyView(CodeContent())),
        ("Ideen-Sammlung", "lightbulb", "Moment, Sprachmemo, Skizze, Zeitkapsel", AnyView(IdeasView())),
        ("Foto-Editor", "slider.horizontal.3", "Filter, Licht, KI-Bearbeitung", AnyView(PhotoEditorView())),
        ("Video", "film", "Analysieren, aufnehmen, Diashow", AnyView(VideoToolView())),
        ("Transkription", "waveform", "Audio & Video zu Text", AnyView(TranscribeView())),
        ("Recherche", "globe.europe.africa", "Websuche mit Quellen", AnyView(ResearchView())),
        ("Daten & Statistik", "tablecells", "CSV analysieren", AnyView(DataToolView())),
        ("Aufgaben", "checklist", "Planen mit Erinnerung", AnyView(TasksView())),
        ("Ergebnisse", "bookmark", "Gespeicherte Inhalte", AnyView(ResultsView())),
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 12) {
                    ForEach(tools, id: \.0) { title, icon, sub, dest in
                        NavigationLink {
                            dest.navigationTitle(title == "Code-Assistent" ? "Code" : title).navigationBarTitleDisplayMode(.inline)
                                .background(Theme.backgroundGradient.ignoresSafeArea())
                        } label: {
                            VStack(alignment: .leading, spacing: 8) {
                                Image(systemName: icon).font(.title).foregroundStyle(Theme.accentGradient)
                                Text(title).font(.headline).foregroundStyle(.white)
                                Text(sub).font(.caption).foregroundStyle(Theme.secondaryText).multilineTextAlignment(.leading)
                            }
                            .frame(maxWidth: .infinity, minHeight: 110, alignment: .topLeading)
                            .card()
                        }
                    }
                }
                .padding()
            }
            .background(Theme.backgroundGradient.ignoresSafeArea())
            .navigationTitle("Werkzeuge")
        }
    }
}

// MARK: Foto-Editor

struct PhotoEditorView: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var picks: [PhotosPickerItem] = []
    @State private var photos: [UIImage] = []
    @State private var current = 0
    @State private var useAll = true
    @State private var savedAll = false
    @State private var original: UIImage?
    @State private var edited: UIImage?
    @State private var brightness = 0.0
    @State private var contrast = 1.0
    @State private var saturation = 1.0
    @State private var preset = "Original"
    @State private var aiPrompt = ""
    @State private var aiImage: UIImage?
    @State private var step = ""
    @State private var error: String?
    @State private var busy = false
    private let context = CIContext()
    private let presets = ["Original", "Schwarz-Weiß", "Sepia", "Lebendig", "Kühl", "Warm", "Vintage", "Noir"]

    var body: some View {
        ScrollView {
            VStack(spacing: 14) {
                PhotosPicker(selection: $picks, maxSelectionCount: max(1, 10 - photos.count), matching: .images) {
                    Label(photos.isEmpty ? "Fotos auswählen (auch mehrere)" : "Weitere Fotos (\(photos.count)/10)", systemImage: "photo.on.rectangle")
                }
                .buttonStyle(.borderedProminent).tint(Theme.blue)
                .disabled(photos.count >= 10)

                if photos.count > 1 {
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(Array(photos.enumerated()), id: \.offset) { i, img in
                                ZStack(alignment: .topTrailing) {
                                    Button { select(i) } label: {
                                        Image(uiImage: img).resizable().scaledToFill().frame(width: 70, height: 70)
                                            .clipShape(RoundedRectangle(cornerRadius: 10))
                                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(i == current ? Theme.cyan : .clear, lineWidth: 3))
                                            .overlay(alignment: .bottomLeading) {
                                                Text("\(i + 1)").font(.caption2.bold()).padding(.horizontal, 5).background(.black.opacity(0.6), in: Capsule()).padding(4)
                                            }
                                    }
                                    Button { removePhoto(i) } label: { Image(systemName: "xmark.circle.fill").foregroundStyle(.white, .black.opacity(0.7)) }
                                        .offset(x: 5, y: -5)
                                }
                            }
                        }
                        .padding(.top, 5)
                    }
                }

                if let edited {
                    Image(uiImage: edited).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12))
                    VStack(alignment: .leading) {
                        Picker("Filter", selection: $preset) { ForEach(presets, id: \.self) { Text($0) } }.tint(Theme.cyan)
                        slider("Helligkeit", $brightness, -0.5...0.5)
                        slider("Kontrast", $contrast, 0.5...1.8)
                        slider("Sättigung", $saturation, 0...2)
                        ResultActions(text: "", image: edited, kind: "Foto")
                        if photos.count > 1 {
                            Button { saveAll() } label: {
                                Label(savedAll ? "Alle \(photos.count) gespeichert" : "Filter auf alle \(photos.count) anwenden & speichern", systemImage: savedAll ? "checkmark" : "square.and.arrow.down.on.square")
                            }
                            .font(.callout).padding(.top, 4)
                        }
                    }
                    .card()

                    VStack(alignment: .leading, spacing: 10) {
                        Text("KI-Bearbeitung").font(.headline)
                        Text(settings.canEditFaithfully ? "Gesichtstreu mit FLUX Kontext." : "Ohne Black-Forest-Labs-Schlüssel wird ein neues, ähnliches Bild erzeugt (Gesichter können abweichen).")
                            .font(.caption).foregroundStyle(Theme.secondaryText)
                        if photos.count > 1 {
                            Toggle(isOn: $useAll) {
                                VStack(alignment: .leading) {
                                    Text("Alle Fotos einbeziehen").font(.callout)
                                    Text("Foto \(current + 1) ist das Hauptbild, die anderen dienen als Vorlage – z. B. „alle Personen auf ein Gruppenfoto“.")
                                        .font(.caption).foregroundStyle(Theme.secondaryText)
                                }
                            }
                        }
                        TextField("z. B. „Hintergrund als Strand bei Sonnenuntergang“", text: $aiPrompt, axis: .vertical)
                            .lineLimit(2...5).padding(10).background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 10))
                        GradientButton(title: "Neu gestalten", icon: "wand.and.stars", busy: busy) { Task { await aiEdit() } }
                            .disabled(aiPrompt.trimmingCharacters(in: .whitespaces).isEmpty)
                        if busy { Text(step).font(.callout).foregroundStyle(Theme.cyan) }
                        if let error { ErrorCard(message: error) }
                        if let aiImage {
                            Image(uiImage: aiImage).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12))
                            HStack {
                                ResultActions(text: aiPrompt, image: aiImage, kind: "Foto (KI)")
                                Spacer()
                                Button("Weiter bearbeiten") {
                                    if photos.indices.contains(current) { photos[current] = aiImage }
                                    original = aiImage; reset()
                                }.font(.callout)
                            }
                        }
                    }
                    .card()
                } else {
                    Text("Wähle ein Foto, um Filter anzuwenden oder es per KI umzugestalten.")
                        .foregroundStyle(Theme.secondaryText).multilineTextAlignment(.center).padding(.top, 40)
                }
            }
            .padding()
        }
        .onChange(of: picks) { _, items in
            guard !items.isEmpty else { return }
            Task {
                let start = photos.count
                for item in items where photos.count < 10 {
                    if let d = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: d) {
                        photos.append(img.scaled(maxSide: 2048))
                    }
                }
                picks = []
                if photos.count > start { select(start) }
            }
        }
        .onChange(of: brightness) { _, _ in apply() }
        .onChange(of: contrast) { _, _ in apply() }
        .onChange(of: saturation) { _, _ in apply() }
        .onChange(of: preset) { _, _ in apply() }
    }

    private func slider(_ title: String, _ value: Binding<Double>, _ range: ClosedRange<Double>) -> some View {
        HStack {
            Text(title).font(.caption).frame(width: 80, alignment: .leading)
            Slider(value: value, in: range)
        }
    }

    private func select(_ i: Int) {
        guard photos.indices.contains(i) else { return }
        current = i
        original = photos[i]
        aiImage = nil
        apply()
        if edited == nil { edited = original }
    }

    private func removePhoto(_ i: Int) {
        guard photos.indices.contains(i) else { return }
        photos.remove(at: i)
        if photos.isEmpty { original = nil; edited = nil; current = 0; return }
        select(min(current, photos.count - 1))
    }

    private func saveAll() {
        for img in photos { UIImageWriteToSavedPhotosAlbum(render(img) ?? img, nil, nil, nil) }
        savedAll = true
    }

    private func reset() {
        brightness = 0; contrast = 1; saturation = 1; preset = "Original"
        edited = original
    }

    private func apply() {
        guard let original, let out = render(original) else { return }
        edited = out
    }

    /// Wendet die aktuellen Filter und Regler auf ein Bild an.
    private func render(_ original: UIImage) -> UIImage? {
        guard let cg = original.cgImage else { return nil }
        var img = CIImage(cgImage: cg)
        let controls = CIFilter.colorControls()
        controls.inputImage = img
        controls.brightness = Float(brightness)
        controls.contrast = Float(contrast)
        controls.saturation = Float(saturation)
        img = controls.outputImage ?? img
        switch preset {
        case "Schwarz-Weiß": let f = CIFilter.photoEffectMono(); f.inputImage = img; img = f.outputImage ?? img
        case "Sepia": let f = CIFilter.sepiaTone(); f.inputImage = img; f.intensity = 0.9; img = f.outputImage ?? img
        case "Lebendig": let f = CIFilter.vibrance(); f.inputImage = img; f.amount = 0.9; img = f.outputImage ?? img
        case "Kühl": let f = CIFilter.temperatureAndTint(); f.inputImage = img; f.neutral = CIVector(x: 6500, y: 0); f.targetNeutral = CIVector(x: 9000, y: 0); img = f.outputImage ?? img
        case "Warm": let f = CIFilter.temperatureAndTint(); f.inputImage = img; f.neutral = CIVector(x: 6500, y: 0); f.targetNeutral = CIVector(x: 4500, y: 0); img = f.outputImage ?? img
        case "Vintage": let f = CIFilter.photoEffectTransfer(); f.inputImage = img; img = f.outputImage ?? img
        case "Noir": let f = CIFilter.photoEffectNoir(); f.inputImage = img; img = f.outputImage ?? img
        default: break
        }
        guard let out = context.createCGImage(img, from: img.extent) else { return nil }
        return UIImage(cgImage: out, scale: original.scale, orientation: original.imageOrientation)
    }

    @MainActor
    private func aiEdit() async {
        guard let source = edited ?? original else { return }
        let others = (useAll && photos.count > 1) ? photos.enumerated().filter { $0.offset != current }.map(\.element) : []
        busy = true; error = nil; aiImage = nil
        defer { busy = false }
        do {
            if settings.canEditFaithfully {
                step = "Bearbeite gesichtstreu …"
                let instruction = others.isEmpty ? aiPrompt
                    : "\(aiPrompt.isEmpty ? "Combine the people and elements of all images naturally into one photo" : aiPrompt). Image 1 is the main photo; the other images are references (people, objects, style)."
                aiImage = UIImage(data: try await ImageService.editFaithfully(key: settings.bflKey, instruction: instruction, images: [source] + others))
            } else {
                guard settings.hasKey else { throw APIError(code: 401, message: "Bitte den Mistral-Schlüssel in den Optionen eintragen.") }
                step = "Analysiere Foto …"
                let desc = try await MistralClient.shared.describe(key: settings.key, preferredModel: "mistral-small-latest",
                    instruction: others.isEmpty
                        ? "Describe this photo precisely in English as an image prompt (people, faces, clothing, scene, light, colors, camera angle). Max 120 words, description only."
                        : "These are several photos: image 1 is the main photo, the others are references. Describe in English, as one image prompt, a single scene that combines the people and elements of all images (faces, clothing, setting, light, colors). Max 150 words, description only.",
                    images: ([source] + others.prefix(5)).compactMap { $0.dataURL })
                step = "Erzeuge neues Bild …"
                let (data, _) = try await ImageService.generate(prompt: "\(desc). Change: \(aiPrompt)", aspect: source.size.width / max(1, source.size.height), settings: settings)
                aiImage = UIImage(data: data)
            }
            if aiImage == nil { throw APIError(code: 0, message: "Es kam kein Bild zurück.") }
        } catch {
            ErrorLog.report(error, context: "Foto-Editor KI")
            self.error = friendly(error)
        }
    }
}

// MARK: Video

struct VideoToolView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var session: SessionController
    @State private var pick: PhotosPickerItem?
    @State private var videoURL: URL?
    @State private var showRecorder = false
    @State private var question = "Was passiert in diesem Video? Fasse den Inhalt zusammen."
    @State private var withAudio = true
    @State private var result = ""
    @State private var step = ""
    @State private var error: String?
    @State private var busy = false
    // Diashow
    @State private var slidePicks: [PhotosPickerItem] = []
    @State private var slideSeconds = 3.0
    @State private var slideURL: URL?

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Video analysieren").font(.headline)
                HStack {
                    PhotosPicker(selection: $pick, matching: .videos) { Label("Video wählen", systemImage: "film") }
                    Button { session.externalPickerActive = true; showRecorder = true } label: { Label("Live aufnehmen", systemImage: "record.circle") }
                }
                .buttonStyle(.bordered).tint(Theme.cyan)
                if let videoURL {
                    VideoPlayer(player: AVPlayer(url: videoURL)).frame(height: 220).clipShape(RoundedRectangle(cornerRadius: 12))
                    TextField("Frage zum Video", text: $question, axis: .vertical).lineLimit(2...4)
                        .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    Toggle("Ton mit auswerten (Transkription)", isOn: $withAudio)
                    GradientButton(title: "Analysieren", icon: "sparkles", busy: busy) { Task { await analyze(videoURL) } }
                        .disabled(!settings.hasKey)
                }
                if busy { Text(step).font(.callout).foregroundStyle(Theme.cyan) }
                if let error { ErrorCard(message: error) }
                if !result.isEmpty {
                    VStack(alignment: .leading) { MarkdownView(text: result); ResultActions(text: result, kind: "Video-Analyse") }.card()
                }

                Divider().overlay(Theme.cyan.opacity(0.3)).padding(.vertical, 6)

                Text("Diashow-Video erstellen").font(.headline)
                PhotosPicker(selection: $slidePicks, maxSelectionCount: 30, matching: .images) { Label("Fotos wählen (\(slidePicks.count))", systemImage: "photo.stack") }
                    .buttonStyle(.bordered).tint(Theme.cyan)
                HStack { Text("Sekunden pro Bild: \(Int(slideSeconds))"); Slider(value: $slideSeconds, in: 1...8, step: 1) }
                GradientButton(title: "Video erstellen", icon: "play.rectangle", busy: busy) { Task { await makeSlideshow() } }
                    .disabled(slidePicks.isEmpty)
                if let slideURL {
                    VideoPlayer(player: AVPlayer(url: slideURL)).frame(height: 220).clipShape(RoundedRectangle(cornerRadius: 12))
                    HStack {
                        ShareLink(item: slideURL) { Label("Teilen", systemImage: "square.and.arrow.up") }
                        Spacer()
                        Button { UISaveVideoAtPathToSavedPhotosAlbum(slideURL.path, nil, nil, nil) } label: { Label("In Fotos sichern", systemImage: "square.and.arrow.down") }
                    }
                }
            }
            .padding()
        }
        .onChange(of: pick) { _, item in
            Task { if let v = try? await item?.loadTransferable(type: VideoFile.self) { videoURL = v.url; result = "" } }
        }
        .fullScreenCover(isPresented: $showRecorder, onDismiss: { session.externalPickerActive = false }) {
            CameraPicker(video: true) { url in if let url { videoURL = url; result = "" } }.ignoresSafeArea()
        }
    }

    @MainActor
    private func analyze(_ url: URL) async {
        busy = true; error = nil; result = ""
        defer { busy = false }
        do {
            step = "Lese Einzelbilder …"
            let frames = await AttachmentLoader.videoFrames(url, count: 6)
            var transcript = ""
            if withAudio {
                step = "Transkribiere Ton …"
                if let audio = try? await AttachmentLoader.extractAudio(url), let data = try? Data(contentsOf: audio) {
                    transcript = (try? await MistralClient.shared.transcribe(key: settings.key, data: data, fileName: "audio.m4a", mime: "audio/mp4", language: nil)) ?? ""
                }
            }
            step = "KI analysiert …"
            var prompt = "\(question)\n\nDas Video ist durch \(frames.count) gleichmäßig verteilte Einzelbilder dargestellt."
            if !transcript.isEmpty { prompt += "\n\nTranskript des Tons:\n\(transcript.prefix(20_000))" }
            for try await chunk in MistralClient.shared.stream(key: settings.key, model: "mistral-small-latest",
                messages: [ApiMessage(role: "user", text: prompt, images: frames.compactMap { $0.dataURL })]) { result += chunk }
        } catch {
            ErrorLog.report(error, context: "Video-Analyse")
            self.error = friendly(error)
        }
    }

    @MainActor
    private func makeSlideshow() async {
        busy = true; error = nil; step = "Lade Fotos …"
        defer { busy = false }
        var images: [UIImage] = []
        for p in slidePicks {
            if let d = try? await p.loadTransferable(type: Data.self), let img = UIImage(data: d) { images.append(img) }
        }
        step = "Erstelle Video …"
        do {
            slideURL = try await Slideshow.make(images: images, secondsPerImage: slideSeconds)
        } catch {
            self.error = friendly(error)
        }
    }
}

enum Slideshow {
    static func make(images: [UIImage], secondsPerImage: Double, size: CGSize = CGSize(width: 1080, height: 1920)) async throws -> URL {
        let url = FileManager.default.temporaryDirectory.appendingPathComponent("KI-GenC-Diashow-\(Int(Date().timeIntervalSince1970)).mp4")
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: size.width, AVVideoHeightKey: size.height,
        ])
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32ARGB,
            kCVPixelBufferWidthKey as String: size.width, kCVPixelBufferHeightKey as String: size.height,
        ])
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? APIError(code: 0, message: "Video konnte nicht erstellt werden.") }
        writer.startSession(atSourceTime: .zero)
        let fps: Int32 = 30
        let framesPerImage = Int(secondsPerImage * Double(fps))
        var frame: Int64 = 0
        for img in images {
            guard let buffer = pixelBuffer(img, size: size) else { continue }
            for _ in 0..<framesPerImage {
                while !input.isReadyForMoreMediaData { try await Task.sleep(nanoseconds: 5_000_000) }
                adaptor.append(buffer, withPresentationTime: CMTime(value: frame, timescale: fps))
                frame += 1
            }
        }
        input.markAsFinished()
        await writer.finishWriting()
        guard writer.status == .completed else { throw writer.error ?? APIError(code: 0, message: "Video konnte nicht erstellt werden.") }
        return url
    }

    private static func pixelBuffer(_ image: UIImage, size: CGSize) -> CVPixelBuffer? {
        var pb: CVPixelBuffer?
        CVPixelBufferCreate(kCFAllocatorDefault, Int(size.width), Int(size.height), kCVPixelFormatType_32ARGB, nil, &pb)
        guard let pb else { return nil }
        CVPixelBufferLockBaseAddress(pb, [])
        defer { CVPixelBufferUnlockBaseAddress(pb, []) }
        guard let ctx = CGContext(data: CVPixelBufferGetBaseAddress(pb), width: Int(size.width), height: Int(size.height), bitsPerComponent: 8,
                                  bytesPerRow: CVPixelBufferGetBytesPerRow(pb), space: CGColorSpaceCreateDeviceRGB(),
                                  bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue) else { return nil }
        ctx.setFillColor(UIColor.black.cgColor)
        ctx.fill(CGRect(origin: .zero, size: size))
        let fixed = UIGraphicsImageRenderer(size: image.size).image { _ in image.draw(at: .zero) }
        guard let cg = fixed.cgImage else { return nil }
        let scale = min(size.width / fixed.size.width, size.height / fixed.size.height)
        let w = fixed.size.width * scale, h = fixed.size.height * scale
        ctx.draw(cg, in: CGRect(x: (size.width - w) / 2, y: (size.height - h) / 2, width: w, height: h))
        return pb
    }
}

// MARK: Transkription

final class AudioRecorder: NSObject, ObservableObject {
    @Published var recording = false
    @Published var seconds = 0
    private var recorder: AVAudioRecorder?
    private var timer: Timer?
    var url: URL { FileManager.default.temporaryDirectory.appendingPathComponent("aufnahme.m4a") }

    func toggle() {
        if recording { stop(); return }
        AVAudioApplication.requestRecordPermission { ok in
            guard ok else { return }
            DispatchQueue.main.async { self.start() }
        }
    }

    private func start() {
        try? AVAudioSession.sharedInstance().setCategory(.playAndRecord, mode: .default, options: .defaultToSpeaker)
        try? AVAudioSession.sharedInstance().setActive(true)
        let settings: [String: Any] = [AVFormatIDKey: kAudioFormatMPEG4AAC, AVSampleRateKey: 16_000, AVNumberOfChannelsKey: 1]
        recorder = try? AVAudioRecorder(url: url, settings: settings)
        recorder?.record()
        recording = recorder != nil
        seconds = 0
        timer = Timer.scheduledTimer(withTimeInterval: 1, repeats: true) { [weak self] _ in self?.seconds += 1 }
    }

    func stop() {
        recorder?.stop()
        timer?.invalidate()
        recording = false
    }
}

struct TranscribeView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var session: SessionController
    @StateObject private var recorder = AudioRecorder()
    @State private var showFiles = false
    @State private var pick: PhotosPickerItem?
    @State private var source: (URL, String)?
    @State private var language = ""
    @State private var transcript = ""
    @State private var summary = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Sprachaufnahme oder Audio-/Videodatei in Text umwandeln (Mistral Voxtral).").font(.callout).foregroundStyle(Theme.secondaryText)
                HStack {
                    Button { recorder.toggle(); if !recorder.recording { source = nil } } label: {
                        Label(recorder.recording ? "Stopp (\(recorder.seconds) s)" : "Aufnehmen", systemImage: recorder.recording ? "stop.circle.fill" : "mic.circle")
                    }
                    .tint(recorder.recording ? .red : Theme.cyan)
                    Button { session.externalPickerActive = true; showFiles = true } label: { Label("Datei", systemImage: "doc") }
                    PhotosPicker(selection: $pick, matching: .videos) { Label("Video", systemImage: "film") }
                }
                .buttonStyle(.bordered).tint(Theme.cyan)
                .onChange(of: recorder.recording) { _, rec in if !rec && recorder.seconds > 0 { source = (recorder.url, "Aufnahme") } }

                if let source { Label(source.1, systemImage: "waveform").foregroundStyle(Theme.cyan) }
                Picker("Sprache", selection: $language) {
                    Text("Automatisch").tag("")
                    Text("Deutsch").tag("de"); Text("Englisch").tag("en"); Text("Französisch").tag("fr")
                    Text("Spanisch").tag("es"); Text("Italienisch").tag("it"); Text("Niederländisch").tag("nl"); Text("Portugiesisch").tag("pt")
                }
                .tint(Theme.cyan)
                GradientButton(title: "Transkribieren", icon: "text.quote", busy: busy) { Task { await run() } }
                    .disabled(source == nil || !settings.hasKey || recorder.recording)
                if let error { ErrorCard(message: error) }
                if !transcript.isEmpty {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Transkript").font(.headline)
                        Text(transcript).textSelection(.enabled)
                        ResultActions(text: transcript, kind: "Transkript")
                        Button("Zusammenfassen") { Task { await summarize() } }.disabled(busy)
                    }
                    .card()
                }
                if !summary.isEmpty { VStack(alignment: .leading) { MarkdownView(text: summary); ResultActions(text: summary, kind: "Zusammenfassung") }.card() }
            }
            .padding()
        }
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.audio, .movie, .mpeg4Movie]) { r in
            session.externalPickerActive = false
            if case .success(let url) = r {
                let scoped = url.startAccessingSecurityScopedResource()
                let local = FileManager.default.temporaryDirectory.appendingPathComponent(url.lastPathComponent)
                try? FileManager.default.removeItem(at: local)
                try? FileManager.default.copyItem(at: url, to: local)
                if scoped { url.stopAccessingSecurityScopedResource() }
                source = (local, url.lastPathComponent)
            }
        }
        .onChange(of: pick) { _, item in
            Task { if let v = try? await item?.loadTransferable(type: VideoFile.self) { source = (v.url, "Video") } }
        }
    }

    @MainActor
    private func run() async {
        guard let src = source else { return }
        let (url, name) = src
        busy = true; error = nil; transcript = ""; summary = ""
        defer { busy = false }
        do {
            var file = url
            let ext = url.pathExtension.lowercased()
            if ["mov", "mp4", "m4v"].contains(ext) { file = try await AttachmentLoader.extractAudio(url) }
            let data = try Data(contentsOf: file)
            let mime: String = {
                switch file.pathExtension.lowercased() {
                case "mp3": return "audio/mpeg"
                case "wav": return "audio/wav"
                case "ogg": return "audio/ogg"
                case "flac": return "audio/flac"
                default: return "audio/mp4"
                }
            }()
            transcript = try await MistralClient.shared.transcribe(key: settings.key, data: data, fileName: file.lastPathComponent, mime: mime, language: language.isEmpty ? nil : language)
            if transcript.isEmpty { error = "In „\(name)“ wurde keine Sprache erkannt." }
        } catch {
            ErrorLog.report(error, context: "Transkription")
            self.error = friendly(error)
        }
    }

    @MainActor
    private func summarize() async {
        busy = true
        defer { busy = false }
        do {
            for try await c in MistralClient.shared.stream(key: settings.key, model: settings.chatModel, messages: [ApiMessage(role: "user", text: "Fasse dieses Transkript zusammen (Kernpunkte, Entscheidungen, Aufgaben):\n\n\(transcript.prefix(30_000))")]) { summary += c }
        } catch { self.error = friendly(error) }
    }
}

// MARK: Recherche

struct ResearchView: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var question = ""
    @State private var answer = ""
    @State private var sources: [(String, String)] = []
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                TextField("Was möchtest du recherchieren?", text: $question, axis: .vertical).lineLimit(2...5)
                    .padding(12).background(Theme.card, in: RoundedRectangle(cornerRadius: 14))
                GradientButton(title: "Recherchieren", icon: "globe", busy: busy) { Task { await run() } }
                    .disabled(question.trimmingCharacters(in: .whitespaces).isEmpty || !settings.hasKey)
                if let error { ErrorCard(message: error) }
                if !answer.isEmpty {
                    VStack(alignment: .leading, spacing: 10) {
                        MarkdownView(text: answer)
                        if !sources.isEmpty {
                            Text("Quellen").font(.headline)
                            ForEach(sources, id: \.1) { title, url in
                                if let u = URL(string: url) { Link(title, destination: u).font(.callout).lineLimit(2) }
                            }
                        }
                        ResultActions(text: answer + "\n\nQuellen:\n" + sources.map { "- \($0.0): \($0.1)" }.joined(separator: "\n"), kind: "Recherche")
                    }
                    .card()
                }
            }
            .padding()
        }
    }

    @MainActor
    private func run() async {
        busy = true; error = nil; answer = ""; sources = []
        defer { busy = false }
        do {
            (answer, sources) = try await MistralClient.shared.webSearch(key: settings.key, model: "mistral-medium-latest", question: question)
            if answer.isEmpty { throw APIError(code: 0, message: "Keine Antwort erhalten.") }
        } catch {
            // Ohne Websuche: Antwort aus dem Modellwissen
            do {
                answer = "_Websuche nicht verfügbar (\(friendly(error))) – Antwort aus dem Modellwissen:_\n\n"
                answer += try await MistralClient.shared.complete(key: settings.key, model: settings.chatModel, messages: [ApiMessage(role: "user", text: question)])
            } catch {
                ErrorLog.report(error, context: "Recherche")
                self.error = friendly(error)
                answer = ""
            }
        }
    }
}

// MARK: Daten & Statistik

struct DataToolView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var session: SessionController
    @State private var showFiles = false
    @State private var csv = ""
    @State private var name = ""
    @State private var stats = ""
    @State private var question = "Welche Muster, Trends und Auffälligkeiten gibt es? Gib eine Prognose."
    @State private var answer = ""
    @State private var error: String?
    @State private var busy = false

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Button { session.externalPickerActive = true; showFiles = true } label: { Label("CSV-/Textdatei wählen", systemImage: "tablecells") }
                    .buttonStyle(.bordered).tint(Theme.cyan)
                TextField("…oder Tabelle hier einfügen (Spalten mit ; , oder Tab)", text: $csv, axis: .vertical).lineLimit(4...10)
                    .font(.system(.footnote, design: .monospaced))
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                Button("Statistik berechnen") { stats = DataStats.describe(csv) }.disabled(csv.isEmpty)
                if !stats.isEmpty { Text(stats).font(.system(.footnote, design: .monospaced)).textSelection(.enabled).card() }
                TextField("Frage an die KI", text: $question, axis: .vertical).lineLimit(2...4)
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 12))
                GradientButton(title: "KI-Analyse", icon: "chart.bar.xaxis", busy: busy) { Task { await analyze() } }
                    .disabled(csv.isEmpty || !settings.hasKey)
                if let error { ErrorCard(message: error) }
                if !answer.isEmpty { VStack(alignment: .leading) { MarkdownView(text: answer); ResultActions(text: answer, kind: "Datenanalyse") }.card() }
            }
            .padding()
        }
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.commaSeparatedText, .tabSeparatedText, .plainText, .text]) { r in
            session.externalPickerActive = false
            if case .success(let url) = r, let a = try? AttachmentLoader.file(url) {
                csv = a.text; name = a.name; stats = DataStats.describe(csv)
            }
        }
    }

    @MainActor
    private func analyze() async {
        busy = true; error = nil; answer = ""
        if stats.isEmpty { stats = DataStats.describe(csv) }
        defer { busy = false }
        do {
            let prompt = "\(question)\n\nBerechnete Statistik:\n\(stats)\n\nDaten\(name.isEmpty ? "" : " (\(name))"):\n\(csv.prefix(25_000))"
            for try await c in MistralClient.shared.stream(key: settings.key, model: settings.chatModel, messages: [ApiMessage(role: "user", text: prompt)]) { answer += c }
        } catch {
            ErrorLog.report(error, context: "Datenanalyse")
            self.error = friendly(error)
        }
    }
}

enum DataStats {
    static func describe(_ text: String) -> String {
        let lines = text.split(whereSeparator: \.isNewline).map(String.init).filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        guard lines.count > 1 else { return "Zu wenige Zeilen." }
        let sep: Character = lines[0].contains(";") ? ";" : (lines[0].contains("\t") ? "\t" : ",")
        let header = lines[0].split(separator: sep, omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces.union(CharacterSet(charactersIn: "\""))) }
        let rows = lines.dropFirst().map { $0.split(separator: sep, omittingEmptySubsequences: false).map { $0.trimmingCharacters(in: .whitespaces.union(CharacterSet(charactersIn: "\""))) } }
        var out = "\(rows.count) Zeilen, \(header.count) Spalten\n"
        for (i, col) in header.enumerated() {
            let values = rows.compactMap { $0.count > i ? Double($0[i].replacingOccurrences(of: ",", with: ".")) : nil }
            if values.count >= max(1, rows.count / 2) {
                let sorted = values.sorted()
                let mean = values.reduce(0, +) / Double(values.count)
                let median = sorted.count % 2 == 0 ? (sorted[sorted.count / 2 - 1] + sorted[sorted.count / 2]) / 2 : sorted[sorted.count / 2]
                let sd = sqrt(values.map { ($0 - mean) * ($0 - mean) }.reduce(0, +) / Double(max(1, values.count - 1)))
                out += "\n\(col): n=\(values.count) Ø=\(fmt(mean)) Median=\(fmt(median)) Min=\(fmt(sorted.first!)) Max=\(fmt(sorted.last!)) σ=\(fmt(sd)) Summe=\(fmt(values.reduce(0, +)))"
            } else {
                let distinct = Set(rows.compactMap { $0.count > i ? $0[i] : nil })
                out += "\n\(col): Text, \(distinct.count) verschiedene Werte"
            }
        }
        return out
    }

    private static func fmt(_ d: Double) -> String { String(format: abs(d) >= 1000 ? "%.0f" : "%.2f", d) }
}

// MARK: Aufgaben

struct TasksView: View {
    @EnvironmentObject private var settings: AppSettings
    @ObservedObject private var store = TaskStore.shared
    @State private var title = ""
    @State private var due = Date().addingTimeInterval(3600)
    @State private var hasDue = true
    @State private var planText = ""
    @State private var busy = false
    @State private var error: String?

    var body: some View {
        List {
            Section("Neue Aufgabe") {
                TextField("Titel", text: $title)
                Toggle("Erinnern", isOn: $hasDue)
                if hasDue { DatePicker("Fällig", selection: $due) }
                Button("Hinzufügen") {
                    store.add(TaskItem(title: title, due: hasDue ? due : nil)); title = ""
                }.disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
            }
            .listRowBackground(Theme.card)
            Section("KI-Planer") {
                TextField("z. B. „Umzug nächsten Monat vorbereiten“", text: $planText, axis: .vertical)
                Button { Task { await plan() } } label: { HStack { if busy { ProgressView() }; Text("Aufgaben vorschlagen") } }
                    .disabled(planText.isEmpty || busy || !settings.hasKey)
                if let error { Text(error).foregroundStyle(.orange).font(.caption) }
            }
            .listRowBackground(Theme.card)
            Section("Aufgaben (\(store.tasks.filter { !$0.done }.count) offen)") {
                ForEach(store.tasks) { t in
                    HStack {
                        Button { store.toggle(t) } label: { Image(systemName: t.done ? "checkmark.circle.fill" : "circle").foregroundStyle(Theme.cyan) }
                            .buttonStyle(.plain)
                        VStack(alignment: .leading) {
                            Text(t.title).strikethrough(t.done).foregroundStyle(t.done ? Theme.secondaryText : .white)
                            if let d = t.due { Text(d.formatted(date: .abbreviated, time: .shortened)).font(.caption).foregroundStyle(d < Date() && !t.done ? .orange : Theme.secondaryText) }
                        }
                    }
                }
                .onDelete { idx in idx.map { store.tasks[$0] }.forEach(store.delete) }
            }
            .listRowBackground(Theme.card)
        }
        .clearListBackground()
    }

    @MainActor
    private func plan() async {
        busy = true; error = nil
        defer { busy = false }
        do {
            let today = ISO8601DateFormatter().string(from: Date())
            let json = try await MistralClient.shared.complete(key: settings.key, model: settings.chatModel, messages: [ApiMessage(role: "user", text:
                "Heute ist \(today). Zerlege dieses Vorhaben in 3–10 konkrete Aufgaben mit sinnvollem Fälligkeitsdatum. Antworte nur als JSON: {\"tasks\":[{\"title\":\"…\",\"due\":\"YYYY-MM-DDTHH:MM\"}]}\n\nVorhaben: \(planText)")], json: true)
            guard let d = json.data(using: .utf8), let o = try JSONSerialization.jsonObject(with: d) as? [String: Any], let arr = o["tasks"] as? [[String: Any]] else {
                throw APIError(code: 0, message: "Die KI hat keine Aufgabenliste geliefert.")
            }
            let f = DateFormatter(); f.dateFormat = "yyyy-MM-dd'T'HH:mm"
            for t in arr {
                if let title = t["title"] as? String { store.add(TaskItem(title: title, due: (t["due"] as? String).flatMap { f.date(from: String($0.prefix(16))) })) }
            }
            planText = ""
        } catch {
            self.error = friendly(error)
        }
    }
}

// MARK: Ergebnisse

struct ResultsView: View {
    @ObservedObject private var store = ResultStore.shared

    var body: some View {
        List {
            if store.results.isEmpty {
                Text("Noch nichts gespeichert. Tippe bei einem Ergebnis auf das Lesezeichen.").foregroundStyle(Theme.secondaryText).listRowBackground(Theme.card)
            }
            ForEach(store.results) { r in
                NavigationLink {
                    ScrollView {
                        VStack(alignment: .leading, spacing: 12) {
                            if let img = store.image(for: r) { Image(uiImage: img).resizable().scaledToFit().clipShape(RoundedRectangle(cornerRadius: 12)) }
                            MarkdownView(text: r.text)
                            ResultActions(text: r.text, image: store.image(for: r), kind: r.kind)
                        }
                        .padding()
                    }
                    .background(Theme.backgroundGradient.ignoresSafeArea())
                    .navigationTitle(r.kind)
                } label: {
                    HStack {
                        if let img = store.image(for: r) { Image(uiImage: img).resizable().scaledToFill().frame(width: 44, height: 44).clipShape(RoundedRectangle(cornerRadius: 8)) }
                        VStack(alignment: .leading) {
                            Text(r.title).lineLimit(1)
                            Text("\(r.kind) · \(r.date.formatted(date: .abbreviated, time: .shortened))").font(.caption).foregroundStyle(Theme.secondaryText)
                        }
                    }
                }
                .listRowBackground(Theme.card)
            }
            .onDelete { idx in idx.map { store.results[$0] }.forEach(store.delete) }
        }
        .clearListBackground()
    }
}
