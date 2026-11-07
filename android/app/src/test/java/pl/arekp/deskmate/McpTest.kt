package pl.arekp.deskmate

import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import pl.arekp.deskmate.mcp.McpClient
import pl.arekp.deskmate.mcp.McpSchema
import pl.arekp.deskmate.mcp.McpServerConfig
import pl.arekp.deskmate.mcp.McpTool

class McpParsingTest {

    @Test
    fun sse_picksMessageWithMatchingId() {
        val body = "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/progress\"}\n\n" +
            "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":7,\"result\":{\"ok\":true}}\n\n"
        assertTrue(McpClient.parseSse(body, 7).getJSONObject("result").getBoolean("ok"))
    }

    @Test
    fun callResult_joinsTextContent() {
        val r = JSONObject("""{"content":[{"type":"text","text":"Włączono światło w salonie"},{"type":"image","mimeType":"image/png","data":"…"}],"isError":false}""")
        val c = McpClient.parseCallResult(r)
        assertEquals("Włączono światło w salonie\n[obraz image/png]", c.text)
        assertFalse(c.isError)
    }

    @Test
    fun schema_isSimplifiedForGemini() {
        val s = JSONObject(
            """
            {"${'$'}schema":"http://json-schema.org/draft-07/schema#","type":"object","additionalProperties":false,
             "properties":{
               "name":{"type":["string","null"],"title":"Name","default":null},
               "brightness":{"anyOf":[{"type":"integer","minimum":0,"maximum":100},{"type":"null"}]},
               "area":{"type":"array"}
             },
             "required":["name","nie_ma"]}
            """.trimIndent(),
        )
        val out = McpSchema.sanitize(s)
        assertFalse(out.has("${'$'}schema"))
        assertFalse(out.has("additionalProperties"))
        val props = out.getJSONObject("properties")
        assertEquals("string", props.getJSONObject("name").getString("type"))
        assertFalse(props.getJSONObject("name").has("title"))
        assertEquals("integer", props.getJSONObject("brightness").getString("type"))
        assertEquals(100, props.getJSONObject("brightness").getInt("maximum"))
        assertEquals("string", props.getJSONObject("area").getJSONObject("items").getString("type"))
        assertEquals(JSONArray(listOf("name")).toString(), out.getJSONArray("required").toString())
    }

    @Test
    fun toolNames_areSafeAndPrefixed() {
        assertEquals("ha__HassTurnOn", McpSchema.toolName("ha", "HassTurnOn"))
        assertEquals("ha__light_turn_on", McpSchema.toolName("ha", "light.turn-on"))
        assertTrue(McpSchema.toolName("ha", "x".repeat(100)).length <= 64)
    }

    @Test
    fun configRoundTripAndImport() {
        val list = listOf(McpServerConfig("ha", "http://192.168.1.10:8123/api/mcp", "tok", true))
        assertEquals(list, McpServerConfig.listFromJson(McpServerConfig.listToJson(list)))
        val imported = McpServerConfig.importMcpServers(
            """{"mcpServers":{"dom":{"url":"https://ha.example/api/mcp","headers":{"Authorization":"Bearer abc123"}}}}""",
        )
        assertEquals(listOf(McpServerConfig("dom", "https://ha.example/api/mcp", "abc123")), imported)
        assertEquals("ha", McpServerConfig("HA", "u").prefix)
    }
}

/** Minimalny serwer HTTP na gniazdach (pakiet com.sun.net.httpserver nie jest dostępny w testach Androida). */
class TinyHttpServer(private val handler: (method: String, headers: Map<String, String>, body: String) -> Triple<Int, Map<String, String>, String>) {
    private val socket = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort
    @Volatile private var running = true

    fun start() {
        Thread {
            while (running) {
                val c = runCatching { socket.accept() }.getOrNull() ?: break
                Thread { serve(c) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun serve(c: java.net.Socket) = c.use {
        val input = java.io.BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        while (running) {
            val requestLine = readLine(input) ?: return
            if (requestLine.isBlank()) return
            val headers = mutableMapOf<String, String>()
            while (true) {
                val l = readLine(input) ?: return
                if (l.isEmpty()) break
                val k = l.substringBefore(":").trim().lowercase()
                headers[k] = l.substringAfter(":").trim()
            }
            val len = headers["content-length"]?.toIntOrNull() ?: 0
            val buf = ByteArray(len)
            var read = 0
            while (read < len) { val n = input.read(buf, read, len - read); if (n < 0) return; read += n }
            val (code, respHeaders, body) = handler(requestLine.substringBefore(" "), headers, String(buf))
            val bytes = body.toByteArray()
            val sb = StringBuilder("HTTP/1.1 $code X\r\nContent-Length: ${bytes.size}\r\n")
            respHeaders.forEach { (k, v) -> sb.append("$k: $v\r\n") }
            sb.append("\r\n")
            out.write(sb.toString().toByteArray()); out.write(bytes); out.flush()
        }
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    fun stop() { running = false; socket.close() }
}

/** Klient MCP przeciw udawanemu serwerowi (jak Home Assistant: SSE, sesja, token). */
class McpClientIntegrationTest {
    private lateinit var server: TinyHttpServer
    private val calls = mutableListOf<String>()
    private var expireSessionOnce = false

    @Before
    fun start() {
        server = TinyHttpServer { _, headers, raw ->
            val body = JSONObject(raw)
            val method = body.optString("method")
            synchronized(calls) { calls += method }
            fun json(code: Int, text: String, sse: Boolean = false, session: String? = null): Triple<Int, Map<String, String>, String> =
                Triple(code, buildMap {
                    put("Content-Type", if (sse) "text/event-stream" else "application/json")
                    session?.let { put("Mcp-Session-Id", it) }
                }, text)
            if (headers["authorization"] != "Bearer sekret") return@TinyHttpServer json(401, "{}")
            val id = body.opt("id")
            when (method) {
                "initialize" -> json(
                    200,
                    "event: message\ndata: " + JSONObject().put("jsonrpc", "2.0").put("id", id).put(
                        "result",
                        JSONObject().put("protocolVersion", "2025-06-18").put("serverInfo", JSONObject().put("name", "home-assistant")),
                    ) + "\n\n",
                    sse = true, session = "sesja-1",
                )
                "notifications/initialized" -> json(202, "")
                else -> {
                    if (headers["mcp-session-id"] != "sesja-1") return@TinyHttpServer json(400, "{}")
                    if (expireSessionOnce && method == "tools/call") {
                        expireSessionOnce = false
                        return@TinyHttpServer json(404, "{}")
                    }
                    val result = when (method) {
                        "tools/list" -> JSONObject().put(
                            "tools",
                            JSONArray().put(
                                JSONObject().put("name", "HassTurnOn").put("description", "Włącza urządzenie")
                                    .put("inputSchema", JSONObject("""{"type":"object","properties":{"name":{"type":"string"}},"additionalProperties":false}""")),
                            ),
                        )
                        "tools/call" -> JSONObject().put(
                            "content",
                            JSONArray().put(JSONObject().put("type", "text").put("text", "Włączono: " + body.getJSONObject("params").getJSONObject("arguments").optString("name"))),
                        )
                        else -> JSONObject()
                    }
                    json(200, JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.stop()

    private fun url() = "http://127.0.0.1:${server.port}/api/mcp"

    @Test
    fun fullFlow_initListCall() = runBlocking {
        val client = McpClient(url(), "sekret")
        client.connect()
        assertEquals("home-assistant", client.serverName)
        val tools = client.listTools()
        assertEquals(listOf("HassTurnOn"), tools.map { it.name })
        val tool = McpTool(client, tools.first(), "ha", "Home Assistant")
        assertEquals("ha__HassTurnOn", tool.name)
        assertFalse(tool.inputSchema.has("additionalProperties"))
        val r = tool.run(JSONObject().put("name", "lampa w salonie"))
        assertEquals("Włączono: lampa w salonie", r.text)
        assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call"), calls)
    }

    @Test
    fun expiredSession_reconnectsAndRetries() = runBlocking {
        val client = McpClient(url(), "sekret")
        client.connect()
        expireSessionOnce = true
        assertEquals("Włączono: lampa", client.callTool("HassTurnOn", JSONObject().put("name", "lampa")).text)
        assertEquals(2, calls.count { it == "initialize" })
    }

    @Test
    fun wrongToken_givesClearError() = runBlocking {
        val e = runCatching { McpClient(url(), "zly").connect() }.exceptionOrNull()
        assertTrue(e?.message.orEmpty(), e?.message.orEmpty().contains("zły token"))
    }
}

/**
 * Udawany Home Assistant ze starszym transportem SSE: POST /api/mcp → 405,
 * GET /mcp_server/sse → strumień (endpoint + odpowiedzi), POST /mcp_server/messages/abc → 202.
 */
class LegacySseServer {
    private val socket = java.net.ServerSocket(0, 50, java.net.InetAddress.getByName("127.0.0.1"))
    val port: Int get() = socket.localPort
    @Volatile private var stream: java.io.OutputStream? = null
    private val streamReady = java.util.concurrent.CountDownLatch(1)
    val methods = java.util.Collections.synchronizedList(mutableListOf<String>())

    fun start() {
        Thread {
            while (true) {
                val c = runCatching { socket.accept() }.getOrNull() ?: break
                Thread { serve(c) }.apply { isDaemon = true }.start()
            }
        }.apply { isDaemon = true }.start()
    }

    private fun readLine(input: java.io.InputStream): String? {
        val sb = StringBuilder()
        while (true) {
            val b = input.read()
            if (b < 0) return if (sb.isEmpty()) null else sb.toString()
            if (b == '\n'.code) return sb.toString().trimEnd('\r')
            sb.append(b.toChar())
        }
    }

    private fun serve(c: java.net.Socket) {
        val input = java.io.BufferedInputStream(c.getInputStream())
        val out = c.getOutputStream()
        while (true) {
            val requestLine = readLine(input) ?: return
            if (requestLine.isBlank()) return
            val (method, path) = requestLine.split(" ").let { it[0] to it[1] }
            val headers = mutableMapOf<String, String>()
            while (true) {
                val l = readLine(input) ?: return
                if (l.isEmpty()) break
                headers[l.substringBefore(":").trim().lowercase()] = l.substringAfter(":").trim()
            }
            val len = headers["content-length"]?.toIntOrNull() ?: 0
            val buf = ByteArray(len)
            var read = 0
            while (read < len) { val n = input.read(buf, read, len - read); if (n < 0) return; read += n }
            fun reply(code: Int) {
                out.write("HTTP/1.1 $code X\r\nContent-Length: 0\r\n\r\n".toByteArray()); out.flush()
            }
            if (headers["authorization"] != "Bearer sekret") { reply(401); continue }
            when {
                method == "POST" && path == "/api/mcp" -> reply(405)
                method == "GET" && path == "/mcp_server/sse" -> {
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nCache-Control: no-cache\r\n\r\n".toByteArray())
                    out.write("event: endpoint\ndata: /mcp_server/messages/abc\n\n".toByteArray())
                    out.flush()
                    stream = out
                    streamReady.countDown()
                    // strumień zostaje otwarty – wątek czeka, aż test się skończy
                    runCatching { Thread.sleep(60_000) }
                    return
                }
                method == "POST" && path == "/mcp_server/messages/abc" -> {
                    val body = JSONObject(String(buf))
                    methods += body.optString("method")
                    reply(202)
                    if (body.has("id")) {
                        val result = when (body.optString("method")) {
                            "initialize" -> JSONObject().put("protocolVersion", "2024-11-05").put("serverInfo", JSONObject().put("name", "home-assistant"))
                            "tools/list" -> JSONObject().put("tools", JSONArray().put(JSONObject().put("name", "HassTurnOff").put("description", "Wyłącza")
                                .put("inputSchema", JSONObject().put("type", "object"))))
                            "tools/call" -> JSONObject().put("content", JSONArray().put(JSONObject().put("type", "text").put("text", "Wyłączono")))
                            else -> JSONObject()
                        }
                        val msg = JSONObject().put("jsonrpc", "2.0").put("id", body.get("id")).put("result", result)
                        streamReady.await()
                        synchronized(this) {
                            stream!!.write("event: message\ndata: $msg\n\n".toByteArray()); stream!!.flush()
                        }
                    }
                }
                else -> reply(404)
            }
        }
    }

    fun stop() = socket.close()
}

class McpLegacySseTest {
    @Test
    fun method405_switchesToSseTransport_andWorks() = runBlocking {
        val server = LegacySseServer().apply { start() }
        try {
            val client = McpClient("http://127.0.0.1:${server.port}/api/mcp", "sekret", timeoutMs = 5_000)
            client.connect()
            assertEquals("SSE (starszy)", client.transport)
            assertEquals("home-assistant", client.serverName)
            assertEquals(listOf("HassTurnOff"), client.listTools().map { it.name })
            assertEquals("Wyłączono", client.callTool("HassTurnOff", JSONObject().put("name", "lampa")).text)
            assertEquals(listOf("initialize", "notifications/initialized", "tools/list", "tools/call"), server.methods.toList())
        } finally {
            server.stop()
        }
    }

    @Test
    fun legacyUrl_isDerivedForHomeAssistant() {
        assertEquals("http://192.168.1.5:8123/mcp_server/sse", McpClient.legacySseUrl("http://192.168.1.5:8123/api/mcp"))
        assertEquals("https://ha.example/mcp_server/sse", McpClient.legacySseUrl("https://ha.example/mcp_server/sse/"))
    }
}
