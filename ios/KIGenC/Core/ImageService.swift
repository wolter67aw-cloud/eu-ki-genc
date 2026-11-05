import Foundation
import UIKit

/// Bildgenerierung über Mistral, Pollinations oder Black Forest Labs (FLUX).
enum ImageService {
    private static let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 180
        return URLSession(configuration: c)
    }()

    static func size(for aspect: Double?) -> (Int, Int) {
        guard let a = aspect, a > 0 else { return (1024, 1024) }
        if a > 1.15 { return (1344, 768) }
        if a < 0.87 { return (768, 1344) }
        return (1024, 1024)
    }

    /// Erzeugt ein Bild mit dem in den Optionen gewählten Anbieter. Liefert Bilddaten und eine Info zum Anbieter.
    static func generate(prompt: String, aspect: Double? = nil, settings: AppSettings) async throws -> (Data, String) {
        let (w, h) = size(for: aspect)
        switch settings.imageProvider {
        case .pollinations:
            return (try await pollinations(prompt: prompt, width: w, height: h), "Pollinations")
        case .bfl:
            guard settings.canEditFaithfully else { throw APIError(code: 0, message: "Bitte zuerst den Black-Forest-Labs-Schlüssel in den Optionen eintragen.") }
            return (try await bflGenerate(key: settings.bflKey, prompt: prompt, width: w, height: h), "Black Forest Labs")
        case .mistral:
            return (try await mistral(prompt: prompt, settings: settings), "Mistral AI")
        case .auto:
            do {
                return (try await mistral(prompt: prompt, settings: settings), "Mistral AI")
            } catch {
                if settings.canEditFaithfully, let d = try? await bflGenerate(key: settings.bflKey, prompt: prompt, width: w, height: h) {
                    return (d, "Black Forest Labs")
                }
                return (try await pollinations(prompt: prompt, width: w, height: h), "Pollinations (Mistral nicht verfügbar)")
            }
        }
    }

    private static func mistral(prompt: String, settings: AppSettings) async throws -> Data {
        guard settings.hasKey else { throw APIError(code: 401, message: "Kein Mistral-Schlüssel hinterlegt.") }
        let client = MistralClient.shared
        if let d = try? await client.generateImagesDirect(key: settings.key, model: "mistral-medium-latest", prompt: prompt).first {
            return d
        }
        var agent = settings.imageAgentID
        if agent.isEmpty {
            agent = try await client.createImageAgent(key: settings.key, model: "mistral-medium-latest")
            await MainActor.run { settings.imageAgentID = agent }
        }
        do {
            guard let d = try await client.generateImagesWithAgent(key: settings.key, agentID: agent, prompt: prompt).first else {
                throw APIError(code: 0, message: "Mistral hat kein Bild erzeugt.")
            }
            return d
        } catch let e as APIError where e.code == 404 {
            await MainActor.run { settings.imageAgentID = "" }
            throw e
        }
    }

    static func pollinations(prompt: String, width: Int, height: Int) async throws -> Data {
        let encoded = String(prompt.prefix(1500)).addingPercentEncoding(withAllowedCharacters: .urlPathAllowed.subtracting(CharacterSet(charactersIn: "/?#"))) ?? ""
        let seed = Int.random(in: 1...999_999)
        let url = URL(string: "https://image.pollinations.ai/prompt/\(encoded)?width=\(width)&height=\(height)&nologo=true&model=flux&seed=\(seed)")!
        var last: Error?
        for attempt in 0..<3 {
            do {
                var r = URLRequest(url: url)
                r.timeoutInterval = 150
                let (data, resp) = try await session.data(for: r)
                let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
                if status == 429 { throw APIError(code: 429, message: "Pollinations ist ausgelastet (429). Bitte etwa 15 Sekunden warten.") }
                guard (200...299).contains(status), UIImage(data: data) != nil else {
                    throw APIError(code: status, message: "Pollinations hat kein Bild geliefert (\(status)).")
                }
                return data
            } catch {
                last = error
                if attempt < 2 { try? await Task.sleep(nanoseconds: UInt64(6 + attempt * 6) * 1_000_000_000) }
            }
        }
        throw last ?? APIError(code: 0, message: "Pollinations nicht erreichbar.")
    }

    // MARK: Black Forest Labs

    private static func bflPost(path: String, key: String, body: [String: Any]) async throws -> Data {
        var r = URLRequest(url: URL(string: "https://api.bfl.ai/v1/\(path)")!)
        r.httpMethod = "POST"
        r.setValue("application/json", forHTTPHeaderField: "Content-Type")
        r.setValue(key.trimmingCharacters(in: .whitespacesAndNewlines), forHTTPHeaderField: "x-key")
        r.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, resp) = try await session.data(for: r)
        let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(status) else {
            let text = String(data: data, encoding: .utf8) ?? ""
            switch status {
            case 401, 403: throw APIError(code: status, message: "Black-Forest-Labs-Schlüssel ungültig (\(status)).")
            case 402: throw APIError(code: status, message: "Black Forest Labs: Guthaben aufgebraucht (402).")
            case 429: throw APIError(code: status, message: "Black Forest Labs: zu viele Anfragen (429).")
            default: throw APIError(code: status, message: "Black Forest Labs Fehler \(status): \(text.prefix(200))")
            }
        }
        guard let o = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let polling = o["polling_url"] as? String, let pollURL = URL(string: polling) else {
            throw APIError(code: 0, message: "Black Forest Labs: unerwartete Antwort.")
        }
        for _ in 0..<120 {
            try await Task.sleep(nanoseconds: 1_500_000_000)
            var p = URLRequest(url: pollURL)
            p.setValue(key.trimmingCharacters(in: .whitespacesAndNewlines), forHTTPHeaderField: "x-key")
            let (pd, _) = try await session.data(for: p)
            guard let po = try JSONSerialization.jsonObject(with: pd) as? [String: Any] else { continue }
            let state = po["status"] as? String ?? ""
            if state == "Ready" {
                guard let result = po["result"] as? [String: Any], let sample = result["sample"] as? String, let su = URL(string: sample) else {
                    throw APIError(code: 0, message: "Black Forest Labs: Ergebnis ohne Bild.")
                }
                let (img, _) = try await session.data(from: su)
                return img
            }
            if ["Error", "Failed", "Content Moderated", "Request Moderated"].contains(state) {
                throw APIError(code: 0, message: "Black Forest Labs: \(state).")
            }
        }
        throw APIError(code: 0, message: "Black Forest Labs: Zeitüberschreitung.")
    }

    static func bflGenerate(key: String, prompt: String, width: Int, height: Int) async throws -> Data {
        try await bflPost(path: "flux-2-pro", key: key, body: ["prompt": prompt, "width": width, "height": height, "output_format": "jpeg"])
    }

    /// Gesichtstreue Bearbeitung (FLUX Kontext): das Originalbild bleibt erhalten, nur die gewünschte Änderung wird umgesetzt.
    static func editFaithfully(key: String, instruction: String, images: [UIImage]) async throws -> Data {
        var body: [String: Any] = [
            "prompt": instruction + ". Keep the exact same person, identical face, facial features, skin tone, expression and proportions. Do not alter the face.",
            "output_format": "jpeg",
            "safety_tolerance": 2,
        ]
        for (i, img) in images.prefix(4).enumerated() {
            guard let b64 = img.scaled(maxSide: 1536).jpegData(compressionQuality: 0.9)?.base64EncodedString() else { continue }
            body[i == 0 ? "input_image" : "input_image_\(i + 1)"] = b64
        }
        if let first = images.first {
            let a = first.size.width / max(1, first.size.height)
            body["aspect_ratio"] = a > 1.15 ? "16:9" : (a < 0.87 ? "9:16" : "1:1")
        }
        return try await bflPost(path: "flux-kontext-pro", key: key, body: body)
    }
}

extension UIImage {
    func scaled(maxSide: CGFloat) -> UIImage {
        let longest = max(size.width, size.height)
        guard longest > maxSide else { return self }
        let factor = maxSide / longest
        let newSize = CGSize(width: size.width * factor, height: size.height * factor)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: newSize, format: format).image { _ in draw(in: CGRect(origin: .zero, size: newSize)) }
    }

    /// Bild als Data-URL für die Vision-Modelle von Mistral.
    var dataURL: String? {
        guard let d = scaled(maxSide: 1280).jpegData(compressionQuality: 0.8) else { return nil }
        return "data:image/jpeg;base64," + d.base64EncodedString()
    }
}
