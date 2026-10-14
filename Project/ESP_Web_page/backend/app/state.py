import time
from typing import Dict, List, Optional
from threading import Lock
from .models import DeviceState, TelemetryPayload


class DeviceStateManager:
    def __init__(self):
        self._devices: Dict[str, DeviceState] = {}
        self._lock = Lock()

    def get_or_create(self, device_id: str) -> DeviceState:
        with self._lock:
            if device_id not in self._devices:
                self._devices[device_id] = DeviceState(
                    deviceId=device_id,
                    status="offline",
                    lastSeen=None,
                    latestTelemetry=None
                )
            return self._devices[device_id].model_copy()

    def update_telemetry(self, telemetry: TelemetryPayload) -> DeviceState:
        with self._lock:
            device = self._devices.get(telemetry.deviceId)
            if not device:
                device = DeviceState(
                    deviceId=telemetry.deviceId,
                    status="online",
                    lastSeen=time.time(),
                    latestTelemetry=telemetry
                )
                self._devices[telemetry.deviceId] = device
            else:
                device.latestTelemetry = telemetry
                device.status = "online"
                device.lastSeen = time.time()
            return device.model_copy()

    def update_status(self, device_id: str, status: str) -> DeviceState:
        with self._lock:
            device = self._devices.get(device_id)
            if not device:
                device = DeviceState(
                    deviceId=device_id,
                    status="online" if status == "online" else "offline",
                    lastSeen=time.time() if status == "online" else None,
                    latestTelemetry=None
                )
                self._devices[device_id] = device
            else:
                device.status = "online" if status == "online" else "offline"
                if status == "online":
                    device.lastSeen = time.time()
            return device.model_copy()

    def get_device(self, device_id: str) -> Optional[DeviceState]:
        with self._lock:
            device = self._devices.get(device_id)
            return device.model_copy() if device else None

    def list_devices(self) -> List[DeviceState]:
        with self._lock:
            return [dev.model_copy() for dev in self._devices.values()]

    def mark_all_offline(self) -> List[DeviceState]:
        with self._lock:
            for device in self._devices.values():
                device.status = "offline"
            return [device.model_copy() for device in self._devices.values()]


device_state_manager = DeviceStateManager()
