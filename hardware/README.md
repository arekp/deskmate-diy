# Sprzęt i obudowa

Tu trafią pliki obudowy (STEP/STL/3MF) i schematy. Na razie – założenia projektowe.

## Moduły

1. **Podstawa** – mieści ESP32, przetwornice, serwo PAN; na górze łożysko obrotowe. Ciężka (obciążnik), z gumowymi stopkami.
2. **Jarzmo** – obraca się na łożysku; po jednej stronie serwo TILT, po drugiej łożysko kulkowe osi.
3. **Płytka głowy** – wymienna; w środku gniazdo na krążek Qi2, otwory wentylacyjne, czujnik dotyku od góry.

## Wymagania

- Oś TILT jak najbliżej środka ciężkości telefonu w etui (dla rozłożonego Fold8 Ultra – środek tylnej połowy).
- Zakres ruchu: obrót ±80°, pochylenie −25°…+30° bez kolizji z podstawą przy największym telefonie.
- Kanał na przewody przez oś obrotu (pętla serwisowa, bez skręcania przewodu ponad zakres).
- Szczelina 2–3 mm między płytką a telefonem na wentylację, poza obszarem magnesu.
- Druk z PETG; wkładki gwintowane M3 w miejscach montażu.

Wymiary telefonów: [../docs/telefony.md](../docs/telefony.md) · dobór serw: [../docs/sterowanie-mocowaniem.md](../docs/sterowanie-mocowaniem.md)
