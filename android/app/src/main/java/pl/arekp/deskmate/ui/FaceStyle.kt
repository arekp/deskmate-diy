package pl.arekp.deskmate.ui

/**
 * Jak wygląda twarz w danej emocji – same liczby, bez rysowania (łatwe do testów i strojenia).
 * Rysowanie: EyesScreen.kt. Wartości są animowane płynnie przy zmianie emocji.
 */
data class EyeStyle(
    /** Wysokość oka (1 = normalna). */
    val open: Float = 1f,
    /** Szerokość oka (1 = normalna). */
    val width: Float = 1f,
    /** Nachylenie górnej powieki w stopniach: + opada zewnętrzny kącik (smutek), − wewnętrzny (złość). */
    val lidAngle: Float = 0f,
    /** Jak nisko schodzi górna powieka (0 = wcale, 0,5 = do połowy oka). */
    val lidCover: Float = 0f,
    /** Uśmiech oczu – zakrycie od dołu łukiem (0 = wcale, 0,6 = oczy jak ^ ^). */
    val bottomCover: Float = 0f,
    /** Zaokrąglenie rogów: 0,2 = prostokąt z zaokrągleniem, 0,5 = owal. */
    val roundness: Float = 0.32f,
    /** Mnożnik wielkości lewego / prawego oka (asymetria: ciekawość, zmieszanie). */
    val leftScale: Float = 1f,
    val rightScale: Float = 1f,
)

data class MouthStyle(
    /** Wygięcie: +1 szeroki uśmiech, 0 prosto, −1 smutna podkówka. */
    val curve: Float = 0.25f,
    /** Szerokość (1 = normalna). */
    val width: Float = 1f,
    /** Otwarcie bez mówienia (0 = zamknięte, 1 = szeroko). */
    val open: Float = 0f,
    /** Usta w kształcie „O” (zdziwienie). */
    val round: Boolean = false,
)

enum class Accessory { NONE, BLUSH, HEARTS, ZZZ, DOTS, QUESTION, SPARKLE }

data class FaceStyle(
    val eye: EyeStyle,
    val mouth: MouthStyle,
    /** Kolor oczu i ust (ARGB). */
    val color: Long,
    val accessory: Accessory = Accessory.NONE,
    /** Podskakiwanie oczu (radosne podekscytowanie), amplituda jako ułamek wysokości oka. */
    val bounce: Float = 0f,
) {
    companion object {
        const val CYAN = 0xFF7FE7FFL
        const val WARM = 0xFF9CF7D8L
        const val BLUE = 0xFF6FA8FFL
        const val PINK = 0xFFFF8AC8L
        const val RED = 0xFFFF6B5EL
        const val VIOLET = 0xFFB9A2FFL

        fun of(emotion: Emotion): FaceStyle = when (emotion) {
            Emotion.NEUTRAL -> FaceStyle(EyeStyle(), MouthStyle(), CYAN)
            Emotion.HAPPY -> FaceStyle(
                EyeStyle(open = 1f, bottomCover = 0.55f, roundness = 0.45f),
                MouthStyle(curve = 1f, width = 1.15f, open = 0.25f), WARM, Accessory.BLUSH,
            )
            Emotion.SAD -> FaceStyle(
                EyeStyle(open = 0.85f, lidAngle = 22f, lidCover = 0.18f),
                MouthStyle(curve = -0.8f, width = 0.8f), BLUE,
            )
            Emotion.CURIOUS -> FaceStyle(
                EyeStyle(open = 1.05f, lidAngle = -6f, leftScale = 0.85f, rightScale = 1.18f),
                MouthStyle(curve = 0.1f, width = 0.6f, open = 0.15f, round = true), CYAN, Accessory.QUESTION,
            )
            Emotion.THINKING -> FaceStyle(
                EyeStyle(open = 0.75f, lidCover = 0.12f, lidAngle = -4f),
                MouthStyle(curve = -0.1f, width = 0.55f), VIOLET, Accessory.DOTS,
            )
            Emotion.SLEEPY -> FaceStyle(
                EyeStyle(open = 0.2f, lidCover = 0.1f, roundness = 0.45f),
                MouthStyle(curve = 0f, width = 0.45f, open = 0.2f, round = true), BLUE, Accessory.ZZZ,
            )
            Emotion.SURPRISED -> FaceStyle(
                EyeStyle(open = 1.3f, width = 0.9f, roundness = 0.5f),
                MouthStyle(curve = 0f, width = 0.7f, open = 0.8f, round = true), CYAN,
            )
            Emotion.LISTENING -> FaceStyle(
                EyeStyle(open = 1.12f, width = 1.02f, roundness = 0.38f),
                MouthStyle(curve = 0.35f, width = 0.75f), CYAN,
            )
            Emotion.EXCITED -> FaceStyle(
                EyeStyle(open = 1.2f, width = 1.08f, bottomCover = 0.25f, roundness = 0.42f),
                MouthStyle(curve = 1f, width = 1.25f, open = 0.55f), WARM, Accessory.SPARKLE, bounce = 0.06f,
            )
            Emotion.LOVE -> FaceStyle(
                EyeStyle(open = 1.05f, roundness = 0.5f),
                MouthStyle(curve = 0.8f, width = 0.9f, open = 0.1f), PINK, Accessory.HEARTS,
            )
            Emotion.CONFUSED -> FaceStyle(
                EyeStyle(open = 0.95f, lidAngle = 12f, lidCover = 0.1f, leftScale = 1.15f, rightScale = 0.75f),
                MouthStyle(curve = -0.35f, width = 0.7f), VIOLET, Accessory.QUESTION,
            )
            Emotion.ANGRY -> FaceStyle(
                EyeStyle(open = 0.85f, lidAngle = -24f, lidCover = 0.22f, roundness = 0.25f),
                MouthStyle(curve = -0.6f, width = 0.85f), RED,
            )
        }
    }
}
