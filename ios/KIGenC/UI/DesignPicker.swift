import SwiftUI

/// Auswahl der App-Designs als „Synthesizer-Skins“ mit Live-Vorschau.
struct DesignPicker: View {
    @Binding var selected: String

    var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 12) {
                ForEach(AppDesign.all) { d in
                    Button { selected = d.id } label: { DesignCard(design: d, selected: d.id == selected) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.vertical, 4)
        }
    }
}

private struct DesignCard: View {
    let design: AppDesign
    let selected: Bool

    var body: some View {
        let d = design
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottomTrailing) {
                VStack(spacing: 8) {
                    // „Display“ des Geräts
                    Text("KI-GENC")
                        .font(.system(size: 13, weight: .bold, design: d.fontDesign)).kerning(2)
                        .foregroundStyle(d.primary)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 8).frame(height: 30)
                        .background(d.surface, in: RoundedRectangle(cornerRadius: d.corner * 0.5))
                        .overlay(RoundedRectangle(cornerRadius: d.corner * 0.5).stroke(d.primary.opacity(0.5)))
                        .overlay(alignment: .trailing) {
                            if let sym = d.symbol {
                                Image(sym).resizable().scaledToFill().frame(width: 22, height: 22)
                                    .clipShape(RoundedRectangle(cornerRadius: 5)).padding(.trailing, 4)
                            }
                        }
                    // Drehregler
                    HStack {
                        Knob(design: d, accent: d.primary, value: 0.72)
                        Spacer()
                        Knob(design: d, accent: d.secondary, value: 0.4)
                        Spacer()
                        Knob(design: d, accent: d.tertiary, value: 0.58)
                    }
                    // Fader
                    HStack(alignment: .bottom, spacing: 6) {
                        ForEach(Array([0.8, 0.45, 0.65, 0.3, 0.9, 0.55].enumerated()), id: \.offset) { i, v in
                            RoundedRectangle(cornerRadius: d.corner * 0.25)
                                .fill([d.primary, d.secondary, d.tertiary][i % 3].opacity(0.85))
                                .frame(height: 40 * v)
                        }
                    }
                    .frame(height: 40, alignment: .bottom)
                }
                .padding(10)
                if selected {
                    Image(systemName: "checkmark.circle.fill").font(.title3).foregroundStyle(d.primary).padding(6)
                }
            }
            .frame(width: 156, height: 176)
            .background(LinearGradient(colors: d.background, startPoint: .top, endPoint: .bottom), in: RoundedRectangle(cornerRadius: max(4, d.corner)))
            .overlay(RoundedRectangle(cornerRadius: max(4, d.corner)).stroke(selected ? Theme.cyan : d.textMuted.opacity(0.35), lineWidth: selected ? 3 : 1))
            Text(d.name).font(.caption.bold()).foregroundStyle(Theme.text).lineLimit(1)
            Text(d.tagline).font(.caption2).foregroundStyle(Theme.secondaryText).lineLimit(2).frame(width: 156, alignment: .leading)
        }
    }
}

private struct Knob: View {
    let design: AppDesign
    let accent: Color
    let value: Double

    var body: some View {
        ZStack {
            Circle().trim(from: 0, to: 0.75).stroke(design.textMuted.opacity(0.3), style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(135))
            Circle().trim(from: 0, to: 0.75 * value).stroke(accent, style: StrokeStyle(lineWidth: 4, lineCap: .round))
                .rotationEffect(.degrees(135))
            Circle().fill(design.surface).padding(6)
            Rectangle().fill(accent).frame(width: 2.5, height: 9).offset(y: -6)
                .rotationEffect(.degrees(-135 + 270 * value))
        }
        .frame(width: 36, height: 36)
    }
}

/// Wählbare App-Symbole für den Home-Bildschirm (alternative App-Icons).
struct AppIconChoice: Identifiable {
    let id: String
    let label: String
    let iconName: String?   // nil = Standard-Symbol
    let preview: String     // Asset für die Vorschau

    static let all: [AppIconChoice] = [
        AppIconChoice(id: "standard", label: "KI-GenC", iconName: nil, preview: "Logo"),
        AppIconChoice(id: "genc", label: "GenC Hologramm", iconName: "AppIcon-GenC", preview: "Symbol-genc"),
        AppIconChoice(id: "vielfalt", label: "Vielfalt", iconName: "AppIcon-Vielfalt", preview: "Symbol-vielfalt"),
        AppIconChoice(id: "vegan", label: "Vegan", iconName: "AppIcon-Vegan", preview: "Symbol-vegan"),
        AppIconChoice(id: "pride", label: "Pride", iconName: "AppIcon-Pride", preview: "Symbol-pride"),
        AppIconChoice(id: "zweisamkeit", label: "Zweisamkeit", iconName: "AppIcon-Zweisamkeit", preview: "Symbol-zweisamkeit"),
        AppIconChoice(id: "klassisch", label: "Klassisch", iconName: "AppIcon-Klassisch", preview: "Symbol-klassisch"),
    ]
}

struct AppIconPicker: View {
    @State private var current: String? = UIApplication.shared.alternateIconName
    @State private var error: String?

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 14) {
                    ForEach(AppIconChoice.all) { c in
                        Button { set(c.iconName) } label: {
                            VStack(spacing: 4) {
                                Image(c.preview).resizable().scaledToFill().frame(width: 60, height: 60)
                                    .clipShape(RoundedRectangle(cornerRadius: 14))
                                    .overlay(RoundedRectangle(cornerRadius: 14).stroke(current == c.iconName ? Theme.cyan : .clear, lineWidth: 3))
                                Text(c.label).font(.caption2).foregroundStyle(Theme.text).lineLimit(1)
                            }
                            .frame(width: 72)
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(.vertical, 4)
            }
            if let error { Text(error).font(.caption).foregroundStyle(.orange) }
        }
    }

    func set(_ name: String?) {
        guard UIApplication.shared.supportsAlternateIcons else { error = "Dieses Gerät unterstützt keine anderen App-Symbole."; return }
        UIApplication.shared.setAlternateIconName(name) { err in
            DispatchQueue.main.async {
                if let err { error = err.localizedDescription } else { current = name; error = nil }
            }
        }
    }
}
