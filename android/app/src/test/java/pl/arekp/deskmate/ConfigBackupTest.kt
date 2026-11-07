package pl.arekp.deskmate

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigBackupTest {
    @Test
    fun roundTripKeepsTypes() {
        val values = mapOf<String, Any?>(
            "geminiKey" to "AIza-test", "debugMode" to true, "configVersion" to 1, "x" to 3L, "f" to 1.5f,
            "mcpServers" to """[{"name":"ha","url":"http://ha:8123/api/mcp","token":"t","enabled":true}]""",
        )
        val json = ConfigBackup.toJson(values, "0.1.0", "2026-11-07 15:30")
        assertEquals(values, ConfigBackup.parse(json))
    }

    @Test
    fun rejectsForeignFiles() {
        val e = runCatching { ConfigBackup.parse("""{"hello":"world"}""") }.exceptionOrNull()
        assertTrue(e is IllegalArgumentException)
        val newer = runCatching { ConfigBackup.parse("""{"format":"deskmate-config","formatVersion":99,"settings":{}}""") }.exceptionOrNull()
        assertTrue(newer?.message.orEmpty().contains("nowszej"))
    }
}
