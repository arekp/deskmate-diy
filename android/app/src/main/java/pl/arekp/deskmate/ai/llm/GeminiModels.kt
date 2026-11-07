package pl.arekp.deskmate.ai.llm

import pl.arekp.deskmate.DiagLog

/**
 * Wybór modelu Gemini z darmowym pakietem.
 *
 * Aliasy typu `gemini-flash-latest` potrafią wskazywać model BEZ darmowego pakietu (wtedy Google zwraca 429
 * „You exceeded your current quota” już przy pierwszym zapytaniu – logi 3.11.2026). Dlatego przy 429/404
 * próbujemy kolejnych modeli z darmowym pakietem i zapamiętujemy ten, który zadziałał.
 * Lista wg dokumentacji darmowego pakietu (październik 2026) – do aktualizacji, gdy Google zmieni modele.
 */
object GeminiModels {
    // gemini-2.5-flash: „no longer available to new users” (logi 3.11.2026) – usunięty z listy
    // stabilne kody wg dokumentacji Google (listopad 2026); 3.8-flash zalecany, *-lite lżejsze i rzadziej przeciążone
    val FREE_TIER = listOf("gemini-3.8-flash", "gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.1-flash-lite", "gemini-2.5-flash-lite")

    /** Gdy ŻADEN model nie ma limitu (problem konta/klucza), nie sprawdzamy ich ponownie przez [ACCOUNT_COOLDOWN_MS]. */
    const val ACCOUNT_COOLDOWN_MS = 10 * 60_000L
    @Volatile private var accountBlockedUntil = 0L
    @Volatile private var accountBlockReason = ""
    var clock: () -> Long = { System.currentTimeMillis() }

    /** Model, który ostatnio zadziałał, gdy model z ustawień nie miał limitu. */
    @Volatile private var working: String? = null

    /** Kolejność prób: (zapamiętany działający) → model z ustawień → darmowe z listy. */
    fun candidates(configured: String): List<String> =
        (listOfNotNull(working.takeIf { it != null && configuredFailed.contains(configured) }) + configured + FREE_TIER)
            .filter { it.isNotBlank() }.distinct()

    private val configuredFailed = mutableSetOf<String>()

    /** Czy ten błąd znaczy „ten model nie ma u Ciebie limitu / nie istnieje” – warto spróbować innego modelu. */
    fun isModelProblem(e: ProviderException): Boolean =
        e.kind == ProviderException.Kind.RATE_LIMIT || e.kind == ProviderException.Kind.QUOTA ||
            // 503 „This model is currently experiencing high demand” – inny model zwykle odpowiada od razu
            (e.kind == ProviderException.Kind.SERVER && (e.message?.contains("HTTP 503") == true ||
                e.detail.contains("high demand", ignoreCase = true) || e.detail.contains("overloaded", ignoreCase = true))) ||
            (e.kind == ProviderException.Kind.BAD_REQUEST && (e.message?.contains("HTTP 404") == true ||
                e.detail.contains("not found", ignoreCase = true)))

    /**
     * Wykonuje [call] dla kolejnych modeli, aż któryś zadziała. Rzuca ostatni błąd, gdy żaden.
     * @return wynik i nazwa modelu, który odpowiedział
     */
    suspend fun <T> withFallback(configured: String, call: suspend (model: String) -> T): Pair<T, String> {
        if (clock() < accountBlockedUntil) {
            throw ProviderException(ProviderException.Kind.QUOTA, "Gemini bez limitu na tym koncie (przerwa)", detail = accountBlockReason)
        }
        var last: ProviderException? = null
        var allQuota = true
        for (m in candidates(configured)) {
            try {
                val r = call(m)
                if (m != configured) {
                    if (working != m) DiagLog.i("Gemini", "Model $configured bez limitu – używam $m")
                    configuredFailed += configured
                    working = m
                }
                return r to m
            } catch (e: ProviderException) {
                if (!isModelProblem(e)) throw e
                DiagLog.w("Gemini", "Model $m: ${e.kind} ${e.detail.take(80)} – próbuję następny")
                if (e.kind != ProviderException.Kind.RATE_LIMIT && e.kind != ProviderException.Kind.QUOTA) allQuota = false
                last = e
            }
        }
        if (allQuota && last != null) {
            accountBlockedUntil = clock() + ACCOUNT_COOLDOWN_MS
            accountBlockReason = "żaden model Gemini nie ma limitu na tym kluczu: ${last.detail.take(60)}"
            DiagLog.w("Gemini", "Wszystkie modele bez limitu – Gemini wstrzymany na 10 min (sprawdź klucz/projekt w AI Studio)")
        }
        throw last ?: ProviderException(ProviderException.Kind.RATE_LIMIT, "Brak dostępnego modelu Gemini")
    }
}
