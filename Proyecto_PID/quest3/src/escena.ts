import {
  AmbientLight, BoxGeometry, CanvasTexture, Color, DirectionalLight, Group, Mesh,
  MeshBasicMaterial, MeshStandardMaterial, PerspectiveCamera, PlaneGeometry,
  Scene, SphereGeometry, SRGBColorSpace, Vector3, WebGLRenderer,
} from 'three';
import { Interaccion } from './interaccion.ts';
import { Panel } from './panel.ts';
import { xDesdeAngulo, limitarAngulo, MITAD_RIEL } from './angles.ts';
import type { Motor } from './motor.ts';

function etiqueta(texto: string, ancho: number, alto: number, fondo = '#192941'): Mesh {
  const canvas = document.createElement('canvas');
  canvas.width = 1024; canvas.height = 192;
  const ctx = canvas.getContext('2d')!;
  ctx.fillStyle = fondo; ctx.fillRect(0, 0, 1024, 192);
  ctx.fillStyle = '#e8f0ff'; ctx.font = '700 64px sans-serif';
  ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
  ctx.fillText(texto, 512, 96);
  const textura = new CanvasTexture(canvas); textura.colorSpace = SRGBColorSpace;
  return new Mesh(new PlaneGeometry(ancho, alto), new MeshBasicMaterial({ map: textura }));
}

export class Escena {
  readonly renderer = new WebGLRenderer({ antialias: true, alpha: true });
  private escena = new Scene();
  private camara = new PerspectiveCamera(48, 1, 0.05, 30);
  private raiz = new Group();
  private panel: Panel;
  private bolita = new Mesh(new SphereGeometry(0.055, 24, 16), new MeshStandardMaterial({
    color: 0x70e3d2, emissive: 0x154b44, roughness: 0.3, metalness: 0.2,
  }));
  private interaccion: Interaccion;
  private referencia = 0;
  private ultimaEdicion = 0;
  private recentrar = false;

  constructor(
    contenedor: HTMLElement,
    private motor: Motor,
    private referenciaEditada: (angulo: number) => void,
    private alSalirVR: () => void,
  ) {
    // Keep the XR layer transparent so the Quest can show passthrough behind it.
    this.escena.background = null;
    this.renderer.setPixelRatio(Math.min(devicePixelRatio, 1.5));
    this.renderer.outputColorSpace = SRGBColorSpace;
    this.renderer.setClearColor(0x000000, 0);
    this.renderer.xr.enabled = true;
    this.renderer.xr.setReferenceSpaceType('local-floor');
    contenedor.append(this.renderer.domElement);
    this.camara.position.set(0, 1.45, 0.7);
    this.camara.lookAt(0, 1.35, -1.2);
    this.escena.add(this.raiz, new AmbientLight(0xffffff, 2));
    const luz = new DirectionalLight(0xffffff, 3);
    luz.position.set(1, 3, 2); this.escena.add(luz);

    this.panel = new Panel(motor.dispositivo);
    const tablero = new Mesh(new PlaneGeometry(1.55, 0.97), new MeshBasicMaterial({ map: this.panel.textura }));
    tablero.position.set(0, 1.65, -1.35);
    this.raiz.add(tablero);

    const riel = new Mesh(new BoxGeometry(MITAD_RIEL * 2, 0.016, 0.016), new MeshStandardMaterial({ color: 0x455b7b }));
    riel.position.set(0, 0.99, -0.62); this.raiz.add(riel);
    this.bolita.position.copy(riel.position); this.raiz.add(this.bolita);
    const titulo = etiqueta('AGARRA LA BOLITA Y MUEVE LOS GRADOS', 1.05, 0.09);
    titulo.position.set(0, 1.16, -0.68); this.raiz.add(titulo);
    [-180, -90, 0, 90, 180].forEach(angulo => {
      const marca = etiqueta(angulo + '°', 0.17, 0.045);
      marca.position.set(xDesdeAngulo(angulo), 0.885, -0.64); this.raiz.add(marca);
    });
    const paro = etiqueta('PARAR MOTOR', 0.36, 0.11, '#a32d43');
    paro.position.set(-0.23, 0.71, -0.62);
    const reset = etiqueta('RESTABLECER', 0.36, 0.11, '#20324c');
    reset.position.set(0.23, 0.71, -0.62);
    this.raiz.add(paro, reset);
    this.interaccion = new Interaccion(this.renderer, this.camara, this.raiz, this.bolita, [
      { objeto: paro, accion: () => { this.interaccion.soltar(); void motor.parar(); } },
      { objeto: reset, accion: () => { this.interaccion.soltar(); void motor.restablecer(); } },
    ], () => motor.puedeMover(), angulo => this.cambiarReferencia(angulo), () => void motor.parar());

    for (let i = 0; i < 2; i++) {
      const grip = this.renderer.xr.getControllerGrip(i);
      const cuerpo = new Mesh(new BoxGeometry(0.032, 0.08, 0.032), new MeshStandardMaterial({ color: 0x91a7ff }));
      grip.add(cuerpo);
      this.escena.add(this.renderer.xr.getController(i), grip, this.renderer.xr.getHand(i));
    }

    new ResizeObserver(() => {
      this.renderer.setSize(contenedor.clientWidth, contenedor.clientHeight);
      this.camara.aspect = contenedor.clientWidth / contenedor.clientHeight;
      this.camara.updateProjectionMatrix();
    }).observe(contenedor);
    this.renderer.xr.addEventListener('sessionstart', () => { this.recentrar = true; });
    this.renderer.xr.addEventListener('sessionend', () => {
      this.interaccion.soltar();
      this.raiz.position.set(0, 0, 0); this.raiz.rotation.set(0, 0, 0);
      this.alSalirVR();
    });
    this.renderer.setAnimationLoop(() => this.dibujar());
  }

  get enVR(): boolean { return this.renderer.xr.isPresenting; }
  get agarrado(): boolean { return this.interaccion.agarrado; }
  get activa(): boolean { return this.renderer.xr.getSession()?.visibilityState === 'visible'; }

  cambiarReferencia(angulo: number): void {
    if (!this.motor.puedeMover()) return;
    this.referencia = limitarAngulo(angulo);
    this.ultimaEdicion = performance.now();
    this.motor.cambiarReferencia(this.referencia);
    this.referenciaEditada(this.referencia);
  }

  private dibujar(): void {
    if (this.recentrar && this.enVR) {
      const camara = this.renderer.xr.getCamera();
      const cabeza = camara.getWorldPosition(new Vector3());
      if (cabeza.y > 0.1) {
        const frente = camara.getWorldDirection(new Vector3());
        this.raiz.position.set(cabeza.x, cabeza.y - 1.6, cabeza.z);
        this.raiz.rotation.y = Math.atan2(-frente.x, -frente.z);
        this.recentrar = false;
      }
    }
    this.escena.updateMatrixWorld(true);
    if (!this.motor.puedeMover()) this.interaccion.soltar();
    this.interaccion.actualizar();
    const confirmado = this.motor.estado.telemetria?.setpoint;
    if (!this.agarrado && performance.now() - this.ultimaEdicion > 1200 && confirmado !== undefined) {
      this.referencia = limitarAngulo(confirmado);
      this.referenciaEditada(this.referencia);
    }
    this.bolita.position.x = xDesdeAngulo(this.referencia);
    this.bolita.material.color.setHex(this.motor.puedeMover() ? 0x70e3d2 : 0x66758c);
    this.bolita.scale.setScalar(this.agarrado ? 1.12 : 1);
    this.panel.dibujar(this.motor.estado, this.referencia, this.agarrado);
    this.renderer.render(this.escena, this.camara);
  }
}
