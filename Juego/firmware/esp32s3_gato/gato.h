// Hardware-independent Cartesian tic-tac-toe rules; build on S3 or PC
// (firmware/pruebas/test_gato.cpp). During TURNO the active player moves while the timer runs.
// Firing or timeout enters RESOLVIENDO: reject moves, wait for the head to settle, and read the
// sensor mask. One active free cell places a piece; none, multiple, or occupied loses the turn.
// Then switch players. TERMINADO means a winner or full board.
#pragma once

#include <stdint.h>

namespace gato {

enum Fase : uint8_t {
  ESPERANDO = 0,     // No game has started yet.
  TURNO = 1,
  RESOLVIENDO = 2,
  TERMINADO = 3,
};

enum Resultado : uint8_t {
  NINGUNO = 0,
  PUESTA = 1,        // Head detected over a free cell.
  FALLO = 2,         // No sensor or multiple sensors detected the head.
  OCUPADA = 3,       // Detected an occupied cell.
};

constexpr uint8_t VACIA = 0;
constexpr uint8_t JUGADOR_X = 1;
constexpr uint8_t JUGADOR_O = 2;
constexpr uint8_t EMPATE = 3;          // Return value of ganador().

constexpr uint8_t SIN_CASILLA = 0xFF;  // No active sensor.
constexpr uint8_t VARIAS = 0xFE;       // Multiple active sensors.

struct Config {
  uint32_t turnoMs = 15000;      // Time available to move the head.
  uint32_t quietoMs = 300;       // Required settling time before reading sensors.
  uint32_t resolverMaxMs = 3000; // Read anyway after this timeout if the head never settles.
};

/// 0 = no winner, JUGADOR_X/JUGADOR_O = three-in-a-row, EMPATE = full board.
inline uint8_t ganador(const uint8_t t[9]) {
  static const uint8_t lineas[8][3] = {
      {0, 1, 2}, {3, 4, 5}, {6, 7, 8},  // filas
      {0, 3, 6}, {1, 4, 7}, {2, 5, 8},  // columnas
      {0, 4, 8}, {2, 4, 6},             // diagonales
  };
  for (const auto &l : lineas) {
    if (t[l[0]] != VACIA && t[l[0]] == t[l[1]] && t[l[1]] == t[l[2]]) return t[l[0]];
  }
  for (int i = 0; i < 9; i++) {
    if (t[i] == VACIA) return VACIA;
  }
  return EMPATE;
}

/// Convert the nine-sensor bitmask (bit i = cell i) into a cell index.
inline uint8_t casillaDeMascara(uint16_t mascara) {
  mascara &= 0x1FF;
  if (mascara == 0) return SIN_CASILLA;
  if (mascara & (mascara - 1)) return VARIAS;
  uint8_t i = 0;
  while (!(mascara & 1)) {
    mascara >>= 1;
    i++;
  }
  return i;
}

class Juego {
 public:
  explicit Juego(Config c = Config()) : cfg(c) {}

  Config cfg;
  Fase fase = ESPERANDO;
  uint8_t turno = JUGADOR_X;
  uint8_t tablero[9] = {};
  uint8_t ganadorPartida = VACIA;
  Resultado ultimo = NINGUNO;
  uint8_t ultimaCasilla = SIN_CASILLA;
  uint8_t ultimoJugador = VACIA;

  /// Start a new game, alternating the starting player between games.
  void nueva(uint32_t ahora) {
    for (auto &c : tablero) c = VACIA;
    turno = empieza;
    empieza = empieza == JUGADOR_X ? JUGADOR_O : JUGADOR_X;
    ganadorPartida = VACIA;
    ultimo = NINGUNO;
    ultimaCasilla = SIN_CASILLA;
    ultimoJugador = VACIA;
    iniciarTurno(ahora);
  }

  /// Only the active player can move, and only while their timer runs.
  bool puedeMover(uint8_t jugador) const { return fase == TURNO && jugador == turno; }

  /// The active player fires before the turn timer expires.
  bool tirar(uint8_t jugador, uint32_t ahora) {
    if (!puedeMover(jugador)) return false;
    empezarResolver(ahora);
    return true;
  }

  /// Call every cycle. cabezaQuieta means the head has arrived and stopped; casillaIr is the
  /// currently detected cell, or SIN_CASILLA/VARIAS.
  void actualizar(uint32_t ahora, bool cabezaQuieta, uint8_t casillaIr) {
    if (fase == TURNO) {
      if (vencio(ahora, limite)) empezarResolver(ahora);
      return;
    }
    if (fase != RESOLVIENDO) return;

    if (cabezaQuieta) {
      if (!hayQuieto) {
        hayQuieto = true;
        quietoDesde = ahora;
      }
    } else {
      hayQuieto = false;
    }
    const bool asentada = hayQuieto && ahora - quietoDesde >= cfg.quietoMs;
    if (asentada || ahora - inicioResolver >= cfg.resolverMaxMs) resolver(ahora, casillaIr);
  }

  /// Milliseconds remaining in the turn; zero outside TURNO.
  uint32_t restanteMs(uint32_t ahora) const {
    if (fase != TURNO || vencio(ahora, limite)) return 0;
    return limite - ahora;
  }

 private:
  uint8_t empieza = JUGADOR_X;
  uint32_t limite = 0;
  uint32_t inicioResolver = 0;
  uint32_t quietoDesde = 0;
  bool hayQuieto = false;

  // Comparison tolerates millis() wraparound after about 49 days.
  static bool vencio(uint32_t ahora, uint32_t t) { return (int32_t)(ahora - t) >= 0; }

  void empezarResolver(uint32_t ahora) {
    fase = RESOLVIENDO;
    inicioResolver = ahora;
    quietoDesde = 0;
    hayQuieto = false;
  }

  void iniciarTurno(uint32_t ahora) {
    fase = TURNO;
    limite = ahora + cfg.turnoMs;
  }

  void resolver(uint32_t ahora, uint8_t casilla) {
    ultimoJugador = turno;
    ultimaCasilla = casilla;
    if (casilla < 9) {
      if (tablero[casilla] == VACIA) {
        tablero[casilla] = turno;
        ultimo = PUESTA;
      } else {
        ultimo = OCUPADA;
      }
    } else {
      ultimo = FALLO;
    }

    ganadorPartida = ganador(tablero);
    if (ganadorPartida != VACIA) {
      fase = TERMINADO;
      return;
    }
    turno = turno == JUGADOR_X ? JUGADOR_O : JUGADOR_X;
    iniciarTurno(ahora);
  }
};

}  // namespace gato (tic-tac-toe rules).
