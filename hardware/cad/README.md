# Części do druku 3D – DeskMate DIY

Parametryczne źródła **OpenSCAD** (2021.01 lub nowszy) i gotowe pliki **STL** dla
głowy robota na dwóch osiach. Domyślny wariant: ładowarka **Anker 313 Wireless
Charger (Pad) A2503** (Qi 10 W, **bez magnesów**, Ø 100 × 11,2 mm, 67 g),
serwa **MG996R**, łożysko lazy susan **75 × 75 mm**.

| Plik | Zawartość |
|---|---|
| `params.scad` | **wszystkie parametry**: telefony, ładowarki, serwa, łożyska, luzy, wkładki, wymiary podstawy |
| `base.scad` | podstawa (obudowa) i pokrywa dolna (`part="base"` / `"cover"`) |
| `yoke.scad`, `pan_hub.scad` | jarzmo U i piasta serwa PAN |
| `bracket.scad`, `spacer.scad` | wsporniki głowy (`side="servo"` / `"bearing"`) i tuleja osi |
| `head_plate.scad`, `clamp.scad` | płyta głowy (kołyska telefonu + kieszeń ładowarki) i zaczepy górne – osobne dla każdego profilu telefonu |
| `assembly.scad` | złożenie z bryłami telefonu, serw i ładowarki – podgląd zakresu ruchu (`pan`, `tilt`, `profile`) |
| `../stl/` | wygenerowane STL (ułożone do druku) |
| `../renders/` | podglądy PNG |
| `../tools/` | `build.sh` (STL + PNG), `stl_check.py` (szczelność), `collision_check.py` (kolizje, moment, stabilność) |

## Ważne: ładowarka bez magnesów → telefon w kołysce

Anker 313 Pad nie ma magnesów, więc telefon **nie trzyma się magnetycznie**.
Płyta głowy jest kołyską: telefon stoi na **półce** z wargą zachodzącą 4 mm na
ekran, od góry przytrzymują go **dwa regulowane zaczepy** (śruba M3 w szczelinie,
±6 mm). Tył telefonu dociska się do wierzchu ładowarki – **bez plastiku pomiędzy**
(wierzch ładowarki = płaszczyzna tyłu telefonu, `pad_proud = 0`).

- Etui **bez magnesów, pierścieni i metalowych płytek** (zakłócają ładowanie i mogą
  grzać się w polu cewki). Etui do 5 mm grubości wg Ankera.
- Realna moc: Anker 313 daje 10 W tylko starszym Galaxy; S23 / Flip6 / Fold8 Ultra
  ładują się z nim w praktyce z mocą **ok. 5 W**. Przy ciągłej pracy ekranu, kamery
  i AI to raczej **spowalnianie rozładowania** niż ładowanie.
- Zasilanie ładowarki: micro-USB, zasilacz **QC 2.0/3.0** (5 V/2 A – 9 V/2 A) albo
  przetwornica 5 V / 2 A w podstawie (wtedy ładuje wolniej).
- **Opcja zapasowa – przewód USB-C:** końce telefonu (krótkie boki, gdzie jest
  gniazdo USB-C) wystają poza płytę i są wolne; przewód kątowy USB-C prowadź po
  wsporniku do przelotu w pokładzie jarzma. Środkowe wycięcie półki (40 mm) zostawia
  wolne przyciski boczne.
- Ładowarki magnetyczne Qi2 (Samsung EP-P2900, ESR HaloLock Qi2 Mini, Anker Zolo
  A25M2) są w `params.scad` jako alternatywy (`pad = "samsung_ep_p2900"` itd.).
  Kołyska działa z nimi tak samo; z etui magnetycznym telefon trzyma się dodatkowo
  magnesem.

## Lista części do druku

| Część (STL) | Szt. | Gabaryt [mm] | Orientacja druku | Podpory |
|---|---|---|---|---|
| `base.stl` – podstawa | 1 | 184 × 160 × 55 | do góry dnem (płyta górna na stole) | nie |
| `cover.stl` – pokrywa dolna | 1 | 183 × 159 × 33 | spodem na stole | nie |
| `yoke.stl` – jarzmo | 1 | 182 × 147 × 121 | pokładem na stole | nie (mostek 42 mm nad oknem serwa) |
| `pan_hub.stl` – piasta PAN | 1 | Ø37 × 10,5 | szerszą częścią na stole | nie |
| `bracket_servo.stl` – wspornik (serwo) | 1 | 56 × 30 × 31,5 | zewnętrzną ścianą (gniazdo orczyka) na stole | nie |
| `bracket_bearing.stl` – wspornik (łożysko) | 1 | 56 × 30 × 31,5 | zewnętrzną ścianą na stole | nie |
| `spacer.stl` – tuleja osi | 1 | Ø11,5 × 9,5 | pionowo | nie |
| `head_plate_<profil>.stl` – płyta głowy | 1 na telefon | S23/Flip6/Fold8 zł.: 112 × 147 × 30; Fold8 rozł.: 112 × 190 × 27 | tyłem na stole (kieszeń do góry) | nie (mały mostek nad kieszenią TTP223) |
| `clamp_<profil>.stl` – zaczep górny | 2 na telefon | 30 × ~22 × 12 | bokiem na stole | nie |

Profile: `s23`, `fold8_open` (Fold8 Ultra rozłożony), `fold8_closed`, `flip6_open`
(Flip6 rozłożony). Wymiana telefonu = przełożenie płyty głowy (2 × 2 śruby M3 do
wsporników) i zaczepów.

Dodatkowo z **TPU** (lub filcu samoprzylepnego 0,5–1 mm): paski na półkę, wargi
i spód zaczepów – telefon nie rysuje się i nie grzechocze (`fit_t = 0.6` mm luzu
jest właśnie na tę wkładkę).

## Ustawienia druku

- **PETG** (lub ASA) – nie PLA: ładowarka i telefon nagrzewają się (Qi2 do ~58 °C,
  Anker 313 mniej, ale ciągle), PLA mięknie od ~55 °C. Płyta głowy z ASA/PETG.
- Warstwa **0,2 mm**, dysza 0,4 mm.
- Ścianki: **4 obrysy**, 5 warstw góra/dół (płyta głowy, jarzmo, wsporniki, piasta);
  3 obrysy wystarczą w podstawie i pokrywie.
- Wypełnienie: jarzmo, wsporniki, piasta **40–50 %** (gyroid); płyta głowy **25–30 %**;
  podstawa/pokrywa **20 %**.
- Wkładki gwintowane M3 wtapiaj lutownicą (~220 °C dla PETG), prostopadle.
- Gniazdo łożyska 608: najpierw wydrukuj próbkę (`b608_fit` = 0,1 mm daje lekki
  wcisk; 0,2 – suwliwe pasowanie + kropla kleju).

## Śruby, wkładki, części kupne

| Element | Szt. | Gdzie |
|---|---|---|
| Wkładka gwintowana M3 × 5,7 (Ø otworu 4,2) | 4 | narożniki podstawy (pokrywa) |
| | 4 | płyta podstawy – pierścień dolny lazy susan |
| | 4 | pokład jarzma od spodu – pierścień górny lazy susan |
| | 4 | ramka serwa PAN (uszy serwa) |
| | 3 | piasta PAN (śruby od pokładu) |
| | 4 | wsporniki głowy (kołnierze) |
| | 2 | zaczepy górne (stopa) |
| **Razem wkładek M3** | **25** | |
| Śruba M3 × 8 (stożkowa DIN 7991) | 4 | pokrywa dolna |
| Śruba M3 × 8 (walcowa) | 8 | lazy susan (dół i góra) |
| Śruba M3 × 8–10 | 4 | uszy serwa PAN |
| Śruba M3 × 12 (stożkowa) | 3 | pokład → piasta PAN |
| Śruba M3 × 8 (stożkowa) | 4 | płyta głowy → wsporniki (łby pod ładowarką / pod telefonem) |
| Śruba M3 × 12 + podkładka | 2 | regulacja zaczepów |
| Śruba M3 × 10–12 + nakrętka M3 | 4 | uszy serwa TILT (nakrętki w gniazdach od zewnątrz ramienia) |
| Śruba M3 × 16 + nakrętka M3 | 4 | wentylator 30 × 30 (opcja; nakrętki pod ładowarką, mieszczą się w szczelinie żeber 2,5 mm) |
| Śruba M8 × 45 (łeb 6-kątny) + nakrętka M8 samokontrująca + 2 podkładki | 1 | oś TILT po stronie łożyska |
| Łożysko 608 (22 × 7 × 8) | 1 | ramię jarzma |
| Łożysko lazy susan 75 × 75 mm (lub okrągłe Ø 80) | 1 | podstawa ↔ jarzmo |
| Wkręty z kompletu orczyka (2 × 4 szt.) | 8 | tarcze orczyków do piasty PAN i wspornika TILT |
| Nóżki gumowe Ø 12 mm, samoprzylepne | 4 | wgłębienia w pokrywie |
| Obciążnik ~300 g (podkładki stalowe M10, śrut w woreczku lub zalany żywicą) | – | komora 108 × 29 × 30 mm w pokrywie |
| Wentylator 30 × 30 × 10 mm 5 V (opcja) | 1 | tył płyty głowy, pod ładowarką |
| Opaski zaciskowe 2,5 mm | ~6 | odciążenie kabla ładowarki, przewody na ramionach |

## Kolejność montażu

1. **Wkładki** – wtop wszystkie wkładki M3 (lista wyżej).
2. **Pokrywa** – przyklej moduły w ramkach (taśma dwustronna / klej na gorąco):
   ESP32 (USB-C w lewo, do okna w lewej ściance), XL4015 (serwa), LM2596
   (ESP32 + ładowarka), PD trigger (USB-C w tył, do wycięcia w tylnej ściance).
   Włóż obciążnik do komory. Ustaw napięcia przetwornic **przed** podłączeniem.
3. **Serwo PAN** – ustaw w pozycji środkowej (komenda `H` lub tester), włóż od spodu
   w ramkę pod płytą podstawy (korpus w stronę przodu), przykręć uszy M3.
4. **Lazy susan** – pierścień dolny przykręć do płyty podstawy (4 × M3 × 8).
5. **Piasta PAN** – przykręć tarczę orczyka do piasty wkrętami z kompletu, piastę
   do pokładu jarzma (3 × M3 × 12 stożkowe od góry).
6. **Jarzmo** – nasuń piastę na wałek serwa (głowa na wprost), przykręć śrubę wałka
   przez otwór Ø 8 w pokładzie. Obróć jarzmo o 45° i przez otwory serwisowe
   w płycie podstawy (od spodu, pokrywa zdjęta) przykręć pierścień górny lazy susan
   do wkładek w pokładzie. Wróć do 0°.
7. **Przewody** – przeprowadź przewody głowy (serwo TILT, TTP223, wentylator,
   zasilanie ładowarki) przez otwór Ø 14 w pokładzie i **łuk** w płycie podstawy.
   Zostaw pętlę serwisową – przy obrocie ±80° przewód przesuwa się w łuku, nie
   skręca się wokół osi (środek osi PAN zajmuje wałek serwa).
8. **Serwo TILT** – ustaw środek, wsuń korpus od wewnątrz w okno prawego ramienia
   (wałek na osi, korpus do tyłu), przykręć uszy M3 × 10–12 z nakrętkami.
9. **Łożysko 608** – wciśnij od zewnątrz w lewe ramię.
10. **Wsporniki** – tarczę orczyka przykręć do wspornika „servo”. W „bearing” włóż
    łeb M8 w gniazdo 6-kątne. Przykręć oba wsporniki do płyty głowy (4 × M3 × 8
    stożkowe od przodu płyty).
11. **Głowa** – nasuń wspornik „servo” z orczykiem na wałek serwa TILT (płyta
    pionowo), śruba wałka przez otwór Ø 9. Po drugiej stronie: tuleja 9,5 mm na
    śrubę M8 → łożysko → podkładka → nakrętka samokontrująca (dociśnij tylko
    pierścień wewnętrzny, oś ma się obracać lekko).
12. **Ładowarka** – włóż do kieszeni gniazdem micro-USB w kanał (domyślnie w dół),
    wtyk micro-USB, kabel w kanale, opaska przez dwie szczeliny = odciążenie.
    Ładowarka trzyma się na wcisk; jeśli lata, dodaj 2–3 kropki silikonu na żebrach.
13. **TTP223** – wklej w kieszeń w górnej krawędzi płyty, przewód rowkiem do tyłu.
14. **Zaczepy** – stopy w kanały, M3 × 12 + podkładka od tyłu. Postaw telefon na
    półce, opuść zaczepy do lekkiego docisku, dokręć.
15. **Kalibracja** – wg [../../docs/sterowanie-mocowaniem.md](../../docs/sterowanie-mocowaniem.md),
    najpierw bez telefonu, potem z najcięższym (Fold8 Ultra rozłożony).

## Sprawdzenie kolizji, momentu i stabilności

Wynik `tools/collision_check.py` dla domyślnych parametrów (pełny zakres
pan ±80°, tilt −25…+30°, krok 1° tilt / 5° pan, woksele 0,5 mm):

| Profil | Telefon ↔ jarzmo | Głowa ↔ jarzmo | Głowa+telefon ↔ podstawa | Masa głowy z telefonem (plastik) | Środek ciężkości od osi TILT (Y / Z) | Maks. moment na TILT | Zapas stabilności / kąt przewrócenia |
|---|---|---|---|---|---|---|---|
| S23 | 2,5 mm (tilt −25°) | 2,0 mm | > 10 mm | ≈ 429 g (97 g) | +20,9 / +0,6 mm | 0,90 kg·cm (10 % MG996R) | 47 mm / 29° |
| Fold8 Ultra rozłożony | 2,5 mm (tilt −25°) | 2,0 mm | > 10 mm | ≈ 522 g (143 g) | +20,4 / +5,5 mm | 1,10 kg·cm (12 %) | 47 mm / 28° |
| Fold8 Ultra złożony | 2,5 mm (tilt −25°) | 2,0 mm | > 10 mm | ≈ 477 g (98 g) | +22,2 / +0,5 mm | 1,06 kg·cm (11 %) | 47 mm / 28° |
| Flip6 rozłożony | 2,2 mm (tilt −25°) | 2,0 mm | > 10 mm | ≈ 448 g (97 g) | +21,1 / +0,6 mm | 0,95 kg·cm (10 %) | 45 mm / 28° |

Jarzmo ↔ podstawa (pan ±80°): 8 mm (wysokość lazy susan). Najniższy punkt głowy
z telefonem: 83 mm nad blatem (Fold8 rozłożony, tilt −25°), podstawa ma 58 mm.
Masa głowy uwzględnia ładowarkę Anker 313 (67 g), wentylator 10 g, oś M8 z łożyskiem
~35 g i drobnicę. Moment statyczny to ok. 10–12 % możliwości MG996R, więc zostaje
duży zapas na przyspieszenia (szybkie gesty) i na serwo pracujące cicho. MG90S
(~1,8 kg·cm) też by uniósł głowę statycznie, ale bez zapasu – na TILT zostaw
MG996R / DS3218.

Założenia: wydruk ~55 % gęstości litego PETG, telefon + etui 40 g, obciążnik
300 g, podparcie na 4 nóżkach (prostokąt 140 × 120 mm). Bez obciążnika robot nadal
stoi stabilnie, ale przy szybkim obrocie może się przesuwać – obciążnik zalecany.

Jak to osiągnięto:
- oś TILT leży w połowie wysokości telefonu (środek ciężkości w Z); ładowarka jest
  przesuwana do cewki telefonu (Fold8 rozłożony: cewka w górnej połowie, +35,8 mm;
  Flip6 rozłożony: cewka w prawej połowie, telefon przesunięty o 41,3 mm w X),
- przednie krawędzie ramion jarzma są ścięte płaszczyznami tyłu telefonu przy
  tilt −25° i +30° (`clear_r` = 23,5 mm od osi, tył telefonu 25 mm od osi) –
  nawet Fold8 rozłożony (szerszy niż rozstaw ramion) przechodzi przed ramionami,
- oś TILT 96 mm nad pokładem: najniższy punkt telefonu przy tilt −25° jest nad
  pokładem, a pokład obraca się razem z głową, więc pan niczego nie zmienia.

## Wymiary do zmierzenia PRZED drukiem

Wpisz zmierzone wartości w `params.scad`. Najważniejsze (ryzyko przeróbki):

1. **Serwo TILT i PAN** (tabela `SERVOS`) – długość/szerokość korpusu, wysokość do
   spodu uszu, grubość uszu, rozstaw otworów uszu (wzdłuż i w poprzek), odległość
   osi wałka od końca korpusu, **odległość od wierzchu uszu do zewnętrznej
   powierzchni tarczy orczyka** (`sv_ht` – ustala położenie ramienia jarzma
   i wysokość piasty), średnica i grubość tarczy orczyka, promienie otworów
   w tarczy (`horn_hole_r`). Klony MG996R różnią się do ~1 mm.
2. **Lazy susan** – bok/średnica, wysokość całkowita (`ls_t`), otwór środkowy
   (`ls_id` ≥ 38 mm – przechodzi piasta Ø 37), rozstaw otworów obu pierścieni.
3. **Anker 313 Pad** – średnica 100 i grubość 11,2 są z dokumentacji; sprawdź
   tylko **kąt położenia gniazda micro-USB** (`pad_port_angle`, domyślnie 270° =
   w dół) i rozmiar wtyku z kablem (`PADS[...][6]`, 12 mm). Jeśli zmienisz na
   ładowarkę Qi2 – zmierz jej średnicę i grubość (wartości w tabeli `PADS` są
   szacunkowe).
4. **Telefony w etui** – długość, wysokość, grubość z etui (`case_side`,
   `case_thick`) i **położenie cewki** (`PHONES[...][5..6]`: przyłóż telefon do
   ładowarki i przesuwaj, aż zacznie ładować / sprawdź w serwisowych schematach).
5. **ESP32-S3-DevKitC-1, XL4015, LM2596, PD trigger** – wymiary płytek
   (`esp_dim`, `xl_dim`, `lm_dim`, `pd_dim`) i położenie gniazd USB względem
   krawędzi (okna w ściankach podstawy są na wysokości 3–14 mm nad pokrywą).
6. **Łożysko 608** i **wkładki M3** – zrób próbkę otworu (`b608_fit`, `ins_d`).
7. **TTP223** (`ttp`) i **wentylator** (rozstaw otworów 24 mm).

## Jak zmieniać parametry

- W OpenSCAD otwórz plik części i użyj **Customizer** (Okno → Customizer) albo
  edytuj `params.scad`.
- Z linii poleceń, np. płyta dla Fold8 rozłożonego z serwem DS3218:
  ```
  openscad -o head_plate_fold8_open.stl -D 'profile="fold8_open"' -D 'servo_tilt="DS3218"' hardware/cad/head_plate.scad
  ```
- Wszystkie STL i podglądy: `OPENSCAD=/ścieżka/openscad hardware/tools/build.sh`
  (bez zmiennej używa `openscad` z PATH; PNG wymaga `xvfb-run` na serwerze bez ekranu).
- Po każdej zmianie: `python3 hardware/tools/stl_check.py hardware/stl/*.stl`
  i `python3 hardware/tools/collision_check.py` (numpy + scipy).
- Nowy telefon: dopisz wiersz do `PHONES` (wymiary bez etui, masa, położenie cewki),
  dodaj profil w `build.sh`.
- Oś TILT względem telefonu: `axis_dz` przesuwa telefon w górę/dół (np. gdy etui
  jest asymetryczne), `D_back` – odległość osi od tyłu telefonu (mniej = mniejszy
  moment, ale musi zmieścić się ładowarka, żebra i wsporniki).

## Znane ograniczenia

- Wymiary serw, lazy susan, modułów i położenie cewek to wartości katalogowe /
  szacunkowe – **zmierz przed drukiem**.
- Przewody nie idą dokładnie przez oś PAN (zajmuje ją wałek serwa) – idą łukiem
  Ø 124 mm z tyłu; to wymaga pętli serwisowej, ale nie ma skręcania.
- Płyta dla Fold8 rozłożonego jest duża (112 × 190 mm) – zmieści się na stole
  220 × 220 mm.
- Ładowarka siedzi w kieszeni na wcisk – bez telefonu nie odwracaj głowy do góry
  nogami.
- Wentylator 30 × 30 jest opcjonalny (`fan_on`); bez niego zostają szczeliny
  powietrzne pod ładowarką.
