import {
  Group, Line, BufferGeometry, LineBasicMaterial, Matrix4, Mesh, Object3D,
  Plane, Raycaster, Vector2, Vector3, WebGLRenderer, Camera,
} from 'three';
import { XRHandModelFactory } from 'three/addons/webxr/XRHandModelFactory.js';
import { anguloDesdeX } from './angles.ts';

export interface BotonVR { objeto: Mesh; accion: () => void; }
interface Agarre { dueno: Object3D; tipo: 'rayo' | 'mando' | 'mano' | 'raton'; offset: number; }

export class Interaccion {
  private agarre: Agarre | null = null;
  private raycaster = new Raycaster();
  private planoRiel: Plane;

  constructor(
    private renderer: WebGLRenderer,
    private camara: Camera,
    private raiz: Group,
    private bolita: Mesh,
    private botones: BotonVR[],
    private permitido: () => boolean,
    private cambiar: (angulo: number) => void,
    private interrupcion: () => void,
  ) {
    this.planoRiel = new Plane(new Vector3(0, 0, 1), -bolita.position.z);
    this.prepararMandos();
    this.prepararRaton();
  }

  get agarrado(): boolean { return this.agarre !== null; }
  soltar(): void { this.agarre = null; }

  private prepararMandos(): void {
    const fabricaManos = new XRHandModelFactory();
    for (let i = 0; i < 2; i++) {
      const mando = this.renderer.xr.getController(i);
      const grip = this.renderer.xr.getControllerGrip(i);
      const mano = this.renderer.xr.getHand(i);
      let usaMano = false;
      mando.addEventListener('connected', evento => { usaMano = !!evento.data.hand; });
      mando.addEventListener('selectstart', () => {
        if (usaMano) return;
        this.rayoMando(mando);
        this.tomarConRayo(mando, 'rayo');
      });
      mando.addEventListener('selectend', () => this.soltarDueno(mando));
      mando.addEventListener('squeezestart', () => {
        if (!usaMano) this.tomarCerca(grip, 'mando', this.puntoLocal(grip));
      });
      mando.addEventListener('squeezeend', () => this.soltarDueno(grip));
      mando.addEventListener('disconnected', () => {
        if (this.agarre?.dueno === mando || this.agarre?.dueno === grip || this.agarre?.dueno === mano) {
          this.soltar(); this.interrupcion();
        }
      });
      const geometria = new BufferGeometry().setFromPoints([new Vector3(), new Vector3(0, 0, -2)]);
      mando.add(new Line(geometria, new LineBasicMaterial({ color: 0x70e3d2 })));
      mano.add(fabricaManos.createHandModel(mano, 'spheres'));
      mano.addEventListener('pinchstart', () => {
        const punto = this.puntoMano(mano);
        if (punto) this.tomarCerca(mano, 'mano', punto);
      });
      mano.addEventListener('pinchend', () => this.soltarDueno(mano));
    }
  }

  private soltarDueno(dueno: Object3D): void {
    if (this.agarre?.dueno === dueno) this.soltar();
  }

  private puntoLocal(objeto: Object3D): Vector3 {
    return this.raiz.worldToLocal(objeto.getWorldPosition(new Vector3()));
  }

  private puntoMano(mano: ReturnType<WebGLRenderer['xr']['getHand']>): Vector3 | null {
    const indice = mano.joints['index-finger-tip'];
    const pulgar = mano.joints['thumb-tip'];
    if (!indice?.visible || !pulgar?.visible) return null;
    const punto = indice.getWorldPosition(new Vector3());
    punto.add(pulgar.getWorldPosition(new Vector3())).multiplyScalar(0.5);
    return this.raiz.worldToLocal(punto);
  }

  private tomarCerca(dueno: Object3D, tipo: 'mando' | 'mano', punto: Vector3): void {
    if (this.agarre) return;
    const boton = this.botones.find(b => punto.distanceTo(b.objeto.position) < 0.14);
    if (boton) { boton.accion(); return; }
    if (!this.permitido() || punto.distanceTo(this.bolita.position) > 0.14) return;
    this.agarre = { dueno, tipo, offset: this.bolita.position.x - punto.x };
  }

  private rayoMando(mando: Object3D): void {
    this.raycaster.ray.origin.setFromMatrixPosition(mando.matrixWorld);
    this.raycaster.ray.direction.set(0, 0, -1).transformDirection(mando.matrixWorld);
  }

  private puntoRiel(): Vector3 | null {
    const rayo = this.raycaster.ray.clone().applyMatrix4(new Matrix4().copy(this.raiz.matrixWorld).invert());
    return rayo.intersectPlane(this.planoRiel, new Vector3());
  }

  private tomarConRayo(dueno: Object3D, tipo: 'rayo' | 'raton'): void {
    if (this.agarre) return;
    const golpe = this.raycaster.intersectObjects([this.bolita, ...this.botones.map(b => b.objeto)], false)[0];
    if (!golpe) return;
    const boton = this.botones.find(b => b.objeto === golpe.object);
    if (boton) { boton.accion(); return; }
    const punto = this.puntoRiel();
    if (!this.permitido() || !punto) return;
    this.agarre = { dueno, tipo, offset: this.bolita.position.x - punto.x };
  }

  private prepararRaton(): void {
    const canvas = this.renderer.domElement;
    const actualizarRayo = (evento: PointerEvent) => {
      const rect = canvas.getBoundingClientRect();
      this.raycaster.setFromCamera(new Vector2(
        (evento.clientX - rect.left) / rect.width * 2 - 1,
        -(evento.clientY - rect.top) / rect.height * 2 + 1,
      ), this.camara);
    };
    canvas.addEventListener('pointerdown', evento => {
      if (this.renderer.xr.isPresenting) return;
      actualizarRayo(evento);
      this.tomarConRayo(this.camara, 'raton');
      if (this.agarre?.tipo === 'raton') canvas.setPointerCapture(evento.pointerId);
    });
    canvas.addEventListener('pointermove', evento => {
      if (this.agarre?.tipo !== 'raton') return;
      actualizarRayo(evento);
      const punto = this.puntoRiel();
      if (punto) this.arrastrar(punto);
    });
    canvas.addEventListener('pointerup', () => this.soltarDueno(this.camara));
    canvas.addEventListener('pointercancel', () => {
      if (this.agarre?.tipo === 'raton') { this.soltar(); this.interrupcion(); }
    });
  }

  private arrastrar(punto: Vector3): void {
    if (!this.agarre || !this.permitido()) { this.soltar(); return; }
    this.cambiar(anguloDesdeX(punto.x + this.agarre.offset));
  }

  actualizar(): void {
    if (!this.agarre || this.agarre.tipo === 'raton') return;
    let punto: Vector3 | null;
    if (this.agarre.tipo === 'rayo') {
      this.rayoMando(this.agarre.dueno);
      punto = this.puntoRiel();
    } else if (this.agarre.tipo === 'mano') {
      punto = this.puntoMano(this.agarre.dueno as ReturnType<WebGLRenderer['xr']['getHand']>);
    } else {
      punto = this.agarre.dueno.visible ? this.puntoLocal(this.agarre.dueno) : null;
    }
    if (punto) this.arrastrar(punto);
    else { this.soltar(); this.interrupcion(); }
  }
}
