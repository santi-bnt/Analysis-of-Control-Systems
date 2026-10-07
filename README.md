# Analysis of Control Systems

Each project is kept in its own folder:

- `Matlab/` — MATLAB scripts, Simulink models, and practice data.
- `practica1/` — the original PlatformIO practice project.
- `Proyecto/` — control final del motor DC, distribuido en:
  - `ESP32/` — firmware en PlatformIO para control de posición local con encoder, BTS7960 y telemetría MQTT. Consulta [Proyecto/ESP32/README.md](Proyecto/ESP32/README.md).
  - `pagina_esp/` — dashboard web (`web/`) y backend API/MQTT (`backend/`). Consulta [Proyecto/pagina_esp/README.md](Proyecto/pagina_esp/README.md).
  - `quest3/` — interfaz WebXR para Quest 3. Consulta [Proyecto/quest3/README.md](Proyecto/quest3/README.md).

Abre `Proyecto/ESP32/` de forma independiente en PlatformIO para compilar el firmware. Para encender el dashboard o la simulación, sigue [Proyecto/pagina_esp/README.md](Proyecto/pagina_esp/README.md).

## Encender la página

Para el motor real con MQTT local, abre **Docker Desktop** y arranca el broker:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Proyecto\pagina_esp'
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

Después abre dos terminales PowerShell y deja ambas abiertas.

**Terminal 1 — backend:**

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Proyecto\pagina_esp\backend'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

**Terminal 2 — página:**

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Proyecto\pagina_esp\web'
npm.cmd run dev
```

Abre [http://localhost:3000](http://localhost:3000). La clave de acceso es el valor de `CONTROL_API_TOKEN` en `Proyecto/pagina_esp/backend/.env`. Pulsa **Ctrl+C** en cada terminal para apagar.

Actualmente la simulación está desactivada: el backend espera a la ESP32 por MQTT. Para probar sin hardware, sigue las instrucciones de simulación en [Proyecto/pagina_esp/README.md](Proyecto/pagina_esp/README.md).
