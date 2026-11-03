package pl.arekp.deskmate

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.voice.BargeInVad
import kotlin.random.Random

class BargeInVadTest {

    private val rnd = Random(7)

    /** Echo głosu robota: sylaby, czyli poziom skacze między -58 a -46 dB. */
    private fun echo() = -58f + rnd.nextFloat() * 12f

    private fun feed(vad: BargeInVad, fromMs: Long, toMs: Long, level: () -> Float): Long? {
        var t = fromMs
        while (t < toMs) {
            if (vad.update(t, level())) return t
            t += 20
        }
        return null
    }

    @Test
    fun echoAloneNeverTriggers() {
        val vad = BargeInVad()
        assertFalse(feed(vad, 0, 10_000, ::echo) != null)
    }

    @Test
    fun userSpeakingOverEcho_triggersWithin400ms() {
        val vad = BargeInVad()
        assertFalse(feed(vad, 0, 2000, ::echo) != null)
        val at = feed(vad, 2000, 3000) { -30f + rnd.nextFloat() * 6f }
        assertTrue("nie wykrył mowy", at != null && at - 2000 <= 400)
    }

    @Test
    fun shortClick_doesNotTrigger() {
        val vad = BargeInVad()
        feed(vad, 0, 2000, ::echo)
        var t = 2000L
        var fired = false
        repeat(4) { fired = fired || vad.update(t, -15f); t += 20 } // 80 ms stuknięcia
        fired = fired || feed(vad, t, t + 1000, ::echo) != null
        assertFalse(fired)
    }

    @Test
    fun ignoresStartBeforeCalibration() {
        val vad = BargeInVad()
        assertFalse(feed(vad, 0, BargeInVad.CALIBRATION_MS - 20) { -20f } != null)
    }

    @Test
    fun quietRoomStillNeedsMinimumLevel() {
        val vad = BargeInVad()
        feed(vad, 0, 2000) { -80f }
        assertFalse(feed(vad, 2000, 3000) { -50f } != null) // szept poniżej MIN_LEVEL_DB
    }
}
