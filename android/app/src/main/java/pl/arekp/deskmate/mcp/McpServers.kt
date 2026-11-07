package pl.arekp.deskmate.mcp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import pl.arekp.deskmate.DiagLog
import pl.arekp.deskmate.ai.AssistantTool
import pl.arekp.deskmate.ai.ToolResult

/** Jeden serwer MCP skonfigurowany przez użytkownika (ustawienia → Serwery MCP). */
data class McpServerConfig(
    /** Krótka nazwa = przedrostek narzędzi, np. „ha” → `ha__HassTurnOn`. */
    val name: String,
    val url: String,
    val token: String = "",
    val enabled: Boolean = true,
) {
    val prefix: String get() = McpSchema.safeName(name.lowercase()).take(12).ifBlank { "mcp" }

    fun toJson(): JSONObject = JSONObject().put("name", name).put("url", url).put("token", token).put("enabled", enabled)

    companion object {
        fun fromJson(j: JSONObject) = McpServerConfig(
            j.optString("name"), j.optString("url"), j.optString("token"), j.optBoolean("enabled", true),
        )

        fun listToJson(list: List<McpServerConfig>): String = JSONArray(list.map { it.toJson() }).toString()

        fun listFromJson(json: String): List<McpServerConfig> = runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.let(::fromJson) }.filter { it.url.isNotBlank() }
        }.getOrDefault(emptyList())

        /**
         * Import konfiguracji w formacie znanym z innych aplikacji:
         * `{"mcpServers": {"ha": {"url": "...", "headers": {"Authorization": "Bearer ..."}}}}`.
         */
        fun importMcpServers(json: String): List<McpServerConfig> {
            val root = JSONObject(json)
            val servers = root.optJSONObject("mcpServers") ?: root
            return servers.keys().asSequence().mapNotNull { key ->
                val s = servers.optJSONObject(key) ?: return@mapNotNull null
                val url = s.optString("url").ifBlank { s.optString("serverUrl") }
                if (url.isBlank()) return@mapNotNull null
                val auth = s.optJSONObject("headers")?.optString("Authorization").orEmpty()
                McpServerConfig(key, url, auth.removePrefix("Bearer ").trim().ifBlank { s.optString("token") })
            }.toList()
        }

        /** Domyślny adres Home Assistant (integracja „Model Context Protocol Server”). */
        const val HOME_ASSISTANT_URL_HINT = "http://homeassistant.local:8123/api/mcp"
    }
}

/** Schematy i nazwy narzędzi MCP dopasowane do tego, co przyjmują modele (szczególnie Gemini). */
object McpSchema {
    /** Nazwa funkcji: litery, cyfry, _ ; maks. 64 znaki (wymóg Gemini/OpenAI). */
    fun safeName(s: String): String = s.replace(Regex("[^A-Za-z0-9_]"), "_").trim('_')

    fun toolName(prefix: String, remote: String): String =
        "${prefix}__${safeName(remote)}".take(64).let { if (it.first().isLetter()) it else "t$it".take(64) }

    private val ALLOWED = setOf("type", "description", "properties", "required", "items", "enum", "format", "minimum", "maximum", "nullable")

    /**
     * Upraszcza JSON Schema do podzbioru akceptowanego przez Gemini (inaczej HTTP 400):
     * usuwa `$schema`, `additionalProperties`, `default`, `title`…, zamienia `type: ["string","null"]` na `string`,
     * z `anyOf`/`oneOf` bierze pierwszą sensowną opcję.
     */
    fun sanitize(schema: JSONObject, depth: Int = 0): JSONObject {
        var src = schema
        for (key in listOf("anyOf", "oneOf", "allOf")) {
            val alts = src.optJSONArray(key) ?: continue
            val pick = (0 until alts.length()).mapNotNull { alts.optJSONObject(it) }
                .firstOrNull { it.optString("type") != "null" } ?: continue
            src = JSONObject(src.toString()).apply { remove(key) }
            pick.keys().forEach { k -> if (!src.has(k)) src.put(k, pick.get(k)) }
        }
        val out = JSONObject()
        for (k in src.keys()) {
            if (k !in ALLOWED) continue
            val v = src.get(k)
            when (k) {
                "type" -> out.put("type", if (v is JSONArray) (0 until v.length()).map { v.optString(it) }.firstOrNull { it != "null" } ?: "string" else v)
                "properties" -> if (v is JSONObject && depth < 6) {
                    val props = JSONObject()
                    v.keys().forEach { p -> v.optJSONObject(p)?.let { props.put(p, sanitize(it, depth + 1)) } }
                    out.put("properties", props)
                }
                "items" -> if (v is JSONObject && depth < 6) out.put("items", sanitize(v, depth + 1))
                "format" -> if (src.optString("type") == "string" && v in setOf("enum", "date-time")) out.put(k, v)
                "required" -> if (v is JSONArray && v.length() > 0) out.put(k, v)
                else -> out.put(k, v)
            }
        }
        if (!out.has("type")) out.put("type", if (out.has("properties")) "object" else "string")
        if (out.optString("type") == "array" && !out.has("items")) out.put("items", JSONObject().put("type", "string"))
        // „required” może wymieniać tylko istniejące pola
        out.optJSONArray("required")?.let { req ->
            val props = out.optJSONObject("properties") ?: JSONObject()
            val kept = (0 until req.length()).map { req.optString(it) }.filter { props.has(it) }
            if (kept.isEmpty()) out.remove("required") else out.put("required", JSONArray(kept))
        }
        return out
    }
}

/** Narzędzie z serwera MCP widziane przez agenta jak każde inne. */
class McpTool(
    private val client: McpClient,
    private val remote: McpClient.RemoteTool,
    prefix: String,
    serverLabel: String,
) : AssistantTool {
    override val name = McpSchema.toolName(prefix, remote.name)
    override val description = "[$serverLabel] ${remote.description}".take(1000)
    override val inputSchema: JSONObject = McpSchema.sanitize(remote.inputSchema).also {
        if (it.optString("type") != "object") it.put("type", "object")
    }

    override suspend fun run(input: JSONObject): ToolResult = try {
        val r = client.callTool(remote.name, input)
        ToolResult.text(r.text, isError = r.isError)
    } catch (e: Exception) {
        ToolResult.text("Serwer $name niedostępny: ${e.message}", isError = true)
    }
}

/**
 * Zarządza serwerami MCP: łączy w tle, trzyma listę narzędzi, podaje ją agentowi.
 * Agent pyta [tools] przy każdym zapytaniu, więc nowy serwer działa bez restartu aplikacji.
 */
class McpManager(
    private val scope: CoroutineScope,
    private val configs: () -> List<McpServerConfig>,
    private val onEvent: (String) -> Unit = {},
) {
    data class Status(val server: String, val ok: Boolean, val text: String)

    private val mutex = Mutex()
    @Volatile private var connected: List<McpTool> = emptyList()
    @Volatile var statuses: List<Status> = emptyList()
        private set

    fun tools(): List<AssistantTool> = connected

    /** Łączy (ponownie) wszystkie włączone serwery – przy starcie i po zmianie ustawień. */
    fun refresh() {
        scope.launch {
            mutex.withLock {
                val all = mutableListOf<McpTool>()
                val st = mutableListOf<Status>()
                for (c in configs().filter { it.enabled }) {
                    val r = load(c)
                    st += r.first
                    all += r.second
                }
                connected = all
                statuses = st
                if (st.isNotEmpty()) onEvent("MCP: " + st.joinToString("; ") { it.text })
            }
        }
    }

    /** Test jednego serwera (przycisk w ustawieniach) – bez zmiany aktywnych narzędzi. */
    suspend fun test(c: McpServerConfig): String {
        val (status, tools) = load(c)
        return if (!status.ok) "❌ ${status.text}" else
            "✅ ${status.text}: " + tools.joinToString(", ") { it.name.substringAfter("__") }.take(400)
    }

    private suspend fun load(c: McpServerConfig): Pair<Status, List<McpTool>> = try {
        val client = McpClient(c.url.trim(), c.token)
        client.connect()
        val tools = client.listTools().map { McpTool(client, it, c.prefix, client.serverName.ifBlank { c.name }) }
        DiagLog.i("MCP", "${c.name} (${c.url}): połączono z ${client.serverName}, narzędzia: ${tools.joinToString { it.name }}")
        Status(c.name, true, "${c.name} – ${tools.size} narzędzi") to tools
    } catch (e: Exception) {
        DiagLog.w("MCP", "${c.name} (${c.url}): ${e.message}")
        Status(c.name, false, "${c.name} – ${e.message?.take(120)}") to emptyList()
    }
}
