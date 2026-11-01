#pragma once
// Płynny ruch "głowy": kolejka klatek kluczowych + wygładzanie (easing)
// + mikro-ruchy w spoczynku, żeby robot wyglądał na żywego.

#include <Arduino.h>
#include <ESP32Servo.h>

struct Pose {
  float pan;
  float tilt;
};

struct Keyframe {
  Pose pose;
  uint16_t durationMs;  // czas dojścia do pozycji
  uint16_t holdMs;      // ile stać w pozycji po dojściu
};

class Motion {
 public:
  void begin();
  void update();  // wywoływać jak najczęściej w loop()

  // Natychmiast przerywa bieżący gest i płynnie patrzy w dany punkt.
  void lookAt(float pan, float tilt, uint16_t ms);
  void home(uint16_t ms = 600);

  // Gesty: nod, shake, happy, sad, curious, sleep, wake. false = nieznany.
  bool playGesture(String name);

  void setIdle(bool enabled);
  bool idleEnabled() const { return idleEnabled_; }
  bool asleep() const { return asleep_; }
  Pose current() const { return current_; }
  Pose target() const { return base_; }

 private:
  static constexpr size_t QUEUE_LEN = 16;

  bool push(const Keyframe& k);
  void clearQueue() { qHead_ = 0; qCount_ = 0; }
  void startNext();
  void write(const Pose& p);
  void markActivity();
  static float ease(float t);
  static Pose clamp(Pose p);

  Servo panServo_;
  Servo tiltServo_;

  Keyframe queue_[QUEUE_LEN];
  size_t qHead_ = 0;
  size_t qCount_ = 0;

  Pose current_{0, 0};
  Pose from_{0, 0};
  Pose to_{0, 0};
  Pose base_{0, 0};  // "docelowe" spojrzenie, wokół którego są gesty i idle

  uint32_t segStart_ = 0;
  uint16_t segDur_ = 0;
  uint16_t segHold_ = 0;
  uint32_t holdStart_ = 0;
  bool moving_ = false;
  bool holding_ = false;

  bool idleEnabled_ = true;
  bool asleep_ = false;
  uint32_t lastActivity_ = 0;
  uint32_t nextIdleAt_ = 0;
};
