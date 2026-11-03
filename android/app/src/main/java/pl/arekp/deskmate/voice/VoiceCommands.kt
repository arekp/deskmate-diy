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

    /**
     * Pytania o godzinę i datę – odpowiedź od razu, bez modelu AI. Null, gdy to inne pytanie.
     */
    fun localAnswer(text: String, now: java.time.LocalDateTime = java.time.LocalDateTime.now()): String? {
        val t = text.lowercase(Locale.forLanguageTag("pl"))
        if (t.split(Regex("\\s+")).size > 7) return null
        val pl = Locale.forLanguageTag("pl-PL")
        return when {
            Regex("któr(a|ej) (jest )?godzin|ile (jest )?czasu|jaka (jest )?godzina").containsMatchIn(t) ->
                "Jest ${now.format(java.time.format.DateTimeFormatter.ofPattern("H:mm"))}."
            Regex("jaki (dziś|dzisiaj|mamy|jest)( dzisiaj| dziś)? (dzień|data)|któr(y|ego) (dziś|dzisiaj|jest|mamy)|jaka (jest )?(dziś |dzisiaj )?data").containsMatchIn(t) ->
                "Dziś jest " + now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM", pl)) + "."
            else -> null
        }
    }

    /** Krótkie „stop / cisza / przestań / wystarczy” – robot milknie, bez pytania modelu. */
    fun isStop(text: String): Boolean {
        val words = text.lowercase(Locale.forLanguageTag("pl")).replace(Regex("[^\\p{L}\\s]"), " ")
            .split(Regex("\\s+")).filter { it.isNotBlank() }
        if (words.isEmpty() || words.size > 3) return false
        return words.any { w -> STOP_WORDS.any { w.startsWith(it) } } && words.none { it.startsWith("debug") }
    }

    private val STOP_WORDS = listOf("stop", "cisza", "przestań", "wystarczy", "dość", "nieważne", "zamilcz", "milcz", "starczy")

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
