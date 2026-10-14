import asyncio
import json
import logging
from typing import Dict, Set
from fastapi import WebSocket

logger = logging.getLogger("websocket_manager")


class ConnectionManager:
    def __init__(self):
        # Mapping from deviceId to set of connected WebSockets
        self._active_connections: Dict[str, Set[WebSocket]] = {}
        self._loop: asyncio.AbstractEventLoop = None

    def set_event_loop(self, loop: asyncio.AbstractEventLoop):
        self._loop = loop

    async def connect(self, device_id: str, websocket: WebSocket, accepted: bool = False):
        if not accepted:
            await websocket.accept()
        if device_id not in self._active_connections:
            self._active_connections[device_id] = set()
        self._active_connections[device_id].add(websocket)
        logger.info(f"WebSocket client connected for device '{device_id}'. Total listeners: {len(self._active_connections[device_id])}")

    def disconnect(self, device_id: str, websocket: WebSocket):
        if device_id in self._active_connections:
            self._active_connections[device_id].discard(websocket)
            if not self._active_connections[device_id]:
                del self._active_connections[device_id]
        logger.info(f"WebSocket client disconnected for device '{device_id}'.")

    async def broadcast_json(self, device_id: str, data: dict):
        if device_id not in self._active_connections:
            return

        message_str = json.dumps(data)
        stale_sockets = []

        for socket in list(self._active_connections[device_id]):
            try:
                await socket.send_text(message_str)
            except Exception as e:
                logger.warning(f"Error sending to WebSocket client on '{device_id}': {e}")
                stale_sockets.append(socket)

        for stale in stale_sockets:
            self.disconnect(device_id, stale)

    def threadsafe_broadcast(self, device_id: str, data: dict):
        if self._loop and self._loop.is_running():
            asyncio.run_coroutine_threadsafe(self.broadcast_json(device_id, data), self._loop)


ws_manager = ConnectionManager()
