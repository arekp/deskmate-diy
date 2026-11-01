package pl.arekp.deskmate

import android.os.Build

/**
 * Ustawienia zależne od modelu telefonu.
 * Wspierane: Galaxy S23, Galaxy Z Fold8 Ultra, Galaxy Z Flip6 (opis: docs/telefony.md).
 *
 * Kody modeli Samsunga mają postać SM-XNNN + litera regionu (np. SM-S911B).
 * Jeśli Twój telefon nie zostanie rozpoznany, aplikacja użyje profilu GENERIC
 * – dopisz jego Build.MODEL do listy prefixes.
 */
enum class DeviceProfile(
    val label: String,
    val prefixes: List<String>,
    /** Poziome pole widzenia przedniej kamery w orientacji poziomej (stopnie). */
    val cameraHfovDeg: Float,
    val cameraVfovDeg: Float,
    /** Odwrócenie osi, jeśli głowa ucieka od twarzy zamiast do niej. */
    val invertPan: Boolean = false,
    val invertTilt: Boolean = false,
    /** Wielkość oczu względem krótszego boku ekranu. */
    val eyeScale: Float = 1f,
) {
    GALAXY_S23(
        label = "Galaxy S23",
        prefixes = listOf("SM-S911"),
        cameraHfovDeg = 72f,
        cameraVfovDeg = 56f,
    ),
    GALAXY_Z_FOLD8_ULTRA(
        label = "Galaxy Z Fold8 Ultra",
        // Kod modelu do potwierdzenia na telefonie: Ustawienia > Informacje o telefonie.
        prefixes = listOf("SM-F976", "SM-F97"),
        cameraHfovDeg = 80f,
        cameraVfovDeg = 62f,
        eyeScale = 0.85f, // duży, prawie kwadratowy ekran po rozłożeniu
    ),
    GALAXY_Z_FLIP6(
        label = "Galaxy Z Flip6",
        prefixes = listOf("SM-F741"),
        cameraHfovDeg = 76f,
        cameraVfovDeg = 58f,
    ),
    GENERIC(
        label = "Inny telefon",
        prefixes = emptyList(),
        cameraHfovDeg = 70f,
        cameraVfovDeg = 55f,
    );

    companion object {
        fun detect(model: String = Build.MODEL ?: ""): DeviceProfile =
            entries.firstOrNull { p -> p.prefixes.any { model.startsWith(it, ignoreCase = true) } }
                ?: GENERIC
    }
}
