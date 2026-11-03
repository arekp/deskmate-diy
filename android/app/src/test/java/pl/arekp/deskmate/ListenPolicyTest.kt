package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.voice.ListenPolicy
import pl.arekp.deskmate.voice.ListenPolicy.Trigger

class ListenPolicyTest {

    private val p = ListenPolicy()

    @Test
    fun lookingAloneDoesNotStartListening() {
        assertNull(p.next(1_000, someoneHere = true, engaged = true, asleep = false))
        assertTrue(p.wantsVoiceDetection(engaged = true, asleep = false))
    }

    @Test
    fun voiceWhileLooking_listensOnce() {
        p.onVoice(1_000)
        assertEquals(Trigger.VOICE, p.next(1_200, someoneHere = true, engaged = true, asleep = false))
        p.onListened()
        assertNull(p.next(1_300, someoneHere = true, engaged = true, asleep = false))
    }

    @Test
    fun voiceWithoutLooking_isIgnored_andGoesStale() {
        p.onVoice(1_000)
        assertNull(p.next(1_100, someoneHere = true, engaged = false, asleep = false))
        assertNull(p.next(1_000 + ListenPolicy.VOICE_FRESH_MS + 1, someoneHere = true, engaged = true, asleep = false))
    }

    @Test
    fun followUpOnlyOnceAndOnlyWhenSomeoneIsHere() {
        p.onAnswered(0)
        assertNull(p.next(100, someoneHere = false, engaged = false, asleep = false))
        assertEquals(Trigger.FOLLOW_UP, p.next(200, someoneHere = true, engaged = false, asleep = false))
        p.onListened() // cisza po dopytaniu
        assertNull(p.next(300, someoneHere = true, engaged = true, asleep = false))
    }

    @Test
    fun followUpWindowExpires() {
        p.onAnswered(0)
        assertNull(p.next(ListenPolicy.FOLLOW_UP_MS + 1, someoneHere = true, engaged = true, asleep = false))
    }

    @Test
    fun tapAndInterruptWorkEvenAsleepOrNotLooking() {
        p.onTap()
        assertEquals(Trigger.TAP, p.next(0, someoneHere = false, engaged = false, asleep = true))
        p.onListened()
        p.onInterrupted()
        assertEquals(Trigger.INTERRUPTED, p.next(0, someoneHere = false, engaged = false, asleep = false))
    }

    @Test
    fun asleep_noVoiceDetection() {
        assertFalse(p.wantsVoiceDetection(engaged = true, asleep = true))
    }
}
