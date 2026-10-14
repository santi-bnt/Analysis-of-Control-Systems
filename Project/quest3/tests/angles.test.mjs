import test from 'node:test';
import assert from 'node:assert/strict';
import { anguloDesdeX, xDesdeAngulo, limitarAngulo, MITAD_RIEL } from '../src/angles.ts';

test('el riel completo representa de -180 a +180 grados sin invertir el firmware', () => {
  assert.equal(anguloDesdeX(-MITAD_RIEL), -180);
  assert.equal(anguloDesdeX(0), 0);
  assert.equal(anguloDesdeX(MITAD_RIEL), 180);
  for (const grados of [-180, -90, -1, 0, 1, 90, 180]) {
    assert.equal(anguloDesdeX(xDesdeAngulo(grados)), grados);
  }
});

test('agarrar fuera del riel satura la referencia y rechaza coordenadas inválidas', () => {
  assert.equal(anguloDesdeX(-10), -180);
  assert.equal(anguloDesdeX(10), 180);
  assert.throws(() => limitarAngulo(NaN));
  assert.throws(() => anguloDesdeX(Infinity));
});
