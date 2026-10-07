export const MIN_ANGULO = -180;
export const MAX_ANGULO = 180;
export const MITAD_RIEL = 0.55;

export function limitarAngulo(angulo: number): number {
  if (!Number.isFinite(angulo)) throw new Error('El ángulo debe ser un número finito.');
  return Math.round(Math.max(MIN_ANGULO, Math.min(MAX_ANGULO, angulo)));
}

export function anguloDesdeX(x: number): number {
  return limitarAngulo((x / MITAD_RIEL) * MAX_ANGULO);
}

export function xDesdeAngulo(angulo: number): number {
  return limitarAngulo(angulo) / MAX_ANGULO * MITAD_RIEL;
}
