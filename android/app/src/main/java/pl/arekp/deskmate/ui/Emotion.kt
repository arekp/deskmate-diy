package pl.arekp.deskmate.ui

enum class Emotion {
    NEUTRAL, HAPPY, SAD, CURIOUS, THINKING, SLEEPY, SURPRISED, LISTENING;

    companion object {
        fun parse(name: String?): Emotion =
            entries.firstOrNull { it.name.equals(name?.trim(), ignoreCase = true) } ?: NEUTRAL
    }
}
