package pl.arekp.deskmate.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.random.Random

/** Słowo wypowiadane właśnie przez TTS – kolejny numer i liczba sylab (do ruchu ust). */
data class SpeechBeat(val seq: Long = 0, val syllables: Int = 0)

data class UiState(
    val emotion: Emotion = Emotion.NEUTRAL,
    /** Kierunek spojrzenia -1..1. */
    val gazeX: Float = 0f,
    val gazeY: Float = 0f,
    val status: String = "",
    val listening: Boolean = false,
    /** Robot mówi (TTS) – usta się ruszają. */
    val speaking: Boolean = false,
    val speechBeat: SpeechBeat = SpeechBeat(),
    val caption: String = "",
    val eyeScale: Float = 1f,
    val ticker: TickerState = TickerState(),
)

private val Background = Color.Black

/** Pełnoekranowa "twarz" robota: animowane oczy i usta. */
@Composable
fun EyesScreen(state: UiState, modifier: Modifier = Modifier) {
    val style = FaceStyle.of(state.emotion)

    // Mruganie (rzadsze w zdziwieniu, wcale w śnie)
    val blink = remember { Animatable(1f) }
    LaunchedEffect(state.emotion) {
        if (state.emotion == Emotion.SLEEPY) { blink.snapTo(1f); return@LaunchedEffect }
        while (true) {
            delay(Random.nextLong(2500, if (state.emotion == Emotion.SURPRISED) 9000 else 6000))
            blink.animateTo(0.05f, tween(80))
            blink.animateTo(1f, tween(130))
            if (Random.nextInt(5) == 0) { // czasem mrugnięcie podwójne
                delay(120)
                blink.animateTo(0.05f, tween(70))
                blink.animateTo(1f, tween(120))
            }
        }
    }

    // Drobne, losowe ruchy oczu (sakkady), gdy nikogo nie śledzimy – twarz wygląda na „żywą”
    val saccade = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(Random.nextLong(900, 2600))
            saccade.animateTo(Offset(Random.nextFloat() * 0.3f - 0.15f, Random.nextFloat() * 0.2f - 0.1f), tween(90))
        }
    }

    // Usta: każda sylaba otwiera i zamyka usta; gdy TTS nie podaje słów – rytm zastępczy
    val talk = remember { Animatable(0f) }
    var lastBeatAt by remember { mutableLongStateOf(0L) }
    LaunchedEffect(state.speechBeat.seq) {
        if (state.speechBeat.seq == 0L) return@LaunchedEffect
        lastBeatAt = System.currentTimeMillis()
        repeat(state.speechBeat.syllables.coerceIn(1, 6)) {
            talk.animateTo(0.45f + Random.nextFloat() * 0.55f, tween(70))
            talk.animateTo(0.08f + Random.nextFloat() * 0.12f, tween(85))
        }
    }
    LaunchedEffect(state.speaking) {
        if (!state.speaking) { talk.animateTo(0f, tween(120)); return@LaunchedEffect }
        while (true) {
            if (System.currentTimeMillis() - lastBeatAt > 450) {
                talk.animateTo(0.35f + Random.nextFloat() * 0.65f, tween(75))
                talk.animateTo(0.05f + Random.nextFloat() * 0.15f, tween(90))
                if (Random.nextInt(6) == 0) delay(140) // przerwa między słowami
            } else {
                delay(60)
            }
        }
    }

    // Efekty ciągłe: podskakiwanie, pulsowanie serc, unoszenie „Zzz”, kropki myślenia
    val loop = rememberInfiniteTransition(label = "loop")
    val phase by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "phase")
    val bounce by loop.animateFloat(-1f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "bounce")

    val forcedGaze = when (state.emotion) {
        Emotion.THINKING -> Offset(-0.6f, 0.6f) // zamyślony – w górę i w bok
        Emotion.SLEEPY -> Offset(0f, -0.4f)
        Emotion.SAD -> Offset(0f, -0.35f)
        else -> null
    }
    val gx by animateFloatAsState((forcedGaze?.x ?: state.gazeX) + saccade.value.x, spring(stiffness = 120f), label = "gx")
    val gy by animateFloatAsState((forcedGaze?.y ?: state.gazeY) + saccade.value.y, spring(stiffness = 120f), label = "gy")

    val t = tween<Float>(280)
    val open by animateFloatAsState(style.eye.open, t, label = "open")
    val width by animateFloatAsState(style.eye.width, t, label = "width")
    val lidAngle by animateFloatAsState(style.eye.lidAngle, t, label = "lidAngle")
    val lidCover by animateFloatAsState(style.eye.lidCover, t, label = "lidCover")
    val bottomCover by animateFloatAsState(style.eye.bottomCover, t, label = "bottom")
    val roundness by animateFloatAsState(style.eye.roundness, t, label = "round")
    val leftScale by animateFloatAsState(style.eye.leftScale, t, label = "ls")
    val rightScale by animateFloatAsState(style.eye.rightScale, t, label = "rs")
    val mouthCurve by animateFloatAsState(style.mouth.curve, t, label = "mc")
    val mouthWidth by animateFloatAsState(style.mouth.width, t, label = "mw")
    val mouthOpen by animateFloatAsState(style.mouth.open, t, label = "mo")
    val mouthRound by animateFloatAsState(if (style.mouth.round) 1f else 0f, t, label = "mr")
    val bounceAmp by animateFloatAsState(style.bounce + if (state.speaking) 0.012f else 0f, t, label = "ba")
    val color by animateColorAsState(Color(style.color), tween(400), label = "color")

    val density = LocalDensity.current
    val reservePx = with(density) { (TickerHeight + CaptionReserve).toPx() }

    Box(modifier.fillMaxSize().background(Background)) {
        Canvas(Modifier.fillMaxSize()) {
            val face = FaceGeometry.of(size.width, size.height - reservePx, state.eyeScale)
            val eye = EyeStyle(open * blink.value, width, lidAngle, lidCover, bottomCover, roundness, leftScale, rightScale)
            val dy = bounce * bounceAmp * face.base
            drawEyes(face, eye, color, gx, gy, dy, style.accessory, phase)
            drawMouth(
                face, color,
                MouthStyle(mouthCurve, mouthWidth, mouthOpen, mouthRound > 0.5f),
                talk = talk.value, roundness = mouthRound, dy = dy * 0.5f, gx = gx,
            )
            drawAccessory(face, style.accessory, color, phase, gx, gy)
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
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = TickerHeight + 6.dp),
            )
        }

        NewsTicker(state.ticker, Modifier.align(Alignment.BottomCenter))
    }
}

/** Miejsce na podpis (2 linijki) nad paskiem – twarz rysujemy powyżej. */
private val CaptionReserve = 52.dp

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

/** Rozmieszczenie twarzy w dostępnym obszarze ekranu (nad podpisem i paskiem). */
private data class FaceGeometry(val cx: Float, val eyeY: Float, val mouthY: Float, val base: Float, val gap: Float) {
    companion object {
        fun of(w: Float, h: Float, scale: Float): FaceGeometry {
            val base = minOf(w * 0.5f, h * 0.62f) * 0.62f * scale
            return FaceGeometry(
                cx = w / 2f,
                eyeY = h * 0.42f,
                mouthY = h * 0.42f + base * 0.92f,
                base = base,
                gap = base * 0.55f,
            )
        }
    }
}

private fun DrawScope.drawEyes(
    f: FaceGeometry, s: EyeStyle, color: Color, gx: Float, gy: Float, dy: Float, accessory: Accessory, phase: Float,
) {
    val eyeW = f.base * 0.62f
    val cx = f.cx + gx * size.width * 0.06f
    val cy = f.eyeY - gy * f.base * 0.25f + dy

    for (side in listOf(-1, 1)) {
        val k = if (side < 0) s.leftScale else s.rightScale
        val w = eyeW * s.width * k
        val h = (f.base * s.open * k).coerceAtLeast(f.base * 0.035f)
        val center = Offset(cx + side * (f.gap / 2f + eyeW / 2f), cy)

        if (accessory == Accessory.HEARTS) {
            val pulse = 1f + 0.08f * kotlin.math.sin(phase * 2f * Math.PI.toFloat() * 2f)
            drawHeart(center, w * 1.05f * pulse, color, squash = (s.open / 1.05f).coerceIn(0.08f, 1f))
            continue
        }

        val topLeft = Offset(center.x - w / 2f, center.y - h / 2f)
        drawRoundRect(color, topLeft, Size(w, h), CornerRadius(minOf(w, h) * s.roundness * 1.6f))

        // blask – mały jaśniejszy owal, przesuwa się lekko ze spojrzeniem
        if (h > f.base * 0.3f) {
            drawOval(
                Color.White.copy(alpha = 0.35f),
                topLeft = Offset(center.x - w * 0.3f + gx * w * 0.08f, center.y - h * 0.38f - gy * h * 0.05f),
                size = Size(w * 0.22f, h * 0.16f),
            )
        }

        // górna powieka – skośna krawędź w kolorze tła
        if (s.lidCover > 0.01f || kotlin.math.abs(s.lidAngle) > 0.5f) {
            val outerX = center.x + side * (w / 2f + 4f)
            val innerX = center.x - side * (w / 2f + 4f)
            val base = topLeft.y + s.lidCover * h
            val slope = kotlin.math.tan(Math.toRadians(s.lidAngle.toDouble())).toFloat() * w / 2f
            val path = Path().apply {
                moveTo(innerX, topLeft.y - h)
                lineTo(outerX, topLeft.y - h)
                lineTo(outerX, base + slope)
                lineTo(innerX, base - slope)
                close()
            }
            drawPath(path, Background)
        }

        // uśmiech oczu – łuk zasłaniający dół
        if (s.bottomCover > 0.01f) {
            val ovalTop = center.y + h / 2f - s.bottomCover * h
            drawOval(Background, Offset(center.x - w * 0.8f, ovalTop), Size(w * 1.6f, h * 1.3f))
        }
    }
}

/** Serce; [squash] < 1 spłaszcza je w pionie (mrugnięcie). */
private fun DrawScope.drawHeart(center: Offset, size: Float, color: Color, squash: Float = 1f) {
    val s = size / 2f
    val v = s * squash
    val path = Path().apply {
        moveTo(center.x, center.y + v * 0.9f)
        cubicTo(center.x - s * 1.6f, center.y - v * 0.1f, center.x - s * 0.7f, center.y - v * 1.2f, center.x, center.y - v * 0.45f)
        cubicTo(center.x + s * 0.7f, center.y - v * 1.2f, center.x + s * 1.6f, center.y - v * 0.1f, center.x, center.y + v * 0.9f)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawMouth(
    f: FaceGeometry, color: Color, m: MouthStyle, talk: Float, roundness: Float, dy: Float, gx: Float,
) {
    val cx = f.cx + gx * size.width * 0.03f
    val y = f.mouthY + dy
    val opening = (m.open + talk * (1f - m.open * 0.5f)).coerceIn(0f, 1.2f)
    val w = f.base * 0.62f * m.width * (1f - 0.35f * roundness * opening.coerceAtMost(1f)) * (1f - 0.12f * talk)
    val bend = m.curve * w * 0.32f
    val stroke = f.base * 0.055f

    if (opening < 0.06f) {
        val path = Path().apply {
            moveTo(cx - w / 2f, y - bend * 0.4f)
            quadraticBezierTo(cx, y + bend, cx + w / 2f, y - bend * 0.4f)
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        return
    }

    if (roundness > 0.5f) {
        val d = w * (0.45f + 0.35f * opening)
        drawOval(color, Offset(cx - d / 2f, y - d * 0.5f * opening), Size(d, d * (0.55f + 0.45f * opening)))
        return
    }

    // otwarte usta: górna i dolna krawędź jako łuki, w środku ciemne wnętrze z „językiem”
    val h = f.base * 0.38f * opening
    val path = Path().apply {
        moveTo(cx - w / 2f, y - bend * 0.4f)
        quadraticBezierTo(cx, y + bend - h * 0.25f, cx + w / 2f, y - bend * 0.4f)
        quadraticBezierTo(cx, y + bend + h, cx - w / 2f, y - bend * 0.4f)
        close()
    }
    drawPath(path, color)
    if (h > f.base * 0.08f) {
        val inner = Path().apply {
            val iw = w * 0.78f
            moveTo(cx - iw / 2f, y - bend * 0.3f + stroke * 0.4f)
            quadraticBezierTo(cx, y + bend - h * 0.1f, cx + iw / 2f, y - bend * 0.3f + stroke * 0.4f)
            quadraticBezierTo(cx, y + bend + h - stroke * 1.2f, cx - iw / 2f, y - bend * 0.3f + stroke * 0.4f)
            close()
        }
        drawPath(inner, Color(0xFF0B1A22))
        drawOval(
            Color(0xFFFF7A90).copy(alpha = 0.85f),
            Offset(cx - w * 0.18f, y + bend + h * 0.45f - stroke),
            Size(w * 0.36f, h * 0.32f),
        )
    }
}

private fun DrawScope.drawAccessory(f: FaceGeometry, a: Accessory, color: Color, phase: Float, gx: Float, gy: Float) {
    val eyeW = f.base * 0.62f
    val cx = f.cx + gx * size.width * 0.06f
    val cy = f.eyeY - gy * f.base * 0.25f
    val rightEyeX = cx + f.gap / 2f + eyeW / 2f
    val leftEyeX = cx - f.gap / 2f - eyeW / 2f
    when (a) {
        Accessory.NONE -> Unit
        Accessory.BLUSH, Accessory.HEARTS -> for (x in listOf(leftEyeX, rightEyeX)) {
            drawOval(
                Color(0xFFFF6FA5).copy(alpha = 0.33f),
                Offset(x - eyeW * 0.45f, cy + f.base * 0.55f),
                Size(eyeW * 0.9f, f.base * 0.2f),
            )
        }
        Accessory.ZZZ -> for (i in 0..2) {
            val p = (phase + i / 3f) % 1f
            val s = f.base * (0.1f + 0.08f * i) * (0.6f + 0.4f * p)
            val x = rightEyeX + eyeW * 0.6f + p * f.base * 0.3f
            val y = cy - f.base * 0.5f - p * f.base * 0.9f
            val path = Path().apply {
                moveTo(x, y); lineTo(x + s, y); lineTo(x, y + s); lineTo(x + s, y + s)
            }
            drawPath(path, color.copy(alpha = (1f - p).coerceIn(0f, 1f)), style = Stroke(f.base * 0.03f, cap = StrokeCap.Round))
        }
        Accessory.DOTS -> for (i in 0..2) {
            val lit = ((phase * 3f).toInt() % 3) == i
            drawCircle(
                color.copy(alpha = if (lit) 1f else 0.3f),
                radius = f.base * (if (lit) 0.06f else 0.045f),
                center = Offset(rightEyeX + eyeW * 0.2f + i * f.base * 0.2f, cy - f.base * 0.85f),
            )
        }
        Accessory.QUESTION -> {
            val x = rightEyeX + eyeW * 0.7f
            val y = cy - f.base * 0.9f + kotlin.math.sin(phase * 2f * Math.PI.toFloat()) * f.base * 0.03f
            val r = f.base * 0.11f
            val path = Path().apply {
                moveTo(x - r, y)
                cubicTo(x - r, y - r * 1.4f, x + r, y - r * 1.4f, x + r, y)
                cubicTo(x + r, y + r * 0.6f, x, y + r * 0.5f, x, y + r * 1.3f)
            }
            drawPath(path, color, style = Stroke(f.base * 0.035f, cap = StrokeCap.Round))
            drawCircle(color, f.base * 0.025f, Offset(x, y + r * 2f))
        }
        Accessory.SPARKLE -> for (i in 0..1) {
            val p = (phase + i * 0.5f) % 1f
            val x = if (i == 0) leftEyeX - eyeW * 0.75f else rightEyeX + eyeW * 0.75f
            val y = cy - f.base * 0.55f
            val r = f.base * 0.09f * kotlin.math.sin(p * Math.PI.toFloat())
            val path = Path().apply {
                moveTo(x, y - r); lineTo(x + r * 0.25f, y - r * 0.25f); lineTo(x + r, y)
                lineTo(x + r * 0.25f, y + r * 0.25f); lineTo(x, y + r); lineTo(x - r * 0.25f, y + r * 0.25f)
                lineTo(x - r, y); lineTo(x - r * 0.25f, y - r * 0.25f); close()
            }
            drawPath(path, Color.White.copy(alpha = 0.9f))
        }
    }
}
