package pl.arekp.deskmate.vision

/**
 * Rozpoznaje machanie dłonią i podniesioną rękę z punktów pozy (ML Kit Pose).
 * Czysta logika bez Androida – łatwa do testów.
 *
 * Współrzędne obrazu: x, y w 0..1, y rośnie w dół (jak w ML Kit).
 * Ręka "w górze" = nadgarstek wyżej niż bark. Machanie = ręka w górze i co najmniej
 * [MIN_REVERSALS] zmian kierunku ruchu nadgarstka w poziomie (każda o ≥ [MIN_SWING]) w ciągu [WINDOW_MS].
 */
class WaveDetector {

    data class Arm(val wristX: Float, val wristY: Float, val shoulderY: Float, val visible: Boolean)

    enum class Event { NONE, WAVE }

    /** Ręka trzymana w górze dłużej niż [RAISED_MS] (bez machania). */
    var handRaised = false
        private set

    private val xs = ArrayDeque<Pair<Long, Float>>()
    private var raisedSince = -1L
    private var cooldownUntil = 0L

    fun update(timeMs: Long, arms: List<Arm>): Event {
        val up = arms.filter { it.visible && it.wristY < it.shoulderY - RAISE_MARGIN }
            .minByOrNull { it.wristY } // wyżej podniesiona ręka
        if (up == null) {
            xs.clear()
            raisedSince = -1L
            handRaised = false
            return Event.NONE
        }
        if (raisedSince < 0) raisedSince = timeMs
        xs.addLast(timeMs to up.wristX)
        while (xs.isNotEmpty() && timeMs - xs.first().first > WINDOW_MS) xs.removeFirst()

        if (timeMs >= cooldownUntil && reversals() >= MIN_REVERSALS) {
            xs.clear()
            cooldownUntil = timeMs + COOLDOWN_MS
            handRaised = false
            raisedSince = timeMs
            return Event.WAVE
        }
        handRaised = timeMs - raisedSince >= RAISED_MS && reversals() < 2
        return Event.NONE
    }

    /** Liczba zmian kierunku ruchu nadgarstka, każda po wychyleniu o co najmniej [MIN_SWING]. */
    private fun reversals(): Int {
        val anchor = xs.firstOrNull()?.second ?: return 0
        var count = 0
        var dir = 0
        var peak = anchor
        for ((_, x) in xs) {
            when (dir) {
                0 -> when {
                    x - anchor >= MIN_SWING -> { dir = 1; peak = x }
                    anchor - x >= MIN_SWING -> { dir = -1; peak = x }
                }
                1 -> if (x > peak) peak = x else if (peak - x >= MIN_SWING) { count++; dir = -1; peak = x }
                else -> if (x < peak) peak = x else if (x - peak >= MIN_SWING) { count++; dir = 1; peak = x }
            }
        }
        return count
    }

    companion object {
        const val WINDOW_MS = 1800L
        const val MIN_REVERSALS = 3
        const val MIN_SWING = 0.04f
        const val RAISE_MARGIN = 0.02f
        const val RAISED_MS = 1200L
        const val COOLDOWN_MS = 3000L
    }
}
