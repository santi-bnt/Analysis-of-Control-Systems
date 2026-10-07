# Publicación sin pago

La ruta gratuita usa tres servicios separados:

1. **EMQX Cloud Serverless** para MQTT/TLS. Crea un despliegue Serverless y conserva `Spend Limit = 0`. Crea credenciales distintas para el backend y `motor-01`, con permisos sólo para sus temas.
2. **Render Free Web Service** para FastAPI. Conecta el repositorio de GitHub y crea un Blueprint usando `pagina_esp/render.yaml`. Mantén `plan: free` y no agregues un método de pago. En la primera creación, Render pedirá `MQTT_HOST`, `MQTT_USERNAME`, `MQTT_PASSWORD` y `CONTROL_API_TOKEN` (mínimo 32 caracteres). Guarda la clave de control de forma privada: es la que se introduce en la página. El host MQTT debe ser el asignado por EMQX, no el broker público de ejemplo. Comprueba `https://<servicio>.onrender.com/health` y anota el URL exacto.
3. **Firebase Hosting Spark** para la página. Usa el proyecto independiente `dc-motor-cloud-santi-2026`, sin cambiarlo a Blaze. Desde `pagina_esp/web`, compila la exportación estática con `FIREBASE_EXPORT=true`, `NEXT_PUBLIC_BACKEND_URL=https://<servicio>.onrender.com` y `NEXT_PUBLIC_WS_URL=wss://<servicio>.onrender.com`. Después ejecuta `firebase deploy --only hosting --project dc-motor-cloud-santi-2026` desde `pagina_esp`. La página estará en `https://dc-motor-cloud-santi-2026.web.app`.

En PowerShell, el paso de compilación del punto 3 se puede hacer así (sustituye el nombre real del servicio):

```powershell
cd pagina_esp/web
$env:FIREBASE_EXPORT = 'true'
$env:NEXT_PUBLIC_BACKEND_URL = 'https://<servicio>.onrender.com'
$env:NEXT_PUBLIC_WS_URL = 'wss://<servicio>.onrender.com'
npm.cmd run build
cd ..
firebase deploy --only hosting --project dc-motor-cloud-santi-2026
```

La ESP32 se configura con la misma dirección MQTT, puerto 8883, su propio usuario y contraseña, y el certificado raíz del broker en `firmware/include/Secrets.h` (archivo ignorado por Git). Para conservar la cuota gratuita, añade `#define TELEMETRY_PERIOD_MS 1000` a ese archivo **antes** de incluir `Config.example.h`. El PID sigue ejecutándose localmente cada 10 ms; sólo la publicación de telemetría baja a 1 Hz. La gráfica guarda el último minuto. Si se usa el valor predeterminado de 100 ms continuamente, es probable que se agote la cuota gratuita de tráfico MQTT.

Render Free suspende el backend después de 15 minutos sin tráfico entrante; la primera apertura puede tardar alrededor de un minuto en reactivarlo. Mientras el panel está abierto envía un mensaje WebSocket cada 30 segundos. Al suspenderse el backend, su suscripción MQTT también se detiene; la ESP32 conserva el control local y apaga el motor si pierde comunicación o caduca el comando. El panel no almacena historial durante una suspensión. Cuando el servicio despierta vuelve a suscribirse y recibe el estado retenido del dispositivo.

EMQX Serverless incluye 1 millón de minutos de sesión y 1 GB de tráfico por mes con límite de gasto 0. Si se agota la cuota, suspende el broker hasta el siguiente periodo o hasta una intervención manual; no se debe subir el límite de gasto para esta configuración. Render y Firebase también tienen límites gratuitos. Sin método de pago en Render, la plataforma deshabilita el servicio al superar sus cuotas en lugar de cobrar.

Antes de mandar una velocidad distinta de cero, verifica cableado, sentido del driver, PPR real del encoder y ganancias PID. Las ganancias incluidas son marcadores en cero. La URL pública por sí sola no mueve el motor hasta que el broker, backend y ESP32 estén conectados y configurados.

Referencias: [Render Free](https://render.com/docs/free), [Render Blueprint](https://render.com/docs/blueprint-spec), [EMQX Serverless](https://docs.emqx.com/en/cloud/latest/create/serverless.html), [Firebase Hosting](https://firebase.google.com/docs/hosting/usage-quotas-pricing).
