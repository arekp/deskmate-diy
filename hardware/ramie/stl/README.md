# DeskMate na ramieniu – pliki do druku (prototyp v0.2)

Drukarka: **Bambu Lab P2S** (stół 256 × 256 mm). Każdy katalog to **jeden wydruk**.
Masz dwie drogi:

- **3MF (polecane):** w katalogu [`../3mf`](../3mf) jest gotowy projekt na każdy stół, z rozmieszczonymi częściami.
  1. Otwórz plik w Bambu Studio.
  2. Wybierz drukarkę **P2S** i profil **PETG** (Bambu PETG Basic albo inny PETG).
  3. Pokrój i drukuj.
- **STL:**
  1. *Plik → Importuj*.
  2. Zaznacz wszystkie STL z katalogu.
  3. *Rozmieść* (A).

  Części są już obrócone do druku.

> **Zacznij od `00_test_pasowania`** (ok. 15 min). Płytka ma w takich samych wymiarach jak w przegubach:
> - gniazdo łożyska 608,
> - sześciokątne gniazdo łba M8 z miejscem na czop gałki,
> - otwór Ø8 na czop gałki,
> - otwory M3: pilotowe Ø2,6 i przelotowe Ø3,4.
>
> Jeśli łożysko wchodzi za ciasno albo za luźno, popraw w Bambu Studio *Kompensację otworów X-Y* (X-Y hole compensation) i dopiero potem drukuj resztę.

## Katalogi

| Katalog / 3MF | Części | Filament | Podpory i uwagi |
|---|---|---|---|
| `00_test_pasowania_PETG` | płytka testowa | PETG | nie |
| `01_korpus_PETG_szary` | korpus 220 × 190 × 53 mm | PETG szary | **tak**, drzewiaste, *tylko od stołu* (pod sufit) |
| `02b_dno_piaskowe_PETG_szary` | **polecane:** cokół 22 mm na piasek | PETG szary, **gyroid 6–8%**, 3 ściany, 5 warstw góra i dół | nie |
| `02_korpus_dno_PETG_szary` | zamiennik: dno z przegródkami na stal | PETG szary | nie, górną ścianką na stole |
| `03_wieza_PETG_szary` | połówka lewa i prawa, talerz | PETG szary | tak, drzewiaste; **brim 5 mm** pod połówki |
| `04_ramie_PETG_szary` | połówka lewa i prawa, ażur Voronoi, rowki na listwy | PETG szary (+ wkładki w kolorze 2) | tak, drzewiaste; **brim 5 mm** |
| `05_przedramie_PETG_szary` | połówka lewa i prawa, ażur, napis „DeskMate” i rowek na listwę | PETG szary (+ wkładki w kolorze 2) | tak, drzewiaste; **brim 5 mm** |
| `06_szyjka_PETG_szary` | nadgarstek z szyjką, połówka lewa i prawa | PETG szary | tak, drzewiaste |
| `07_uchwyt_telefonu_PETG_szary` | rama ładowarki, szyna z lewą szczęką, prawa szczęka, zaślepka, kołnierz przechyłu | PETG szary | tak, tylko pod szyną (wisi 3 mm nad stołem) |
| `08_akcent_pomaranczowy_PETG` | 7 pierścieni, 3 listwy do wklejenia | PETG pomarańczowy | nie |
| `09_galki_czarne_PETG` | 6 gałek z radełkiem: bark, łokieć, nadgarstek × lewa i prawa | PETG czarny | nie, drukują się górą na stole |
| `10_TPU` | 2 nakładki szczęk | TPU 95A | nie |
| `_wklady_wielokolor` | wkładki listew i napisu do druku wielokolorowego (używane w 3MF) | – | – |

**Polecane ustawienia (PETG):**
- warstwa 0,2 mm,
- 4 ściany, 5 warstw góra i dół,
- wypełnienie 15% gyroid (cokół 6–8%),
- dysza 0,4 mm,
- podpory drzewiaste (tree, auto) z opcją *tylko od stołu* tam, gdzie się da.

Łącznie około 0,7–0,9 kg PETG, cokół jest najcięższy.

### Kolory i wkładki (AMS)

W `04_ramie.3mf` i `05_przedramie.3mf` każda połówka ma drugą część „…_wklad” przypisaną do **filamentu 2**:
- czerwone listwy w rowkach,
- napis „DeskMate” na przedramieniu.

**Z AMS:** ustaw w Bambu Studio filament 1 na szary, a filament 2 na pomarańczowy albo czerwony. Wkładki wydrukują się w rowkach od razu.

**Bez AMS:**
1. Usuń wkładki z obiektów albo przypisz je do filamentu 1.
2. Listwy wydrukuj osobno z `08_akcent_pomaranczowy` i wklej w rowki 3,5 × 0,8 mm. Wystają wtedy 0,6 mm.
3. Wgłębiony napis (0,6 mm) pomaluj farbą akrylową albo markerem.

## Co kupić

**Serwa**
- 2 × DS3218 (bark, łokieć),
- 2 × MG996R (nadgarstek, obrót podstawy),
- 1 × MG90S (przechył głowy),
- 4 × okrągły metalowy orczyk 25T, Ø25 mm.

**Łożyska i śruby**
- 3 × łożysko 608 (8 × 22 × 7 mm).
- **3 × śruba M8 × 12** z łbem sześciokątnym (DIN 933), jako oś przegubu po stronie łożyska. **Uwaga:** w v0.1 była podana M8 × 16, a ona weszłaby w serwo. Poprawne jest M8 × 12.
- Śruby M3:
  - 16 × M3 × 10, uszy serw,
  - 4 × M3 × 10, talerz wieży, wkręcane promieniowo,
  - 2 × M3 × 50, 4 × M3 × 45, 6 × M3 × 35, łączenie połówek,
  - 4 × M3 × 20 stożkowe, korpus do cokołu albo dna, od spodu,
  - 4 × M3 × 10 stożkowe, rama ładowarki do szyny,
  - 2 × M3 × 10, kołnierz przechyłu do szyny,
  - 1 × M3 × 12, zaślepka.
- Wkręty do orczyków: zwykle są w komplecie z orczykiem.

**Pozostałe**
- około 1 kg suchego piasku i żywica albo klej na gorąco (cokół) albo około 800 g stali (dno 02),
- 4 gumowe nóżki Ø10 albo pianka samoprzylepna na spód,
- klej CA do pierścieni i listew.

**Elektronika:**
- ładowarka USB-C PD 65 W z kablem 5 A,
- wyzwalacz PD (CH224K lub IP2721) ustawiony na 20 V,
- przetwornica 20 V → 6 V 10 A,
- moduł USB QC3.0,
- przetwornica 5 V,
- ESP32 i PCA9685,
- kondensator 4700 µF / 10 V,
- gniazdo USB-C,
- ładowarka Anker 313.

Płytki mocujesz w korpusie na taśmę dwustronną.

Otwory pilotowe M3 mają Ø2,6 mm (śruba wcina się w PETG), a przelotowe Ø3,4 mm.

## Jak to się składa

Każde ogniwo (wieża, ramię, przedramię, szyjka) to **dwie połówki: lewa i prawa**.
- **Prawa połówka** trzyma serwo. Wkładasz je od strony płaszczyzny podziału i przykręcasz 4 × M3 × 10 przez uszy do żeberek.
- **Lewa połówka** ma gniazdo łożyska 608, w które łożysko wciskasz od środka.
- **Połówki skręcasz** śrubami M3 wkładanymi od lewej strony.

Przegub (bark, łokieć, nadgarstek):
1. **Krążek ogniwa-rodzica** ma w środku serwo, po prawej stronie otwór Ø27 na orczyk, a po lewej łożysko 608.
2. **Widełki ogniwa-dziecka** obejmują krążek z obu stron.
   - Prawy policzek (3 mm) przykręcasz do orczyka. Otwory nawierć Ø2 przez otwory orczyka. Środkowy otwór Ø8 daje dostęp do śruby orczyka.
   - Lewy policzek (9 mm) ma sześciokątne gniazdo, w którym chowa się łeb śruby M8 × 12. Śruba przechodzi przez policzek do łożyska i jest osią.
3. **Gałki z radełkiem:**
   - prawa wchodzi czopem Ø7,8 w otwór Ø8, a od spodu ma wnękę na łby wkrętów orczyka,
   - lewa wchodzi sześciokątnym czopem w gniazdo nad łbem M8.

   Obie są wciskane, więc prawą da się zdjąć, żeby dokręcić orczyk. Jeśli siedzą luźno, dodaj kroplę kleju na gorąco.
4. **Pierścienie** przyklejasz na policzki dookoła gałek.

**Kolejność dla jednego przegubu:**
1. Łożysko do lewej połówki rodzica.
2. Serwo do prawej połówki rodzica.
3. Ustaw serwo w pozycji środkowej (90°) i załóż orczyk. Przykręć do niego prawy policzek dziecka.
4. Połóż lewą połówkę rodzica i włóż śrubę M8 × 12 przez lewy policzek dziecka do łożyska.
5. Skręć połówki rodzica, potem połówki dziecka, a na koniec wciśnij gałki.

**Wieża:**
1. Talerz przykręcasz do orczyka serwa obrotu (serwo w korpusie, przykręcone od spodu do słupków).
2. Połówki wieży nakładasz fartuchem na talerz.
3. Wieżę mocujesz 4 śrubami M3 × 10 z boku.

Kable przechodzą przez otwór w talerzu i łukową szczelinę w korpusie, co pozwala na obrót ±90°.

**Szyjka:** MG90S jest zaciśnięte między połówkami szyjki, bez śrub. Jego orczyk przykręcasz do kołnierza przechyłu, a kołnierz 2 × M3 × 10 do szyny.

**Ładowarka pod cewkę telefonu:** rama ładowarki jest przykręcona do szyny przez podłużne otwory, które pozwalają przesunąć ją o ±8 mm w bok.
1. Włóż telefon.
2. Przesuwaj ramę, aż telefon zacznie się ładować.
3. Dokręć 4 śruby.

## Stabilność i limity ruchu

Bez obciążnika robot przewraca się do przodu przy szybkim geście z wysuniętą głową ([../porownanie-so101.md](../porownanie-so101.md)).

**Cokół z piaskiem (`02b`):**
1. Drukuj z gyroidem 6–8%.
2. Nawierć Ø8 w jednym ze znaczników na górnej ściance.
3. Wsyp około 1 kg suchego piasku, opukaj i zalej otwór żywicą albo klejem na gorąco.
4. Na spód naklej piankę.

**Limity w programie:** wysięg głowy najwyżej 200 mm, powyżej 170 mm połowa prędkości, a energiczne gesty tylko poniżej 150 mm.

## Kontrola przed drukiem (v0.2)

Każdy plik zmierzyłem skryptem [`../generator/dfam_lite.py`](../generator/dfam_lite.py). Repo ma też umiejętność `dfam-check`, ale potrzebuje bibliotek `trimesh`, `rtree`, `networkx` i `lxml`, których nie dało się tu zainstalować, więc pomiary są z własnego skryptu.

**Siatki:**
- **Szczelność:** wszystkie siatki są zamknięte, każda krawędź należy do dwóch trójkątów.
- **Normalne:** w v0.1 normalne części z wokseli były odwrócone („na lewą stronę”). Bambu Studio zwykle to naprawia, ale w v0.2 jest to poprawione u źródła.

**Grubość ścianek:** mierzona na wokselach, z których powstały części.
- 5. percentyl wynosi od 1,2 mm (gałka nadgarstka, równo z limitem dla FDM 1,2 mm) do kilku milimetrów. W ogniwach, wieży i korpusie jest to 1,6–2,1 mm.
- Pojedyncze minima 0,3–0,7 mm to krawędzie wokseli na brzegach, nie cienkie ścianki.
- W gałce nadgarstka v0.2 pogrubiłem podstawę, bo wnęka na wkręty zostawiała 0,6 mm.

**Orientacja:** sprawdziłem 6 ustawień każdej części.
- **Zmienione:**
  - talerz wieży leży teraz górą na stole (nawisy spadły z 6 do 1 cm²),
  - prawa szczęka leży zewnętrzną ścianką na stole (z 11 do 0,6 cm², bez podpór).
- **Ramię, przedramię i szyjka:** zostają płaszczyzną podziału na stole. Ułożenie „do góry nogami” dałoby mniej podpór, ale rowki, napis i powierzchnie łączenia połówek wyszłyby gorzej.
- **Korpus:** zostaje pionowo, bo każde inne ustawienie ma ponad 190 mm wysokości. Wnętrze wymaga podpór (około 340 cm² nawisów).

**Wymiary:** wszystkie części i każdy 3MF mieszczą się na stole 256 × 256 mm.

**Czego ta kontrola nie obejmuje:**
- minimalnej średnicy otworów i mostów,
- realnej wytrzymałości,
- pasowań na prawdziwym wydruku, do czego służy płytka testowa.

## Ograniczenia

- **Prototyp nie był drukowany.**
- **Wymiary serw** pochodzą z kart katalogowych: MG996R 40,7 × 19,7 mm, uszy 26,7 mm od dna, otwory co 49,5 × 10 mm. Klony różnią się o około 1 mm, więc w razie potrzeby podłóż podkładkę pod ucho.
- **3MF:** pliki zapisałem w strukturze Bambu Studio (obiekty, części, filamenty, jeden stół), ale nie mogłem ich tu otworzyć w samym Bambu Studio. Jeśli któryś się nie wczyta, użyj STL z tego samego katalogu.
- **Krzywizny:** bryły powstały z wokseli (0,35 mm), więc na krzywiznach mogą być widoczne delikatne schodki.
- **Brakujące elementy:**
  - osłona tyłu głowy,
  - mocowania płytek elektroniki.
