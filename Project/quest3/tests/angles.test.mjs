import test from 'node:test';
import assert from 'node:assert/strict';
import { anguloDesdeX, xDesdeAngulo, limitarAngulo, MITAD_RIEL } from '../src/angles.ts';

test('full rail maps -180 to +180 degrees without inverting the firmware', () => {
  assert.equal(anguloDesdeX(-MITAD_RIEL), -180);
  assert.equal(anguloDesdeX(0), 0);
  assert.equal(anguloDesdeX(MITAD_RIEL), 180);
  for (const grados of [-180, -90, -1, 0, 1, 90, 180]) {
    assert.equal(anguloDesdeX(xDesdeAngulo(grados)), grados);
  }
});

test('grabs outside the rail clamp the setpoint and reject invalid coordinates', () => {
  assert.equal(anguloDesdeX(-10), -180);
  assert.equal(anguloDesdeX(10), 180);
  assert.throws(() => limitarAngulo(NaN));
  assert.throws(() => anguloDesdeX(Infinity));
});
