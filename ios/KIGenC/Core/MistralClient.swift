import Foundation

struct APIError: LocalizedError {
    let code: Int
    let message: String
    var retryAfter: Double? = nil
    var errorDescription: String? { message }
}

/// Eine Nachricht im Format der Mistral-Chat-API (Text + optional Bilder als Data-URL).
struct ApiMessage {
    var role: String
    var text: String
    var images: [String] = []

    var json: [String: Any] {
        if images.isEmpty { return ["role": role, "content": text] }
        var parts: [[String: Any]] = []
        if !text.isEmpty { parts.append(["type": "text", "text": text]) }
        for img in images { parts.append(["type": "image_url", "image_url": img]) }
        return ["role": role, "content": parts]
    }
}

func friendly(_ error: Error) -> String {
    if let e = error as? APIError { return e.message }
    let ns = error as NSError
    if ns.domain == NSURLErrorDomain {
        switch ns.code {
        case NSURLErrorNotConnectedToInternet: return "Keine Internetverbindung."
        case NSURLErrorTimedOut: return "Zeitüberschreitung – der Server hat zu lange gebraucht."
        default: return "Netzwerkfehler: \(ns.localizedDescription)"
        }
    }
    return error.localizedDescription
}

/// Client für die Mistral-AI-API (Server in der EU).
final class MistralClient {
    static let shared = MistralClient()
    static let fallbackModel = "mistral-small-latest"
    private let base = "https://api.mistral.ai/v1"
    private let session: URLSession = {
        let c = URLSessionConfiguration.default
        c.timeoutIntervalForRequest = 180
        c.timeoutIntervalForResource = 600
        return URLSession(configuration: c)
    }()

    private func request(_ path: String, key: String, method: String = "POST", body: [String: Any]? = nil, accept: String = "application/json") -> URLRequest {
        var r = URLRequest(url: URL(string: base + path)!)
        r.httpMethod = method
        r.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        r.setValue(accept, forHTTPHeaderField: "Accept")
        if let body {
            r.setValue("application/json", forHTTPHeaderField: "Content-Type")
            r.httpBody = try? JSONSerialization.data(withJSONObject: body)
        }
        return r
    }

    static func errorFor(status: Int, body: Data, headers: [AnyHashable: Any]) -> APIError {
        var detail = ""
        if let o = try? JSONSerialization.jsonObject(with: body) as? [String: Any] {
            detail = (o["message"] as? String) ?? ((o["error"] as? [String: Any])?["message"] as? String) ?? (o["detail"] as? String) ?? ""
        }
        let retry = (headers["Retry-After"] as? String).flatMap(Double.init)
        let msg: String
        switch status {
        case 401: msg = "API-Schlüssel ungültig oder abgelaufen (401). Bitte in den Optionen prüfen."
        case 403: msg = "Zugriff verweigert (403). \(detail)"
        case 404: msg = "Nicht gefunden (404). \(detail)"
        case 422: msg = "Ungültige Anfrage (422). \(detail)"
        case 429: msg = "Mistral-Limit erreicht (429)\(detail.isEmpty ? "." : ": \(detail)") Im Gratis-Tarif sind nur wenige Anfragen pro Minute erlaubt – kurz warten."
        case 500...599: msg = "Mistral-Server nicht erreichbar (\(status)). Bitte später erneut versuchen."
        default: msg = "Fehler \(status). \(detail)"
        }
        return APIError(code: status, message: msg.trimmingCharacters(in: .whitespaces), retryAfter: retry)
    }

    private func json(_ r: URLRequest) async throws -> [String: Any] {
        let (data, resp) = try await session.data(for: r)
        let http = resp as? HTTPURLResponse
        let status = http?.statusCode ?? 0
        guard (200...299).contains(status) else {
            throw Self.errorFor(status: status, body: data, headers: http?.allHeaderFields ?? [:])
        }
        return (try JSONSerialization.jsonObject(with: data) as? [String: Any]) ?? [:]
    }

    private static func contentText(_ content: Any?) -> String {
        if let s = content as? String { return s }
        if let arr = content as? [[String: Any]] {
            return arr.compactMap { ($0["type"] as? String) == "text" ? $0["text"] as? String : nil }.joined()
        }
        return ""
    }

    // MARK: Chat (Streaming)

    /// Streamt die Antwort. Bei Limit-Fehlern: kurz warten, erneut versuchen, dann auf das kleinere Modell ausweichen.
    func stream(key: String, model: String, messages: [ApiMessage], temperature: Double = 0.7) -> AsyncThrowingStream<String, Error> {
        AsyncThrowingStream { continuation in
            let task = Task {
                let candidates = model == Self.fallbackModel ? [model] : [model, Self.fallbackModel]
                var lastError: Error?
                outer: for (index, m) in candidates.enumerated() {
                    let body: [String: Any] = [
                        "model": m, "stream": true, "temperature": temperature,
                        "messages": messages.map { $0.json },
                    ]
                    for attempt in 0..<3 {
                        do {
                            let r = request("/chat/completions", key: key, body: body, accept: "text/event-stream")
                            let (bytes, resp) = try await session.bytes(for: r)
                            let http = resp as? HTTPURLResponse
                            let status = http?.statusCode ?? 0
                            if !(200...299).contains(status) {
                                var data = Data()
                                for try await b in bytes { data.append(b) }
                                throw Self.errorFor(status: status, body: data, headers: http?.allHeaderFields ?? [:])
                            }
                            if index > 0 { continuation.yield("_Hinweis: \(model) war ausgelastet, Antwort von \(m)._\n\n") }
                            for try await line in bytes.lines {
                                try Task.checkCancellation()
                                guard line.hasPrefix("data:") else { continue }
                                let payload = line.dropFirst(5).trimmingCharacters(in: .whitespaces)
                                if payload == "[DONE]" { break }
                                guard let d = payload.data(using: .utf8),
                                      let o = try? JSONSerialization.jsonObject(with: d) as? [String: Any],
                                      let choices = o["choices"] as? [[String: Any]], let first = choices.first,
                                      let delta = first["delta"] as? [String: Any] else { continue }
                                let text = Self.contentText(delta["content"])
                                if !text.isEmpty { continuation.yield(text) }
                            }
                            continuation.finish()
                            return
                        } catch let e as APIError where e.code == 429 {
                            lastError = e
                            if attempt < 2 {
                                let wait = min(10, max(1, e.retryAfter ?? pow(2, Double(attempt + 1))))
                                try? await Task.sleep(nanoseconds: UInt64(wait * 1_000_000_000))
                            }
                        } catch {
                            if Task.isCancelled { continuation.finish(); return }
                            continuation.finish(throwing: error)
                            return
                        }
                    }
                    if lastError == nil { break outer }
                }
                continuation.finish(throwing: lastError ?? APIError(code: 429, message: "Mistral-Limit erreicht (429)."))
            }
            continuation.onTermination = { _ in task.cancel() }
        }
    }

    /// Nicht gestreamte Antwort (optional als JSON).
    func complete(key: String, model: String, messages: [ApiMessage], json jsonMode: Bool = false, temperature: Double = 0.3) async throws -> String {
        var body: [String: Any] = ["model": model, "temperature": temperature, "messages": messages.map { $0.json }]
        if jsonMode { body["response_format"] = ["type": "json_object"] }
        var last: Error?
        for attempt in 0..<3 {
            do {
                let o = try await json(request("/chat/completions", key: key, body: body))
                let choices = o["choices"] as? [[String: Any]]
                let message = choices?.first?["message"] as? [String: Any]
                return Self.contentText(message?["content"])
            } catch let e as APIError where e.code == 429 {
                last = e
                if attempt < 2 { try? await Task.sleep(nanoseconds: UInt64(min(10, e.retryAfter ?? pow(2, Double(attempt + 1))) * 1_000_000_000)) }
            }
        }
        throw last ?? APIError(code: 429, message: "Mistral-Limit erreicht (429).")
    }

    /// Beschreibt Bilder; kann das Chat-Modell keine Bilder, wird auf ein Vision-Modell ausgewichen.
    func describe(key: String, preferredModel: String, instruction: String, images: [String]) async throws -> String {
        var last: Error?
        for m in Array(NSOrderedSet(array: [preferredModel, Self.fallbackModel, "mistral-medium-latest"])) as! [String] {
            do {
                let t = try await complete(key: key, model: m, messages: [ApiMessage(role: "user", text: instruction, images: images)])
                if !t.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty { return String(t.prefix(1800)) }
            } catch let e as APIError where e.code != 401 && e.code != 429 {
                last = e
            }
        }
        throw last ?? APIError(code: 0, message: "Die KI konnte die Bilder nicht erkennen.")
    }

    func listModels(key: String) async throws -> [String] {
        let o = try await json(request("/models", key: key, method: "GET"))
        let data = o["data"] as? [[String: Any]] ?? []
        let ids = data.compactMap { m -> String? in
            let caps = m["capabilities"] as? [String: Any]
            if let chat = caps?["completion_chat"] as? Bool, !chat { return nil }
            if let dep = m["deprecation"] as? String, !dep.isEmpty { return nil }
            guard let id = m["id"] as? String, !id.contains("embed"), !id.contains("moderation"), !id.contains("ocr") else { return nil }
            return id
        }
        return Array(Set(ids)).sorted { a, b in
            let la = a.hasSuffix("-latest"), lb = b.hasSuffix("-latest")
            return la != lb ? la : a < b
        }
    }

    // MARK: Recherche

    func webSearch(key: String, model: String, question: String) async throws -> (String, [(String, String)]) {
        let prompt = question + "\n\n(Recherchiere aktuell im Web. Antworte auf Deutsch, gut gegliedert in Markdown, gestützt auf die Quellen.)"
        let body: [String: Any] = [
            "model": model, "inputs": [["role": "user", "content": prompt]],
            "tools": [["type": "web_search"]], "store": false,
        ]
        let o = try await json(request("/conversations", key: key, body: body))
        var text = ""
        var sources: [(String, String)] = []
        for entry in o["outputs"] as? [[String: Any]] ?? [] where (entry["type"] as? String) == "message.output" {
            if let s = entry["content"] as? String { text += s }
            for chunk in entry["content"] as? [[String: Any]] ?? [] {
                switch chunk["type"] as? String {
                case "text": text += chunk["text"] as? String ?? ""
                case "tool_reference":
                    if let url = chunk["url"] as? String, url.hasPrefix("http"), !sources.contains(where: { $0.1 == url }) {
                        sources.append(((chunk["title"] as? String) ?? url, url))
                    }
                default: break
                }
            }
        }
        return (text.trimmingCharacters(in: .whitespacesAndNewlines), sources)
    }

    // MARK: Transkription (Voxtral)

    func transcribe(key: String, data: Data, fileName: String, mime: String, language: String?) async throws -> String {
        let boundary = "----KIGenC\(Int(Date().timeIntervalSince1970))"
        var r = URLRequest(url: URL(string: base + "/audio/transcriptions")!)
        r.httpMethod = "POST"
        r.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        r.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        var body = Data()
        func field(_ name: String, _ value: String) {
            body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"\(name)\"\r\n\r\n\(value)\r\n".utf8))
        }
        field("model", "voxtral-mini-latest")
        if let language, !language.isEmpty { field("language", language) }
        body.append(Data("--\(boundary)\r\nContent-Disposition: form-data; name=\"file\"; filename=\"\(fileName)\"\r\nContent-Type: \(mime)\r\n\r\n".utf8))
        body.append(data)
        body.append(Data("\r\n--\(boundary)--\r\n".utf8))
        r.httpBody = body
        let o = try await json(r)
        return (o["text"] as? String ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
    }

    // MARK: Bildgenerierung über Mistral

    func generateImagesDirect(key: String, model: String, prompt: String) async throws -> [Data] {
        let body: [String: Any] = [
            "model": model,
            "instructions": "Du bist ein Bildgenerator. Nutze für jede Anfrage das Werkzeug image_generation und erzeuge genau ein Bild.",
            "inputs": [["role": "user", "content": "Erzeuge ein Bild: \(prompt)"]],
            "tools": [["type": "image_generation"]],
        ]
        return try await imagesFromConversation(key: key, body: body)
    }

    func createImageAgent(key: String, model: String) async throws -> String {
        let body: [String: Any] = [
            "model": model, "name": "KI-GenC Bildgenerator",
            "instructions": "Du bist ein Bildgenerator. Nutze für jede Anfrage das Werkzeug zur Bildgenerierung und erzeuge genau ein Bild.",
            "tools": [["type": "image_generation"]],
        ]
        let o = try await json(request("/agents", key: key, body: body))
        guard let id = o["id"] as? String else { throw APIError(code: 0, message: "Bild-Agent konnte nicht angelegt werden.") }
        return id
    }

    func generateImagesWithAgent(key: String, agentID: String, prompt: String) async throws -> [Data] {
        try await imagesFromConversation(key: key, body: ["agent_id": agentID, "inputs": prompt])
    }

    private func imagesFromConversation(key: String, body: [String: Any]) async throws -> [Data] {
        let o = try await json(request("/conversations", key: key, body: body))
        var ids: [String] = []
        var caption = ""
        for entry in o["outputs"] as? [[String: Any]] ?? [] where (entry["type"] as? String) == "message.output" {
            if let s = entry["content"] as? String { caption += s }
            for chunk in entry["content"] as? [[String: Any]] ?? [] {
                if (chunk["type"] as? String) == "tool_file", let id = chunk["file_id"] as? String { ids.append(id) }
                if (chunk["type"] as? String) == "text" { caption += chunk["text"] as? String ?? "" }
            }
        }
        guard !ids.isEmpty else {
            throw APIError(code: 0, message: "Mistral hat kein Bild erzeugt" + (caption.isEmpty ? "." : ": \(caption.prefix(200))"))
        }
        var result: [Data] = []
        for id in ids {
            var r = request("/files/\(id)/content", key: key, method: "GET", accept: "*/*")
            r.timeoutInterval = 120
            let (data, resp) = try await session.data(for: r)
            let status = (resp as? HTTPURLResponse)?.statusCode ?? 0
            guard (200...299).contains(status) else { throw Self.errorFor(status: status, body: data, headers: [:]) }
            result.append(data)
        }
        return result
    }
}
