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
