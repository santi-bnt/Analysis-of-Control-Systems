import pytest
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect
from unittest.mock import patch

from app.main import app
from app.config import settings
from app.state import device_state_manager
from app.models import TelemetryPayload

settings.CONTROL_API_TOKEN = "1234"
client = TestClient(app, headers={"Authorization": "Bearer 1234"})


def test_api_rejects_missing_token():
    response = TestClient(app).get("/api/devices")
    assert response.status_code == 401


def test_api_accepts_four_character_token():
    response = client.get("/api/devices")
    assert response.status_code == 200


def test_api_rejects_wrong_token():
    response = TestClient(app, headers={"Authorization": "Bearer 0000"}).get("/api/devices")
    assert response.status_code == 401


def test_api_rejects_too_short_configured_token(monkeypatch):
    monkeypatch.setattr(settings, "CONTROL_API_TOKEN", "123")
    response = client.get("/api/devices")
    assert response.status_code == 503


def test_api_rejects_invalid_device_id():
    response = client.post("/api/devices/bad.id/setpoint", json={"value": 10})
    assert response.status_code == 400


def test_websocket_requires_token_before_snapshot():
    with client.websocket_connect("/ws/devices/motor-01", headers={"origin": "http://localhost:3000"}) as socket:
        socket.send_text("1234")
        snapshot = socket.receive_json()
        assert snapshot["type"] == "init"
        assert snapshot["device"]["deviceId"] == "motor-01"


def test_websocket_rejects_wrong_token():
    with pytest.raises(WebSocketDisconnect):
        with client.websocket_connect("/ws/devices/motor-01", headers={"origin": "http://localhost:3000"}) as socket:
            socket.send_text("wrong-token")
            socket.receive_json()


def test_health_endpoint():
    response = client.get("/health")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "healthy"
    assert "mqtt_connected" in data


def test_get_devices():
    # Ensure motor-01 exists
    device_state_manager.get_or_create("motor-01")

    response = client.get("/api/devices")
    assert response.status_code == 200
    devices = response.json()
    assert isinstance(devices, list)
    assert any(d["deviceId"] == "motor-01" for d in devices)


def test_get_specific_device():
    # Update telemetry for a device
    telemetry = TelemetryPayload(
        deviceId="motor-01",
        setpoint=100.0,
        rpm=98.5,
        error=1.5,
        pwm=52.0,
        timestamp=1000
    )
    device_state_manager.update_telemetry(telemetry)

    response = client.get("/api/devices/motor-01")
    assert response.status_code == 200
    device = response.json()
    assert device["deviceId"] == "motor-01"
    assert device["status"] == "online"
    assert device["latestTelemetry"]["rpm"] == 98.5


def test_setpoint_validation_out_of_bounds():
    # Below minimum
    response = client.post(
        "/api/devices/motor-01/setpoint",
        json={"value": settings.MIN_RPM - 10.0}
    )
    assert response.status_code == 400
    assert "out of bounds" in response.json()["detail"]

    # Above maximum
    response = client.post(
        "/api/devices/motor-01/setpoint",
        json={"value": settings.MAX_RPM + 100.0}
    )
    assert response.status_code == 400
    assert "out of bounds" in response.json()["detail"]


@patch("app.mqtt_client.mqtt_service.publish_command", return_value=True)
def test_valid_setpoint_submission(mock_publish):
    response = client.post(
        "/api/devices/motor-01/setpoint",
        json={"value": 120.0}
    )
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["deviceId"] == "motor-01"
    assert data["command"]["type"] == "setpoint"
    assert data["command"]["value"] == 120.0
    mock_publish.assert_called_once()


@patch("app.mqtt_client.mqtt_service.publish_command", return_value=True)
def test_emergency_stop_submission(mock_publish):
    response = client.post("/api/devices/motor-01/estop")
    assert response.status_code == 200
    data = response.json()
    assert data["status"] == "success"
    assert data["command"] == "emergency_stop"
    mock_publish.assert_called_once()
