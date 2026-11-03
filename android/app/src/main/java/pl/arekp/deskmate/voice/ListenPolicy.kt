package pl.arekp.deskmate.voice

/**
 * Kiedy robot ma słuchać (uruchomić rozpoznawanie mowy). Czysta logika – łatwa do testów.
 *
 * Dawniej: słuchał, dopóki widział twarz, czyli po każdej ciszy znowu (z „pipnięciem”).
 * Teraz tylko na wyraźny sygnał:
 *  - [Trigger.TAP] – dotknięcie ekranu,
 *  - [Trigger.INTERRUPTED] – przerwanie robota w trakcie mówienia,
 *  - [Trigger.FOLLOW_UP] – RAZ zaraz po odpowiedzi (dopytanie), jeśli ktoś jest przed robotem,
 *  - [Trigger.VOICE] – lokalny wykrywacz głosu usłyszał mowę, a użytkownik patrzy na robota.
 * Po ciszy nic się nie dzieje, dopóki nie przyjdzie nowy sygnał.
 */
class ListenPolicy(
    private val followUpMs: Long = FOLLOW_UP_MS,
    private val voiceFreshMs: Long = VOICE_FRESH_MS,
) {
    enum class Trigger(val label: String) {
        TAP("dotyk"), INTERRUPTED("przerwanie"), FOLLOW_UP("dopytanie"), VOICE("głos"),
    }

    @Volatile private var tap = false
    @Volatile private var interrupted = false
    @Volatile private var followUpUntil = 0L
    @Volatile private var voiceAt = 0L

    fun onTap() { tap = true }
    fun onInterrupted() { interrupted = true }
    /** Robot skończył odpowiadać (nie przerwany) – otwórz okno na jedno dopytanie. */
    fun onAnswered(now: Long) { followUpUntil = now + followUpMs }
    /** Wykrywacz głosu usłyszał mowę. */
    fun onVoice(now: Long) { voiceAt = now }

    /** Słuchanie się odbyło – zużywa wszystkie sygnały (także okno dopytania: tylko jedno). */
    fun onListened() {
        tap = false
        interrupted = false
        followUpUntil = 0L
        voiceAt = 0L
    }

    /**
     * @param someoneHere twarz widoczna teraz albo przed chwilą,
     * @param engaged twarz patrzy w kamerę od dłuższej chwili,
     */
    fun next(now: Long, someoneHere: Boolean, engaged: Boolean, asleep: Boolean): Trigger? = when {
        tap -> Trigger.TAP
        interrupted -> Trigger.INTERRUPTED
        asleep -> null
        followUpUntil > now && someoneHere -> Trigger.FOLLOW_UP
        voiceAt > 0 && now - voiceAt <= voiceFreshMs && engaged -> Trigger.VOICE
        else -> null
    }

    /** Czy w spoczynku nasłuchiwać głosu (mikrofon tylko z pomiarem głośności). */
    fun wantsVoiceDetection(engaged: Boolean, asleep: Boolean): Boolean = engaged && !asleep

    companion object {
        const val FOLLOW_UP_MS = 6_000L
        const val VOICE_FRESH_MS = 1_500L
    }
}
