# Analysis of Control Systems

Each project is kept in its own folder:

- `Matlab/` — MATLAB scripts, Simulink models, and practice data.
- `practica1/` — the original PlatformIO practice project.
- `ESP32/` — position control of a DC motor with an encoder and BTS7960 driver, using one ESP32. The control runs locally; the MQTT task receives the webpage reference and reports telemetry.
- `pagina_esp/` — the separate dashboard (`web/`) and API/MQTT bridge (`backend/`).
- `quest3/` — interfaz WebXR para agarrar la bolita y controlar el mismo motor desde Quest 3. Consulta [quest3/README.md](quest3/README.md).

Open `ESP32/` by itself in PlatformIO to build the firmware. For the dashboard and its local simulation, follow [pagina_esp/README.md](pagina_esp/README.md).

## Encender la página

Para el motor real con MQTT local, abre **Docker Desktop** y arranca el broker:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\pagina_esp'
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

Después abre dos terminales PowerShell y deja ambas abiertas.

**Terminal 1 — backend:**

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\pagina_esp\backend'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

**Terminal 2 — página:**

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\pagina_esp\web'
npm.cmd run dev
```

Abre [http://localhost:3000](http://localhost:3000). La clave de acceso es el valor de `CONTROL_API_TOKEN` en `pagina_esp/backend/.env`. Pulsa **Ctrl+C** en cada terminal para apagar.

Actualmente la simulación está desactivada: el backend espera a la ESP32 por MQTT. Para probar sin hardware, sigue las instrucciones de simulación en [pagina_esp/README.md](pagina_esp/README.md).
