package pl.arekp.deskmate.ai.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Dostawca modelu językowego (Gemini, OpenRouter, Claude…). */
interface LlmProvider {
    /** Stały identyfikator, np. "gemini". */
    val id: String
    /** Nazwa do wyświetlenia, np. "Gemini (gemini-flash-latest)". */
    val label: String
    /** Czy korzystamy z darmowego pakietu (router w trybie AUTO zaczyna od darmowych). */
    val isFree: Boolean
    /** Czy wpisano klucz itp. */
    fun isConfigured(): Boolean

    /** Rzuca [ProviderException] przy błędzie. */
    suspend fun complete(request: ChatRequest): ChatResponse
}

/** Wspólny klient HTTP + mapowanie błędów na [ProviderException]. */
object HttpJson {
    private val JSON = "application/json".toMediaType()

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody(JSON))
                .build()
            try {
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) throw ProviderException.fromHttp(resp.code, text, resp.header("Retry-After"))
                    try {
                        JSONObject(text)
                    } catch (e: Exception) {
                        throw ProviderException(ProviderException.Kind.SERVER, "Niepoprawny JSON: ${text.take(200)}", cause = e)
                    }
                }
            } catch (e: IOException) {
                throw ProviderException(ProviderException.Kind.NETWORK, e.message ?: "błąd sieci", cause = e)
            }
        }
}
