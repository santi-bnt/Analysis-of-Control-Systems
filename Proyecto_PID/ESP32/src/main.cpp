#include <Arduino.h>
#include "ComunicacionPagina.h"
#include "ControlPosicion.h"

void setup() {
  Serial.begin(115200);
  ControlPosicion::iniciarHardware();
  ComunicacionPagina::iniciar();
  ControlPosicion::iniciarTarea();
}

void loop() {
  vTaskDelay(pdMS_TO_TICKS(1000));
}
