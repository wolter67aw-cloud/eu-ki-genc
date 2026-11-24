import SwiftUI
import PhotosUI
import AVFoundation
import AVKit
import PDFKit
import UniformTypeIdentifiers

// MARK: Werbevideo – kurze Clips aus eigenen Fotos, Videos und Dateien, mit KI-Werbetexten

enum PromoFormat: String, CaseIterable, Identifiable {
    case portrait, square, landscape
    var id: String { rawValue }
    var label: String {
        switch self {
        case .portrait: return "9:16"
        case .square: return "1:1"
        case .landscape: return "16:9"
        }
    }
    var title: String {
        switch self {
        case .portrait: return "Hochformat 9:16"
        case .square: return "Quadrat 1:1"
        case .landscape: return "Querformat 16:9"
        }
    }
    var size: CGSize {
        switch self {
        case .portrait: return CGSize(width: 720, height: 1280)
        case .square: return CGSize(width: 720, height: 720)
        case .landscape: return CGSize(width: 1280, height: 720)
        }
    }
}

struct PromoScene: Identifiable {
    enum Kind {
        /// Foto oder PDF-Seite (fit = vollständig zeigen, z. B. bei PDF-Seiten)
        case image(UIImage, fit: Bool)
        case clip(URL, duration: Double)
    }
    let id = UUID()
    var kind: Kind
    var label: String
    var caption = ""
    var thumb: UIImage?
}

struct PromoDoc: Identifiable {
    let id = UUID()
    var name: String
    var text: String
}

struct PromoEndCard {
    var name: String
    var cta: String
    var contact: String
    var colorIndex: Int
}

enum PromoRenderer {
    static let fps: Int32 = 30
    static let fade = 0.5
    static let endDuration = 3.0
    static let maxScenes = 20

    /// Farben der Abschluss-Karte (Name, oben, unten).
    static let colors: [(String, UIColor, UIColor)] = [
        ("Nachtblau", UIColor(red: 0.02, green: 0.06, blue: 0.23, alpha: 1), UIColor(red: 0.23, green: 0.48, blue: 0.95, alpha: 1)),
        ("Violett", UIColor(red: 0.08, green: 0.04, blue: 0.24, alpha: 1), UIColor(red: 0.55, green: 0.36, blue: 0.96, alpha: 1)),
        ("Rot", UIColor(red: 0.29, green: 0.04, blue: 0.04, alpha: 1), UIColor(red: 0.90, green: 0.22, blue: 0.21, alpha: 1)),
        ("Grün", UIColor(red: 0.02, green: 0.16, blue: 0.10, alpha: 1), UIColor(red: 0.18, green: 0.62, blue: 0.36, alpha: 1)),
        ("Gold", UIColor(red: 0.17, green: 0.11, blue: 0.0, alpha: 1), UIColor(red: 0.83, green: 0.63, blue: 0.09, alpha: 1)),
        ("Schwarz", UIColor.black, UIColor(white: 0.23, alpha: 1)),
    ]

    static func duration(_ s: PromoScene, photo: Double, clip: Double) -> Double {
        switch s.kind {
        case .image: return photo
        case .clip(_, let d): return max(1, min(clip, d > 0 ? d : clip))
        }
    }

    static func total(scenes: [PromoScene], photo: Double, clip: Double, endCard: Bool) -> Double {
        var d = scenes.map { duration($0, photo: photo, clip: clip) }
        if endCard { d.append(endDuration) }
        guard !d.isEmpty else { return 0 }
        return d.reduce(0, +) - fade * Double(d.count - 1)
    }

    private enum Part {
        case scene(PromoScene, Double, Int)
        case end(PromoEndCard)
        var duration: Double {
            switch self {
            case .scene(_, let d, _): return d
            case .end: return PromoRenderer.endDuration
            }
        }
    }

    /// Rendert das Werbevideo Bild für Bild (H.264, ohne Ton).
    static func render(format: PromoFormat, scenes: [PromoScene], photo: Double, clip: Double, slogan: String,
                       endCard: PromoEndCard?, progress: @escaping (Double) -> Void) async throws -> URL {
        let size = format.size
        let w = Int(size.width), h = Int(size.height)
        var parts: [Part] = scenes.enumerated().map { .scene($1, duration($1, photo: photo, clip: clip), $0) }
        if let endCard { parts.append(.end(endCard)) }
        guard !parts.isEmpty else { throw APIError(code: 0, message: "Keine Bilder oder Clips gewählt.") }
        var starts: [Double] = []
        var t = 0.0
        for (i, p) in parts.enumerated() {
            starts.append(t)
            t += p.duration - (i < parts.count - 1 ? fade : 0)
        }
        let totalFrames = max(1, Int(t * Double(fps)))

        let url = FileManager.default.temporaryDirectory.appendingPathComponent("KI-GenC-Werbevideo-\(Int(Date().timeIntervalSince1970)).mp4")
        try? FileManager.default.removeItem(at: url)
        let writer = try AVAssetWriter(outputURL: url, fileType: .mp4)
        let input = AVAssetWriterInput(mediaType: .video, outputSettings: [
            AVVideoCodecKey: AVVideoCodecType.h264, AVVideoWidthKey: w, AVVideoHeightKey: h,
            AVVideoCompressionPropertiesKey: [AVVideoAverageBitRateKey: w == h ? 4_000_000 : 6_000_000],
        ])
        input.expectsMediaDataInRealTime = false
        let adaptor = AVAssetWriterInputPixelBufferAdaptor(assetWriterInput: input, sourcePixelBufferAttributes: [
            kCVPixelBufferPixelFormatTypeKey as String: kCVPixelFormatType_32BGRA,
            kCVPixelBufferWidthKey as String: w, kCVPixelBufferHeightKey as String: h,
        ])
        writer.add(input)
        guard writer.startWriting() else { throw writer.error ?? APIError(code: 0, message: "Video konnte nicht erstellt werden.") }
        writer.startSession(atSourceTime: .zero)

        var stills: [Int: CGImage] = [:]
        var clips: [Int: ClipReader] = [:]
        let text = TextPainter(size: size)

        func drawPart(_ ctx: CGContext, _ i: Int, local: Double, alpha: CGFloat, textAlpha: CGFloat) async {
            switch parts[i] {
            case .scene(let scene, let d, let index):
                switch scene.kind {
                case .image(let img, let fit):
                    if stills[i] == nil { stills[i] = prepareStill(img, size: size, fit: fit) }
                    if let cg = stills[i] {
                        let p = CGFloat(min(1, max(0, local / d)))
                        let zoomIn = index % 2 == 0
                        let s = zoomIn ? 1 + 0.10 * p : 1.10 - 0.10 * p
                        let fx = [0.5, 0.3, 0.7, 0.5][index % 4], fy = [0.4, 0.5, 0.5, 0.6][index % 4]
                        let cx = size.width * fx, cy = size.height * fy
                        ctx.saveGState()
                        ctx.setAlpha(alpha)
                        ctx.translateBy(x: cx, y: cy)
                        ctx.scaleBy(x: s, y: s)
                        ctx.translateBy(x: -cx, y: -cy)
                        UIImage(cgImage: cg).draw(in: CGRect(origin: .zero, size: size))
                        ctx.restoreGState()
                    }
                case .clip(let url, let dur):
                    if clips[i] == nil { clips[i] = ClipReader(url: url, maxSide: max(size.width, size.height)) }
                    let at = dur > 0 ? min(local, max(0, dur - 0.05)) : local
                    if let cg = await clips[i]?.frame(at: at) {
                        let iw = CGFloat(cg.width), ih = CGFloat(cg.height)
                        let sc = max(size.width / iw, size.height / ih)
                        let dw = iw * sc, dh = ih * sc
                        ctx.saveGState()
                        ctx.setAlpha(alpha)
                        UIImage(cgImage: cg).draw(in: CGRect(x: (size.width - dw) / 2, y: (size.height - dh) / 2, width: dw, height: dh))
                        ctx.restoreGState()
                    }
                }
                let a = CGFloat(min(1, max(0, local / 0.4))) * textAlpha
                if index == 0, !slogan.trimmingCharacters(in: .whitespaces).isEmpty {
                    text.slogan(slogan.trimmingCharacters(in: .whitespacesAndNewlines), alpha: a, local: local)
                }
                let cap = scene.caption.trimmingCharacters(in: .whitespacesAndNewlines)
                if !cap.isEmpty { text.caption(cap, alpha: a) }
            case .end(let card):
                drawEnd(ctx, card: card, size: size, local: local, alpha: alpha)
            }
        }

        for f in 0..<totalFrames {
            try Task.checkCancellation()
            let tSec = Double(f) / Double(fps)
            var i = 0
            while i < parts.count - 1 && starts[i + 1] <= tSec { i += 1 }
            // Nicht mehr benötigte Abschnitte freigeben
            for k in 0..<max(0, i - 1) { stills[k] = nil; clips[k] = nil }

            while !input.isReadyForMoreMediaData { try await Task.sleep(nanoseconds: 3_000_000) }
            guard let pool = adaptor.pixelBufferPool else { throw writer.error ?? APIError(code: 0, message: "Video-Puffer nicht verfügbar.") }
            var pbOut: CVPixelBuffer?
            CVPixelBufferPoolCreatePixelBuffer(nil, pool, &pbOut)
            guard let pb = pbOut else { continue }
            CVPixelBufferLockBaseAddress(pb, [])
            guard let ctx = CGContext(data: CVPixelBufferGetBaseAddress(pb), width: w, height: h, bitsPerComponent: 8,
                                      bytesPerRow: CVPixelBufferGetBytesPerRow(pb), space: CGColorSpaceCreateDeviceRGB(),
                                      bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else {
                CVPixelBufferUnlockBaseAddress(pb, [])
                continue
            }
            // UIKit-Koordinaten (Ursprung oben links)
            ctx.translateBy(x: 0, y: CGFloat(h))
            ctx.scaleBy(x: 1, y: -1)
            UIGraphicsPushContext(ctx)
            ctx.setFillColor(UIColor.black.cgColor)
            ctx.fill(CGRect(origin: .zero, size: size))
            if i > 0 && tSec < starts[i] + fade {
                let p = CGFloat((tSec - starts[i]) / fade)
                await drawPart(ctx, i - 1, local: tSec - starts[i - 1], alpha: 1, textAlpha: 1 - p)
                await drawPart(ctx, i, local: tSec - starts[i], alpha: p, textAlpha: p)
            } else {
                if i > 0 { stills[i - 1] = nil; clips[i - 1] = nil }
                await drawPart(ctx, i, local: tSec - starts[i], alpha: 1, textAlpha: 1)
            }
            UIGraphicsPopContext()
            CVPixelBufferUnlockBaseAddress(pb, [])
            adaptor.append(pb, withPresentationTime: CMTime(value: Int64(f), timescale: fps))
            if f % 6 == 0 || f == totalFrames - 1 { progress(Double(f + 1) / Double(totalFrames)) }
        }
        input.markAsFinished()
        await writer.finishWriting()
        guard writer.status == .completed else { throw writer.error ?? APIError(code: 0, message: "Video konnte nicht erstellt werden.") }
        return url
    }

    /// Liest Einzelbilder eines Videoclips (ohne Ton).
    private final class ClipReader {
        let gen: AVAssetImageGenerator
        var last: CGImage?
        var lastTime = -1.0
        init(url: URL, maxSide: CGFloat) {
            gen = AVAssetImageGenerator(asset: AVURLAsset(url: url))
            gen.appliesPreferredTrackTransform = true
            gen.maximumSize = CGSize(width: maxSide, height: maxSide)
            let tol = CMTime(value: 1, timescale: 60)
            gen.requestedTimeToleranceBefore = tol
            gen.requestedTimeToleranceAfter = tol
        }
        func frame(at seconds: Double) async -> CGImage? {
            if abs(seconds - lastTime) < 0.001 { return last }
            if let (cg, _) = try? await gen.image(at: CMTime(seconds: seconds, preferredTimescale: 600)) { last = cg }
            lastTime = seconds
            return last
        }
    }

    /// Bild auf Videogröße: füllend – bei stark abweichendem Format (oder PDF) vollständig auf unscharfem Hintergrund.
    static func prepareStill(_ image: UIImage, size: CGSize, fit: Bool) -> CGImage? {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let isz = image.size
        guard isz.width > 0, isz.height > 0 else { return nil }
        let srcRatio = isz.width / isz.height, dstRatio = size.width / size.height
        let mismatch = max(srcRatio / dstRatio, dstRatio / srcRatio)
        let cover = max(size.width / isz.width, size.height / isz.height)
        func rect(_ s: CGFloat) -> CGRect {
            CGRect(x: (size.width - isz.width * s) / 2, y: (size.height - isz.height * s) / 2, width: isz.width * s, height: isz.height * s)
        }
        let img = UIGraphicsImageRenderer(size: size, format: format).image { c in
            UIColor.black.setFill()
            c.fill(CGRect(origin: .zero, size: size))
            if !fit && mismatch < 1.45 {
                image.draw(in: rect(cover))
            } else {
                // Unscharfer Hintergrund: stark verkleinert und wieder vergrößert
                let small = image.scaled(maxSide: 40)
                c.cgContext.interpolationQuality = .high
                small.draw(in: rect(cover))
                UIColor.black.withAlphaComponent(0.6).setFill()
                c.fill(CGRect(origin: .zero, size: size))
                image.draw(in: rect(min(size.width / isz.width, size.height / isz.height) * 0.94))
            }
        }
        return img.cgImage
    }

    private static func drawEnd(_ ctx: CGContext, card: PromoEndCard, size: CGSize, local: Double, alpha: CGFloat) {
        let c = colors.indices.contains(card.colorIndex) ? colors[card.colorIndex] : colors[0]
        ctx.saveGState()
        ctx.setAlpha(alpha)
        if let g = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: [c.1.cgColor, c.2.cgColor] as CFArray, locations: [0, 1]) {
            ctx.drawLinearGradient(g, start: .zero, end: CGPoint(x: size.width * 0.3, y: size.height), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        let p = CGFloat(min(1, max(0, local / 0.5)))
        let scale = 0.92 + 0.08 * p
        ctx.translateBy(x: size.width / 2, y: size.height / 2)
        ctx.scaleBy(x: scale, y: scale)
        ctx.translateBy(x: -size.width / 2, y: -size.height / 2)
        let minSide = min(size.width, size.height)
        let gap = minSide * 0.05
        let shadow = NSShadow()
        shadow.shadowColor = UIColor.black.withAlphaComponent(0.5)
        shadow.shadowBlurRadius = 8
        shadow.shadowOffset = CGSize(width: 0, height: 3)
        let name = TextPainter.attributed(card.name, size: minSide * 0.09, bold: true, color: UIColor.white.withAlphaComponent(p), shadow: shadow)
        let cta = TextPainter.attributed(card.cta, size: minSide * 0.055, bold: true, color: c.1.withAlphaComponent(p), shadow: nil)
        let contact = TextPainter.attributed(card.contact, size: minSide * 0.045, bold: false, color: UIColor.white.withAlphaComponent(p), shadow: nil)
        let width = size.width * 0.84
        func box(_ s: NSAttributedString, _ wdt: CGFloat) -> CGRect {
            s.boundingRect(with: CGSize(width: wdt, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).integral
        }
        let padH = minSide * 0.05, padV = minSide * 0.025
        var blocks: [(NSAttributedString, CGRect, Bool)] = []
        if card.name.trimmingCharacters(in: .whitespaces).count > 0 { blocks.append((name, box(name, width), false)) }
        if card.cta.trimmingCharacters(in: .whitespaces).count > 0 { blocks.append((cta, box(cta, size.width * 0.7), true)) }
        if card.contact.trimmingCharacters(in: .whitespaces).count > 0 { blocks.append((contact, box(contact, width), false)) }
        let total = blocks.reduce(CGFloat(0)) { $0 + $1.1.height + ($1.2 ? 2 * padV : 0) } + gap * CGFloat(max(0, blocks.count - 1))
        var y = (size.height - total) / 2
        for (s, r, pill) in blocks {
            if pill {
                let pr = CGRect(x: size.width / 2 - r.width / 2 - padH, y: y, width: r.width + 2 * padH, height: r.height + 2 * padV)
                UIColor.white.withAlphaComponent(p).setFill()
                UIBezierPath(roundedRect: pr, cornerRadius: pr.height / 2).fill()
                s.draw(with: CGRect(x: size.width / 2 - r.width / 2, y: y + padV, width: r.width, height: r.height), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
                y += pr.height + gap
            } else {
                let wdt = s === cta ? size.width * 0.7 : width
                s.draw(with: CGRect(x: (size.width - wdt) / 2, y: y, width: wdt, height: r.height), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
                y += r.height + gap
            }
        }
        ctx.restoreGState()
    }

    /// Slogan (groß, Mitte) und Einblendungen (unten auf dunklem Band).
    final class TextPainter {
        let size: CGSize
        let minSide: CGFloat
        init(size: CGSize) { self.size = size; minSide = min(size.width, size.height) }

        static func attributed(_ text: String, size: CGFloat, bold: Bool, color: UIColor, shadow: NSShadow?) -> NSAttributedString {
            let para = NSMutableParagraphStyle()
            para.alignment = .center
            para.lineBreakMode = .byWordWrapping
            var attrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: size, weight: bold ? .bold : .regular),
                .foregroundColor: color, .paragraphStyle: para,
            ]
            if let shadow { attrs[.shadow] = shadow }
            return NSAttributedString(string: text.trimmingCharacters(in: .whitespacesAndNewlines), attributes: attrs)
        }

        func caption(_ text: String, alpha: CGFloat) {
            let s = Self.attributed(text, size: minSide * 0.055, bold: true, color: UIColor.white.withAlphaComponent(alpha), shadow: nil)
            let maxW = size.width * 0.82
            let r = s.boundingRect(with: CGSize(width: maxW, height: minSide * 0.6), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).integral
            let padH = minSide * 0.04, padV = minSide * 0.025
            // Im Hochformat höher, damit Bedienelemente von Reels/TikTok nichts verdecken
            let bottom = size.height - size.height * (size.height > size.width ? 0.16 : 0.07)
            let band = CGRect(x: size.width / 2 - r.width / 2 - padH, y: bottom - r.height - 2 * padV, width: r.width + 2 * padH, height: r.height + 2 * padV)
            UIColor.black.withAlphaComponent(0.59 * alpha).setFill()
            UIBezierPath(roundedRect: band, cornerRadius: minSide * 0.025).fill()
            s.draw(with: CGRect(x: size.width / 2 - r.width / 2, y: band.minY + padV, width: r.width, height: r.height),
                   options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        }

        func slogan(_ text: String, alpha: CGFloat, local: Double) {
            let shadow = NSShadow()
            shadow.shadowColor = UIColor.black.withAlphaComponent(alpha)
            shadow.shadowBlurRadius = 12
            shadow.shadowOffset = CGSize(width: 0, height: 4)
            let s = Self.attributed(text, size: minSide * 0.085, bold: true, color: UIColor.white.withAlphaComponent(alpha), shadow: shadow)
            let maxW = size.width * 0.86
            let r = s.boundingRect(with: CGSize(width: maxW, height: size.height * 0.5), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil).integral
            let rise = (1 - CGFloat(min(1, local / 0.6))) * minSide * 0.03
            s.draw(with: CGRect(x: (size.width - maxW) / 2, y: size.height * 0.38 - r.height / 2 + rise, width: maxW, height: r.height),
                   options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        }
    }

    /// Legt Hintergrundmusik unter das Video (gekürzt, am Ende ausgeblendet).
    static func addMusic(video: URL, music: URL) async throws -> URL {
        let vAsset = AVURLAsset(url: video)
        let aAsset = AVURLAsset(url: music)
        let comp = AVMutableComposition()
        guard let vSrc = try await vAsset.loadTracks(withMediaType: .video).first,
              let aSrc = try await aAsset.loadTracks(withMediaType: .audio).first else {
            throw APIError(code: 0, message: "Keine Tonspur in der Musikdatei gefunden.")
        }
        let vDur = try await vAsset.load(.duration)
        let aDur = try await aAsset.load(.duration)
        let range = CMTimeRange(start: .zero, duration: CMTimeMinimum(vDur, aDur))
        let vTrack = comp.addMutableTrack(withMediaType: .video, preferredTrackID: kCMPersistentTrackID_Invalid)
        try vTrack?.insertTimeRange(CMTimeRange(start: .zero, duration: vDur), of: vSrc, at: .zero)
        let aTrack = comp.addMutableTrack(withMediaType: .audio, preferredTrackID: kCMPersistentTrackID_Invalid)
        try aTrack?.insertTimeRange(range, of: aSrc, at: .zero)
        let mix = AVMutableAudioMix()
        if let aTrack {
            let params = AVMutableAudioMixInputParameters(track: aTrack)
            let fadeStart = CMTimeSubtract(range.duration, CMTime(seconds: min(1.5, range.duration.seconds / 3), preferredTimescale: 600))
            params.setVolumeRamp(fromStartVolume: 1, toEndVolume: 0, timeRange: CMTimeRange(start: fadeStart, end: range.duration))
            mix.inputParameters = [params]
        }
        guard let export = AVAssetExportSession(asset: comp, presetName: AVAssetExportPresetHighestQuality) else {
            throw APIError(code: 0, message: "Musik konnte nicht eingefügt werden.")
        }
        let out = video.deletingPathExtension().appendingPathExtension("musik.mp4")
        try? FileManager.default.removeItem(at: out)
        export.outputURL = out
        export.outputFileType = .mp4
        export.audioMix = mix
        await export.export()
        guard export.status == .completed else { throw export.error ?? APIError(code: 0, message: "Musik konnte nicht eingefügt werden.") }
        return out
    }
}

struct PromoVideoView: View {
    @EnvironmentObject private var settings: AppSettings
    @EnvironmentObject private var session: SessionController
    @State private var picks: [PhotosPickerItem] = []
    @State private var scenes: [PromoScene] = []
    @State private var docs: [PromoDoc] = []
    @State private var format: PromoFormat = .portrait
    @State private var about = ""
    @State private var slogan = ""
    @State private var endOn = true
    @State private var endName = ""
    @State private var endCta = ""
    @State private var endContact = ""
    @State private var endColor = 0
    @State private var photoSeconds = 2.5
    @State private var clipSeconds = 4.0
    @State private var musicURL: URL?
    @State private var showFiles = false
    @State private var showMusic = false
    @State private var loading = false
    @State private var writing = false
    @State private var progress: Double?
    @State private var resultURL: URL?
    @State private var error: String?
    @State private var info: String?

    private var endCard: PromoEndCard? {
        guard endOn, !(endName + endCta + endContact).trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return nil }
        return PromoEndCard(name: endName, cta: endCta, contact: endContact, colorIndex: endColor)
    }

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                Text("Kurze Werbevideos für Reels, TikTok, WhatsApp-Status oder YouTube aus deinen Fotos, Videoclips und Dateien. Die KI schreibt auf Wunsch Slogan und Einblendungen. Das Video entsteht direkt auf deinem iPhone.")
                    .font(.callout).foregroundStyle(Theme.secondaryText)

                heading("1. Fotos, Videos & Dateien")
                HStack {
                    PhotosPicker(selection: $picks, maxSelectionCount: PromoRenderer.maxScenes, matching: .any(of: [.images, .videos])) {
                        Label("Fotos & Videos", systemImage: "photo.on.rectangle")
                    }
                    Button { session.externalPickerActive = true; showFiles = true } label: { Label("Dateien", systemImage: "doc") }
                }
                .buttonStyle(.bordered).tint(Theme.cyan)
                .disabled(progress != nil || loading)
                Text("Dateien: PDF (Seiten werden zu Bildern), Word oder Text (z. B. Flyer, Speisekarte, Angebot) – die KI nutzt den Inhalt für die Werbetexte.")
                    .font(.caption).foregroundStyle(Theme.secondaryText)
                if loading { HStack { ProgressView(); Text("Lese Dateien …").font(.callout) } }
                if !docs.isEmpty {
                    ForEach(docs) { d in
                        HStack {
                            Image(systemName: "doc.text")
                            Text(d.name).font(.callout).lineLimit(1)
                            Spacer()
                            Button { docs.removeAll { $0.id == d.id } } label: { Image(systemName: "xmark.circle.fill") }
                                .accessibilityLabel("Datei „\(d.name)“ entfernen")
                        }
                        .foregroundStyle(Theme.text)
                    }
                }
                ForEach(Array(scenes.enumerated()), id: \.element.id) { index, scene in
                    sceneRow(index, scene)
                }

                heading("2. Format")
                Picker("Format", selection: $format) {
                    ForEach(PromoFormat.allCases) { Text($0.title).tag($0) }
                }
                .pickerStyle(.segmented)

                heading("3. Werbetexte")
                TextField("Was bewirbst du? (z. B. „Bäckerei Sonnenschein, Angebot der Woche“)", text: $about, axis: .vertical).lineLimit(2...4)
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                GradientButton(title: writing ? "KI schreibt …" : "Werbetexte mit KI schreiben", icon: "sparkles", busy: writing) { Task { await writeTexts() } }
                    .disabled(!settings.hasKey || progress != nil)
                TextField("Slogan (groß auf der ersten Szene)", text: $slogan)
                    .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))

                HStack {
                    heading("4. Abschluss-Karte")
                    Spacer()
                    Toggle("Abschluss-Karte zeigen", isOn: $endOn).labelsHidden()
                }
                if endOn {
                    TextField("Name / Firma", text: $endName).padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    TextField("Aufruf (z. B. „Jetzt bestellen“)", text: $endCta).padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    TextField("Kontakt (Webseite, Telefon, Adresse)", text: $endContact, axis: .vertical).lineLimit(1...3)
                        .padding(10).background(Theme.card, in: RoundedRectangle(cornerRadius: 10))
                    HStack(spacing: 12) {
                        ForEach(PromoRenderer.colors.indices, id: \.self) { i in
                            let c = PromoRenderer.colors[i]
                            Circle()
                                .fill(LinearGradient(colors: [Color(c.1), Color(c.2)], startPoint: .topLeading, endPoint: .bottomTrailing))
                                .frame(width: 34, height: 34)
                                .overlay(Circle().stroke(i == endColor ? Theme.cyan : Color.gray.opacity(0.5), lineWidth: i == endColor ? 3 : 1))
                                .onTapGesture { endColor = i }
                                .accessibilityLabel("Farbe \(c.0)")
                                .accessibilityAddTraits(i == endColor ? [.isButton, .isSelected] : .isButton)
                        }
                    }
                }

                heading("5. Länge & Musik")
                HStack { Text("Sekunden pro Foto/Seite: \(String(format: "%.1f", photoSeconds))"); Slider(value: $photoSeconds, in: 1.5...5, step: 0.5) }
                if scenes.contains(where: { if case .clip = $0.kind { return true } else { return false } }) {
                    HStack { Text("Höchstens Sekunden pro Clip: \(String(format: "%.1f", clipSeconds))"); Slider(value: $clipSeconds, in: 2...10, step: 0.5) }
                    Text("Von jedem Clip wird der Anfang verwendet (ohne Originalton).").font(.caption).foregroundStyle(Theme.secondaryText)
                }
                HStack {
                    Button { session.externalPickerActive = true; showMusic = true } label: {
                        Label(musicURL == nil ? "Hintergrundmusik wählen" : "Musik ändern", systemImage: "music.note")
                    }
                    .buttonStyle(.bordered).tint(Theme.cyan)
                    if let musicURL {
                        Text(musicURL.lastPathComponent).font(.caption).lineLimit(1)
                        Button { self.musicURL = nil } label: { Image(systemName: "xmark.circle.fill") }.accessibilityLabel("Musik entfernen")
                    }
                }

                let total = PromoRenderer.total(scenes: scenes, photo: photoSeconds, clip: clipSeconds, endCard: endCard != nil)
                GradientButton(
                    title: progress != nil ? "Erstelle Werbevideo … \(Int((progress ?? 0) * 100)) %"
                        : scenes.isEmpty ? "Erst Fotos, Videos oder Dateien hinzufügen" : "Werbevideo erstellen (ca. \(Int(total.rounded())) s)",
                    icon: "play.rectangle", busy: progress != nil
                ) { Task { await create() } }
                .disabled(scenes.isEmpty)
                if let progress {
                    ProgressView(value: progress)
                    Text("Bitte die App geöffnet lassen – Clips brauchen etwas länger.").font(.caption).foregroundStyle(Theme.secondaryText)
                }
                if let info { Text(info).font(.callout).foregroundStyle(Theme.cyan) }
                if let error { ErrorCard(message: error) }
                if let resultURL {
                    VideoPlayer(player: AVPlayer(url: resultURL))
                        .aspectRatio(format.size.width / format.size.height, contentMode: .fit)
                        .frame(maxHeight: 420)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    HStack {
                        ShareLink(item: resultURL) { Label("Teilen", systemImage: "square.and.arrow.up") }
                        Spacer()
                        Button {
                            UISaveVideoAtPathToSavedPhotosAlbum(resultURL.path, nil, nil, nil)
                            info = "In Fotos gesichert."
                        } label: { Label("In Fotos sichern", systemImage: "square.and.arrow.down") }
                    }
                }
            }
            .padding()
        }
        .onChange(of: picks) { _, items in
            guard !items.isEmpty else { return }
            Task { await loadPicks(items) }
        }
        .fileImporter(isPresented: $showFiles,
                      allowedContentTypes: [.pdf, .plainText, .text, .image, .movie, UTType(filenameExtension: "docx") ?? .data],
                      allowsMultipleSelection: true) { r in
            session.externalPickerActive = false
            if case .success(let urls) = r { Task { await loadFiles(urls) } }
        }
        .background(EmptyView().fileImporter(isPresented: $showMusic, allowedContentTypes: [.audio]) { r in
            session.externalPickerActive = false
            if case .success(let url) = r {
                let scoped = url.startAccessingSecurityScopedResource()
                defer { if scoped { url.stopAccessingSecurityScopedResource() } }
                let local = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "-" + url.lastPathComponent)
                if (try? FileManager.default.copyItem(at: url, to: local)) != nil { musicURL = local }
            }
        })
    }

    private func heading(_ text: String) -> some View {
        Text(text).font(.headline).foregroundStyle(Theme.cyan).accessibilityAddTraits(.isHeader)
    }

    @ViewBuilder
    private func sceneRow(_ index: Int, _ scene: PromoScene) -> some View {
        HStack(alignment: .center, spacing: 10) {
            Group {
                if let t = scene.thumb {
                    Image(uiImage: t).resizable().scaledToFill()
                } else {
                    Rectangle().fill(Theme.cardHigh)
                }
            }
            .frame(width: 60, height: 60).clipShape(RoundedRectangle(cornerRadius: 8))
            .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: 4) {
                Text("\(index + 1). \(scene.label)").font(.caption).foregroundStyle(Theme.secondaryText)
                TextField("Einblendung (optional)", text: Binding(
                    get: { scenes.first(where: { $0.id == scene.id })?.caption ?? "" },
                    set: { v in if let k = scenes.firstIndex(where: { $0.id == scene.id }) { scenes[k].caption = String(v.prefix(120)) } }
                ), axis: .vertical)
                .lineLimit(1...2)
                .padding(8).background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 8))
            }
            VStack(spacing: 6) {
                Button { move(index, -1) } label: { Image(systemName: "chevron.up") }.disabled(index == 0)
                    .accessibilityLabel("Szene \(index + 1) nach oben")
                Button { scenes.removeAll { $0.id == scene.id }; resultURL = nil } label: { Image(systemName: "xmark") }
                    .accessibilityLabel("Szene \(index + 1) entfernen")
                Button { move(index, 1) } label: { Image(systemName: "chevron.down") }.disabled(index == scenes.count - 1)
                    .accessibilityLabel("Szene \(index + 1) nach unten")
            }
            .buttonStyle(.borderless)
            .disabled(progress != nil)
        }
        .card()
    }

    private func move(_ index: Int, _ delta: Int) {
        let t = index + delta
        guard scenes.indices.contains(index), scenes.indices.contains(t) else { return }
        scenes.swapAt(index, t)
    }

    private static func thumb(_ img: UIImage) -> UIImage { img.scaled(maxSide: 200) }

    private static func clipScene(_ url: URL) async -> PromoScene {
        let dur = (try? await AVURLAsset(url: url).load(.duration).seconds) ?? 0
        let frame = await AttachmentLoader.videoFrames(url, count: 1).first
        let secs = Int(dur)
        return PromoScene(kind: .clip(url, duration: dur), label: String(format: "Clip %d:%02d", secs / 60, secs % 60), thumb: frame.map(thumb))
    }

    @MainActor
    private func loadPicks(_ items: [PhotosPickerItem]) async {
        loading = true; error = nil
        defer { loading = false; picks = [] }
        var skipped = 0
        for item in items {
            guard scenes.count < PromoRenderer.maxScenes else { skipped += 1; continue }
            if item.supportedContentTypes.contains(where: { $0.conforms(to: .movie) }) {
                if let v = try? await item.loadTransferable(type: VideoFile.self) { scenes.append(await Self.clipScene(v.url)) }
                else { error = "Ein Video konnte nicht geladen werden." }
            } else if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                let small = img.scaled(maxSide: 1600)
                scenes.append(PromoScene(kind: .image(small, fit: false), label: "Foto", thumb: Self.thumb(small)))
            }
        }
        if skipped > 0 { error = "Höchstens \(PromoRenderer.maxScenes) Szenen – \(skipped) nicht übernommen." }
        resultURL = nil
    }

    @MainActor
    private func loadFiles(_ urls: [URL]) async {
        loading = true; error = nil
        defer { loading = false }
        var problems: [String] = []
        for url in urls {
            let scoped = url.startAccessingSecurityScopedResource()
            defer { if scoped { url.stopAccessingSecurityScopedResource() } }
            let name = url.lastPathComponent
            let ext = url.pathExtension.lowercased()
            let local = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "-" + name)
            guard (try? FileManager.default.copyItem(at: url, to: local)) != nil else { problems.append("\(name): nicht lesbar"); continue }
            if ["jpg", "jpeg", "png", "heic", "webp", "gif"].contains(ext) {
                if scenes.count < PromoRenderer.maxScenes, let img = UIImage(contentsOfFile: local.path) {
                    let small = img.scaled(maxSide: 1600)
                    scenes.append(PromoScene(kind: .image(small, fit: false), label: "Foto", thumb: Self.thumb(small)))
                }
            } else if ["mp4", "mov", "m4v"].contains(ext) {
                if scenes.count < PromoRenderer.maxScenes { scenes.append(await Self.clipScene(local)) }
            } else if ext == "pdf", let doc = PDFDocument(url: local) {
                var text = ""
                for i in 0..<doc.pageCount { text += (doc.page(at: i)?.string ?? "") + "\n" }
                text = text.trimmingCharacters(in: .whitespacesAndNewlines)
                docs.append(PromoDoc(name: name, text: String(text.prefix(20_000))))
                for i in 0..<min(doc.pageCount, 5) where scenes.count < PromoRenderer.maxScenes {
                    guard let page = doc.page(at: i) else { continue }
                    let img = page.thumbnail(of: CGSize(width: 1400, height: 1400), for: .mediaBox)
                    scenes.append(PromoScene(kind: .image(img, fit: true), label: "PDF S. \(i + 1)", thumb: Self.thumb(img)))
                }
            } else {
                do {
                    let a = try AttachmentLoader.file(local)
                    docs.append(PromoDoc(name: name, text: a.text))
                } catch {
                    problems.append("\(name): \(friendly(error))")
                }
            }
        }
        if !problems.isEmpty { error = problems.joined(separator: "\n") }
        resultURL = nil
    }

    @MainActor
    private func writeTexts() async {
        if scenes.isEmpty && docs.isEmpty && about.trimmingCharacters(in: .whitespaces).isEmpty {
            error = "Füge zuerst Fotos, Videos oder Dateien hinzu oder beschreibe, was beworben werden soll."
            return
        }
        writing = true; error = nil; info = nil
        defer { writing = false }
        var images: [String] = []
        for s in scenes.prefix(6) {
            switch s.kind {
            case .image(let img, _): if let d = img.scaled(maxSide: 768).dataURL { images.append(d) }
            case .clip(let url, _): if let f = await AttachmentLoader.videoFrames(url, count: 1).first, let d = f.scaled(maxSide: 768).dataURL { images.append(d) }
            }
        }
        let docText = String(docs.filter { !$0.text.isEmpty }.map { "### \($0.name)\n\($0.text)" }.joined(separator: "\n\n").prefix(12_000))
        let n = scenes.count
        var prompt = "Erstelle Texte für ein kurzes Werbevideo (\(format.title), \(n) Szenen).\n"
        let topic = about.trimmingCharacters(in: .whitespacesAndNewlines)
        prompt += "Was beworben wird: \(topic.isEmpty ? "(nicht angegeben – aus Bildern und Unterlagen ableiten)" : topic)\n"
        if !images.isEmpty { prompt += "Die \(images.count) Bilder zeigen die Szenen 1–\(images.count) in Reihenfolge.\n" }
        if !docText.isEmpty { prompt += "\nUnterlagen:\n\(docText)\n" }
        prompt += """

        Antworte nur als JSON-Objekt mit den Feldern:
        "slogan": kurzer, einprägsamer Werbeslogan (max. 8 Wörter),
        "captions": Liste mit genau \(n) kurzen Einblendungen (je max. 6 Wörter, passend zur jeweiligen Szene, ohne Emojis),
        "cta": Aufruf zum Handeln (max. 4 Wörter, z. B. „Jetzt bestellen“),
        "name": Firmen- oder Produktname, nur wenn erkennbar, sonst leer,
        "contact": Webseite/Telefon/Adresse NUR wenn wörtlich in den Unterlagen oder Bildern, sonst leer – nichts erfinden.
        Alle Texte auf Deutsch, werbewirksam, ehrlich (keine falschen Versprechen oder Preise).
        """
        let messages = [
            ApiMessage(role: "system", text: "Du bist ein erfahrener Werbetexter für Social-Media-Videos."),
            ApiMessage(role: "user", text: prompt, images: images),
        ]
        var models = [settings.chatModel, MistralClient.fallbackModel, "mistral-medium-latest"]
        if settings.chatModel.contains("ministral") || settings.chatModel.contains("magistral") { models.removeFirst() }
        var raw = ""
        var lastError: Error?
        var seen = Set<String>()
        for m in models where seen.insert(m).inserted {
            do {
                raw = try await MistralClient.shared.complete(key: settings.key, model: m, messages: messages, json: true, temperature: 0.7)
                if !raw.isEmpty { break }
            } catch let e as APIError where e.code == 401 || e.code == 429 {
                error = friendly(e); return
            } catch {
                lastError = error
            }
        }
        guard let start = raw.firstIndex(of: "{"), let end = raw.lastIndex(of: "}"),
              let data = String(raw[start...end]).data(using: .utf8),
              let o = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            error = lastError.map(friendly) ?? "Die KI hat keine verwertbaren Texte geliefert."
            return
        }
        func str(_ k: String) -> String { (o[k] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines) }
        if !str("slogan").isEmpty { slogan = str("slogan") }
        if let caps = o["captions"] as? [Any] {
            for i in scenes.indices where i < caps.count {
                let c = (caps[i] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                if !c.isEmpty { scenes[i].caption = String(c.prefix(120)) }
            }
        }
        if !str("cta").isEmpty { endCta = str("cta") }
        if endName.isEmpty, !str("name").isEmpty { endName = str("name") }
        if endContact.isEmpty, !str("contact").isEmpty { endContact = str("contact") }
        info = "Werbetexte eingesetzt – du kannst alles noch ändern."
    }

    @MainActor
    private func create() async {
        guard !scenes.isEmpty, progress == nil else { return }
        progress = 0; error = nil; info = nil; resultURL = nil
        defer { progress = nil }
        do {
            let video = try await PromoRenderer.render(
                format: format, scenes: scenes, photo: photoSeconds, clip: clipSeconds, slogan: slogan, endCard: endCard
            ) { p in Task { @MainActor in if progress != nil { progress = p * 0.92 } } }
            if let musicURL {
                do {
                    resultURL = try await PromoRenderer.addMusic(video: video, music: musicURL)
                } catch {
                    ErrorLog.report(error, context: "Werbevideo-Musik")
                    info = "Musik konnte nicht eingefügt werden – Video ohne Ton erstellt."
                    resultURL = video
                }
            } else {
                resultURL = video
            }
        } catch {
            ErrorLog.report(error, context: "Werbevideo")
            self.error = "Werbevideo konnte nicht erstellt werden: \(friendly(error))"
        }
    }
}
