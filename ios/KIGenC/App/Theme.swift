import SwiftUI

/// Farben des KI-GenC-Logos: Nachtblau, Cyan → Blau → Violett.
enum Theme {
    static let navy = Color(red: 0 / 255, green: 8 / 255, blue: 27 / 255)
    static let navyMid = Color(red: 6 / 255, green: 16 / 255, blue: 58 / 255)
    static let navyPurple = Color(red: 21 / 255, green: 11 / 255, blue: 62 / 255)
    static let cyan = Color(red: 34 / 255, green: 195 / 255, blue: 245 / 255)
    static let blue = Color(red: 59 / 255, green: 123 / 255, blue: 242 / 255)
    static let purple = Color(red: 139 / 255, green: 92 / 255, blue: 246 / 255)
    static let card = Color(red: 15 / 255, green: 24 / 255, blue: 56 / 255)
    static let cardHigh = Color(red: 22 / 255, green: 32 / 255, blue: 73 / 255)
    static let userBubble = Color(red: 26 / 255, green: 60 / 255, blue: 147 / 255)
    static let secondaryText = Color(red: 169 / 255, green: 179 / 255, blue: 209 / 255)

    static let backgroundGradient = LinearGradient(colors: [navy, navyMid, navyPurple], startPoint: .top, endPoint: .bottom)
    static let accentGradient = LinearGradient(colors: [cyan, blue, purple], startPoint: .leading, endPoint: .trailing)
}

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(14)
            .background(Theme.card, in: RoundedRectangle(cornerRadius: 16))
    }
}

extension View {
    func card() -> some View { modifier(CardStyle()) }

    /// Transparenter Hintergrund für Listen, damit der Logo-Verlauf sichtbar bleibt.
    func clearListBackground() -> some View {
        scrollContentBackground(.hidden).background(Theme.backgroundGradient.ignoresSafeArea())
    }
}
