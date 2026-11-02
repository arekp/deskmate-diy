package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ui.Activity
import pl.arekp.deskmate.ui.TickerState
import java.time.LocalTime

class TickerTest {

    @Test
    fun emptyTicker_saysModelNotUsedYet() {
        assertEquals("MODEL: jeszcze nie użyty", TickerState().text())
        assertEquals(Activity.IDLE, TickerState().activity)
    }

    @Test
    fun showsHeardModelModeAndRobot() {
        val t = TickerState(
            heard = "jaka jest pogoda",
            model = "Gemini (gemini-flash-latest)",
            mode = "Automatycznie (najpierw darmowe)",
            robot = "robot: połączony",
        ).text()
        val segments = t.split(TickerState.SEPARATOR)
        assertEquals("ROZUMIEM: „jaka jest pogoda”", segments[0])
        assertEquals("MODEL: Gemini (gemini-flash-latest)", segments[1])
        assertEquals("TRYB: Automatycznie (najpierw darmowe)", segments[2])
        assertEquals("ROBOT: POŁĄCZONY", segments[3])
    }

    @Test
    fun events_newestFirstWithTime_andLimited() {
        var t = TickerState()
        repeat(TickerState.MAX_EVENTS + 3) { i -> t = t.withEvent("zdarzenie $i", LocalTime.of(9, i)) }
        assertEquals(TickerState.MAX_EVENTS, t.events.size)
        assertEquals("09:07 zdarzenie 7", t.events.first())
        assertTrue(t.text().endsWith("09:03 zdarzenie 3"))
        assertFalse(t.text().contains("zdarzenie 2"))
    }

    @Test
    fun longUtterance_isShortened() {
        val t = TickerState(heard = "a".repeat(500)).text()
        assertTrue(t.length < 200)
    }
}
