// LED animation: ESP32 + WS2812B addressable LEDs (100-200 LEDs)
//
// Wiring and power: see docs/LED_ANIMATION_ESP32_WS2812.md
//   GPIO16 -> 74AHCT125 (powered at 5 V) -> 330 ohm -> strip DIN
//   GPIO27 -> push button -> GND   (press to change animation)
//   LEDs are powered from a separate 5 V supply, never from the ESP32.

#include <Arduino.h>
#include <FastLED.h>

#define DATA_PIN    16
#define BUTTON_PIN  27
#define NUM_LEDS    200
#define MAX_MA      4000   // current budget for the LEDs; keep below your power supply rating
#define BRIGHTNESS  120    // global brightness 0-255

CRGB leds[NUM_LEDS];
uint8_t mode = 0;
const uint8_t NUM_MODES = 4;
uint8_t hue = 0;           // slowly cycling base color

// 1. Rainbow flowing along the strip
void rainbow() {
  fill_rainbow(leds, NUM_LEDS, hue, 2);
}

// 2. Comet: a bright head with a fading tail
void comet() {
  static uint16_t pos = 0;
  fadeToBlackBy(leds, NUM_LEDS, 40);  // the tail fades by 40/255 every frame
  leds[pos] = CHSV(hue, 255, 255);
  pos = (pos + 1) % NUM_LEDS;
}

// 3. Twinkle: random sparkles
void twinkle() {
  fadeToBlackBy(leds, NUM_LEDS, 10);
  leds[random16(NUM_LEDS)] += CHSV(hue + random8(64), 200, 255);
}

// 4. Breathing: the whole design slowly pulses
void breathe() {
  uint8_t level = beatsin8(12, 20, 255);  // 12 breaths per minute, between 20 and 255
  fill_solid(leds, NUM_LEDS, CHSV(hue, 255, level));
}

void setup() {
  Serial.begin(115200);
  pinMode(BUTTON_PIN, INPUT_PULLUP);
  FastLED.addLeds<WS2812B, DATA_PIN, GRB>(leds, NUM_LEDS);
  FastLED.setMaxPowerInVoltsAndMilliamps(5, MAX_MA);  // dims automatically to stay within budget
  FastLED.setBrightness(BRIGHTNESS);
  FastLED.clear(true);
  Serial.printf("LED animation: %d LEDs, budget %d mA\n", NUM_LEDS, MAX_MA);
}

void loop() {
  // Button: next animation on each press (simple 200 ms debounce)
  static bool last = HIGH;
  static uint32_t lastPress = 0;
  bool now = digitalRead(BUTTON_PIN);
  if (last == HIGH && now == LOW && millis() - lastPress > 200) {
    mode = (mode + 1) % NUM_MODES;
    lastPress = millis();
    FastLED.clear();
    Serial.printf("mode %u\n", mode);
  }
  last = now;

  EVERY_N_MILLISECONDS(20) { hue++; }

  switch (mode) {
    case 0: rainbow(); break;
    case 1: comet();   break;
    case 2: twinkle(); break;
    case 3: breathe(); break;
  }
  FastLED.show();
  delay(16);  // about 60 frames per second
}
