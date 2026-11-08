#pragma once
#include <Arduino.h>

// ---------------------------------------------------------------------------
// Hardware layout
// ---------------------------------------------------------------------------
// One product button per product. Buttons wire GPIO -> button -> GND (internal pull-up).
// Avoid GPIO 34-39 for buttons: they have no internal pull-ups.
static const uint8_t PRODUCT_BUTTON_PINS[] = {32, 33, 25, 26};
static const uint8_t NUM_PRODUCTS = sizeof(PRODUCT_BUTTON_PINS) / sizeof(PRODUCT_BUTTON_PINS[0]);

// SKU sold by each button, same order as PRODUCT_BUTTON_PINS. Must exist in the backend `products` table.
static const char* const PRODUCT_SKUS[] = {"MILK-1L", "BREAD-WW", "EGGS-12", "WATER-20L"};

static const uint8_t SUBMIT_BUTTON_PIN = 27;

// Quantity LEDs (5 per product) are driven by chained 74HC595 shift registers.
// LED number n (0-based) for product p, step s is n = p * LEDS_PER_PRODUCT + s.
// Chip 0 drives LEDs 0-7, chip 1 drives 8-15, and so on.
static const uint8_t LEDS_PER_PRODUCT = 5;
static const uint8_t SR_DATA_PIN  = 23;   // 74HC595 pin 14 (SER / DS)
static const uint8_t SR_CLOCK_PIN = 18;   // 74HC595 pin 11 (SRCLK / SH_CP)
static const uint8_t SR_LATCH_PIN = 19;   // 74HC595 pin 12 (RCLK / ST_CP)

static const uint8_t SUCCESS_LED_PIN = 21;  // order accepted
static const uint8_t PENDING_LED_PIN = 22;  // you have at least one open order
static const uint8_t WIFI_LED_PIN    = 2;   // on-board LED: solid = online, blinking = connecting

// ---------------------------------------------------------------------------
// Behaviour
// ---------------------------------------------------------------------------
static const uint32_t DEBOUNCE_MS          = 30;
static const uint32_t LONG_PRESS_MS        = 1500;   // hold a product button to clear it
static const uint32_t SUCCESS_LED_ON_MS    = 3000;
static const uint32_t PENDING_POLL_MS      = 30000;
static const uint32_t POST_SUBMIT_POLL_MS  = 3000;   // re-check pending soon after submitting
static const uint32_t WIFI_RETRY_MS        = 10000;
static const uint32_t HTTP_TIMEOUT_MS      = 10000;
