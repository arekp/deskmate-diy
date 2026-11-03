package pl.arekp.deskmate.ai

import pl.arekp.deskmate.ui.Emotion

/**
 * Mina i gest zapisane przez model na początku odpowiedzi, np. „[happy nod] Jasne!”.
 * Dzięki temu emocja nie wymaga osobnego wywołania narzędzia (= drugiego zapytania do AI).
 */
data class Expression(val emotion: Emotion?, val gesture: String?, val text: String) {
    companion object {
        private val GESTURES = setOf("nod", "shake", "happy", "sad", "curious")
        private val TAG = Regex("""\[\s*([A-Za-z_ ,|]+?)\s*]""")

        /** Wyciąga pierwszy znacznik z listą znanych słów; usuwa wszystkie takie znaczniki z tekstu. */
        fun parse(raw: String): Expression {
            var emotion: Emotion? = null
            var gesture: String? = null
            val text = TAG.replace(raw) { m ->
                val words = m.groupValues[1].lowercase().split(Regex("[\\s,|]+")).filter { it.isNotBlank() }
                val known = words.filter { w -> Emotion.forModel.any { it.name.equals(w, true) } || w in GESTURES }
                if (known.isEmpty()) return@replace m.value // zwykły nawias w treści – zostaw
                if (emotion == null) {
                    emotion = words.firstNotNullOfOrNull { w -> Emotion.forModel.firstOrNull { it.name.equals(w, true) } }
                    // drugie słowo albo jedyne słowo-gest (np. „[nod]”)
                    gesture = words.drop(if (emotion != null) 1 else 0).firstOrNull { it in GESTURES }
                }
                ""
            }.replace(Regex("\\s{2,}"), " ").trim()
            return Expression(emotion, gesture, text)
        }
    }
}
