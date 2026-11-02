package pl.arekp.deskmate.ai.llm

import android.util.Log

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

    suspend fun complete(request: ChatRequest): Routed {
        val errors = linkedMapOf<String, Throwable>()
        for (p in candidates()) {
            try {
                val response = p.complete(request)
                cooldownUntil.remove(p.id)
                return Routed(p, response)
            } catch (e: ProviderException) {
                errors[p.id] = e
                penalize(p, e)
                onEvent("${p.label}: ${describe(e.kind)} – próbuję dalej")
                logWarn("${p.label}: ${e.kind} ${e.message}")
            } catch (e: Exception) {
                errors[p.id] = e
                cooldownUntil[p.id] = clock() + SERVER_COOLDOWN_MS
                onEvent("${p.label}: błąd – próbuję dalej")
                logWarn("${p.label}: ${e.message}")
            }
        }
        throw AllProvidersFailed(errors)
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
    }
}
