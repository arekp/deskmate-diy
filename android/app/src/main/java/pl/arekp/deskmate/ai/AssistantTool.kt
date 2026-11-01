package pl.arekp.deskmate.ai

import org.json.JSONArray
import org.json.JSONObject

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

/** Wynik narzędzia: tekst albo tablica bloków treści (np. obraz + tekst). */
class ToolResult private constructor(val content: Any, val isError: Boolean) {
    companion object {
        fun text(text: String, isError: Boolean = false) = ToolResult(text, isError)
        fun blocks(blocks: JSONArray) = ToolResult(blocks, false)
    }
}

/** Pomocnik do budowania schematów. */
fun objectSchema(vararg props: Pair<String, JSONObject>, required: List<String> = emptyList()): JSONObject =
    JSONObject()
        .put("type", "object")
        .put("properties", JSONObject().apply { props.forEach { (k, v) -> put(k, v) } })
        .put("required", JSONArray(required))

fun enumProp(description: String, values: List<String>): JSONObject =
    JSONObject().put("type", "string").put("description", description).put("enum", JSONArray(values))
