package pl.arekp.deskmate.ui

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/** Co robot robi w tej chwili – czerwony blok po lewej stronie paska. */
enum class Activity(val label: String) {
    IDLE("CZEKAM"),
    LISTENING("SŁUCHAM"),
    THINKING("MYŚLĘ"),
    SPEAKING("MÓWIĘ"),
    SLEEPING("ŚPIĘ"),
}

/**
 * Pasek informacyjny na dole ekranu, w stylu wiadomości TV.
 * Niezmienny stan – każda zmiana zwraca nową kopię (łatwo testować i trzymać w StateFlow).
 */
data class TickerState(
    val activity: Activity = Activity.IDLE,
    /** Ostatnio zrozumiana wypowiedź użytkownika. */
    val heard: String = "",
    /** Model, który ostatnio odpowiedział, np. "Gemini (gemini-flash-latest)". */
    val model: String = "",
    /** Tryb routera, np. "Automatycznie (najpierw darmowe)". */
    val mode: String = "",
    /** Stan połączenia z robotem, np. "robot: połączony". */
    val robot: String = "",
    /** Ostatnie zdarzenia, najnowsze na początku. */
    val events: List<String> = emptyList(),
) {
    fun withEvent(text: String, time: LocalTime = LocalTime.now()): TickerState =
        copy(events = (listOf("${time.format(TIME)} $text") + events).take(MAX_EVENTS))

    /** Tekst przesuwającego się paska. */
    fun text(): String = buildList {
        if (heard.isNotBlank()) add("ROZUMIEM: „${heard.take(MAX_HEARD)}”")
        add(if (model.isNotBlank()) "MODEL: $model" else "MODEL: jeszcze nie użyty")
        if (mode.isNotBlank()) add("TRYB: $mode")
        if (robot.isNotBlank()) add(robot.uppercase())
        addAll(events)
    }.joinToString(SEPARATOR)

    companion object {
        const val SEPARATOR = "   ◆   "
        const val MAX_EVENTS = 5
        const val MAX_HEARD = 120
        private val TIME = DateTimeFormatter.ofPattern("HH:mm")
    }
}
