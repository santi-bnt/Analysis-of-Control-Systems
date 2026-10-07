from typing import List
from fastapi import APIRouter, Depends, Header, HTTPException, WebSocket, WebSocketDisconnect, status
from fastapi.concurrency import run_in_threadpool
import asyncio
import hmac
import re
import logging

from .config import settings
from .models import DeviceState, SetpointRequest, CommandPayload
from .state import device_state_manager
from .websocket_manager import ws_manager
from .mqtt_client import mqtt_service
from .simulation import local_motor_simulation

logger = logging.getLogger("api_router")
def require_token(authorization: str | None = Header(default=None)) -> None:
    if len(settings.CONTROL_API_TOKEN) < 4:
        raise HTTPException(status_code=503, detail="CONTROL_API_TOKEN must contain at least 4 characters")
    expected = f"Bearer {settings.CONTROL_API_TOKEN}"
    if authorization is None or not hmac.compare_digest(authorization, expected):
        raise HTTPException(status_code=401, detail="Invalid access token")


api_router = APIRouter(prefix="/api", dependencies=[Depends(require_token)])
ws_router = APIRouter()
DEVICE_ID_PATTERN = re.compile(r"^[A-Za-z0-9_-]{1,48}$")


def validate_device_id(device_id: str) -> str:
    if not DEVICE_ID_PATTERN.fullmatch(device_id):
        raise HTTPException(status_code=400, detail="Invalid device ID")
    return device_id


@api_router.get("/devices", response_model=List[DeviceState])
async def list_devices():
    """Retrieve the latest state and telemetry for all known devices."""
    devices = device_state_manager.list_devices()
    # If no devices are tracked yet, ensure default 'motor-01' is listed so UI has a device ready
    if not devices:
        default_dev = device_state_manager.get_or_create("motor-01")
        return [default_dev]
    return devices


@api_router.get("/devices/{device_id}", response_model=DeviceState)
async def get_device(device_id: str):
    validate_device_id(device_id)
    """Retrieve the latest state and telemetry for a specific device."""
    device = device_state_manager.get_device(device_id)
    if not device:
        # Return initialized state if device hasn't published yet
        device = device_state_manager.get_or_create(device_id)
    return device


@api_router.post("/devices/{device_id}/setpoint")
async def set_setpoint(device_id: str, request: SetpointRequest):
    """
    Validate setpoint and transmit setpoint command to the ESP32 via MQTT.
    """
    validate_device_id(device_id)
    if request.value < settings.MIN_POSITION_DEG or request.value > settings.MAX_POSITION_DEG:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail=f"Position {request.value} degrees is out of bounds. Allowed range: [{settings.MIN_POSITION_DEG} - {settings.MAX_POSITION_DEG}] degrees"
        )

    command = CommandPayload(type="position", value=request.value)

    # 2. Transmit via MQTT
    if settings.MOCK_MODE:
        local_motor_simulation.set_setpoint(request.value)
        success = True
    else:
        success = await run_in_threadpool(mqtt_service.publish_command, device_id, command)
    if not success:
        # Check if MQTT client is currently connected
        if not mqtt_service.is_connected():
            raise HTTPException(
                status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                detail="MQTT broker connection is currently unavailable. Please check broker status."
            )
        raise HTTPException(
            status_code=status.HTTP_500_INTERNAL_SERVER_ERROR,
            detail="Failed to publish setpoint command to MQTT broker."
        )

    return {
        "status": "success",
        "deviceId": device_id,
        "command": command.model_dump()
    }


@api_router.post("/devices/{device_id}/estop")
async def trigger_emergency_stop(device_id: str):
    """Publish an emergency stop command to immediately shut down the motor."""
    validate_device_id(device_id)
    command = CommandPayload(type="estop", value=0.0)
    if settings.MOCK_MODE:
        local_motor_simulation.set_setpoint(0.0)
        success = True
    else:
        success = await run_in_threadpool(mqtt_service.publish_command, device_id, command)
    if not success:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="MQTT broker connection is currently unavailable."
        )
    return {
        "status": "success",
        "deviceId": device_id,
        "command": "emergency_stop"
    }


@api_router.post("/devices/{device_id}/heartbeat")
async def refresh_command_timeout(device_id: str):
    """Keep a valid position command alive without changing its target."""
    validate_device_id(device_id)
    command = CommandPayload(type="heartbeat", value=0.0)
    if settings.MOCK_MODE:
        success = True
    else:
        success = await run_in_threadpool(mqtt_service.publish_command, device_id, command)
    if not success:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            detail="MQTT broker connection is currently unavailable.")
    return {"status": "success", "deviceId": device_id}


@api_router.post("/devices/{device_id}/reset")
async def reset_safety_latch(device_id: str):
    """Clear a latched stop/fault after the operator has checked the mechanism."""
    validate_device_id(device_id)
    command = CommandPayload(type="reset", value=0.0)
    if settings.MOCK_MODE:
        success = True
    else:
        success = await run_in_threadpool(mqtt_service.publish_command, device_id, command)
    if not success:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
                            detail="MQTT broker connection is currently unavailable.")
    return {"status": "success", "deviceId": device_id}


@ws_router.websocket("/ws/devices/{device_id}")
async def websocket_device_telemetry(websocket: WebSocket, device_id: str):
    """
    WebSocket endpoint streaming live telemetry and status updates for a device.
    Immediately sends current snapshot upon connection, then listens for disconnects.
    """
    if not DEVICE_ID_PATTERN.fullmatch(device_id):
        await websocket.close(code=1008)
        return
    origin = websocket.headers.get("origin")
    if origin not in settings.cors_origins_list:
        await websocket.close(code=1008)
        return
    if len(settings.CONTROL_API_TOKEN) < 4:
        await websocket.close(code=1013)
        return
    await websocket.accept()
    try:
        credential = await asyncio.wait_for(websocket.receive_text(), timeout=5)
        if not hmac.compare_digest(credential, settings.CONTROL_API_TOKEN):
            await websocket.close(code=1008)
            return
    except (asyncio.TimeoutError, WebSocketDisconnect):
        await websocket.close(code=1008)
        return
    await ws_manager.connect(device_id, websocket, accepted=True)

    try:
        # Send current device state snapshot immediately upon connection
        current_state = device_state_manager.get_or_create(device_id)
        await websocket.send_json({
            "type": "init",
            "device": current_state.model_dump()
        })

        # Keep socket open and process any incoming ping or client messages
        while True:
            data = await websocket.receive_text()
            # If client sends a ping or keepalive
            if data == "ping":
                await websocket.send_text("pong")
    except WebSocketDisconnect:
        ws_manager.disconnect(device_id, websocket)
    except Exception as e:
        logger.warning(f"WebSocket connection error for device '{device_id}': {e}")
        ws_manager.disconnect(device_id, websocket)
