import Foundation
import UIKit
import PDFKit
import AVFoundation
import CoreTransferable
import UniformTypeIdentifiers
import Compression

/// Ein Anhang: Foto, Video (als Einzelbilder + Ton) oder Datei (als Text).
struct Attachment: Identifiable {
    enum Kind { case image, video, file }
    let id = UUID()
    var kind: Kind
    var name: String
    var images: [UIImage] = []
    var text: String = ""
    var fileURL: URL? = nil

    var icon: String {
        switch kind {
        case .image: return "photo"
        case .video: return "film"
        case .file: return "doc.text"
        }
    }
}

/// Video aus der Fotomediathek als Datei übernehmen.
struct VideoFile: Transferable {
    let url: URL
    static var transferRepresentation: some TransferRepresentation {
        FileRepresentation(contentType: .movie) { SentTransferredFile($0.url) } importing: { received in
            let dest = FileManager.default.temporaryDirectory.appendingPathComponent("\(UUID().uuidString).\(received.file.pathExtension.isEmpty ? "mov" : received.file.pathExtension)")
            try? FileManager.default.removeItem(at: dest)
            try FileManager.default.copyItem(at: received.file, to: dest)
            return VideoFile(url: dest)
        }
    }
}

enum AttachmentLoader {
    static let maxText = 60_000

    /// Liest eine Datei (PDF, Word, Text, CSV, Code …) als Text.
    static func file(_ url: URL) throws -> Attachment {
        let scoped = url.startAccessingSecurityScopedResource()
        defer { if scoped { url.stopAccessingSecurityScopedResource() } }
        let name = url.lastPathComponent
        let ext = url.pathExtension.lowercased()
        // lokale Kopie für spätere Verarbeitung (z. B. Audio)
        let local = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + "-" + name)
        try? FileManager.default.copyItem(at: url, to: local)

        if ["jpg", "jpeg", "png", "heic", "webp", "gif"].contains(ext), let img = UIImage(contentsOfFile: local.path) {
            return Attachment(kind: .image, name: name, images: [img], fileURL: local)
        }
        var text = ""
        switch ext {
        case "pdf":
            if let doc = PDFDocument(url: local) {
                for i in 0..<doc.pageCount { text += (doc.page(at: i)?.string ?? "") + "\n" }
            }
        case "docx":
            text = (try? docxText(local)) ?? ""
        case "mp3", "m4a", "wav", "aac", "ogg", "flac", "mp4", "mov":
            return Attachment(kind: ext == "mp4" || ext == "mov" ? .video : .file, name: name, text: "", fileURL: local)
        default:
            let data = try Data(contentsOf: local)
            text = String(data: data, encoding: .utf8) ?? String(data: data, encoding: .isoLatin1) ?? ""
        }
        text = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if text.isEmpty { throw APIError(code: 0, message: "Aus „\(name)“ konnte kein Text gelesen werden.") }
        if text.count > maxText { text = String(text.prefix(maxText)) + "\n…(gekürzt)" }
        return Attachment(kind: .file, name: name, text: text, fileURL: local)
    }

    /// Zieht Einzelbilder aus einem Video (für die Analyse mit einem Vision-Modell).
    static func videoFrames(_ url: URL, count: Int = 6) async -> [UIImage] {
        let asset = AVURLAsset(url: url)
        let gen = AVAssetImageGenerator(asset: asset)
        gen.appliesPreferredTrackTransform = true
        gen.maximumSize = CGSize(width: 1024, height: 1024)
        let seconds = (try? await asset.load(.duration).seconds) ?? 0
        guard seconds > 0 else { return [] }
        var frames: [UIImage] = []
        for i in 0..<count {
            let t = CMTime(seconds: seconds * (Double(i) + 0.5) / Double(count), preferredTimescale: 600)
            if let (cg, _) = try? await gen.image(at: t) { frames.append(UIImage(cgImage: cg)) }
        }
        return frames
    }

    static func video(_ url: URL, name: String) async -> Attachment {
        Attachment(kind: .video, name: name, images: await videoFrames(url), fileURL: url)
    }

    /// Exportiert die Tonspur eines Videos als M4A (für die Transkription).
    static func extractAudio(_ url: URL) async throws -> URL {
        let asset = AVURLAsset(url: url)
        guard let export = AVAssetExportSession(asset: asset, presetName: AVAssetExportPresetAppleM4A) else {
            throw APIError(code: 0, message: "Tonspur konnte nicht gelesen werden.")
        }
        let out = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString + ".m4a")
        export.outputURL = out
        export.outputFileType = .m4a
        await export.export()
        guard export.status == .completed else {
            throw APIError(code: 0, message: "Das Video hat keine lesbare Tonspur.")
        }
        return out
    }

    // MARK: Word-Dateien (DOCX = ZIP mit word/document.xml)

    static func docxText(_ url: URL) throws -> String {
        let data = try Data(contentsOf: url)
        guard let xml = try zipEntry(data, named: "word/document.xml"), let s = String(data: xml, encoding: .utf8) else { return "" }
        var t = s.replacingOccurrences(of: "</w:p>", with: "\n")
            .replacingOccurrences(of: "<w:tab/>", with: "\t")
            .replacingOccurrences(of: "<w:br/>", with: "\n")
        t = t.replacingOccurrences(of: "<[^>]+>", with: "", options: .regularExpression)
        return t.replacingOccurrences(of: "&amp;", with: "&").replacingOccurrences(of: "&lt;", with: "<")
            .replacingOccurrences(of: "&gt;", with: ">").replacingOccurrences(of: "&quot;", with: "\"").replacingOccurrences(of: "&apos;", with: "'")
    }

    /// Minimaler ZIP-Leser (zentrales Verzeichnis, Methoden „stored“ und „deflate“).
    static func zipEntry(_ data: Data, named target: String) throws -> Data? {
        let bytes = [UInt8](data)
        func u16(_ o: Int) -> Int { Int(bytes[o]) | Int(bytes[o + 1]) << 8 }
        func u32(_ o: Int) -> Int { u16(o) | u16(o + 2) << 16 }
        guard bytes.count > 22 else { return nil }
        var eocd = -1
        var i = bytes.count - 22
        while i >= max(0, bytes.count - 65_557) {
            if u32(i) == 0x06054b50 { eocd = i; break }
            i -= 1
        }
        guard eocd >= 0 else { return nil }
        let entries = u16(eocd + 10)
        var p = u32(eocd + 16)
        for _ in 0..<entries {
            guard p + 46 <= bytes.count, u32(p) == 0x02014b50 else { return nil }
            let method = u16(p + 10)
            let compSize = u32(p + 20)
            let size = u32(p + 24)
            let nameLen = u16(p + 28), extraLen = u16(p + 30), commentLen = u16(p + 32)
            let localOffset = u32(p + 42)
            let name = String(bytes: bytes[(p + 46)..<(p + 46 + nameLen)], encoding: .utf8) ?? ""
            if name == target {
                let lh = localOffset
                let start = lh + 30 + u16(lh + 26) + u16(lh + 28)
                guard start + compSize <= bytes.count else { return nil }
                let comp = Array(bytes[start..<(start + compSize)])
                if method == 0 { return Data(comp) }
                guard method == 8 else { return nil }
                var out = [UInt8](repeating: 0, count: max(size, 1))
                let n = compression_decode_buffer(&out, out.count, comp, comp.count, nil, COMPRESSION_ZLIB)
                return n > 0 ? Data(out.prefix(n)) : nil
            }
            p += 46 + nameLen + extraLen + commentLen
        }
        return nil
    }

    /// Fasst Anhänge für die KI zusammen: Text für die Nachricht, Bilder als Data-URLs.
    static func prompt(for attachments: [Attachment]) -> (String, [String]) {
        var text = ""
        var images: [String] = []
        for a in attachments {
            switch a.kind {
            case .file where !a.text.isEmpty:
                text += "\n\n--- Datei: \(a.name) ---\n\(a.text)"
            case .video:
                text += "\n\n[Video „\(a.name)“: \(a.images.count) Einzelbilder angehängt]"
                images += a.images.compactMap { $0.dataURL }
            default:
                images += a.images.compactMap { $0.dataURL }
            }
        }
        return (text, Array(images.prefix(8)))
    }
}
