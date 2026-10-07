import './style.css';
import { Motor } from './motor.ts';
import { Escena } from './escena.ts';

function elemento<T extends HTMLElement>(id: string): T {
  return document.getElementById(id) as T;
}

const motor = new Motor(import.meta.env.VITE_DEVICE_ID || 'motor-01');
const formulario = elemento<HTMLFormElement>('acceso');
const clave = elemento<HTMLInputElement>('clave');
const sesion = elemento('sesion');
const estado = elemento('estado');
const mensaje = elemento('mensaje');
const modo = elemento('modo');
const slider = elemento<HTMLInputElement>('angulo');
const numero = elemento<HTMLInputElement>('valor');
const referencia = elemento<HTMLOutputElement>('referencia');
const aplicar = elemento<HTMLButtonElement>('aplicar');
const parar = elemento<HTMLButtonElement>('paro');
const reset = elemento<HTMLButtonElement>('reset');
const entrarVR = elemento<HTMLButtonElement>('entrar-vr');
let autenticado = false;
let compatibleVR = false;
let pausaSolicitada = false;

const escena = new Escena(elemento('vista'), motor, angulo => {
  slider.value = String(angulo);
  referencia.value = angulo + '°';
  if (document.activeElement !== numero) numero.value = String(angulo);
}, () => {
  entrarVR.textContent = 'Entrar con passthrough';
  void motor.parar();
});

motor.observar(datos => {
  estado.textContent = datos.online && datos.conectado ? 'ONLINE' : 'OFFLINE';
  estado.classList.toggle('online', datos.online && datos.conectado);
  mensaje.textContent = datos.mensaje;
  modo.textContent = datos.modo === 'simulation' ? 'SIMULACIÓN · no se mueve un motor físico' : `MOTOR REAL · ${motor.dispositivo}`;
  const disponible = motor.puedeMover();
  slider.disabled = !disponible;
  numero.disabled = !disponible;
  aplicar.disabled = !disponible;
  parar.disabled = !autenticado;
  reset.disabled = !autenticado || !datos.online || !datos.conectado;
  entrarVR.disabled = !compatibleVR || !autenticado;
});

formulario.addEventListener('submit', async evento => {
  evento.preventDefault();
  const boton = formulario.querySelector('button')!;
  boton.disabled = true;
  try {
    await motor.conectar(clave.value.trim());
    autenticado = true;
    formulario.hidden = true;
    sesion.hidden = false;
    parar.disabled = false;
    entrarVR.disabled = !compatibleVR;
  } catch (error) {
    mensaje.textContent = `No se pudo conectar: ${(error as Error).message}`;
  } finally { boton.disabled = false; }
});

elemento('desconectar').addEventListener('click', async () => {
  if (escena.enVR) await escena.renderer.xr.getSession()?.end();
  await motor.desconectar();
  autenticado = false;
  clave.value = '';
  formulario.hidden = false;
  sesion.hidden = true;
  entrarVR.disabled = true;
  parar.disabled = true;
  reset.disabled = true;
});

slider.addEventListener('input', () => escena.cambiarReferencia(Number(slider.value)));
elemento<HTMLFormElement>('manual').addEventListener('submit', evento => {
  evento.preventDefault();
  if (!numero.checkValidity()) { numero.reportValidity(); return; }
  escena.cambiarReferencia(Number(numero.value));
});
parar.addEventListener('click', () => void motor.parar());
reset.addEventListener('click', () => void motor.restablecer());

async function comprobarVR(): Promise<void> {
  if (!window.isSecureContext) {
    entrarVR.textContent = 'VR necesita una conexión HTTPS segura';
    return;
  }
  try {
    compatibleVR = !!navigator.xr && await navigator.xr.isSessionSupported('immersive-ar');
    entrarVR.textContent = compatibleVR ? 'Entrar con passthrough' : 'Abre esta dirección en el navegador del Quest';
    entrarVR.disabled = !compatibleVR || !autenticado;
  } catch { entrarVR.textContent = 'El navegador no permite iniciar VR'; }
}

entrarVR.addEventListener('click', async () => {
  if (!navigator.xr || !autenticado) return;
  if (escena.enVR) { await escena.renderer.xr.getSession()?.end(); return; }
  try {
    const session = await navigator.xr.requestSession('immersive-ar', {
      requiredFeatures: ['local-floor'],
      optionalFeatures: ['hand-tracking'],
    });
    await escena.renderer.xr.setSession(session);
    entrarVR.textContent = 'Salir de VR';
    session.addEventListener('visibilitychange', () => {
      if (session.visibilityState !== 'visible' && !pausaSolicitada) {
        pausaSolicitada = true;
        void motor.parar();
      }
      if (session.visibilityState === 'visible') pausaSolicitada = false;
    });
  } catch (error) {
    mensaje.textContent = `No se pudo iniciar VR: ${(error as Error).message}`;
  }
});
document.addEventListener('visibilitychange', () => {
  if (document.hidden && !escena.activa && autenticado) void motor.parar();
});
window.addEventListener('pagehide', () => { if (autenticado) void motor.parar(); });
void comprobarVR();
