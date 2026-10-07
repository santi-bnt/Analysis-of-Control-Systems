'use client';

import React, { useState, useEffect, useRef, useCallback } from 'react';
import { DeviceHeader } from '../components/DeviceHeader';
import { TelemetryCards } from '../components/TelemetryCards';
import { SetpointController } from '../components/SetpointController';
import { RealTimeChart } from '../components/RealTimeChart';
import { DeviceState, TelemetryData, ChartPoint, WebSocketMessage } from '../types/telemetry';

const BACKEND_URL = process.env.NEXT_PUBLIC_BACKEND_URL ||
  (typeof window === 'undefined' ? '' : window.location.origin);
const WS_BASE_URL = process.env.NEXT_PUBLIC_WS_URL ||
  (typeof window === 'undefined' ? '' : `${window.location.protocol === 'https:' ? 'wss:' : 'ws:'}//${window.location.host}`);
const DEFAULT_DEVICE_ID = 'motor-01';
const MAX_CHART_POINTS = 600;
const CHART_WINDOW_MS = 60000;
const IS_DEMO_MODE = process.env.NEXT_PUBLIC_DEMO_MODE === 'true';

export default function Dashboard() {
  const deviceId = DEFAULT_DEVICE_ID;
  const [accessToken, setAccessToken] = useState('');
  const [tokenInput, setTokenInput] = useState('');
  const [accessError, setAccessError] = useState('');
  const [deviceState, setDeviceState] = useState<DeviceState | null>(null);
  const [latestTelemetry, setLatestTelemetry] = useState<TelemetryData | null>(null);
  const [chartData, setChartData] = useState<ChartPoint[]>([]);
  const [isOnline, setIsOnline] = useState<boolean>(false);
  const [wsConnected, setWsConnected] = useState<boolean>(false);

  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const heartbeatRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    const saved = sessionStorage.getItem('motor-control-token') || '';
    setAccessToken(saved);
    setTokenInput(saved);
  }, []);

  // 1. Initial REST API load: GET /api/devices/{deviceId}
  const loadDeviceInitialData = useCallback(async (devId: string) => {
    try {
      const res = await fetch(`${BACKEND_URL}/api/devices/${devId}`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (res.status === 401) {
        setAccessError('Clave de acceso incorrecta.');
        setAccessToken('');
        sessionStorage.removeItem('motor-control-token');
        return;
      }
      if (res.ok) {
        const data: DeviceState = await res.json();
        setDeviceState(data);
        setIsOnline(data.status === 'online');
        if (data.latestTelemetry) {
          setLatestTelemetry(data.latestTelemetry);
        }
      }
    } catch (err) {
      console.warn('Initial device fetch warning:', err);
    }
  }, [accessToken]);

  // 2. Open WebSocket connection: /ws/devices/{deviceId}
  const connectWebSocket = useCallback((devId: string) => {
    if (wsRef.current) {
      wsRef.current.close();
    }

    const wsUrl = `${WS_BASE_URL}/ws/devices/${devId}`;
    console.log(`[WebSocket] Connecting to ${wsUrl}...`);

    const ws = new WebSocket(wsUrl);
    wsRef.current = ws;

    ws.onopen = () => {
      ws.send(accessToken);
      console.log('[WebSocket] Connected successfully');
      setWsConnected(true);
      heartbeatRef.current = setInterval(() => {
        if (ws.readyState === WebSocket.OPEN) ws.send('ping');
      }, 30000);
    };

    ws.onmessage = (event) => {
      if (event.data === 'pong') return;
      try {
        const msg: WebSocketMessage = JSON.parse(event.data);

        // Handle Initial state snapshot
        if (msg.type === 'init' && msg.device) {
          setDeviceState(msg.device);
          setIsOnline(msg.device.status === 'online');
          if (msg.device.latestTelemetry) {
            setLatestTelemetry(msg.device.latestTelemetry);
          }
        }
        // Handle Device Status Updates (LWT online / offline)
        else if (msg.type === 'status') {
          const status = msg.status === 'online';
          setIsOnline(status);
        }
        // Handle Telemetry Streams (100 ms updates)
        else if (msg.type === 'telemetry') {
          const t: TelemetryData = {
            deviceId: msg.deviceId || devId,
            setpoint: msg.setpoint ?? 0,
            position: msg.position ?? 0,
            error: msg.error ?? 0,
            pwm: msg.pwm ?? 0,
            timestamp: msg.timestamp ?? Date.now(),
          };

          setLatestTelemetry(t);
          setIsOnline(true);

          // Format timestamp for chart X-axis
          const capturedAt = Date.now();
          const now = new Date(capturedAt);
          const timeLabel = `${now.getMinutes().toString().padStart(2, '0')}:${now
            .getSeconds()
            .toString()
            .padStart(2, '0')}.${Math.floor(now.getMilliseconds() / 100)}`;

          // Retain one minute regardless of the configured telemetry rate.
          setChartData((prev) => {
            const next = [...prev, { capturedAt, timeLabel, setpoint: t.setpoint, position: t.position }]
              .filter((point) => point.capturedAt >= capturedAt - CHART_WINDOW_MS);
            return next.length > MAX_CHART_POINTS ? next.slice(next.length - MAX_CHART_POINTS) : next;
          });
        }
      } catch (err) {
        console.error('[WebSocket] Message parsing error:', err);
      }
    };

    ws.onclose = () => {
      if (wsRef.current !== ws) return;
      if (heartbeatRef.current) {
        clearInterval(heartbeatRef.current);
        heartbeatRef.current = null;
      }
      console.warn('[WebSocket] Connection closed. Retrying in 3 seconds...');
      setWsConnected(false);
      setIsOnline(false);
      // Auto-reconnect with throttle
      reconnectTimeoutRef.current = setTimeout(() => {
        connectWebSocket(devId);
      }, 3000);
    };

    ws.onerror = (err) => {
      console.error('[WebSocket] Socket error:', err);
      ws.close();
    };
  }, [accessToken]);

  // Initialize data and WebSocket on mount or deviceId change
  useEffect(() => {
    if (!accessToken) return;
    loadDeviceInitialData(deviceId);
    connectWebSocket(deviceId);

    return () => {
      if (reconnectTimeoutRef.current) {
        clearTimeout(reconnectTimeoutRef.current);
      }
      if (heartbeatRef.current) {
        clearInterval(heartbeatRef.current);
        heartbeatRef.current = null;
      }
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
    };
  }, [deviceId, accessToken, loadDeviceInitialData, connectWebSocket]);

  // Keep an active command fresh only while an authorized dashboard is open.
  useEffect(() => {
    if (!accessToken || !isOnline || !wsConnected) return;
    const timer = setInterval(() => {
      fetch(`${BACKEND_URL}/api/devices/${deviceId}/heartbeat`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${accessToken}` },
      }).catch(() => {});
    }, 2000);
    return () => clearInterval(timer);
  }, [accessToken, deviceId, isOnline, wsConnected]);

  // 3. User Setpoint Update: POST /api/devices/{deviceId}/setpoint
  const handleApplySetpoint = async (newVal: number): Promise<boolean> => {
    try {
      const response = await fetch(`${BACKEND_URL}/api/devices/${deviceId}/setpoint`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify({ value: newVal }),
      });

      if (!response.ok) {
        const errorData = await response.json();
        console.error('Setpoint error response:', errorData);
        return false;
      }

      return true;
    } catch (err) {
      console.error('Failed to submit setpoint:', err);
      return false;
    }
  };

  // 4. Emergency Stop: POST /api/devices/{deviceId}/estop
  const handleEmergencyStop = async () => {
    try {
      const response = await fetch(`${BACKEND_URL}/api/devices/${deviceId}/estop`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      if (response.ok) {
        // Optimistically zero out setpoint and pwm
        setLatestTelemetry((prev) =>
          prev ? { ...prev, setpoint: 0, pwm: 0 } : null
        );
      }
    } catch (err) {
      console.error('Failed to trigger emergency stop:', err);
    }
  };

  const handleResetSafety = async () => {
    try {
      await fetch(`${BACKEND_URL}/api/devices/${deviceId}/reset`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${accessToken}` },
      });
    } catch (err) {
      console.error('Failed to reset safety latch:', err);
    }
  };

  return (
    <div className="min-h-screen bg-slate-950 flex flex-col">
      {!accessToken && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950 p-4">
          <form className="w-full max-w-sm rounded-2xl border border-slate-700 bg-slate-900 p-6 text-white" onSubmit={(event) => {
            event.preventDefault();
            const token = tokenInput.trim();
            if (!token) return;
            sessionStorage.setItem('motor-control-token', token);
            setAccessError('');
            setAccessToken(token);
          }}>
            <h1 className="mb-2 text-xl font-bold">Acceso al motor</h1>
            <p className="mb-4 text-sm text-slate-400">Introduce la clave configurada en el backend.</p>
            <input type="password" autoComplete="off" value={tokenInput} onChange={(event) => setTokenInput(event.target.value)} aria-label="Clave de acceso" className="mb-3 w-full rounded-lg border border-slate-600 bg-slate-950 p-3" />
            {accessError && <p className="mb-3 text-sm text-rose-400">{accessError}</p>}
            <button type="submit" className="w-full rounded-lg bg-indigo-600 p-3 font-semibold">Entrar</button>
          </form>
        </div>
      )}
      {/* Top Header */}
      <DeviceHeader
        deviceId={deviceId}
        isOnline={isOnline}
        wsConnected={wsConnected}
        isDemoMode={IS_DEMO_MODE}
        onEmergencyStop={handleEmergencyStop}
        onResetSafety={handleResetSafety}
      />

      {IS_DEMO_MODE && (
        <div className="bg-sky-950 border-b border-sky-800 px-4 py-2 text-center text-xs text-sky-200">
          MODO DE SIMULACIÓN LOCAL: los controles no están conectados a un motor físico.
        </div>
      )}

      {/* Main Dashboard Workspace */}
      <main className="flex-1 max-w-7xl w-full mx-auto px-4 py-6 sm:px-6 sm:py-8 flex flex-col gap-6">
        
        {/* Device Offline Notice Banner (if offline) */}
        {!isOnline && (
          <div className="bg-amber-950/40 border border-amber-800/60 rounded-xl px-4 py-3 flex items-center justify-between text-amber-300 text-xs sm:text-sm">
            <div className="flex items-center space-x-2">
              <span className="w-2 h-2 rounded-full bg-amber-400 animate-ping"></span>
              <span>
                <strong>{deviceId}</strong> is offline or waiting for MQTT telemetry.
              </span>
            </div>
          </div>
        )}

        {/* 1. Telemetry Cards */}
        <section aria-label="Device Telemetry Metrics">
          <TelemetryCards telemetry={latestTelemetry} />
        </section>

        {/* 2. Setpoint Control Panel */}
        <section aria-label="Setpoint Control">
          <SetpointController
            currentSetpoint={latestTelemetry?.setpoint ?? 0}
            onApplySetpoint={handleApplySetpoint}
            minPosition={-180}
            maxPosition={180}
            disabled={!isOnline || !wsConnected}
          />
        </section>

        {/* 3. Real-Time Telemetry Chart */}
        <section aria-label="Real-Time Chart" className="flex-1">
          <RealTimeChart dataPoints={chartData} maxPoints={MAX_CHART_POINTS} />
        </section>

      </main>

      {/* Footer */}
      <footer className="w-full border-t border-slate-900 bg-slate-950/80 px-4 py-4 text-center text-xs text-slate-500">
        <p>DC Motor Cloud Control System &bull; ESP32 Hardware Closed-Loop PID &bull; MQTT over TLS</p>
      </footer>
    </div>
  );
}
