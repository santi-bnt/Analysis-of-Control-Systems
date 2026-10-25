// Native game-rule tests: g++ -std=c++17 -Wall -Wextra -I../esp32s3_gato test_gato.cpp -o
// test_gato && ./test_gato
#include <cstdio>
#include <cstdlib>

#include "gato.h"

using namespace gato;

static int fallas = 0;
#define CHECK(c)                                                  \
  do {                                                            \
    if (!(c)) {                                                   \
      std::printf("FAIL %s:%d  %s\n", __FILE__, __LINE__, #c);   \
      fallas++;                                                   \
    }                                                             \
  } while (0)

// Play a full turn: let the timer expire and resolve with the head stationary over casilla.
static uint32_t jugarTurno(Juego &j, uint32_t t, uint8_t casilla) {
  t += j.cfg.turnoMs;
  j.actualizar(t, true, casilla);          // Timer expires: enter RESOLVIENDO.
  j.actualizar(t + 1, true, casilla);      // Start the settling interval.
  t += 1 + j.cfg.quietoMs;
  j.actualizar(t, true, casilla);          // asentada -> resuelve
  return t;
}

int main() {
  // ganador(): rows, columns, diagonals, draw, and ongoing game.
  {
    uint8_t t[9] = {1, 1, 1, 0, 2, 2, 0, 0, 0};
    CHECK(ganador(t) == JUGADOR_X);
    uint8_t c[9] = {2, 1, 0, 2, 1, 0, 2, 0, 0};
    CHECK(ganador(c) == JUGADOR_O);
    uint8_t d[9] = {2, 1, 1, 0, 2, 1, 0, 0, 2};
    CHECK(ganador(d) == JUGADOR_O);
    uint8_t e[9] = {1, 2, 1, 1, 2, 2, 2, 1, 1};
    CHECK(ganador(e) == EMPATE);
    uint8_t v[9] = {1, 2, 0, 0, 0, 0, 0, 0, 0};
    CHECK(ganador(v) == VACIA);
  }

  // casillaDeMascara(): no sensor, one sensor, multiple sensors.
  CHECK(casillaDeMascara(0) == SIN_CASILLA);
  CHECK(casillaDeMascara(1u << 4) == 4);
  CHECK(casillaDeMascara(1u << 8) == 8);
  CHECK(casillaDeMascara((1u << 2) | (1u << 3)) == VARIAS);
  CHECK(casillaDeMascara(0xFE00) == SIN_CASILLA);  // Bits outside the nine cells; turn switching, movement lock, and piece placement.
  {
    Juego j;
    CHECK(j.fase == ESPERANDO);
    CHECK(!j.puedeMover(JUGADOR_X));
    j.nueva(1000);
    CHECK(j.fase == TURNO && j.turno == JUGADOR_X);
    CHECK(j.puedeMover(JUGADOR_X) && !j.puedeMover(JUGADOR_O));
    CHECK(j.restanteMs(1000) == j.cfg.turnoMs);

    // At timeout, nobody can move while the shot resolves.
    j.actualizar(1000 + j.cfg.turnoMs, false, 4);
    CHECK(j.fase == RESOLVIENDO);
    CHECK(!j.puedeMover(JUGADOR_X) && !j.puedeMover(JUGADOR_O));
    // Do not resolve while the head is still moving.
    j.actualizar(1000 + j.cfg.turnoMs + 200, false, 4);
    CHECK(j.fase == RESOLVIENDO);
    // After sufficient settling, place the center piece and pass the turn to O.
    uint32_t t = 1000 + j.cfg.turnoMs + 300;
    j.actualizar(t, true, 4);
    j.actualizar(t + j.cfg.quietoMs, true, 4);
    CHECK(j.tablero[4] == JUGADOR_X);
    CHECK(j.ultimo == PUESTA && j.ultimaCasilla == 4 && j.ultimoJugador == JUGADOR_X);
    CHECK(j.fase == TURNO && j.turno == JUGADOR_O);

    // O hits an occupied cell: lose the turn without changing the board.
    t = jugarTurno(j, t + j.cfg.quietoMs, 4);
    CHECK(j.ultimo == OCUPADA && j.tablero[4] == JUGADOR_X);
    CHECK(j.turno == JUGADOR_X);

    // X misses every cell and loses the turn.
    t = jugarTurno(j, t, SIN_CASILLA);
    CHECK(j.ultimo == FALLO && j.turno == JUGADOR_O);

    // Two active sensors between cells also count as a miss.
    t = jugarTurno(j, t, VARIAS);
    CHECK(j.ultimo == FALLO && j.turno == JUGADOR_X);
  }

  // Resolve at the maximum timeout if the head never settles.
  {
    Juego j;
    j.nueva(0);
    j.actualizar(j.cfg.turnoMs, false, 0);
    j.actualizar(j.cfg.turnoMs + j.cfg.resolverMaxMs - 1, false, 0);
    CHECK(j.fase == RESOLVIENDO);
    j.actualizar(j.cfg.turnoMs + j.cfg.resolverMaxMs, false, 0);
    CHECK(j.tablero[0] == JUGADOR_X && j.fase == TURNO);
  }

  // Only the active player can fire; firing enters RESOLVIENDO immediately.
  {
    Juego j;
    j.nueva(0);
    CHECK(!j.tirar(JUGADOR_O, 10));
    CHECK(j.fase == TURNO);
    CHECK(j.tirar(JUGADOR_X, 10));
    CHECK(j.fase == RESOLVIENDO);
    CHECK(!j.puedeMover(JUGADOR_X));
    j.actualizar(11, true, 4);
    j.actualizar(11 + j.cfg.quietoMs, true, 4);
    CHECK(j.tablero[4] == JUGADOR_X && j.turno == JUGADOR_O && j.fase == TURNO);
  }

  // Full game: X wins diagonally; O starts the next game.
  {
    Juego j;
    j.nueva(0);
    uint32_t t = 0;
    const uint8_t jugadas[] = {0, 1, 4, 2, 8};  // X:0,4,8  O:1,2
    for (uint8_t c : jugadas) t = jugarTurno(j, t, c);
    CHECK(j.fase == TERMINADO && j.ganadorPartida == JUGADOR_X);
    CHECK(!j.puedeMover(JUGADOR_X) && !j.puedeMover(JUGADOR_O));
    j.nueva(t);
    CHECK(j.turno == JUGADOR_O && j.fase == TURNO && j.tablero[0] == VACIA);
  }

  // Draw.
  {
    Juego j;
    j.nueva(0);
    uint32_t t = 0;
    // X: 0 2 3 7 8, O: 1 4 5 6; no winning line.
    const uint8_t jugadas[] = {0, 1, 2, 4, 3, 5, 7, 6, 8};
    for (uint8_t c : jugadas) t = jugarTurno(j, t, c);
    CHECK(j.fase == TERMINADO && j.ganadorPartida == EMPATE);
  }

  // Turn timer survives millis() wraparound.
  {
    Juego j;
    const uint32_t casiFin = 0xFFFFFFFFu - 1000;
    j.nueva(casiFin);
    CHECK(j.restanteMs(casiFin + 500) == j.cfg.turnoMs - 500);
    j.actualizar(casiFin + j.cfg.turnoMs - 1, true, 0);
    CHECK(j.fase == TURNO);
    j.actualizar(casiFin + j.cfg.turnoMs, true, 0);
    CHECK(j.fase == RESOLVIENDO);
  }

  if (fallas == 0) std::printf("OK: all tic-tac-toe tests pass\n");
  return fallas == 0 ? EXIT_SUCCESS : EXIT_FAILURE;
}
