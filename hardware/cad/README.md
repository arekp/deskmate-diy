# Części do druku 3D – DeskMate DIY („ludzik na nóżce”)

Parametryczne źródła **OpenSCAD** (2021.01 lub nowszy) i gotowe **STL** robota
w stylu „uroczego robocika”: **obły kask** otaczający telefon (ekran = twarz),
po bokach **uszy** chowające oś pochylenia, **krótka szyja** z „barkami”,
**tułów-jajo** z elektroniką i **antenka z diodą** na czubku.

Domyślnie: ładowarka **Anker 313 Wireless Charger (Pad) A2503** (Qi 10 W,
**bez magnesów**, Ø 100 × 11,2 mm, 67 g) z **pierścieniem 12 magnesów
neodymowych** w tarczy przed ładowarką (telefon trzyma się magnetycznie, stoi na
krótkiej półce), serwa **MG996R**, łożysko lazy susan
**okrągłe Ø 80** (kwadratowe 75 × 75 też obsługiwane – szyja sama się poszerza).

![Złożenie – 3/4](../renders/asm_34_fold8_open.png)

| Plik | Zawartość |
|---|---|
| `params.scad` | **wszystkie parametry**: telefony, ładowarki, serwa, łożyska, luzy, wkładki, kształty |
| `base.scad` | tułów (`part="base"`), pokrywa dolna (`"cover"`), kołnierz szyi (`"collar"`) |
| `yoke.scad` | szyja z barkami (`"neck"`), ramiona z uszami (`"arm_servo"`, `"arm_bearing"`), nakładki uszu (`"cap_servo"`, `"cap_bearing"`), piasta PAN (`"hub"`) |
| `head_plate.scad` | przód kasku: ramka telefonu, krótka półka z wargą, kieszeń ładowarki, tarcza z magnesami (moduł `magnet_disc`) |
| `head_back.scad` | tylna skorupa kasku |
| `magnet_disc.scad` | tarcza z pierścieniem magnesów (przed ładowarką) |
| `bracket.scad`, `spacer.scad` | uchwyty w głowie: serwo TILT (`side="servo"`) i oś z łożyskiem (`"bearing"`), tuleje osi |
| `antenna.scad` | antenka (`part="antenna"`) i przykrywka diody (`"cap"`) |
| `assembly.scad` | złożenie z bryłami telefonu, serw i ładowarki (`pan`, `tilt`, `profile`) |
| `../stl/`, `../renders/`, `../tools/` | STL do druku, podglądy PNG, skrypty (`build.sh`, `stl_check.py`, `collision_check.py`, `magnet_check.py`) |

## Jak to działa mechanicznie

- **Serwo PAN** wisi w tułowiu pod płytą górną; jego orczyk obraca **piastę**
  przykręconą do **szyi**. Ciężar trzyma **lazy susan** między tułowiem a szyją,
  zasłonięte nieruchomym **kołnierzem**.
- Z szyi wychodzą **barki** i dwa **ramiona** zakończone okrągłymi **uszami**.
- **Serwo TILT siedzi w głowie** (korpusem w dół, w uchwycie przykręconym do
  płyty kasku), a jego tarcza orczyka jest przykręcona do wnętrza **prawego ucha**
  (prawe = +X, po lewej, gdy patrzysz na twarz robota). Głowa obraca się więc
  „na orczyku”, a serwo jedzie razem z nią i dodatkowo ją wyważa.
- W **lewym uchu** jest łożysko 608; śruba M8 od zewnątrz przechodzi przez
  łożysko i tuleję do nakrętki uwięzionej w uchwycie w głowie.
- Nakładki uszu zasłaniają śrubę wałka serwa i łeb M8.
- Oś TILT leży w połowie wysokości telefonu (środek ciężkości), 30,7 mm za jego
  tyłem; ładowarka jest przesunięta do cewki danego telefonu (Fold8 rozłożony:
  górna połowa, +35,8 mm).

## Mocowanie telefonu: magnesy + krótka półka

Żaden z telefonów (S23, Flip6, Fold8 Ultra) ani Anker 313 Pad nie ma magnesów,
więc magnesy są w kasku, a na telefonie jest **etui MagSafe-compatible albo
naklejany pierścień magnetyczny** (stalowy lub magnetyczny, ok. Ø 46 wewn. /
56–60 zewn.), wyśrodkowany na cewce telefonu.

- **Tarcza z magnesami** (`magnet_disc.stl`, Ø 100, 2,7 mm) leży w kieszeni
  przed ładowarką: **12 magnesów Ø 8 × 2 mm N52** na okręgu **Ø 54 mm**,
  współosiowo z cewką ładowarki i z cewką telefonu (przesunięcie cewki – kolumny
  `coil_dx/coil_dz` w `PHONES`, np. Fold8 rozłożony +35,8 mm). Magnesy wciskasz /
  wklejasz **od strony ładowarki**; od strony telefonu zostaje **skórka 0,6 mm**.
  Między ładowarką a tyłem telefonu jest **2,7 mm plastiku** (+ etui).
- Kołnierz tarczy siedzi za wargą kieszeni, więc magnesy nie wyrwą tarczy do
  przodu; ładowarka dociska ją od tyłu.
- **Półka** u dołu z zaokrągloną wargą 4 mm (nad ekranem) przejmuje ciężar
  telefonu; magnesy tylko dociskają go do tarczy. Nad telefonem jest 5,5 mm zapasu.
- **Wkładanie:** unieś telefon nad wargę, przyłóż plecami do tarczy (magnesy
  przyciągną), zsuń w dół na półkę. **Wyjmowanie:** podnieś i odchyl od góry.
- Na środku wargi jest wycięcie na palec/przewód, po bokach ramki wycięcia
  otwarte do przodu na gniazdo USB-C i głośnik.

### Biegunowość (ważne!)

Wszystkie 12 magnesów **tym samym biegunem do telefonu**. Z pierścieniem
**stalowym** biegunowość nie ma znaczenia; z pierścieniem/etui **magnetycznym**
ma: przed klejeniem przyłóż jeden magnes do pierścienia na telefonie, zaznacz
markerem stronę, która **przyciąga**, i wszystkie wklej tą stroną do przodu
(do telefonu). Znacznik na kołnierzu tarczy (kreska) = góra.

### Siła trzymania (`tools/magnet_check.py`)

Model ostrożny: udźwig katalogowy N52 Ø8×2 ≈ 15 N na grubej stali, spadek
z odległością (pole na osi walca)², ×0,3 za cienki i wąski pierścień;
wymagane: telefon na półce przy tilt −25° (odchyla się od płyty), współczynnik
dynamiczny 2, ramię siły względem półki.

| Profil | Telefon + etui | Wymagane | 12 × Ø8×2, szczelina 1,7 mm (etui 1 mm) | 12 × Ø8×2, szczelina 2,7 mm (etui 2 mm) |
|---|---|---|---|---|
| S23 | 208 g | 1,7 N | 22 N (×13) | 11 N (×6,4) |
| Fold8 Ultra rozłożony | 255 g | 1,4 N | 22 N (×16) | 11 N (×7,8) |
| Fold8 Ultra złożony | 255 g | 2,1 N | 22 N (×11) | 11 N (×5,2) |
| Flip6 rozłożony | 227 g | 1,9 N | 22 N (×12) | 11 N (×5,9) |

Zapas jest co najmniej 5-krotny, więc **Ø8×2 wystarcza**. Większe Ø10×3
(×2,2 siły) wymagałyby tarczy 3,7 mm, czyli >3 mm plastiku nad cewką – zmień
tylko, jeśli etui jest wyjątkowo grube (parametry `mag_d`, `mag_t`). Bez półki
(tarcie μ≈0,3) magnesy musiałyby dać ~7 N na sam ciężar – dlatego półka zostaje.

### Ładowanie z magnesami – ostrzeżenia

- **Nie kładź magnesów nad środkiem cewki** – pierścień Ø 54 omija środek;
  nie zmniejszaj `mag_ring_d` poniżej ~50 mm.
- Używaj pierścienia/etui **przeznaczonego do ładowania bezprzewodowego**
  (MagSafe-compatible) – zwykła stalowa podkładka na środku cewki blokuje
  ładowanie i się grzeje.
- Anker ostrzega, że metal i magnesy mogą przerywać ładowanie: **sprawdź
  ładowanie z tarczą i pierścieniem przed drukiem kasku** (wydrukuj samą
  `magnet_disc.stl`, przyłóż do ładowarki). Jeśli nie ładuje – spróbuj
  `mag_ring_d = 58–60` albo plan B: ładowarka Qi2 Samsung EP-P2900
  (`pad = "samsung_ep_p2900"`), która ma własne magnesy.

### Ładowanie i zasilanie

- Realna moc: 10 W tylko starsze Galaxy; S23 / Flip6 / Fold8 Ultra ładują się
  z Ankerem 313 ok. **5 W** – przy ciągłej pracy ekranu, kamery i AI to raczej
  **spowalnianie rozładowania** niż ładowanie.
- Zasilanie ładowarki: micro-USB z zasilacza **QC 2.0/3.0** (5 V/2 A – 9 V/2 A)
  albo z przetwornicy 5 V / 2 A w tułowiu (wolniej). Kabel wychodzi z ładowarki
  w kanale (domyślnie w lewo), przez płytę do wnętrza kasku i lewym bokiem
  (otwór przy osi) po lewym ramieniu do szyi.
- **Zapas – przewód USB-C:** po bokach ramki są wycięcia otwarte do przodu
  dokładnie na wysokości gniazda USB-C telefonu (krótki bok) – przewód kątowy
  wchodzi bez zdejmowania telefonu.
- **Flip6 rozłożony:** Samsung zaleca ładowanie Flipa **złożonego**; rozłożony
  zwykle nie ładuje się bezprzewodowo (cewka w dolnej połowie, ~41 mm od środka).
  Domyślnie telefon jest na środku i zasilany przewodem. Jeśli chcesz spróbować
  ładowania – wpisz `41.3` w `PHONES` (kolumna `coil_dx`) i zwiększ `head_w`
  do ~97 mm (głowa i rozstaw uszu urosną).
- Ładowarki magnetyczne Qi2 (Samsung EP-P2900, ESR HaloLock Qi2 Mini, Anker Zolo
  A25M2) są w `params.scad` jako alternatywy (`pad = "samsung_ep_p2900"` itd.).

## Lista części do druku

| Część (STL) | Szt. | Gabaryt [mm] | Orientacja druku | Podpory |
|---|---|---|---|---|
| `body.stl` – tułów (jajo) | 1 | 208 × 200 × 55 | **do góry dnem** (płaska płyta górna na stole); boki rosną na zewnątrz maks. 45° | nie |
| `body_cover.stl` – pokrywa dolna | 1 | 207 × 199 × 33 | spodem na stole | nie |
| `neck_collar.stl` – kołnierz szyi | 1 | Ø 123 × 7 | spodem na stole | nie |
| `neck_shoulders.stl` – szyja z barkami | 1 | 231 × 30…123 × 12 (w STL obrócona o 45°: 172 × 172) | płaskim spodem, **po przekątnej stołu 220 × 220** | nie |
| `arm_servo.stl` – ramię z uchem (orczyk) | 1 | 136 × 68 × 8 | płaską stroną wewnętrzną na stole | nie |
| `arm_bearing.stl` – ramię z uchem (608) | 1 | 136 × 68 × 10 | płaską stroną wewnętrzną na stole | nie |
| `ear_cap_servo.stl`, `ear_cap_bearing.stl` – nakładki uszu | 1 + 1 | Ø 50 × 8 / 13 | płaskim spodem | nie |
| `pan_hub.stl` – piasta PAN | 1 | Ø 37 × 10,5 | szerszą częścią na stole | nie |
| `head_front_<profil>.stl` – przód kasku | 1 na telefon | 184 × 122 × 30 (Fold8 rozł.: 184 × 187 × 27) | **płaskim tyłem na stole**, ramka do góry | nie (mostki nad komorami odchudzającymi i kieszenią TTP223) |
| `head_back_<profil>.stl` – tylna skorupa kasku | 1 na telefon | 184 × 122 × 71 (Fold8 rozł.: 184 × 187 × 71) | **płaskim tyłem (kratką) na stole**; boki maks. 45° | nie |
| `magnet_disc.stl` – tarcza z magnesami | 1 (wspólna) | Ø 100 × 2,7 | kołnierzem (gniazda magnesów) na stole | nie (skórka 0,6 mm nad gniazdami Ø 8 jako mostek) |
| `head_mount_servo.stl` – uchwyt serwa TILT | 1 | 64 × 25 × 19 | płytą na stole | nie |
| `head_mount_bearing.stl` – uchwyt osi | 1 | 64 × 30 × 29 | płytą na stole | nie |
| `spacers.stl` – tuleje osi (10,1 + 2,5 mm) | 1 kpl. | Ø 11,5 | pionowo | nie |
| `antenna.stl` – antenka | 1 | Ø 20 × 43 | kołnierzem na stole (spód kulki stożkowy 45°) | nie |
| `antenna_cap.stl` – przykrywka diody | 1 | Ø 20 × 12 | płaską stroną na stole, **przezroczysty / biały PETG** | nie |

Profile: `s23`, `fold8_open` (Fold8 Ultra rozłożony), `fold8_closed`, `flip6_open`.
Wymiana telefonu = przełożenie kasku (przód + tył) razem z uchwytami, tarczą,
serwem i ładowarką albo trzymanie kilku kompletów kasku. Ramiona, szyja i tułów są
wspólne. Wszystko mieści się na stole **220 × 220 mm** (szyja po przekątnej).

Sugerowane kolory: skorupy białe/kremowe, szyja, kołnierz, nakładki uszu i tarcza magnesów
w kolorze akcentu (np. pomarańczowym), przykrywka diody przezroczysta.

## Ustawienia druku

- **PETG** (lub ASA) – nie PLA: ładowarka i telefon grzeją się, PLA mięknie od ~55 °C.
- Warstwa **0,2 mm**, dysza 0,4 mm.
- Ścianki: **4 obrysy**, 5 warstw góra/dół dla ramion, szyi, uchwytów, piasty,
  przodu kasku; **skorupy** (tułów, tył kasku) – 3 obrysy (ścianka 2–2,4 mm drukuje
  się praktycznie pełna).
- Wypełnienie: ramiona, szyja, uchwyty, piasta **40 %** (gyroid); przód kasku
  **20 %**; pokrywa, kołnierz, nakładki 15–20 %.
- Obłe skorupy drukuj w podanej orientacji – nawisy są ograniczone do 45°
  (profil boków to parabola). Włącz „chłodzenie nawisów”; podpory niepotrzebne.
- Wkładki gwintowane M3 wtapiaj lutownicą (~220 °C dla PETG).
- Gniazdo łożyska 608 (w ramieniu) – zrób próbkę (`b608_fit` 0,1 = lekki wcisk).

## Śruby, wkładki, części kupne

| Element | Szt. | Gdzie |
|---|---|---|
| Wkładka gwintowana M3 × 5,7 (otwór Ø 4,2) | 4 | ucha tułowia (pokrywa) |
| | 4 | płyta tułowia – pierścień dolny lazy susan |
| | 3 | płyta tułowia – kołnierz szyi |
| | 4 | ramka serwa PAN |
| | 4 | szyja od spodu – pierścień górny lazy susan |
| | 3 | piasta PAN |
| | 4 | stopy ramion (2 na ramię) |
| | 4 | ramiona – nakładki uszu |
| | 4 | przód kasku – łapy uchwytów serwa/osi |
| | 4 | przód kasku – śruby tylnej skorupy (góra/dół) |
| | 2 | przód kasku – antenka |
| **Razem wkładek M3** | **40** | |
| Śruba M3 × 8 stożkowa (DIN 7991) | 4 + 3 | pokrywa dolna; kołnierz szyi |
| Śruba M3 × 8 | 8 | lazy susan (dół i góra) |
| Śruba M3 × 8 | 4 | uszy serwa PAN |
| Śruba M3 × 16 stożkowa | 3 + 4 | szyja → piasta (od góry); barki → stopy ramion (od spodu) |
| Śruba M3 × 8 z łbem walcowym/grzybkowym | 4 | nakładki uszu |
| Śruba M3 × 8–10 | 4 | łapy uchwytów → płyta kasku (od tyłu) |
| Śruba M3 × 10 + nakrętka | 4 | uszy serwa TILT (nakrętki w gniazdach uchwytu) |
| Śruba M3 × 8 grzybkowa | 4 | tylna skorupa → przód kasku (góra/dół) |
| Magnes neodymowy **N52 Ø 8 × 2 mm** (krążek, osiowo namagnesowany) | 12 | tarcza przed ładowarką; klej CA/epoksyd |
| Etui MagSafe-compatible lub naklejany pierścień magnetyczny (do ładowania bezprzewodowego) | 1 na telefon | tył telefonu, środek na cewce |
| Śruba M3 × 8 stożkowa | 2 | antenka |
| Śruba M3 × 16 + nakrętka | 4 | wentylator 30 × 30 (opcja) |
| Śruba M8 × 35 (łeb 6-kątny) + nakrętka M8 | 1 | oś TILT – śruba od zewnątrz lewego ucha |
| Łożysko 608 (22 × 7 × 8) | 1 | lewe ramię |
| Łożysko lazy susan Ø 80 (lub 75 × 75) | 1 | tułów ↔ szyja |
| Wkręty z kompletu orczyka | 8 | tarcze orczyków: do piasty PAN i do prawego ucha |
| Nóżki gumowe Ø 12 mm | 6 | wgłębienia w pokrywie |
| Obciążnik ~300 g (śrut stalowy w woreczku / zalany żywicą, podkładki) | – | komora 72 × 26 × 30 mm w pokrywie |
| Dioda WS2812 (5 mm THT lub mała płytka ≤ Ø 11) + 3 cienkie przewody | 1 | antenka |
| Wentylator 30 × 30 × 10 mm 5 V (opcja) | 1 | za ładowarką |

## Kolejność montażu

1. **Wkładki** – wtop wszystkie wkładki M3.
2. **Pokrywa** – moduły w ramkach (taśma dwustronna / klej na gorąco): ESP32
   (USB do tyłu), XL4015 (serwa), LM2596 (ESP32 + ładowarka), PD trigger
   (USB-C dotyka tylnej ścianki). Obciążnik do komory. Napięcia przetwornic ustaw
   **przed** podłączeniem.
3. **Serwo PAN** – środek (`H`), od spodu w ramkę pod płytą tułowia (korpus do
   przodu), M3 × 8.
4. **Lazy susan** – pierścień dolny do płyty tułowia; **kołnierz** na płytę (3 × M3).
5. **Piasta** – tarcza orczyka PAN do piasty, piasta do szyi (3 × M3 × 16).
6. **Szyja** – nasuń piastę na wałek serwa (barki w poprzek), śruba wałka przez
   otwór Ø 8. Od spodu (pokrywa zdjęta) przez otwory serwisowe przykręć pierścień
   górny lazy susan do szyi.
7. **Ramiona** – przykręć od spodu barków (2 × M3 × 16 na ramię). Wciśnij 608
   w lewe ucho od zewnątrz. Tarczę orczyka TILT przykręć do wnętrza prawego ucha.
8. **Głowa (na stole)** – serwo TILT do uchwytu (uszy M3 × 10 + nakrętki), uchwyty
   do płyty kasku (łapy, M3 × 10 od tyłu). Ładowarka do kieszeni (wtyk micro-USB
   w kanał, kabel przez przelot do tyłu, opaska przez 2 szczeliny = odciążenie),
   wentylator, TTP223 w kieszeń na czubku, przewody antenki.
   **Magnesy:** sprawdź biegunowość (rozdział „Biegunowość”), wciśnij 12 magnesów
   w gniazda tarczy od strony kołnierza (kropla kleju CA lub epoksydu, wszystkie
   tym samym biegunem do przodu), odczekaj do związania. Tarczę włóż w kieszeń
   od tyłu, przodem do telefonu (kreska na kołnierzu u góry), dopiero potem
   ładowarkę – dociśnie tarczę do wargi kieszeni. Przewody zbierz
   w wiązkę do otworu przy osi w lewym boku tylnej skorupy.
9. **Tylna skorupa** – nasuń na stopień przodu kasku (okno na serwo po prawej,
   otwór Ø 16 po lewej), 4 × M3 × 8 (góra/dół).
10. **Głowa między uszy** – serwo w środku zakresu, płyta pionowo: wsuń wałek serwa
    w tarczę orczyka w prawym uchu, śruba wałka przez otwór w uchu. Z lewej:
    śruba M8 od zewnątrz → tuleja 2,5 → łożysko → tuleja 10,1 (przez otwór
    skorupy) → nakrętka uwięziona w uchwycie. Dokręć tak, by oś szła lekko.
11. **Przewody** – lewym ramieniem (opaski w otworach) do szyi, otwór Ø 14
    w szyi, łuk w płycie tułowia. Zostaw pętlę serwisową na ±80°.
12. **Nakładki uszu**, **antenka** (dioda w gniazdo, przykrywka na wcisk).
    Na telefon naklej pierścień magnetyczny (środek na cewce) albo załóż etui MagSafe.
13. **Kalibracja** – [../../docs/sterowanie-mocowaniem.md](../../docs/sterowanie-mocowaniem.md),
    najpierw bez telefonu, potem z najcięższym (Fold8 Ultra rozłożony).

## Sprawdzenie kolizji, momentu i stabilności

Wynik `tools/collision_check.py` (pełny zakres pan ±80°, tilt −25…+30°, krok
1° tilt / 5° pan, woksele 0,5 mm; każdy profil uruchamiany osobno):

| Profil | Telefon ↔ jarzmo | Głowa ↔ jarzmo | Głowa+telefon ↔ tułów | Najniższy punkt głowy | Masa głowy z telefonem (plastik) | Moment TILT | Kąt przewrócenia |
|---|---|---|---|---|---|---|---|
| S23 | > 10 mm | 2 mm* | > 10 mm | 111 mm | ≈ 646 g (254 g) | 1,23 kg·cm (13 % MG996R) | 34° |
| Fold8 Ultra rozłożony | > 10 mm | 2 mm* | > 10 mm | 86 mm | ≈ 728 g (289 g) | 1,42 kg·cm (15 %) | 34° |
| Fold8 Ultra złożony | > 10 mm | 2 mm* | > 10 mm | 110 mm | ≈ 694 g (255 g) | 1,42 kg·cm (15 %) | 34° |
| Flip6 rozłożony | > 10 mm | 2 mm* | > 10 mm | 111 mm | ≈ 654 g (243 g) | 1,25 kg·cm (13 %) | 34° |

\* skrypt raportuje 0,5 mm – to celowy luz między wałkiem serwa a tarczą orczyka
(wielowypust wchodzi w orczyk); bok kasku od tarczy orczyka dzieli 2 mm.
Szyja ↔ kołnierz: 1 mm (obrotowa szyja nad nieruchomym kołnierzem). Barki są
8 mm pod najniższym punktem rozłożonego Folda przy tilt −25°.

Założenia: wydruk ~55 % gęstości litego PETG, w głowie serwo TILT 55 g,
ładowarka 67 g, tarcza z magnesami, wentylator 10 g, oś M8 z łożyskiem ~30 g;
obciążnik 300 g w tułowiu; 6 nóżek na elipsie 187 × 180 mm. Zapas środka
ciężkości do krawędzi wielokąta podparcia ≥ 67 mm w każdej pozycji (wymagane
≥ 25° kąta przewrócenia – jest 34°). Moment statyczny to ~15 % możliwości
MG996R, więc zostaje duży zapas na szybkie gesty; serwo w głowie częściowo
wyważa telefon.

## Wymiary do zmierzenia PRZED drukiem

1. **Serwa TILT i PAN** (tabela `SERVOS`): korpus, wysokość do spodu uszu,
   grubość uszu, rozstaw otworów (wzdłuż i w poprzek), odległość osi wałka od
   końca korpusu, **odległość wierzch uszu → zewnętrzna powierzchnia tarczy
   orczyka** (`sv_ht` – od niej zależy położenie serwa w głowie i wysokość
   piasty), średnica/grubość tarczy, promienie otworów tarczy (`horn_hole_r`).
2. **Lazy susan**: średnica, wysokość (`ls_t`), otwór środkowy (`ls_id` ≥ 38 mm),
   okręgi otworów obu pierścieni.
3. **Anker 313 Pad**: Ø 100 × 11,2 mm wg dokumentacji; sprawdź **kąt gniazda
   micro-USB** (`pad_port_angle`, domyślnie 180° = w lewo) i rozmiar wtyku
   z kablem (12 mm).
4. **Telefony w etui**: wymiary z etui (`case_side`, `case_thick`) i położenie
   cewki (`PHONES[...][5..6]`), położenie gniazda USB-C (wycięcia boczne są na
   środku wysokości telefonu, ±7 mm).
5. **Moduły w tułowiu**: `esp_dim`, `xl_dim`, `lm_dim`, `pd_dim` + położenie
   gniazd USB (wycięcia w tylnej ściance tułowia, 3–14 mm nad pokrywą).
6. **Łożysko 608**, **wkładki M3** – próbki otworów (`b608_fit`, `ins_d`).
7. **TTP223** (`ttp`), **dioda** (gniazdo Ø 12 × ~8 mm), **wentylator** (rozstaw 24 mm).
8. **Magnesy**: rzeczywista średnica/grubość (`mag_d`, `mag_t`, luz `mag_fit`)
   i **pierścień na telefonie** – jego średnica powinna pokrywać się z okręgiem
   magnesów (`mag_ring_d` = 54 mm), a środek z cewką telefonu.

## Jak zmieniać parametry

- W OpenSCAD otwórz plik części → **Customizer**, albo edytuj `params.scad`.
- Linia poleceń, np. kask dla Fold8 rozłożonego:
  ```
  openscad -o head_front_fold8_open.stl -D 'profile="fold8_open"' hardware/cad/head_plate.scad
  ```
- Wszystko naraz: `OPENSCAD=/ścieżka/openscad hardware/tools/build.sh`
  (`stl`, `png` albo `all`; PNG wymaga `xvfb-run` na serwerze bez ekranu).
- Po zmianach: `python3 hardware/tools/stl_check.py hardware/stl/*.stl`
  i `python3 hardware/tools/collision_check.py` (numpy + scipy, ~15 min).
- Drukowalność (ścianki ≥ 1,2 mm, nawisy < 45°, podpory, lepsza orientacja):
  `pip install -r .claude/skills/dfam-check/requirements.txt`, potem
  `python3 hardware/tools/dfam_report.py`. GitHub Actions robi to przy każdym
  pushu (job `printability`, raport w podsumowaniu przebiegu). Skill
  `dfam-check` pochodzi z [earthtojake/text-to-cad](https://github.com/earthtojake/text-to-cad) (MIT).
- Kształt: `head_w`, `head_r`, `front_r`, `dome_back` (kask), `body_a`, `body_b`,
  `body_da` (tułów – `body_da` ≤ 29 trzyma nawisy ≤ 45°), `ear_r`, `ear_cap_r`
  (uszy), `ant_h`, `ant_ball` (antenka), `axis_above_deck` (wysokość głowy nad szyją).
- Magnesy: `mag_n`, `mag_d`, `mag_t`, `mag_ring_d`, `mag_skin` (po zmianie
  uruchom `python3 hardware/tools/magnet_check.py`).
- Nowy telefon: wiersz w `PHONES`, profil w `build.sh`. Telefon dłuższy niż
  ~172 mm w etui wymaga większego `head_w`.

## Znane ograniczenia

- Wymiary serw, lazy susan, modułów i cewek to wartości katalogowe – zmierz.
- Przewody nie idą dokładnie przez oś PAN (zajmuje ją wałek serwa), tylko łukiem
  Ø 100 z tyłu szyi – wymaga pętli serwisowej, ale nie skręca się.
- Szyja z barkami ma 231 mm rozpiętości – na stole 220 × 220 tylko po przekątnej.
- Między bokiem kasku a uszami jest 4,5 mm (tarcza orczyka 2,5 mm + 2 mm luzu); okno na serwo
  w prawym boku skorupy zasłania ucho.
- Ładowarka siedzi w kieszeni na wcisk (dociska tarczę z magnesami).
- Siła magnesów to szacunek (model, nie pomiar) – sprawdź z prawdziwym
  pierścieniem; przy słabym trzymaniu dodaj magnesy lub weź mocniejsze.
- Na Fold8 rozłożonym tarcza wystaje nad górną krawędź telefonu (cewka w górnej
  połowie) – widać ją w szczelinie 5,5 mm nad ekranem.
- Przy mniejszych telefonach (S23, Flip6, Fold8 złożony) głowa „wisi” wyżej nad
  barkami – wysokość osi jest wspólna i dobrana do rozłożonego Fold8.
