// Código de PID
#include <Arduino.h>

const int analogPin1 = 33; // ADC referencia 
const int analogPin = 34; // ADC  (y = signal)
const int pinDAC = 25; //DAC

const float kd = 0.1834;
const float kp = 4.5122;
const float ki = 21.0020;

const unsigned long Ts_ms = 10;
const float Ts = 0.01;
unsigned long lastStepMillis = 0;
bool simulationFinished = false;     

float referencia = 0;
float error = 0;
float error_1 = 0;
float integral = 0;
float derivada = 0;
float y = 0;
float u = 0;

void setup() {
  Serial.begin(115200);
  analogReadResolution(12);
  dacWrite(pinDAC, 0);
}

void loop(){
  if (millis() - lastStepMillis < Ts_ms) {
    return;
  }
  lastStepMillis = millis();
  y = 3.3 * analogRead(analogPin) / 4096.0;
  referencia = 3.3 * analogRead(analogPin1) / 4096.0;
  error = referencia - y;
  integral += error * Ts;
  derivada = ((error - error_1) / Ts);
  u = (kp * error + ki * integral + kd * derivada);
  if (u > 3.3) {
    u = 3.3;
  }
  else if (u < 0.0) {
    u = 0.0;
  }
  Serial.print(referencia);
  Serial.print(",");
  Serial.print(y);
  Serial.print(",");
  Serial.println(u);
  dacWrite(pinDAC, (u / 3.3) * 255);
  error_1 = error;
}