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
            .environmentObject(settings)
            .environmentObject(session)
            .preferredColorScheme(.dark)
            .tint(Theme.cyan)
            .background(Theme.backgroundGradient.ignoresSafeArea())
        }
        .onChange(of: scenePhase) { _, phase in
            session.handle(phase)
        }
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
