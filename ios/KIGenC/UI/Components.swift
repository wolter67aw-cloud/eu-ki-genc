import SwiftUI
import PhotosUI
import UniformTypeIdentifiers

// MARK: Markdown mit Codeblöcken

struct MarkdownView: View {
    let text: String

    private struct Block: Identifiable {
        let id = UUID()
        let isCode: Bool
        let language: String
        let content: String
    }

    private var blocks: [Block] {
        var result: [Block] = []
        let parts = text.components(separatedBy: "```")
        for (i, part) in parts.enumerated() {
            if i % 2 == 1 {
                var lines = part.components(separatedBy: "\n")
                let lang = lines.first?.trimmingCharacters(in: .whitespaces) ?? ""
                if !lines.isEmpty { lines.removeFirst() }
                result.append(Block(isCode: true, language: lang, content: lines.joined(separator: "\n").trimmingCharacters(in: .newlines)))
            } else if !part.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                result.append(Block(isCode: false, language: "", content: part.trimmingCharacters(in: .newlines)))
            }
        }
        return result
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            ForEach(blocks) { b in
                if b.isCode {
                    CodeBlock(code: b.content, language: b.language)
                } else {
                    ForEach(Array(b.content.components(separatedBy: "\n").enumerated()), id: \.offset) { _, line in
                        lineView(line)
                    }
                }
            }
        }
        .textSelection(.enabled)
    }

    @ViewBuilder
    private func lineView(_ raw: String) -> some View {
        let line = raw.trimmingCharacters(in: .whitespaces)
        if line.isEmpty {
            Spacer().frame(height: 2)
        } else if line.hasPrefix("### ") {
            Text(inline(String(line.dropFirst(4)))).font(.headline)
        } else if line.hasPrefix("## ") {
            Text(inline(String(line.dropFirst(3)))).font(.title3.bold())
        } else if line.hasPrefix("# ") {
            Text(inline(String(line.dropFirst(2)))).font(.title2.bold())
        } else if line.hasPrefix("- ") || line.hasPrefix("* ") || line.hasPrefix("• ") {
            HStack(alignment: .firstTextBaseline, spacing: 6) {
                Text("•").foregroundStyle(Theme.cyan)
                Text(inline(String(line.dropFirst(2))))
            }
            .padding(.leading, raw.hasPrefix("  ") ? 16 : 0)
        } else if line.hasPrefix("> ") {
            Text(inline(String(line.dropFirst(2)))).italic().foregroundStyle(Theme.secondaryText)
                .padding(.leading, 10)
                .overlay(alignment: .leading) { Rectangle().fill(Theme.cyan).frame(width: 3) }
        } else if line.hasPrefix("|") {
            Text(line.replacingOccurrences(of: "|", with: "  ").trimmingCharacters(in: .whitespaces))
                .font(.system(.footnote, design: .monospaced))
                .opacity(line.contains("---") ? 0.3 : 1)
        } else {
            Text(inline(raw))
        }
    }

    private func inline(_ s: String) -> AttributedString {
        (try? AttributedString(markdown: s, options: .init(interpretedSyntax: .inlineOnlyPreservingWhitespace))) ?? AttributedString(s)
    }
}

struct CodeBlock: View {
    let code: String
    let language: String
    @State private var copied = false

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(language.isEmpty ? "Code" : language).font(.caption.bold()).foregroundStyle(Theme.cyan)
                Spacer()
                Button {
                    UIPasteboard.general.string = code
                    copied = true
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) { copied = false }
                } label: {
                    Label(copied ? "Kopiert" : "Kopieren", systemImage: copied ? "checkmark" : "doc.on.doc").font(.caption)
                }
            }
            .padding(.horizontal, 10).padding(.vertical, 6)
            .background(Color.black.opacity(0.35))
            ScrollView(.horizontal, showsIndicators: false) {
                Text(code).font(.system(.footnote, design: .monospaced)).padding(10)
            }
        }
        .background(Color.black.opacity(0.5), in: RoundedRectangle(cornerRadius: 10))
        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.cyan.opacity(0.25)))
    }
}

// MARK: Kleine Bausteine

struct GradientButton: View {
    let title: String
    var icon: String? = nil
    var busy = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if busy { ProgressView().tint(.white) } else if let icon { Image(systemName: icon) }
                Text(title).bold()
            }
            .frame(maxWidth: .infinity).padding(.vertical, 13)
            .background(Theme.accentGradient, in: RoundedRectangle(cornerRadius: 14))
            .foregroundStyle(.white)
        }
        .disabled(busy)
        .opacity(busy ? 0.75 : 1)
    }
}

struct ErrorCard: View {
    let message: String
    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "exclamationmark.triangle.fill").foregroundStyle(.orange)
            Text(message).font(.callout).textSelection(.enabled)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color.orange.opacity(0.15), in: RoundedRectangle(cornerRadius: 12))
    }
}

struct ResultActions: View {
    let text: String
    var image: UIImage? = nil
    var kind = "Ergebnis"
    @State private var saved = false
    @ObservedObject private var speaker = Speaker.shared

    var body: some View {
        HStack(spacing: 18) {
            Button { UIPasteboard.general.string = text } label: { Image(systemName: "doc.on.doc") }
            if let image {
                ShareLink(item: Image(uiImage: image), preview: SharePreview("KI-GenC Bild", image: Image(uiImage: image))) { Image(systemName: "square.and.arrow.up") }
                Button { UIImageWriteToSavedPhotosAlbum(image, nil, nil, nil); saved = true } label: { Image(systemName: saved ? "checkmark" : "square.and.arrow.down") }
            } else if !text.isEmpty {
                ShareLink(item: text) { Image(systemName: "square.and.arrow.up") }
                Button { speaker.speaking ? speaker.stop() : speaker.speak(text) } label: { Image(systemName: speaker.speaking ? "stop.circle" : "speaker.wave.2") }
            }
            Button {
                ResultStore.shared.add(kind: kind, title: text.isEmpty ? kind : text, text: text, image: image?.jpegData(compressionQuality: 0.9))
                saved = true
            } label: { Image(systemName: saved ? "bookmark.fill" : "bookmark") }
        }
        .font(.callout)
        .foregroundStyle(Theme.cyan)
    }
}

/// Leiste zum Anhängen von Fotos, Videos und Dateien (mehrere auf einmal).
struct AttachmentBar: View {
    @Binding var attachments: [Attachment]
    var allowFiles = true
    var maxItems = 8
    @EnvironmentObject private var session: SessionController
    @State private var picks: [PhotosPickerItem] = []
    @State private var showFiles = false
    @State private var showCamera = false
    @State private var loading = false
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 10) {
                PhotosPicker(selection: $picks, maxSelectionCount: maxItems, matching: .any(of: [.images, .videos])) {
                    Label("Fotos/Videos", systemImage: "photo.on.rectangle.angled")
                }
                Button { session.externalPickerActive = true; showCamera = true } label: { Label("Kamera", systemImage: "camera") }
                if allowFiles {
                    Button { session.externalPickerActive = true; showFiles = true } label: { Label("Datei", systemImage: "paperclip") }
                }
                if loading { ProgressView() }
            }
            .font(.callout)
            .buttonStyle(.bordered)
            .tint(Theme.cyan)

            if !attachments.isEmpty {
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(attachments) { a in
                            ZStack(alignment: .topTrailing) {
                                Group {
                                    if let img = a.images.first {
                                        Image(uiImage: img).resizable().scaledToFill()
                                    } else {
                                        VStack(spacing: 4) {
                                            Image(systemName: a.icon).font(.title3)
                                            Text(a.name).font(.caption2).lineLimit(2).multilineTextAlignment(.center)
                                        }.padding(4)
                                    }
                                }
                                .frame(width: 64, height: 64)
                                .background(Theme.cardHigh)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                                .overlay(alignment: .bottomLeading) {
                                    if a.kind == .video { Image(systemName: "film").font(.caption2).padding(3).background(.black.opacity(0.6), in: Circle()).padding(3) }
                                }
                                Button { attachments.removeAll { $0.id == a.id } } label: {
                                    Image(systemName: "xmark.circle.fill").foregroundStyle(.white, .black.opacity(0.7))
                                }
                                .offset(x: 5, y: -5)
                            }
                        }
                    }
                    .padding(.top, 5)
                }
            }
            if let error { Text(error).font(.caption).foregroundStyle(.orange) }
        }
        .onChange(of: picks) { _, items in
            guard !items.isEmpty else { return }
            Task { await load(items) }
        }
        .fileImporter(isPresented: $showFiles, allowedContentTypes: [.pdf, .plainText, .commaSeparatedText, .json, .data, .item], allowsMultipleSelection: true) { result in
            session.externalPickerActive = false
            switch result {
            case .success(let urls):
                Task { await loadFiles(urls) }
            case .failure(let e):
                error = e.localizedDescription
            }
        }
        .fullScreenCover(isPresented: $showCamera, onDismiss: { session.externalPickerActive = false }) {
            CameraPicker { img in
                if let img { attachments.append(Attachment(kind: .image, name: "Kamera", images: [img])) }
            }
            .ignoresSafeArea()
        }
    }

    @MainActor
    private func load(_ items: [PhotosPickerItem]) async {
        loading = true
        error = nil
        for item in items where attachments.count < maxItems {
            if item.supportedContentTypes.contains(where: { $0.conforms(to: .movie) }) {
                if let v = try? await item.loadTransferable(type: VideoFile.self) {
                    attachments.append(await AttachmentLoader.video(v.url, name: "Video"))
                } else { error = "Ein Video konnte nicht geladen werden." }
            } else if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                attachments.append(Attachment(kind: .image, name: "Foto", images: [img]))
            } else {
                error = "Ein Foto konnte nicht geladen werden."
            }
        }
        picks = []
        loading = false
    }

    @MainActor
    private func loadFiles(_ urls: [URL]) async {
        loading = true
        for url in urls where attachments.count < maxItems {
            do {
                var a = try AttachmentLoader.file(url)
                if a.kind == .video, let f = a.fileURL { a = await AttachmentLoader.video(f, name: a.name) }
                attachments.append(a)
            } catch {
                self.error = friendly(error)
            }
        }
        loading = false
    }
}

/// Kamera (UIImagePickerController) für Fotos.
struct CameraPicker: UIViewControllerRepresentable {
    var video = false
    var onImage: ((UIImage?) -> Void)? = nil
    var onVideo: ((URL?) -> Void)? = nil
    @Environment(\.dismiss) private var dismiss

    init(onImage: @escaping (UIImage?) -> Void) { self.onImage = onImage }
    init(video: Bool, onVideo: @escaping (URL?) -> Void) { self.video = video; self.onVideo = onVideo }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let p = UIImagePickerController()
        p.sourceType = UIImagePickerController.isSourceTypeAvailable(.camera) ? .camera : .photoLibrary
        if video {
            p.mediaTypes = [UTType.movie.identifier]
            p.videoQuality = .typeHigh
            p.videoMaximumDuration = 300
        }
        p.delegate = context.coordinator
        return p
    }

    func updateUIViewController(_ vc: UIImagePickerController, context: Context) {}
    func makeCoordinator() -> Coordinator { Coordinator(self) }

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let parent: CameraPicker
        init(_ p: CameraPicker) { parent = p }

        func imagePickerController(_ picker: UIImagePickerController, didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]) {
            if parent.video {
                parent.onVideo?(info[.mediaURL] as? URL)
            } else {
                parent.onImage?(info[.originalImage] as? UIImage)
            }
            parent.dismiss()
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            parent.onImage?(nil)
            parent.onVideo?(nil)
            parent.dismiss()
        }
    }
}

struct NeedsKeyView: View {
    var body: some View {
        VStack(spacing: 10) {
            Image(systemName: "key.fill").font(.largeTitle).foregroundStyle(Theme.cyan)
            Text("Mistral-Schlüssel fehlt").font(.headline)
            Text("Trage deinen kostenlosen API-Schlüssel unter „Optionen“ ein (console.mistral.ai › API Keys).")
                .font(.callout).multilineTextAlignment(.center).foregroundStyle(Theme.secondaryText)
        }
        .padding().card().padding()
    }
}
