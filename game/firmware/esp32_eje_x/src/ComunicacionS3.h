#pragma once

// UART link to the S3 BLE/game controller, replacing ComunicacionPagina with the same control
// interface. NMEA-style lines use a hex XOR checksum over characters between $ and *. Every 50
// ms: S3 sends $R,<setpoint mm>,<enabled 0/1>*HH; this board sends $T,<position mm>,<setpoint
// mm>,<pwm>*HH. The control interface remains in motor degrees; see gradosDeMm in Config.h.
namespace ComunicacionS3 {

struct Comando {
  float referencia;
  bool habilitado;
};

void iniciar();
Comando leerComando();
void actualizarTelemetria(float referencia, float posicion, float error, float salida);

}  // namespace ComunicacionS3
