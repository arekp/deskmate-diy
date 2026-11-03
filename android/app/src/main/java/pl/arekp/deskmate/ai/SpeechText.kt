package pl.arekp.deskmate.ai

/** Tekst do czytania na głos – bez urwanego zdania na końcu (gdy model trafił na limit długości). */
object SpeechText {
    private val END = Regex("[.!?…][\"”»)]*$")
    private val SENTENCE_END = Regex("[.!?…][\"”»)]*(?=\\s)")

    fun completeSentences(text: String): String {
        val t = text.trim()
        if (t.isEmpty() || END.containsMatchIn(t)) return t
        val lastEnd = SENTENCE_END.findAll(t).lastOrNull()?.range?.last ?: -1
        return if (lastEnd >= t.length * 0.4) t.substring(0, lastEnd + 1) else "$t…"
    }
}

/**
 * Niektóre darmowe modele piszą swoje rozumowanie w treści odpowiedzi („Użytkownik pyta o…”, <think>…</think>).
 * Usuwamy je, żeby robot nie czytał na głos swoich notatek.
 */
object ReasoningFilter {
    private val THINK_BLOCK = Regex("(?is)<(think|thinking|reasoning)>.*?</\\1>")
    private val THINK_OPEN = Regex("(?is)<(think|thinking|reasoning)>.*$")
    private val LEADING_TAG = Regex("^\\s*\\[[A-Za-z_ ,|]+]\\s*")
    private val META = listOf(
        "użytkownik ", "użytkowniczka ", "the user", "okay, so", "ok, so", "let me", "hmm, the user",
        "muszę ", "powinienem ", "moje poprzednie wyszukiwania", "analizuję", "zastanówmy się",
    )
    const val FALLBACK = "[confused] Przepraszam, pogubiłem się – możesz zapytać jeszcze raz?"

    private fun isMeta(text: String): Boolean {
        val body = LEADING_TAG.replace(text, "").trimStart().lowercase()
        return META.any { body.startsWith(it) }
    }

    fun clean(raw: String): String {
        var t = THINK_BLOCK.replace(raw, "")
        t = THINK_OPEN.replace(t, "").trim()
        if (!isMeta(t)) return t
        // Odpowiedź właściwa bywa po rozumowaniu: od ostatniego znacznika miny albo w ostatnim akapicie
        val lastTag = Regex("\\[[A-Za-z_ ,|]+]").findAll(t).map { it.range.first }.filter { it > 0 }.lastOrNull()
        if (lastTag != null && !isMeta(t.substring(lastTag))) return t.substring(lastTag).trim()
        val lastParagraph = t.split(Regex("\\n\\s*\\n")).lastOrNull()?.trim().orEmpty()
        if (lastParagraph.isNotBlank() && lastParagraph != t && !isMeta(lastParagraph)) return lastParagraph
        return FALLBACK
    }
}
