package pl.arekp.deskmate.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

data class UiState(
    val emotion: Emotion = Emotion.NEUTRAL,
    /** Kierunek spojrzenia -1..1. */
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val status: String = "",
    val listening: Boolean = false,
    val caption: String = "",
    val eyeScale: Float = 1f,
    val ticker: TickerState = TickerState(),
)

private val EyeColor = Color(0xFF7FE7FF)
private val Background = Color.Black

/** Pełnoekranowa "twarz" robota: dwoje animowanych oczu. */
@Composable
fun EyesScreen(state: UiState, modifier: Modifier = Modifier) {
    val blink = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(2500, 6000))
            blink.animateTo(0.05f, tween(80))
            blink.animateTo(1f, tween(130))
            if (Random.nextInt(5) == 0) { // czasem mrugnięcie podwójne
                delay(120)
                blink.animateTo(0.05f, tween(70))
                blink.animateTo(1f, tween(120))
            }
        }
    }

    val forcedGaze = when (state.emotion) {
        Emotion.THINKING -> Offset(-0.6f, 0.6f) // zamyślony – w górę i w bok
        Emotion.SLEEPY -> Offset(0f, -0.4f)
        else -> null
    }
    val gx by animateFloatAsState(forcedGaze?.x ?: state.gazeX, spring(stiffness = 120f), label = "gx")
    val gy by animateFloatAsState(forcedGaze?.y ?: state.gazeY, spring(stiffness = 120f), label = "gy")
    val openness by animateFloatAsState(
        when (state.emotion) {
            Emotion.SLEEPY -> 0.22f
            Emotion.SURPRISED -> 1.25f
            Emotion.LISTENING -> 1.1f
            Emotion.THINKING -> 0.8f
            else -> 1f
        },
        tween(300),
        label = "open",
    )

    Box(modifier.fillMaxSize().background(Background)) {
        Canvas(Modifier.fillMaxSize()) {
            drawEyes(state.emotion, gx, gy, openness * blink.value, state.eyeScale)
        }

        if (state.listening) ListeningDot(Modifier.align(Alignment.TopEnd).padding(20.dp))

        if (state.status.isNotBlank()) {
            Text(
                state.status,
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(12.dp),
            )
        }
        if (state.caption.isNotBlank()) {
            Text(
                state.caption,
                color = Color(0xFFB0BEC5),
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = TickerHeight + 8.dp),
            )
        }

        NewsTicker(state.ticker, Modifier.align(Alignment.BottomCenter))
    }
}

private val TickerHeight = 34.dp
private val TickerRed = Color(0xFFD32F2F)
private val TickerBar = Color(0xFF101820)

/** Pasek w stylu wiadomości TV: czynność po lewej, przesuwające się informacje po prawej. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NewsTicker(ticker: TickerState, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().height(TickerHeight).background(TickerBar),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.fillMaxHeight().background(TickerRed).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(ticker.activity.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            ticker.text(),
            color = Color(0xFFE0E0E0),
            fontSize = 14.sp,
            maxLines = 1,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
                .basicMarquee(iterations = Int.MAX_VALUE, velocity = 60.dp),
        )
    }
}

@Composable
private fun ListeningDot(modifier: Modifier) {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val a by pulse.animateFloat(0.3f, 1f, infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "a")
    Box(modifier.size(14.dp).alpha(a).background(Color(0xFFFF5252), CircleShape))
}

private fun DrawScope.drawEyes(emotion: Emotion, gx: Float, gy: Float, openness: Float, scale: Float) {
    val base = size.minDimension * 0.42f * scale
    val eyeW = base * 0.62f
    val eyeH = (base * openness).coerceAtLeast(base * 0.04f)
    val gap = base * 0.55f
    val cx = size.width / 2f + gx * size.width * 0.07f
    val cy = size.height / 2f - gy * size.height * 0.07f

    for (side in listOf(-1, 1)) {
        val curiousBoost = if (emotion == Emotion.CURIOUS && side == 1) 1.18f else 1f
        val w = eyeW * curiousBoost
        val h = eyeH * curiousBoost
        val center = Offset(cx + side * (gap / 2f + eyeW / 2f), cy)
        val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
        drawRoundRect(EyeColor, topLeft, Size(w, h), CornerRadius(w * 0.32f))

        when (emotion) {
            // "uśmiechnięte" oczy ^ ^ – zasłaniamy dół owalem tła
            Emotion.HAPPY -> drawOval(
                Background,
                topLeft = Offset(center.x - w * 0.75f, center.y + h * 0.05f),
                size = Size(w * 1.5f, h * 1.1f),
            )
            // smutne – opadające zewnętrzne kąciki
            Emotion.SAD -> {
                val outerX = center.x + side * w / 2f
                val innerX = center.x - side * w / 2f
                val path = Path().apply {
                    moveTo(innerX - side * 2f, topLeft.y - 2f)
                    lineTo(outerX + side * 2f, topLeft.y - 2f)
                    lineTo(outerX + side * 2f, topLeft.y + h * 0.45f)
                    close()
                }
                drawPath(path, Background)
            }
            else -> Unit
        }
    }
}
