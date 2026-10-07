from typing import Optional, Literal
from pydantic import BaseModel, Field
import math
from pydantic import field_validator


class SetpointRequest(BaseModel):
    value: float = Field(
        ...,
        description="Target angular position in degrees",
    )

    @field_validator("value")
    @classmethod
    def finite_value(cls, value: float) -> float:
        if not math.isfinite(value):
            raise ValueError("Setpoint must be finite")
        return value


class CommandPayload(BaseModel):
    type: Literal["position", "heartbeat", "reset", "estop", "stop"] = "position"
    value: float = Field(
        ...,
        description="Setpoint value or parameter"
    )


class TelemetryPayload(BaseModel):
    deviceId: str = Field(..., description="Unique hardware identifier")
    setpoint: float = Field(..., description="Target angular position in degrees")
    position: float = Field(..., description="Measured angular position in degrees")
    error: float = Field(..., description="Position error in degrees (setpoint - position)")
    pwm: float = Field(..., description="Signed actuator duty cycle percentage (-100.0 to 100.0%)")
    timestamp: int = Field(..., description="Hardware monotonic or epoch timestamp")


class DeviceState(BaseModel):
    deviceId: str
    status: Literal["online", "offline"] = "offline"
    lastSeen: Optional[float] = None
    latestTelemetry: Optional[TelemetryPayload] = None
