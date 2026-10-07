import asyncio
from contextlib import asynccontextmanager
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import logging

from .config import settings
from .api import api_router, ws_router
from .websocket_manager import ws_manager
from .mqtt_client import mqtt_service
from .simulation import local_motor_simulation

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)s] [%(name)s] %(message)s"
)
logger = logging.getLogger("main")


@asynccontextmanager
async def lifespan(app: FastAPI):
    # Setup asyncio event loop for thread-safe WebSocket broadcasts
    loop = asyncio.get_running_loop()
    ws_manager.set_event_loop(loop)

    simulation_task = None
    if settings.MOCK_MODE:
        logger.info("Starting local motor simulation (no MQTT or hardware).")
        simulation_task = asyncio.create_task(local_motor_simulation.run())
    else:
        logger.info("Starting MQTT Service...")
        mqtt_service.start()

    yield

    if simulation_task:
        simulation_task.cancel()
        try:
            await simulation_task
        except asyncio.CancelledError:
            pass
    else:
        logger.info("Shutting down MQTT Service...")
        mqtt_service.stop()


app = FastAPI(
    title="DC Motor Cloud Control Backend",
    description="Internet-accessible control backend bridging WebSockets to ESP32 via MQTT over TLS",
    version="1.0.0",
    lifespan=lifespan
)

# Configure CORS for Next.js frontend
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins_list,
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Include REST and WebSocket API routes
app.include_router(api_router)
app.include_router(ws_router)


@app.get("/health")
async def health_check():
    return {
        "status": "healthy",
        "mode": "simulation" if settings.MOCK_MODE else "mqtt",
        "mqtt_connected": mqtt_service.is_connected() if not settings.MOCK_MODE else False,
        "mqtt_host": settings.MQTT_HOST,
        "mqtt_port": settings.MQTT_PORT
    }
