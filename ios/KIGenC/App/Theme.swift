import SwiftUI

extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255, blue: Double(hex & 0xFF) / 255)
    }
}

/// Ein App-Design („Skin“) – wie die austauschbaren Oberflächen von Synthesizer-Plugins.
struct AppDesign: Identifiable, Equatable {
    let id: String
    let name: String
    let tagline: String
    let dark: Bool
    let background: [Color]
    let primary: Color
    let secondary: Color
    let tertiary: Color
    let surface: Color
    let surfaceHigh: Color
    let bubble: Color
    let text: Color
    let textMuted: Color
    let corner: CGFloat
    var fontDesign: Font.Design = .default
    /// Textfarbe auf Akzentflächen (hell oder dunkel, je nach Akzent).
    var onAccent: Color = .white
    /// Eigene Akzentfarben für Verläufe (z. B. Regenbogen).
    var accents: [Color]? = nil
    /// Symbol des Designs (Asset-Name); nil = KI-GenC-Logo.
    var symbol: String? = nil
    /// Passendes App-Symbol (alternatives Icon); nil = Standard.
    var iconName: String? = nil

    static func == (a: AppDesign, b: AppDesign) -> Bool { a.id == b.id }

    static let all: [AppDesign] = [
        AppDesign(id: "original", name: "KI-GenC Original", tagline: "Nachtblau mit Cyan & Violett", dark: true,
                  background: [Color(hex: 0x00081B), Color(hex: 0x06103A), Color(hex: 0x150B3E)],
                  primary: Color(hex: 0x22C3F5), secondary: Color(hex: 0x8B5CF6), tertiary: Color(hex: 0x3B7BF2),
                  surface: Color(hex: 0x0F1838), surfaceHigh: Color(hex: 0x162049), bubble: Color(hex: 0x1A3C93),
                  text: Color(hex: 0xE6EAF5), textMuted: Color(hex: 0xA9B3D1), corner: 16),
        AppDesign(id: "neon", name: "Neon Synthwave", tagline: "Pink- und Cyan-Leuchten auf Tiefviolett", dark: true,
                  background: [Color(hex: 0x0D0221), Color(hex: 0x1B0B3A), Color(hex: 0x2E0F4F)],
                  primary: Color(hex: 0xFF2E97), secondary: Color(hex: 0x00F0FF), tertiary: Color(hex: 0xFFD319),
                  surface: Color(hex: 0x1A0838), surfaceHigh: Color(hex: 0x2A1050), bubble: Color(hex: 0x5A1450),
                  text: Color(hex: 0xF7E9FF), textMuted: Color(hex: 0xC3A6E0), corner: 14),
        AppDesign(id: "analog", name: "Analog Vintage", tagline: "Holz, Creme und Bernstein", dark: true,
                  background: [Color(hex: 0x1E140C), Color(hex: 0x2B1D12), Color(hex: 0x3A2717)],
                  primary: Color(hex: 0xFF9F1C), secondary: Color(hex: 0xE8D5B5), tertiary: Color(hex: 0xD9541E),
                  surface: Color(hex: 0x2C1E13), surfaceHigh: Color(hex: 0x3B291A), bubble: Color(hex: 0x6B3A12),
                  text: Color(hex: 0xF3E6D0), textMuted: Color(hex: 0xC2AE90), corner: 8, fontDesign: .serif, onAccent: Color(hex: 0x1E140C)),
        AppDesign(id: "graphite", name: "Studio Graphite", tagline: "Metall mit grünen LED-Akzenten", dark: true,
                  background: [Color(hex: 0x111214), Color(hex: 0x1A1C1F), Color(hex: 0x24272B)],
                  primary: Color(hex: 0x9EFF3E), secondary: Color(hex: 0x4FD1FF), tertiary: Color(hex: 0xFF5C5C),
                  surface: Color(hex: 0x1C1E21), surfaceHigh: Color(hex: 0x2A2D31), bubble: Color(hex: 0x2F3A24),
                  text: Color(hex: 0xE8EAED), textMuted: Color(hex: 0x9AA0A6), corner: 4, onAccent: Color(hex: 0x101214)),
        AppDesign(id: "terminal", name: "Retro Terminal", tagline: "Phosphor-Grün auf Schwarz", dark: true,
                  background: [Color(hex: 0x000000), Color(hex: 0x020A04), Color(hex: 0x04140A)],
                  primary: Color(hex: 0x33FF66), secondary: Color(hex: 0x00CC44), tertiary: Color(hex: 0xFFB000),
                  surface: Color(hex: 0x041008), surfaceHigh: Color(hex: 0x0A1E10), bubble: Color(hex: 0x0E3A1A),
                  text: Color(hex: 0xB8FFC8), textMuted: Color(hex: 0x5FAF72), corner: 2, fontDesign: .monospaced, onAccent: .black),
        AppDesign(id: "sunset", name: "Sunset Wave", tagline: "Violett, Magenta und Orange", dark: true,
                  background: [Color(hex: 0x1A0B2E), Color(hex: 0x3B0F3A), Color(hex: 0x5A1A2E)],
                  primary: Color(hex: 0xFF7A45), secondary: Color(hex: 0xFF3D8B), tertiary: Color(hex: 0xFFC857),
                  surface: Color(hex: 0x2A0E33), surfaceHigh: Color(hex: 0x3A1541), bubble: Color(hex: 0x6A1F45),
                  text: Color(hex: 0xFFE9E0), textMuted: Color(hex: 0xE0A9B5), corner: 22),
        AppDesign(id: "gold", name: "Midnight Gold", tagline: "Tiefschwarz mit Gold und Silber", dark: true,
                  background: [Color(hex: 0x050505), Color(hex: 0x0E0D0A), Color(hex: 0x17140C)],
                  primary: Color(hex: 0xE5C07B), secondary: Color(hex: 0xC9CCD1), tertiary: Color(hex: 0xB08A3E),
                  surface: Color(hex: 0x13110D), surfaceHigh: Color(hex: 0x201C14), bubble: Color(hex: 0x3A2F18),
                  text: Color(hex: 0xF2EBDD), textMuted: Color(hex: 0xADA394), corner: 6, fontDesign: .serif, onAccent: Color(hex: 0x0E0D0A)),
        AppDesign(id: "ocean", name: "Deep Ocean", tagline: "Petrol und Türkis mit Korallen-Akzent", dark: true,
                  background: [Color(hex: 0x001219), Color(hex: 0x002A32), Color(hex: 0x003B46)],
                  primary: Color(hex: 0x2EC4B6), secondary: Color(hex: 0x94D2BD), tertiary: Color(hex: 0xEE9B00),
                  surface: Color(hex: 0x00222A), surfaceHigh: Color(hex: 0x05323C), bubble: Color(hex: 0x0B4A52),
                  text: Color(hex: 0xE0F4F1), textMuted: Color(hex: 0x8DB8B3), corner: 18, onAccent: Color(hex: 0x001219)),
        AppDesign(id: "genc", name: "GenC Hologramm", tagline: "Schillernd: Cyan, Violett, Magenta", dark: true,
                  background: [Color(hex: 0x0A0F2E), Color(hex: 0x2A1660), Color(hex: 0x0E3E6C)],
                  primary: Color(hex: 0x3EE6FF), secondary: Color(hex: 0xFF5BD8), tertiary: Color(hex: 0x8B7BFF),
                  surface: Color(hex: 0x141A44), surfaceHigh: Color(hex: 0x1E2558), bubble: Color(hex: 0x3A2380),
                  text: Color(hex: 0xEAF2FF), textMuted: Color(hex: 0xA8B4E0), corner: 16, onAccent: Color(hex: 0x0A0F2E),
                  accents: [Color(hex: 0x3EE6FF), Color(hex: 0x8B7BFF), Color(hex: 0xFF5BD8)], symbol: "Symbol-genc", iconName: "AppIcon-GenC"),
        AppDesign(id: "vielfalt", name: "Vielfalt", tagline: "Viele Farben, ein Miteinander", dark: true,
                  background: [Color(hex: 0x1B1B2F), Color(hex: 0x2A2440), Color(hex: 0x162447)],
                  primary: Color(hex: 0xFFB84D), secondary: Color(hex: 0x4ECDC4), tertiary: Color(hex: 0xB45CFF),
                  surface: Color(hex: 0x232338), surfaceHigh: Color(hex: 0x2E2E48), bubble: Color(hex: 0x3D3060),
                  text: Color(hex: 0xF1EEFF), textMuted: Color(hex: 0xB3ADCF), corner: 18, onAccent: Color(hex: 0x1B1B2F),
                  accents: [Color(hex: 0xFF6B6B), Color(hex: 0xFFB84D), Color(hex: 0xFFE66D), Color(hex: 0x4ECDC4), Color(hex: 0x4D96FF), Color(hex: 0xB45CFF)],
                  symbol: "Symbol-vielfalt", iconName: "AppIcon-Vielfalt"),
        AppDesign(id: "vegan", name: "Vegan", tagline: "Blattgrün, Limette und Getreide", dark: true,
                  background: [Color(hex: 0x0B2A1A), Color(hex: 0x123D24), Color(hex: 0x1A4D2E)],
                  primary: Color(hex: 0x7ED957), secondary: Color(hex: 0xC9F27B), tertiary: Color(hex: 0xE0B15A),
                  surface: Color(hex: 0x12301F), surfaceHigh: Color(hex: 0x1A3F29), bubble: Color(hex: 0x245C34),
                  text: Color(hex: 0xE8F7E4), textMuted: Color(hex: 0xA6C9A0), corner: 20, onAccent: Color(hex: 0x0B2A1A),
                  symbol: "Symbol-vegan", iconName: "AppIcon-Vegan"),
        AppDesign(id: "pride", name: "Pride", tagline: "Regenbogenfarben auf Nachtviolett", dark: true,
                  background: [Color(hex: 0x140A24), Color(hex: 0x1E1235), Color(hex: 0x120A1E)],
                  primary: Color(hex: 0xFF6FB5), secondary: Color(hex: 0x4D9BFF), tertiary: Color(hex: 0xFFD23F),
                  surface: Color(hex: 0x1E1430), surfaceHigh: Color(hex: 0x2A1C42), bubble: Color(hex: 0x4A1F5E),
                  text: Color(hex: 0xFFF1FA), textMuted: Color(hex: 0xC9B3D6), corner: 22,
                  accents: [Color(hex: 0xE40303), Color(hex: 0xFF8C00), Color(hex: 0xFFED00), Color(hex: 0x008026), Color(hex: 0x004DFF), Color(hex: 0x750787)],
                  symbol: "Symbol-pride", iconName: "AppIcon-Pride"),
        AppDesign(id: "zweisamkeit", name: "Zweisamkeit", tagline: "Weinrot, Rosé und Gold – für zwei", dark: true,
                  background: [Color(hex: 0x2A0A14), Color(hex: 0x4A0F24), Color(hex: 0x3A0A1C)],
                  primary: Color(hex: 0xF2C76E), secondary: Color(hex: 0xF7A8B8), tertiary: Color(hex: 0xE86A8A),
                  surface: Color(hex: 0x3A0F1E), surfaceHigh: Color(hex: 0x4A1628), bubble: Color(hex: 0x6A1E38),
                  text: Color(hex: 0xFFEFF2), textMuted: Color(hex: 0xD9A7B2), corner: 16, fontDesign: .serif, onAccent: Color(hex: 0x2A0A14),
                  symbol: "Symbol-zweisamkeit", iconName: "AppIcon-Zweisamkeit"),
        AppDesign(id: "klassisch", name: "Klassisch", tagline: "Schlicht und hell – Weiß mit Indigo", dark: false,
                  background: [Color(hex: 0xFFFFFF), Color(hex: 0xF4F5F8), Color(hex: 0xECEEF3)],
                  primary: Color(hex: 0x3F51B5), secondary: Color(hex: 0x607D8B), tertiary: Color(hex: 0x2196F3),
                  surface: Color(hex: 0xFFFFFF), surfaceHigh: Color(hex: 0xEEF0F5), bubble: Color(hex: 0xDDE3F7),
                  text: Color(hex: 0x1A1C22), textMuted: Color(hex: 0x5F6470), corner: 12,
                  symbol: "Symbol-klassisch", iconName: "AppIcon-Klassisch"),
        AppDesign(id: "arctic", name: "Arctic Glass", tagline: "Hell: Eisweiß mit kühlem Blau", dark: false,
                  background: [Color(hex: 0xF7FAFD), Color(hex: 0xEAF1F9), Color(hex: 0xDCE7F5)],
                  primary: Color(hex: 0x0068C9), secondary: Color(hex: 0x5B5BD6), tertiary: Color(hex: 0x00897B),
                  surface: Color(hex: 0xFFFFFF), surfaceHigh: Color(hex: 0xEEF3F9), bubble: Color(hex: 0xD6E6FA),
                  text: Color(hex: 0x14202E), textMuted: Color(hex: 0x55657A), corner: 20),
    ]

    static func byID(_ id: String) -> AppDesign { all.first { $0.id == id } ?? all[0] }
}

/// Farben und Formen des aktuell gewählten Designs.
enum Theme {
    /// Wird von AppSettings gesetzt, wenn der Nutzer ein Design wählt.
    static var current: AppDesign = AppDesign.byID(UserDefaults.standard.string(forKey: "design") ?? "original")

    static var navy: Color { current.background[0] }
    static var navyMid: Color { current.background[1] }
    static var navyPurple: Color { current.background[2] }
    static var cyan: Color { current.primary }
    static var blue: Color { current.tertiary }
    static var purple: Color { current.secondary }
    static var card: Color { current.surface }
    static var cardHigh: Color { current.surfaceHigh }
    static var userBubble: Color { current.bubble }
    static var text: Color { current.text }
    static var secondaryText: Color { current.textMuted }
    static var onAccent: Color { current.onAccent }
    static var corner: CGFloat { current.corner }

    static var backgroundGradient: LinearGradient {
        LinearGradient(colors: current.background, startPoint: .top, endPoint: .bottom)
    }
    static var accentGradient: LinearGradient {
        LinearGradient(colors: current.accents ?? [current.primary, current.tertiary, current.secondary], startPoint: .leading, endPoint: .trailing)
    }
}

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: Theme.corner))
    }
}

extension View {
    func card() -> some View { modifier(CardStyle()) }

    /// Transparenter Hintergrund für Listen, damit der Design-Verlauf sichtbar bleibt.
    func clearListBackground() -> some View {
        scrollContentBackground(.hidden).background(Theme.backgroundGradient.ignoresSafeArea())
    }
}
