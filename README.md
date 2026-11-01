# DeskMate DIY 🤖📱

Biurkowy robot-asystent, którego **mózgiem jest Twój telefon z Androidem**, a ciałem – prosta, wydrukowana w 3D głowa na dwóch serwach z magnetyczną ładowarką. Telefon wyświetla animowane oczy, widzi Cię przez przednią kamerę, słucha, rozmawia (model językowy przez API), a ESP32 obraca głowę tak, żeby zawsze na Ciebie „patrzył”.

> **Inspiracja.** Projekt jest inspirowany produktem **Loona Deskmate** firmy **KEYi Tech** (pokazanym na CES 2026). To niezależny projekt hobbystyczny, **niepowiązany z KEYi Tech**: nie zawiera ich oprogramowania, kodu, grafik ani materiałów, nie powstał przez inżynierię wsteczną ich aplikacji, a funkcje zaprojektowano od zera na podstawie publicznie dostępnych opisów. „Loona” i „Deskmate” to nazwy należące do ich właściciela. Lista funkcji oryginału i ich odpowiedników tutaj: [docs/inspiracja-funkcje.md](docs/inspiracja-funkcje.md).

## Docelowe telefony

| Telefon | Tryb pracy na robocie |
|---|---|
| **Samsung Galaxy S23** | poziomo, oczy na całym ekranie |
| **Samsung Galaxy Z Fold8 Ultra** | rozłożony (duży ekran 8") albo złożony (ekran 6,5") |
| **Samsung Galaxy Z Flip6** | rozłożony poziomo; ładowanie bezprzewodowe tylko po złożeniu |

Szczegóły, wymiary i dobór uchwytu: [docs/telefony.md](docs/telefony.md). Inny telefon z Androidem 8+ i BLE też zadziała (profil „GENERIC”).

## Jak to działa

```
 ┌──────────── TELEFON (mózg) ────────────┐
 │ oczy na ekranie   kamera → ML Kit (twarz)│
 │ mikrofon → mowa na tekst → model AI      │──── Internet ──▶ Messages API
 │ synteza mowy ← odpowiedź + emocje        │
 └───────────────┬─────────────────────────┘
                 │ Bluetooth LE (komendy tekstowe: "L 12 5 180", "G nod")
 ┌───────────────▼─────────────────────────┐
 │ ESP32: płynny ruch, gesty, "oddychanie"  │
 │ serwo PAN (obrót)   serwo TILT (pochył)  │
 │ czujnik dotyku      ładowarka Qi2 (5 V)  │
 └──────────────────────────────────────────┘
```

Więcej: [docs/aplikacja-android.md](docs/aplikacja-android.md) (oprogramowanie telefonu) · [docs/architektura.md](docs/architektura.md) · sterowanie głową: [docs/sterowanie-mocowaniem.md](docs/sterowanie-mocowaniem.md) · protokół: [docs/protokol-ble.md](docs/protokol-ble.md)

## Co już działa w szkielecie (v0.1)

- animowane oczy z mruganiem i emocjami (radość, smutek, ciekawość, zamyślenie, senność, zaskoczenie, słuchanie),
- śledzenie twarzy kamerą (lokalnie, ML Kit) i obracanie głowy za użytkownikiem,
- rozmowa głosowa po polsku **bez słowa-klucza**: patrzysz w robota → słucha,
- **router modeli AI**: Gemini (darmowy) → OpenRouter (darmowe) → Claude (płatny), automatyczne przełączanie przy limitach,
- model językowy z narzędziami: emocje i gesty głową, aktualna data/godzina, „spójrz na mój ekran” (opcjonalny pomocnik na PC),
- gesty serw: kiwanie, kręcenie głową, radość, smutek, ciekawość, sen/pobudka, mikro-ruchy w spoczynku,
- zasypianie, gdy nikogo nie ma, i budzenie po zobaczeniu twarzy,
- głaskanie (czujnik dotyku) → radość,
- automatyczne rozpoznanie modelu telefonu (S23 / Fold8 Ultra / Flip6).

Plan dalszych prac (Gmail, kalendarz, tryb skupienia, ekran okładki Flipa…): [docs/roadmapa.md](docs/roadmapa.md).

## Struktura repozytorium

| Katalog | Zawartość |
|---|---|
| [`firmware/`](firmware/) | ESP32 (PlatformIO, Arduino): BLE, serwa, gesty |
| [`android/`](android/) | aplikacja Android (Kotlin, Jetpack Compose) – „mózg” |
| [`desktop/`](desktop/) | pomocnik ekranu na PC/Mac (Python) |
| [`tools/`](tools/) | konsola BLE do testów robota z komputera |
| [`hardware/`](hardware/) | obudowa, uchwyt, schemat połączeń |
| [`docs/`](docs/) | dokumentacja |

## Instalacja aplikacji na telefonie

Każda zmiana w gałęzi `main` jest automatycznie kompilowana i testowana, a gotowy plik APK trafia do zakładki Releases.

1. Na telefonie otwórz: **https://github.com/arekp/deskmate-diy/releases/latest/download/deskmate-diy.apk**
2. Pobierz plik i otwórz go. Android zapyta o zgodę na instalację z tego źródła (przeglądarki) – zezwól.
3. Uruchom **DeskMate DIY**, nadaj uprawnienia (kamera, mikrofon, urządzenia w pobliżu).
4. **Przytrzymaj palec na ekranie z oczami** → wpisz klucz **Gemini** (darmowy, aistudio.google.com) i/lub **OpenRouter** (darmowe modele) albo płatny klucz **Claude API** → Zapisz. Router sam wybiera dostępny model.

Kolejne wersje instalują się jako aktualizacja (wspólny klucz podpisu testowego). To wersja testowa (debug) – nie do publikacji w sklepie.

## Szybki start

1. **Kup części** – [docs/bom.md](docs/bom.md) (ok. 300–550 zł).
2. **Złóż elektronikę** – [docs/montaz.md](docs/montaz.md).
3. **Wgraj firmware** – `cd firmware && pio run -e esp32-s3 -t upload`, potem przetestuj: `python tools/ble_console.py` → `demo`.
4. **Zainstaluj aplikację** – gotowy APK (sekcja wyżej) albo zbuduj sam: katalog `android/` w Android Studio (opcjonalnie klucz w `local.properties`, wzór: `local.properties.example`).
5. **Postaw telefon na robocie** i popatrz na niego 🙂

## Licencja

MIT – patrz [LICENSE](LICENSE).
