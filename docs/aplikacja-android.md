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
            │ ESP32 – ciało       │            │ LlmRouter – wybiera model:  │
            │ serwa, dotyk        │            │  Gemini API (darmowy)       │
            └────────────────────┘            │  OpenRouter (darmowe modele)│
                                             │  Claude API (płatny)        │
                                             └─────────────────────────────┘
                                             ┌─────────────────────────────┐
              HTTP w sieci lokalnej (opcja) ─►│ PC: desktop/screen_helper.py│
                                             │ zrzut ekranu na żądanie      │
                                             └─────────────────────────────┘
```

| Połączenie | Protokół | Co płynie | Kiedy |
|---|---|---|---|
| **Robot (ESP32)** | Bluetooth LE, usługa `4d7d0001-…` | komendy tekstowe `L`/`G`/`H` → robot; zdarzenia `TOUCH`, `OK` ← robot | stale, gdy robot jest w zasięgu ([protokol-ble.md](protokol-ble.md)) |
| **Modele AI (przez router)** | HTTPS → Gemini API (`generativelanguage.googleapis.com`), OpenRouter (`openrouter.ai`), Claude (`api.anthropic.com`) | tekst rozmowy, definicje narzędzi; opcjonalnie zrzut ekranu | po każdej wypowiedzi użytkownika |
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
| `Settings.kt` | tryb routera, klucze i modele Gemini / OpenRouter / Claude, pomocnik ekranu (SharedPreferences, z zapasem z `local.properties`) | – |
| `Config.kt` | stałe: UUID BLE, limity ruchu, czułość śledzenia, czasy, prompt systemowy | – |
| `DeviceProfile.kt` | profile telefonów: S23, Fold8 Ultra, Flip6, GENERIC (pole widzenia kamery, odwrócenie osi, wielkość oczu) | `Build.MODEL` |
| `ble/RobotLink.kt` | skanowanie po UUID, połączenie GATT, kolejka zapisów (jeden naraz), powiadomienia, auto-reconnect co 3 s | Android Bluetooth LE |
| `vision/FaceTracker.kt` | kamera przednia → ML Kit (lokalnie): `FaceObservation` (pozycja −1…1, rozmiar, uśmiech, czy patrzy w kamerę) w każdej klatce, `SceneObservation` (etykiety otoczenia co 1,5 s, poza co 150 ms), `waves` (machanie), `readText()` (OCR na żądanie) | CameraX, ML Kit |
| `vision/WaveDetector.kt` | machanie i podniesiona ręka z punktów pozy (nadgarstek nad barkiem + ≥ 3 zmiany kierunku w 1,8 s) | – |
| `vision/LabelsPl.kt` | polskie nazwy częstych etykiet ML Kit | – |
| `ai/WeatherTool.kt` | `get_weather`: pogoda i prognoza z Open-Meteo (bez klucza), miasto domyślne z ustawień | OkHttp |
| `ai/WebSearchTool.kt` | `web_search`: Google przez Gemini (grounding, klucz Gemini), bez klucza / przy błędzie – Wikipedia | OkHttp |
| `ai/LookTool.kt` | narzędzie `look_around` dla modelu: opis kadru tekstem (twarz, etykiety, ręka, OCR) – obraz nie wychodzi z telefonu | – |
| `vision/HeadController.kt` | pętla śledzenia: odchyłka twarzy w stopniach × wzmocnienie → komenda `L pan tilt 180`, max 10/s, strefa martwa, pauza po geście | `DeviceProfile`, `Config` |
| `voice/Speech.kt` | `listenOnce()` – jedna wypowiedź → tekst; `say()` – mówi i czeka na koniec | SpeechRecognizer, TextToSpeech |
| `ai/Assistant.kt` | rozmowa: historia (20 wiadomości) we wspólnym formacie, **pętla narzędzi** (do 4 rund), wycofanie tury po błędzie | `LlmRouter` |
| `ai/llm/LlmRouter.kt` | **router modeli**: kolejność wg trybu, automatyczne przełączanie przy limitach i błędach, „ławka kar” | dostawcy |
| `ai/llm/GeminiProvider.kt`, `OpenRouterProvider.kt`, `ClaudeProvider.kt` | tłumaczenie wspólnego formatu na API danego dostawcy i z powrotem | OkHttp, org.json |
| `ai/llm/Model.kt` | wspólny format: wiadomości, tekst, obrazy, wywołania i wyniki narzędzi, klasy błędów | – |
| `ai/AssistantTool.kt` | interfejs narzędzia + pomocniki do schematów JSON | – |
| `ai/Tools.kt` | narzędzie `look_at_screen` (oraz `ExpressTool`/`TimeTool` – zostawione, ale nieużywane: mina i czas idą bez dodatkowej rundy) | `Settings`, OkHttp |
| `ai/ExpressionTag.kt` | odczyt miny i gestu ze znacznika na początku odpowiedzi, np. `[happy nod]` | – |
| `voice/ListenPolicy.kt` | kiedy słuchać: dotyk, przerwanie, jedno dopytanie, głos przy spojrzeniu | – |
| `voice/BargeInMic.kt`, `voice/BargeInVad.kt` | lekki nasłuch głośności: przerywanie robota i wykrycie, że zaczynasz mówić | – |
| `ui/EyesScreen.kt` | rysowanie twarzy na Canvas: oczy (powieki, asymetria, blask, kolor), usta poruszające się w rytm sylab mówionych przez TTS, dodatki (rumieńce, serca, „Zzz”, kropki myślenia, „?”, iskierki), mruganie, sakkady, kropka „słucham”, napisy | Compose |
| `ui/FaceStyle.kt` | parametry twarzy dla każdej emocji (same liczby – łatwo stroić i testować) | – |
| `ui/Emotion.kt` | 12 emocji: neutral, happy, sad, curious, thinking, sleepy, surprised, listening, excited, love, confused, angry | – |
| `ui/Ticker.kt` | **pasek informacji** na dole ekranu (styl wiadomości TV): czynność, co zrozumiał, model, tryb, zdarzenia | – |
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
 [czeka] – oczy śledzą twarz; gdy patrzysz na robota, mikrofon mierzy TYLKO głośność (BargeInMic)
    │  start słuchania (ListenPolicy) – jeden z sygnałów:
    │   • zaczynasz mówić, patrząc na robota     • dotknięcie ekranu
    │   • przerwałeś robota                      • RAZ zaraz po odpowiedzi (dopytanie, 6 s)
    ▼
 [SŁUCHA] – oczy LISTENING, słowa pojawiają się na ekranie na bieżąco, limit 15 s
    │ cisza → wraca do [czeka] i NIE słucha ponownie bez nowego sygnału
    ▼ koniec wypowiedzi → od razu oczy THINKING
 polecenia lokalne (bez AI, od razu): stop / cisza · tryb debug · która godzina · jaki dziś dzień
    ▼ inne pytania
 [MYŚLI] Assistant.ask() → LlmRouter – zwykle JEDNO zapytanie:
    │   prompt zawiera datę i godzinę; model zaczyna odpowiedź od znacznika „[mina gest]”
    │   narzędzia tylko gdy trzeba: look_around (kamera), look_at_screen (PC)
    ▼
 [MÓWI] TTS + usta + mina/gest ze znacznika; da się przerwać (dotyk, ręka, głos)
```

Mina i gest: model pisze np. `[happy nod] Jasne!`. `Expression.parse()` usuwa znacznik z tekstu do czytania, aplikacja zmienia oczy i wysyła do robota `G nod` (śledzenie twarzy wstrzymuje się na 1,8 s, żeby nie przerwać gestu). Wcześniej służyło do tego narzędzie `express`, co dawało drugie zapytanie do AI przy prawie każdej odpowiedzi.

**Szybkość odpowiedzi (router):** Gemini z niskim poziomem myślenia (`thinkingLevel: low`, dla 2.5 `thinkingBudget: 0`; jeśli model odrzuci ustawienie, ponowienie bez niego), limit 15 s na zapytanie z prawdziwym anulowaniem, a gdy model nie odpowie w 5 s, router pyta równolegle następny **darmowy** model i bierze pierwszą odpowiedź (płatnego Claude nie uruchamia „na zapas”).

### 3.4 Śledzenie twarzy

Kamera jest w telefonie i obraca się razem z głową, więc to pętla sprzężenia zwrotnego. Szczegóły i strojenie: [sterowanie-mocowaniem.md](sterowanie-mocowaniem.md).

### 3.4a Przerywanie robota

Nie trzeba czekać, aż robot skończy mówić. Przerywasz go:
- **dotknięciem ekranu** (krótkie; przytrzymanie dalej otwiera ustawienia) – działa zawsze, także żeby zacząć mówić bez patrzenia w kamerę,
- **podniesieniem ręki albo machaniem** (poza z ML Kit),
- **głosem** (ustawienia → Przerywanie robota, domyślnie włączone, eksperymentalne): podczas mówienia `voice/BargeInMic.kt` nasłuchuje mikrofonu (źródło VOICE_COMMUNICATION z systemowym wycinaniem echa), a `voice/BargeInVad.kt` uznaje mowę, gdy poziom jest ≥ 9 dB nad echem głosu robota przez większość 300 ms. Dźwięk nie jest zapisywany – liczy się tylko głośność.

Po przerwaniu robot milknie i od razu słucha; model dostaje dopisek „(Przerwałem Ci poprzednią odpowiedź.)”. Słowa „stop / cisza / przestań / wystarczy” tylko uciszają robota, bez pytania modelu. W trybie debug pasek pokazuje „Przerywanie: mikrofon … dB, próg … dB, AEC tak/nie” – po tym widać, czy echo nie jest za głośne. Pierwsze sylaby przerywającego zdania mogą umknąć (mikrofon przełącza się z nasłuchu na rozpoznawanie ~0,3 s), więc najlepiej zacząć od „poczekaj…” albo powtórzyć pytanie.

### 3.5 Pasek informacji (na dole ekranu)

Jak w wiadomościach TV: czerwony blok z bieżącą czynnością – **CZEKAM / SŁUCHAM / MYŚLĘ / MÓWIĘ / ŚPIĘ** – a obok przesuwający się tekst:

`ROZUMIEM: „…”  ◆  MODEL: Gemini (gemini-flash-latest)  ◆  TRYB: Automatycznie  ◆  ROBOT: POŁĄCZONY  ◆  20:41 Gest: kiwam głową  ◆  20:40 Gemini: limit zapytań – próbuję dalej`

Zdarzenia (5 ostatnich, z godziną): zmiana modelu, przełączenie routera i powód (limit, zły klucz, awaria), gesty, połączenie i utrata połączenia z robotem, zaśnięcie, pobudka, dotyk. Nowe zdarzenie dodajesz wywołaniem `event("…")` w `DeskMateBrain`.

**Tryb debug** (ustawienia → Diagnostyka albo głosowo „tryb debug” / „wyłącz debug”, obsługiwane lokalnie bez modelu AI) dodaje do paska blok informacji technicznych:

`DEBUG ▸ Wzrok: ML Kit, wykrywanie twarzy na urządzeniu · STT: Google (domyślne) · TTS: Samsung TTS (domyślny) · głos pl-pl-x-… · lokalnie · STT czas: 3120 ms (rozpoznanie 410 ms) · LLM czas: 1840 ms · TTS czas: start 120 ms, mówienie 3.4 s`

Nową informację dodajesz wywołaniem `tech("KLUCZ", "wartość")` (pusta wartość usuwa). Usługę rozpoznawania mowy (domyślna, „na urządzeniu (offline)” od Androida 12 albo konkretna zainstalowana) i silnik TTS wybierasz w ustawieniach; głos zmienia się w ustawieniach telefonu.

### 3.6 Logi diagnostyczne

`DiagLog.kt` zapisuje dziennik do `files/logs/deskmate.log` (rotacja po 1 MB): każde zapytanie do modelu (dostawca, czas, wynik albo pełny błąd serwera), wywołania narzędzi z wynikiem, słuchanie (powód i co usłyszał), odpowiedzi, zdarzenia z paska, błędy BLE/kamery/mikrofonu i **awarie aplikacji ze śladem stosu**. Klucze API są wycinane (znane wartości z ustawień + wzorce `AIza…`, `sk-or-…`, `sk-ant-…`, `Bearer …`).

Ustawienia → **Logi diagnostyczne**: „Udostępnij logi” (systemowe Udostępnij – np. do aplikacji Claude), „Zapisz w Pobranych” (plik `deskmate-log-…txt` do dołączenia w rozmowie), „Wyczyść”. Raport ma nagłówek: wersja i numer buildu z CI, model telefonu, Android, profil, tryb routera, modele i ustawienia (bez kluczy). Nowy wpis: `DiagLog.i/w/e("Tag", "…")`.

## 4. Router modeli AI

Aplikacja nie jest przywiązana do jednego modelu. `LlmRouter` wybiera dostawcę według trybu z ustawień i sam przełącza się na następnego, gdy coś pójdzie nie tak.

| Tryb | Kolejność prób |
|---|---|
| **Automatycznie** (domyślny) | Gemini (darmowy) → OpenRouter (darmowe) → Claude (płatny, awaryjnie) |
| Najlepsza jakość | Claude → Gemini → OpenRouter |
| Tylko darmowe | Gemini → OpenRouter (nigdy płatny) |
| Tylko Gemini / OpenRouter / Claude | wyłącznie wybrany |

Dostawcy bez klucza są pomijani. Po błędzie dostawca trafia na „ławkę kar”:

| Błąd | Przerwa |
|---|---|
| 429 – limit zapytań | czas z `Retry-After` albo 60 s |
| 402 – brak środków / pakietu | 1 h |
| 401/403 – zły klucz | 30 min |
| 5xx, brak sieci | 30 s |
| 400 – odrzucone zapytanie | bez przerwy, od razu następny |

Historia rozmowy jest w formacie wspólnym (`ai/llm/Model.kt`), więc zmiana modelu w środku rozmowy nie gubi kontekstu ani wyników narzędzi. Aktualnie używany model widać w napisie statusu w lewym górnym rogu.

**Klucze** (wpisuje się je w aplikacji – przytrzymaj palec na oczach):
- **Gemini** – darmowy pakiet z [Google AI Studio](https://aistudio.google.com/apikey); domyślny model `gemini-flash-latest`. W darmowym pakiecie Google może używać danych do ulepszania produktów.
- **OpenRouter** – [openrouter.ai/keys](https://openrouter.ai/keys); domyślnie `openrouter/free` (OpenRouter sam wybiera dostępny darmowy model). Darmowe modele: ok. 20 zapytań/min i 50/dzień (1000/dzień po jednorazowym doładowaniu 10 USD). Gdy wybrany model nie obsługuje narzędzi, aplikacja rozmawia z nim bez gestów.
- **Claude** – płatne API z [console.anthropic.com](https://console.anthropic.com/). Subskrypcja Claude Pro **nie** obejmuje API.

**Nowy dostawca** (np. Gemini Nano offline na Fold8 Ultra, lokalny model, Mistral): klasa implementująca `LlmProvider` (`buildBody` / `parse` dla formatu API) + wpis na liście w `DeskMateBrain` + test w `ProviderFormatTest`.

## 5. Jak rozwijać

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
2. Aplikacja: dopisz nazwę do `GESTURES` w `ai/ExpressionTag.kt` i do listy gestów w promptcie (`Config.SYSTEM_PROMPT`).

### 4.3 Nowa emocja / wygląd oczu
1. Dodaj wartość w `ui/Emotion.kt`.
2. Opisz jej wygląd w `FaceStyle.of()` w `ui/FaceStyle.kt` (otwarcie i nachylenie powiek, uśmiech oczu, usta, kolor, dodatek). Rysowanie w `EyesScreen.kt` korzysta z tych liczb, więc zwykle nic więcej nie trzeba.
3. Dopisz ją do listy min w `Config.SYSTEM_PROMPT` (parser znacznika zna wszystkie emocje z `Emotion.forModel`).

### 4.4 Nowy telefon
Dodaj wpis w `DeviceProfile.kt` (prefiks `Build.MODEL`, pole widzenia kamery) i test w `DeviceProfileTest.kt`.

### 4.5 Zmiana zachowania
Większość „charakteru” jest w dwóch miejscach:
- `Config.SYSTEM_PROMPT` – osobowość, długość odpowiedzi, kiedy gestykulować,
- `Config` – czasy (`ENGAGE_AFTER_MS`, `SLEEP_AFTER_MS`), czułość śledzenia (`TRACK_GAIN`, `TRACK_DEADZONE_DEG`).

## 6. Budowanie, testy, wydania

| Co | Jak |
|---|---|
| Lokalnie | otwórz `android/` w Android Studio, uruchom na telefonie (debugowanie USB) |
| Testy jednostkowe | `gradle -p android testDebugUnitTest` – `HeadControllerTest`, `DeviceProfileTest`, `ToolsTest`, `LlmRouterTest`, `ProviderFormatTest` |
| CI | każdy push: testy + APK ([build.yml](../.github/workflows/build.yml)) |
| Instalacja | push do `main` → APK w Releases: https://github.com/arekp/deskmate-diy/releases/latest/download/deskmate-diy.apk |
| Test robota bez telefonu | `python tools/ble_console.py` → `demo`, `track` |

## 7. Znane ograniczenia i kolejne kroki

| Ograniczenie | Pomysł na rozwiązanie |
|---|---|
| Słuchanie startuje po spojrzeniu w kamerę, systemowy rozpoznawacz „pika” | wykrywanie głosu (VAD, np. Silero) + ciągły strumień audio |
| Nie da się przerwać robota w trakcie mówienia | barge-in: nasłuch podczas TTS + `speech.stop()` |
| Historia rozmowy tylko w pamięci RAM | trwała pamięć (Room/DataStore) z podglądem i „zapomnij” |
| Fold8 Ultra rozłożony – możliwy wybór złej kamery przedniej | wybór kamery po `CameraCharacteristics` i stanie zawiasu (Jetpack WindowManager) |
| Aplikacja nie startuje sama po postawieniu na robocie | usługa pierwszoplanowa + start po połączeniu BLE / ładowaniu |
| Rozpoznawanie mowy zależy od usługi systemowej (chmura) | lokalny Whisper (whisper.cpp) dla prywatności i trybu offline |
| Rozmowa tekstowa przez rozpoznawanie mowy systemu | tryb głos-głos przez Gemini Live API (WebSocket, przerywanie, darmowy pakiet) |
| Brak trybu offline | dostawca Gemini Nano (ML Kit GenAI) – Fold8 Ultra tak, S23 nie |
| Ładowarka Anker 313 daje ~5 W | monitorować baterię (`BatteryManager`) i usypiać ekran/kamerę przy niskim poziomie |

Pełna lista: [roadmapa.md](roadmapa.md).
