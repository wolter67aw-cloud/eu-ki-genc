import Foundation

/// Erkennt, ob eine Chat-Nachricht ein Bild bestellt („Erstelle ein Bild von …“, „Zeichne eine Katze“,
/// „Kannst du mir ein Logo für meine Bäckerei malen?“). Fragen ÜBER Bilder bleiben normale Chat-Fragen.
/// Gleiche Regeln wie Android (ImageRequest.kt).
enum ImageRequest {
    private static let start = "^(hey |hallo |hi |bitte |kannst du( mir| uns)? |könntest du( mir| uns)? |würdest du( mir| uns)? |ich (hätte|möchte|will|brauche) (gern |gerne )?)*"
    private static let noun = "[a-zäöüß-]*(bild|foto|grafik|illustration|logo|zeichnung|gemälde|poster|plakat|icon|wallpaper|comic|cartoon|skizze|avatar|porträt|portrait|titelbild|profilbild)(s|er|n|en)?"
    private static let obj = "(ein|eine|einen|ein neues|eine neue|zwei|drei|vier|mehrere)\\s+([a-zäöüß-]+(e|es|en|er)\\s+)?" + noun + "\\b"
    private static let filler = "((mir|uns|bitte|doch|mal|noch|schnell|kurz|jetzt|daraus)\\s+){0,3}"
    private static let exclude = "(prompt|beschreib|ideen?\\b|idee für|analysier|slogan|gedicht|was (ist|zeigt|siehst|bedeutet))"
    private static let patterns = [
        start + "(erstell|erzeug|generier|gestalt|kreier|entwirf|entwerf|mach|zeichn|mal)\\w*\\s+" + filler + obj,
        start + "(zeichne|zeichnen|male|malen|mal mir|mal uns)\\b",
        start + obj + ".{0,120}\\b(malen|zeichnen|erstellen|erzeugen|generieren|machen|gestalten|entwerfen)\\b",
        "^(ich (hätte|möchte|will|brauche) (gern |gerne )?)" + obj + "\\s+(von|vom|mit|für|zum|zur|im|in|als)\\b",
        "^(please |can you )?(draw|paint|create|generate|make)\\b.{0,60}\\b(image|picture|photo|drawing|logo|illustration|poster)",
    ]

    static func matches(_ text: String) -> Bool {
        let t = text.trimmingCharacters(in: .whitespacesAndNewlines).lowercased(with: Locale(identifier: "de_DE"))
        guard !t.isEmpty, t.count <= 600 else { return false }
        if t.range(of: exclude, options: .regularExpression) != nil { return false }
        return patterns.contains { t.range(of: $0, options: .regularExpression) != nil }
    }
}
