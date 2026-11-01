package pl.arekp.deskmate.ai

import org.json.JSONArray
import org.json.JSONObject
import pl.arekp.deskmate.ai.llm.ImageData
import pl.arekp.deskmate.ai.llm.ToolSpec

/**
 * Narzędzie, które model może wywołać (tool use).
 * Nowe integracje (Gmail, Kalendarz, Slack, Home Assistant…) dodajesz jako kolejne implementacje
 * i rejestrujesz w DeskMateBrain.buildTools(). Patrz docs/roadmapa.md.
 */
interface AssistantTool {
    val name: String
    val description: String
    /** JSON Schema parametrów. */
    val inputSchema: JSONObject
    suspend fun run(input: JSONObject): ToolResult
}

/** Wynik narzędzia: tekst i opcjonalnie obrazy (np. zrzut ekranu). */
class ToolResult(
    val text: String,
    val images: List<ImageData> = emptyList(),
    val isError: Boolean = false,
) {
    companion object {
        fun text(text: String, isError: Boolean = false) = ToolResult(text, isError = isError)
    }
}

fun AssistantTool.toSpec() = ToolSpec(name, description, inputSchema)

/** Pomocnik do budowania schematów. */
fun objectSchema(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject =
    JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required))

fun enumProp(description: String, values: List<String>): JSONObject =
    JSONObject().put("type", "string").put("description", description).put("enum", JSONArray(values))
