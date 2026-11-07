# DeskMate DIY – pamięć projektu

Biurkowy robot-asystent: telefon z Androidem = mózg i twarz, ESP32 + 2 serwa = ruchoma głowa. Inspiracja: Loona Deskmate (KEYi Tech) – projekt niezależny, bez ich kodu i bez inżynierii wstecznej. Dokumentacja i komunikacja z właścicielem po polsku.

## Decyzje (nie zmieniaj bez pytania)

- **Telefony docelowe:** Samsung Galaxy S23, Galaxy Z Fold8 Ultra, Galaxy Z Flip6 (profile w `android/.../DeviceProfile.kt`). Kod modelu Fold8 Ultra (`SM-F976*`) – niepotwierdzony.
  - **Telefon właściciela do testów (potwierdzone z logów 3.11.2026): Galaxy S23 Ultra, SM-S918B, Android 17 / API 37** – to jest „S23” właściciela. Aplikacja ma profil `GALAXY_S23_ULTRA`. **Do zrobienia:** profil S23 Ultra w CAD (`hardware/cad/params.scad`) – wymiary ok. 163,4 × 78,1 × 8,9 mm, 234 g (sprawdzić w docs/telefony.md i zmierzyć w etui); obecny kask `s23` jest pod zwykłego S23 i Ultra się w nim nie zmieści.
- **Ładowarka (kupiona 1.11.2026):** Anker 313 Wireless Charger (Pad) A2503 – Qi 10 W, **bez magnesów**, micro-USB, zasilacz QC 3.0 osobno. Wymiary z dokumentacji: Ø 100 × 11,2 mm, 67 g, gniazdo micro-USB z boku, cewka na środku.
  - ⇒ telefon trzymany **magnetycznie** (decyzja właściciela 2.11.2026): pierścień magnesów neodymowych w przedniej ściance kasku wokół cewki + krótka półka u dołu jako podparcie; na telefonie etui/pierścień magnetyczny typu MagSafe. Realnie ~5 W ładowania. Ryzyko: Anker ostrzega, że metal/magnesy mogą blokować ładowanie – do sprawdzenia przed drukiem; plan B: EP-P2900.
  - Alternatywa magnetyczna, gdyby zmienić decyzję: Samsung EP-P2900 (Qi2 25 W).
- **Platforma aplikacji:** Android (Kotlin + Compose). Klucze wpisywane w aplikacji (przytrzymanie palca na oczach), nie w repo.
- **Modele AI – router** (`ai/llm/LlmRouter.kt`): tryb domyślny AUTO = Gemini (darmowy pakiet właściciela) → OpenRouter (darmowe modele) → Claude API (płatne; Claude Pro właściciela NIE obejmuje API). Automatyczne przełączanie przy 429/402/401/5xx.
- **Firmware:** ESP32 (PlatformIO, Arduino), protokół tekstowy BLE – `docs/protokol-ble.md`. UUID muszą być zgodne w firmware, aplikacji i `tools/ble_console.py`.

## Budowanie i wydania

- Lokalne środowisko Claude nie ma Android SDK ani PlatformIO – kompilacja i testy działają w GitHub Actions (`.github/workflows/build.yml`).
- Każdy push do `main` publikuje APK: https://github.com/arekp/deskmate-diy/releases/latest/download/deskmate-diy.apk (wersja R8, ~14 MB; zapas: `deskmate-diy-debug.apk`, ~25 MB).
- Rozmiar APK: tylko `arm64-v8a`, ML Kit twarze/etykiety/OCR z Usług Google Play (`play-services-mlkit-*`), poza wbudowana (~11 MB z 14 MB). Nie dodawaj innych ABI ani wbudowanych modeli bez potrzeby (było 221 MB).
- Wspólny klucz testowy `android/app/debug.keystore` – dzięki niemu kolejne wersje instalują się jako aktualizacja.

## Mapa dokumentów

`docs/aplikacja-android.md` (moduły aplikacji, połączenia, jak rozwijać), `docs/inspiracja-funkcje.md` (funkcje oryginału + status), `docs/telefony.md` (telefony, ładowarka), `docs/sterowanie-mocowaniem.md`, `docs/bom.md`, `docs/montaz.md`, `docs/roadmapa.md`, `hardware/` (CAD OpenSCAD w `hardware/cad/`, gotowe STL w `hardware/stl/`, instrukcja druku `hardware/cad/README.md`; regeneracja: `hardware/tools/build.sh` – wymaga OpenSCAD).
