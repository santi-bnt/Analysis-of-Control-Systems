# Analysis of Control Systems

Control systems experiments, motor firmware, dashboards, MATLAB models, and a
Cartesian tic-tac-toe game for Meta Quest.

## Projects

| Folder | Contents |
| --- | --- |
| [Juego](Juego/README.md) | Tic-Cat-Toe: two Quest headsets, a Cartesian machine, BLE, UART, and LDR detection. Updated from [Sesgaro/meta_apps_control](https://github.com/Sesgaro/meta_apps_control). |
| [Project/ESP32](Project/ESP32/README.md) | Encoder position control with selectable P, PI, or PID and MQTT telemetry. |
| [Project/ESP_Web_page](Project/ESP_Web_page/README.md) | Next.js dashboard, FastAPI backend, and local MQTT broker configuration. |
| [Project/quest3](Project/quest3/README.md) | WebXR motor interface for the Quest browser. |
| [Project/ESP32_S3_Motor2_Test](Project/ESP32_S3_Motor2_Test/README.md) | Independent PlatformIO test for motor 2 on an ESP32-S3. |
| `Matlab/` | MATLAB scripts, Simulink models, and measured responses. |
| `practice1/` | Original PlatformIO PID exercise. |

Existing file names, code identifiers, device names, and protocol fields are kept
compatible with the hardware and applications. Documentation and interface text
are in English.

## Wiring and connections

- **Cartesian game:** [interactive wiring diagram](Juego/conexiones/index.html)
  and [wiring guide](Juego/conexiones/README.md). Download or open the HTML locally
  in a browser; GitHub displays its source. It needs no server or dependencies.
- **MQTT motor controller:** [interactive wiring diagram](Project/ESP32/Conexion_diagram/index.html)
  and [firmware wiring instructions](Project/ESP32/README.md#wiring).
- **Independent motor 2 test:** [ESP32-S3 connections](Project/ESP32_S3_Motor2_Test/README.md#wiring).

### Cartesian game pinout

| Connection | ESP32 X | ESP32-S3 Y |
| --- | --- | --- |
| Encoder A / B | GPIO32 / GPIO33 | GPIO21 / GPIO47 |
| BTS7960 RPWM / LPWM | GPIO25 / GPIO26 | GPIO5 / GPIO4 |
| BTS7960 R_EN / L_EN | GPIO27 / GPIO14 | GPIO12 / GPIO14 |
| UART TX / RX | GPIO17 / GPIO16 | GPIO17 / GPIO18 |
| Homing switch | GPIO4 → NO; GND → COM | GPIO15 → NC; GND → COM |
| LDR AO, cells 0–8 | — | GPIO13, 11, 10, 9, 8, 7, 6, 2, 1 |

Cross UART: **S3 TX17 → ESP32 RX16**, **ESP32 TX17 → S3 RX18**.
Connect a common ground between controllers, drivers, encoders, sensors, and
the motor supply. Use 3.3 V compatible signal levels. Power the LDR modules at
3.3 V and connect AO; DO is unused. Motor power goes to the drivers, not the ESP32.
See the diagrams for terminal-level power connections.

The game uses `Juego/firmware/eje_x/eje_x.ino` and
`Juego/firmware/esp32s3_gato/esp32s3_gato.ino`. Its Y geometry still needs hardware
calibration: the third row is calculated at 993 while `Y_MAX` is 896.7.

## Start the dashboard and motor

Run the following from the repository root in three PowerShell terminals.
Open Docker Desktop before starting the broker.

**1. Local MQTT broker**

```powershell
Set-Location Project/ESP_Web_page
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

**2. API backend**

```powershell
Set-Location Project/ESP_Web_page/backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

Configure the Python environment and `backend/.env` first if they are missing.
See the [dashboard guide](Project/ESP_Web_page/README.md).

**3. Frontend**

```powershell
Set-Location Project/ESP_Web_page/web
npm.cmd run dev
```

Open the URL printed by Next.js (port 3017 by default). If that port is occupied, stop the existing instance before starting another.
Use the `CONTROL_API_TOKEN` configured in `backend/.env`.
Keep the broker, backend, and frontend running while controlling the motor.

Press **Ctrl+C** in the frontend and backend terminals to stop them. From
`Project/ESP_Web_page`, stop the broker with:

```powershell
docker compose --env-file backend/.env -f compose.local.yaml down
```

## Build, upload, and monitor firmware

Choose the appropriate firmware folder before running PlatformIO:

- MQTT controller: `Project/ESP32`.
- Independent motor 2 test: `Project/ESP32_S3_Motor2_Test`.

Example for the MQTT controller, starting at the repository root:

```powershell
Set-Location Project/ESP32
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" device list
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run --target upload --upload-port COM3
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" device monitor --port COM3 --baud 115200
```

Replace `COM3` with the detected port. Close the monitor before uploading again.
If uploading reports `Wrong boot mode`, hold **BOOT** during `Connecting...` and
release it when `Writing at...` starts. If no port appears, check the data USB
cable, USB-serial driver, and Windows Device Manager.

The MQTT controller must share the broker's network and use matching Wi-Fi,
MQTT credentials, and `DEVICE_ID` in its private configuration. Keep credentials
out of Git. USB provides uploading and diagnostics; dashboard ONLINE status
requires a working MQTT connection. The ESP32 runs the control loop locally.

For game firmware, app builds, calibration, and BLE/UART details, see the
[Tic-Cat-Toe guide](Juego/README.md).
