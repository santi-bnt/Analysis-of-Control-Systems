import { limitarAngulo } from './angles.ts';
import type { Dispositivo, EstadoMotor, Telemetria } from './types.ts';

type Avisar = (estado: EstadoMotor) => void;

export class Motor {
  readonly estado: EstadoMotor = {
    online: false, conectado: false, detenido: false,
    telemetria: null, historial: [], modo: null, mensaje: 'Sin conectar.',
  };
  private clave = '';
  private socket: WebSocket | null = null;
  private reconexion?: ReturnType<typeof setTimeout>;
  private ciclo?: ReturnType<typeof setInterval>;
  private pendiente: number | null = null;
  private enviando: Promise<void> | null = null;
  private ultimoEnvio = 0;
  private ultimoDato = 0;
  private manteniendoReferencia = false;
  private generacion = 0;
  private avisos: Avisar[] = [];

  constructor(readonly dispositivo: string) {}

  observar(aviso: Avisar): void { this.avisos.push(aviso); aviso(this.estado); }
  private avisar(): void { this.avisos.forEach(aviso => aviso(this.estado)); }
  private mensaje(texto: string): void { this.estado.mensaje = texto; this.avisar(); }

  private async peticion(ruta: string, opciones: RequestInit = {}): Promise<Response> {
    const respuesta = await fetch(ruta, {
      ...opciones,
      signal: opciones.keepalive ? undefined : AbortSignal.timeout(4000),
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${this.clave}`,
        ...opciones.headers,
      },
    });
    if (!respuesta.ok) {
      if (respuesta.status === 401) throw new Error('Clave de acceso incorrecta.');
      const datos = await respuesta.json().catch(() => ({}));
      throw new Error(typeof datos.detail === 'string' ? datos.detail : 'No se pudo comunicar con el backend.');
    }
    return respuesta;
  }

  private ruta(accion = ''): string {
    return `/api/devices/${encodeURIComponent(this.dispositivo)}${accion ? '/' + accion : ''}`;
  }

  async conectar(clave: string): Promise<void> {
    this.cerrarConexion();
    this.clave = clave;
    const [respuesta, salud] = await Promise.all([
      this.peticion(this.ruta()),
      this.peticion('/health'),
    ]);
    const dispositivo = await respuesta.json() as Dispositivo;
    const health = await salud.json();
    this.estado.modo = health.mode === 'simulation' ? 'simulation' : 'mqtt';
    this.aplicarDispositivo(dispositivo);
    this.abrirSocket();
    let ultimoHeartbeat = 0;
    this.ciclo = setInterval(() => {
      if (this.estado.online && Date.now() - this.ultimoDato > 5000) {
        this.estado.online = false;
        this.pendiente = null;
        this.manteniendoReferencia = false;
        this.mensaje('Sin telemetría reciente. Comprueba la conexión de la ESP32.');
      }
      this.enviarPendiente();
      if (this.puedeMover() && this.manteniendoReferencia && Date.now() - ultimoHeartbeat >= 2000) {
        ultimoHeartbeat = Date.now();
        this.peticion(this.ruta('heartbeat'), { method: 'POST' }).catch(error => {
          this.manteniendoReferencia = false;
          this.mensaje(`No se pudo mantener el comando: ${error.message}`);
        });
      }
    }, 100);
  }

  private aplicarDispositivo(dispositivo: Dispositivo): void {
    this.estado.online = dispositivo.status === 'online';
    if (dispositivo.latestTelemetry) this.guardarTelemetria(dispositivo.latestTelemetry);
    this.ultimoDato = Date.now();
    this.avisar();
  }

  private guardarTelemetria(datos: Telemetria): void {
    if (![datos.setpoint, datos.position, datos.error, datos.pwm, datos.timestamp].every(Number.isFinite)) return;
    this.estado.telemetria = datos;
    const ahora = Date.now();
    this.ultimoDato = ahora;
    this.estado.historial.push({ ...datos, recibido: ahora });
    this.estado.historial = this.estado.historial.filter(dato => dato.recibido >= ahora - 60000).slice(-600);
  }

  private abrirSocket(): void {
    const protocolo = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const socket = new WebSocket(`${protocolo}//${location.host}/ws/devices/${encodeURIComponent(this.dispositivo)}`);
    this.socket = socket;
    socket.onopen = () => socket.send(this.clave);
    socket.onmessage = evento => {
      if (socket !== this.socket || evento.data === 'pong') return;
      try {
        const datos = JSON.parse(evento.data);
        if (datos.type === 'init') {
          this.estado.conectado = true;
          this.aplicarDispositivo(datos.device);
          this.mensaje(this.estado.online ? 'Conectado. Mueve la bolita para enviar grados.' : 'ESP32 offline. Esperando telemetría.');
        } else if (datos.type === 'telemetry') {
          this.guardarTelemetria(datos);
          this.estado.online = true;
          this.avisar();
        } else if (datos.type === 'status') {
          this.estado.online = datos.status === 'online';
          if (!this.estado.online) {
            this.pendiente = null;
            this.manteniendoReferencia = false;
          }
          this.avisar();
        }
      } catch { this.mensaje('El backend envió un mensaje no válido.'); }
    };
    socket.onclose = evento => {
      if (socket !== this.socket) return;
      this.estado.conectado = false;
      this.estado.online = false;
      this.pendiente = null;
      this.manteniendoReferencia = false;
      if (evento.code === 1008) {
        this.mensaje('WebSocket rechazado: revisa la clave y BACKEND_WS_ORIGIN.');
        return;
      }
      this.mensaje('Conexión perdida. Reconectando…');
      this.reconexion = setTimeout(() => this.abrirSocket(), 3000);
    };
    socket.onerror = () => socket.close();
  }

  puedeMover(): boolean {
    return !!this.clave && this.estado.online && this.estado.conectado && !this.estado.detenido;
  }

  cambiarReferencia(grados: number): void {
    if (!this.puedeMover()) return;
    this.pendiente = limitarAngulo(grados);
    this.enviarPendiente();
  }

  private enviarPendiente(): void {
    if (this.pendiente === null || this.enviando || !this.puedeMover() || Date.now() - this.ultimoEnvio < 100) return;
    const valor = this.pendiente;
    const generacion = this.generacion;
    this.pendiente = null;
    this.ultimoEnvio = Date.now();
    // Una sola petición en vuelo; los movimientos siguientes reemplazan el pendiente.
    this.enviando = this.peticion(this.ruta('setpoint'), {
      method: 'POST', body: JSON.stringify({ value: valor }),
    }).then(() => {
      if (generacion !== this.generacion) return;
      this.manteniendoReferencia = true;
      this.mensaje(`Referencia enviada: ${valor}°.`);
    }).catch(error => {
      if (generacion !== this.generacion) return;
      this.pendiente = null;
      this.manteniendoReferencia = false;
      this.mensaje(`No se envió la referencia: ${error.message}`);
    }).finally(() => { this.enviando = null; });
  }

  async parar(): Promise<void> {
    this.generacion++;
    this.pendiente = null;
    this.manteniendoReferencia = false;
    this.estado.detenido = true;
    this.avisar();
    if (!this.clave) return;
    try {
      await this.peticion(this.ruta('estop'), { method: 'POST', keepalive: true });
      this.mensaje('Paro enviado. Pulsa Restablecer antes de volver a mover.');
    } catch (error) {
      this.mensaje(`Paro no confirmado: ${(error as Error).message}`);
    }
  }

  async restablecer(): Promise<void> {
    this.estado.detenido = true;
    this.pendiente = null;
    this.manteniendoReferencia = false;
    this.generacion++;
    this.avisar();
    // No dejamos que una referencia anterior llegue después del reset.
    await this.enviando;
    try {
      await this.peticion(this.ruta('reset'), { method: 'POST' });
      this.estado.detenido = false;
      this.mensaje('Restablecido. Mueve la bolita para enviar una nueva referencia.');
    } catch (error) { this.mensaje((error as Error).message); }
  }

  async desconectar(): Promise<void> {
    if (this.clave) await this.parar();
    this.cerrarConexion();
    this.clave = '';
    this.estado.online = false;
    this.estado.detenido = false;
    this.mensaje('Sin conectar.');
  }

  private cerrarConexion(): void {
    clearInterval(this.ciclo);
    clearTimeout(this.reconexion);
    const socket = this.socket;
    this.socket = null;
    socket?.close();
    this.generacion++;
    this.pendiente = null;
    this.estado.conectado = false;
    this.manteniendoReferencia = false;
  }
}
