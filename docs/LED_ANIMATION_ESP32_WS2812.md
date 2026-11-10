# LED Animation With ESP32 + WS2812B (100–200 RGB LEDs)

Animate 100–200 full-color LEDs from **one ESP32 pin**. The code is a ready-to-build PlatformIO project in
[`led-animation/`](../led-animation). For a version with no microcontroller, see
[LED_ANIMATION_NO_MCU.md](LED_ANIMATION_NO_MCU.md).

## 1. Why WS2812B instead of 74HC595?

| | 74HC595 + LEDs (IoT device) | **WS2812B addressable** |
|---|---|---|
| Parts for 200 LEDs | 25 chips + 200 resistors + 200 LEDs | **200 LEDs only** (strip or pixels) |
| ESP32 pins | 3 | **1** |
| Colors | One fixed color | **16 million, set per LED** |
| Brightness | On/off only | 256 levels per color |
| Soldering | Hundreds of joints | 3 wires per strip segment |

Each WS2812B contains a small chip. The ESP32 sends a stream of colors down one wire. Each LED keeps the first
color it receives and passes the rest on to the next LED.

## 2. Power: the most important lesson

Each WS2812B draws up to **60 mA at full white** (20 mA each for red, green and blue).

| LEDs | Full white, 100% | Typical animation (~30%) | Power supply to buy |
|---|---|---|---|
| 100 | 6 A | ~2 A | **5 V 6 A** (30 W) |
| 200 | **12 A** | ~4 A | **5 V 10 A** (50 W) + software power limit |

⚠️ **12 A is roughly 100 times what the ESP32 dev board can supply.** Never power the LEDs from the ESP32's 5 V
or 3.3 V pin. They need their own 5 V supply.

The code calls `FastLED.setMaxPowerInVoltsAndMilliamps(5, MAX_MA)`, which automatically dims the LEDs so the total
current stays under `MAX_MA` (4000 mA by default).

## 3. Wiring

```
                 5V 10A power supply
                 (+5V)            (GND)
                   │                │
   ┌───────────────┼────────────────┼──────────────────────────┐
   │      1000 µF capacitor across +5V/GND at the strip input  │
   │               │                │                          │
   │   ESP32 VIN ◄─┤                ├─► ESP32 GND  (COMMON GND!)│
   │               │                │                          │
   │  GPIO16 ─► [74AHCT125] ─► 330 Ω ─► DIN  LED1 → LED2 → … → LED100 ─┐
   │               │                │                                  │
   │               └─ +5V/GND also connected here (power injection) ───┘ → LED101 … LED200
   │                                     inject +5V/GND again every 50–100 LEDs
   └──────────────────────────────────────────────────────────────────┘
   Button: GPIO27 → button → GND   (changes animation)
```

| Part | Why |
|---|---|
| **5 V 10 A supply** | Powers the LEDs. Enclosed "LED driver" units are the safe option. ⚠️ If it has screw terminals for 230 V/110 V mains, have someone experienced wire the mains side, or buy a ready-made 5 V 10 A barrel-jack adapter. |
| **1000 µF / 10 V capacitor** | Absorbs the inrush current at power-on and smooths sudden brightness changes. Stripe (−) to GND. |
| **74AHCT125 level shifter** | The ESP32 outputs 3.3 V, but a WS2812B on 5 V needs at least about 3.5 V for a reliable HIGH. An **HCT/AHCT chip powered at 5 V** accepts 3.3 V as HIGH and outputs a full 5 V. (This is the opposite of the 74HC595 tip: here HCT is the right choice.) Tie the unused enable pins (1OE etc.) of the channel you use to GND. |
| **330 Ω on the data line** | Reduces ringing on the data wire and protects the first LED. |
| **Common GND** | The ESP32 GND and the supply GND must be connected, otherwise the data has no reference and the LEDs flicker randomly. |
| **Power injection** | Strip copper is thin. Over 100+ LEDs the voltage drops and the far end turns **orange and dim**. Feed 5 V and GND in again every 50–100 LEDs. |
| **Thick wires** | 18 AWG for the main 5 V/GND runs, 22 AWG for data. |
| **10 A fuse** (recommended) | Inline on the +5 V line. |

**ESP32 power:** feed the supply's 5 V into **VIN**. Don't connect USB at the same time. To flash, disconnect VIN
first.

## 4. Code

Build and flash:
```bash
cd led-animation && pio run -t upload && pio device monitor
```

Settings at the top of [`led-animation/src/main.cpp`](../led-animation/src/main.cpp):

| Setting | Default | Meaning |
|---|---|---|
| `DATA_PIN` | 16 | GPIO going to the level shifter |
| `BUTTON_PIN` | 27 | Mode button to GND |
| `NUM_LEDS` | 200 | Number of LEDs |
| `MAX_MA` | 4000 | Current budget for the LEDs in mA. Keep it below your supply's rating. |
| `BRIGHTNESS` | 120 | Global brightness, 0–255 |

Animations (press the button to cycle): **rainbow**, **comet**, **twinkle**, **breathing**.

What the code teaches:
- **HSV color** (`CHSV(hue, saturation, value)`) makes rainbows and fades easy: change one number instead of
  mixing red, green and blue.
- `fadeToBlackBy` produces trails and afterglow.
- **Timing:** each LED takes about 30 µs to receive its data, so 200 LEDs need about 6 ms per frame. The maximum is
  about 150 fps; 60 fps looks smooth.

## 5. Design Ideas

| Design | LED count | Notes |
|---|---|---|
| **12 × 12 matrix** | 144 | Wire it zig-zag ("serpentine"). Text, pixel art, fire effects. |
| **Concentric rings** (8 + 12 + 16 + 24 + 32 + 40 + 48) | 180 | Ready-made WS2812 rings exist. Ripples, radar sweeps. |
| **Heart, star or name outline** | 100–150 | Cut the strip into segments and glue them along a shape on MDF or acrylic. |
| **Infinity mirror** | 100 | Strip between a mirror and a one-way mirror: tunnel effect. |

A frosted acrylic sheet or tracing paper 1–2 cm in front of the LEDs blends the dots into smooth light. For
mounting on a large board, see the assembly section in [LED_ANIMATION_NO_MCU.md](LED_ANIMATION_NO_MCU.md#12-assembly-on-a-big-board)
(with strips you glue segments along the design instead of drilling a hole per LED).

## 6. Build Order

1. **Start with 8–16 LEDs** (a stick or ring) powered from USB, with `NUM_LEDS` and `MAX_MA` (e.g. 500) set to
   match. Test code and wiring.
2. Add the level shifter, 1000 µF capacitor and external 5 V supply.
3. Go to 100 LEDs and check the color at the far end. If it turns orange, add power injection.
4. Go to 200 LEDs and measure the current with a multimeter or clamp meter.

## 7. Shopping List (200 LEDs)

| Item | Qty |
|---|---|
| WS2812B strip, 60 LEDs/m, IP30 (indoor) | 4 m |
| 5 V 10 A power supply | 1 |
| 74AHCT125 (DIP-14) | 1 |
| 1000 µF / 10 V electrolytic capacitor | 1 |
| 330 Ω resistor | 1 |
| Push button | 1 |
| 18 AWG red/black wire, 22 AWG wire | A few metres |
| DC barrel jack or screw terminal | 1 |
| 10 A inline fuse | 1 |
| ESP32 DevKit V1 | 1 (the IoT device's board can be reused for experiments) |

## 8. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing lights | Data goes into the wrong end: strips have arrows, connect to **DIN**, not DOUT. No common GND. |
| Random colors / flicker | Missing level shifter or common GND. Data wire too long (keep under ~1 m). |
| Wrong colors (red shows green) | Color order: change `GRB` to `RGB` in `addLeds`. |
| Far end orange / dim | Voltage drop: add power injection. |
| ESP32 resets when LEDs get bright | LEDs powered through the ESP32, or supply too small. Lower `MAX_MA`. |
| First LED dead | Missing 330 Ω data resistor or 1000 µF capacitor. Cut it out and solder to the next LED. |
