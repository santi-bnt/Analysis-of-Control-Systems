from typing import List
from pydantic_settings import BaseSettings, SettingsConfigDict
from pydantic import Field


class Settings(BaseSettings):
    PORT: int = Field(default=8000, description="FastAPI server port")
    HOST: str = Field(default="0.0.0.0", description="FastAPI host")

    MQTT_HOST: str = Field(default="broker.emqx.io", description="MQTT Broker host")
    MQTT_PORT: int = Field(default=8883, description="MQTT Broker port")
    MQTT_USERNAME: str = Field(default="", description="MQTT Username")
    MQTT_PASSWORD: str = Field(default="", description="MQTT Password")
    MQTT_USE_TLS: bool = Field(default=True, description="Enable TLS for MQTT")
    MQTT_KEEPALIVE: int = Field(default=60, description="MQTT Keep-alive interval in seconds")
    CONTROL_API_TOKEN: str = Field(default="", description="Private dashboard access token")
    MOCK_MODE: bool = Field(default=False, description="Run a local motor simulation instead of MQTT")

    MIN_POSITION_DEG: float = Field(default=-180.0, description="Minimum position command in degrees")
    MAX_POSITION_DEG: float = Field(default=180.0, description="Maximum position command in degrees")

    CORS_ORIGINS: str = Field(
        default="http://localhost:3000,http://127.0.0.1:3000",
        description="Comma-separated allowed CORS origins",
    )

    @property
    def cors_origins_list(self) -> List[str]:
        return [origin.strip() for origin in self.CORS_ORIGINS.split(",") if origin.strip()]

    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        extra="ignore"
    )


settings = Settings()
