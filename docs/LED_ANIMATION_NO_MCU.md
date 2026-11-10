# LED Animation Without a Microcontroller (555 + CD4017 + MOSFETs)

A learning project that animates **about 200 LEDs** with **no ESP32 and no code**. The animation comes from
three hardware blocks:

```
 ┌──────────┐ clock  ┌──────────┐ 10 outputs  ┌────────────┐  switch   ┌──────────────┐
 │ NE555    │ ─────► │ CD4017   │ ──────────► │ 10 MOSFETs │ ────────► │ 10 LED groups│
 │ (timer)  │ pulses │ (counter)│ one at a    │ (switches) │  20 LEDs  │ (~200 LEDs)  │
 └──────────┘        └──────────┘ time HIGH   └────────────┘  each     └──────────────┘
   sets SPEED          sets ORDER               provide CURRENT         the DESIGN
```

- **NE555** produces a steady stream of pulses. A potentiometer sets the speed.
- **CD4017** counts those pulses. Only one of its 10 outputs (Q0…Q9) is HIGH at a time, and each pulse moves
  the HIGH to the next output: Q0 → Q1 → … → Q9 → Q0. This produces a **running light (chaser)**.
- A **CD4017 output can supply only a few mA**, which is enough for one LED but not for 20. Each output therefore
  drives the gate of a **MOSFET**, and the MOSFET switches the full current for its group.

What you'll learn: astable timers, RC time constants, digital counters, MOSFETs as switches, series LED strings,
diode logic, and power budgeting.

---

## 1. Power: 12 V

At 12 V you can put **several LEDs in series** in one string, which wastes less power in resistors than 5 V would.

- Supply: **12 V / 1 A DC adapter** (barrel jack). In chaser mode only one group is on, so the draw is
  about 100 mA. 1 A gives headroom for experiments.
- The NE555 (4.5–16 V) and CD4017 (3–15 V) both run directly from 12 V. No regulator is needed.
- Optional: **1N5819 Schottky diode** in series with +12 V for reverse-polarity protection, and a
  **1 A fuse**.
- **100 µF electrolytic** across +12 V / GND at the input, plus **100 nF ceramic** at each IC's power pins.

---

## 2. Block 1: NE555 clock (speed)

```
 +12V ─────┬───────────────┬──────────┐
           │               │          │
         [R1 1kΩ]       pin 8 VCC   pin 4 RESET
           │
   pin 7 ──┤ DISCHARGE
           │
         [R2 10kΩ]
           │
         [VR 100kΩ pot]   ◄── speed knob
           │
   pin 6 ──┼── pin 2       (THRESHOLD and TRIGGER tied together)
           │
         [C 10µF] (+ to pin 2/6, − to GND)
           │
 GND ──────┴──── pin 1 GND
                 pin 5 CONTROL ── [10nF] ── GND
                 pin 3 OUTPUT ──────────────► CD4017 pin 14 (CLOCK)
```

**Speed formula:** f = 1.44 / ((R1 + 2·R2_total) · C)

| Pot position | R2_total | Frequency | Feels like |
|---|---|---|---|
| Minimum | 10 kΩ | 1.44 / (21 kΩ × 10 µF) ≈ **6.9 steps/s** | Fast chase |
| Maximum | 110 kΩ | 1.44 / (221 kΩ × 10 µF) ≈ **0.65 steps/s** | Slow walk |

**Tips**
- For faster animation, use C = 4.7 µF (doubles the speed). For slower, use 22 µF.
- The fixed 10 kΩ in series with the pot stops R2 reaching 0 Ω, which would stop the 555 from oscillating.
- Put one LED + 1 kΩ from pin 3 to GND while testing to see the clock blinking.

---

## 3. Block 2: CD4017 decade counter (order)

| Pin | Name | Connect to |
|---:|---|---|
| 16 | VDD | +12 V (+100 nF to GND) |
| 8 | VSS | GND |
| 14 | CLOCK | 555 pin 3 |
| 13 | CLOCK INHIBIT / ENABLE | GND (always counting) |
| 15 | RESET | GND (all 10 steps), or see "fewer steps" below |
| 12 | CARRY OUT | Not used |
| 3, 2, 4, 7, 10, 1, 5, 6, 9, 11 | **Q0 … Q9** | MOSFET channel 0 … 9 |

⚠️ The outputs are **not** in pin order: Q0 = 3, Q1 = 2, Q2 = 4, Q3 = 7, Q4 = 10, Q5 = 1, Q6 = 5, Q7 = 6,
Q8 = 9, Q9 = 11.

**Fewer steps:** to use only N groups (for example 6), connect **pin 15 RESET to output Q(N)** instead of GND.
For 6 groups that's Q6 = pin 5. When the count reaches Q6 the chip resets to Q0 at once, so only Q0–Q5 light.

⚠️ CMOS inputs must **never float**. Pins 13, 14 and 15 must always connect to something.

---

## 4. Block 3: MOSFET channel (one per output, × 10)

```
 +12V ──[270Ω]──►|──►|──►|──►|──┐   string 1: 4 red LEDs in series
 +12V ──[270Ω]──►|──►|──►|──►|──┤   string 2
 +12V ──[270Ω]──►|──►|──►|──►|──┤   string 3
 +12V ──[270Ω]──►|──►|──►|──►|──┤   string 4
 +12V ──[270Ω]──►|──►|──►|──►|──┤   string 5      → 20 LEDs per channel
                                │
                              DRAIN
 4017 Qn ──[100Ω]──┬────────── GATE      N-channel MOSFET (2N7000 or IRLZ44N)
                   │          SOURCE
               [100kΩ]          │
                   │            │
 GND ──────────────┴────────────┘
```

**How it works:** when Qn goes HIGH (12 V) the gate charges, the MOSFET turns on, and the strings' current flows
through it to GND. When Qn goes LOW, the 100 kΩ pull-down discharges the gate and the MOSFET turns off. This
arrangement is called a **low-side switch**: the MOSFET sits between the load and GND.

| Part | Why |
|---|---|
| **100 Ω gate resistor** | Limits the current spike that charges the gate, protecting the 4017 output. |
| **100 kΩ pull-down** | Keeps the MOSFET **off** at power-up and whenever the gate isn't driven. Required for the diode tricks in section 6. |
| **One resistor per string** | Each string needs its own current-limiting resistor. **Never** share one resistor between parallel strings: the string with the lowest Vf takes most of the current. |

### Choosing the MOSFET

| MOSFET | Package | Max current | Use for |
|---|---|---|---|
| **2N7000** | TO-92 (small) | 200 mA | Normal chaser with 5 strings (75 mA). Cheap. |
| **IRLZ44N** | TO-220 | 47 A | Fade/tail effect (section 6.2), 12 V LED strip segments, or groups larger than ~150 mA |

Pinouts:
- **2N7000**: flat face toward you, legs down: **S – G – D** (Source, Gate, Drain), left to right.
- **IRLZ44N**: labels toward you, legs down: **G – D – S**. The metal tab is also Drain.

### LED string resistor (at 12 V, ~15 mA)

R = (12 V − n × Vf) / 0.015 A

| LED color | Vf | LEDs per string | Resistor | Current |
|---|---|---:|---|---|
| Red / yellow / orange | ~2.0 V | 4 | **270 Ω** | (12 − 8) / 270 ≈ 15 mA |
| Green (standard) | ~2.1 V | 4 | **240 Ω** | ≈ 15 mA |
| Blue / white / pure green | ~3.1 V | 3 | **180 Ω** | (12 − 9.3) / 180 ≈ 15 mA |

Resistor power: 0.015² × 270 ≈ 0.06 W, so ¼ W resistors are fine.

Don't put too many LEDs in series. If n × Vf is close to 12 V, small variations in the supply make the LEDs
flicker or go dark. Leave **at least 2.5–4 V** for the resistor.

### LED count and current

| Layout | LEDs per channel | Channels | Total LEDs | Current (chaser, 1 channel on) |
|---|---|---:|---:|---|
| Red, 5 strings × 4 | 20 | 10 | **200** | 5 × 15 mA = **75 mA** |
| White, 6 strings × 3 | 18 | 10 | **180** | 6 × 15 mA = **90 mA** |
| Red, 8 strings × 4 (bounce mode, 6 channels) | 32 | 6 | **192** | 8 × 15 mA = **120 mA** |

Even if every channel were on at once: 10 × 75 mA = 750 mA, which is still within a 12 V / 1 A adapter.

---

## 5. Animation 1: Running light (chaser)

Wire blocks 1 → 2 → 3 as above. Arrange the 10 LED groups **in order** along your design: as segments of a
circle, rings from the center outward, letters of a name, or rays of a sun.

| Arrange groups as | Effect |
|---|---|
| Segments around a circle | Spinning wheel |
| Concentric rings, inner → outer | Ripple / pulse moving outward |
| Letters of a word | Letters light one after another |
| Arrow segments | Moving arrow (shop-sign style) |

---

## 6. Animations without code: diode logic

### 6.1 Bounce ("scanner") with 10 diodes

The light runs forward and then back. You need **6 channels (A–F)** and **10 × 1N4148 diodes**. Each 4017
output connects through a diode (anode at Q, cathode at the channel's gate node, before the 100 Ω):

| 4017 output | Q0 | Q1 | Q2 | Q3 | Q4 | Q5 | Q6 | Q7 | Q8 | Q9 |
|---|---|---|---|---|---|---|---|---|---|---|
| **Lights channel** | A | B | C | D | E | F | E | D | C | B |

Sequence: A B C D E F E D C B A B C… The light bounces back and forth.

```
 Q1 ──►|──┐
 Q9 ──►|──┴──[100Ω]──┬── GATE of channel B
                  [100kΩ]
                    GND
```

**Why it works:** the diodes form an **OR gate**. If either Q1 *or* Q9 is HIGH, the gate goes HIGH. The diodes
keep the two outputs from shorting into each other, and the 100 kΩ pull-down turns the MOSFET off when both
are LOW.

### 6.2 Comet tail / fade-out (RC on the gate)

Each group stays lit briefly after the light moves on, then fades out, so the light looks like a comet with a
tail.

```
 Qn ──►|──[1kΩ]──┬──────────[100Ω]── GATE  (use IRLZ44N for this mode)
    1N4148       │
              [4.7µF] ‖ [100kΩ]
                 │
                GND
```

- **Turn on (fast):** the 4.7 µF capacitor charges through 1 kΩ: τ = 1 kΩ × 4.7 µF ≈ 5 ms.
- **Turn off (slow):** the diode blocks the 4017 from pulling the capacitor down, so it can only discharge
  through 100 kΩ: τ = 100 kΩ × 4.7 µF ≈ **0.47 s**.
- While the gate voltage falls through the MOSFET's threshold (~1.5–2.5 V), the MOSFET is partly on, so the
  LEDs dim gradually instead of switching off.
- Longer tail: use 10 µF. Shorter tail: use 2.2 µF.

⚠️ While fading, the MOSFET is **partly on and gets warm**. Use an **IRLZ44N** (TO-220) here, not a 2N7000.

### 6.3 Shortened sequence

Use the **RESET → Q(N)** trick from section 3 to make a 3-, 4- or 6-step animation that fits your shape.

---

## 7. Animation 2: Twinkle (several 555 timers)

For a "starry sky" you don't need the 4017 at all:

- Build **3–4 separate 555 astable circuits** with slightly different timing capacitors (for example 4.7 µF,
  6.8 µF, 10 µF, 15 µF).
- Connect each 555's pin 3 to its own MOSFET channel (100 Ω gate resistor + 100 kΩ pull-down).
- **Mix the LEDs of each channel randomly** across the design.

Because the frequencies don't line up, the pattern takes a long time to repeat and looks random. Add the RC
tail (6.2) to make each star fade instead of blinking hard.

Tip: a **NE556** is two 555s in one 14-pin chip, so you need fewer parts.

---

## 8. Bill of Materials (200 red LEDs, 10-channel chaser)

| # | Component | Qty | Notes |
|---|---|---:|---|
| 1 | NE555 timer (DIP-8) | 1 | + 1 for each extra twinkle channel |
| 2 | CD4017 decade counter (DIP-16) | 1 | CD4017BE (CMOS, 3–15 V) |
| 3 | DIP-8 and DIP-16 IC sockets | 1 each | Recommended |
| 4 | 2N7000 MOSFET | 10 | Or IRLZ44N if you build the fade tail |
| 5 | 5 mm LED (red/yellow) | 200 | Diffused LEDs look better in designs |
| 6 | 270 Ω resistor ¼ W | 50 | One per 4-LED string |
| 7 | 100 Ω resistor | 10 | Gate resistors |
| 8 | 100 kΩ resistor | 10 | Gate pull-downs |
| 9 | 1 kΩ + 10 kΩ resistor | 1 each | 555 timing |
| 10 | 100 kΩ potentiometer | 1 | Speed knob |
| 11 | 10 µF electrolytic capacitor | 1 | 555 timing |
| 12 | 10 nF ceramic capacitor | 1 | 555 pin 5 |
| 13 | 100 nF ceramic capacitor | 2 | Decoupling at each IC |
| 14 | 100 µF / 25 V electrolytic | 1 | Supply input |
| 15 | 1N4148 diodes | 20 | Bounce and tail effects |
| 16 | 4.7 µF electrolytic | 10 | Tail effect (optional) |
| 17 | 12 V / 1 A DC adapter + barrel jack | 1 | |
| 18 | 1N5819 diode, 1 A fuse | 1 each | Protection (optional) |
| 19 | Perfboard / stripboard, wire | — | Plus MDF, acrylic or cardboard for the design |

---

## 9. Build Order (test each step)

1. **555 only:** test LED on pin 3. Turning the pot should change the blink speed.
2. **Add the CD4017:** put one test LED + 1 kΩ on Q0, Q1 and Q2. They should light in sequence.
3. **One MOSFET channel:** wire one 4-LED string to channel 0. Check that it lights when Q0 is HIGH.
4. **Measure** with the multimeter in series with the string. You should see about 15 mA.
5. **Add all 10 channels**, then all strings.
6. **Mount** the LEDs into your design. Put frosted acrylic or tracing paper in front of them to soften
   the light.
7. Try the **bounce diodes**, then the **tail capacitors**.

---

## 10. Troubleshooting

| Symptom | Likely cause |
|---|---|
| Nothing moves | 555 not oscillating: check pins 4 and 8 on +12 V and pins 2 and 6 tied together. Check that the 10 µF capacitor isn't reversed. |
| Sequence jumps randomly | CD4017 pin 13 or pin 15 is floating. Missing 100 nF decoupling capacitor. Long, noisy clock wire. |
| A group never turns off | Missing 100 kΩ gate pull-down, or Drain and Source swapped. |
| A group never turns on | Wrong MOSFET pinout. LED string reversed. A broken LED in the string: **one open LED turns off the whole series string**. |
| One string dim, others bright | Wrong resistor value, or mixed LED colors in one string. |
| MOSFET hot | Normal in fade mode with a 2N7000: switch to IRLZ44N. In plain chaser mode, check for a short. |
| Whole design flickers | Weak adapter or thin GND wire. Add the 100 µF capacitor at the input. |

---

## 11. Next Steps

- **Master dimmer:** a second 555 running at ~1 kHz as a PWM generator drives one MOSFET in the common GND path
  of all channels. Turning a knob changes overall brightness.
- **Sound-reactive:** an electret microphone + LM386 amplifier output can clock the 4017, so the light steps
  forward on each beat.
- **20 channels:** cascade two CD4017s using a CD4013 flip-flop to choose which counter is active.
- **12 V LED strip segments** (non-addressable) can replace 5 mm LEDs. They already have resistors built in.
  Cut at the marks (every 3 LEDs, about 20 mA per segment) and use IRLZ44N MOSFETs.

---

## 12. Assembly on a Big Board

How to go from a design drawing to a finished board with 10 groups of 20 LEDs (strings of 4).

### 12.1 Plan on paper first

1. **Draw the design at full size** and mark a dot for every LED. Spacing of **2–3 cm** looks smooth (up to 4 cm
   with a diffuser). 200 LEDs at ~2.5 cm fit on a board of about **60 × 60 cm**.
2. **Divide the dots into 10 groups** in the order the animation should run:

   | Design | How to split into 10 groups |
   |---|---|
   | Sun / circle | 10 rays or pie slices → spinning |
   | Concentric rings | ring 1 (center) … ring 10 (outer) → ripple |
   | Name / word | 1 group per letter (≤ 10 letters) |
   | Heart | horizontal bands bottom → top → "filling" heart |

3. **Divide each group into strings** of 4 LEDs that are **physically close and in the same group**. Label every
   dot, for example **G3-S2** (group 3, string 2). If a group's count isn't a multiple of 4, use some 3-LED
   strings with a **390 Ω** resistor: (12 − 6) / 0.015 ≈ 400 Ω.

### 12.2 Prepare the board

| Material | Good for |
|---|---|
| **6 mm MDF or plywood** (recommended) | Strong, easy to drill and paint |
| Foam board (5 mm) | Light, cuts with a knife, but weak for heavy wiring |
| Black acrylic | Looks premium, but cracks when drilled; use a step bit |

1. Print the design full size (tile A4 pages and tape them together) and tape it to the board.
2. Mark each hole through the paper with an awl, and write the group label next to it on the **back**.
3. Drill a 2 mm pilot hole, then **5 mm**. Clamp scrap wood behind the board to stop splintering.
4. **Paint the front** (matte black or white) before inserting LEDs.

### 12.3 Mount the LEDs

- Insert each LED **from the front**, legs out the back.
- **Orientation trick:** within a string, rotate each LED so its **long leg (+) points toward the previous LED**.
  Then each short leg (−) bends straight over to the next LED's long leg (+).
- Fix each LED with a drop of **hot glue** on the back. Optional: chrome LED bezels on the front.

### 12.4 Wire the back

One string:
```
 +12V bus ──[270Ω]──(+)LED1(−)──(+)LED2(−)──(+)LED3(−)──(+)LED4(−)──► channel wire G3
              ↑ resistor right at the first LED          legs bent and soldered directly
```

Whole board (back view):
```
 ┌──────────────────────────────────────────────────────────┐
 │  ════════ +12V bus (bare tinned copper wire / red) ════════ │  ← runs around the board
 │   │      │       │        │                              │
 │  [R]    [R]     [R]      [R]   ← one resistor per string │
 │   ●─●─●─●  ●─●─●─●  ●─●─●─●  ●─●─●─●    LED strings        │
 │         │        │        │        │                     │
 │         └── G0 ──┘        └── G1 ──┘   ← string ends of the same
 │              │                 │         group joined into 1 channel wire
 │              └────────┬────────┘                          │
 │                       ▼                                   │
 │        ┌───────────────────────────────┐                  │
 │        │  CONTROLLER (perfboard)       │                  │
 │        │  555 + 4017 + 10 MOSFETs      │ ◄── 12V DC jack  │
 │        │  screw terminals: +12V, G0…G9 │                  │
 │        └───────────────────────────────┘                  │
 └──────────────────────────────────────────────────────────┘
```

- **+12V bus:** bare tinned copper wire (0.8–1 mm) or red 22 AWG as a loop or spine across the back. Each string's
  resistor solders onto it.
- **Channel wires:** join the (−) end of every string in a group into one wire back to that MOSFET's drain. Use
  **one wire color per group**, or label both ends with masking tape (G0…G9).
- A **12-wire ribbon cable** (10 channels + 12V + spare) between the LED area and the controller keeps it tidy.
- Mount the **controller perfboard** on the back with standoffs. **Screw terminals** for the channel wires make
  repairs easy.
- Insulate joints with **heat-shrink tubing**; crossing bare legs will short.
- Secure wires with hot glue or adhesive cable-tie mounts every 10–15 cm.

### 12.5 Test while building

1. **Each string before soldering it in:** 12V through the resistor to the first LED, (−) end to GND. All 4 light.
   A multimeter in **diode mode** lights a single LED to check it.
2. **Each group:** touch its channel wire to GND. The whole group lights, even without the controller.
3. Connect the controller **one group at a time**.

A dark string means one LED in it is **broken or reversed**: one open LED turns off the whole series string.

### 12.6 Finishing

- **Diffuser:** frosted acrylic or tracing paper **1.5–3 cm in front**, on spacers.
- **Frame:** wooden strips around the edge hide the wiring depth (~3–4 cm) and hold the diffuser.
- Put the **DC jack and speed knob** on an edge you can reach.
- Hang it with two D-rings or picture wire.

**Tools:** soldering iron (~350 °C), helping hands, hot glue gun, drill with 2 mm and 5 mm bits, wire stripper,
multimeter. 12 V is safe to touch, but **a short circuit heats wires quickly**, so keep the **1 A fuse** in the
+12V line. Solder in a ventilated room.

**Order of work:** paper plan → drill → paint → insert LEDs → wire strings → test strings → channel wires → test
groups → controller → diffuser and frame.
