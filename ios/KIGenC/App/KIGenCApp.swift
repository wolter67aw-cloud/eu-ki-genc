import SwiftUI

@main
struct KIGenCApp: App {
    @StateObject private var settings = AppSettings.shared
    @StateObject private var session = SessionController()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            BetaGateView {
                RootView()
                    .id(session.sessionID) // neue Sitzung → alle Eingaben leer
            }
            .id(settings.design) // Design gewechselt → Oberfläche neu aufbauen
            .modifier(AppTextSize(steps: settings.textSize))
            .environmentObject(settings)
            .environmentObject(session)
            .preferredColorScheme(AppDesign.byID(settings.design).dark ? .dark : .light)
            .fontDesign(AppDesign.byID(settings.design).fontDesign)
            .tint(AppDesign.byID(settings.design).primary)
            .background(Theme.backgroundGradient.ignoresSafeArea())
        }
        .onChange(of: scenePhase) { _, phase in
            session.handle(phase)
            // App verlassen → Start-Musik verstummt
            if phase == .background { ChartMusic.shared.stop() }
        }
    }
}

/// Größere Schrift in der App: hebt die Textgröße des Geräts (Dynamic Type) um 0–3 Stufen an.
struct AppTextSize: ViewModifier {
    let steps: Int
    @Environment(\.dynamicTypeSize) private var deviceSize

    func body(content: Content) -> some View {
        let all = DynamicTypeSize.allCases
        let index = all.firstIndex(of: deviceSize) ?? all.startIndex
        let target = all[min(index + max(steps, 0), all.count - 1)]
        content.dynamicTypeSize(target)
    }
}

/// Startet beim erneuten Öffnen der App (nach mehr als 30 Sekunden im Hintergrund) eine leere Sitzung.
final class SessionController: ObservableObject {
    @Published var sessionID = UUID()
    private var backgroundSince: Date?
    /// Während Kamera, Fotoauswahl usw. offen sind, nicht zurücksetzen.
    var externalPickerActive = false

    func handle(_ phase: ScenePhase) {
        switch phase {
        case .background:
            backgroundSince = Date()
        case .active:
            if let since = backgroundSince, !externalPickerActive, Date().timeIntervalSince(since) > 30 {
                sessionID = UUID()
            }
            backgroundSince = nil
        default:
            break
        }
    }
}
