package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import pl.arekp.deskmate.ui.TickerState
import pl.arekp.deskmate.voice.VoiceCommands

class DebugModeTest {

    @Test
    fun techInfoHiddenUnlessDebug() {
        val t = TickerState().withTech("STT", "Google · w chmurze")
        assertFalse(t.text().contains("STT"))
        val d = t.copy(debug = true).withTech("TTS", "Samsung TTS")
        assertTrue(d.text().contains("DEBUG ▸ STT: Google · w chmurze · TTS: Samsung TTS"))
    }

    @Test
    fun techValueUpdatesInPlaceAndBlankRemoves() {
        val t = TickerState(debug = true).withTech("STT", "a").withTech("LLM", "1 s").withTech("STT", "b")
        assertEquals(listOf("STT" to "b", "LLM" to "1 s"), t.tech.toList())
        assertFalse(t.withTech("LLM", "").tech.containsKey("LLM"))
        assertTrue(TickerState(debug = true).text().contains("DEBUG ▸ brak danych"))
    }

    @Test
    fun debugVoiceCommands() {
        assertEquals(true, VoiceCommands.debugToggle("tryb debug"))
        assertEquals(true, VoiceCommands.debugToggle("Włącz debug."))
        assertEquals(true, VoiceCommands.debugToggle("pokaż informacje techniczne"))
        assertEquals(false, VoiceCommands.debugToggle("wyłącz tryb debug"))
        assertEquals(false, VoiceCommands.debugToggle("koniec debugu"))
        assertEquals(false, VoiceCommands.debugToggle("ukryj informacje techniczne"))
        assertNull(VoiceCommands.debugToggle("jaka jest pogoda"))
        assertNull(VoiceCommands.debugToggle("opowiedz mi długo o tym jak działa debugowanie programów w Kotlinie"))
    }
}

class SyllablesTest {
    @Test
    fun polishSyllables() {
        assertEquals(1, pl.arekp.deskmate.voice.Syllables.count("nie"))
        assertEquals(1, pl.arekp.deskmate.voice.Syllables.count("się"))
        assertEquals(2, pl.arekp.deskmate.voice.Syllables.count("robot"))
        assertEquals(2, pl.arekp.deskmate.voice.Syllables.count("Dzisiaj,"))
        assertEquals(4, pl.arekp.deskmate.voice.Syllables.count("telefonem"))
        assertEquals(1, pl.arekp.deskmate.voice.Syllables.count("w")) // samo „w” – też ruch ust
        assertEquals(0, pl.arekp.deskmate.voice.Syllables.count("…"))
    }
}

class StopCommandTest {
    @Test
    fun stopWords() {
        assertTrue(VoiceCommands.isStop("stop"))
        assertTrue(VoiceCommands.isStop("Cisza!"))
        assertTrue(VoiceCommands.isStop("dobra, wystarczy"))
        assertTrue(VoiceCommands.isStop("przestań"))
        assertFalse(VoiceCommands.isStop("przestań mówić o pogodzie i powiedz mi coś o kosmosie"))
        assertFalse(VoiceCommands.isStop("jaka jest pogoda"))
        assertFalse(VoiceCommands.isStop(""))
    }
}
