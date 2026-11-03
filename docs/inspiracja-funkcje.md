# Inspiracja: funkcje oryginalnego Loona Deskmate

Ten dokument spisuje funkcje produktu **Loona Deskmate** (KEYi Tech) na podstawie publicznych materiałów: zapowiedzi z CES 2026, stron producenta, kampanii crowdfundingowej i artykułów prasowych. Służy jako lista wymagań – **każdą funkcję implementujemy samodzielnie, od zera**.

> DeskMate DIY to niezależny projekt hobbystyczny, niepowiązany z KEYi Tech. Nie korzystamy z oprogramowania, aplikacji, grafik, dźwięków ani znaków towarowych oryginału i nie wykonujemy inżynierii wstecznej ich produktu. „Loona” i „Deskmate” to nazwy należące do ich właściciela. Opisy poniżej to nasze streszczenia publicznych informacji, a nie dokumentacja techniczna producenta.

Legenda statusu w DeskMate DIY: ✅ jest w szkielecie v0.1 · 🟡 częściowo · ⬜ w planach ([roadmapa](roadmapa.md))

## 1. Koncepcja

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Telefon jest „mózgiem”: jego ekran, kamera, mikrofon, głośnik i procesor zastępują własną elektronikę robota | Aplikacja Android robi całą „inteligencję”, ESP32 tylko porusza głową | ✅ |
| Postawienie telefonu na stacji automatycznie uruchamia aplikację towarzysza | Android: wykrycie ładowania/połączenia BLE i uruchomienie aplikacji (np. tryb kiosku, automatyzacja w Bixby Routines) | ⬜ |
| Wymiana telefonu na nowszy = lepszy robot (szybsze AI, lepsza kamera) bez zmiany sprzętu | To samo – sprzęt robota jest niezależny od telefonu | ✅ |
| Oryginał: iPhone z MagSafe (12 i nowsze); Android zapowiadany na przyszłość | My: Android (S23, Fold8 Ultra, Flip6 + inne) | ✅ |

## 2. Ciało i ekspresja

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Ruchoma, zmotoryzowana „głowa” (3 stopnie swobody, ciche bezszczotkowe serwa) | 2 osie (obrót + pochylenie) na serwach; 3. oś (przechył) opcjonalnie | 🟡 |
| Animowane, duże oczy na ekranie – „osobowość” | Oczy i usta w Compose: 12 emocji, powieki, kolory, dodatki (serca, „Zzz”), usta w rytm mowy | ✅ |
| Płynne, „żywe” ruchy | Wygładzanie (easing), kolejka klatek, mikro-ruchy w spoczynku | ✅ |
| Rozpoznawanie emocji / stanu użytkownika i dopasowanie reakcji | Uśmiech i kierunek patrzenia z ML Kit; analiza nastroju przez model | 🟡 |

## 3. Interakcja

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Rozmowa bez słowa-klucza – robot jednocześnie widzi i słyszy | Słuchanie startuje, gdy twarz patrzy w kamerę | ✅ |
| Percepcja multimodalna (obraz + dźwięk) | Twarz lokalnie; zrzut ekranu / zdjęcie z kamery do modelu na żądanie | 🟡 |
| Świadomość uwagi: milczy, gdy jesteś skupiony, odzywa się, gdy się rozpraszasz | Tryb skupienia: timer + obserwacja (patrzysz w telefon zamiast w monitor, odchodzisz od biurka) | ⬜ |
| Proaktywne podpowiedzi i przypomnienia | Zaplanowane przypomnienia, podsumowania dnia | ⬜ |

## 4. Integracja z komputerem

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Aplikacja towarzysząca na Mac/Windows | `desktop/screen_helper.py` (Python) | 🟡 |
| Świadomość ekranu – robot rozumie, nad czym pracujesz, bez tłumaczenia | Narzędzie `look_at_screen`: zrzut ekranu na wyraźną prośbę | ✅ |
| Synchronizacja schowka | Endpoint schowka w pomocniku + narzędzie dla modelu | ⬜ |
| Widzi tylko to, co mu wskażesz (nie podgląda ekranu w tle) | Zrzut wyłącznie na żądanie, token, brak zapisu na dysk | ✅ |

## 5. Integracje z usługami (asystent pracy)

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Gmail: podsumowania poczty, szkice odpowiedzi | Narzędzie Gmail API (tylko odczyt + szkice; wysyłka zawsze po potwierdzeniu) | ⬜ |
| Kalendarz: przegląd i zmiany wydarzeń | Google Calendar API | ⬜ |
| Slack | Slack API (odczyt wzmianek, szkice) | ⬜ |
| Spotkania Zoom, umawianie przez Calendly | Odpowiednie API / linki | ⬜ |
| Rezerwacje i zamówienia usług | Poza zakresem v1 | ⬜ |
| Dysk Google, Mapy Google | Google Drive API, Maps | ⬜ |
| Streszczanie tekstów, odpisywanie, planowanie – mikro-zadania | Model językowy + powyższe narzędzia | 🟡 |
| „50+ aplikacji” (według kampanii) | Architektura narzędzi `AssistantTool` – każda integracja to jedna klasa | 🟡 |

## 6. Pamięć i prywatność

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Kontrolowana pamięć: podgląd, co robot pamięta, i czyszczenie jednym ruchem | Historia rozmowy w pamięci RAM; trwała pamięć z podglądem i „wyczyść” | 🟡 |
| Hybryda: wrażliwe przetwarzanie na urządzeniu, chmura tylko gdy trzeba | Twarz i rozpoznawanie twarzy lokalnie; mowa i model w chmurze (opcja: lokalny Whisper) | 🟡 |
| Sam robot nie ma kamer, mikrofonów ani pamięci – działa w granicach uprawnień systemu telefonu | To samo: ESP32 nie ma czujników audio/wideo | ✅ |

## 7. Stacja biurkowa (sprzęt)

| Funkcja oryginału | Jak to robimy | Status |
|---|---|---|
| Ładowanie bezprzewodowe Qi2 (do 15 W) z magnetycznym mocowaniem | Krążek Qi2 w głowie + magnetyczne etui ([telefony.md](telefony.md)) | ✅ (projekt) |
| Szybka ładowarka GaN do 165 W | Zewnętrzna ładowarka USB-C PD; opcjonalny hub | 🟡 |
| Hub: 3× USB-C i 1× USB-A | Opcjonalny moduł hubu w podstawie | ⬜ |

## 8. Model biznesowy (dla porządku)

- Według producenta: jednorazowy zakup (MSRP ok. 299 USD), bez abonamentu na podstawowe funkcje; aktualizacje oprogramowania dodają funkcje.
- U nas: koszt części + własny klucz API modelu językowego (płatność za użycie).

## Źródła

- [GameStar – CES 2026: Loona Deskmate](https://www.gamestar.de/artikel/ces-2026-loona-deskmate-ki-assistent,3445639.html)
- [Macotakara – Loona DeskMate](https://www.macotakara.jp/accessories/entry-50239.html)
- [Notebookcheck – Loona DeskMate](https://www.notebookcheck.org/Loona-DeskMate-convierte-el-iPhone-en-un-practico-robot-con-inteligencia-artificial.1199230.0.html)
- [basic-tutorials.de – Loona DeskMate](https://basic-tutorials.de/news/loona-deskmate-apple-iphone-wird-zum-smarten-schreibtisch-roboter/)
- [TechEBlog – Loona Deskmate](https://www.techeblog.com/loona-deskmate-iphone-ai-companion/)
- [PR Times – premiera w Japonii](https://prtimes.jp/main/html/rd/p/000000001.000187084.html)
- [KEYi Robot – przegląd funkcji](https://keyirobot.com/en-us/blogs/news/loona-deskmate-features-how-it-helps-you-work-smarter)
- [KEYi Robot – cena](https://keyirobot.com/blogs/buying-guide/loona-deskmate-price-value-guide)
- [KEYi Robot – strona produktu](https://keyirobot.com/en-bh/products/deskmate)
- [Selfridges – Loona Deskmate](https://www.selfridges.com/JP/en/product/smartech-loona-deskmate_R04690951/)
