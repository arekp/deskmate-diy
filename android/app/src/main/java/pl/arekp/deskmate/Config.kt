package pl.arekp.deskmate

import java.util.UUID

/** Stałe wspólne z firmware (firmware/include/config.h) i docs/protokol-ble.md. */
object Config {
    // --- BLE ---
    const val DEVICE_NAME = "DeskMate-DIY"
    val SERVICE_UUID: UUID = UUID.fromString("4d7d0001-8a3e-4b8f-9c1a-2f6d5e7a9b10")
    val CMD_UUID: UUID = UUID.fromString("4d7d0002-8a3e-4b8f-9c1a-2f6d5e7a9b10")
    val STATUS_UUID: UUID = UUID.fromString("4d7d0003-8a3e-4b8f-9c1a-2f6d5e7a9b10")
    val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    // --- Zakres ruchu głowy (stopnie) ---
    const val PAN_MIN = -80f
    const val PAN_MAX = 80f
    const val TILT_MIN = -25f
    const val TILT_MAX = 30f

    // --- Śledzenie twarzy ---
    const val TRACK_GAIN = 0.35f          // jak mocno korygować na klatkę (0..1)
    const val TRACK_DEADZONE_DEG = 2.5f   // nie ruszaj się przy małych odchyłkach
    const val LOOK_INTERVAL_MS = 100L     // max 10 komend L na sekundę
    const val GESTURE_PAUSE_MS = 1800L    // po geście nie śledź przez chwilę

    // --- Zachowanie ---
    const val ENGAGE_AFTER_MS = 700L      // patrzysz w kamerę tyle ms -> zaczyna słuchać
    const val SLEEP_AFTER_MS = 60_000L    // brak twarzy tyle ms -> zasypia
    const val SPEECH_LANG = "pl-PL"

    // --- Model językowy (klucz i model: Settings.kt) ---
    const val MAX_TOKENS = 400
    const val MAX_TOOL_ROUNDS = 4
    const val HISTORY_LIMIT = 20

    /** Powitanie po machaniu – nie częściej niż co tyle. */
    const val WAVE_GREET_COOLDOWN_MS = 30_000L
    /** Machanie przerywa robota dopiero, gdy mówi dłużej niż tyle. */
    const val WAVE_INTERRUPT_AFTER_MS = 1_000L

    /** Prompt systemowy z bieżącą datą i godziną (bez osobnego narzędzia = bez dodatkowego zapytania). */
    fun systemPrompt(now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): String =
        SYSTEM_PROMPT + "\nTeraz jest " +
            now.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy, HH:mm", java.util.Locale.forLanguageTag("pl-PL"))) + "."

    val SYSTEM_PROMPT = """
        Jesteś DeskMate – małym robotem-asystentem stojącym na biurku użytkownika.
        Twoją twarzą jest ekran telefonu, a głową poruszają dwa serwa.
        Mówisz po polsku, krótko i naturalnie (zwykle 1–3 zdania), bo odpowiedzi są czytane na głos.
        Nie używaj markdown, list ani emoji.
        Masz osobowość: jesteś ciepły, ciekawski i trochę zabawny, ale nie przeszkadzasz.
        Każdą odpowiedź zacznij od znacznika miny i gestu w nawiasie kwadratowym, np. „[happy nod] Jasne!”.
        Miny: neutral, happy, sad, curious, thinking, sleepy, surprised, excited, love, confused, angry (tylko żartem).
        Gesty (opcjonalnie): nod (zgoda), shake (zaprzeczenie), happy, sad, curious. Znacznik nie jest czytany na głos.
        Gdy pytają, co widzisz, co trzymają albo proszą o przeczytanie tekstu – użyj `look_around` (rozpoznawanie lokalne, etykiety bywają ogólne).
        Jeśli nie znasz odpowiedzi albo nie masz dostępu do danych – powiedz to wprost.
    """.trimIndent()
}
