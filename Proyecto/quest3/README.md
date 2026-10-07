# Motor en Quest 3

Aplicación WebXR independiente para usar el mismo motor y backend de `pagina_esp/`. Se abre en el navegador del Quest, sin instalar una APK.

La bolita representa la referencia entre -180° y +180°. Al arrastrarla, el cliente envía el ángulo a la misma API del dashboard; el backend lo publica por MQTT. La ESP32 ejecuta el control local. El panel VR muestra referencia recibida, posición del encoder, error, PWM, estado de conexión y gráfica del último minuto.

## Encender

Deja Docker, el broker y el backend funcionando como indica [el README de la página](../pagina_esp/README.md). No es necesario encender Next.js para usar Quest.

En otra terminal PowerShell:

```powershell
Set-Location -LiteralPath 'C:\Users\santi\OneDrive\Documents\Analysis-of-Control-Systems\quest3'
npm.cmd run dev
```

Las dependencias ya están instaladas en este equipo. En una instalación nueva, ejecuta primero `npm.cmd install`.

Conecta la PC y el Quest al mismo Wi-Fi. Abre en el navegador del visor la dirección HTTPS que Vite muestra como **Network**, por ejemplo:

```text
https://192.168.137.119:5173
```

Introduce la clave actual del backend y pulsa **Conectar**, luego **Entrar en VR**.

Para ver el movimiento de Quest en la página normal, abre también `pagina_esp/web` en la PC con `npm.cmd run dev` y entra a `http://localhost:3000` con la misma clave. Deja ambas pantallas abiertas: al arrastrar la bolita en el Quest, la bolita del control de referencia, las tarjetas y la gráfica de la página siguen la telemetría de la ESP32 en tiempo real.

## Agarrar y mover

- Con mandos: acerca el mando a la bolita, mantén el botón de agarre y muévelo horizontalmente. También puedes apuntar con el rayo y mantener el gatillo.
- Con manos: activa el seguimiento de manos en el Quest, acerca pulgar e índice a la bolita, pellizca y deslízala.
- Al soltar, se mantiene el ángulo solicitado mientras esta interfaz esté activa.
- Los botones **PARAR MOTOR** y **RESTABLECER** funcionan dentro y fuera de VR.
- Al salir de VR, ocultar la sesión o perder el seguimiento durante un agarre, se solicita un paro. Pulsa Restablecer y mueve la bolita para enviar otra referencia.

La actualización compartida recorre Quest → API → MQTT → ESP32 → telemetría → WebSocket → página. La pantalla 2D muestra los grados confirmados por la ESP32, por lo que puede ir unas lecturas detrás del movimiento de la mano.

El cliente conserva como máximo 600 muestras de 60 segundos. Envía referencias como máximo 10 veces por segundo, con una sola petición en vuelo, y mantiene vigente únicamente el comando que envió esta interfaz. Usa una sola interfaz de control a la vez para evitar referencias de varios operadores.

## HTTPS local

WebXR necesita un contexto seguro. Vite genera un certificado de desarrollo en `.cert/`, que está excluido de Git. El navegador puede mostrar un aviso porque no está firmado por una autoridad reconocida; la decisión de continuar debe tomarla el usuario. Si el visor no permite WebXR con ese certificado, configura uno que el navegador del Quest confíe.

Para usar un certificado propio, copia `.env.example` a `.env.local` y establece `HTTPS_CERT` y `HTTPS_KEY`. No subas claves privadas ni certificados de desarrollo a Git.

El servidor HTTPS reenvía `/api`, `/health` y `/ws` al backend en `http://127.0.0.1:8002`. El Quest usa HTTPS/WSS contra la PC, sin conexiones HTTP directas que puedan bloquearse por contenido mixto.

El proxy se identifica ante el backend con `BACKEND_WS_ORIGIN=http://localhost:3000`; esa dirección debe figurar en `CORS_ORIGINS`. La clave sigue siendo obligatoria para REST y como primer mensaje del WebSocket. No se incluyen credenciales MQTT en el visor.

## Archivos

- `src/main.ts`: acceso, controles de la página y entrada/salida de VR.
- `src/escena.ts`: escenario 3D, bolita y botones.
- `src/interaccion.ts`: agarre con mando, manos y ratón.
- `src/motor.ts`: REST, WebSocket, referencias y paro.
- `src/panel.ts`: tarjetas y gráfica visibles en VR.
- `src/angles.ts`: conversión del desplazamiento de la bolita a grados.
- `vite.config.ts`: HTTPS y proxy al backend.

## Verificar

```powershell
npm.cmd test
npm.cmd run build
```

La escala y el sentido físicos son los definidos en `../ESP32/`; Quest no aplica otra inversión ni otro factor. El funcionamiento del seguimiento y la ergonomía deben comprobarse en el visor real.
