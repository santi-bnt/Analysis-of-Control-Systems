import { CanvasTexture, SRGBColorSpace } from 'three';
import type { EstadoMotor } from './types.ts';

export class Panel {
  readonly canvas = document.createElement('canvas');
  readonly textura: CanvasTexture;
  private ctx: CanvasRenderingContext2D;
  private ultimoDibujo = 0;

  constructor(readonly dispositivo: string) {
    this.canvas.width = 1600;
    this.canvas.height = 1000;
    this.ctx = this.canvas.getContext('2d')!;
    this.textura = new CanvasTexture(this.canvas);
    this.textura.colorSpace = SRGBColorSpace;
  }

  dibujar(estado: EstadoMotor, referencia: number, agarrado: boolean): void {
    if (performance.now() - this.ultimoDibujo < 100) return;
    this.ultimoDibujo = performance.now();
    const c = this.ctx;
    c.fillStyle = '#101b2d';
    c.fillRect(0, 0, 1600, 1000);
    c.textAlign = 'left';
    c.font = '700 40px sans-serif';
    c.fillStyle = '#e8f0ff';
    c.fillText(this.dispositivo + ' · CONTROL DE POSICIÓN', 60, 70);
    c.font = '600 28px sans-serif';
    c.fillStyle = estado.online && estado.conectado ? '#70e3d2' : '#ffa7b9';
    c.fillText(estado.online && estado.conectado ? 'ONLINE' : 'OFFLINE', 60, 124);
    c.fillStyle = '#9aabc4';
    c.fillText(estado.modo === 'simulation' ? 'SIMULACIÓN' : 'MOTOR REAL', 280, 124);
    c.fillText(estado.conectado ? 'Datos en vivo' : 'WebSocket desconectado', 1000, 124);
    const t = estado.telemetria;
    const tarjetas = [
      ['Referencia recibida', t?.setpoint, '°', '#91a7ff'],
      ['Posición del encoder', t?.position, '°', '#70e3d2'],
      ['Error', t?.error, '°', '#ffd484'],
      ['PWM / control', t?.pwm, '%', '#d5a1ff'],
    ] as const;
    tarjetas.forEach(([nombre, valor, unidad, color], i) => {
      const x = 60 + i * 377;
      c.fillStyle = '#192941';
      c.beginPath(); c.roundRect(x, 158, 348, 142, 18); c.fill();
      c.font = '24px sans-serif'; c.fillStyle = '#9aabc4';
      c.fillText(nombre, x + 20, 196);
      c.font = '700 47px sans-serif'; c.fillStyle = color;
      c.fillText(valor === undefined ? '—' : valor.toFixed(1) + unidad, x + 20, 263);
    });
    c.font = '600 27px sans-serif';
    c.fillStyle = '#e8f0ff';
    c.fillText('Últimos 60 segundos', 60, 350);
    c.fillStyle = '#91a7ff'; c.fillText('— Referencia', 880, 350);
    c.fillStyle = '#70e3d2'; c.fillText('— Posición', 1170, 350);
    this.grafica(estado);
    c.font = '700 38px sans-serif';
    c.fillStyle = estado.detenido ? '#ffa7b9' : '#70e3d2';
    c.fillText(estado.detenido ? 'MOTOR DETENIDO' : `Tu referencia: ${referencia}°`, 60, 850);
    c.font = '25px sans-serif'; c.fillStyle = '#9aabc4';
    c.fillText(agarrado ? 'Sigue moviendo la bolita. Suéltala para mantener el ángulo.' : 'Agarra la bolita y deslízala. Rango: −180° a +180°.', 60, 900);
    c.font = '22px sans-serif';
    this.texto(estado.mensaje, 60, 950, 1480);
    this.textura.needsUpdate = true;
  }

  private grafica(estado: EstadoMotor): void {
    const c = this.ctx;
    const left = 135, top = 394, width = 1380, height = 345;
    const valores = estado.historial.flatMap(t => [t.setpoint, t.position]);
    const limite = Math.max(180, ...valores.map(Math.abs));
    const y = (valor: number) => top + height * (1 - (valor + limite) / (2 * limite));
    const ahora = Date.now();
    c.font = '22px sans-serif';
    for (const valor of [-limite, -limite / 2, 0, limite / 2, limite]) {
      c.strokeStyle = '#2a3c57'; c.lineWidth = 1;
      c.beginPath(); c.moveTo(left, y(valor)); c.lineTo(left + width, y(valor)); c.stroke();
      c.fillStyle = '#9aabc4'; c.fillText(Math.round(valor) + '°', 48, y(valor) + 7);
    }
    for (const segundos of [-60, -45, -30, -15, 0]) {
      const x = left + width * (1 + segundos / 60);
      c.fillStyle = '#9aabc4'; c.fillText(segundos === 0 ? 'Ahora' : segundos + ' s', x - 25, top + height + 40);
    }
    c.save();
    c.beginPath(); c.rect(left, top, width, height); c.clip();
    for (const [campo, color] of [['setpoint', '#91a7ff'], ['position', '#70e3d2']] as const) {
      c.strokeStyle = color; c.lineWidth = 4; c.beginPath();
      let anterior = 0;
      estado.historial.forEach(dato => {
        const x = left + width * (1 - (ahora - dato.recibido) / 60000);
        if (!anterior || dato.recibido - anterior > 2000) c.moveTo(x, y(dato[campo]));
        else c.lineTo(x, y(dato[campo]));
        anterior = dato.recibido;
      });
      c.stroke();
    }
    c.restore();
    if (!estado.historial.length) {
      c.fillStyle = '#9aabc4'; c.font = '28px sans-serif';
      c.fillText('Esperando lecturas del motor…', 500, 580);
    }
  }

  private texto(texto: string, x: number, y: number, ancho: number): void {
    while (this.ctx.measureText(texto).width > ancho && texto.length > 0) texto = texto.slice(0, -1);
    this.ctx.fillText(texto, x, y);
  }
}
