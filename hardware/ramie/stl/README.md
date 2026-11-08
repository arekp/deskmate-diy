# DeskMate na ramieniu – pliki STL (prototyp v0.1)

Każdy katalog to **jeden wydruk** na Bambu Lab P2S (stół 256 × 256 mm).
W Bambu Studio: *Plik → Importuj* → zaznacz wszystkie STL z katalogu → *Rozmieść* (A).
Części są już obrócone do druku: płaską stroną na stole.

> **Zacznij od `00_test_pasowania`.** To mały kawałek (ok. 15 min) z gniazdem łożyska 608,
> gniazdem łba M8 i otworami M3 w takich samych wymiarach jak w przegubach.
> Jeśli łożysko wchodzi za ciasno albo za luźno, ustaw w slicerze *Kompensację otworów (X-Y hole compensation)*
> i dopiero potem drukuj resztę.

## Katalogi

| Katalog | Części | Filament | Podpory |
|---|---|---|---|
| `00_test_pasowania_PETG` | 1 płytka testowa | PETG, dowolny kolor | nie |
| `01_korpus_PETG_szary` | korpus (220 × 190 × 53 mm) | PETG szary | **tak**, drzewiaste, tylko od stołu (pod sufit korpusu) |
| `02_korpus_dno_PETG_szary` | dno z komorą obciążnika (12 przegródek, głęb. 7 mm) | PETG szary | nie (górną ścianką na stole) |
| `02b_dno_piaskowe_PETG_szary` | **zamiast 02 (polecane):** cokół 22 mm wypełniany piaskiem, stopa szersza o 8% | PETG szary, **wypełnienie gyroid 6–8%**, 3 ściany, 5 warstw góra i dół | nie |
| `03_wieza_PETG_szary` | wieża: połówka lewa i prawa, talerz | PETG szary | tak, drzewiaste (wnętrze krążka barku) |
| `04_ramie_PETG_szary` | ramię: połówka lewa i prawa (ażur) | PETG szary | tak, drzewiaste |
| `05_przedramie_PETG_szary` | przedramię: połówka lewa i prawa (ażur) | PETG szary | tak, drzewiaste |
| `06_szyjka_PETG_szary` | nadgarstek z szyjką: połówka lewa i prawa | PETG szary | tak, drzewiaste |
| `07_uchwyt_telefonu_PETG_szary` | rama ładowarki, szyna z lewą szczęką, prawa szczęka, zaślepka szyny, kołnierz przechyłu | PETG szary | tak, pod szyną (wisi 3 mm nad stołem) |
| `08_akcent_pomaranczowy_PETG` | 4 pierścienie bark/łokieć, 2 pierścienie nadgarstka, 1 pierścień szyjki | PETG pomarańczowy | nie |
| `09_dekle_czarne_PETG` | 4 dekle bark/łokieć, 2 dekle nadgarstka | PETG czarny | nie |
| `10_TPU` | 2 nakładki szczęk | TPU 95A | nie |
| `../renders` | obrazy pomocnicze | – | – |

**Ustawienia, które polecam:** PETG, warstwa 0,2 mm, 4 obrysy (ściany), wypełnienie 15% gyroid,
podpory drzewiaste (tree) z opcją *tylko od stołu* tam, gdzie się da.
Połówki ramion leżą płaszczyzną podziału na stole, więc ażur drukuje się w pionie i wychodzi czysto.
Łącznie około 0,5–0,7 kg PETG.

## Co kupić

**Serwa**
- 2 × DS3218 (bark i łokieć). MG996R też wejdzie, ale jest na granicy siły.
- 2 × MG996R (nadgarstek i obrót podstawy).
- 1 × MG90S (przechył głowy).
- 4 × okrągły metalowy orczyk 25T, Ø25 mm (bark, łokieć, nadgarstek, obrót).

**Łożyska i śruby**
- 3 × łożysko 608 (8 × 22 × 7 mm).
- 3 × śruba M8 × 16 z łbem sześciokątnym (oś po stronie łożyska).
- Śruby M3:
  - 16 × M3 × 10, uszy serw (4 serwa × 4).
  - 4 × M3 × 10, talerz wieży (wkręcane promieniowo z boku).
  - 2 × M3 × 50, 4 × M3 × 45, 6 × M3 × 35, łączenie połówek.
  - 4 × M3 × 20 stożkowe, dno (wkręcane od spodu).
  - 4 × M3 × 10 stożkowe, rama z szyną.
  - 2 × M3 × 10, kołnierz przechyłu (wkręcane w szynę).
  - 1 × M3 × 12, zaślepka.
- Wkręty do orczyków: zwykle są w komplecie z orczykiem.

**Pozostałe**
- 4 × gumowa nóżka Ø10 mm.
- **Obciążnik około 800 g**, np. samoprzylepne ciężarki do felg albo kawałki płaskownika stalowego, przyklejone w przegródkach dna. Dwie środkowe przegródki mają otwory wentylacyjne, więc zostaw je puste.
- Klej CA (cyjanoakrylowy) do pierścieni i dekli.

**Elektronika:** ładowarka USB-C PD 65 W z kablem 5 A, wyzwalacz PD (CH224K lub IP2721) ustawiony na 20 V, przetwornica 20 V → 6 V 10 A, moduł USB QC3.0, przetwornica 5 V, ESP32, PCA9685, kondensator 4700 µF / 10 V, gniazdo USB-C (montaż w otworze z tyłu korpusu), ładowarka Anker 313 (z wersji A). Płytki w korpusie mocujesz na taśmę dwustronną.

Otwory pilotowe pod M3 mają Ø2,6 mm (śruba wcina się w PETG). Przeloty mają Ø3,4 mm.

## Jak to się składa

Każde ogniwo (wieża, ramię, przedramię, szyjka) to **dwie połówki: lewa i prawa**.
- **Prawa połówka** trzyma serwo. Serwo wkładasz od strony płaszczyzny podziału i przykręcasz 4 × M3 × 10 przez uszy do żeberek.
- **Lewa połówka** ma gniazdo łożyska 608, w które łożysko wciskasz od środka.
- **Połówki skręcasz** śrubami M3 wkładanymi od lewej strony. Pogłębienia na łby są w lewej połówce.

Przegub (bark, łokieć, nadgarstek) wygląda zawsze tak samo:
1. **Krążek ogniwa-rodzica** ma w środku serwo, po prawej stronie otwór Ø27 na orczyk, a po lewej łożysko 608.
2. **Widełki ogniwa-dziecka** obejmują krążek z obu stron.
   - Prawy policzek (3 mm) przykręcasz do okrągłego orczyka. Otwory nawierć Ø2 przez otwory orczyka, bo rozstaw zależy od producenta. Środkowy otwór Ø8 daje dostęp do śruby orczyka.
   - Lewy policzek (7 mm) ma gniazdo sześciokątne na łeb śruby M8 × 16. Śruba przechodzi przez policzek do łożyska i jest osią przegubu.
3. **Dekiel i pierścień** przyklejasz na zewnątrz policzków.

**Kolejność dla jednego przegubu:**
1. Łożysko do lewej połówki rodzica.
2. Serwo do prawej połówki rodzica.
3. Ustaw serwo w pozycji środkowej (90°) i załóż orczyk na wałek. Prawy policzek dziecka przykręć do orczyka.
4. Połóż lewą połówkę rodzica i włóż śrubę M8 przez lewy policzek dziecka do łożyska.
5. Skręć połówki rodzica, a potem połówki dziecka.

**Wieża:**
1. Talerz przykręcasz do orczyka serwa obrotu (serwo w korpusie, przykręcone od spodu do słupków).
2. Połówki wieży z serwem barku nakładasz fartuchem na talerz.
3. Wieżę mocujesz 4 śrubami M3 z boku.
4. Kable przechodzą przez otwór w talerzu i łukową szczelinę w korpusie, co pozwala na obrót ±90°.

**Szyjka:** MG90S jest zaciśnięte w gnieździe między połówkami szyjki, bez śrub. Jego orczyk przykręcasz do kołnierza przechyłu, a kołnierz 2 × M3 × 10 do szyny.

**Ustawienie ładowarki pod cewkę telefonu:** rama ładowarki jest przykręcona do szyny przez podłużne otwory, które pozwalają przesunąć ją o ±8 mm w bok.
1. Włóż telefon w szczęki.
2. Przesuwaj ramę z ładowarką, aż telefon zacznie się ładować.
3. Dokręć 4 śruby.

Cewka w telefonie zwykle nie leży na środku jego długości, a źle ustawiona ładowarka grzeje się i ładuje słabo. Pomysł pochodzi z „Cyber Base”.

## Stabilność i limity ruchu (v0.2)

Bez obciążnika robot przewróci się do przodu przy szybkim geście z głową wysuniętą, np. w pozach „Ciekawość” i „Klawiatura”. Szczegóły są w [../porownanie-so101.md](../porownanie-so101.md).
- **Obciążnik, wariant A (polecany):** wydrukuj `02b_dno_piaskowe` zamiast `02`. Działa jak podstawa SO-ARM101 „Weighted Base”.
  1. Wydrukuj cokół z rzadkim gyroidem (6–8%).
  2. Nawierć Ø8 w jednym z dwóch znaczników na górnej ściance.
  3. Wsyp suchy piasek do pełna, około 1 kg, i opukuj, żeby się ułożył.
  4. Zalej otwór żywicą albo klejem na gorąco.
  5. Na spód naklej piankę albo filc samoprzylepny.

  Korpus przykręcasz od spodu 4 × M3 × 20. Szczeliny wentylacyjne są przelotowe, a slicer zrobi wokół nich ścianki, więc piasek się nie wysypie.
- **Obciążnik, wariant B:** dno `02` z około 800 g stali w przegródkach.
- **Limity w programie** (ESP32 albo aplikacja):
  - wysięg głowy najwyżej 200 mm przed osią obrotu,
  - powyżej 170 mm prędkość ograniczona do połowy,
  - gesty „Radość” i „Nie” tylko przy wysięgu poniżej 150 mm.

## Zmiany względem wizualizacji

- **Podział połówek:** ogniwa dzielą się na lewą i prawą połówkę zamiast na przednią i tylną. Każdy policzek widełek jest wtedy w jednym kawałku, a serwo przykręca się do jednej połówki.
- **Łożysko w nadgarstku:** wszystkie trzy przeguby mają łożysko 608, także nadgarstek.
- **Oś przegubu:** zamiast trzpienia z druku jest stalowa śruba M8 × 16.
- **Bark wyżej:** oś barku jest 6 mm wyżej (98 mm nad spodem korpusu), żeby ucho serwa barku nie zahaczało o talerz wieży. Wysokość dochodzi też z dna (9 mm z komorą obciążnika).
- **Szersze końce ogniw:** ramię i przedramię są przy krążkach szersze, 50 mm zamiast 44–46 mm. Inaczej ścianka przy serwie miałaby poniżej 1 mm.
- **Szerokość przegubu:** z policzkami wynosi 70 mm.

## Ograniczenia tej wersji

- **To pierwszy prototyp i nie był drukowany.**
  - Każdy plik sprawdziłem pod kątem szczelności siatki.
  - Sprawdziłem w modelu, że serwa i łożyska mieszczą się w krążkach i nie zahaczają o talerz.
  - Pasowań na prawdziwym wydruku nie sprawdzałem. Do tego służy płytka testowa.
- **Wymiary serw** wziąłem z typowych kart katalogowych: korpus MG996R 40,7 × 19,7 mm, uszy na wysokości 26,7 mm od dna, rozstaw otworów 49,5 × 10 mm. Klony różnią się o około 1 mm. Jeśli ucho nie sięga żeberka, podłóż podkładkę.
- **Krzywizny:** bryły powstały z wokseli (0,35 mm, korpus 0,5 mm), więc na dużych krzywiznach mogą być widoczne delikatne schodki. Można je przeszlifować.
- **Brak osłony tyłu głowy:** siatkowej osłony nie ma w tej wersji.
- **Elektronika:** mocowania płytek nie są wymodelowane, płytki mocujesz na taśmę dwustronną.
