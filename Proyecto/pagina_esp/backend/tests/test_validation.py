import pytest
from pydantic import ValidationError
from app.models import SetpointRequest, CommandPayload, TelemetryPayload, DeviceState


def test_valid_setpoint_request():
    req = SetpointRequest(value=150.0)
    assert req.value == 150.0

    req_zero = SetpointRequest(value=0.0)
    assert req_zero.value == 0.0


def test_invalid_setpoint_request_types():
    with pytest.raises(ValidationError):
        SetpointRequest(value="not_a_number")
    with pytest.raises(ValidationError):
        SetpointRequest(value=float("nan"))
    with pytest.raises(ValidationError):
        SetpointRequest(value=float("inf"))


def test_command_payload_serialization():
    cmd = CommandPayload(type="setpoint", value=120.0)
    data = cmd.model_dump()
    assert data["type"] == "setpoint"
    assert data["value"] == 120.0

    cmd_estop = CommandPayload(type="estop", value=0.0)
    assert cmd_estop.type == "estop"


def test_telemetry_payload_parsing():
    raw_telemetry = {
        "deviceId": "motor-01",
        "setpoint": 120.0,
        "rpm": 117.4,
        "error": 2.6,
        "pwm": 63.2,
        "timestamp": 123456
    }
    telemetry = TelemetryPayload(**raw_telemetry)
    assert telemetry.deviceId == "motor-01"
    assert telemetry.setpoint == 120.0
    assert telemetry.rpm == 117.4
    assert telemetry.error == 2.6
    assert telemetry.pwm == 63.2
    assert telemetry.timestamp == 123456


def test_telemetry_payload_missing_field():
    incomplete_data = {
        "deviceId": "motor-01",
        "setpoint": 120.0,
        # missing rpm, error, pwm, timestamp
    }
    with pytest.raises(ValidationError):
        TelemetryPayload(**incomplete_data)


def test_device_state_defaults():
    dev = DeviceState(deviceId="motor-01")
    assert dev.status == "offline"
    assert dev.lastSeen is None
    assert dev.latestTelemetry is None
