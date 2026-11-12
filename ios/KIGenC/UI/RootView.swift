import SwiftUI

struct RootView: View {
    @EnvironmentObject private var settings: AppSettings
    @State private var tab = 5
    @State private var showOnboarding = false
    @State private var whatsNew: [WhatsNewEntry] = []

    var body: some View {
        TabView(selection: $tab) {
            StartView(openTab: { tab = $0 })
                .tabItem { Label("Start", systemImage: "house") }.tag(5)
            ChatView()
                .tabItem { Label("Chat", systemImage: "bubble.left.and.bubble.right") }.tag(0)
            StudioView()
                .tabItem { Label("Studio", systemImage: "wand.and.stars") }.tag(1)
            ToolsView()
                .tabItem { Label("Werkzeuge", systemImage: "square.grid.2x2") }.tag(2)
            SettingsView()
                .tabItem { Label("Optionen", systemImage: "gearshape") }.tag(4)
        }
        .onAppear {
            let appearance = UITabBarAppearance()
            appearance.configureWithOpaqueBackground()
            appearance.backgroundColor = UIColor(Theme.navy)
            UITabBar.appearance().standardAppearance = appearance
            UITabBar.appearance().scrollEdgeAppearance = appearance
            let nav = UINavigationBarAppearance()
            nav.configureWithTransparentBackground()
            nav.titleTextAttributes = [.foregroundColor: UIColor(Theme.text)]
            nav.largeTitleTextAttributes = [.foregroundColor: UIColor(Theme.text)]
            UINavigationBar.appearance().standardAppearance = nav
            UINavigationBar.appearance().scrollEdgeAppearance = nav
            if !settings.onboardingDone { showOnboarding = true }
            // „Was ist neu“ einmal nach einem Update – nicht bei einer Neuinstallation
            whatsNew = WhatsNew.pending(freshInstall: !settings.onboardingDone)
            refreshModelsIfNeeded()
        }
        .sheet(isPresented: $showOnboarding) {
            OnboardingView { settings.onboardingDone = true; showOnboarding = false; if !settings.hasKey { tab = 4 } }
                .interactiveDismissDisabled()
        }
        .sheet(isPresented: Binding(get: { !whatsNew.isEmpty && !showOnboarding },
                                    set: { if !$0 { WhatsNew.markSeen(); whatsNew = [] } })) {
            WhatsNewView(entries: whatsNew) { WhatsNew.markSeen(); whatsNew = [] }
                .presentationDetents([.medium, .large])
        }
    }
}

extension RootView {
    /// Hält die KI-Modelle aktuell: lädt einmal pro Woche die Mistral-Modellliste neu und
    /// stellt auf das Standardmodell um, falls ein gewähltes Modell abgeschaltet wurde.
    func refreshModelsIfNeeded() {
        let own = settings.apiKey.trimmingCharacters(in: .whitespacesAndNewlines)
        let last = UserDefaults.standard.double(forKey: "models_refreshed_at")
        guard !own.isEmpty, Date().timeIntervalSince1970 - last > 7 * 86_400 else { return }
        Task {
            guard let models = try? await MistralClient.shared.listModels(key: own), !models.isEmpty else { return }
            UserDefaults.standard.set(Date().timeIntervalSince1970, forKey: "models_refreshed_at")
            settings.cachedModels = models
            if !models.contains(settings.chatModel) { settings.chatModel = "mistral-small-latest" }
            if !models.contains(settings.codeModel) { settings.codeModel = "codestral-latest" }
        }
    }
}

struct OnboardingView: View {
    let done: () -> Void

    private let items: [(String, String, String)] = [
        ("bubble.left.and.bubble.right", "Chat", "Frag die KI, häng Fotos, Videos oder Dateien an und lass dir Antworten vorlesen."),
        ("wand.and.stars", "Studio", "Texte, E-Mails, Berichte, Übersetzungen und Bilder erzeugen – mit Vorlagen."),
        ("square.grid.2x2", "Werkzeuge", "Fotos bearbeiten, Audio transkribieren, recherchieren, Daten analysieren, Aufgaben planen."),
        ("chevron.left.forwardslash.chevron.right", "Code", "Programme und Automationen erstellen und erklären lassen."),
        ("lock.shield", "Datenschutz", "Die KI läuft bei Mistral AI in der EU. Dein Schlüssel liegt sicher im Schlüsselbund."),
    ]

    var body: some View {
        ScrollView {
            VStack(spacing: 18) {
                Image("Logo").resizable().scaledToFit().frame(width: 110, height: 110)
                Text("Willkommen bei KI-GenC").font(.title.bold())
                ForEach(items, id: \.1) { icon, title, text in
                    HStack(alignment: .top, spacing: 14) {
                        Image(systemName: icon).font(.title2).foregroundStyle(Theme.cyan).frame(width: 34)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(title).font(.headline)
                            Text(text).font(.callout).foregroundStyle(Theme.secondaryText)
                        }
                        Spacer()
                    }
                    .card()
                }
                Text(Beta.sharedKey != nil ? "In der Testversion ist bereits ein Mistral-Schlüssel hinterlegt – du kannst sofort loslegen." : "Zum Start brauchst du einen kostenlosen API-Schlüssel von console.mistral.ai. Er wird unter „Optionen“ eingetragen.")
                    .font(.footnote).foregroundStyle(Theme.secondaryText).multilineTextAlignment(.center)
                GradientButton(title: "Los geht's", icon: "arrow.right", action: done)
            }
            .padding(20)
        }
        .background(Theme.backgroundGradient.ignoresSafeArea())
        .preferredColorScheme(Theme.current.dark ? .dark : .light)
    }
}

/// Zugangscode-Abfrage und Ablauf-Sperre der Testversion.
struct BetaGateView<Content: View>: View {
    @ViewBuilder let content: () -> Content
    @State private var unlocked = Beta.storedCode != nil
    @State private var keyReady = !Beta.hasSharedKey
    @State private var code = ""
    @State private var error: String?

    var body: some View {
        if !Beta.isBeta {
            content()
        } else if Beta.isExpired {
            gate {
                Image(systemName: "hourglass.bottomhalf.filled").font(.system(size: 54)).foregroundStyle(Theme.cyan)
                Text("Testphase beendet").font(.title.bold())
                Text("Die Testversion von KI-GenC war bis zum \(Beta.expiryText) gültig. Vielen Dank fürs Testen!")
                    .multilineTextAlignment(.center).foregroundStyle(Theme.secondaryText)
            }
        } else if unlocked {
            if keyReady {
                content()
            } else {
                ProgressView().frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Theme.backgroundGradient.ignoresSafeArea())
                    .task {
                        // Test-Schlüssel freischalten, damit Tester keinen eigenen Mistral-Schlüssel brauchen
                        if let c = Beta.storedCode {
                            _ = await Task.detached(priority: .userInitiated) { Beta.unlockSharedKey(c) }.value
                        }
                        AppSettings.shared.objectWillChange.send()
                        keyReady = true
                    }
            }
        } else {
            gate {
                Image("Logo").resizable().scaledToFit().frame(width: 100, height: 100)
                Text("KI-GenC Testversion").font(.title.bold())
                Text("Bitte gib deinen persönlichen Zugangscode ein. Gültig bis \(Beta.expiryText).")
                    .multilineTextAlignment(.center).foregroundStyle(Theme.secondaryText)
                // „KIG-“ steht fest davor – getippt werden nur die 8 Zeichen dahinter
                HStack(spacing: 4) {
                    Text("KIG-").font(.title3.monospaced()).foregroundStyle(Theme.secondaryText)
                    TextField("XXXX-XXXX", text: $code)
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                        .keyboardType(.asciiCapable)
                        .font(.title3.monospaced())
                }
                .padding(12)
                .background(Theme.cardHigh, in: RoundedRectangle(cornerRadius: 12))
                Text("Nur die 8 Zeichen nach „KIG-“ eingeben, z. B. ABCD-1234.")
                    .font(.footnote).foregroundStyle(Theme.secondaryText)
                if let error { Text(error).foregroundStyle(.orange).font(.callout) }
                GradientButton(title: "Freischalten", icon: "lock.open") {
                    if Beta.isValid(code) {
                        Beta.store(code)
                        unlocked = true
                    } else {
                        error = "Dieser Code ist nicht gültig."
                    }
                }
            }
        }
    }

    private func gate<V: View>(@ViewBuilder _ inner: () -> V) -> some View {
        VStack(spacing: 18) { inner() }
            .padding(28)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Theme.backgroundGradient.ignoresSafeArea())
    }
}
