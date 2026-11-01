package pl.arekp.deskmate

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ai.ExpressTool
import pl.arekp.deskmate.ai.TimeTool
import pl.arekp.deskmate.ui.Emotion

class ToolsTest {

    @Test
    fun expressTool_passesEmotionAndGesture() = runBlocking {
        var got: Pair<Emotion, String?>? = null
        val tool = ExpressTool { e, g -> got = e to g }
        val result = tool.run(JSONObject().put("emotion", "happy").put("gesture", "nod"))
        assertEquals(Emotion.HAPPY to "nod", got)
        assertFalse(result.isError)
    }

    @Test
    fun expressTool_gestureNoneMeansNoGesture() = runBlocking {
        var gesture: String? = "x"
        ExpressTool { _, g -> gesture = g }.run(JSONObject().put("emotion", "sad").put("gesture", "none"))
        assertNull(gesture)
    }

    @Test
    fun expressTool_schemaListsGesturesSupportedByFirmware() {
        val gestures = ExpressTool { _, _ -> }.inputSchema
            .getJSONObject("properties").getJSONObject("gesture").getJSONArray("enum")
        val firmwareGestures = setOf("nod", "shake", "happy", "sad", "curious", "sleep", "wake")
        for (i in 0 until gestures.length()) {
            val g = gestures.getString(i)
            assertTrue("gest $g nieobsługiwany przez firmware", g == "none" || g in firmwareGestures)
        }
    }

    @Test
    fun timeTool_returnsText() = runBlocking {
        val result = TimeTool().run(JSONObject())
        assertTrue(result.text.isNotBlank())
    }

    @Test
    fun emotionParse_isForgiving() {
        assertEquals(Emotion.HAPPY, Emotion.parse("Happy "))
        assertEquals(Emotion.NEUTRAL, Emotion.parse(null))
        assertEquals(Emotion.NEUTRAL, Emotion.parse("nieznana"))
    }
}
