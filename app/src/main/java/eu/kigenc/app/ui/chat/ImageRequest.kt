package eu.kigenc.app.ui.chat

import java.util.Locale

/**
 * Erkennt, ob eine Chat-Nachricht ein Bild bestellt („Erstelle ein Bild von …“, „Zeichne eine Katze“,
 * „Kannst du mir ein Logo für meine Bäckerei malen?“). Bewusst vorsichtig: Fragen ÜBER Bilder
 * („Beschreibe das Foto“, „Mach eine Zusammenfassung des Fotos“) bleiben normale Chat-Fragen.
 */
object ImageRequest {
    private const val START = "^(hey |hallo |hi |bitte |kannst du( mir| uns)? |könntest du( mir| uns)? |würdest du( mir| uns)? |" +
        "ich (hätte|möchte|will|brauche) (gern |gerne )?)*"
    private const val NOUN = "[a-zäöüß-]*(bild|foto|grafik|illustration|logo|zeichnung|gemälde|poster|plakat|icon|wallpaper|" +
        "comic|cartoon|skizze|avatar|porträt|portrait|titelbild|profilbild)(s|er|n|en)?"
    private const val OBJ = "(ein|eine|einen|ein neues|eine neue|zwei|drei|vier|mehrere)\\s+([a-zäöüß-]+(e|es|en|er)\\s+)?$NOUN\\b"
    private const val FILLER = "((mir|uns|bitte|doch|mal|noch|schnell|kurz|jetzt|daraus)\\s+){0,3}"

    private val exclude = Regex("(prompt|beschreib|ideen?\\b|idee für|analysier|slogan|gedicht|was (ist|zeigt|siehst|bedeutet))")
    private val patterns = listOf(
        Regex(START + "(erstell|erzeug|generier|gestalt|kreier|entwirf|entwerf|mach|zeichn|mal)\\w*\\s+" + FILLER + OBJ),
        Regex(START + "(zeichne|zeichnen|male|malen|mal mir|mal uns)\\b"),
        Regex(START + OBJ + ".{0,120}\\b(malen|zeichnen|erstellen|erzeugen|generieren|machen|gestalten|entwerfen)\\b"),
        Regex("^(ich (hätte|möchte|will|brauche) (gern |gerne )?)" + OBJ + "\\s+(von|vom|mit|für|zum|zur|im|in|als)\\b"),
        Regex("^(please |can you )?(draw|paint|create|generate|make)\\b.{0,60}\\b(image|picture|photo|drawing|logo|illustration|poster)"),
    )

    fun matches(text: String): Boolean {
        val t = text.trim().lowercase(Locale.GERMAN)
        if (t.isEmpty() || t.length > 600) return false
        if (exclude.containsMatchIn(t)) return false
        return patterns.any { it.containsMatchIn(t) }
    }
}
