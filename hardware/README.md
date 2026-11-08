# Sprzęt i obudowa

Parametryczne części do druku 3D (OpenSCAD) są w **[cad/](cad/README.md)** – tam
lista części, orientacja i ustawienia druku, śruby, kolejność montażu i **lista
wymiarów do zmierzenia przed drukiem**. Gotowe pliki: [stl/](stl/), podglądy:
[renders/](renders/), skrypty (STL/PNG, szczelność, kolizje): [tools/](tools/).

> **Wariant B (koncept):** głowa z ładowarką na ramieniu 5 DOF z ażurem Voronoi – STL, generator i podgląd 3D w [ramie/](ramie/README.md).

| Przód | 3/4 | Bok (pochylenie −25°) |
|---|---|---|
| ![](renders/asm_front_fold8_open.png) | ![](renders/asm_34_s23.png) | ![](renders/asm_side_fold8_open_tilt-25.png) |

Magnesy, biegunowość, siła trzymania i ostrzeżenia dot. ładowania: [cad/README.md – Mocowanie telefonu](cad/README.md#mocowanie-telefonu-magnesy--krótka-półka).

## Styl: „ludzik na nóżce”

1. **Głowa = obły kask** wokół telefonu (ekran = twarz, cały widoczny). Telefon
   trzyma się **magnetycznie**: przed ładowarką Anker 313 Pad (Ø 100, bez magnesów)
   leży tarcza z **pierścieniem 12 magnesów N52 Ø 8 × 2 mm** (Ø 54, współosiowo
   z cewką, skórka 0,6 mm), a na telefonie etui/pierścień MagSafe-compatible;
   ciężar przejmuje **krótka półka** z zaokrągloną wargą u dołu. Kieszeń ładowarki
   ma kanał kabla i wentylację. Tylna skorupa chowa ładowarkę, serwo TILT,
   przewody i opcjonalny wentylator 30 × 30. Na czubku czujnik dotyku TTP223
   i **antenka z diodą** WS2812.
2. **Uszy** – okrągłe zakończenia ramion z wypukłymi nakładkami; w prawym uchu
   tarcza orczyka serwa TILT (serwo siedzi w głowie), w lewym łożysko 608 osi.
3. **Szyja** – walec na łożysku lazy susan (zasłoniętym kołnierzem), z „barkami”,
   z których wyrastają ramiona.
4. **Tułów = jajo** – elipsoida ścięta płasko u dołu: ESP32-S3, dwie przetwornice,
   PD trigger (USB-C z tyłu), serwo PAN, komora obciążnika, ozdobne kratki
   i „guziki”, płaska pokrywa z 6 nóżkami.

## Wymagania (sprawdzone w projekcie)

- Oś TILT w środku wysokości telefonu w etui; ładowarka i pierścień magnesów
  przesunięte do cewki telefonu.
- Magnesy trzymają telefon z ≥ 5-krotnym zapasem (tilt −25°, ×2 dynamicznie,
  `tools/magnet_check.py`).
- Obrót ±80°, pochylenie −25°…+30° bez kolizji dla wszystkich profili telefonów
  (`tools/collision_check.py`), kąt przewrócenia ≥ 25° z obciążnikiem.
- Przewody do głowy przez szyję i łuk w płycie tułowia (pętla serwisowa).
- Wszystkie części na stół 220 × 220, bez podpór; PETG/ASA; wkładki M3.

Wymiary telefonów: [../docs/telefony.md](../docs/telefony.md) · dobór serw: [../docs/sterowanie-mocowaniem.md](../docs/sterowanie-mocowaniem.md)
