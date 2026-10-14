#pragma once

namespace ComunicacionPagina {

struct Comando {
  float referencia;
  bool habilitado;
};

void iniciar();
Comando leerComando();
void actualizarTelemetria(float referencia, float posicion, float error, float salida);

}  // namespace ComunicacionPagina
