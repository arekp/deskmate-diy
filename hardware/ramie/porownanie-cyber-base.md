# Analiza „Cyber Base” (ręczne ramię na telefon) pod kątem DeskMate

Źródło: plik projektu Bambu Studio (`ramie.3mf`, 56 części na 5 stołach) i instrukcja PDF (15 kroków), dostarczone przez właściciela.
Plików tego modelu **nie dodaję do repo**, bo to cudzy projekt. Poniżej są tylko pomiary i wnioski.

## Co to jest

Biurkowy uchwyt na telefon w stylu cyber, bez silników, ustawiany ręką:
- **Podstawa:** 180 × 125 × 20 mm z tacką na drobiazgi i tarczą obrotu Ø80 mm.
- **Przeguby:** bark, łokieć i nadgarstek to zapadki z drukowanymi sprężynkami w kształcie litery S. Mocniej trzymają w stronę grawitacji, a w drugą obracają się lżej. Na osiach są gałki z radełkiem Ø48, Ø44 i Ø34 mm.
- **Ogniwa:** płaskie płytki L i P z pierścieniami zapadek na końcach. Rozstaw osi wynosi **około 81 mm**, czyli mniej niż u nas (110 i 100 mm). Osłony ogniw (40–47 × 74–82 × 20,5 mm) są klejone z połówek.
- **Głowa:**
  - przegub kulowy Ø20 mm z nakrętką zaciskową Ø35,
  - płyta 86 × 108 mm z ładowarką bezprzewodową **przesuwaną na śrubach pod cewkę telefonu**,
  - na dole dwa czerwone haczyki-półki, na których stoi telefon.
- **Kabel:** idzie na zewnątrz, w rowkach przykrytych zatrzaskowymi listwami.
- **Montaż:** na klej i kołki sześciokątne, bez śrub. Za ciasne pasowanie poprawia się, grzejąc otwór zapalniczką, a za luźne klejem.
- **Styl:**
  - segmentowe panele z rowkami,
  - czerwone akcenty na ciemnym tle,
  - napis „Cyber Base” na nadgarstku,
  - doklejane ozdobne listwy i nakładki (krok 15).

## Co przejmuję

1. **Przesuwana ładowarka pod cewkę.** Zrobione w v0.2: rama ładowarki ma podłużne otwory ±8 mm i przesuwa się względem szyny ze szczękami. Cewki telefonów, także S23 Ultra, nie leżą dokładnie na środku, a w uchwycie poziomym wychodzi to jako przesunięcie w bok. Śruby kołnierza przechyłu idą teraz tylko w szynę (2 × M3 × 10), żeby nie blokowały przesuwu.
2. **Stylistyka do rozważenia na v0.3:**
   - gałki z radełkiem zamiast płaskich dekli,
   - wycięte rowki paneli,
   - czerwone listwy ozdobne,
   - napis „DeskMate” na nadgarstku.

   To można dodać do generatora, a ażur Voronoi zostaje na ramieniu i przedramieniu.
3. **Kabel na zewnątrz w listwach.** Gdyby kabel w środku ażuru przeszkadzał przy obrocie przegubów, zrobimy rowek z zatrzaskową listwą wzdłuż ogniwa, tak jak tutaj.

## Czego nie przejmuję i dlaczego

- **Zapadki ze sprężynkami S** są dobre do ręcznego ustawiania. Z serwem dałyby szarpany, klikający ruch, a serwo musiałoby pokonywać zapadkę przy każdym kroku. Ich sens, czyli większy opór w stronę grawitacji, lepiej u nas zrealizować sprężyną w barku, jak w lampce. To zostaje w planie.
- **Przegub kulowy** daje ręczną regulację, a my mamy przechył z serwem.
- **Klejenie zamiast śrub:** nasze przeguby muszą dać się rozkręcić, żeby wymienić serwo, więc zostajemy przy śrubach.
- **Krótsze ogniwa (81 mm):**
  - zmniejszyłyby moment na barku o około 20–25% i poprawiły stabilność,
  - ale zmniejszyłyby też zasięg i wyrazistość ruchów.

  Jeśli prototyp okaże się za ciężki dla serw, to pierwsza rzecz do zmiany: na przykład ramię 95 mm i przedramię 85 mm.

## Uwaga do druku

Autor zaznacza, że części przegubów trzeba drukować ze zwykłego PLA albo PETG, nie z matowego, „silk” ani przezroczystego, bo sprężynki pękają. U nas to samo dotyczy widełek i krążków: zwykły PETG.
