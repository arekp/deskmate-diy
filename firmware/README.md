# Firmware – „ciało” robota (ESP32)

ESP32 odbiera tekstowe komendy przez Bluetooth LE (lub port szeregowy) i płynnie porusza dwoma serwami: **pan** (obrót lewo/prawo) i **tilt** (pochylenie góra/dół).

## Funkcje

- płynny ruch z wygładzaniem (easeInOutCubic) i kolejką klatek kluczowych,
- gesty: `nod`, `shake`, `happy`, `sad`, `curious`, `sleep`, `wake`,
- „oddychanie” w spoczynku – drobne losowe ruchy, gdy nikt nie wydaje komend,
- opcjonalny czujnik dotyku TTP223 – głaskanie wywołuje gest `happy` i zdarzenie `TOUCH`,
- po rozłączeniu telefonu głowa wraca do środka.

## Budowanie i wgrywanie

Wymagany [PlatformIO](https://platformio.org/) (CLI lub wtyczka do VS Code).

```bash
cd firmware
pio run -e esp32-s3 -t upload     # albo: -e esp32dev / -e esp32-c3
pio device monitor
```

W monitorze wpisz `?`, żeby zobaczyć listę komend, np.:

```
L 30 10 500     # spójrz 30° w prawo i 10° w górę w 0,5 s
G nod           # kiwnij głową
H               # wróć do środka
```

## Piny (domyślne)

| Płytka | PAN | TILT | TOUCH |
|---|---|---|---|
| esp32-s3 | GPIO 4 | GPIO 5 | GPIO 6 |
| esp32dev | GPIO 18 | GPIO 19 | GPIO 23 |
| esp32-c3 | GPIO 3 | GPIO 4 | GPIO 5 |

Zmienisz je w `platformio.ini` (`-DPIN_...`).

## Kalibracja

W `include/config.h`:

1. Ustaw `PAN_CENTER_US` / `TILT_CENTER_US`, aż przy komendzie `H` głowa patrzy idealnie prosto.
2. Jeśli serwo kręci się w złą stronę – `PAN_INVERT` / `TILT_INVERT = true`.
3. Zawęź `PAN_MIN/MAX`, `TILT_MIN/MAX`, żeby telefon o nic nie zahaczał.

> ⚠️ Serwa zasilaj z osobnej przetwornicy 5–6 V, nie z pinu 5V płytki. Masy (GND) muszą być połączone. Szczegóły: [docs/montaz.md](../docs/montaz.md).
