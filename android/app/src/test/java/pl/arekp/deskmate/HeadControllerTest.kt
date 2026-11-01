package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.HeadController

class HeadControllerTest {

    private var now = 1_000L
    private val sent = mutableListOf<String>()
    private lateinit var head: HeadController

    @Before
    fun setUp() {
        sent.clear()
        head = HeadController(DeviceProfile.GALAXY_S23, clock = { now }) { sent += it }
    }

    private fun step(face: FaceObservation) {
        head.onFace(face)
        now += Config.LOOK_INTERVAL_MS
    }

    @Test
    fun faceOnTheRight_turnsHeadRight() {
        step(FaceObservation(present = true, x = 0.5f, y = 0f, attentive = true))
        assertTrue("pan powinien wzrosnąć", head.pan > 0f)
        assertEquals(0f, head.tilt, 0.001f)
        assertTrue(sent.single().startsWith("L "))
    }

    @Test
    fun faceAbove_tiltsHeadUp() {
        step(FaceObservation(present = true, x = 0f, y = 0.5f))
        assertTrue(head.tilt > 0f)
    }

    @Test
    fun smallOffset_isInsideDeadzone() {
        step(FaceObservation(present = true, x = 0.02f, y = -0.02f))
        assertEquals(0f, head.pan, 0.001f)
        assertEquals(0f, head.tilt, 0.001f)
    }

    @Test
    fun commandFormat_usesDotDecimalsAndDuration() {
        step(FaceObservation(present = true, x = 1f, y = 0f))
        val parts = sent.single().split(" ")
        assertEquals("L", parts[0])
        assertEquals(4, parts.size)
        assertTrue(parts[1].contains('.') && !parts[1].contains(','))
        assertEquals("180", parts[3])
    }

    @Test
    fun commandsAreRateLimited() {
        val face = FaceObservation(present = true, x = 1f, y = 0f)
        head.onFace(face)
        head.onFace(face) // ten sam moment – bez nowej komendy
        assertEquals(1, sent.size)
        now += Config.LOOK_INTERVAL_MS
        head.onFace(face)
        assertEquals(2, sent.size)
    }

    @Test
    fun panIsClampedToLimits() {
        repeat(100) { step(FaceObservation(present = true, x = 1f, y = -1f)) }
        assertEquals(Config.PAN_MAX, head.pan, 0.001f)
        assertEquals(Config.TILT_MIN, head.tilt, 0.001f)
    }

    @Test
    fun pause_blocksTracking() {
        head.pauseFor(1_000)
        step(FaceObservation(present = true, x = 1f, y = 0f))
        assertTrue(sent.isEmpty())
        now += 1_000
        step(FaceObservation(present = true, x = 1f, y = 0f))
        assertEquals(1, sent.size)
    }

    @Test
    fun noFace_noCommand() {
        step(FaceObservation(present = false))
        assertTrue(sent.isEmpty())
    }

    @Test
    fun reset_returnsToCenter() {
        repeat(5) { step(FaceObservation(present = true, x = 1f, y = 1f)) }
        head.reset()
        assertEquals(0f, head.pan, 0.001f)
        assertEquals(0f, head.tilt, 0.001f)
    }
}
