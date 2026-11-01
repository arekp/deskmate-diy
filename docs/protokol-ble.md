# Protokół BLE telefon ↔ robot

Prosty protokół tekstowy (ASCII), łatwy do debugowania – te same komendy działają przez port szeregowy (115200 bodów).

## Usługa GATT

| Element | UUID | Właściwości |
|---|---|---|
| Usługa DeskMate | `4d7d0001-8a3e-4b8f-9c1a-2f6d5e7a9b10` | – |
| Komendy (telefon → robot) | `4d7d0002-8a3e-4b8f-9c1a-2f6d5e7a9b10` | Write, Write Without Response |
| Status (robot → telefon) | `4d7d0003-8a3e-4b8f-9c1a-2f6d5e7a9b10` | Read, Notify |

Nazwa rozgłoszeniowa: `DeskMate-DIY`. Telefon szuka urządzenia po UUID usługi.

## Komendy

Jedna komenda w linii. Jeden zapis może zawierać kilka komend oddzielonych `\n` lub `;`. Maks. 63 znaki na komendę. Kąty w stopniach, liczby dziesiętne z kropką.

| Komenda | Parametry | Odpowiedź | Opis |
|---|---|---|---|
| `L <pan> <tilt> [ms]` | pan −80…80, tilt −25…30, ms 20…5000 (domyślnie 400) | brak | Płynnie spójrz w punkt. Przerywa bieżący gest. Wysyłana często przy śledzeniu twarzy. |
| `G <gest>` | `nod`, `shake`, `happy`, `sad`, `curious`, `sleep`, `wake` | `OK` / `ERR …` | Gest względem bieżącego kierunku patrzenia. |
| `H` | – | `OK` | Powrót do środka. |
| `I <0\|1>` | – | `OK` | Mikro-ruchy w spoczynku wył./wł. |
| `S` | – | `S <pan> <tilt> idle=<0\|1> sleep=<0\|1>` | Stan. |
| `P` | – | `PONG` | Test łączności. |
| `?` | – | lista komend | Pomoc. |

Konwencja kierunków: **pan > 0 = w prawo robota**, **tilt > 0 = w górę**.

## Zdarzenia (Notify)

| Komunikat | Kiedy |
|---|---|
| `TOUCH` | dotknięto czujnika na głowie (robot sam wykonuje też gest `happy`) |
| `OK`, `ERR <opis>`, `S …`, `PONG` | odpowiedzi na komendy |

## Rozszerzanie

- Nowy gest: dopisz gałąź w `Motion::playGesture()` (firmware) i wartość w `ExpressTool` (aplikacja).
- Nowa komenda: nowa litera w `handleCommand()`; zachowaj zasadę „komendy częste nie odpowiadają”, żeby nie zapychać łącza.
- Zmiana UUID: trzeba ją zrobić w trzech miejscach – `firmware/include/config.h`, `android/.../Config.kt`, `tools/ble_console.py`.
