# Quest 3 motor interface

Standalone WebXR client for the motor and backend in
[`../ESP_Web_page/`](../ESP_Web_page/README.md). It runs in the Quest browser
without installing an APK. The draggable ball sets −180° to +180° through the
same REST/MQTT flow as the dashboard. ESP32 runs control locally. The VR panel
shows confirmed setpoint, encoder position, error, PWM, connection state, and
a one-minute chart.

## Start

Keep Docker, MQTT, and the backend running as described in the dashboard guide.
Next.js is optional for Quest. From this folder:

```powershell
npm.cmd install  # First setup only
npm.cmd run dev
```

Connect PC and Quest to the same Wi-Fi. Open Vite's **Network** HTTPS address
in the headset browser, for example `https://192.168.137.119:5173`. Enter the
backend access token, select **Connect**, then **Enter VR**.

To also view movement on the desktop dashboard, start `../ESP_Web_page/web`
with `npm.cmd run dev` and open its printed URL with the same access token.
The desktop setpoint, cards, and chart follow confirmed ESP32 telemetry.
Hardware wiring is documented in [the motor guide](../ESP32/README.md#wiring)
and [the interactive diagram](../ESP32/Conexion_diagram/index.html).

## Grab and move

- Controllers: hold grip near the ball and move horizontally, or point the ray
  and hold the trigger.
- Hands: enable hand tracking, pinch the ball between thumb and index finger,
  and slide it.
- Releasing holds the requested angle while this interface remains active.
- **STOP MOTOR** and **RESET** work inside and outside VR.
- Leaving VR, hiding the session, or losing tracking during a grab requests a
  stop. Select **Reset** and move the ball to send another setpoint.

The shared path is Quest → API → MQTT → ESP32 → telemetry → WebSocket → dashboard.
The 2D view can trail hand motion by several samples because it shows confirmed
degrees. The client retains at most 600 samples over 60 seconds, sends at most
10 setpoints per second with one request in flight, and refreshes only its own
command. Use one controlling interface at a time.

## Local HTTPS

WebXR requires a secure context. Vite creates a development certificate in
`.cert/`, excluded from Git. The browser may warn that its issuer is untrusted;
the user decides whether to continue. If Quest rejects WebXR with that
certificate, configure one the headset browser trusts.

For custom certificates, copy `.env.example` to `.env.local` and set
`HTTPS_CERT` / `HTTPS_KEY`. Keep private keys and development certificates out
of Git. Vite proxies `/api`, `/health`, and `/ws` to `http://127.0.0.1:8002`.
Quest uses HTTPS/WSS to the PC, avoiding direct mixed-content HTTP requests.

The proxy uses `BACKEND_WS_ORIGIN=http://localhost:3000`; include that value in
`CORS_ORIGINS`. REST still requires the access token; WebSocket sends it as its
first message. No MQTT credentials are included in the headset client.

## Files and verification

| File | Purpose |
| --- | --- |
| `src/main.ts` | Access, page controls, VR entry/exit. |
| `src/escena.ts` | 3D scene, ball, buttons. |
| `src/interaccion.ts` | Controller, hand, and mouse grabs. |
| `src/motor.ts` | REST, WebSocket, setpoints, stop. |
| `src/panel.ts` | VR cards and chart. |
| `src/angles.ts` | Ball displacement to degrees. |
| `vite.config.ts` | HTTPS and backend proxy. |

```powershell
npm.cmd test
npm.cmd run build
```

Physical direction and scale come from `../ESP32/`; Quest adds no inversion
or scale factor. Check tracking behavior and ergonomics on the actual headset.
