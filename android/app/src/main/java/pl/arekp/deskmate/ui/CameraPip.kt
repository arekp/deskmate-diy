package pl.arekp.deskmate.ui

import androidx.camera.core.Preview
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.SceneObservation

private val PipShape = RoundedCornerShape(12.dp)
private val Accent = Color(0xFF7FE7FF)

/**
 * Okienko „co widzę”: podgląd przedniej kamery (jak lustro), ramka wykrytej twarzy
 * i to, co rozpoznaje ML Kit – etykiety otoczenia, uśmiech, podniesiona ręka.
 */
@Composable
fun CameraPip(
    face: FaceObservation,
    scene: SceneObservation,
    attach: (Preview.SurfaceProvider?) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(PIP_WIDTH)) {
        Box(
            Modifier
                .size(PIP_WIDTH, PIP_WIDTH * 3 / 4) // kadr 4:3, jak analiza obrazu
                .clip(PipShape)
                .background(Color(0xFF0B1A22))
                .border(1.dp, if (face.present) Accent else Color(0x55FFFFFF), PipShape),
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        // TextureView – żeby zaokrąglone rogi przycinały obraz
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        // podłączamy raz przy tworzeniu (ponowne podłączanie restartowałoby strumień kamery)
                        attach(surfaceProvider)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(Unit) { onDispose { attach(null) } }

            // Ramka twarzy – podgląd przedniej kamery jest lustrzany, więc odbijamy x
            val box = face.box
            if (face.present && box != null) {
                Canvas(Modifier.fillMaxSize()) {
                    val l = (1f - box.right) * size.width
                    val t = box.top * size.height
                    drawRoundRect(
                        color = if (face.smiling) Color(0xFF9CF7D8) else Accent,
                        topLeft = Offset(l, t),
                        size = Size((box.right - box.left) * size.width, (box.bottom - box.top) * size.height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
            }
        }
        val faceInfo = when {
            !face.present -> "brak twarzy"
            face.smiling -> "twarz · uśmiech"
            face.attentive -> "twarz · patrzy na mnie"
            else -> "twarz · patrzy w bok"
        }
        val extras = buildList {
            add(faceInfo)
            if (scene.handRaised) add("ręka w górze")
        }.joinToString(" · ")
        Text(
            extras,
            color = if (face.present) Accent else Color.Gray,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (scene.labels.isNotEmpty()) {
            Text(
                scene.labels.take(4).joinToString(", ") { "${it.name} ${(it.confidence * 100).toInt()}%" },
                color = Color(0xFFB0BEC5),
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private val PIP_WIDTH = 168.dp
