# Earlier ESP32 X-axis firmware (PlatformIO)

Based on `Project/ESP32` from Analysis-of-Control-Systems. The original controller
documentation is retained in [README.origen.md](README.origen.md).

- `ComunicacionS3` replaces Wi-Fi/MQTT with UART to the ESP32-S3, keeping the
  `leerComando()` / `actualizarTelemetria()` control interface. Commands expire
  after 0.5 seconds and the motor shuts down.
- The setpoint arrives in mm and is converted to motor degrees with
  `MM_POR_VUELTA` (40 mm for the original GT2 20-tooth pulley). PID remains in degrees.
- This earlier variant has no automatic homing. The current game uses
  `../eje_x/eje_x.ino`, which has different calibration and homing support.

Pins and timing are in `include/Config.h`. Run from this folder:

```sh
pio run --target upload
pio device monitor -b 115200
```

The monitor shows setpoint, angle, RPM, error, and output. See the
[game README](../../README.md) for current protocols and wiring.
