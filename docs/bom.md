# Lista części (BOM)

Ceny orientacyjne w PLN (sklepy z elektroniką, Allegro, AliExpress). Wybór serw zależy od najcięższego telefonu – patrz [sterowanie-mocowaniem.md](sterowanie-mocowaniem.md).

## Elektronika

| # | Część | Szt. | ~Cena | Uwagi |
|---|---|---|---|---|
| 1 | ESP32-S3 DevKitC-1 (alternatywnie ESP32 DevKit lub ESP32-C3 SuperMini) | 1 | 30–60 zł | BLE + Wi-Fi; firmware ma profile dla wszystkich trzech |
| 2 | Serwo TILT: MG996R / DS3218 (metalowe zębatki) | 1 | 25–60 zł | mocne – potrzebne dla Fold8 Ultra |
| 3 | Serwo PAN: MG90S (przy łożysku wystarczy) lub MG996R | 1 | 12–40 zł | obrót podstawy |
| 4 | Zasilacz USB-C PD 45–65 W | 1 | 60–120 zł | zasila robota i ładowarkę telefonu |
| 5 | Moduł USB-C PD trigger (wybór 9 V lub 12 V) | 1 | 10–20 zł | wymusza wyższe napięcie z zasilacza |
| 6 | Przetwornica step-down 5–6 V, min. 3 A (np. XL4015, LM2596 z radiatorem) | 1 | 10–25 zł | **tylko** dla serw |
| 7 | Przetwornica step-down 5 V / 3 A lub drugi kanał USB z zasilacza | 1 | 10–20 zł | dla ESP32 i ładowarki Qi2 |
| 8 | Kondensator elektrolityczny 1000 µF / 16 V | 2 | 2 zł | przy serwach (tłumi skoki napięcia) |
| 9 | Czujnik dotyku TTP223 | 1 | 3–5 zł | opcjonalny „głaskanie” |
| 10 | Przewody, złącza JST/Dupont, płytka uniwersalna | – | 15 zł | |

## Mocowanie telefonu

| # | Część | ~Cena | Uwagi |
|---|---|---|---|
| 11 | Magnetyczna ładowarka Qi2 typu krążek, np. [ESR HaloLock Qi2 Mini 15 W](https://homescreen.pl/pl/ladowarki-indukcyjne/76006-ladowarka-indukcyjna-esr-halolock-qi2-mini-magnetic-magsafe-15w-black-4894240189238.html) | 80–150 zł | wbudowana w płytkę głowy |
| 12 | Etui magnetyczne Fold8 Ultra (np. Samsung Silicone/Carbon Magnet Case) | 150–250 zł | |
| 13 | Etui z pierścieniem magnetycznym S23 | 30–80 zł | |
| 14 | Etui z pierścieniem magnetycznym Flip6 | 30–80 zł | |

## Mechanika

| # | Część | ~Cena | Uwagi |
|---|---|---|---|
| 15 | Łożysko obrotowe „lazy susan” Ø 60–100 mm | 10–25 zł | dźwiga ciężar głowy |
| 16 | Łożysko kulkowe 608 lub 6800 dla osi TILT | 5 zł | po przeciwnej stronie serwa |
| 17 | Śruby M2/M3 z nakrętkami, wkładki gwintowane | 15 zł | |
| 18 | Filament PETG ~300 g | 30 zł | PETG jest odporniejszy na ciepło od ładowarki |
| 19 | Podkładki antypoślizgowe / obciążnik podstawy | 10 zł | żeby robot nie przewracał się przy obrocie |

**Razem:** ok. 300–550 zł (bez etui ~250–400 zł).

## Narzędzia

Lutownica, multimetr, drukarka 3D (lub usługa druku), komputer z PlatformIO i Android Studio.
