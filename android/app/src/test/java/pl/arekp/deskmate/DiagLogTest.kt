package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagLogTest {
    @Test
    fun knownKeysAndPatternsAreRedacted() {
        val gemini = "AIzaSyD-abcdefghijklmnopqrstuvwxyz012345"
        val text = "url?key=$gemini header Authorization: Bearer sk-or-v1-0123456789abcdef " +
            "x-api-key: sk-ant-api03-zzzzzzzzzzzz moj-sekret-token-123"
        val out = DiagLog.redact(text, known = listOf("moj-sekret-token-123"))
        assertFalse(out, out.contains(gemini))
        assertFalse(out, out.contains("sk-or-v1"))
        assertFalse(out, out.contains("sk-ant-api03"))
        assertFalse(out, out.contains("moj-sekret-token-123"))
        assertTrue(out, out.contains("Bearer ***"))
    }

    @Test
    fun normalTextUntouched() {
        val t = "Gemini (gemini-flash-latest): HTTP 429 limit zapytań, 1840 ms"
        assertEquals(t, DiagLog.redact(t, known = listOf("", "abc")))
    }

    @Test
    fun withoutInitLoggingIsNoOp() {
        DiagLog.i("Test", "nic się nie stanie")
        assertEquals("", DiagLog.contents())
    }
}
