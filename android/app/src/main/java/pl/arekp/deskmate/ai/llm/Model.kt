package pl.arekp.deskmate.ai.llm

import org.json.JSONObject

/*
 * Wspólny (niezależny od dostawcy) format rozmowy.
 * Każdy dostawca tłumaczy go na swój format API i z powrotem – dzięki temu router może
 * przełączyć model nawet w środku rozmowy, a historia zostaje ta sama.
 */

enum class Role { USER, ASSISTANT }

data class ImageData(val mediaType: String, val base64: String)

sealed interface Part {
    data class Text(val text: String) : Part

    data class Image(val image: ImageData) : Part

    /**
     * Wywołanie narzędzia przez model.
     * [raw] + [rawProvider] – oryginalny fragment odpowiedzi; gdy rozmowa wraca do tego samego
     * dostawcy, odsyłamy go bez zmian (np. Gemini wymaga zwrotu "thought signature").
     */
    data class ToolCall(
        val id: String,
        val name: String,
        val args: JSONObject,
        val raw: JSONObject? = null,
        val rawProvider: String? = null,
    ) : Part

    data class ToolResult(
        val callId: String,
        val name: String,
        val text: String,
        val images: List<ImageData> = emptyList(),
        val isError: Boolean = false,
    ) : Part
}

data class ChatMessage(val role: Role, val parts: List<Part>) {
    val isPlainUserText: Boolean
        get() = role == Role.USER && parts.isNotEmpty() && parts.all { it is Part.Text || it is Part.Image }

    companion object {
        fun user(text: String) = ChatMessage(Role.USER, listOf(Part.Text(text)))
    }
}

/** Opis narzędzia przekazywany modelowi (schemat parametrów w JSON Schema). */
data class ToolSpec(val name: String, val description: String, val inputSchema: JSONObject)

data class ChatRequest(
    val system: String,
    val messages: List<ChatMessage>,
    val tools: List<ToolSpec>,
    val maxTokens: Int,
)

data class ChatResponse(val parts: List<Part>) {
    val toolCalls: List<Part.ToolCall> get() = parts.filterIsInstance<Part.ToolCall>()
    val text: String get() = parts.filterIsInstance<Part.Text>().joinToString(" ") { it.text }.trim()
}

/** Błąd dostawcy, sklasyfikowany tak, żeby router wiedział, co zrobić. */
class ProviderException(
    val kind: Kind,
    message: String,
    val retryAfterMs: Long? = null,
    cause: Throwable? = null,
    /** Krótki opis od serwera, np. „API key not valid”. */
    val detail: String = "",
    /** Rodzaj przekroczonego limitu (Google QuotaFailure.quotaId), np. …PerMinute… / …PerDay…. */
    val quotaId: String? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** 429 – limit zapytań; spróbuj innego, wróć po chwili. */
        RATE_LIMIT,
        /** 402 – brak środków / wyczerpany pakiet; wróć dużo później. */
        QUOTA,
        /** 401/403 – zły klucz. */
        AUTH,
        /** 5xx, przeciążenie. */
        SERVER,
        /** brak sieci, timeout. */
        NETWORK,
        /** 400/404/422 – dostawca nie obsłużył zapytania (np. model bez narzędzi). */
        BAD_REQUEST,
    }

    companion object {
        fun fromHttp(code: Int, body: String, retryAfterHeader: String?): ProviderException {
            val retry = retryAfterHeader?.trim()?.toLongOrNull()?.times(1000)
            val kind = when (code) {
                429 -> Kind.RATE_LIMIT
                402 -> Kind.QUOTA
                401, 403 -> Kind.AUTH
                in 500..599 -> Kind.SERVER
                else -> Kind.BAD_REQUEST
            }
            val (googleRetry, quotaId) = googleQuotaInfo(body)
            val detail = shortDetail(body) + (quotaId?.let { " [$it]" } ?: "")
            return ProviderException(kind, "HTTP $code: ${body.take(300)}", retry ?: googleRetry, detail = detail, quotaId = quotaId)
        }

        /**
         * Google (Gemini) w 429 podaje szczegóły: RetryInfo.retryDelay („37s”) i QuotaFailure.quotaId
         * (np. „GenerateRequestsPerMinutePerProjectPerModel-FreeTier” albo „…PerDay…”). Zwraca (czekaj ms, quotaId).
         */
        fun googleQuotaInfo(body: String): Pair<Long?, String?> = runCatching {
            val details = org.json.JSONObject(body).optJSONObject("error")?.optJSONArray("details") ?: return@runCatching null to null
            var retry: Long? = null
            var quota: String? = null
            for (i in 0 until details.length()) {
                val d = details.optJSONObject(i) ?: continue
                val type = d.optString("@type")
                if (type.endsWith("RetryInfo")) {
                    retry = d.optString("retryDelay").removeSuffix("s").toDoubleOrNull()?.let { (it * 1000).toLong() }
                }
                if (type.endsWith("QuotaFailure")) {
                    quota = d.optJSONArray("violations")?.optJSONObject(0)?.optString("quotaId")?.ifBlank { null }
                }
            }
            retry to quota
        }.getOrDefault(null to null)

        /** Limit dzienny (albo zerowy) – wtedy dłuższa przerwa; minutowy mija po kilkudziesięciu sekundach. */
        fun isDailyQuota(e: ProviderException): Boolean =
            e.quotaId?.contains("PerDay", ignoreCase = true) == true || e.message?.contains("limit: 0") == true

        /** Krótki opis błędu z odpowiedzi serwera (pole error.message albo message), do pokazania użytkownikowi. */
        fun shortDetail(body: String): String {
            val msg = runCatching {
                val j = org.json.JSONObject(body)
                val e = j.opt("error")
                when (e) {
                    is org.json.JSONObject -> e.optString("message")
                    is String -> e
                    else -> j.optString("message")
                }
            }.getOrNull().orEmpty().ifBlank { body }
            return msg.replace(Regex("\\s+"), " ").trim().take(90)
        }
    }
}
