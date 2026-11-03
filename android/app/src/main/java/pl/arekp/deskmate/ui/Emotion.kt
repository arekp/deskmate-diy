package pl.arekp.deskmate.ui

enum class Emotion {
    NEUTRAL, HAPPY, SAD, CURIOUS, THINKING, SLEEPY, SURPRISED, LISTENING,
    EXCITED, LOVE, CONFUSED, ANGRY;

    companion object {
        fun parse(name: String?): Emotion =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: NEUTRAL

        /** Emocje, które może wybrać model AI (LISTENING ustawia sama aplikacja). */
        val forModel: List<Emotion> get() = entries.filter { it != LISTENING }
    }
}
