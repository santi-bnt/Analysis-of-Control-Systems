# Projects

This folder contains the ESP32 motor controller, its dashboard and API, a
and a Quest WebXR client.

| Folder | Purpose |
| --- | --- |
| [`ESP32/`](ESP32/README.md) | Motor position controller, encoder, P/PI/PID, and MQTT telemetry. |
| [`ESP_Web_page/`](ESP_Web_page/README.md) | Next.js dashboard, FastAPI backend, and local Mosquitto broker setup. |
| [`quest3/`](quest3/README.md) | WebXR controller for the Quest browser. |

Wiring: [ESP32 motor controller](ESP32/Conexion_diagram/index.html). The
Cartesian game has its own wiring guide in [`../Juego/`](../Juego/README.md).

## Run the motor dashboard

The commands below start the dashboard stack for local development. Run them
from the repository root in three separate PowerShell terminals. Start Docker
Desktop before the MQTT broker. The frontend uses port **3017**.

### 1. Start the local MQTT broker

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page'
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

### 2. Start the API backend

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page\backend'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

If this is a new setup, follow the [dashboard setup guide](ESP_Web_page/README.md)
to create the Python environment and configure `backend/.env`.

### 3. Start the dashboard

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page\web'
npm.cmd run dev
```

Open <http://localhost:3017> and enter the `CONTROL_API_TOKEN` configured in
`Project/ESP_Web_page/backend/.env`. Keep all three services running while using
the motor. For API documentation, open <http://localhost:8002/docs>.

To stop the frontend and backend, press **Ctrl+C** in their terminals. Stop the
broker from `Project/ESP_Web_page`:

```powershell
docker compose --env-file backend/.env -f compose.local.yaml down
```

## Upload the ESP32 motor controller

Connect the ESP32 with a data-capable USB cable. In a PowerShell terminal:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP32'
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" device list
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" run --target upload --upload-port COM3
```

Replace `COM3` with the port reported by PlatformIO. To open the serial monitor,
run this in another terminal:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP32'
& "$env:USERPROFILE\.platformio\penv\Scripts\platformio.exe" device monitor --port COM3 --baud 115200
```

Close the monitor with **Ctrl+C** before uploading again. If upload reports
`Wrong boot mode`, hold **BOOT** while it says `Connecting...`, then release it
when `Writing at...` starts. If no port appears, check the USB cable, USB-serial
driver, and Windows Device Manager.

The ESP32 runs the control loop locally. To control it through the dashboard,
configure Wi-Fi, MQTT credentials, broker address, and `DEVICE_ID` in the local
`Secrets.h` file. The ESP32 and broker must be on the same network. Keep private
credentials out of Git. See the [ESP32 guide](ESP32/README.md) for wiring,
controller settings, and troubleshooting.
