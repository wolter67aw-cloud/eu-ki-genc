import SwiftUI
import WebKit

/// Live-Vorschau wie Claudes Artefakte: HTML-Seiten, Mini-Apps, SVG-Grafiken und Mermaid-Diagramme
/// aus einer KI-Antwort direkt in der App ansehen und ausprobieren.
enum LivePreview {
    struct Artifact: Identifiable { let id = UUID(); let title: String; let html: String }

    /// Sucht in der Antwort nach etwas Darstellbarem (nil = nichts).
    static func find(_ md: String) -> Artifact? {
        guard let re = try? NSRegularExpression(pattern: "```([\\w+#.-]*)[^\\n]*\\n([\\s\\S]*?)```") else { return nil }
        let ns = md as NSString
        let blocks: [(String, String)] = re.matches(in: md, range: NSRange(location: 0, length: ns.length)).map {
            (ns.substring(with: $0.range(at: 1)).lowercased(), ns.substring(with: $0.range(at: 2)))
        }
        guard !blocks.isEmpty else { return nil }
        let css = blocks.filter { $0.0 == "css" }.map(\.1).joined(separator: "\n")
        let js = blocks.filter { ["js", "javascript"].contains($0.0) }.map(\.1).joined(separator: "\n")
        if let b = blocks.first(where: { ["html", "htm", "xhtml"].contains($0.0) || $0.1.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().hasPrefix("<!doctype") || $0.1.lowercased().contains("<html") }) {
            return Artifact(title: "Webseite", html: inject(b.1, css: css, js: js))
        }
        if let b = blocks.first(where: { $0.0 == "svg" || (["xml", ""].contains($0.0) && $0.1.trimmingCharacters(in: .whitespacesAndNewlines).hasPrefix("<svg")) }) {
            return Artifact(title: "Grafik", html: page("<div style=\"display:flex;justify-content:center\">\(b.1)</div>", style: "svg{max-width:100%;height:auto}"))
        }
        if let b = blocks.first(where: { $0.0 == "mermaid" }) {
            return Artifact(title: "Diagramm", html: mermaid(b.1))
        }
        return nil
    }

    private static func page(_ body: String, style: String = "", head: String = "") -> String {
        "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"utf-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><style>body{font-family:-apple-system,sans-serif;margin:12px}\(style)</style>\(head)</head><body>\(body)</body></html>"
    }

    private static func insert(_ h: String, before pattern: String, _ text: String, after: Bool = false) -> String {
        guard let r = h.range(of: pattern, options: [.regularExpression, .caseInsensitive]) else { return h }
        var s = h
        s.insert(contentsOf: text, at: after ? r.upperBound : r.lowerBound)
        return s
    }

    private static func inject(_ html: String, css: String, js: String) -> String {
        var h = html
        if h.range(of: "<html", options: .caseInsensitive) == nil { h = page(h) }
        if h.range(of: "name=\"viewport\"", options: .caseInsensitive) == nil {
            h = insert(h, before: "<head[^>]*>", "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">", after: true)
        }
        if !css.isEmpty { h = insert(h, before: "</head>", "<style>\n\(css)\n</style>") }
        if !js.isEmpty {
            let tag = "<script>\n\(js)\n</script>"
            h = h.range(of: "</body>", options: .caseInsensitive) != nil ? insert(h, before: "</body>", tag) : h + tag
        }
        return h
    }

    private static func mermaid(_ code: String) -> String {
        let esc = code.replacingOccurrences(of: "&", with: "&amp;").replacingOccurrences(of: "<", with: "&lt;").replacingOccurrences(of: ">", with: "&gt;")
        return page("<pre class=\"mermaid\">\(esc)</pre><p id=\"err\" style=\"color:#b00\"></p>",
                    head: "<script src=\"https://cdn.jsdelivr.net/npm/mermaid@11.4.1/dist/mermaid.min.js\"></script><script>window.addEventListener('load',function(){try{mermaid.initialize({startOnLoad:false,theme:'default'});mermaid.run().catch(function(e){document.getElementById('err').textContent='Diagramm fehlerhaft: '+e});}catch(e){document.getElementById('err').textContent='Diagramm-Bibliothek nicht geladen (Internet?)';}});</script>")
    }
}

/// Knopf „Vorschau“ – erscheint nur, wenn die Antwort etwas Darstellbares enthält.
struct LivePreviewButton: View {
    let text: String
    @State private var shown: LivePreview.Artifact?

    var body: some View {
        if let artifact = LivePreview.find(text) {
            Button { shown = artifact } label: { Image(systemName: "play.rectangle") }
                .accessibilityLabel("Live-Vorschau (\(artifact.title))")
                .fullScreenCover(item: $shown) { a in LivePreviewView(artifact: a) }
        }
    }
}

struct LivePreviewView: View {
    let artifact: LivePreview.Artifact
    @Environment(\.dismiss) private var dismiss
    @State private var reload = 0
    @State private var share: ExportMenu.ExportFile?

    var body: some View {
        NavigationStack {
            HTMLView(html: artifact.html, reload: reload)
                .ignoresSafeArea(edges: .bottom)
                .navigationTitle("Vorschau: \(artifact.title)")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) { Button("Schließen") { dismiss() } }
                    ToolbarItemGroup(placement: .primaryAction) {
                        Button { reload += 1 } label: { Image(systemName: "arrow.clockwise") }.accessibilityLabel("Neu laden")
                        Button {
                            let url = FileManager.default.temporaryDirectory.appendingPathComponent("vorschau.html")
                            if (try? artifact.html.write(to: url, atomically: true, encoding: .utf8)) != nil { share = .init(url: url) }
                        } label: { Image(systemName: "square.and.arrow.up") }.accessibilityLabel("Als HTML-Datei teilen")
                    }
                }
                .sheet(item: $share) { f in ShareSheet(items: [f.url]) }
        }
    }
}

/// Abgeschottete Web-Ansicht: kein Dateizugriff, Links öffnen in Safari.
struct HTMLView: UIViewRepresentable {
    let html: String
    var reload = 0

    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        return web
    }

    func updateUIView(_ web: WKWebView, context: Context) {
        guard context.coordinator.loaded != reload else { return }
        context.coordinator.loaded = reload
        web.loadHTMLString(html, baseURL: nil)
    }

    final class Coordinator: NSObject, WKNavigationDelegate {
        var loaded = -1
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping @MainActor (WKNavigationActionPolicy) -> Void) {
            if action.navigationType == .linkActivated, let url = action.request.url, ["http", "https", "mailto", "tel"].contains(url.scheme ?? "") {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }
    }
}
