# Hardware: Components & Wiring

This is the default build: **4 products** with 5 quantity LEDs each, plus SUBMIT, SUCCESS and PENDING.
To use more products, see [Scaling](#scaling-to-more-products).

## Bill of Materials

### Core electronics

| # | Component | Qty | Spec / Notes |
|---|-----------|----:|--------------|
| 1 | **ESP32 DevKit V1** (ESP32-WROOM-32) | 1 | 30- or 38-pin dev board with USB. Any ESP32 (not S2/C3) board with the pins below works. |
| 2 | **74HC595** shift register (DIP-16) | 3 | Drives the 20 quantity LEDs (3 × 8 = 24 outputs, 4 spare). Runs at 3.3 V. |
| 3 | DIP-16 IC socket | 3 | Optional, recommended when soldering. |
| 4 | **5 mm LED: quantity** (red or yellow) | 20 | 5 per product row. Use red/yellow/standard green (Vf ≈ 2 V). Avoid blue/white: their Vf ≈ 3 V is too close to 3.3 V. |
| 5 | **5 mm LED: SUCCESS** (green) | 1 | Order accepted. |
| 6 | **5 mm LED: PENDING** (amber/yellow) | 1 | Lit while an order is open. |
| 7 | **330 Ω resistor**, ¼ W | 22 | One per LED (20 quantity + 2 status). About 4 mA per LED at 3.3 V. |
| 8 | **Tactile push button** 12×12 mm (or 16–24 mm arcade/panel button) | 5 | 4 product buttons + 1 SUBMIT. Momentary, normally open. |
| 9 | 100 nF ceramic capacitor | 3 | Decoupling: one across VCC/GND of each 74HC595, as close to the chip as possible. |
| 10 | 10–100 µF electrolytic capacitor, ≥ 10 V | 1 | Bulk capacitor on the 3.3 V rail. |

### Power

| # | Component | Qty | Notes |
|---|-----------|----:|-------|
| 11 | USB cable (Micro-USB or USB-C to match the board) | 1 | Also used for flashing. |
| 12 | 5 V / 1 A USB power adapter | 1 | Worst case draw is about 250 mA (WiFi TX peaks plus all LEDs). |

### Build / prototyping

| # | Component | Qty | Notes |
|---|-----------|----:|-------|
| 13 | Breadboard, 830-point | 2 | For prototyping. Use perfboard (7×9 cm or larger) for a permanent build. |
| 14 | Jumper wires (M-M and M-F assortment) | 1 set | About 60 wires. |
| 15 | Male/female pin headers | 1 strip | When soldering to perfboard. |
| 16 | 22 AWG hook-up wire | 1 roll | Permanent build. |
| 17 | Enclosure (about 150 × 100 × 40 mm ABS box) | 1 | Optional. Drill 5 mm holes for LEDs and button holes on the lid. |
| 18 | M3 standoffs + screws | 4 | Optional, for mounting the board in the enclosure. |
| 19 | Product labels / stickers | 4 | One per button row. |

### Tools
Soldering iron and solder (permanent build), wire stripper, multimeter, and a drill for the enclosure.

## Pin Map (ESP32)

| Function | GPIO | Connects to |
|----------|-----:|-------------|
| Product 1 button (MILK-1L) | 32 | Button → GND |
| Product 2 button (BREAD-WW) | 33 | Button → GND |
| Product 3 button (EGGS-12) | 25 | Button → GND |
| Product 4 button (WATER-20L) | 26 | Button → GND |
| SUBMIT button | 27 | Button → GND |
| 74HC595 DATA (SER) | 23 | Chip 1, pin 14 |
| 74HC595 CLOCK (SRCLK) | 18 | Pin 11 on **all** chips |
| 74HC595 LATCH (RCLK) | 19 | Pin 12 on **all** chips |
| SUCCESS LED | 21 | 330 Ω → green LED anode, cathode → GND |
| PENDING LED | 22 | 330 Ω → amber LED anode, cathode → GND |
| WiFi status | 2 | On-board LED (no wiring) |
| 3V3 | — | 74HC595 VCC (16) and MR (10) |
| GND | — | 74HC595 GND (8) and OE (13), all LED cathodes, all buttons |

Buttons use the ESP32's internal pull-ups, so no external resistors are needed. Software debounces them (30 ms).
Don't move buttons to GPIO 34–39: those pins have no internal pull-ups.

## 74HC595 Wiring

Wiring common to every chip:

| 74HC595 pin | Name | Connect to |
|------------:|------|-----------|
| 16 | VCC | 3V3 (+100 nF to GND) |
| 8 | GND | GND |
| 10 | MR (SRCLR) | 3V3 (never reset) |
| 13 | OE | GND (outputs always enabled) |
| 11 | SRCLK | GPIO 18 |
| 12 | RCLK | GPIO 19 |

Daisy chain:

```
GPIO23 ──► [Chip 1] SER(14)   Q7'(9) ──► [Chip 2] SER(14)   Q7'(9) ──► [Chip 3] SER(14)
```

Output pins: **Q0 = pin 15**, Q1–Q7 = pins 1–7.
Each output connects as: `Qx ─ 330 Ω ─►|─ GND` (resistor, then LED anode, then cathode to GND).

## LED Map

The LED for product `p` at quantity step `s` (both 0-based) is `n = p×5 + s`, on chip `n / 8`, output `Q(n mod 8)`.

| Product row | Qty 1 | Qty 2 | Qty 3 | Qty 4 | Qty 5 |
|-------------|-------|-------|-------|-------|-------|
| 1 MILK-1L   | C1 Q0 | C1 Q1 | C1 Q2 | C1 Q3 | C1 Q4 |
| 2 BREAD-WW  | C1 Q5 | C1 Q6 | C1 Q7 | C2 Q0 | C2 Q1 |
| 3 EGGS-12   | C2 Q2 | C2 Q3 | C2 Q4 | C2 Q5 | C2 Q6 |
| 4 WATER-20L | C2 Q7 | C3 Q0 | C3 Q1 | C3 Q2 | C3 Q3 |

Chip 3, outputs Q4–Q7, are spare.

## Front Panel Layout

```
 ┌──────────────────────────────────────────────┐
 │  (●) MILK 1L       ○ ○ ○ ○ ○                  │
 │  (●) BREAD         ○ ○ ○ ○ ○                  │
 │  (●) EGGS 12       ○ ○ ○ ○ ○                  │
 │  (●) WATER 20L     ○ ○ ○ ○ ○                  │
 │                                              │
 │  [ SUBMIT ]   ✓ SUCCESS ○   ⏳ PENDING ○       │
 └──────────────────────────────────────────────┘
```

## LED Behaviour

| Indication | Meaning |
|-----------|---------|
| Row shows N LEDs | Quantity N (0–5) of that product in the cart. Each press adds 1, and 5 wraps to 0. **Hold 1.5 s** to clear the row. |
| SUCCESS on, during submit | Request in progress |
| SUCCESS solid 3 s | Order accepted (stored in Kafka). Cart cleared. |
| SUCCESS 2 fast blinks | Cart is empty |
| SUCCESS 3 slow blinks | Network, server or Kafka problem. Cart kept. Press SUBMIT again to retry; retries reuse the order id, so you can't get a duplicate order. |
| SUCCESS 8 fast blinks | Rejected (wrong device key or unknown SKU) |
| PENDING on | At least one order is PENDING, CONFIRMED or SHIPPED |
| On-board LED blinking / solid | WiFi connecting / connected |

## Power Budget

- 74HC595: about 70 mA total per chip. Worst case here is 8 LEDs × ~4 mA = 32 mA per chip, which is fine.
- 3.3 V rail total: about 22 LEDs × 4 mA ≈ 90 mA, plus ESP32 (~240 mA peak on WiFi TX). The dev board's AMS1117 regulator (800 mA) handles this.
- For brighter LEDs, don't drop the resistors below 220 Ω.

## Scaling to More Products

1. Add a button pin to `PRODUCT_BUTTON_PINS` and a SKU to `PRODUCT_SKUS` in `firmware/include/config.h`.
   Good spare pins: 13, 14, 16, 17, 4.
2. Add the product to `iotecomm.products` in `backend/src/main/resources/application.yml`.
3. Shift registers needed: `ceil(products × 5 / 8)`. For example, 6 products need 4 chips and 8 products need 5. Chain each new chip's SER to the previous chip's Q7'.
