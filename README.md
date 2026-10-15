# Analysis of Control Systems

Repositorio de prácticas y proyectos de sistemas de control.

## Carpetas

- `Matlab/`: archivos y modelos de MATLAB.
- `practica1/`: práctica original de PlatformIO.
- `Project/`: proyecto integrado del motor DC:
  - `ESP32/`: firmware PlatformIO, encoder, control y MQTT. Guía: [Project/ESP32/README.md](Project/ESP32/README.md).
  - `ESP_Web_page/`: frontend Next.js, backend FastAPI y configuración del broker local. Guía: [Project/ESP_Web_page/README.md](Project/ESP_Web_page/README.md).
  - `quest3/`: interfaz para Oculus/Meta Quest 3.
  - `mosquitto.local.conf` y `mosquitto.local.acl`: configuración del broker de desarrollo.
- `Proyecto_PID/`: archivos de la variante de análisis PID.

## Iniciar la página y el motor

Usa tres terminales PowerShell. Abre Docker Desktop antes de iniciar el broker.

### 1. Broker MQTT local

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page'
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

### 2. Backend API

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page\backend'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

Deja esta terminal abierta. Si `.venv` o `backend/.env` no existen, consulta el README del backend para crear el entorno y configurar las variables locales.

### 3. Frontend

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page\web'
npm.cmd run dev
```

La página usa el puerto alternativo **3017** para evitar el 3000 ocupado. Abre [http://localhost:3017](http://localhost:3017). La clave de acceso es `CONTROL_API_TOKEN` en `Project/ESP_Web_page/backend/.env`.

Para apagar los servicios, pulsa **Ctrl+C** en las terminales del backend y frontend, y ejecuta:

```powershell
docker compose --env-file backend/.env -f compose.local.yaml down
```

desde `Project/ESP_Web_page`.

## Compilar, cargar y ver la ESP32

Conecta la ESP32 por USB con un cable de datos y comprueba qué puerto detecta PlatformIO:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP32'
& 'C:\Users\santi\.platformio\penv\Scripts\platformio.exe' device list
```

Sustituye `COM3` por el puerto que aparezca. Carga el firmware:

Ejecuta la carga desde la carpeta del firmware. Este bloque también funciona si tu terminal está abierta en `backend`:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP32'
& 'C:\Users\santi\.platformio\penv\Scripts\platformio.exe' run --target upload --upload-port COM3
```

Abre el monitor serial en otra terminal; incluye de nuevo la carpeta del firmware porque cada terminal tiene su propio directorio actual:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP32'
& 'C:\Users\santi\.platformio\penv\Scripts\platformio.exe' device monitor --port COM3 --baud 115200
```

La ESP32 debe usar la misma red local que el broker y tener configurados WiFi, credenciales MQTT y `DEVICE_ID` en sus archivos locales de configuración. No subas contraseñas al repositorio. Si la carga muestra `Wrong boot mode`, mantén presionado **BOOT** en la ESP32 cuando aparezca `Connecting...` y suéltalo cuando empiece `Writing at...`. Si `device list` no muestra ningún puerto, revisa el cable USB, el controlador USB-serial y el Administrador de dispositivos de Windows.

La página local necesita el broker y el backend para enviar referencias por MQTT; la ESP32 ejecuta el control del motor localmente y publica telemetría. Para la conexión física, selección del controlador P/PI/PID y solución de problemas, consulta las guías dentro de `Project/`.
