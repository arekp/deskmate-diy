package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import pl.arekp.deskmate.ai.Expression
import pl.arekp.deskmate.ui.Emotion

class ExpressionTagTest {
    @Test
    fun emotionAndGesture() {
        val e = Expression.parse("[happy nod] Jasne, już mówię!")
        assertEquals(Emotion.HAPPY, e.emotion)
        assertEquals("nod", e.gesture)
        assertEquals("Jasne, już mówię!", e.text)
    }

    @Test
    fun emotionOnly_andCaseInsensitive() {
        val e = Expression.parse("  [Curious]  Co to jest?")
        assertEquals(Emotion.CURIOUS, e.emotion)
        assertNull(e.gesture)
        assertEquals("Co to jest?", e.text)
    }

    @Test
    fun happyAsGestureAfterEmotion() {
        val e = Expression.parse("[excited happy] Hura!")
        assertEquals(Emotion.EXCITED, e.emotion)
        assertEquals("happy", e.gesture)
    }

    @Test
    fun noTag_textUnchanged() {
        val e = Expression.parse("Zwykła odpowiedź [1] bez miny.")
        assertNull(e.emotion)
        assertEquals("Zwykła odpowiedź [1] bez miny.", e.text)
    }

    @Test
    fun unknownWordsInBracketStay() {
        assertEquals("To jest [przypis] tekst.", Expression.parse("To jest [przypis] tekst.").text)
    }

    @Test
    fun tagInMiddleIsRemovedFromSpeech() {
        val e = Expression.parse("Dobrze. [sad shake] Niestety nie wiem.")
        assertEquals(Emotion.SAD, e.emotion)
        assertEquals("shake", e.gesture)
        assertEquals("Dobrze. Niestety nie wiem.", e.text)
    }
}

class SpeechTextTest {
    @Test
    fun completeAnswerUnchanged() {
        assertEquals("To jest pełne zdanie. I drugie!", pl.arekp.deskmate.ai.SpeechText.completeSentences("To jest pełne zdanie. I drugie!"))
    }

    @Test
    fun cutAnswer_dropsUnfinishedLastSentence() {
        assertEquals(
            "Pierwsze zdanie jest całe. Drugie też.",
            pl.arekp.deskmate.ai.SpeechText.completeSentences("Pierwsze zdanie jest całe. Drugie też. A trzecie urwało si"),
        )
    }

    @Test
    fun shortCutAnswer_getsEllipsis() {
        assertEquals("Krótko. A potem bardzo długie zdanie bez końca…",
            pl.arekp.deskmate.ai.SpeechText.completeSentences("Krótko. A potem bardzo długie zdanie bez końca"))
    }
}
