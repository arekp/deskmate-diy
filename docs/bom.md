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
| 11 | **Wybrana:** Anker 313 Wireless Charger (Pad) A2503 – Qi 10 W, bez magnesów, micro-USB, Ø 100 × 11,2 mm, 67 g (✅ kupiona) | ~60–100 zł | wbudowana w płytkę głowy; telefon w uchwycie mechanicznym |
| 11a | Zasilacz Quick Charge 3.0 (min. 9 V/2 A) do ładowarki | 30–60 zł | nie ma go w zestawie z Ankerem |
| 11b | Alternatywa magnetyczna: Samsung EP-P2900TB (Qi2 25 W, 4,4 mm, z zasilaczem 45 W) – [MediaMarkt](https://mediamarkt.pl/pl/product/_ladowarka-bezprzewodowa-samsung-25w-ciemnoszary-ladowarka-sieciowa-45-w-1504055.html) | 150–250 zł | wymaga etui magnetycznych |
| 12 | Etui **bez magnesów i metalu** (cienkie, do 5 mm) – S23, Flip6, Fold8 Ultra | 30–150 zł/szt. | przy ładowarce Anker 313; etui magnetyczne tylko dla wariantu 11b |
| 13 | Filament TPU (miękkie wkładki uchwytu) | 30 zł | opcjonalnie |

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
