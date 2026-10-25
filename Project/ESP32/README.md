# ESP32 motor position controller

One ESP32 controls a DC motor with an encoder and a BTS7960 driver. Open this
folder as a PlatformIO project.

| File | Purpose |
| --- | --- |
| `src/main.cpp` | Starts control and communication. |
| `src/ControlPosicion.cpp` | Encoder, selectable P/PI/PID loop, BTS7960 output. |
| `src/ComunicacionPagina.h/.cpp` | Wi-Fi, MQTT commands, and telemetry. |
| `include/Config.h` | Pins, gains, encoder resolution, and timing. |
| `include/Secrets.h` | Private credentials and TLS certificate; excluded from Git. |

## Wiring

Open the [interactive wiring diagram](Conexion_diagram/index.html) in a local browser.

| Signal | ESP32 GPIO |
| --- | --- |
| Encoder A / B | 32 / 33 |
| BTS7960 RPWM / LPWM | 25 / 26 |
| BTS7960 R_EN / L_EN | 27 / 14 |
| Encoder and driver GND | Common GND |

Connect the two motor leads to the BTS7960 motor outputs, and the motor supply
to the driver's power input. Share ground between ESP32, encoder, driver, and
motor supply. Supply driver logic and encoder according to their specifications.
Do not power the motor from the ESP32. Encoder A/B inputs must use **3.3 V**
compatible levels; level-shift 5 V outputs before connecting them.

## Control

Enable exactly one `CONTROL_MODE_*` in `include/Config.h`; P is currently enabled.
The loop computes setpoint minus measured angle every 10 ms. PI/PID include
conditional integral anti-windup. Gains are `KP=4.5122`, `KI=21.0020`,
`KD=0.1834`, copied from an analog-input/DAC exercise for another plant and
still requiring validation on this motor. Output is limited to −255…255;
the driver uses 5 kHz, 8-bit PWM. Telemetry is published every 100 ms.

The encoder counts A rising edges and reads B for direction. The retained
scale is `PPR=11`, `REDUCCION=34`, and `CPR=11×34×4=1496`. This is a calibrated
scale factor, not a claim of x4 edge decoding in this implementation. It doubled
the earlier 748 setting after an indicated 180° was observed to be about 90°
physically. Check the physical rotation after uploading.

Startup position defines 0°; all setpoints are relative to it. The incremental
encoder does not retain an absolute position across restarts. Motor and encoder
directions were inverted together: positive output uses RPWM, negative output
uses LPWM, and the encoder increments when B is HIGH. Upload the firmware for
source changes to take effect on the connected board.

The motor waits for a valid dashboard command. A stop, MQTT disconnect, or
expired command disables PWM and driver enables. Reset allows a new command
without clearing the encoder count. No automatic timed motion test runs.

## Communication

`setup()` calls `ComunicacionPagina::iniciar()`. Each control step reads its
setpoint and motion permission with `leerComando()`. `actualizarTelemetria()`
stores setpoint, angle, error, and output for a separate MQTT publishing task.

Configure Wi-Fi, broker, and credentials in `include/Secrets.h`. For a local
broker use your PC's LAN IP, port 1883, and `MQTT_USE_TLS=false`; the ESP32
cannot reach the PC using `127.0.0.1`. For TLS use `MQTT_USE_TLS=true` and
configure `MQTT_ROOT_CA_PEM`. Follow the [dashboard guide](../ESP_Web_page/README.md).

## Build, upload, and monitor

Run from this folder in PowerShell; replace `COM3` with the detected port:

```powershell
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" device list
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run --target upload --upload-port COM3
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" device monitor --port COM3 --baud 115200
```

Alternatively use **PlatformIO: Build** and **PlatformIO: Upload**. The serial
monitor shows setpoint, angle, RPM, error, and output. Close it with **Ctrl+C**
before uploading again. If the board reports `Wrong boot mode` or stays at
`Connecting...`, hold **BOOT** until writing starts. If no USB port appears,
check a data-capable cable, another USB port, and the USB-serial driver in
Windows Device Manager.

## Dashboard remains OFFLINE

USB supports uploading and serial diagnostics; ONLINE status comes from MQTT.
The ESP32 needs Wi-Fi and broker connectivity, and the backend must connect to
the same broker. Start Docker Desktop and the local broker as described in the
dashboard guide. Serial output reports Wi-Fi reconnects, MQTT attempts, and a
successful `motor-01` connection. Upload again after changing firmware or secrets.

Before applying a load, verify that a positive command increases the measured
angle. Mismatched motor and encoder directions cause the loop to drive away
from its setpoint.
