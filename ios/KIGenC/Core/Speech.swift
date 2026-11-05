import Foundation
import AVFoundation
import Speech
import UserNotifications

/// Vorlesen mit der deutschen Systemstimme.
final class Speaker: NSObject, ObservableObject, AVSpeechSynthesizerDelegate {
    static let shared = Speaker()
    private let synth = AVSpeechSynthesizer()
    @Published var speaking = false

    override init() {
        super.init()
        synth.delegate = self
    }

    func speak(_ text: String) {
        stop()
        let clean = text
            .replacingOccurrences(of: "```[\\s\\S]*?```", with: " Codeblock. ", options: .regularExpression)
            .replacingOccurrences(of: "[*#_`>|]", with: "", options: .regularExpression)
        try? AVAudioSession.sharedInstance().setCategory(.playback, mode: .spokenAudio)
        try? AVAudioSession.sharedInstance().setActive(true)
        let u = AVSpeechUtterance(string: clean)
        u.voice = AVSpeechSynthesisVoice(language: "de-DE")
        u.rate = AVSpeechUtteranceDefaultSpeechRate
        speaking = true
        synth.speak(u)
    }

    func stop() {
        if synth.isSpeaking { synth.stopSpeaking(at: .immediate) }
        speaking = false
    }

    func speechSynthesizer(_ synthesizer: AVSpeechSynthesizer, didFinish utterance: AVSpeechUtterance) {
        DispatchQueue.main.async { self.speaking = false }
    }
}

/// Spracheingabe mit der Apple-Spracherkennung (Deutsch).
final class Dictation: ObservableObject {
    @Published var listening = false
    @Published var transcript = ""
    @Published var error: String?

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "de-DE"))
    private let engine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?

    func toggle(onResult: @escaping (String) -> Void) {
        if listening { stop(); return }
        SFSpeechRecognizer.requestAuthorization { status in
            DispatchQueue.main.async {
                guard status == .authorized else { self.error = "Spracherkennung nicht erlaubt (Einstellungen › KI-GenC)."; return }
                AVAudioApplication.requestRecordPermission { ok in
                    DispatchQueue.main.async {
                        guard ok else { self.error = "Mikrofon nicht erlaubt (Einstellungen › KI-GenC)."; return }
                        self.start(onResult: onResult)
                    }
                }
            }
        }
    }

    private func start(onResult: @escaping (String) -> Void) {
        guard let recognizer, recognizer.isAvailable else { error = "Spracherkennung gerade nicht verfügbar."; return }
        transcript = ""
        error = nil
        do {
            let s = AVAudioSession.sharedInstance()
            try s.setCategory(.record, mode: .measurement, options: .duckOthers)
            try s.setActive(true, options: .notifyOthersOnDeactivation)
            let req = SFSpeechAudioBufferRecognitionRequest()
            req.shouldReportPartialResults = true
            request = req
            let input = engine.inputNode
            let format = input.outputFormat(forBus: 0)
            input.removeTap(onBus: 0)
            input.installTap(onBus: 0, bufferSize: 1024, format: format) { buffer, _ in req.append(buffer) }
            engine.prepare()
            try engine.start()
            listening = true
            task = recognizer.recognitionTask(with: req) { [weak self] result, err in
                guard let self else { return }
                if let result {
                    DispatchQueue.main.async {
                        self.transcript = result.bestTranscription.formattedString
                        if result.isFinal { onResult(self.transcript); self.stop() }
                    }
                }
                if err != nil {
                    DispatchQueue.main.async {
                        if !self.transcript.isEmpty { onResult(self.transcript) }
                        self.stop()
                    }
                }
            }
        } catch {
            self.error = "Aufnahme fehlgeschlagen: \(error.localizedDescription)"
            stop()
        }
    }

    func stop() {
        if engine.isRunning {
            engine.stop()
            engine.inputNode.removeTap(onBus: 0)
        }
        request?.endAudio()
        task?.finish()
        request = nil
        task = nil
        listening = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }
}

/// Erinnerungen für Aufgaben über lokale Mitteilungen.
enum Reminders {
    static func requestPermission() {
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { _, _ in }
    }

    static func schedule(_ t: TaskItem) {
        guard let due = t.due, due > Date(), !t.done else { return }
        requestPermission()
        let content = UNMutableNotificationContent()
        content.title = "KI-GenC Aufgabe"
        content.body = t.title
        content.sound = .default
        let comps = Calendar.current.dateComponents([.year, .month, .day, .hour, .minute], from: due)
        let trigger = UNCalendarNotificationTrigger(dateMatching: comps, repeats: false)
        UNUserNotificationCenter.current().add(UNNotificationRequest(identifier: t.id.uuidString, content: content, trigger: trigger))
    }

    static func cancel(_ id: UUID) {
        UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: [id.uuidString])
    }
}
