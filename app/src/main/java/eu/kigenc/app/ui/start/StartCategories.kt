package eu.kigenc.app.ui.start

import eu.kigenc.app.ui.tools.Tool

/** Eine Auswahlgruppe (z. B. „Ton“ mit Freundlich, Förmlich …). */
data class OptionGroup(val key: String, val label: String, val options: List<String>, val default: String? = null)

/** Ein Freitextfeld (z. B. „Zielgruppe“). */
data class ExtraField(val key: String, val label: String, val hint: String)

enum class StartMode { TEXT, RESEARCH, IMAGE, TOOL, CODE }

data class StartCategory(
    val id: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val mode: StartMode,
    val contentLabel: String = "",
    val contentHint: String = "",
    val groups: List<OptionGroup> = emptyList(),
    val extras: List<ExtraField> = emptyList(),
    /** Aufgabenbeschreibung für die KI. */
    val instruction: String = "",
    val tool: Tool? = null,
)

private val LENGTH = OptionGroup("length", "Länge", listOf("Kurz", "Mittel", "Ausführlich"), "Mittel")
private val LANGUAGE = OptionGroup("language", "Sprache", listOf("Deutsch", "Englisch", "Französisch", "Spanisch", "Italienisch", "Niederländisch", "Polnisch", "Türkisch"), "Deutsch")

val START_CATEGORIES = listOf(
    StartCategory(
        "write", "✍️", "Schreiben", "E-Mail, Brief, Bericht, Post …", StartMode.TEXT,
        "Worum geht es?", "z. B. Absage für den Termin am Freitag, freundlich, neuen Termin vorschlagen",
        listOf(
            OptionGroup("type", "Art", listOf("E-Mail", "Brief", "Bericht", "Social-Media-Post", "Bewerbung", "Geschichte", "Rede", "Produkttext", "Einladung", "Gedicht"), "E-Mail"),
            OptionGroup("tone", "Ton", listOf("Neutral", "Freundlich", "Förmlich", "Locker", "Überzeugend", "Humorvoll", "Emotional"), "Freundlich"),
            LENGTH, LANGUAGE,
        ),
        listOf(ExtraField("audience", "Empfänger / Zielgruppe", "z. B. mein Vermieter, Kunden, Kollegen"), ExtraField("must", "Muss enthalten", "z. B. Datum, Ansprechpartner, Frist")),
        "Schreibe den gewünschten Text fertig zum Verwenden. Bei E-Mails mit Betreffzeile, bei Briefen nach DIN 5008.",
    ),
    StartCategory(
        "translate", "🌍", "Übersetzen", "In 20+ Sprachen", StartMode.TEXT,
        "Text", "Text, der übersetzt werden soll",
        listOf(
            OptionGroup("target", "Zielsprache", listOf("Englisch", "Französisch", "Spanisch", "Italienisch", "Niederländisch", "Polnisch", "Portugiesisch", "Türkisch", "Ukrainisch", "Griechisch", "Tschechisch", "Deutsch"), "Englisch"),
            OptionGroup("style", "Stil", listOf("Natürlich", "Wörtlich", "Förmlich", "Locker"), "Natürlich"),
        ),
        instruction = "Übersetze den Text. Gib nur die Übersetzung aus, danach (falls sinnvoll) kurze Hinweise zu Begriffen.",
    ),
    StartCategory(
        "analyze", "🔎", "Analysieren", "Text, Vertrag, Argumente prüfen", StartMode.TEXT,
        "Inhalt", "Text hier einfügen",
        listOf(
            OptionGroup("what", "Was ist es?", listOf("Text", "Vertrag / Dokument", "E-Mail", "Argumentation", "Zahlen / Daten", "Angebot"), "Text"),
            OptionGroup("focus", "Fokus", listOf("Zusammenfassung", "Kernaussagen", "Stärken & Schwächen", "Risiken & Fallstricke", "Faktencheck", "Antwortvorschlag"), "Zusammenfassung"),
            OptionGroup("format", "Format", listOf("Stichpunkte", "Fließtext", "Tabelle"), "Stichpunkte"),
        ),
        listOf(ExtraField("question", "Konkrete Frage (optional)", "z. B. Kann ich vorzeitig kündigen?")),
        "Analysiere den Inhalt gründlich und gezielt nach dem gewählten Fokus.",
    ),
    StartCategory(
        "research", "🌐", "Recherchieren", "Aktuell im Web mit Quellen", StartMode.RESEARCH,
        "Frage", "z. B. Welche E-Autos unter 30.000 € gibt es aktuell?",
        listOf(
            OptionGroup("depth", "Tiefe", listOf("Kurzüberblick", "Gründlich"), "Gründlich"),
            OptionGroup("format", "Format", listOf("Stichpunkte", "Fließtext", "Vergleichstabelle"), "Stichpunkte"),
        ),
    ),
    StartCategory(
        "plan", "🗓️", "Planen", "Projekt, Reise, Event, Woche", StartMode.TEXT,
        "Was soll geplant werden?", "z. B. Geburtstagsfeier für 20 Personen im Garten",
        listOf(
            OptionGroup("type", "Art", listOf("Projekt", "Reise", "Event / Feier", "Wochenplan", "Lernplan", "Trainingsplan", "Umzug", "Essensplan"), "Projekt"),
            OptionGroup("period", "Zeitraum", listOf("1 Tag", "1 Woche", "1 Monat", "3 Monate", "Offen"), "1 Woche"),
            OptionGroup("format", "Format", listOf("Checkliste", "Zeitplan", "Tabelle"), "Checkliste"),
        ),
        listOf(ExtraField("constraints", "Rahmenbedingungen", "z. B. Budget 300 €, 2 Kinder, ab 14 Uhr")),
        "Erstelle einen konkreten, realistischen Plan mit Schritten, Zeiten und Hinweisen.",
    ),
    StartCategory(
        "learn", "🎓", "Lernen", "Erklären, Quiz, Karteikarten", StartMode.TEXT,
        "Thema", "z. B. Photosynthese, Bruchrechnen, Französische Revolution",
        listOf(
            OptionGroup("level", "Niveau", listOf("Kind (10 J.)", "Einsteiger", "Fortgeschritten", "Experte"), "Einsteiger"),
            OptionGroup("form", "Form", listOf("Erklärung", "Zusammenfassung", "Quiz mit Lösungen", "Karteikarten", "Übungsaufgaben", "Eselsbrücken"), "Erklärung"),
            LENGTH,
        ),
        instruction = "Erkläre bzw. bereite den Lernstoff didaktisch gut auf, mit Beispielen.",
    ),
    StartCategory(
        "code", "💻", "Programmieren", "Code schreiben & erklären", StartMode.CODE,
        "Aufgabe", "z. B. Skript, das alle Fotos eines Ordners nach Datum umbenennt",
        listOf(
            OptionGroup("lang", "Sprache", listOf("Python", "JavaScript", "Kotlin", "Swift", "Java", "C#", "SQL", "Bash", "HTML/CSS", "Excel-Formel"), "Python"),
            OptionGroup("task", "Aufgabe", listOf("Neu schreiben", "Erklären", "Fehler finden", "Verbessern", "Tests schreiben"), "Neu schreiben"),
        ),
        instruction = "Liefere vollständigen, lauffähigen Code in Markdown-Codeblöcken mit kurzer Erklärung.",
    ),
    StartCategory(
        "image", "🎨", "Bild erstellen", "Aus einer Beschreibung", StartMode.IMAGE,
        "Was soll zu sehen sein?", "z. B. Ein Leuchtturm an der Nordsee bei Sonnenuntergang",
        listOf(
            OptionGroup("style", "Stil", listOf("Fotorealistisch", "Illustration", "Aquarell", "3D-Render", "Comic", "Ölgemälde", "Minimalistisch", "Anime"), "Fotorealistisch"),
            OptionGroup("format", "Format", listOf("Quadrat", "Hochformat", "Querformat"), "Quadrat"),
            OptionGroup("mood", "Stimmung", listOf("Hell & freundlich", "Dramatisch", "Mystisch", "Warm", "Kühl", "Verträumt"), "Hell & freundlich"),
        ),
    ),
    StartCategory("photo", "🖼️", "Foto bearbeiten", "Filter & KI-Bearbeitung", StartMode.TOOL, tool = Tool.PHOTO),
    StartCategory("transcribe", "🎙️", "Transkribieren", "Sprache & Audio zu Text", StartMode.TOOL, tool = Tool.TRANSCRIBE),
    StartCategory("ideas", "💡", "Idee festhalten", "Moment, Memo, Skizze", StartMode.TOOL, tool = Tool.IDEAS),
    StartCategory("data", "📊", "Daten auswerten", "Tabellen & Statistik", StartMode.TOOL, tool = Tool.DATA),
)

fun categoryById(id: String?) = START_CATEGORIES.firstOrNull { it.id == id }
