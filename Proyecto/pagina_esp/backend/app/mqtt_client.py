import json
import logging
import ssl
import paho.mqtt.client as mqtt
from typing import Optional

from .config import settings
from .models import TelemetryPayload, CommandPayload
from .state import device_state_manager
from .websocket_manager import ws_manager

logger = logging.getLogger("mqtt_client")


class BackendMQTTClient:
    def __init__(self):
        # Support paho-mqtt v2.x CallbackAPIVersion
        try:
            self._client = mqtt.Client(
                callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
                client_id="fastapi-backend-controller"
            )
        except AttributeError:
            self._client = mqtt.Client(client_id="fastapi-backend-controller")

        self._connected = False
        self._setup_callbacks()

    def _setup_callbacks(self):
        self._client.on_connect = self._on_connect
        self._client.on_disconnect = self._on_disconnect
        self._client.on_message = self._on_message

    def start(self):
        if settings.MQTT_USERNAME:
            self._client.username_pw_set(
                username=settings.MQTT_USERNAME,
                password=settings.MQTT_PASSWORD
            )

        if settings.MQTT_USE_TLS:
            ssl_context = ssl.create_default_context()
            self._client.tls_set_context(ssl_context)
            logger.info("Configured MQTT client with verified TLS.")

        logger.info(f"Connecting to MQTT Broker at {settings.MQTT_HOST}:{settings.MQTT_PORT}...")
        try:
            self._client.connect_async(
                host=settings.MQTT_HOST,
                port=settings.MQTT_PORT,
                keepalive=settings.MQTT_KEEPALIVE
            )
            self._client.loop_start()
        except Exception as e:
            logger.error(f"Failed to initiate MQTT connection: {e}")

    def stop(self):
        logger.info("Stopping MQTT client loop...")
        try:
            self._client.loop_stop()
            self._client.disconnect()
        except Exception as e:
            logger.warning(f"Error while stopping MQTT client: {e}")

    def is_connected(self) -> bool:
        return self._connected

    def _on_connect(self, client, userdata, flags, rc, properties=None):
        rc_code = rc if isinstance(rc, int) else getattr(rc, "value", rc)
        if rc_code == 0:
            self._connected = True
            logger.info("Successfully connected to MQTT Broker!")

            # Subscribe to all device telemetry and status topics
            client.subscribe("motor/+/telemetry", qos=1)
            client.subscribe("motor/+/status", qos=1)
            logger.info("Subscribed to motor/+/telemetry and motor/+/status")
        else:
            self._connected = False
            logger.error(f"Failed to connect to MQTT broker with result code: {rc}")

    def _on_disconnect(self, client, userdata, flags_or_rc, rc_or_props=None, properties=None):
        self._connected = False
        for device in device_state_manager.mark_all_offline():
            ws_manager.threadsafe_broadcast(device.deviceId, {
                "type": "status", "deviceId": device.deviceId,
                "status": "offline", "lastSeen": device.lastSeen,
            })
        logger.warning("Disconnected from MQTT Broker. Auto-reconnecting in background...")

    def _on_message(self, client, userdata, msg):
        topic = msg.topic
        payload_str = msg.payload.decode("utf-8", errors="ignore").strip()

        try:
            parts = topic.split("/")
            if len(parts) != 3 or parts[0] != "motor":
                return

            device_id = parts[1]
            sub_topic = parts[2]
            if not device_id or any(char not in "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789_-" for char in device_id):
                return

            # 1. Handle Status: motor/{deviceId}/status
            if sub_topic == "status":
                status = payload_str.lower()
                if status in ["online", "offline"]:
                    device_state = device_state_manager.update_status(device_id, status)
                    logger.info(f"Device '{device_id}' status update: {status}")
                    # Broadcast status change to connected WebSockets
                    ws_manager.threadsafe_broadcast(
                        device_id,
                        {
                            "type": "status",
                            "deviceId": device_id,
                            "status": status,
                            "lastSeen": device_state.lastSeen
                        }
                    )

            # 2. Handle Telemetry: motor/{deviceId}/telemetry
            elif sub_topic == "telemetry":
                data_dict = json.loads(payload_str)
                telemetry = TelemetryPayload(**data_dict)
                if telemetry.deviceId != device_id:
                    logger.warning("Discarding telemetry with mismatched device ID on %s", topic)
                    return
                device_state = device_state_manager.update_telemetry(telemetry)

                # Broadcast telemetry to connected WebSockets
                ws_manager.threadsafe_broadcast(
                    device_id,
                    {
                        "type": "telemetry",
                        **telemetry.model_dump()
                    }
                )

        except Exception as e:
            logger.warning(f"Error processing MQTT message on topic '{topic}': {e}")

    def publish_command(self, device_id: str, command: CommandPayload) -> bool:
        if not self._connected:
            logger.error("Cannot publish command: MQTT client is disconnected.")
            return False

        topic = f"motor/{device_id}/command"
        payload_str = json.dumps(command.model_dump())
        try:
            info = self._client.publish(topic, payload_str, qos=1)
            info.wait_for_publish(timeout=2.0)
            published = info.is_published()
        except Exception as exc:
            logger.warning("MQTT publish failed: %s", exc)
            return False
        if published:
            logger.info(f"Published command to '{topic}': {payload_str}")
        else:
            logger.warning(f"Failed to publish command to '{topic}' within timeout.")
        return published


mqtt_service = BackendMQTTClient()
