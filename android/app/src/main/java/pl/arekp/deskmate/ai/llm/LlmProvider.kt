package pl.arekp.deskmate.ai.llm

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

/**
 * Wspólny klient HTTP + mapowanie błędów na [ProviderException].
 * Krótkie limity czasu (robot ma odpowiadać od razu, a router w razie czego zapyta inny model)
 * i prawdziwe anulowanie – gdy router dostanie odpowiedź od szybszego modelu, wolniejsze zapytanie jest przerywane.
 */
object HttpJson {
    private val JSON = "application/json".toMediaType()

    /** Cały czas jednego zapytania (połączenie + odpowiedź). */
    const val CALL_TIMEOUT_MS = 15_000L

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    suspend fun post(url: String, headers: Map<String, String>, body: JSONObject): JSONObject =
        execute(
            Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
                .post(body.toString().toRequestBody(JSON)).build(),
        )

    suspend fun put(url: String, headers: Map<String, String>, body: JSONObject): JSONObject =
        execute(
            Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }
                .put(body.toString().toRequestBody(JSON)).build(),
        )

    suspend fun get(url: String, headers: Map<String, String> = emptyMap()): JSONObject =
        execute(Request.Builder().url(url).apply { headers.forEach { (k, v) -> header(k, v) } }.get().build())

    private suspend fun execute(request: Request): JSONObject {
        val text = suspendCancellableCoroutine { cont ->
            val call = client.newCall(request)
            cont.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    if (cont.isActive) {
                        val kind = if (e is java.io.InterruptedIOException) ProviderException.Kind.SERVER else ProviderException.Kind.NETWORK
                        cont.resumeWithException(ProviderException(kind, e.message ?: "błąd sieci", cause = e))
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    response.use { resp ->
                        val text = runCatching { resp.body?.string().orEmpty() }.getOrElse { "" }
                        when {
                            !cont.isActive -> Unit
                            !resp.isSuccessful ->
                                cont.resumeWithException(ProviderException.fromHttp(resp.code, text, resp.header("Retry-After")))
                            else -> cont.resume(text)
                        }
                    }
                }
            })
        }
        return try {
            JSONObject(text)
        } catch (e: Exception) {
            throw ProviderException(ProviderException.Kind.SERVER, "Niepoprawny JSON: ${text.take(200)}", cause = e)
        }
    }
}
