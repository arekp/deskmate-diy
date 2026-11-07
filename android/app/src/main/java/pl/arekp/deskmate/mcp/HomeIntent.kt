package pl.arekp.deskmate.mcp

import java.util.Locale

/**
 * Czy wypowiedź dotyczy domu (sterowanie, czujniki) – wtedy agent dostaje narzędzia z serwerów MCP.
 * Bez tego każde pytanie niosłoby ~26 opisów narzędzi Home Assistant: więcej tokenów, wolniej
 * i szybciej wyczerpany darmowy limit na minutę (logi 7.11.2026).
 */
object HomeIntent {
    private val STEMS = listOf(
        "świat", "lamp", "żarów", "led", "jasn", "ciemn", "włącz", "wyłącz", "zapal", "zgaś", "przyciemn", "rozjaśn",
        "temperatur", "stopni", "ciepł", "zimn", "grzan", "grzej", "ogrzew", "klimat", "wilgotn", "termostat",
        "salon", "kuchni", "sypialn", "łazienk", "pokój", "pokoj", "przedpok", "garaż", "ogród", "balkon", "biur",
        "rolet", "żaluzj", "zasłon", "okn", "drzwi", "brama", "gniazd", "wtyczk", "odkurzacz", "pralk", "zmywark",
        "czujnik", "alarm", "kamer", "telewiz", "głośnik", "muzyk", "scen", "automatyzac", "home assistant", "dom",
        "mieszkan", "prąd", "energi", "zużyci",
    )

    fun matches(text: String): Boolean {
        val t = text.lowercase(Locale.forLanguageTag("pl"))
        return STEMS.any { it in t }
    }
}
