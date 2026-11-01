# Oprogramowanie telefonu (aplikacja Android) – jak działa i jak je rozwijać

Aplikacja **DeskMate DIY** zamienia telefon w „mózg” i „twarz” robota. Kod: `android/app/src/main/java/pl/arekp/deskmate/`. Język: Kotlin, interfejs: Jetpack Compose, minimalny Android 8.0 (API 26).

## 1. Z czym się łączy

```
                    ┌──────────────────────────────────────────┐
                    │           TELEFON – DeskMate DIY          │
                    │                                           │
  Kamera przednia ─►│ FaceTracker (ML Kit, lokalnie)            │
  Mikrofon ────────►│ Speech: SpeechRecognizer ─┐               │
  Głośnik ◄─────────│ Speech: TextToSpeech      │               │
  Ekran ◄───────────│ EyesScreen (oczy)         ▼               │
                    │                     DeskMateBrain          │
                    │                        │       │           │
                    └────────────────────────┼───────┼───────────┘
                                             │       │
             Bluetooth LE (GATT, tekst) ◄────┘       └────► HTTPS
                       │                                     │
            ┌──────────▼─────────┐            ┌──────────────▼──────────────┐
            │ ESP32 – ciało       │            │ api.anthropic.com           │
            │ serwa, dotyk        │            │ (Messages API, model AI)    │
            └────────────────────┘            └─────────────────────────────┘
                                             ┌─────────────────────────────┐
              HTTP w sieci lokalnej (opcja) ─►│ PC: desktop/screen_helper.py│
                                             │ zrzut ekranu na żądanie      │
                                             └─────────────────────────────┘
```

| Połączenie | Protokół | Co płynie | Kiedy |
|---|---|---|---|
| **Robot (ESP32)** | Bluetooth LE, usługa `4d7d0001-…` | komendy tekstowe `L`/`G`/`H` → robot; zdarzenia `TOUCH`, `OK` ← robot | stale, gdy robot jest w zasięgu ([protokol-ble.md](protokol-ble.md)) |
| **Model AI** | HTTPS → `https://api.anthropic.com/v1/messages` | tekst rozmowy, definicje narzędzi; opcjonalnie zrzut ekranu | po każdej wypowiedzi użytkownika |
| **Rozpoznawanie mowy** | systemowa usługa Androida (`SpeechRecognizer`) | dźwięk z mikrofonu → tekst; zwykle przetwarzany przez usługę Google/Samsung, często w chmurze | gdy robot „słucha” |
| **Synteza mowy** | systemowy `TextToSpeech` (głos pl-PL) | tekst → dźwięk | gdy robot odpowiada |
| **Pomocnik ekranu PC** (opcja) | HTTP w LAN, nagłówek `X-Token` | JPEG ze zrzutem ekranu | tylko gdy poprosisz „spójrz na mój ekran” |
| **Kamera** | CameraX + ML Kit Face Detection | obraz **nie opuszcza telefonu** – wychodzi tylko pozycja twarzy | stale |

Uprawnienia: `CAMERA`, `RECORD_AUDIO`, `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT` (Android 12+) / `ACCESS_FINE_LOCATION` (Android 8–11), `INTERNET`.

## 2. Moduły

| Moduł (plik) | Odpowiedzialność | Główne zależności |
|---|---|---|
| `MainActivity.kt` | start, pełny ekran, ekran nie gaśnie, prośba o uprawnienia, przytrzymanie palca → ustawienia | Compose, `DeskMateBrain` |
| `DeskMateBrain.kt` | **orkiestrator zachowań** – łączy wszystkie moduły, prowadzi rozmowę, sen/pobudka, emocje, gesty | wszystkie poniższe |
| `Settings.kt` | klucz API, model, adres i token pomocnika ekranu (SharedPreferences, z zapasem z `local.properties`) | – |
| `Config.kt` | stałe: UUID BLE, limity ruchu, czułość śledzenia, czasy, prompt systemowy | – |
| `DeviceProfile.kt` | profile telefonów: S23, Fold8 Ultra, Flip6, GENERIC (pole widzenia kamery, odwrócenie osi, wielkość oczu) | `Build.MODEL` |
| `ble/RobotLink.kt` | skanowanie po UUID, połączenie GATT, kolejka zapisów (jeden naraz), powiadomienia, auto-reconnect co 3 s | Android Bluetooth LE |
| `vision/FaceTracker.kt` | kamera przednia → ML Kit → `FaceObservation` (pozycja −1…1, rozmiar, uśmiech, czy patrzy w kamerę) | CameraX, ML Kit |
| `vision/HeadController.kt` | pętla śledzenia: odchyłka twarzy w stopniach × wzmocnienie → komenda `L pan tilt 180`, max 10/s, strefa martwa, pauza po geście | `DeviceProfile`, `Config` |
| `voice/Speech.kt` | `listenOnce()` – jedna wypowiedź → tekst; `say()` – mówi i czeka na koniec | SpeechRecognizer, TextToSpeech |
| `ai/ClaudeClient.kt` | zapytanie do modelu, **pętla narzędzi** (do 4 rund), historia rozmowy (20 wiadomości), wycofanie tury po błędzie | OkHttp, org.json, `Settings` |
| `ai/AssistantTool.kt` | interfejs narzędzia + pomocniki do schematów JSON | – |
| `ai/Tools.kt` | narzędzia: `express` (emocja + gest), `get_current_time`, `look_at_screen` | `Settings`, OkHttp |
| `ui/EyesScreen.kt` | rysowanie oczu na Canvas: mruganie, kierunek spojrzenia, 8 emocji, kropka „słucham”, napisy | Compose |
| `ui/Emotion.kt` | lista emocji | – |
| `ui/SettingsDialog.kt` | okno ustawień | Compose Material 3 |

## 3. Jak działa – przepływ

### 3.1 Start
1. `MainActivity` prosi o uprawnienia → `DeskMateBrain.start()`.
2. Uruchamiają się: synteza i rozpoznawanie mowy, skanowanie BLE, kamera.
3. Po połączeniu z robotem aplikacja wysyła `G wake` (robot „budzi się”).

### 3.2 Pętle działające równolegle (korutyny)

```
faces.collect      ─► onFace(): śledzenie głową + kierunek oczu + wybudzenie
robot.events       ─► "TOUCH" → radość
robot.state        ─► napis statusu (profil telefonu · stan robota)
conversationLoop() ─► rozmowa (niżej)
sleepWatcher()     ─► 60 s bez twarzy → G sleep + oczy SLEEPY
```

### 3.3 Rozmowa (bez słowa-klucza)

```
 [czeka] ──twarz patrzy w kamerę > 0,7 s──► [SŁUCHA] (oczy LISTENING, czerwona kropka)
                                               │
                         cisza ◄───────────────┤──────────► tekst
                  (przerwa rośnie 1→10 s)       │
                                               ▼
                                         [MYŚLI] (oczy THINKING)
                                               │ ClaudeClient.ask()
                                               │   model ⇄ narzędzia (express, czas, ekran)
                                               ▼
                                         [MÓWI] TTS + napis + emocje/gesty
                                               │
                                               └──► wraca do [czeka]
```

Model sam decyduje o mimice: wywołuje narzędzie `express`, np. `{"emotion":"happy","gesture":"nod"}`. Aplikacja zmienia wtedy oczy i wysyła do robota `G nod` (śledzenie twarzy wstrzymuje się na 1,8 s, żeby nie przerwać gestu).

### 3.4 Śledzenie twarzy

Kamera jest w telefonie i obraca się razem z głową, więc to pętla sprzężenia zwrotnego. Szczegóły i strojenie: [sterowanie-mocowaniem.md](sterowanie-mocowaniem.md).

## 4. Jak rozwijać

### 4.1 Nowa integracja (np. Gmail, Kalendarz, Home Assistant)

Każda umiejętność to jedna klasa `AssistantTool`:

```kotlin
class WeatherTool : AssistantTool {
    override val name = "get_weather"
    override val description = "Zwraca prognozę pogody dla podanego miasta."
    override val inputSchema = objectSchema(
        "city" to JSONObject().put("type", "string").put("description", "Nazwa miasta"),
        required = listOf("city"),
    )

    override suspend fun run(input: JSONObject): ToolResult {
        val city = input.getString("city")
        // ... zapytanie HTTP (Dispatchers.IO) ...
        return ToolResult.text("W mieście $city: 12°C, pochmurno")
    }
}
```

Potem dopisz ją w `DeskMateBrain.buildTools()`. Model sam zdecyduje, kiedy jej użyć. Zasady:
- Akcje nieodwracalne (wysłanie maila, usunięcie wydarzenia) – najpierw szkic i potwierdzenie głosem.
- Klucze i tokeny trzymaj w `Settings`, nie w kodzie.
- Długie operacje uruchamiaj na `Dispatchers.IO`.

### 4.2 Nowy gest
1. Firmware: nowa gałąź w `Motion::playGesture()` (`firmware/src/Motion.cpp`).
2. Aplikacja: dopisz nazwę w `ExpressTool` (`ai/Tools.kt`) – test `ToolsTest` pilnuje, żeby lista zgadzała się z firmware (zaktualizuj w nim zbiór gestów).

### 4.3 Nowa emocja / wygląd oczu
1. Dodaj wartość w `ui/Emotion.kt`.
2. Narysuj ją w `drawEyes()` w `ui/EyesScreen.kt` (kształt, otwarcie, wymuszony kierunek spojrzenia).
3. Dopisz ją do enum w `ExpressTool`.

### 4.4 Nowy telefon
Dodaj wpis w `DeviceProfile.kt` (prefiks `Build.MODEL`, pole widzenia kamery) i test w `DeviceProfileTest.kt`.

### 4.5 Zmiana zachowania
Większość „charakteru” jest w dwóch miejscach:
- `Config.SYSTEM_PROMPT` – osobowość, długość odpowiedzi, kiedy gestykulować,
- `Config` – czasy (`ENGAGE_AFTER_MS`, `SLEEP_AFTER_MS`), czułość śledzenia (`TRACK_GAIN`, `TRACK_DEADZONE_DEG`).

## 5. Budowanie, testy, wydania

| Co | Jak |
|---|---|
| Lokalnie | otwórz `android/` w Android Studio, uruchom na telefonie (debugowanie USB) |
| Testy jednostkowe | `gradle -p android testDebugUnitTest` – `HeadControllerTest`, `DeviceProfileTest`, `ToolsTest` |
| CI | każdy push: testy + APK ([build.yml](../.github/workflows/build.yml)) |
| Instalacja | push do `main` → APK w Releases: https://github.com/arekp/deskmate-diy/releases/latest/download/deskmate-diy.apk |
| Test robota bez telefonu | `python tools/ble_console.py` → `demo`, `track` |

## 6. Znane ograniczenia i kolejne kroki

| Ograniczenie | Pomysł na rozwiązanie |
|---|---|
| Słuchanie startuje po spojrzeniu w kamerę, systemowy rozpoznawacz „pika” | wykrywanie głosu (VAD, np. Silero) + ciągły strumień audio |
| Nie da się przerwać robota w trakcie mówienia | barge-in: nasłuch podczas TTS + `speech.stop()` |
| Historia rozmowy tylko w pamięci RAM | trwała pamięć (Room/DataStore) z podglądem i „zapomnij” |
| Fold8 Ultra rozłożony – możliwy wybór złej kamery przedniej | wybór kamery po `CameraCharacteristics` i stanie zawiasu (Jetpack WindowManager) |
| Aplikacja nie startuje sama po postawieniu na robocie | usługa pierwszoplanowa + start po połączeniu BLE / ładowaniu |
| Rozpoznawanie mowy zależy od usługi systemowej (chmura) | lokalny Whisper (whisper.cpp) dla prywatności i trybu offline |
| Ładowarka Anker 313 daje ~5 W | monitorować baterię (`BatteryManager`) i usypiać ekran/kamerę przy niskim poziomie |

Pełna lista: [roadmapa.md](roadmapa.md).
