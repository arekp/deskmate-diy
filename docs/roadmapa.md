# Roadmapa

Kolejność proponowana – od rzeczy, które najbardziej poprawiają codzienne używanie.

## v0.2 – solidne podstawy
- [ ] Przetestować i skalibrować śledzenie na S23, Fold8 Ultra (rozłożony/złożony) i Flip6; uzupełnić kody modeli i pola widzenia w `DeviceProfile.kt`.
- [ ] Fold8 Ultra: wybór właściwej kamery przedniej po rozłożeniu (CameraX `CameraSelector` po `LENS_FACING` + rozmiarze ekranu).
- [ ] Wykrywanie głosu (VAD) zamiast samego „patrzenia w kamerę” – mniej pustych nasłuchów, szybsza reakcja.
- [ ] Przerywanie robota w trakcie mówienia (barge-in).
- [x] Ekran ustawień: klucz API, model, pomocnik ekranu (przytrzymaj palec na oczach).
- [ ] Ustawienia zaawansowane: czułość śledzenia, odwrócenie osi, głos.
- [ ] Auto-start po postawieniu telefonu na robocie (połączenie BLE / ładowanie).

## v0.3 – asystent pracy
- [ ] Narzędzie Gmail (podsumowania, szkice – wysyłka tylko po potwierdzeniu głosowym).
- [ ] Narzędzie Kalendarz Google (plan dnia, przypomnienia przed spotkaniami).
- [ ] Tryb skupienia (Pomodoro): robot milknie, a po przerwie zagaduje.
- [ ] Schowek PC ↔ robot w pomocniku `screen_helper.py`.
- [ ] Trwała pamięć z podglądem i przyciskiem „zapomnij wszystko”.

## v0.4 – osobowość i sprzęt
- [ ] Więcej emocji i animacji oczu (Rive/Lottie), reakcja na uśmiech użytkownika.
- [ ] Trzecia oś (przechył głowy) – „przekrzywienie z ciekawości”.
- [ ] Cichsze napędy: silniki gimbalowe BLDC + SimpleFOC albo serwa magistralowe Feetech STS.
- [ ] Flip6: tryb „mały robot” na ekranie zewnętrznym 3,4" (złożony, ładuje się).
- [ ] Hub USB w podstawie.
- [ ] Obudowa w CAD (FreeCAD/Onshape) z wymiennymi płytkami pod każdy telefon.

## Pomysły na później
- Lokalny model mowy (Whisper) i lokalny LLM dla trybu offline.
- Integracja z Home Assistant (masz już projekty ESPHome – robot jako panel głosowy domu).
- Aplikacja iOS (ten sam protokół BLE).
