package pl.arekp.deskmate.voice

import java.util.Locale

/**
 * Polecenia głosowe obsługiwane lokalnie, bez pytania modelu AI.
 */
object VoiceCommands {

    /**
     * "tryb debug", "włącz debug", "pokaż informacje techniczne" → true,
     * "wyłącz debug", "koniec trybu debug", "ukryj informacje techniczne" → false,
     * inne wypowiedzi → null.
     */
    fun debugToggle(text: String): Boolean? {
        val t = text.lowercase(Locale.forLanguageTag("pl")).replace(Regex("[^\\p{L}\\s]"), " ")
        val words = t.split(Regex("\\s+")).filter { it.isNotBlank() }
        val about = words.any { it.startsWith("debug") || it.startsWith("debag") || it == "dbg" } ||
            (words.any { it.startsWith("informacj") || it.startsWith("dan") } && words.any { it.startsWith("techniczn") })
        if (!about || words.size > 6) return null
        val off = words.any { it in OFF_WORDS || it.startsWith("wyłącz") || it.startsWith("ukryj") || it.startsWith("schowaj") }
        return !off
    }

    private val OFF_WORDS = setOf("koniec", "stop", "zakończ", "bez", "wyłącz", "off")
}
