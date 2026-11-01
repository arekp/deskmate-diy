#include "Motion.h"

#include "config.h"

void Motion::begin() {
  ESP32PWM::allocateTimer(0);
  ESP32PWM::allocateTimer(1);
  panServo_.setPeriodHertz(50);
  tiltServo_.setPeriodHertz(50);
  panServo_.attach(PIN_PAN, cfg::SERVO_MIN_US, cfg::SERVO_MAX_US);
  tiltServo_.attach(PIN_TILT, cfg::SERVO_MIN_US, cfg::SERVO_MAX_US);
  current_ = {0, 0};
  write(current_);
  markActivity();
}

void Motion::update() {
  const uint32_t now = millis();

  if (moving_) {
    const uint32_t elapsed = now - segStart_;
    if (elapsed >= segDur_) {
      current_ = to_;
      moving_ = false;
      holding_ = segHold_ > 0;
      holdStart_ = now;
    } else {
      const float t = ease(static_cast<float>(elapsed) / segDur_);
      current_.pan = from_.pan + (to_.pan - from_.pan) * t;
      current_.tilt = from_.tilt + (to_.tilt - from_.tilt) * t;
    }
    write(current_);
  } else if (holding_ && now - holdStart_ >= segHold_) {
    holding_ = false;
  }

  if (moving_ || holding_) return;

  if (qCount_ > 0) {
    startNext();
    return;
  }

  // Spoczynek: drobne, losowe ruchy wokół pozycji bazowej ("oddychanie").
  if (idleEnabled_ && !asleep_ && now - lastActivity_ > cfg::IDLE_DELAY_MS &&
      static_cast<int32_t>(now - nextIdleAt_) >= 0) {
    const float dp = random(-100, 101) / 100.0f * cfg::IDLE_PAN_RANGE;
    const float dt = random(-100, 101) / 100.0f * cfg::IDLE_TILT_RANGE;
    push({clamp({base_.pan + dp, base_.tilt + dt}),
          static_cast<uint16_t>(random(1200, 2600)),
          static_cast<uint16_t>(random(300, 1200))});
    nextIdleAt_ = now + random(2500, 6000);
  }
}

void Motion::lookAt(float pan, float tilt, uint16_t ms) {
  clearQueue();
  asleep_ = false;
  base_ = clamp({pan, tilt});
  push({base_, ms, 0});
  moving_ = false;  // przerwij bieżący segment – nowy startuje z aktualnej pozycji
  holding_ = false;
  startNext();
  markActivity();
}

void Motion::home(uint16_t ms) { lookAt(0, 0, ms); }

bool Motion::playGesture(String name) {
  name.trim();
  name.toLowerCase();

  const Pose b = base_;
  auto k = [&](float dp, float dt, uint16_t ms, uint16_t hold = 0) {
    push({clamp({b.pan + dp, b.tilt + dt}), ms, hold});
  };

  clearQueue();
  if (name == "nod") {  // "tak"
    k(0, 12, 180);
    k(0, -6, 180);
    k(0, 10, 160);
    k(0, 0, 220);
  } else if (name == "shake") {  // "nie"
    k(15, 0, 180);
    k(-15, 0, 240);
    k(10, 0, 200);
    k(0, 0, 200);
  } else if (name == "happy") {
    k(0, 8, 150);
    k(8, 4, 150);
    k(-8, 4, 200);
    k(8, 4, 200);
    k(0, 0, 250);
  } else if (name == "sad") {
    k(0, -18, 900, 800);
    k(0, 0, 700);
  } else if (name == "curious") {
    k(10, 10, 400, 700);
    k(0, 0, 400);
  } else if (name == "sleep") {
    k(0, cfg::TILT_MIN - b.tilt, 1500);
    asleep_ = true;
  } else if (name == "wake") {
    asleep_ = false;
    k(0, 10, 400);
    k(0, 0, 400);
  } else {
    return false;
  }

  moving_ = false;
  holding_ = false;
  startNext();
  markActivity();
  return true;
}

void Motion::setIdle(bool enabled) {
  idleEnabled_ = enabled;
  markActivity();
}

bool Motion::push(const Keyframe& k) {
  if (qCount_ >= QUEUE_LEN) return false;
  queue_[(qHead_ + qCount_) % QUEUE_LEN] = k;
  qCount_++;
  return true;
}

void Motion::startNext() {
  if (qCount_ == 0) return;
  const Keyframe k = queue_[qHead_];
  qHead_ = (qHead_ + 1) % QUEUE_LEN;
  qCount_--;

  from_ = current_;
  to_ = clamp(k.pose);
  segDur_ = k.durationMs > 0 ? k.durationMs : 1;
  segHold_ = k.holdMs;
  segStart_ = millis();
  moving_ = true;
  holding_ = false;
}

void Motion::write(const Pose& p) {
  const float panSign = cfg::PAN_INVERT ? -1.0f : 1.0f;
  const float tiltSign = cfg::TILT_INVERT ? -1.0f : 1.0f;
  const int panUs = constrain(
      static_cast<int>(cfg::PAN_CENTER_US + panSign * p.pan * cfg::US_PER_DEG),
      cfg::SERVO_MIN_US, cfg::SERVO_MAX_US);
  const int tiltUs = constrain(
      static_cast<int>(cfg::TILT_CENTER_US + tiltSign * p.tilt * cfg::US_PER_DEG),
      cfg::SERVO_MIN_US, cfg::SERVO_MAX_US);
  panServo_.writeMicroseconds(panUs);
  tiltServo_.writeMicroseconds(tiltUs);
}

void Motion::markActivity() {
  lastActivity_ = millis();
  nextIdleAt_ = lastActivity_;
}

// easeInOutCubic – łagodny start i hamowanie, jak u żywego stworzenia
float Motion::ease(float t) {
  if (t < 0.5f) return 4.0f * t * t * t;
  const float f = -2.0f * t + 2.0f;
  return 1.0f - f * f * f / 2.0f;
}

Pose Motion::clamp(Pose p) {
  p.pan = constrain(p.pan, cfg::PAN_MIN, cfg::PAN_MAX);
  p.tilt = constrain(p.tilt, cfg::TILT_MIN, cfg::TILT_MAX);
  return p;
}
