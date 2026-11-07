package pl.arekp.deskmate

import com.sun.net.httpserver.HttpServer
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
import java.net.InetSocketAddress

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

/** Klient MCP przeciw udawanemu serwerowi (jak Home Assistant: SSE, sesja, token). */
class McpClientIntegrationTest {
    private lateinit var server: HttpServer
    private val calls = mutableListOf<String>()
    private var expireSessionOnce = false

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/api/mcp") { ex ->
            val body = JSONObject(ex.requestBody.bufferedReader().readText())
            val method = body.optString("method")
            calls += method
            fun reply(code: Int, text: String, sse: Boolean = false, session: String? = null) {
                ex.responseHeaders.add("Content-Type", if (sse) "text/event-stream" else "application/json")
                session?.let { ex.responseHeaders.add("Mcp-Session-Id", it) }
                val bytes = text.toByteArray()
                ex.sendResponseHeaders(code, if (bytes.isEmpty()) -1 else bytes.size.toLong())
                if (bytes.isNotEmpty()) ex.responseBody.use { it.write(bytes) } else ex.close()
            }
            if (ex.requestHeaders.getFirst("Authorization") != "Bearer sekret") return@createContext reply(401, "{}")
            val id = body.opt("id")
            when (method) {
                "initialize" -> reply(
                    200,
                    "event: message\ndata: " + JSONObject().put("jsonrpc", "2.0").put("id", id).put(
                        "result",
                        JSONObject().put("protocolVersion", "2025-06-18").put("serverInfo", JSONObject().put("name", "home-assistant")),
                    ) + "\n\n",
                    sse = true, session = "sesja-1",
                )
                "notifications/initialized" -> reply(202, "")
                else -> {
                    if (ex.requestHeaders.getFirst("Mcp-Session-Id") != "sesja-1") return@createContext reply(400, "{}")
                    if (expireSessionOnce && method == "tools/call") {
                        expireSessionOnce = false
                        return@createContext reply(404, "{}")
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
                    reply(200, JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", result).toString())
                }
            }
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun url() = "http://127.0.0.1:${server.address.port}/api/mcp"

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
