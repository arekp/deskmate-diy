package pl.arekp.deskmate.mcp

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.ConcurrentHashMap
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
 *
 * Starszy transport **SSE** (np. Home Assistant `/mcp_server/sse`): gdy POST pod adresem daje 405/404 albo adres
 * kończy się na `/sse`, klient sam przełącza się na SSE – GET otwiera strumień, serwer podaje adres do wysyłania
 * (zdarzenie `endpoint`), a odpowiedzi JSON-RPC wracają strumieniem.
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

    @Volatile private var legacy: LegacySse? = null

    /** Używany transport (do testu i logów). */
    val transport: String get() = if (legacy != null) "SSE (starszy)" else "Streamable HTTP"

    suspend fun connect() {
        sessionId = null
        serverName = ""
        legacy?.close()
        legacy = null
        try {
            initialize()
        } catch (e: McpException) {
            if (e.httpCode != 405 && e.httpCode != 404 && !url.trimEnd('/').endsWith("/sse")) throw e
            val sseUrl = legacySseUrl(url)
            DiagLog.i("MCP", "$url: ${e.message} – próbuję starszego transportu SSE: $sseUrl")
            legacy = LegacySse(sseUrl, token, timeoutMs).also { it.open() }
            initialize()
        }
    }

    private suspend fun initialize() {
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
        if (!isConnected || legacy?.alive == false) connect()
    }

    private suspend fun notify(method: String) {
        val body = JSONObject().put("jsonrpc", "2.0").put("method", method)
        // 202 Accepted bez treści – błędy powiadomień nie są krytyczne
        runCatching { legacy?.post(body) ?: post(body) }
    }

    private suspend fun request(method: String, params: JSONObject, retryOnExpiredSession: Boolean = true): JSONObject {
        val id = ids.getAndIncrement()
        val body = JSONObject().put("jsonrpc", "2.0").put("id", id).put("method", method).put("params", params)
        legacy?.let { sse ->
            val msg = sse.request(body, id)
            msg.optJSONObject("error")?.let { err -> throw McpException("MCP $method: ${err.optString("message")} (kod ${err.optInt("code")})") }
            return msg.optJSONObject("result") ?: JSONObject()
        }
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
                val hint = if (resp.code in setOf(401, 403, 404, 405)) hintFor(resp.code) else text.take(150)
                throw McpException("HTTP ${resp.code}: $hint", resp.code)
            }
            Triple(resp.code, resp.header("Content-Type").orEmpty(), text)
        }
    }

    /** Starszy transport MCP: strumień SSE (GET) + wysyłanie (POST) pod adres podany w zdarzeniu `endpoint`. */
    private class LegacySse(private val sseUrl: String, private val token: String, private val timeoutMs: Long) {
        // strumień może długo milczeć – bez limitu czasu odczytu
        private val client = HttpJson.client.newBuilder().readTimeout(0, TimeUnit.MILLISECONDS).build()
        private val pending = ConcurrentHashMap<Long, CompletableDeferred<JSONObject>>()
        private val endpoint = CompletableDeferred<String>()
        @Volatile private var call: okhttp3.Call? = null
        @Volatile private var postUrl = ""
        @Volatile var alive = false
            private set

        private fun Request.Builder.auth() = apply { if (token.isNotBlank()) header("Authorization", "Bearer ${token.trim()}") }

        suspend fun open() {
            val c = client.newCall(Request.Builder().url(sseUrl).header("Accept", "text/event-stream").auth().get().build())
            call = c
            Thread({
                try {
                    c.execute().use { resp ->
                        if (!resp.isSuccessful) {
                            endpoint.completeExceptionally(McpException("SSE HTTP ${resp.code}: ${hintFor(resp.code)}", resp.code))
                            return@use
                        }
                        alive = true
                        val src = resp.body!!.source()
                        var event = "message"
                        val data = StringBuilder()
                        while (true) {
                            val line = src.readUtf8Line() ?: break
                            when {
                                line.startsWith("event:") -> event = line.removePrefix("event:").trim()
                                line.startsWith("data:") -> data.append(line.removePrefix("data:").trimStart()).append('\n')
                                line.isEmpty() -> {
                                    if (data.isNotEmpty()) dispatch(event, data.toString().trim())
                                    event = "message"
                                    data.clear()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    endpoint.completeExceptionally(McpException("SSE: ${e.message}"))
                } finally {
                    alive = false
                    pending.values.forEach { it.completeExceptionally(McpException("Połączenie SSE zamknięte")) }
                    pending.clear()
                }
            }, "mcp-sse").apply { isDaemon = true }.start()
            val path = withTimeoutOrNull(timeoutMs) { endpoint.await() }
                ?: throw McpException("SSE: serwer nie podał adresu (zdarzenie endpoint)")
            postUrl = java.net.URI(sseUrl).resolve(path).toString()
        }

        private fun dispatch(event: String, data: String) {
            if (event == "endpoint") {
                endpoint.complete(data)
                return
            }
            val msg = runCatching { JSONObject(data) }.getOrNull() ?: return
            val id = msg.optLong("id", -1)
            if (id >= 0) pending.remove(id)?.complete(msg)
        }

        suspend fun request(body: JSONObject, id: Long): JSONObject {
            val d = CompletableDeferred<JSONObject>()
            pending[id] = d
            try {
                post(body)
            } catch (e: Exception) {
                pending.remove(id)
                throw e
            }
            return withTimeoutOrNull(timeoutMs) { d.await() }
                ?: run { pending.remove(id); throw McpException("SSE: brak odpowiedzi w ${timeoutMs / 1000} s") }
        }

        suspend fun post(body: JSONObject) = withContext(Dispatchers.IO) {
            val req = Request.Builder().url(postUrl).header("Content-Type", "application/json").auth()
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            val c = HttpJson.client.newCall(req)
            c.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)
            c.execute().use { resp ->
                if (!resp.isSuccessful) throw McpException("SSE POST HTTP ${resp.code}: ${hintFor(resp.code)}", resp.code)
            }
        }

        fun close() {
            call?.cancel()
        }
    }

    companion object {
        const val PROTOCOL_VERSION = "2025-06-18"

        fun hintFor(code: Int): String = when (code) {
            401, 403 -> "zły token albo brak uprawnień"
            404 -> "nie ma serwera MCP pod tym adresem (w Home Assistant: dodaj integrację „Model Context Protocol Server”)"
            405 -> "serwer nie przyjmuje tej metody pod tym adresem – sprawdź adres (/api/mcp albo /mcp_server/sse)"
            else -> "błąd serwera"
        }

        /** Adres starszego transportu SSE: podany wprost (…/sse) albo standard Home Assistant /mcp_server/sse. */
        fun legacySseUrl(url: String): String {
            val u = url.trim().trimEnd('/')
            if (u.endsWith("/sse")) return u
            val uri = java.net.URI(u)
            val port = if (uri.port > 0) ":${uri.port}" else ""
            return "${uri.scheme}://${uri.host}$port/mcp_server/sse"
        }

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
