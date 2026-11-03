package pl.arekp.deskmate.ai.llm

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Router modeli: wybiera dostawcę według trybu i automatycznie przełącza się na następnego,
 * gdy bieżący trafi na limit, brak środków, zły klucz albo awarię.
 *
 * Dostawca z błędem trafia na "ławkę kar" (cooldown) – przez ten czas jest pomijany:
 *  - limit zapytań (429): czas z nagłówka Retry-After albo 60 s,
 *  - brak środków / pakietu (402): 1 h,
 *  - zły klucz (401/403): 30 min,
 *  - awaria serwera / sieć: 30 s,
 *  - odrzucone zapytanie (400): bez kary – po prostu próbujemy następnego.
 */
class LlmRouter(
    private val providers: List<LlmProvider>,
    private val mode: () -> Mode,
    private val clock: () -> Long = { System.currentTimeMillis() },
    /** Zdarzenia do pokazania użytkownikowi (pasek informacji), np. "Gemini: limit zapytań". */
    private val onEvent: (String) -> Unit = {},
    /** Po tylu ms bez odpowiedzi pytamy równolegle następnego darmowego dostawcę. */
    private val hedgeMs: Long = HEDGE_MS,
) {
    enum class Mode(val label: String) {
        /** Najpierw darmowe (Gemini → OpenRouter), płatny Claude tylko awaryjnie. */
        AUTO("Automatycznie (najpierw darmowe)"),
        /** Najpierw najlepsza jakość (Claude), potem darmowe. */
        QUALITY("Najlepsza jakość"),
        /** Tylko darmowe – nigdy nie używa płatnych. */
        FREE_ONLY("Tylko darmowe"),
        GEMINI("Tylko Gemini"),
        OPENROUTER("Tylko OpenRouter"),
        CLAUDE("Tylko Claude"),
    }

    data class Routed(val provider: LlmProvider, val response: ChatResponse)

    class AllProvidersFailed(val errors: Map<String, Throwable>) :
        Exception("Żaden model nie odpowiedział: " + errors.entries.joinToString { "${it.key}: ${it.value.message}" })

    private val cooldownUntil = mutableMapOf<String, Long>()

    /** Kolejność dostawców dla bieżącego trybu (bez filtrowania). */
    fun order(): List<LlmProvider> {
        fun byId(vararg ids: String) = ids.mapNotNull { id -> providers.firstOrNull { it.id == id } }
        return when (mode()) {
            Mode.AUTO -> providers.filter { it.isFree } + providers.filter { !it.isFree }
            Mode.QUALITY -> byId(ClaudeProvider.ID) + providers.filter { it.id != ClaudeProvider.ID }
            Mode.FREE_ONLY -> providers.filter { it.isFree }
            Mode.GEMINI -> byId(GeminiProvider.ID)
            Mode.OPENROUTER -> byId(OpenRouterProvider.ID)
            Mode.CLAUDE -> byId(ClaudeProvider.ID)
        }
    }

    /** Dostawcy, których router spróbuje teraz, w kolejności. */
    fun candidates(): List<LlmProvider> {
        val now = clock()
        val configured = order().filter { it.isConfigured() }
        val ready = configured.filter { (cooldownUntil[it.id] ?: 0L) <= now }
        // Gdy wszyscy są na ławce kar, spróbuj mimo to (lepiej niż milczeć)
        return ready.ifEmpty { configured }
    }

    fun isAnyConfigured(): Boolean = order().any { it.isConfigured() }

    /**
     * Pyta dostawców po kolei. Gdy bieżący nie odpowie w [hedgeMs], równolegle pyta następnego
     * DARMOWEGO (płatnego nie uruchamiamy „na zapas”) – wygrywa pierwsza poprawna odpowiedź,
     * pozostałe zapytania są anulowane. Błąd od razu przechodzi do następnego dostawcy.
     */
    suspend fun complete(request: ChatRequest): Routed {
        val queue = candidates()
        if (queue.isEmpty()) throw AllProvidersFailed(emptyMap())
        val errors = linkedMapOf<String, Throwable>()
        val results = Channel<Pair<LlmProvider, Result<ChatResponse>>>(Channel.UNLIMITED)
        return coroutineScope {
            var next = 0
            var running = 0
            fun launchNext() {
                val p = queue[next++]
                running++
                launch {
                    val r = try {
                        Result.success(p.complete(request))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        Result.failure(e)
                    }
                    results.send(p to r)
                }
            }
            launchNext()
            var winner: Routed? = null
            while (winner == null) {
                val canHedge = next < queue.size && queue[next].isFree
                val r = if (canHedge) withTimeoutOrNull(hedgeMs) { results.receive() } else results.receive()
                if (r == null) {
                    onEvent("${queue[next - 1].label}: odpowiada wolno – pytam też ${queue[next].label}")
                    launchNext()
                    continue
                }
                running--
                val (p, res) = r
                val response = res.getOrNull()
                if (response != null) {
                    cooldownUntil.remove(p.id)
                    winner = Routed(p, response)
                } else {
                    val e = res.exceptionOrNull()!!
                    errors[p.id] = e
                    if (e is ProviderException) {
                        penalize(p, e)
                        onEvent("${p.label}: ${describe(e.kind)} – próbuję dalej")
                        logWarn("${p.label}: ${e.kind} ${e.message}")
                    } else {
                        cooldownUntil[p.id] = clock() + SERVER_COOLDOWN_MS
                        onEvent("${p.label}: błąd – próbuję dalej")
                        logWarn("${p.label}: ${e.message}")
                    }
                    if (running == 0) {
                        if (next < queue.size) launchNext() else throw AllProvidersFailed(errors)
                    }
                }
            }
            coroutineContext.cancelChildren() // przerwij wolniejsze zapytania
            winner!!
        }
    }

    private fun penalize(p: LlmProvider, e: ProviderException) {
        val ms = when (e.kind) {
            ProviderException.Kind.RATE_LIMIT -> e.retryAfterMs ?: RATE_LIMIT_COOLDOWN_MS
            ProviderException.Kind.QUOTA -> QUOTA_COOLDOWN_MS
            ProviderException.Kind.AUTH -> AUTH_COOLDOWN_MS
            ProviderException.Kind.SERVER, ProviderException.Kind.NETWORK -> SERVER_COOLDOWN_MS
            ProviderException.Kind.BAD_REQUEST -> 0L
        }
        if (ms > 0) cooldownUntil[p.id] = clock() + ms
    }

    private fun describe(kind: ProviderException.Kind) = when (kind) {
        ProviderException.Kind.RATE_LIMIT -> "limit zapytań"
        ProviderException.Kind.QUOTA -> "wyczerpany pakiet"
        ProviderException.Kind.AUTH -> "zły klucz"
        ProviderException.Kind.SERVER -> "awaria serwera"
        ProviderException.Kind.NETWORK -> "brak sieci"
        ProviderException.Kind.BAD_REQUEST -> "odrzucone zapytanie"
    }

    private fun logWarn(msg: String) {
        runCatching { Log.w("LlmRouter", msg) }
    }

    companion object {
        const val RATE_LIMIT_COOLDOWN_MS = 60_000L
        const val QUOTA_COOLDOWN_MS = 60 * 60_000L
        const val AUTH_COOLDOWN_MS = 30 * 60_000L
        const val SERVER_COOLDOWN_MS = 30_000L
        const val HEDGE_MS = 5_000L
    }
}
