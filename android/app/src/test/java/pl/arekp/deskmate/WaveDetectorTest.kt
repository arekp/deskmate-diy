package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.vision.WaveDetector
import pl.arekp.deskmate.vision.WaveDetector.Arm
import pl.arekp.deskmate.vision.WaveDetector.Event

class WaveDetectorTest {

    private fun raised(x: Float) = listOf(Arm(wristX = x, wristY = 0.2f, shoulderY = 0.5f, visible = true))
    private fun lowered(x: Float) = listOf(Arm(wristX = x, wristY = 0.7f, shoulderY = 0.5f, visible = true))

    /** Nadgarstek kołysze się lewo-prawo o ±amp wokół 0.5, co [stepMs]. */
    private fun swing(d: WaveDetector, start: Long, frames: Int, amp: Float, arms: (Float) -> List<Arm> = ::raised): List<Event> {
        val pattern = listOf(0f, amp, 0f, -amp)
        return (0 until frames).map { i -> d.update(start + i * 100L, arms(0.5f + pattern[i % 4])) }
    }

    @Test
    fun wavingRaisedHand_isDetectedOnce() {
        val d = WaveDetector()
        val events = swing(d, 0, 14, 0.08f)
        assertEquals(1, events.count { it == Event.WAVE })
    }

    @Test
    fun loweredHandMovement_isNotWave() {
        val d = WaveDetector()
        assertTrue(swing(d, 0, 20, 0.08f, ::lowered).none { it == Event.WAVE })
    }

    @Test
    fun tinyJitter_isNotWave() {
        val d = WaveDetector()
        assertTrue(swing(d, 0, 20, 0.01f).none { it == Event.WAVE })
    }

    @Test
    fun handHeldStill_becomesRaisedButNotWave() {
        val d = WaveDetector()
        var t = 0L
        repeat(15) { assertEquals(Event.NONE, d.update(t, raised(0.5f))); t += 100 }
        assertTrue(d.handRaised)
        d.update(t, lowered(0.5f))
        assertFalse(d.handRaised)
    }

    @Test
    fun cooldownPreventsRepeatedWaves() {
        val d = WaveDetector()
        val events = swing(d, 0, 26, 0.08f) // 2,6 s ciągłego machania
        assertEquals(1, events.count { it == Event.WAVE })
        val later = swing(d, 4000, 14, 0.08f)
        assertEquals(1, later.count { it == Event.WAVE })
    }

    @Test
    fun invisibleArmIsIgnored() {
        val d = WaveDetector()
        val events = (0 until 14).map { i ->
            d.update(i * 100L, listOf(Arm(0.5f + if (i % 2 == 0) 0.1f else -0.1f, 0.2f, 0.5f, visible = false)))
        }
        assertTrue(events.none { it == Event.WAVE })
    }
}
