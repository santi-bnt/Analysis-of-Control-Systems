export interface Telemetria {
  deviceId: string;
  setpoint: number;
  position: number;
  error: number;
  pwm: number;
  timestamp: number;
}

export interface Dispositivo {
  deviceId: string;
  status: 'online' | 'offline';
  latestTelemetry: Telemetria | null;
}

export interface Muestra extends Telemetria {
  recibido: number;
}

export interface EstadoMotor {
  online: boolean;
  conectado: boolean;
  detenido: boolean;
  telemetria: Telemetria | null;
  historial: Muestra[];
  modo: 'mqtt' | 'simulation' | null;
  mensaje: string;
}
