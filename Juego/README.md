# Tic-Cat-Toe: Cartesian tic-tac-toe

Two Meta Quest headsets play tic-tac-toe using an X/Y Cartesian machine.
During a turn, the player moves the machine head over a virtual board. When
the player fires or the 20-second timer expires, the machine waits for the head
to settle and reads the LDR array. A free detected cell receives that player's
piece; a missed or occupied cell loses the turn. Control then passes to the
other headset.

Imported from [Sesgaro/meta_apps_control](https://github.com/Sesgaro/meta_apps_control),
commit `126db217a2f8515904fb1f4da31aedec4dbaba97`. The X controller originated in
[Analysis-of-Control-Systems](https://github.com/santi-bnt/Analysis-of-Control-Systems),
with UART replacing Wi-Fi/MQTT.

```text
Quest 1 ─┐ BLE                    UART (TX/RX/GND)
Quest 2 ─┴──► ESP32-S3 ◄──────────────────────► ESP32
              Game, turns, timer, board         X rack-and-pinion motor
              Y T8 lead-screw motor             X position telemetry
              Fixed analog LDR array, BLE
```

## Files

| Folder | Purpose |
| --- | --- |
| `firmware/esp32s3_gato/` | Current ESP32-S3 firmware (Arduino); `gato.h` contains hardware-independent rules. |
| `firmware/eje_x/` | Current ESP32 X firmware (Arduino), with homing and UART. |
| `firmware/esp32_eje_x/` | Earlier PlatformIO variant retained from upstream; no automatic homing. |
| `firmware/pruebas/` | Native C++ tests for the game rules. |
| `conexiones/` | Standalone wiring diagram and guide. |
| `lib/` | Flutter connection, player, status, and spectator panels. |
| `android/app/src/quest/` | Immersive board using Meta Spatial SDK. |
| `modelos/`, `tool/` | Source models and conversion utilities. |

## Wiring and connections

Open [the interactive wiring diagram](conexiones/index.html) locally in a browser.
It includes an overview, both axes, UART, switches, LDR array, and power wiring.
The print button prints every section. See also [the wiring guide](conexiones/README.md).

| Signal | ESP32 X | ESP32-S3 Y |
| --- | --- | --- |
| Encoder A / B | 32 / 33 | 21 / 47 |
| BTS7960 RPWM / LPWM | 25 / 26 | 5 / 4 |
| BTS7960 R_EN / L_EN | 27 / 14 | 12 / 14 |
| UART TX / RX | 17 / 16 | 17 / 18 |
| Homing switch input | GPIO4 → NO | GPIO15 → NC |
| Switch common | GND → COM | GND → COM |
| LDR AO, cells 0–8 | — | 13, 11, 10, 9, 8, 7, 6, 2, 1 |

- Cross UART: **S3 TX17 → X RX16**, **X TX17 → S3 RX18**, common GND;
  115200 baud, 8N1, 3.3 V logic.
- Share ground between both controllers, drivers, encoders, sensors, and supply.
- X uses a normally open switch: released HIGH, pressed LOW. Y uses a normally
  closed switch: released LOW, pressed HIGH. Both use `INPUT_PULLUP`.
- Cell index = row × 3 + column; row 0 is minimum Y, column 0 is minimum X.
- Supply LDR modules at **3.3 V** and connect AO. DO is unused. Encoder A/B
  must also be compatible with 3.3 V inputs.
- LDR readings are 12-bit (0–4095). Detection requires a drop greater than
  `ldrBajada` from the filtered ambient baseline for 10 readings. The initial
  drop threshold is 70; adjust it with `t <drop>` over USB serial at 115200.

## Playing

1. Power both controllers. Each current Arduino firmware searches for its
   homing switch and establishes zero; the search timeout is 60 seconds.
2. Open **Tic-Cat-Toe** on each Quest, select **Connect**, choose `ESP32S3-Gato`,
   and assign **Player X** to one headset and **Player O** to the other.
3. Select **New game**. The starting player alternates between games.
4. Choose a **Game mode**. The 3D board opens automatically on connection, or
   through **Open 3D board**. The table turns pink during your turn.
5. In **Slingshot** mode, grab the red ball, pull it toward you, and release.
   Dots preview the trajectory and the head moves to the predicted landing
   position. A valid shot falls through the cell; a missed shot bounces.
6. In **Claw machine** mode, tilt the left joystick to move the head; releasing
   it stops movement. Press the red button on the right to drop the claw.
7. After firing or timing out, green marks a placed piece, red marks a missed
   or occupied cell, and blue marks the cell currently detected by the LDRs.
8. **Back to 2D** returns to the panel. The BLE connection stays active in the
   background. Opening 3D minimizes the panel so hands can interact with 3D.

Each headset chooses its own mode. Players face each other across the table;
player O is at maximum Y with the table rotated half a turn. The phone is a
spectator: it shows the 2D board but cannot move the head or start a game.
**Try without ESP** on Quest simulates both players on one headset.

The table includes a turn timer (red for the last five seconds), a yellow
landing marker, a gold winning line, confetti, and sound effects. Session scores
appear on the sign and panel. Player and mode preferences are saved. Calibration
survives returning to 2D until the app closes. BLE reconnects every three seconds
for up to ten attempts after an unexpected disconnect.

## Shared board calibration

With the game waiting or finished, both players press **Calibrate** within two
seconds of each other, using the sign or the cyan button. One press is enough
if only one headset is connected. The board turns green and the S3 moves the
head to cell 0. Each player points at and touches the real head with an index
fingertip, holding still for 0.8 seconds. When both have touched it, the head
moves to cell 8 and the process repeats. Each headset derives table rotation,
scale, and height from the two points. Simulation displays a floating marker.

Measure `HEAD_TOUCH_M` in `GameActivity3D.kt`: the height of the top of the real
head above the table. Each headset relays head and hand poses through the S3.
The other player appears as a gray X cat or orange O cat; simulation mirrors
your own avatar. Cat paws follow hand closure using body tracking permission.

## Hardware calibration values

| Constant | File | Current value |
| --- | --- | --- |
| `MM_POR_VUELTA_Y` | `esp32s3_gato.ino` | 8 mm, T8 lead screw |
| `MM_POR_VUELTA_X` | `eje_x/eje_x.ino` | 11.72, upstream calibration |
| `X_MIN/X_MAX`, `Y_MIN/Y_MAX` | S3 | X: 0–246.8; Y: 0–896.7 |
| `X_CASILLA0`, `X_PASO`, `Y_CASILLA0`, `Y_PASO` | S3 | 26, 87, 347, 323 |
| `turnoMs`, `quietoMs`, `resolverMaxMs` | S3 game configuration | 20 s, 0.3 s, 12 s |
| `TOLERANCIA_MM` | S3 | 2 mm |
| `ldrBajada`, `LDR_CONFIRMA` | S3 | 70, 10 readings |

These geometry values come from upstream. Its comments say Y is not yet in
physical millimeters; the calculated third row is Y=993, exceeding
`Y_MAX=896.7`. Review calibration on the actual machine before relying on it.
Geometry is sent in BLE state, so both app views adjust to the S3 configuration.
Update the X firmware limits to match when changing the travel range.

Both control loops work in motor degrees and convert at the mm interface.
Y gains are kp=7.6, ki=51, kd=0.135; X gains are kp=4.5122, ki=21.0020,
kd=0.1834. Verify them with the actual carriage load. The Y lead screw's
approximate 17 mm/s speed motivated the 20-second turn limit.

## Protocols

BLE uses service `7a1d1000-…`; see `lib/ble/game_protocol.dart` and the S3.

| Characteristic | Payload |
| --- | --- |
| `7a1d1001-…`, write without response | `C0 player xLo xHi yLo yHi`: int16 tenths of mm; accepted only during that player's turn. `C1 01`: new game. `C2 player`: fire. `C3 player`: calibrate/cancel. `C4 player step`: calibration touch confirmed. |
| `7a1d1003-…`, write/notify | `D0 player` plus 28 bytes of head, hand, and ball poses in board mm (`pose_frame.dart`). S3 relays the latest pose per player at 20 Hz. |
| `7a1d1002-…`, read/notify | State every 100 ms, 47 bytes: phase, turn, time, board, winner, last shot, positions/setpoints, LDR cell, UART link, geometry, calibration step and participants. |

UART uses text lines with NMEA-style XOR checksums at 20 Hz:

```text
S3 → X: $R,<X setpoint mm>,<enabled>*HH
X → S3: $T,<position mm>,<setpoint mm>,<pwm>*HH
X → S3: $H,<homing state>*HH
```

Homing states: 0 searching, 1 ready, 2 failed. X disables the motor after
0.5 seconds without a command. S3 marks the X link lost after 0.5 seconds
without telemetry; shots then resolve using LDRs after `resolverMaxMs` expires.

## Build and upload

Run commands from this `Juego` folder. Replace serial ports for your system.

```sh
# Native game-rule tests
g++ -std=c++17 -Wall -Wextra -Ifirmware/esp32s3_gato firmware/pruebas/test_gato.cpp -o test_gato
./test_gato

# ESP32-S3, Arduino ESP32 core 3.x
arduino-cli compile --fqbn esp32:esp32:esp32s3 firmware/esp32s3_gato
arduino-cli upload -p /dev/ttyACM0 --fqbn esp32:esp32:esp32s3 firmware/esp32s3_gato

# Two headsets without motors: real BLE/poses, simulated axes and sensors
arduino-cli compile --upload -p /dev/ttyACM0 --fqbn esp32:esp32:esp32s3 --build-property "compiler.cpp.extra_flags=-DSIMULAR=1" firmware/esp32s3_gato

# Current ESP32 X firmware, with homing
arduino-cli compile --fqbn esp32:esp32:esp32 firmware/eje_x
arduino-cli upload -p /dev/ttyUSB0 --fqbn esp32:esp32:esp32 firmware/eje_x

# Apps
flutter build apk --release --flavor quest
flutter build apk --release --flavor phone
```

Upload without `SIMULAR` to restore hardware mode. The earlier non-homing
PlatformIO firmware can be built separately from `firmware/esp32_eje_x` with
`pio run --target upload`. The app ID is `com.sesgaro.gato` (`.quest` on Quest),
allowing it to coexist with the angle-control app. S3 supports three BLE clients.

## Model conversion

Blender source models are in `modelos/`; generated assets go to
`android/app/src/quest/assets/models/`:

```sh
python tool/modelos_a_glb.py  # Pieces and slingshot; extracts each object
python tool/pata_a_glb.py     # Two-bone paws, four baked closure frames per color
python tool/cabeza_a_glb.py   # Cat heads and sprite faces for game reactions
```

Faces show normal, miss, loss, hit, and win reactions. Background music is
optional: a local `android/app/src/quest/res/raw/musica.ogg` is excluded from Git.
