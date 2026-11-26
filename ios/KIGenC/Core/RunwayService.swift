import Foundation
import UIKit

/// Runway (KI-Videos, runwayml.com) – macht aus einem Foto einen kurzen KI-Videoclip (Bild-zu-Video, Gen-4 Turbo).
/// Eigener API-Schlüssel aus dem Entwicklerportal dev.runwayml.com, Abrechnung pro Sekunde über Runway-Guthaben.
enum RunwayService {
    static let base = "https://api.dev.runwayml.com/v1"
    static let model = "gen4_turbo"
    /// Gen-4 Turbo: 5 Credits pro Sekunde, 1 Credit = 1 Cent.
    static let creditsPerSecond = 5
    static let defaultMotion = "Smooth cinematic camera push-in, subtle natural motion, professional commercial look"

    /// Seitenverhältnis für Gen-4 Turbo passend zum Videoformat.
    static func ratio(for size: CGSize) -> String {
        size.width > size.height ? "1280:720" : (size.height > size.width ? "720:1280" : "960:960")
    }

    private static func request(_ path: String, method: String, key: String, body: [String: Any]? = nil) async throws -> [String: Any] {
        var r = URLRequest(url: URL(string: base + path)!)
        r.httpMethod = method
        r.timeoutInterval = 60
        r.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        r.setValue("2024-11-06", forHTTPHeaderField: "X-Runway-Version")
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if let body { r.httpBody = try JSONSerialization.data(withJSONObject: body) }
        let (data, resp) = try await URLSession.shared.data(for: r)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any] ?? [:]
        guard (200..<300).contains(code) else {
            let detail = (json["error"] as? String) ?? (json["message"] as? String) ?? String(data: data.prefix(200), encoding: .utf8) ?? ""
            switch code {
            case 401: throw APIError(code: 401, message: "Runway-Schlüssel ungültig – bitte in den Optionen prüfen.")
            case 429: throw APIError(code: 429, message: "Runway: zu viele Anfragen gleichzeitig – bitte kurz warten.")
            default: throw APIError(code: code, message: "Runway-Fehler \(code): \(detail)")
            }
        }
        return json
    }

    /// Erzeugt einen Clip aus dem Foto und speichert ihn als MP4. `status` meldet den Zwischenstand.
    static func imageToVideo(key: String, image: UIImage, motion: String, ratio: String, seconds: Int,
                             status: @escaping (String) -> Void) async throws -> URL {
        guard let jpeg = image.scaled(maxSide: 1280).jpegData(compressionQuality: 0.85) else {
            throw APIError(code: 0, message: "Das Foto konnte nicht gelesen werden.")
        }
        var body: [String: Any] = [
            "model": model,
            "promptImage": "data:image/jpeg;base64," + jpeg.base64EncodedString(),
            "ratio": ratio,
            "duration": seconds,
        ]
        let text = motion.trimmingCharacters(in: .whitespacesAndNewlines)
        body["promptText"] = String((text.isEmpty ? defaultMotion : text).prefix(900))
        let start = try await request("/image_to_video", method: "POST", key: key, body: body)
        guard let id = start["id"] as? String, !id.isEmpty else { throw APIError(code: 0, message: "Runway hat keine Auftragsnummer geliefert.") }
        status("In der Warteschlange …")
        let deadline = Date().addingTimeInterval(6 * 60)
        var videoURL: URL?
        while videoURL == nil {
            try Task.checkCancellation()
            if Date() > deadline { throw APIError(code: 0, message: "Runway braucht ungewöhnlich lange – bitte später erneut versuchen.") }
            try await Task.sleep(nanoseconds: 5_000_000_000)
            let task = try await request("/tasks/\(id)", method: "GET", key: key)
            switch task["status"] as? String ?? "" {
            case "SUCCEEDED":
                guard let s = (task["output"] as? [String])?.first, let u = URL(string: s) else {
                    throw APIError(code: 0, message: "Runway hat kein Video geliefert.")
                }
                videoURL = u
            case "FAILED", "CANCELLED":
                let code = task["failureCode"] as? String ?? ""
                let reason = task["failure"] as? String ?? "unbekannter Grund"
                if code.uppercased().contains("SAFETY") || code.uppercased().contains("MODERATION") {
                    throw APIError(code: 0, message: "Runway hat das Bild oder die Beschreibung abgelehnt (Inhaltsprüfung). Bitte ein anderes Foto oder eine andere Beschreibung wählen.")
                }
                throw APIError(code: 0, message: "Runway konnte den Clip nicht erzeugen: \(reason)")
            case "RUNNING":
                if let p = task["progress"] as? Double { status("Wird erzeugt … \(Int(p * 100)) %") } else { status("Wird erzeugt …") }
            default:
                status("In der Warteschlange …")
            }
        }
        // Sofort herunterladen – Runway-Links laufen nach 24–48 Stunden ab
        status("Wird heruntergeladen …")
        let (tmp, resp) = try await URLSession.shared.download(from: videoURL!)
        guard ((resp as? HTTPURLResponse)?.statusCode ?? 0) < 300 else { throw APIError(code: 0, message: "Runway-Video konnte nicht geladen werden.") }
        let out = FileManager.default.temporaryDirectory.appendingPathComponent("KI-GenC-Runway-\(Int(Date().timeIntervalSince1970)).mp4")
        try? FileManager.default.removeItem(at: out)
        try FileManager.default.moveItem(at: tmp, to: out)
        return out
    }
}
