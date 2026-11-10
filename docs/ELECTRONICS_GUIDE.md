# Electronics Guide: Learning Through the IoTEcomm Circuit

Notes from learning electronics with this project's hardware. Each topic uses the real circuit as its example.

Related documents:
- [HARDWARE.md](HARDWARE.md): parts list, pin map, wiring, current with everything on, capacitor summary
- [diagrams/decoupling-capacitor.html](diagrams/decoupling-capacitor.html): drawings of the long wire, the
  capacitor's current loops and the 74HC595 pinout (download and open in a browser)
- [LED_ANIMATION_NO_MCU.md](LED_ANIMATION_NO_MCU.md): ~200 LEDs with a 555 timer, CD4017 and MOSFETs, plus
  assembly on a big board
- [LED_ANIMATION_ESP32_WS2812.md](LED_ANIMATION_ESP32_WS2812.md): 100–200 RGB LEDs with an ESP32

## Contents

1. [The circuit as blocks](#1-the-circuit-as-blocks)
2. [Voltage, current, resistance](#2-voltage-current-resistance)
3. [Each component](#3-each-component)
4. [Build tips for this circuit](#4-build-tips-for-this-circuit)
5. [Putting everything on one circuit board](#5-putting-everything-on-one-circuit-board)
6. [Why GND is needed](#6-why-gnd-is-needed)
7. [Capacitors](#7-capacitors)
8. [How an IC uses current, and its voltage limits](#8-how-an-ic-uses-current-and-its-voltage-limits)
9. [Finding voltage problems](#9-finding-voltage-problems)

---

## 1. The Circuit as Blocks

```
 USB 5V ──► [ESP32 DevKit] ──3.3V──► powers everything else
                 │
   inputs  ◄─────┤  GPIO 32,33,25,26,27 ◄── 5 push buttons (to GND)
                 │
   outputs ──────┤  GPIO 23/18/19 ──► 74HC595 ×3 ──► 20 qty LEDs (each with a 330 Ω resistor)
                 │  GPIO 21 ──► 330 Ω ──► SUCCESS LED
                 │  GPIO 22 ──► 330 Ω ──► PENDING LED
                 └─ WiFi ──► backend
```

Every circuit reduces to **power**, **inputs** and **outputs**. Here the inputs are buttons and the outputs are LEDs.

## 2. Voltage, Current, Resistance

| Quantity | Meaning | Water analogy | In this circuit |
|---|---|---|---|
| **Voltage (V)** | Electrical pressure between two points | Water level / pressure | Logic runs at 3.3 V. GPIO HIGH = 3.3 V, LOW = 0 V. |
| **Current (I)** | How fast charge flows, in mA | Litres per second in a pipe | ~4 mA per LED |
| **Resistance (R)** | Opposition to flow | A narrow section of pipe | 330 Ω resistors limit LED current |
| **Charge (Q)** | An amount of electricity, in coulombs | Amount of water | What a capacitor stores |

**Ohm's law: V = I × R.** Choosing the LED resistor:

```
3.3 V supply − ~2.0 V LED forward voltage = 1.3 V across the resistor
I = 1.3 V / 330 Ω ≈ 4 mA   → bright enough, safe for the 74HC595 and the ESP32
```

**Power: P = V × I = I² × R.** The 330 Ω resistor dissipates (0.004)² × 330 ≈ 5 mW, far below a ¼ W rating.

## 3. Each Component

### ESP32 DevKit V1

The dev board is a small system:
- **ESP32-WROOM-32 module** (metal can): CPU, WiFi, flash memory, antenna
- **AMS1117-3.3 regulator**: USB 5 V → 3.3 V, up to 800 mA
- **USB-to-serial chip** (CP2102 or CH340): flashing and the serial monitor
- **EN and BOOT buttons** plus two transistors that enter flashing mode automatically

Rules:
- ⚠️ **GPIOs are 3.3 V only.** Never connect 5 V to a GPIO. The absolute maximum is about 3.6 V.
- Keep each GPIO at **≤ 20 mA** (40 mA absolute maximum).
- **Never use GPIO 6–11**: they are wired to the internal flash.
- **GPIO 34–39 are input-only with no pull-ups**, which is why the buttons aren't on them.
- **Strapping pins (0, 2, 5, 12, 15)** are read at boot. GPIO 12 pulled HIGH at boot stops the board starting.
  This design uses only GPIO 2 (on-board LED), which is safe.

### LEDs

- Polarity: **long leg = anode (+)**, **flat edge on the rim = cathode (−)**. A reversed LED doesn't light but isn't
  damaged.
- An LED **always** needs a series resistor. Without one it draws too much current and can damage the pin driving it.
- **Forward voltage (Vf)** depends on color: red/yellow/green ~2 V; blue/white ~3 V. At 3.3 V, blue and white have
  only 0.3 V of headroom and look dim and uneven, so this design avoids them.

### Resistors

- Color code for 330 Ω: **orange, orange, brown, gold** = 33 × 10 Ω, ±5%.
- Don't go below **220 Ω** (~6 mA) for brighter LEDs, because of the 74HC595 total-current limit.

### Push buttons, pull-ups and debouncing

- A 4-leg tactile button is two pairs of legs that are already connected. **Use diagonally opposite legs**: they
  are always on opposite sides of the switch.
- **Pull-up:** an unconnected input "floats" and reads random values. `INPUT_PULLUP` connects an internal resistor
  (~45 kΩ) to 3.3 V, so the pin reads HIGH when released. Pressing shorts it to GND, so it reads LOW. This is
  **active-low**, hence `digitalRead(pin) == LOW` in the firmware.
- **Bounce:** contacts chatter for a few milliseconds, so one press can look like several. The firmware ignores
  changes for 30 ms (`DEBOUNCE_MS`). A hardware alternative is a 100 nF capacitor across the button.

### 74HC595 shift register

Three ESP32 pins drive any number of LEDs:

```
DATA  (GPIO23) ── the bit value (1 = LED on)
CLOCK (GPIO18) ── each pulse shifts one bit in and moves the others along
LATCH (GPIO19) ── copies all shifted bits to the outputs at once (no flicker)
```

- **Daisy-chaining:** bits that overflow out of chip 1 (Q7', pin 9) feed chip 2's DATA input. Three chips give 24
  outputs. That's why `renderQuantityLeds()` sends the **last** chip's byte first.
- **Orientation:** the notch or dot marks **pin 1**. Pins are numbered counter-clockwise, so VCC (16) and GND (8)
  are at opposite corners.
- **Limit:** about **70 mA total per chip**. 8 LEDs × 4 mA = 32 mA is fine.
- ⚠️ **Buy 74HC595, not 74HCT595.** HCT needs 5 V and isn't reliable at 3.3 V. (For the WS2812B level shifter the
  opposite is true: there an HCT/AHCT chip at 5 V is the right choice.)

### Capacitors

See [section 7](#7-capacitors). In short: **100 nF ceramic** at each 74HC595 (decoupling) and **10–100 µF
electrolytic** on the 3.3 V rail (bulk reservoir for WiFi bursts).

## 4. Build Tips for This Circuit

1. **Build and test in stages:** ESP32 + one LED on GPIO 21 → buttons (check in the serial monitor) → one
   74HC595 with 8 LEDs → chain the other two. The **lamp test** at boot (all LEDs for 0.5 s) shows any LED that's
   reversed or not connected.
2. **Breadboard trap:** many 830-point breadboards have their **power rails split in the middle**. Bridge them.
3. **Random LEDs at power-on:** 74HC595 outputs are random until the first latch. To avoid it, connect OE (pin 13)
   to a spare GPIO with a 10 kΩ pull-up to 3.3 V, and drive it LOW after the first `renderQuantityLeds()`.
4. **Use the multimeter:** continuity mode finds broken jumpers; pins 16–8 on each chip should read 3.3 V.
5. **Faster LED updates:** GPIO 23 and 18 are the hardware SPI pins (MOSI, SCK), so `shiftOut()` could become
   `SPI.transfer()`. Not needed at this size.
6. **One power source at a time:** don't feed 3.3 V into the 3V3 pin while USB is plugged in.
7. **WiFi range:** keep the module's antenna end away from metal, batteries and wires. A metal enclosure blocks WiFi.
8. **Brownouts:** if the board resets exactly when you press SUBMIT, add the bulk capacitor and use a better USB
   cable.

## 5. Putting Everything on One Circuit Board

| Option | What it means | Difficulty | Firmware changes |
|---|---|---|---|
| **A. Carrier PCB + plug-in DevKit** | Custom PCB holds buttons, LEDs, 74HC595s, resistors; the DevKit plugs into female headers | Easy | None |
| **B. ESP32 module soldered directly** (recommended) | Solder an **ESP32-WROOM-32E** module onto your PCB and add what the DevKit provided | Medium | None |
| C. Bare ESP32 chip | Design the RF, crystal and flash circuitry yourself | Hard; RF knowledge and certification | — |

Option B keeps the module's pre-certified antenna, so you avoid RF design, and the existing firmware and pin map
work unchanged.

### What Option B adds

```
USB-C ──5V──► [3.3 V regulator, ≥600 mA] ──3.3V──┬──► ESP32-WROOM-32E
  │                                              ├──► 74HC595 ×3, LEDs
  │                                       10 µF + 100 nF next to the module
  │
  └─D+/D−──► [USB-UART: CH340C or CP2102N] ──TX/RX──► ESP32 GPIO1/GPIO3
                     │ DTR/RTS
                     ▼
             [Auto-reset: 2× NPN transistor + 2× 10 kΩ] ──► EN and GPIO0

EN pin:    10 kΩ to 3.3 V + 1 µF to GND (clean power-on reset) + RESET button to GND
GPIO0:     10 kΩ to 3.3 V + BOOT button to GND
```

Parts: WROOM-32E module, AP2112K-3.3 or AMS1117-3.3, USB-C connector with 2× 5.1 kΩ on the CC pins (without them
USB-C chargers supply no power), CH340C (no external crystal needed), 2× MMBT3904, 2 small buttons, resistors and
capacitors.

### PCB tips

- **Antenna keep-out:** the module's antenna should **stick past the board edge**, with **no copper or ground plane
  under it**. Getting this wrong ruins WiFi range.
- Use a **solid ground plane** and keep 100 nF capacitors right at each chip's power pins.
- **Cheaper per unit:** skip the USB-UART chip and transistors; add a 6-pin header (3V3, GND, TX, RX, EN, IO0) and
  program each board with one USB-UART adapter.
- **ESP32-S3-WROOM-1** has USB built in, removing the CH340 and auto-reset circuit, but `config.h` pin numbers and
  `board =` in `platformio.ini` would change.
- **Fewer parts:** a TLC5916 / STP16CP05 constant-current driver replaces a 74HC595 and its 8 resistors with one
  chip and one current-setting resistor. SMD 0805 parts shrink the board a lot.
- **Tools:** KiCad (free) for schematic and PCB; JLCPCB or PCBWay for manufacturing and SMD assembly.

**Suggested path:** breadboard prototype → perfboard or Option A → Option B custom PCB.

## 6. Why GND Is Needed

### Current needs a complete loop

```
 Supply + ──► resistor ──► LED ──► GND ──► back to Supply −
     ▲                                          │
     └──────────── supply pushes it around again ┘
```

The supply is a **pump**: it pushes charge out of + and pulls the same amount back in through −. Without the return
path, **no current flows at all**. The **GND wire carries exactly the same current as the + wire**, so it must be
just as thick.

### Voltage is a difference between two points

"3.3 V" means **3.3 V above GND**. GND is the 0 V reference everyone measures from, which is why the multimeter's
black probe goes on GND.

### Separate parts must share GND

A receiver decides HIGH or LOW by comparing the signal to **its own GND**. If two parts' grounds aren't connected,
there's no shared reference.

| Connection | Without it |
|---|---|
| ESP32 GND ↔ 74HC595 GND (pin 8) | Random LEDs, or nothing lights |
| ESP32 GND ↔ 5 V LED supply GND (WS2812) | Random colors and flicker |
| Buttons → GND | A press has nothing to pull the pin to, so it's never detected |
| MOSFET source → GND | The LED group's current has no path home |
| Decoupling capacitor → GND pin | No return path for the burst; the capacitor does nothing |

**Circuit GND vs earth:** circuit GND is the return and reference wire. Earth is the mains safety ground. Our 5 V
and 12 V adapters are isolated, so circuit GND doesn't need to connect to earth.

## 7. Capacitors

### What a capacitor stores

A capacitor **stores charge**, not current. Current is a flow and can't be stored, just as you can't store "litres
per second" in a bucket.

1. **Charging:** current flows in, and charge collects on the plates.
2. **Storing:** the stored charge produces a voltage: **Q = C × V**.
3. **Discharging:** the charge flows back out as current, and the voltage drops a little.

The voltage shows how full it is. It holds a voltage steady because it has charge in reserve, not because it
generates voltage. **I = C × ΔV/Δt**: a bigger capacitor supplies the same current with a smaller voltage drop.

| Capacitor vs battery | Capacitor | Battery |
|---|---|---|
| Stores | Charge on its plates | Energy in chemicals |
| Amount | Tiny (100 nF runs an IC for microseconds) | Large (hours) |
| Speed | Instant (nanoseconds) | Slower |
| Voltage while discharging | Falls steadily | Stays fairly constant |

### Its two legs

Both legs are equal connection points. **Current enters one leg and leaves the other at the same moment**, and the
direction **reverses** between charging and discharging. No charge crosses the insulator inside; charge piling onto
one plate pushes an equal amount off the other.

```
 CHARGING  (wire refills the capacitor)        DISCHARGING  (burst to the IC)

   VCC wire ──► IN  ┤ VCC leg ═╗                 IC pin 16 ◄── OUT ┤ VCC leg ═╗
                               ║ 100 nF                             ║ 100 nF
   GND wire ◄── OUT ┤ GND leg ═╝                 IC pin 8  ──► IN  ┤ GND leg ═╝
```

- **Ceramic:** legs are identical; fit it either way.
- **Electrolytic:** marked + and − (stripe = −). That's **voltage polarity**, not in/out: the + leg must always be
  at the higher voltage. Reversed, it heats, bulges and can pop.

### The four jobs in our circuits

| Job | Example | Without it |
|---|---|---|
| **Decoupling** | 100 nF at each 74HC595, NE555, CD4017 | Flicker, wrong LEDs, skipped steps |
| **Bulk reservoir** | 10–100 µF on the ESP32 3.3 V rail; 1000 µF at a WS2812 strip | Brownout resets; inrush damages the first LED |
| **Timing (τ = R × C)** | 10 µF on the NE555 sets animation speed; 4.7 µF on a MOSFET gate makes the fade tail | No animation; no fade |
| **Filtering** | 10 nF on NE555 pin 5 | Jittery animation speed |

### Why the decoupling capacitor sits right at the IC

The power supply is far away at the end of long wires (jumpers and breadboard rails, 20–50 cm). Those wires have
resistance and inductance, so they can't deliver a burst lasting nanoseconds.

| Moment | Who supplies the IC |
|---|---|
| Steady, nothing switching | The **long wire** supplies the small steady current and keeps the capacitor at 3.3 V |
| The instant the IC switches | The **capacitor supplies most of the burst** |
| Right after | The long wire **refills the capacitor** |

```
 Supply ════ long wire (slow) ════╦═══ VCC pin 16 ─┐
                                  ║                 │ IC switches → needs current now
                               [100 nF]             │
                                  ║                 │
 GND ═════════════════════════════╩═══ GND pin 8  ─┘
```

The burst loop is capacitor → pin 16 → through the chip → pin 8 → capacitor. It uses **both** pins. The shorter
the loop, the better, so:

1. One 100 nF ceramic per chip, connected straight across that chip's VCC and GND pins.
2. As close to the chip as possible, legs trimmed short. A capacitor 10 cm away is on the slow side of the wire.
3. Ceramic for speed; the bulk electrolytic handles longer, larger dips.

The capacitor also absorbs **overshoot** (brief spikes above 3.3 V when a burst stops), and stops one chip's noise
reaching the others through the shared supply. That is where the name **de-coupling** comes from.

**The numbers:** 100 nF at 3.3 V holds Q = 0.1 µF × 3.3 V = 0.33 µC. A switching burst of 50 mA for 10 ns takes
0.5 nC, so the voltage drops by ΔV = 0.5 nC / 100 nF ≈ **5 mV**. Stored energy: E = ½ × C × V² ≈ 0.5 µJ.

### Buying and reading capacitors

| | Ceramic | Electrolytic |
|---|---|---|
| Looks like | Small disc | Cylinder |
| Values | pF to ~1 µF | 1 µF to 10,000 µF |
| Polarity | None | Stripe = − |
| Speed | Very fast | Slower |

Ceramic codes are in pF: **104** = 10 + 0000 pF = **100 nF**; 103 = 10 nF; 105 = 1 µF. Choose a voltage rating
of at least 1.5–2× the circuit voltage (10 V+ for 3.3/5 V, 25 V for 12 V).

## 8. How an IC Uses Current, and Its Voltage Limits

### Charge isn't used up; energy is

Charge enters at the **VCC pin** and leaves at the **GND pin**, or through an output pin and an LED to the GND rail.
What's consumed is its **energy**: it falls from 3.3 V to 0 V, and that energy becomes **heat** (and **light** in
the LEDs). Like water turning a water wheel: the water flows out at the bottom; it loses height, not volume.

### Inside: CMOS transistor pairs

```
          VCC (3.3 V, pin 16)
             │
          [PMOS]   ← on for HIGH
             ├────────► output (internal node, or Q0–Q7 → LED)
          [NMOS]   ← on for LOW
             │
          GND (0 V, pin 8)
```

| Where the current goes | When | Size in our 74HC595 chain |
|---|---|---|
| **Charging tiny internal capacitances** (P = C × V² × f) | Each CLOCK or LATCH edge | Bursts of tens of mA for nanoseconds; microwatts on average |
| **Shoot-through** (PMOS and NMOS briefly both partly on) | During each switch | Part of the same bursts |
| **Driving LEDs** | Whenever LEDs are lit | ~4 mA per LED, up to 32 mA per chip |
| **Leakage** | Always | < 1 µA |

Where the energy goes for one lit LED (~4 mA, ~13 mW from 3.3 V):

| Part | Voltage | Power | Becomes |
|---|---|---|---|
| LED | ~2.0 V | ~8 mW | Light and some heat |
| 330 Ω resistor | ~1.2 V | ~4.8 mW | Heat |
| PMOS inside the chip | ~0.1 V | ~0.4 mW | Heat in the chip |

```
 Regulator ─► long wire ─► 100 nF ─► VCC pin 16
                                       │
                     ┌─────────────────┤
                     ▼                 ▼
          internal switching     output Q0–Q7 ─► 330 Ω + LED (heat + light)
                     │                                   │
                GND pin 8 ──────────► GND rail ◄─────────┘
                                         │
                                back to the regulator
```

### Can the voltage go above 3.3 V?

In normal operation, **no**. The regulator fixes 3.3 V, a capacitor only charges up to the voltage it's connected
to, an output's HIGH is at most VCC, and the IC has no booster. The only exception is **overshoot**: nanosecond
spikes of perhaps 3.4–3.5 V, which the decoupling capacitor absorbs.

| Part | Normal | Recommended | Absolute maximum |
|---|---|---|---|
| 74HC595 VCC | 3.3 V | 2–6 V | 7 V |
| ESP32 3V3 rail | 3.3 V | 3.0–3.6 V | **3.6 V** |
| ESP32 GPIO input | 0 / 3.3 V | 0–3.3 V | ~3.6 V |
| 74HC595 input | 0 / 3.3 V | 0–VCC | VCC + 0.5 V |

Real overvoltage only comes from a fault:

| Fault | Risk |
|---|---|
| Regulator fails as a short (5 V on the 3.3 V rail) | ESP32 destroyed |
| 5 V wire touches a GPIO | ESP32 pin damaged |
| 5 V sensor output to the ESP32 | Use a level shifter or voltage divider |
| Input above VCC (e.g. 5 V into a 74HC595 on 3.3 V) | Internal **protection diodes** conduct into VCC; the chip overheats or fails |

Every CMOS input has tiny protection diodes to VCC and GND. They survive brief static discharges, not a continuous
overvoltage. Above **3.45 V** on the 3V3 pin, unplug at once.

## 9. Finding Voltage Problems

Use a multimeter on **DC volts (V⎓)** (20 V range on a manual meter).

### 1. Supply voltage at the chip, not at the supply

Black probe on the chip's GND pin, red on its VCC pin. **Tip:** measure across the legs of the 100 nF capacitor:
same points, easier to hold, and no risk of shorting neighbouring IC pins.

| Point | Pins (red / black) | Good | Problem if |
|---|---|---|---|
| ESP32 3V3 | 3V3 / GND | 3.25–3.35 V | < 3.1 V |
| ESP32 VIN on USB | VIN / GND | 4.75–5.25 V | < 4.6 V (cable or adapter) |
| 74HC595 (each) | 16 / 8 | 3.2–3.35 V | < 3.0 V, or chip 3 lower than chip 1 |
| NE555 | 8 / 1 | 11.5–12.5 V | < 11 V |
| CD4017 | 16 / 8 | 11.5–12.5 V | < 11 V |
| 12 V adapter | + / − | 12–12.6 V | < 11.5 V |

Measure **idle** and with **everything on** (lamp test, or all products at quantity 5). A drop of more than ~0.2 V
under load means a weak wire, connection or supply.

### 2. Ground difference

Between the chip's GND pin and the supply's GND, everything on: **< 0.05 V is good; ≥ 0.1 V means a bad ground
path** (loose jumper, thin wire, bad joint). Check + the same way: supply + to chip VCC should read nearly 0 V.

### 3. Signal levels

| Reading | Meaning |
|---|---|
| Close to VCC | Clean HIGH |
| Close to 0 V | Clean LOW |
| In between (e.g. 1.5 V at 3.3 V) | Floating input, two outputs fighting, or an overloaded output |

| Point | Expected |
|---|---|
| 74HC595 pin 10 (MR) | 3.3 V (0 V = always resetting) |
| 74HC595 pin 13 (OE) | 0 V (3.3 V = outputs off) |
| 74HC595 output driving a lit LED | ~2.9–3.3 V |
| ESP32 button pin | 3.3 V released, 0 V pressed |
| CD4017 pins 13 and 15 | 0 V (floating = skips or freezes) |
| NE555 pin 3 | ~0 V / ~10.3 V (NE555 HIGH is ~1.7 V below VCC) |
| MOSFET gate | ~12 V on, 0 V off |
| MOSFET drain | ~0.1 V on; several volts up to ~12 V off |

### 4. Current without breaking the circuit

Measure the voltage across a resistor and divide: **I = V / R**. 4.0 V across 270 Ω ≈ 15 mA; 1.3 V across 330 Ω ≈
4 mA. 0 V across a resistor means no current: broken string, reversed LED, or MOSFET off.

### 5. What a multimeter can't see

It updates a few times a second, so nanosecond dips don't show. Signs of them:
- Serial monitor shows `Brownout detector was triggered`: add the bulk capacitor, use a better cable or adapter.
- Random glitches while every pin measures fine: check the 100 nF capacitors and shorten power wires.
- Tools that see dips: MIN/MAX mode on some meters, or a cheap oscilloscope (a DSO138 kit is a good learning tool).

### 6. Physical checks (power off)

- **Hot chip:** usually inserted backwards (check the notch), a shorted output, or 12 V on a 3.3 V part.
- **Continuity:** chip VCC to supply + beeps; chip GND to supply − beeps; VCC to GND must **not** beep.
- **Visual:** solder bridges, bent IC legs folded under the chip, reversed electrolytics.

**Order:** supply at the source → supply at each chip (idle and loaded) → ground difference → control pins (MR, OE,
RESET, CLOCK INHIBIT) → signal levels → resistor currents → serial monitor or oscilloscope.
