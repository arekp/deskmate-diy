// DeskMate DIY – firmware "ciała" robota.
// Telefon (mózg) wysyła tekstowe komendy przez BLE, ESP32 porusza głową.
// Te same komendy można wpisywać w monitorze portu szeregowego (115200).
// Protokół: docs/protokol-ble.md

#include <Arduino.h>
#include <NimBLEDevice.h>

#include "Motion.h"
#include "config.h"

namespace {

constexpr size_t CMD_MAX = 64;

Motion motion;
QueueHandle_t cmdQueue = nullptr;
NimBLECharacteristic* statusChr = nullptr;
volatile bool bleConnected = false;
volatile bool bleJustDisconnected = false;

void notify(const String& msg) {
  if (msg.isEmpty()) return;
  Serial.println(msg);
  if (statusChr != nullptr && bleConnected) {
    statusChr->setValue(std::string(msg.c_str()));
    statusChr->notify();
  }
}

String statusLine() {
  const Pose p = motion.current();
  char buf[64];
  snprintf(buf, sizeof(buf), "S %.1f %.1f idle=%d sleep=%d", p.pan, p.tilt,
           motion.idleEnabled() ? 1 : 0, motion.asleep() ? 1 : 0);
  return String(buf);
}

String handleCommand(String line) {
  line.trim();
  if (line.isEmpty()) return "";

  const char cmd = static_cast<char>(toupper(line[0]));
  String args = line.substring(1);
  args.trim();

  switch (cmd) {
    case 'L': {  // L <pan> <tilt> [ms]
      float pan = 0, tilt = 0;
      int ms = 400;
      if (sscanf(args.c_str(), "%f %f %d", &pan, &tilt, &ms) < 2) {
        return "ERR usage: L <pan> <tilt> [ms]";
      }
      motion.lookAt(pan, tilt, static_cast<uint16_t>(constrain(ms, 20, 5000)));
      return "";  // L przychodzi często (śledzenie twarzy) – bez potwierdzeń
    }
    case 'G':  // G <gest>
      return motion.playGesture(args) ? "OK" : "ERR unknown gesture";
    case 'H':
      motion.home();
      return "OK";
    case 'I':  // I 0|1
      motion.setIdle(args.toInt() != 0);
      return "OK";
    case 'S':
      return statusLine();
    case 'P':
      return "PONG";
    case '?':
      return "CMDS: L <pan> <tilt> [ms] | G nod|shake|happy|sad|curious|sleep|wake | H | I 0|1 | S | P";
    default:
      return "ERR unknown command";
  }
}

// --- BLE ---------------------------------------------------------------

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* /*server*/) override { bleConnected = true; }
  void onDisconnect(NimBLEServer* /*server*/) override {
    bleConnected = false;
    bleJustDisconnected = true;
  }
};

class CmdCallbacks : public NimBLECharacteristicCallbacks {
  // Wywoływane w wątku stosu BLE – tylko kopiujemy do kolejki,
  // a wykonanie odbywa się w loop(), żeby nie ruszać serw z dwóch wątków.
  void onWrite(NimBLECharacteristic* chr) override {
    const std::string value = chr->getValue();
    char buf[CMD_MAX] = {0};
    // Jeden zapis może zawierać kilka komend oddzielonych '\n' lub ';'
    size_t n = 0;
    for (char c : value) {
      if (c == '\n' || c == ';') {
        if (n > 0) xQueueSend(cmdQueue, buf, 0);
        memset(buf, 0, sizeof(buf));
        n = 0;
      } else if (n < CMD_MAX - 1) {
        buf[n++] = c;
      }
    }
    if (n > 0) xQueueSend(cmdQueue, buf, 0);
  }
};

void setupBle() {
  NimBLEDevice::init(cfg::DEVICE_NAME);
  NimBLEDevice::setPower(ESP_PWR_LVL_P9);

  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  NimBLEService* service = server->createService(cfg::SERVICE_UUID);
  NimBLECharacteristic* cmdChr = service->createCharacteristic(
      cfg::CMD_UUID, NIMBLE_PROPERTY::WRITE | NIMBLE_PROPERTY::WRITE_NR);
  cmdChr->setCallbacks(new CmdCallbacks());

  statusChr = service->createCharacteristic(
      cfg::STATUS_UUID, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  statusChr->setValue("READY");

  service->start();

  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID(cfg::SERVICE_UUID);
  adv->setScanResponse(true);
  adv->start();
}

// --- Dotyk (opcjonalny TTP223) ----------------------------------------

void pollTouch() {
  if (PIN_TOUCH < 0) return;
  static bool last = false;
  static uint32_t lastChange = 0;
  const bool now = digitalRead(PIN_TOUCH) == HIGH;
  if (now != last && millis() - lastChange > cfg::TOUCH_DEBOUNCE_MS) {
    lastChange = millis();
    last = now;
    if (now) {
      motion.playGesture("happy");
      notify("TOUCH");
    }
  }
}

// --- Port szeregowy ----------------------------------------------------

void pollSerial() {
  static String line;
  while (Serial.available()) {
    const char c = static_cast<char>(Serial.read());
    if (c == '\n' || c == '\r') {
      if (!line.isEmpty()) {
        const String resp = handleCommand(line);
        Serial.println(resp.isEmpty() ? "OK" : resp);
        line = "";
      }
    } else if (line.length() < CMD_MAX) {
      line += c;
    }
  }
}

}  // namespace

void setup() {
  Serial.begin(115200);
  delay(200);
  Serial.println("DeskMate DIY firmware – start");

  if (PIN_TOUCH >= 0) pinMode(PIN_TOUCH, INPUT);

  cmdQueue = xQueueCreate(16, CMD_MAX);
  motion.begin();
  setupBle();
  motion.playGesture("wake");

  Serial.println("BLE gotowe, nazwa: " + String(cfg::DEVICE_NAME));
  Serial.println(handleCommand("?"));
}

void loop() {
  char buf[CMD_MAX];
  while (xQueueReceive(cmdQueue, buf, 0) == pdTRUE) {
    notify(handleCommand(String(buf)));
  }

  if (bleJustDisconnected) {
    bleJustDisconnected = false;
    motion.home();  // telefon zdjęty / rozłączony – wróć do środka
  }

  pollSerial();
  pollTouch();
  motion.update();
  delay(5);  // ~200 Hz aktualizacji ruchu
}
