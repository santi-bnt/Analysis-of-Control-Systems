# Firebase deployment for this project

Firebase project: `dc-motor-cloud-santi-2026`. Hosting address: `https://dc-motor-cloud-santi-2026.web.app` **after deployment**. The existing `matt-205c1` project is unrelated.

The dashboard can be exported as static files to Firebase Hosting. FastAPI must run as a persistent Cloud Run service because its MQTT subscriber and WebSocket listeners need a running process. The project must be linked to a Cloud Billing account (Blaze) before Cloud Run can be used. Set a billing budget and review costs. An MQTT/TLS broker with credentials and device-specific topic permissions is also required.

1. Enable billing for the new project in the Firebase console. Enable Cloud Run, Cloud Build, Artifact Registry, and Secret Manager APIs. Install and authenticate the Google Cloud CLI for this project.
2. Create three Secret Manager secrets: `motor-mqtt-username`, `motor-mqtt-password`, and `motor-control-token` (a random token of at least 32 characters). Grant the Cloud Run runtime service account access to those secrets. Do not put secret values in commands, Git, or the web build.
3. Deploy `backend/` from source with its Dockerfile as one Cloud Run instance. Use `--min-instances=1 --max-instances=1 --no-cpu-throttling`, because the MQTT subscriber needs CPU even without browser requests and the backend keeps state in memory. Set `MQTT_HOST`, `MQTT_PORT=8883`, `MQTT_USE_TLS=true`, and `CORS_ORIGINS=https://dc-motor-cloud-santi-2026.web.app`. Map the three secrets to `MQTT_USERNAME`, `MQTT_PASSWORD`, and `CONTROL_API_TOKEN`. Allow public HTTPS requests; the application verifies the control token.
4. Record the deployed Cloud Run HTTPS URL. Build the frontend in `web/` with `FIREBASE_EXPORT=true`, `NEXT_PUBLIC_BACKEND_URL=<Cloud Run HTTPS URL>`, and `NEXT_PUBLIC_WS_URL=<same URL with wss://>`. Then run `firebase deploy --only hosting --project dc-motor-cloud-santi-2026` from `pagina_esp/`.
5. Verify `/health`, browser login, WebSocket telemetry, a zero-RPM command, and the broker's Last Will status before commanding motor speed. Configure the ESP32 to use the same broker and its own topic-scoped credentials.

The frontend URL alone does not provide motor control. Deploy the backend and configure MQTT first. Local development does not require Firebase; see the main README.
