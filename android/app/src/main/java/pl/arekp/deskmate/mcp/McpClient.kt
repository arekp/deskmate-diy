package pl.arekp.deskmate.mcp

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import pl.arekp.deskmate.BuildConfig
import pl.arekp.deskmate.DiagLog
import pl.arekp.deskmate.ai.llm.HttpJson
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

/**
 * Lekki klient MCP (Model Context Protocol) przez **Streamable HTTP** – bez dodatkowych bibliotek (OkHttp + org.json).
 *
 * Obsługuje to, czego potrzebuje agent: `initialize` → `notifications/initialized` → `tools/list` → `tools/call`.
 * Serwer może odpowiedzieć zwykłym JSON albo strumieniem SSE (`text/event-stream`) – oba warianty są obsłużone.
 * Sesja: nagłówek `Mcp-Session-Id` z odpowiedzi na `initialize`; gdy serwer ją unieważni (404), łączymy się od nowa.
 *
 * Sprawdzone pod kątem Home Assistant (`http://<ha>:8123/api/mcp`, token „long-lived” jako Bearer).
 */
class McpClient(
    private val url: String,
    private val token: String,
    private val timeoutMs: Long = 20_000,
) {
    class McpException(message: String, val httpCode: Int = 0) : IOException(message)

    data class RemoteTool(val name: String, val description: String, val inputSchema: JSONObject)
    data class CallResult(val text: String, val isError: Boolean)

    private val ids = AtomicLong(1)
    @Volatile private var sessionId: String? = null
    @Volatile private var protocolVersion: String = PROTOCOL_VERSION
    @Volatile var serverName: String = ""
        private set

    val isConnected: Boolean get() = serverName.isNotEmpty()

    suspend fun connect() {
        sessionId = null
        val init = request(
            "initialize",
            JSONObject()
                .put("protocolVersion", PROTOCOL_VERSION)
                .put("capabilities", JSONObject())
                .put("clientInfo", JSONObject().put("name", "DeskMateDIY").put("version", BuildConfig.VERSION_NAME)),
            retryOnExpiredSession = false,
        )
        protocolVersion = init.optString("protocolVersion", PROTOCOL_VERSION)
        serverName = init.optJSONObject("serverInfo")?.optString("name").orEmpty().ifBlank { "MCP" }
        notify("notifications/initialized")
    }

    suspend fun listTools(): List<RemoteTool> {
        ensureConnected()
        val out = mutableListOf<RemoteTool>()
        var cursor: String? = null
        do {
            val params = JSONObject().apply { cursor?.let { put("cursor", it) } }
            val r = request("tools/list", params)
            val arr = r.optJSONArray("tools") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.optJSONObject(i) ?: continue
                out += RemoteTool(
                    t.optString("name"),
                    t.optString("description"),
                    t.optJSONObject("inputSchema") ?: JSONObject().put("type", "object"),
                )
            }
            cursor = r.optString("nextCursor").ifBlank { null }
        } while (cursor != null && out.size < 500)
        return out
    }

    suspend fun callTool(name: String, args: JSONObject): CallResult {
        ensureConnected()
        val r = request("tools/call", JSONObject().put("name", name).put("arguments", args))
        return parseCallResult(r)
    }

    private suspend fun ensureConnected() {
        if (!isConnected) connect()
    }

    private suspend fun notify(method: String) {
        val body = JSONObject().put("jsonrpc", "2.0").put("method", method)
        runCatching { post(body) } // 202 Accepted bez treści – błędy powiadomień nie są krytyczne
    }

    private suspend fun request(method: String, params: JSONObject, retryOnExpiredSession: Boolean = true): JSONObject {
        val id = ids.getAndIncrement()
        val body = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        val (code, contentType, text) = try {
            post(body)
        } catch (e: McpException) {
            if (e.httpCode == 404 && sessionId != null && retryOnExpiredSession) {
                DiagLog.i("MCP", "Sesja wygasła ($url) – łączę ponownie")
                serverName = ""
                connect()
                return request(method, params, retryOnExpiredSession = false)
            }
            throw e
        }
        val msg = if (contentType.contains("text/event-stream")) parseSse(text, id) else JSONObject(text)
        msg.optJSONObject("error")?.let { err ->
            throw McpException("MCP $method: ${err.optString("message")} (kod ${err.optInt("code")})", code)
        }
        return msg.optJSONObject("result") ?: JSONObject()
    }

    /** Wysyła JSON-RPC; zwraca (kod HTTP, Content-Type, treść). */
    private suspend fun post(body: JSONObject): Triple<Int, String, String> = withContext(Dispatchers.IO) {
        val req = Request.Builder().url(url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .apply {
                if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}")
                sessionId?.let { header("Mcp-Session-Id", it) }
                if (isConnected) header("MCP-Protocol-Version", protocolVersion)
            }
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val call = HttpJson.client.newCall(req)
        call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
        call.execute().use { resp ->
            resp.header("Mcp-Session-Id")?.let { sessionId = it }
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) {
                val hint = when (resp.code) {
                    401, 403 -> "zły token albo brak uprawnień"
                    404 -> "nie ma serwera MCP pod tym adresem (w Home Assistant: dodaj integrację „Model Context Protocol Server”)"
                    else -> text.take(150)
                }
                throw McpException("HTTP ${resp.code}: $hint", resp.code)
            }
            Triple(resp.code, resp.header("Content-Type").orEmpty(), text)
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"

        /** Z odpowiedzi SSE wybiera wiadomość JSON-RPC o danym id (zdarzenia mogą mieć wiele linii `data:`). */
        fun parseSse(body: String, id: Long): JSONObject {
            val events = mutableListOf<String>()
            val current = StringBuilder()
            for (line in body.lineSequence()) {
                when {
                    line.startsWith("data:") -> current.append(line.removePrefix("data:").trimStart()).append('\n')
                    line.isBlank() && current.isNotEmpty() -> { events += current.toString().trim(); current.clear() }
                }
            }
            if (current.isNotEmpty()) events += current.toString().trim()
            val messages = events.mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            return messages.firstOrNull { it.optLong("id", -1) == id }
                ?: messages.lastOrNull { it.has("result") || it.has("error") }
                ?: throw McpException("Brak odpowiedzi JSON-RPC w strumieniu SSE")
        }

        /** Wynik `tools/call` → tekst dla modelu (treści tekstowe, zasoby; obrazy jako wzmianka). */
        fun parseCallResult(result: JSONObject): CallResult {
            val content = result.optJSONArray("content") ?: JSONArray()
            val parts = (0 until content.length()).mapNotNull { i ->
                val c = content.optJSONObject(i) ?: return@mapNotNull null
                when (c.optString("type")) {
                    "text" -> c.optString("text")
                    "resource" -> c.optJSONObject("resource")?.optString("text")
                    "image" -> "[obraz ${c.optString("mimeType")}]"
                    else -> null
                }
            }.filter { it.isNotBlank() }
            val structured = result.optJSONObject("structuredContent")?.toString()
            val text = parts.joinToString("\n").ifBlank { structured ?: "OK" }
            return CallResult(text.take(MAX_RESULT_CHARS), result.optBoolean("isError", false))
        }

        /** Długie wyniki (np. stan wszystkich urządzeń) przycinamy, żeby nie zjadały limitów tokenów. */
        const val MAX_RESULT_CHARS = 6_000
    }
}
