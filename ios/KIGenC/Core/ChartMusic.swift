import Foundation
import AVFoundation

/// Spielt beim App-Start die aktuelle Nr. 1 der Charts (gewähltes Genre) als 30-Sekunden-Hörprobe.
/// Quelle: öffentliche Deezer-API (ohne Schlüssel, ohne Anmeldung). Respektiert den Lautlos-Schalter
/// (Audio-Kategorie „ambient“) und spielt nicht, wenn bereits andere Musik läuft.
@MainActor
final class ChartMusic: ObservableObject {
    static let shared = ChartMusic()

    struct Track: Equatable {
        let title: String
        let artist: String
        let preview: URL
        let link: URL
        let genre: String
    }

    /// Musik-Genres der Deezer-Charts (Kennung, Name). 0 = alle Genres.
    static let genres: [(id: Int, name: String)] = [
        (0, "Alle Charts"), (132, "Pop"), (116, "Hip-Hop & Rap"), (152, "Rock"), (113, "Dance"),
        (106, "Electro"), (165, "R&B"), (85, "Alternative"), (464, "Metal"), (169, "Soul & Funk"),
        (197, "Latin"), (144, "Reggae"), (84, "Country"), (129, "Jazz"), (98, "Klassik"),
    ]

    static func genreName(_ id: Int) -> String { genres.first { $0.id == id }?.name ?? "Alle Charts" }

    @Published private(set) var track: Track?
    @Published private(set) var loading = false
    @Published var error: String?

    private var player: AVPlayer?
    private var endObserver: NSObjectProtocol?
    private var task: Task<Void, Never>?
    private var lastAutoPlay = Date.distantPast

    /// Beim App-Start: nur wenn eingeschaltet, höchstens alle 10 Minuten, nicht wenn schon Musik läuft.
    func playOnStart(settings: AppSettings) {
        guard settings.startMusic, Date().timeIntervalSince(lastAutoPlay) > 600 else { return }
        lastAutoPlay = Date()
        guard !AVAudioSession.sharedInstance().isOtherAudioPlaying else { return }
        play(genre: settings.musicGenre, quiet: true)
    }

    /// Nr. 1 des Genres laden und abspielen (auch zum Ausprobieren in den Optionen).
    func play(genre: Int, quiet: Bool = false) {
        stop()
        error = nil
        loading = true
        task = Task {
            do {
                var found = try await fetchTop(genre: genre)
                if found == nil, genre != 0 { found = try await fetchTop(genre: 0) }
                guard !Task.isCancelled else { return }
                guard let t = found else { throw URLError(.cannotParseResponse) }
                loading = false
                start(t)
            } catch {
                guard !Task.isCancelled else { return }
                loading = false
                ErrorLog.report(error, context: "Musik beim Start")
                if !quiet { self.error = "Die Charts konnten gerade nicht geladen werden." }
            }
        }
    }

    private func fetchTop(genre: Int) async throws -> Track? {
        var req = URLRequest(url: URL(string: "https://api.deezer.com/chart/\(genre)/tracks?limit=10")!)
        req.timeoutInterval = 15
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, response) = try await URLSession.shared.data(for: req)
        if let http = response as? HTTPURLResponse, !(200...299).contains(http.statusCode) { throw URLError(.badServerResponse) }
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any],
              let items = json["data"] as? [[String: Any]] else { return nil }
        for o in items {
            guard let p = o["preview"] as? String, !p.isEmpty, let preview = URL(string: p) else { continue }
            let title = (o["title_short"] as? String).flatMap { $0.isEmpty ? nil : $0 } ?? (o["title"] as? String ?? "")
            let artist = (o["artist"] as? [String: Any])?["name"] as? String ?? ""
            let id = o["id"].map { "\($0)" } ?? ""
            let link = URL(string: o["link"] as? String ?? "") ?? URL(string: "https://www.deezer.com/track/\(id)")!
            return Track(title: title, artist: artist, preview: preview, link: link, genre: Self.genreName(genre))
        }
        return nil
    }

    private func start(_ t: Track) {
        let session = AVAudioSession.sharedInstance()
        // „ambient“: verstummt beim Lautlos-Schalter und unterbricht keine andere Musik
        try? session.setCategory(.ambient, mode: .default)
        try? session.setActive(true)
        let item = AVPlayerItem(url: t.preview)
        let p = AVPlayer(playerItem: item)
        p.volume = 0
        player = p
        track = t
        endObserver = NotificationCenter.default.addObserver(forName: .AVPlayerItemDidPlayToEndTime, object: item, queue: .main) { [weak self] _ in
            Task { @MainActor in self?.stop() }
        }
        p.play()
        // Sanft einblenden
        Task {
            for step in 1...10 {
                try? await Task.sleep(nanoseconds: 150_000_000)
                guard player === p else { return }
                p.volume = Float(step) / 10 * 0.6
            }
        }
    }

    /// Wiedergabe beenden und Anzeige ausblenden.
    func stop() {
        task?.cancel()
        task = nil
        loading = false
        if let o = endObserver { NotificationCenter.default.removeObserver(o) }
        endObserver = nil
        if player != nil {
            player?.pause()
            player = nil
            try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        }
        track = nil
    }
}
