# Motor position dashboard

The dashboard and API live here; the separate PlatformIO firmware is in
[`../ESP32/`](../ESP32/README.md). For hardware connections, open the
[motor wiring diagram](../ESP32/Conexion_diagram/index.html) locally in a browser.

## Start the broker, backend, and frontend

Open **Docker Desktop** and wait for it to be ready. From this folder:

```powershell
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

Keep Docker and MQTT running while using the motor. Start two more PowerShell
terminals from this folder.

**Backend:**

```powershell
Set-Location backend
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

**Frontend:**

```powershell
Set-Location web
npm.cmd run dev
```

On a new machine, create a Python virtual environment in `backend/.venv`,
install `backend/requirements.txt`, copy `backend/.env.example` to
`backend/.env`, and configure it. Run `npm.cmd install` in `web`.

Keep both terminals open and use the URL printed by Next.js (normally
<http://localhost:3017>). Enter `CONTROL_API_TOKEN` from `backend/.env`.
API documentation is at <http://localhost:8002/docs>. If port 3017 is occupied,
use the existing instance or stop it before starting another.

Press **Ctrl+C** in each terminal to stop the apps; use
`docker compose --env-file backend/.env -f compose.local.yaml down` here to stop
the broker. The broker must run and the ESP32 must be ONLINE for motor motion.

## Frontend URLs

Configure `web/.env.local` to match the backend:

```dotenv
NEXT_PUBLIC_BACKEND_URL=http://localhost:8002
NEXT_PUBLIC_WS_URL=ws://localhost:8002
```

## Connect the ESP32

The dashboard sends an angle to the REST API. The backend publishes a
`position` command to `motor/motor-01/command`. The MQTT callback in
`../ESP32/src/ComunicacionPagina.cpp` stores it; `ControlPosicion.cpp` reads it
with `ComunicacionPagina::leerComando()` and runs the selected P/PI/PID loop
every 10 ms. A communication task publishes to `motor/motor-01/telemetry`.

Quest and the normal dashboard share this flow. With both using the same
access token, Quest movement appears on the dashboard as confirmed telemetry.

1. Configure Wi-Fi, broker, MQTT credentials, and the TLS root certificate in
   `../ESP32/include/Secrets.h`; this file is excluded from Git.
2. Configure the same broker and backend MQTT credentials in `backend/.env`.
   Keep an access token and the limits `MIN_POSITION_DEG=-180`, `MAX_POSITION_DEG=180`.
3. Set REST/WebSocket URLs in `web/.env.local`.
4. Restart backend/frontend, build the ESP32 project, and upload its firmware.

Encoder GPIO32/33, RPWM/LPWM GPIO25/26, and enables GPIO27/14 connect one ESP32
to its motor. The implementation counts A rising edges and uses the calibrated
scale `CPR=11×34×4=1496`. Check physical angle after uploading. Startup position
defines 0°; dashboard angles are relative to that point.

Select exactly one P/PI/PID mode in `../ESP32/include/Config.h`; P is currently
enabled. Gains are `KP=4.5122`, `KI=21.0020`, `KD=0.1834`, with output limited
to −255…255 and 5 kHz, 8-bit PWM. These gains originated in a different plant
and require validation on the motor. A stop, lost MQTT connection, or expired
command disables PWM and the driver. Reset accepts a new command while keeping
the encoder zero. See the firmware guide for details.
