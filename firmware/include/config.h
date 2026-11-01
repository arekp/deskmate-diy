#pragma once
// Konfiguracja sprzętu. Piny można nadpisać w platformio.ini (build_flags -DPIN_...).

#include <Arduino.h>

#ifndef PIN_PAN
#define PIN_PAN 4      // serwo obrotu (lewo/prawo)
#endif
#ifndef PIN_TILT
#define PIN_TILT 5     // serwo pochylenia (góra/dół)
#endif
#ifndef PIN_TOUCH
#define PIN_TOUCH -1   // czujnik dotyku TTP223 (wyjście cyfrowe), -1 = brak
#endif

namespace cfg {

// --- BLE ---------------------------------------------------------------
// Musi zgadzać się z aplikacją (android/.../Config.kt) i docs/protokol-ble.md
constexpr const char* DEVICE_NAME  = "DeskMate-DIY";
constexpr const char* SERVICE_UUID = "4d7d0001-8a3e-4b8f-9c1a-2f6d5e7a9b10";
constexpr const char* CMD_UUID     = "4d7d0002-8a3e-4b8f-9c1a-2f6d5e7a9b10";  // write
constexpr const char* STATUS_UUID  = "4d7d0003-8a3e-4b8f-9c1a-2f6d5e7a9b10";  // notify

// --- Serwa -------------------------------------------------------------
// Kąty w stopniach względem pozycji "patrzę prosto" (0, 0).
constexpr float PAN_MIN  = -80.0f;
constexpr float PAN_MAX  =  80.0f;
constexpr float TILT_MIN = -25.0f;   // w dół
constexpr float TILT_MAX =  30.0f;   // w górę

// Kalibracja: impuls dla pozycji środkowej i ile mikrosekund na stopień.
// Typowe serwo 180°: 500–2500 µs  ->  ~11.1 µs/°
constexpr int   PAN_CENTER_US  = 1500;
constexpr int   TILT_CENTER_US = 1500;
constexpr float US_PER_DEG     = 2000.0f / 180.0f;
constexpr bool  PAN_INVERT     = false;
constexpr bool  TILT_INVERT    = false;
constexpr int   SERVO_MIN_US   = 500;
constexpr int   SERVO_MAX_US   = 2500;

// --- Zachowanie --------------------------------------------------------
constexpr uint32_t IDLE_DELAY_MS   = 4000;  // po ilu ms bez komend zaczyna "oddychać"
constexpr float    IDLE_PAN_RANGE  = 6.0f;  // amplituda mikro-ruchów w spoczynku
constexpr float    IDLE_TILT_RANGE = 3.0f;
constexpr uint32_t TOUCH_DEBOUNCE_MS = 300;

}  // namespace cfg
