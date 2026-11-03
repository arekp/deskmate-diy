package pl.arekp.deskmate.voice

/**
 * Wykrywa, że użytkownik zaczął mówić, kiedy robot jeszcze mówi (przerwanie, ang. barge-in).
 * Czysta logika bez Androida – dostaje poziom głośności mikrofonu co ~20 ms.
 *
 * Mikrofon słyszy też głos robota (echo), więc próg jest względny: mowa użytkownika to poziom
 * o [marginDb] wyższy niż 90. percentyl echa z ostatnich [ECHO_WINDOW_MS], utrzymany przez
 * większość [TRIGGER_MS], i nie niższy niż [MIN_LEVEL_DB]. Pierwsze [CALIBRATION_MS] tylko uczy echa.
 */
class BargeInVad(private val marginDb: Float = DEFAULT_MARGIN_DB) {

    private val history = ArrayDeque<Pair<Long, Float>>()
    private var startedAt = -1L

    /** Ostatnio policzony próg (do podglądu w trybie debug). */
    var thresholdDb = MIN_LEVEL_DB
        private set

    /** Zwraca true, gdy uznał, że użytkownik mówi. */
    fun update(timeMs: Long, levelDb: Float): Boolean {
        if (startedAt < 0) startedAt = timeMs
        history.addLast(timeMs to levelDb)
        while (history.isNotEmpty() && timeMs - history.first().first > ECHO_WINDOW_MS) history.removeFirst()
        if (timeMs - startedAt < CALIBRATION_MS) return false

        // Echo = ramki sprzed okna wyzwalania (żeby własna mowa użytkownika nie podnosiła progu)
        val echo = history.filter { timeMs - it.first > TRIGGER_MS }.map { it.second }.sorted()
        val p90 = if (echo.isEmpty()) MIN_LEVEL_DB else echo[((echo.size - 1) * 0.9f).toInt()]
        thresholdDb = maxOf(p90 + marginDb, MIN_LEVEL_DB)

        val recent = history.filter { timeMs - it.first <= TRIGGER_MS }
        if (recent.size < MIN_FRAMES) return false
        val loud = recent.count { it.second >= thresholdDb }
        return loud >= recent.size * LOUD_FRACTION
    }

    companion object {
        const val DEFAULT_MARGIN_DB = 9f
        const val MIN_LEVEL_DB = -42f
        const val ECHO_WINDOW_MS = 1500L
        const val TRIGGER_MS = 300L
        const val CALIBRATION_MS = 400L
        const val MIN_FRAMES = 8
        const val LOUD_FRACTION = 0.6f
    }
}
