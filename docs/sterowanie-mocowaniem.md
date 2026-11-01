# Sterowanie mocowaniem (głową robota)

Mocowanie to ruchoma „głowa”: płytka z magnetyczną ładowarką Qi2, do której przypinasz telefon, osadzona na dwóch osiach.

```
          telefon (ekran = twarz)
         ┌──────────────────┐
         │   ◉        ◉     │
         └────────┬─────────┘
          krążek Qi2 + płytka
                  │
         ┌────────┴────────┐   ← oś TILT (pochylenie góra/dół) – serwo w jarzmie
         │     jarzmo      │
         └────────┬────────┘
                  │            ← oś PAN (obrót lewo/prawo) – serwo w podstawie
        ══════════╧══════════  łożysko obrotowe (dźwiga ciężar, serwo tylko obraca)
             PODSTAWA (ESP32, zasilanie)
```

## Trzy poziomy sterowania

1. **Telefon decyduje, *gdzie* patrzeć** – śledzi twarz kamerą i wysyła docelowe kąty (`L pan tilt ms`) maksymalnie 10 razy na sekundę. Model językowy może zlecić gest (`G nod`).
2. **ESP32 decyduje, *jak* się poruszyć** – planuje płynny ruch (krzywa easeInOutCubic), kolejkuje gesty i dodaje mikro-ruchy w spoczynku.
3. **Serwa wykonują ruch** – sygnał PWM 50 Hz, 500–2500 µs.

Dzięki temu podziałowi ruch jest płynny nawet przy chwilowych opóźnieniach Bluetooth, a gesty działają też bez telefonu (np. z konsoli `tools/ble_console.py`).

## Śledzenie twarzy (pętla sprzężenia zwrotnego)

Kamera porusza się razem z głową, więc nie liczymy pozycji absolutnej – tylko korygujemy błąd:

```
błąd_pan  = x_twarzy (-1..1) × (poziome pole widzenia kamery / 2)
pan      += błąd_pan × TRACK_GAIN          (domyślnie 0,35)
```

- **Strefa martwa** 2,5° – głowa nie drga przy drobnych ruchach twarzy.
- **Pauza po geście** 1,8 s – śledzenie nie przerywa kiwnięcia głową.
- Pole widzenia kamery bierzemy z profilu telefonu (`DeviceProfile.kt`).
- Jeśli głowa **ucieka** od twarzy zamiast za nią podążać – ustaw `invertPan` / `invertTilt` w profilu telefonu albo `PAN_INVERT` w firmware.
- Jeśli **oscyluje** (przestrzeliwuje w lewo i prawo) – zmniejsz `TRACK_GAIN`; jeśli jest **ospała** – zwiększ.

## Komendy

Pełna specyfikacja: [protokol-ble.md](protokol-ble.md). Najważniejsze:

| Komenda | Działanie |
|---|---|
| `L 20 -5 300` | spójrz 20° w prawo, 5° w dół, ruch trwa 300 ms |
| `G nod` / `G shake` | tak / nie |
| `G happy` `G sad` `G curious` | gesty emocji |
| `G sleep` / `G wake` | zaśnij (głowa opada) / obudź się |
| `H` | wróć do środka |
| `I 0` / `I 1` | wyłącz / włącz mikro-ruchy w spoczynku |

## Kalibracja krok po kroku

1. Zamontuj serwa **bez** telefonu, wgraj firmware, otwórz monitor portu (`pio device monitor`).
2. Wpisz `H`. Odkręć ramię serwa i załóż je tak, żeby płytka patrzyła prosto przed siebie i była pionowa.
3. Dostrój `PAN_CENTER_US` / `TILT_CENTER_US` w `firmware/include/config.h` (kroki po ~10 µs).
4. Sprawdź kierunki: `L 30 0` powinno obrócić głowę w **prawo robota** (Twoje lewo, gdy patrzysz na niego). `L 0 20` – w górę. W razie potrzeby ustaw `*_INVERT`.
5. Przypnij **najcięższy** telefon (Fold8 Ultra rozłożony) i przejdź po krańcach (`L -80 0`, `L 80 0`, `L 0 -25`, `L 0 30`). Zawęź `PAN_MIN/MAX`, `TILT_MIN/MAX`, jeśli coś zahacza albo serwo się męczy.
6. W aplikacji te same granice są w `Config.kt` – utrzymuj je zgodne.

## Dobór serw i obciążenie

Najtrudniej ma oś **TILT**: telefon jest wysunięty przed oś obrotu, więc serwo musi utrzymać moment siły.

| Konfiguracja | Masa na głowie* | Zalecane serwo TILT |
|---|---|---|
| S23 (≈168 g) | ≈ 270 g | MG90S wystarczy, lepiej MG996R / DS3218 |
| Flip6 (≈187 g) | ≈ 290 g | MG996R / DS3218 |
| Fold8 Ultra rozłożony (≈215 g, szeroki) | ≈ 320 g | **MG996R / DS3218 lub mocniejsze** |

\* telefon + etui magnetyczne (~40 g) + krążek Qi2 i płytka (~60 g). Wartości orientacyjne – zważ swój zestaw.

Zasady, które robią największą różnicę:

- **Oś TILT jak najbliżej środka ciężkości telefonu** – wtedy nawet małe serwo daje radę i ruch jest cichszy.
- **Oś PAN na łożysku** (np. łożysko „lazy susan” Ø 60–100 mm) – ciężar trzyma łożysko, serwo tylko obraca.
- Serwa cyfrowe z metalowymi zębatkami są mniej podatne na luzy; dla najcichszej pracy rozważ później silniki gimbalowe z SimpleFOC (patrz roadmapa).
- Serwa zasilaj z osobnej przetwornicy 5–6 V / min. 3 A (MG996R pobiera chwilowo do ~2 A).

## Bezpieczeństwo

- Ruch głowy jest ograniczany programowo w dwóch miejscach (aplikacja i firmware) – nie usuwaj limitów z `config.h`.
- Po rozłączeniu telefonu ESP32 wraca do środka.
- Przy montażu i kalibracji trzymaj palce z dala od jarzma.
