# Telefony: Galaxy S23, Z Fold8 Ultra, Z Flip6

Projekt jest przygotowany pod trzy modele Samsunga. Aplikacja rozpoznaje telefon automatycznie (`DeviceProfile.kt` – po kodzie modelu `Build.MODEL`) i dopasowuje pole widzenia kamery oraz wielkość oczu.

## Porównanie

| | Galaxy S23 | Galaxy Z Fold8 Ultra | Galaxy Z Flip6 |
|---|---|---|---|
| Wymiary | 146,3 × 70,9 × 7,6 mm¹ | rozłożony 158,4 × 143,2 × 4,1 mm; złożony 158,4 × 72,8 × 8,9 mm | rozłożony 165,1 × 71,9 × 6,9 mm; złożony 85,1 × 71,9 × 14,9 mm¹ |
| Masa | ok. 168 g¹ | 215 g | ok. 187 g¹ |
| Ekran(y) | 6,1" | 8,0" wewnętrzny, 6,5" zewnętrzny | 6,7" wewnętrzny, 3,4" zewnętrzny¹ |
| Ładowanie bezprzewodowe | 15 W tylko z ładowarką Samsunga, z innymi 10 W | do 20 W (Qi2.2) | 15 W |
| Magnesy Qi2 w telefonie | nie | **nie** – wymaga magnetycznego etui | nie |
| Kod modelu (prefiks) | SM-S911 | do potwierdzenia na telefonie² | SM-F741 |

¹ Wartości z pamięci/specyfikacji producenta – zmierz telefon w etui przed projektowaniem uchwytu.
² Ustawienia → Informacje o telefonie → Model. Jeśli różni się od prefiksów w `DeviceProfile.kt`, dopisz go tam.

## Mocowanie ładujące – rekomendacja

Żaden z trzech telefonów nie ma wbudowanych magnesów, więc najlepszy jest **jeden uniwersalny układ**:

1. **Magnetyczna ładowarka Qi2 typu „krążek”** wbudowana w płytkę głowy, np. [ESR HaloLock Qi2 Mini 15 W (homescreen.pl)](https://homescreen.pl/pl/ladowarki-indukcyjne/76006-ladowarka-indukcyjna-esr-halolock-qi2-mini-magnetic-magsafe-15w-black-4894240189238.html). Jest mała i płaska, więc łatwo ją osadzić w wydruku 3D.
2. **Magnetyczne etui na każdy telefon**:
   - Fold8 Ultra – oryginalne etui Samsung z magnesem (np. [Silicone Magnet Case EF-EF976](https://www.samsung.com/ie/mobile-accessories/galaxy-z-fold8-ultra-silicone-magnet-case-violet-shadow-ef-ef976cvegww/)); z takim etui telefon spełnia Qi2.
   - S23 i Flip6 – etui „MagSafe compatible” z pierścieniem magnetycznym (np. dla Flip6 [takie](https://www.bestbuy.ca/en-ca/product/hexinyigjly-case-for-galaxy-z-flip-6-with-metal-ring-magsafe-compatible-black/19959356) – poszukaj odpowiednika w polskim sklepie).

Zalety: telefon przypinasz i zdejmujesz jednym ruchem, płytka głowy jest wspólna dla wszystkich, a środek pierścienia wyznacza położenie cewki.

Alternatywa: zasilanie **przewodem USB-C** poprowadzonym przez głowę – mniej ciepła i pewniejsze ładowanie, ale trzeba trafić w gniazdo i przewód ogranicza obrót.

## Uwagi dla każdego modelu

### Galaxy S23
- Najprostszy przypadek: płaski, lekki, poziomo cały ekran to twarz.
- Z ładowarką inną niż Samsunga ładuje się do 10 W – przy ciągłej pracy ekranu, kamery i AI to „podtrzymanie”, a nie szybkie ładowanie.

### Galaxy Z Fold8 Ultra
- **Tryb rozłożony** – duży, prawie kwadratowy ekran 8" daje najlepsze oczy. To najcięższa i najszersza konfiguracja: wymaga mocniejszego serwa TILT ([sterowanie-mocowaniem.md](sterowanie-mocowaniem.md)).
- **Tryb złożony** – zachowuje się jak zwykły smartfon z ekranem 6,5".
- Kamera: po rozłożeniu aplikacja powinna używać kamery przedniej po stronie dużego ekranu. CameraX wybiera „przednią” kamerę automatycznie – sprawdź na telefonie, czy to ta właściwa (zadanie w roadmapie).
- Magnes etui jest na tylnej połowie – zaprojektuj płytkę tak, żeby rozłożony telefon był wyważony względem osi TILT.

### Galaxy Z Flip6
- Samsung zaleca ładować Flipa bezprzewodowo **po złożeniu**, środkiem tyłu na środku ładowarki. Przy pracy rozłożonej ładowanie bezprzewodowe może nie działać – wtedy użyj przewodu albo ładuj w przerwach.
- **Ciekawa opcja**: Flip złożony z oczami na **zewnętrznym ekranie 3,4"** (wymaga dopuszczenia aplikacji na ekranie okładki, np. przez Good Lock / MultiStar) – mały, uroczy robot, który przy okazji się ładuje. W roadmapie.
- Tryb Flex (zgięty pod kątem) to kolejna możliwość: dolna połowa w uchwycie, górna jako twarz.

## Ciepło i bateria

Ciągła praca ekranu, kamery i rozpoznawania mowy plus ładowanie bezprzewodowe grzeją telefon. Zalecenia:

- w ustawieniach baterii włącz ochronę (limit ładowania 80%),
- zostaw szczelinę wentylacyjną między telefonem a płytką,
- zmniejsz jasność ekranu (czarne tło oczu na AMOLED-zie prawie nie zużywa energii),
- usypiaj robota, gdy nikogo nie ma (robi to automatycznie po 60 s).

## Źródła

- [GSMArena – Galaxy Z Fold8 Ultra](https://m.gsmarena.com/samsung_galaxy_z_fold8_ultra-ampp-14802.php)
- [SammyGuru – Fold8/Fold8 Ultra i Qi2](https://sammyguru.com/galaxy-z-fold-8-fold-8-ultra-flip-8-qi2-charging/)
- [SamMobile – ładowanie bezprzewodowe Galaxy S23](https://www.sammobile.com/news/galaxy-s23-slower-wireless-charging-speed-galaxy-s22)
- [Android Central – ładowanie bezprzewodowe Z Flip6](https://androidcentral.com/phones/does-samsung-galaxy-z-flip-6-support-wireless-charging)
- [Samsung – jak ładować Galaxy Z Flip bezprzewodowo](https://www.samsung.com/uk/support/mobile-devices/how-to-properly-charge-the-galaxy-z-flip-using-wireless-charger/)
