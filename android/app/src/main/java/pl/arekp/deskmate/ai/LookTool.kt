package pl.arekp.deskmate.ai

import org.json.JSONObject
import pl.arekp.deskmate.vision.FaceObservation
import pl.arekp.deskmate.vision.SceneObservation

/**
 * "Co widzisz?" – opis kadru z lokalnego rozpoznawania (ML Kit na telefonie).
 * Do modelu AI trafia tylko tekst (etykiety, twarz, poza, odczytany tekst), nie obraz.
 */
class LookTool(
    private val face: () -> FaceObservation,
    private val scene: () -> SceneObservation,
    private val readText: suspend () -> String,
) : AssistantTool {
    override val name = "look_around"
    override val description =
        "Sprawdza, co widzi przednia kamera robota (rozpoznawanie lokalne na telefonie): czy jest twarz " +
            "i czy się uśmiecha, ogólne etykiety otoczenia (np. kubek, laptop, roślina – mogą być nieprecyzyjne), " +
            "czy ktoś podnosi rękę. Z read_text=true odczytuje też tekst pokazany do kamery (np. kartkę, okładkę). " +
            "Używaj, gdy użytkownik pyta, co widzisz, co trzyma, albo prosi o przeczytanie tekstu."
    override val inputSchema = objectSchema(
        "read_text" to JSONObject().put("type", "boolean").put("description", "Czy odczytać tekst w kadrze (OCR)"),
    )

    override suspend fun run(input: JSONObject): ToolResult =
        ToolResult.text(describe(face(), scene(), if (input.optBoolean("read_text")) readText() else null))

    companion object {
        fun describe(face: FaceObservation, scene: SceneObservation, text: String?): String = buildList {
            add(
                if (!face.present) "Twarz: nie widzę."
                else buildString {
                    append("Twarz: tak")
                    if (face.smiling) append(", uśmiechnięta")
                    append(if (face.attentive) ", patrzy w kamerę" else ", patrzy w bok")
                    append(
                        when {
                            face.size > 0.45f -> ", bardzo blisko"
                            face.size > 0.2f -> ", blisko"
                            else -> ", dalej od kamery"
                        },
                    )
                    append(".")
                },
            )
            add(
                if (scene.labels.isEmpty()) "Otoczenie: brak pewnych etykiet."
                else "Otoczenie (etykiety ogólne): " +
                    scene.labels.joinToString(", ") { "${it.name} ${(it.confidence * 100).toInt()}%" } + ".",
            )
            if (scene.handRaised) add("Ktoś trzyma podniesioną rękę.")
            if (text != null) add(if (text.isBlank()) "Tekst w kadrze: brak." else "Tekst w kadrze: „${text.take(600)}”.")
        }.joinToString(" ")
    }
}
