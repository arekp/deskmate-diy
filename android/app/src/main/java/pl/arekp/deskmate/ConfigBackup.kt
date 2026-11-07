package pl.arekp.deskmate

import org.json.JSONObject

/**
 * Kopia całej konfiguracji w pliku JSON (ustawienia → Kopia konfiguracji).
 * Pozwala przenieść ustawienia na inny telefon albo odtworzyć je po odinstalowaniu aplikacji.
 * Zwykła aktualizacja aplikacji niczego nie usuwa – kopia jest na wypadek reinstalacji.
 *
 * Plik zawiera klucze API i tokeny – trzymaj go w bezpiecznym miejscu.
 */
object ConfigBackup {
    const val FORMAT = "deskmate-config"
    const val FORMAT_VERSION = 1

    fun toJson(values: Map<String, Any?>, appVersion: String, exportedAt: String): String {
        val settings = JSONObject()
        val types = JSONObject()
        values.toSortedMap().forEach { (k, v) ->
            if (v == null) return@forEach
            settings.put(k, v)
            types.put(k, when (v) { is Boolean -> "b"; is Int -> "i"; is Long -> "l"; is Float -> "f"; else -> "s" })
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("formatVersion", FORMAT_VERSION)
            .put("appVersion", appVersion)
            .put("exportedAt", exportedAt)
            .put("settings", settings)
            .put("types", types)
            .toString(2)
    }

    /** Odczyt kopii; rzuca IllegalArgumentException, gdy to nie jest plik konfiguracji DeskMate. */
    fun parse(json: String): Map<String, Any?> {
        val root = runCatching { JSONObject(json) }.getOrElse { throw IllegalArgumentException("To nie jest plik JSON") }
        require(root.optString("format") == FORMAT) { "To nie jest kopia konfiguracji DeskMate" }
        require(root.optInt("formatVersion", 0) <= FORMAT_VERSION) { "Kopia z nowszej wersji aplikacji – zaktualizuj aplikację" }
        val s = root.optJSONObject("settings") ?: JSONObject()
        val types = root.optJSONObject("types") ?: JSONObject()
        return s.keys().asSequence().associateWith { k ->
            when (types.optString(k)) {
                "b" -> s.optBoolean(k)
                "i" -> s.optInt(k)
                "l" -> s.optLong(k)
                "f" -> s.optDouble(k).toFloat()
                else -> s.opt(k)?.toString()
            }
        }
    }
}
