import asyncio
import time

from .models import TelemetryPayload
from .state import device_state_manager
from .websocket_manager import ws_manager


class LocalMotorSimulation:
    """Simple first-order motor model for local dashboard development only."""

    def __init__(self):
        self.setpoint = 0.0
        self.position = 0.0
        self._started_at = time.monotonic()

    def set_setpoint(self, value: float) -> None:
        self.setpoint = value

    async def run(self) -> None:
        device_id = "motor-01"
        device_state = device_state_manager.update_status(device_id, "online")
        await ws_manager.broadcast_json(device_id, {
            "type": "status", "deviceId": device_id,
            "status": "online", "lastSeen": device_state.lastSeen,
        })

        previous = time.monotonic()
        while True:
            await asyncio.sleep(0.1)
            now = time.monotonic()
            dt = now - previous
            previous = now

            error = self.setpoint - self.position
            pwm = min(100.0, max(-100.0, error * 0.8))
            movement = pwm * 3.0 * dt
            if abs(movement) > abs(error):
                self.position = self.setpoint
            else:
                self.position += movement
            telemetry = TelemetryPayload(
                deviceId=device_id,
                setpoint=self.setpoint,
                position=self.position,
                error=error,
                pwm=pwm,
                timestamp=int((now - self._started_at) * 1000),
            )
            device_state_manager.update_telemetry(telemetry)
            await ws_manager.broadcast_json(device_id, {
                "type": "telemetry", **telemetry.model_dump(),
            })


local_motor_simulation = LocalMotorSimulation()
