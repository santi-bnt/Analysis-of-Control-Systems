export interface TelemetryData {
  deviceId: string;
  setpoint: number;
  position: number;
  error: number;
  pwm: number;
  timestamp: number;
}

export interface DeviceState {
  deviceId: string;
  status: 'online' | 'offline';
  lastSeen?: number | null;
  latestTelemetry?: TelemetryData | null;
}

export interface WebSocketMessage {
  type: 'init' | 'telemetry' | 'status';
  device?: DeviceState;
  deviceId?: string;
  status?: 'online' | 'offline';
  lastSeen?: number;
  setpoint?: number;
  position?: number;
  error?: number;
  pwm?: number;
  timestamp?: number;
}

export interface ChartPoint {
  capturedAt: number;
  timeLabel: string;
  setpoint: number;
  position: number;
}
