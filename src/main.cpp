#include <Arduino.h>

// Encoder
const int ENC_A = 32;
const int ENC_B = 33;

// Driver BTS7960
const int RPWM = 25;
const int LPWM = 26;
const int R_EN = 27;
const int L_EN = 14;
const int CANAL_RPWM = 0;
const int CANAL_LPWM = 1;

const float PPR = 11.0;
const float REDUCCION = 34.0;
const float CPR = PPR * REDUCCION;

const int PWM = 180;
const unsigned long TIEMPO_MUESTRA = 100;
const unsigned long DURACION_PRUEBA = 8000;

volatile long encoderCount = 0;
long conteoAnterior = 0;
unsigned long inicioPrueba = 0;
unsigned long tiempoAnterior = 0;
bool pruebaTerminada = false;

void IRAM_ATTR leerEncoder() {
  if (digitalRead(ENC_B) == HIGH) {
    encoderCount++;
  } else {
    encoderCount--;
  }
}

void detenerMotor() {
  ledcWrite(CANAL_RPWM, 0);
  ledcWrite(CANAL_LPWM, 0);
  digitalWrite(R_EN, LOW);
  digitalWrite(L_EN, LOW);
}

void setup() {
  Serial.begin(115200);

  pinMode(ENC_A, INPUT_PULLUP);
  pinMode(ENC_B, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(ENC_A), leerEncoder, RISING);

  pinMode(R_EN, OUTPUT);
  pinMode(L_EN, OUTPUT);
  digitalWrite(R_EN, HIGH);
  digitalWrite(L_EN, HIGH);

  ledcSetup(CANAL_RPWM, 5000, 8);
  ledcSetup(CANAL_LPWM, 5000, 8);
  ledcAttachPin(RPWM, CANAL_RPWM);
  ledcAttachPin(LPWM, CANAL_LPWM);

  ledcWrite(CANAL_RPWM, 0);
  ledcWrite(CANAL_LPWM, 0);

  delay(2000);

  encoderCount = 0;
  conteoAnterior = 0;
  inicioPrueba = millis();
  tiempoAnterior = inicioPrueba;

  Serial.println("Prueba iniciada");
  Serial.println("Tiempo: 0 ms | RPM: 0.00 | Angulo: 0.00 grados");

}

void loop() {
  if (pruebaTerminada) {
    return;
  }

  unsigned long ahora = millis();
  unsigned long tiempoPrueba = ahora - inicioPrueba;

  if (tiempoPrueba >= DURACION_PRUEBA) {
    detenerMotor();
    pruebaTerminada = true;
    Serial.println("Prueba terminada. Motor detenido.");
    return;
  }

  if (tiempoPrueba < 2000) {
    ledcWrite(CANAL_RPWM, 0);
  } else if (tiempoPrueba < 4000) {
    ledcWrite(CANAL_RPWM, 180);
  } else if (tiempoPrueba < 6000) {
    ledcWrite(CANAL_RPWM, 120);
  } else {
    ledcWrite(CANAL_RPWM, 0);
  }

  if (ahora - tiempoAnterior >= TIEMPO_MUESTRA) {
    noInterrupts();
    long conteoActual = encoderCount;
    interrupts();

    long pulsosNuevos = conteoActual - conteoAnterior;
    unsigned long tiempoTranscurrido = ahora - tiempoAnterior;

    float revoluciones = pulsosNuevos / CPR;
    float rpm = fabsf(revoluciones / (tiempoTranscurrido / 60000.0));
    float angulo = ((float)conteoActual / CPR) * 360.0;

    Serial.print("Tiempo: ");
    Serial.print(ahora - inicioPrueba);
    Serial.print(" ms | RPM: ");
    Serial.print(rpm, 2);
    Serial.print(" | Angulo: ");
    Serial.print(angulo, 2);
    Serial.println(" grados");

    conteoAnterior = conteoActual;
    tiempoAnterior = ahora;
  }
}
