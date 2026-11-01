# Montaż elektroniki

## Schemat połączeń (ESP32-S3)

```
Zasilacz USB-C PD ──► PD trigger (12 V)
                        │
            ┌───────────┴─────────────┐
            ▼                         ▼
   Step-down 6 V / 3 A+        Step-down 5 V / 3 A
   (tylko serwa)               (ESP32 + ładowarka Qi2)
     │   │                        │          │
     │   └─ 1000 µF ─┐            │          └─► USB-C krążka Qi2 *
     ▼               ▼            ▼
  Serwo PAN       Serwo TILT    ESP32-S3 (pin 5V)
  sygnał ◄─ GPIO4  sygnał ◄─ GPIO5
                               TTP223: VCC ◄─ 3V3, OUT ─► GPIO6

  WSZYSTKIE GND połączone razem!
```

\* Ładowarki Qi2 zwykle potrzebują zasilacza USB-C PD (np. 9 V) do pełnej mocy 15 W; przy samym 5 V naładują wolniej. Najprościej: zasilacz z **dwoma** gniazdami USB-C – jedno do krążka Qi2, drugie do PD triggera robota.

## Kolejność

1. Ustaw napięcia przetwornic multimetrem **przed** podłączeniem czegokolwiek (6,0 V dla serw, 5,0 V dla ESP32).
2. Podłącz ESP32 tylko przez USB komputera, wgraj firmware, sprawdź `P` → `PONG`.
3. Podłącz serwa (bez ramion), zasilanie z przetwornicy, sygnały do GPIO. Sprawdź `H`, `G nod`.
4. Załóż ramiona serw w pozycji środkowej (patrz kalibracja w [sterowanie-mocowaniem.md](sterowanie-mocowaniem.md)).
5. Zamontuj mechanikę i krążek Qi2, potem telefon.

## Częste problemy

| Objaw | Przyczyna | Rozwiązanie |
|---|---|---|
| ESP32 restartuje się przy ruchu | serwa zasilane z ESP32 / spadki napięcia | osobna przetwornica, kondensator 1000 µF, wspólna masa |
| Serwo drży w spoczynku | zakłócenia, za słabe zasilanie | krótsze przewody sygnałowe, lepsza przetwornica |
| Telefon nie ładuje się | cewka nie jest w środku krążka | dopasuj etui/pierścień; Flip6 – ładuj złożony |
| Telefon nie znajduje robota | brak uprawnień Bluetooth / lokalizacji | nadaj uprawnienia, sprawdź w `tools/ble_console.py`, czy ESP32 się rozgłasza |
