# Game wiring and connections

Open [index.html](index.html) directly in a browser. This standalone guide needs
no Node.js, installation, or server. It covers both axes, UART, encoders,
switches, the LDR array, and power. Printing includes every section.

The guide matches `firmware/eje_x/eje_x.ino` (ESP32 X) and
`firmware/esp32s3_gato/esp32s3_gato.ino` (ESP32-S3 Y).

- X switch: GPIO4 → NO, GND → COM; pressed = LOW.
- Y switch: GPIO15 → NC, GND → COM; pressed = HIGH.
- LDR AO, cells 0–8: **13, 11, 10, 9, 8, 7, 6, 2, 1** on the S3.
- LDR detection: drop from the ambient baseline, initial threshold 70,
  confirmation over 10 readings. Adjust over serial with `t <drop>`.
- UART: S3 TX17 → X RX16, X TX17 → S3 RX18, common GND; 115200 8N1.

Only the standalone HTML and this guide are required. Historical replacement
scripts, deployment packages, and nested Git metadata are not dependencies.
