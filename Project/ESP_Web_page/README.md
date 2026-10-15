# Dashboard de posición

La página y el firmware son proyectos separados: aquí están el dashboard y la API; el programa PlatformIO está en `../ESP32/`.

## Encender backend y página

Para usar el motor real con MQTT local, primero abre **Docker Desktop** y espera a que esté funcionando. Arranca el broker:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page'
docker compose --env-file backend/.env -f compose.local.yaml up -d mqtt
```

Después abre dos terminales PowerShell. Las dependencias ya están instaladas en este equipo. Mantén Docker y el broker MQTT activos durante el uso del motor.

**Terminal 1 — backend:**

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page\backend'
.\.venv\Scripts\python.exe -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8002
```

**Terminal 2 — página:**

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\Project\ESP_Web_page\web'
npm.cmd run dev
```

Deja ambas terminales abiertas y entra a [http://localhost:3000](http://localhost:3000). Usa la clave configurada como `CONTROL_API_TOKEN` en `backend/.env`. La documentación de la API está en [http://localhost:8002/docs](http://localhost:8002/docs).

Para apagar los servicios, pulsa **Ctrl+C** en cada terminal. Si `npm.cmd run dev` muestra `EADDRINUSE` en el puerto 3000, la página ya está abierta o hay otra instancia en ese puerto: primero prueba [http://localhost:3000](http://localhost:3000) y no inicies una segunda copia.

Las referencias y acciones de seguridad del dashboard se envían al backend y se publican por MQTT. Para mover el motor, el broker debe estar activo y la ESP32 debe aparecer ONLINE.

El frontend debe apuntar al mismo puerto del backend. La configuración actual de `web/.env.local` es:

```dotenv
NEXT_PUBLIC_BACKEND_URL=http://localhost:8002
NEXT_PUBLIC_WS_URL=ws://localhost:8002
```

## Conectar la ESP32

El dashboard envía el ángulo al backend mediante la API REST. El backend publica el valor elegido en `motor/motor-01/command`, con tipo `position`. En `../ESP32/src/ComunicacionPagina.cpp`, el callback MQTT guarda la referencia. `ControlPosicion.cpp` la obtiene con `ComunicacionPagina::leerComando()` y ejecuta cada 10 ms el modo P, PI o PID seleccionado en `../ESP32/include/Config.h`. La tarea de comunicación publica posición, error y salida en `motor/motor-01/telemetry`.

El dashboard normal y Quest usan este mismo flujo. Abre ambos clientes con la misma clave: al mover la bolita en Quest, la página normal actualiza su control de referencia, las tarjetas y la gráfica con la telemetría de la ESP32.

1. En `../ESP32/include/Secrets.h`, configura Wi-Fi, broker, usuario MQTT, contraseña y certificado raíz TLS. Ese archivo se ignora en Git.
2. En `backend/.env`, configura el mismo broker y las credenciales MQTT del backend. Conserva la clave de acceso y los límites `MIN_POSITION_DEG=-180` y `MAX_POSITION_DEG=180`.
3. En `web/.env.local`, configura las direcciones locales REST/WebSocket que ya aparecen en ese archivo.
4. Reinicia backend y frontend. Abre `../ESP32/` en PlatformIO, compila y sube el programa.

Una sola ESP32 lee el encoder en GPIO 32/33 y controla el motor mediante el BTS7960. GPIO 25/26 son RPWM/LPWM y GPIO 27/14 son R_EN/L_EN. El código cuenta un flanco ascendente del canal A y usa `CPR = 11 × 34 × 4 = 1496`, con el segundo ajuste de escala observado en el motor. Comprueba el ángulo físico después de cargar el firmware para confirmar ese ajuste. La posición al encender es 0° y los ángulos de la página son relativos a ese punto.

El firmware permite seleccionar P, PI o PID en `../ESP32/include/Config.h`; PID está activo por defecto. Usa las ganancias proporcionadas (`KP = 4.5122`, `KI = 21.0020`, `KD = 0.1834`), limita la salida a -255…255 y maneja el BTS7960 con PWM de 5 kHz y 8 bits. Las ganancias se copiaron de un ejemplo con otra planta y aún no se han validado en este motor. El motor espera un comando válido de la página; al perder MQTT, vencer el comando o recibir paro, los PWM pasan a cero y se deshabilita el driver. El reset permite recibir otro comando y conserva el cero del encoder. Los detalles de conexión están en `../ESP32/README.md`.
