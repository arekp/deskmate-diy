# DeskMate DIY – pamięć projektu

Biurkowy robot-asystent: telefon z Androidem = mózg i twarz, ESP32 + 2 serwa = ruchoma głowa. Inspiracja: Loona Deskmate (KEYi Tech) – projekt niezależny, bez ich kodu i bez inżynierii wstecznej. Dokumentacja i komunikacja z właścicielem po polsku.

## Decyzje (nie zmieniaj bez pytania)

- **Telefony docelowe:** Samsung Galaxy S23, Galaxy Z Fold8 Ultra, Galaxy Z Flip6 (profile w `android/.../DeviceProfile.kt`). Kod modelu Fold8 Ultra (`SM-F976*`) – niepotwierdzony.
- **Ładowarka (kupiona 1.11.2026):** Anker 313 Wireless Charger (Pad) A2503 – Qi 10 W, **bez magnesów**, micro-USB, zasilacz QC 3.0 osobno. Wymiary z dokumentacji: Ø 100 × 11,2 mm, 67 g, gniazdo micro-USB z boku, cewka na środku.
  - ⇒ telefon trzymany **mechanicznie** (kołyska z półką i zaczepami, wkładki pod każdy telefon), etui **bez magnesów/metalu**, realnie ~5 W ładowania dla tych telefonów.
  - Alternatywa magnetyczna, gdyby zmienić decyzję: Samsung EP-P2900 (Qi2 25 W).
- **Platforma aplikacji:** Android (Kotlin + Compose), model językowy przez Messages API; klucz wpisywany w aplikacji (przytrzymanie palca na oczach), nie w repo.
- **Firmware:** ESP32 (PlatformIO, Arduino), protokół tekstowy BLE – `docs/protokol-ble.md`. UUID muszą być zgodne w firmware, aplikacji i `tools/ble_console.py`.

## Budowanie i wydania

- Lokalne środowisko Claude nie ma Android SDK ani PlatformIO – kompilacja i testy działają w GitHub Actions (`.github/workflows/build.yml`).
- Każdy push do `main` publikuje APK: https://github.com/arekp/deskmate-diy/releases/latest/download/deskmate-diy.apk
- Wspólny klucz testowy `android/app/debug.keystore` – dzięki niemu kolejne wersje instalują się jako aktualizacja.

## Mapa dokumentów

`docs/inspiracja-funkcje.md` (funkcje oryginału + status), `docs/telefony.md` (telefony, ładowarka), `docs/sterowanie-mocowaniem.md`, `docs/bom.md`, `docs/montaz.md`, `docs/roadmapa.md`, `hardware/` (CAD, STL).
