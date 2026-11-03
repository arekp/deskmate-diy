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

/**
 * Przybliżona liczba sylab w polskim słowie = liczba grup samogłosek
 * ("nie", "się" → 1, "robot" → 2, "dzień dobry" liczone osobno dla słów). Do ruchu ust.
 */
object Syllables {
    private const val VOWELS = "aeiouyąęó"

    fun count(word: String): Int {
        var groups = 0
        var inVowel = false
        for (c in word.lowercase(Locale.forLanguageTag("pl"))) {
            val v = c in VOWELS
            if (v && !inVowel) groups++
            inVowel = v
        }
        return if (groups == 0 && word.any { it.isLetterOrDigit() }) 1 else groups
    }
}
