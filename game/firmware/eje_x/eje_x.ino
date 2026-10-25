// ESP32 X axis: BTS7960, encoder, automatic homing; Arduino-ESP32 2.x/3.x. NO switch: GPIO4 to
// NO, GND to COM. UART: X RX16 from S3 TX17, X TX17 to S3 RX18, shared GND. Receives $R,<refX
// mm>,<enabled>*HH; sends $T,<posX mm>,<refX mm>,<pwm>*HH and $H,<homing state>*HH. States: 0
// searching, 1 ready, 2 failed. HH is XOR between $ and *. Home before accepting setpoints. USB
// serial at 115200 with newline: r restarts; l measures X travel; x <mm> moves manually,
// ignoring S3; a restores S3 control. Status prints every second.

#include <Arduino.h>
#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <esp_arduino_version.h>

// ================= HOMING =================

class HomingSwitch {
 public:
  enum Estado {
    BUSCANDO,
    LISTO,
    FALLO
  };

  void iniciar(unsigned long ahora, bool cerrado) {
    inicio = ahora;
    cambio = ahora;
    lectura = cerrado;
    estado = BUSCANDO;
  }

  int actualizar(
      unsigned long ahora,
      bool cerrado,
      int magnitud,
      unsigned long limiteMs
  ) {
    if (estado != BUSCANDO) return 0;

    if (cerrado != lectura) {
      lectura = cerrado;
      cambio = ahora;
    }

    if (ahora - inicio >= limiteMs) {
      estado = FALLO;
      return 0;
    }

    // Stop on switch detection; require 30 ms of stable closure before setting zero.
    if (lectura) {
      if (ahora - cambio >= 30) {
        estado = LISTO;
      }
      return 0;
    }

    // Wait for a stable open switch before moving.
    if (ahora - cambio < 30) return 0;

    // Move toward minimum X.
    return -magnitud;
  }

  Estado estado = BUSCANDO;

 private:
  unsigned long inicio = 0;
  unsigned long cambio = 0;
  bool lectura = false;
};

// PWM 255 caused enough inertia to overshoot the switch by about 10 mm.
constexpr int HOMING_PWM = 140;
constexpr unsigned long HOMING_TIMEOUT_MS = 60000;

HomingSwitch homing;
bool homingAnunciado = false;

// Pin assignments.

constexpr int ENC_A = 32;
constexpr int ENC_B = 33;

constexpr int RPWM = 25;
constexpr int LPWM = 26;
constexpr int R_EN = 27;
constexpr int L_EN = 14;

constexpr int UART_RX = 16;
constexpr int UART_TX = 17;

constexpr int CERO_X_PIN = 4;
constexpr unsigned long CERO_X_ANTIRREBOTE_MS = 30;

constexpr int CANAL_R = 0;
constexpr int CANAL_L = 1;

constexpr int PWM_HZ = 5000;
constexpr int PWM_BITS = 8;

// Mechanics: x4 quadrature encoder, 1496 counts per revolution.
constexpr float CPR = 11.0f * 34.0f * 4.0f;

// Sensor calibration: GPIO13 to GPIO11 is physically 85 mm but measured 290 at 40 mm/rev;
// adjusted scale is 40 * 85 / 290.
constexpr float MM_POR_VUELTA_X = 11.72f;

constexpr float X_MIN = 0.0f;
// Measured with l: stop at 876.3 using 40 mm/rev, equivalent to 256.8 mm; reserve 10 mm.
constexpr float X_MAX = 246.8f;

// Positive motion moves away from the switch; negative approaches it. Encoder counts rose toward
// the switch, so use sign -1.
constexpr int SIGNO_ENCODER = -1;
constexpr int SIGNO_MOTOR = 1;

// PID only. Apply gains to angular error in degrees.
constexpr float KP = 4.5122f;
constexpr float KI = 21.0020f;
constexpr float KD = 0.1834f;

constexpr float BANDA_GRADOS = 0.5f;

constexpr unsigned long CONTROL_MS = 10;
constexpr unsigned long TELEMETRIA_MS = 50;
constexpr unsigned long TIMEOUT_MS = 500;

// ================= VARIABLES =================

HardwareSerial enlace(2);
uint32_t bytesRx = 0;   // Received S3 byte count for link diagnostics.

volatile long conteo = 0;
volatile uint8_t estadoEncoder = 0;

portMUX_TYPE bloqueoEncoder = portMUX_INITIALIZER_UNLOCKED;

// Quadrature lookup table kept in RAM for the ISR.
DRAM_ATTR static const int8_t pasos[16] = {
   0, -1,  1,  0,
   1,  0,  0, -1,
  -1,  0,  0,  1,
   0,  1, -1,  0
};

float posX = 0.0f;
float refX = 0.0f;
int pwm = 0;

bool recibido = false;
bool habilitado = false;
bool manual = false;   // Manual serial x <mm> mode ignores S3 commands.

unsigned long ultimoComando = 0;

float integral = 0.0f;
float anteriorGrados = 0.0f;
float velocidadFiltrada = 0.0f;

int ceroXLectura = HIGH;
int ceroXEstable = HIGH;
unsigned long ceroXCambioMs = 0;

// ================= ENCODER =================

void IRAM_ATTR leerEncoder() {
  portENTER_CRITICAL_ISR(&bloqueoEncoder);

  const uint8_t actual =
      (digitalRead(ENC_A) << 1) | digitalRead(ENC_B);

  conteo += pasos[(estadoEncoder << 2) | actual];
  estadoEncoder = actual;

  portEXIT_CRITICAL_ISR(&bloqueoEncoder);
}

long leerConteo() {
  portENTER_CRITICAL(&bloqueoEncoder);
  const long copia = conteo;
  portEXIT_CRITICAL(&bloqueoEncoder);

  return copia;
}

// ================= DRIVER BTS7960 =================

void escribirPWM(int r, int l) {
#if ESP_ARDUINO_VERSION_MAJOR >= 3
  ledcWrite(RPWM, r);
  ledcWrite(LPWM, l);
#else
  ledcWrite(CANAL_R, r);
  ledcWrite(CANAL_L, l);
#endif
}

void detenerMotor() {
  escribirPWM(0, 0);

  digitalWrite(R_EN, LOW);
  digitalWrite(L_EN, LOW);

  pwm = 0;
}

void moverMotor(int orden) {
  orden = constrain(orden, -255, 255);

  if (orden == 0) {
    detenerMotor();
    return;
  }

  const int fisico = SIGNO_MOTOR * orden;

  escribirPWM(0, 0);

  digitalWrite(R_EN, HIGH);
  digitalWrite(L_EN, HIGH);

  escribirPWM(
      fisico > 0 ? fisico : 0,
      fisico < 0 ? -fisico : 0
  );

  pwm = orden;
}

// Switch zeroing: one zero per stable closure. Require release before accepting another closure.
bool atenderCeroX(unsigned long ahora) {
  const int lectura = digitalRead(CERO_X_PIN);

  if (lectura != ceroXLectura) {
    ceroXLectura = lectura;
    ceroXCambioMs = ahora;
  }

  if (lectura == ceroXEstable ||
      ahora - ceroXCambioMs < CERO_X_ANTIRREBOTE_MS) {
    return false;
  }

  ceroXEstable = lectura;

  if (ceroXEstable != LOW) return false;

  detenerMotor();

  portENTER_CRITICAL(&bloqueoEncoder);

  conteo = 0;
  estadoEncoder =
      (digitalRead(ENC_A) << 1) | digitalRead(ENC_B);

  portEXIT_CRITICAL(&bloqueoEncoder);

  posX = 0.0f;
  refX = 0.0f;

  integral = 0.0f;
  anteriorGrados = 0.0f;
  velocidadFiltrada = 0.0f;

  Serial.println(
      "[ZERO X] Switch activated: this is 0 mm."
  );

  return true;
}

// ================= HOMING AUTOMATICO =================

bool atenderHomingX(unsigned long ahora) {
  if (homing.estado == HomingSwitch::LISTO) {
    return false;
  }

  const int orden = homing.actualizar(
      ahora,
      digitalRead(CERO_X_PIN) == LOW,
      HOMING_PWM,
      HOMING_TIMEOUT_MS
  );

  // Homing bypasses PID position limits because absolute position is not yet known.
  moverMotor(orden);

  posX =
      SIGNO_ENCODER *
      (leerConteo() / CPR) *
      MM_POR_VUELTA_X;

  if (homing.estado == HomingSwitch::LISTO) {
    detenerMotor();

    portENTER_CRITICAL(&bloqueoEncoder);

    conteo = 0;
    estadoEncoder =
        (digitalRead(ENC_A) << 1) | digitalRead(ENC_B);

    portEXIT_CRITICAL(&bloqueoEncoder);

    posX = 0.0f;
    refX = 0.0f;

    integral = 0.0f;
    anteriorGrados = 0.0f;
    velocidadFiltrada = 0.0f;

    ceroXLectura = LOW;
    ceroXEstable = LOW;
    ceroXCambioMs = ahora;

    // Wait for a new command after establishing zero.
    recibido = false;
    habilitado = false;

    Serial.println("[HOME X] Ready: this is 0 mm.");

  } else if (homing.estado == HomingSwitch::FALLO &&
             !homingAnunciado) {
    detenerMotor();

    homingAnunciado = true;

    Serial.println(
        "[HOME X] ERROR: switch not found within 60 s. "
        "Restart to retry."
    );
  }

  return true;
}

// X travel test: after homing, serial l moves away from the switch until the motor stalls at the
// stop, then reports position.
constexpr int MEDIR_PWM = 140;
constexpr unsigned long MEDIR_QUIETO_MS = 500;
bool midiendoX = false;
unsigned long medirInicioMs = 0;
unsigned long medirAvanceMs = 0;
float medirMaxX = 0.0f;

bool atenderMedirX(unsigned long ahora) {
  if (!midiendoX) return false;

  posX = SIGNO_ENCODER * (leerConteo() / CPR) * MM_POR_VUELTA_X;

  if (posX > medirMaxX + 0.2f) {
    medirMaxX = posX;
    medirAvanceMs = ahora;
  }

  if (ahora - medirAvanceMs < MEDIR_QUIETO_MS &&
      ahora - medirInicioMs < 30000) {
    moverMotor(MEDIR_PWM);
    return true;
  }

  detenerMotor();
  midiendoX = false;
  refX = posX;
  integral = 0.0f;
  velocidadFiltrada = 0.0f;

  Serial.printf(
      "[LIMIT X] motor stopped at %.1f mm -> limit = %.1f mm\n",
      posX, posX - 10.0f);
  return true;
}

// ================= PROTOCOLO UART =================

uint8_t checksum(const char *s, size_t n) {
  uint8_t resultado = 0;

  for (size_t i = 0; i < n; ++i) {
    resultado ^= (uint8_t)s[i];
  }

  return resultado;
}

int hexDigito(char c) {
  if (c >= '0' && c <= '9') return c - '0';
  if (c >= 'A' && c <= 'F') return c - 'A' + 10;
  if (c >= 'a' && c <= 'f') return c - 'a' + 10;

  return -1;
}

void procesarLinea(char *linea) {
  if (linea[0] != '$') return;

  char *asterisco = strchr(linea, '*');

  if (!asterisco || strlen(asterisco + 1) != 2) {
    return;
  }

  const int h = hexDigito(asterisco[1]);
  const int l = hexDigito(asterisco[2]);

  if (h < 0 || l < 0) return;

  const uint8_t calculado =
      checksum(linea + 1, asterisco - linea - 1);

  if (calculado != ((h << 4) | l)) return;

  *asterisco = '\0';

  if (strncmp(linea + 1, "R,", 2) != 0) return;

  char *fin = nullptr;

  const float referencia = strtof(linea + 3, &fin);

  if (fin == linea + 3 ||
      !isfinite(referencia) ||
      *fin != ',') {
    return;
  }

  const char *flag = fin + 1;

  if ((flag[0] != '0' && flag[0] != '1') ||
      flag[1] != '\0') {
    return;
  }

  if (referencia < X_MIN || referencia > X_MAX) {
    return;
  }

  // Reject setpoints until homing completes and while serial manual movement is active.
  if (homing.estado != HomingSwitch::LISTO || manual) {
    return;
  }

  refX = referencia;
  habilitado = flag[0] == '1';

  recibido = true;
  ultimoComando = millis();
}

void leerUART() {
  static char linea[80];
  static size_t largo = 0;
  static bool capturando = false;

  for (int i = 0; i < 128 && enlace.available(); ++i) {
    const char c = enlace.read();
    bytesRx++;

    if (c == '$') {
      largo = 0;
      capturando = true;

      linea[largo++] = c;

    } else if (c == '\n' || c == '\r') {
      if (capturando && largo > 0) {
        linea[largo] = '\0';
        procesarLinea(linea);
      }

      largo = 0;
      capturando = false;

    } else if (capturando) {
      if (largo < sizeof(linea) - 1) {
        linea[largo++] = c;
      } else {
        largo = 0;
        capturando = false;
      }
    }
  }
}

// ================= CONTROL PID =================

void controlar(float dt, unsigned long ahora) {
  const float grados =
      SIGNO_ENCODER *
      (leerConteo() / CPR) *
      360.0f;

  // Linear position in millimeters.
  posX = grados * MM_POR_VUELTA_X / 360.0f;

  const bool activo =
      manual ||
      (recibido &&
       habilitado &&
       ahora - ultimoComando <= TIMEOUT_MS);

  if (!activo) {
    integral = 0.0f;
    velocidadFiltrada = 0.0f;
    anteriorGrados = grados;

    detenerMotor();
    return;
  }

  // Convert linear error to degrees.
  const float error =
      (refX - posX) * 360.0f / MM_POR_VUELTA_X;

  // Filtered derivative on measurement.
  velocidadFiltrada =
      0.8f * velocidadFiltrada +
      0.2f * (grados - anteriorGrados) / dt;

  anteriorGrados = grados;

  if (fabsf(error) <= BANDA_GRADOS) {
    integral = 0.0f;

    detenerMotor();
    return;
  }

  const float derivada = -KD * velocidadFiltrada;
  const float propuesta = integral + error * dt;

  const float u =
      KP * error +
      KI * propuesta +
      derivada;

  // Anti-windup.
  const bool empujaSaturacion =
      (u > 255.0f && error > 0.0f) ||
      (u < -255.0f && error < 0.0f);

  if (!empujaSaturacion) {
    integral = propuesta;
  }

  float salida =
      KP * error +
      KI * integral +
      derivada;

  salida = constrain(salida, -255.0f, 255.0f);

  // Limits based on measured position.
  if ((posX <= X_MIN && salida < 0.0f) ||
      (posX >= X_MAX && salida > 0.0f)) {
    salida = 0.0f;
    integral = 0.0f;
  }

  // While the switch is pressed, allow only motion away from it toward positive X.
  if ((digitalRead(CERO_X_PIN) == LOW ||
       ceroXEstable == LOW) &&
      salida < 0.0f) {
    salida = 0.0f;
    integral = 0.0f;
  }

  moverMotor((int)lroundf(salida));
}

// ================= TELEMETRIA =================

void enviarTelemetria() {
  char cuerpo[64];

  // Separate homing-status message.
  const int nh = snprintf(
      cuerpo,
      sizeof(cuerpo),
      "H,%d",
      (int)homing.estado
  );

  enlace.printf(
      "$%s*%02X\n",
      cuerpo,
      checksum(cuerpo, nh)
  );

  // Original position, setpoint, and PWM telemetry message.
  const int n = snprintf(
      cuerpo,
      sizeof(cuerpo),
      "T,%.3f,%.3f,%d",
      posX,
      refX,
      pwm
  );

  if (n > 0 && n < (int)sizeof(cuerpo)) {
    enlace.printf(
        "$%s*%02X\n",
        cuerpo,
        checksum(cuerpo, n)
    );
  }
}

// ================= MONITOR SERIE =================

void ejecutarComando(const char *linea) {
  float mm;

  if (strcmp(linea, "r") == 0) {
    ESP.restart();

  } else if (strcmp(linea, "l") == 0) {
    if (homing.estado == HomingSwitch::LISTO && !midiendoX) {
      midiendoX = true;
      medirInicioMs = medirAvanceMs = millis();
      medirMaxX = posX;
      Serial.println("[LIMIT X] Measuring: moving toward the travel stop...");
    }

  } else if (sscanf(linea, "x %f", &mm) == 1) {
    if (homing.estado != HomingSwitch::LISTO) {
      Serial.println("[MAP] Wait for X homing to finish.");
      return;
    }
    refX = constrain(mm, X_MIN, X_MAX);
    manual = true;
    Serial.printf("[MAP] Moving to x=%.1f (manual; \"a\" returns control to S3)\n", refX);

  } else if (strcmp(linea, "a") == 0) {
    manual = false;
    Serial.println("[MAP] Control returned to S3.");

  } else {
    Serial.printf("[MAP] Unknown command: %s\n", linea);
  }
}

void atenderMonitor() {
  static char linea[32];
  static size_t largo = 0;

  while (Serial.available()) {
    const char c = Serial.read();
    if (c == '\n' || c == '\r') {
      if (largo > 0) {
        linea[largo] = '\0';
        ejecutarComando(linea);
      }
      largo = 0;
    } else if (largo < sizeof(linea) - 1) {
      linea[largo++] = c;
    }
  }
}

// ================= INICIALIZACION =================

void setup() {
  Serial.begin(115200);

  pinMode(R_EN, OUTPUT);
  pinMode(L_EN, OUTPUT);

  digitalWrite(R_EN, LOW);
  digitalWrite(L_EN, LOW);

#if ESP_ARDUINO_VERSION_MAJOR >= 3
  const bool okR = ledcAttach(RPWM, PWM_HZ, PWM_BITS);
  const bool okL = ledcAttach(LPWM, PWM_HZ, PWM_BITS);

  if (!okR || !okL) {
    Serial.println(
        "PWM setup failed; driver disabled."
    );

    while (true) {
      delay(1000);
    }
  }
#else
  ledcSetup(CANAL_R, PWM_HZ, PWM_BITS);
  ledcSetup(CANAL_L, PWM_HZ, PWM_BITS);

  ledcAttachPin(RPWM, CANAL_R);
  ledcAttachPin(LPWM, CANAL_L);
#endif

  detenerMotor();

  pinMode(ENC_A, INPUT_PULLUP);
  pinMode(ENC_B, INPUT_PULLUP);

  estadoEncoder =
      (digitalRead(ENC_A) << 1) | digitalRead(ENC_B);

  attachInterrupt(
      digitalPinToInterrupt(ENC_A),
      leerEncoder,
      CHANGE
  );

  attachInterrupt(
      digitalPinToInterrupt(ENC_B),
      leerEncoder,
      CHANGE
  );

  pinMode(CERO_X_PIN, INPUT_PULLUP);

  ceroXLectura = digitalRead(CERO_X_PIN);
  ceroXEstable = HIGH;
  ceroXCambioMs = millis();

  enlace.begin(
      115200,
      SERIAL_8N1,
      UART_RX,
      UART_TX
  );

  homing.iniciar(
      millis(),
      digitalRead(CERO_X_PIN) == LOW
  );

  Serial.println("[HOME X] Searching for switch on GPIO4...");
}

// ================= BUCLE PRINCIPAL =================

void loop() {
  static unsigned long ultimoControl = millis();
  static unsigned long ultimaTelemetria = 0;
  static unsigned long ultimoLog = 0;

  // USB serial r restarts the board.
  atenderMonitor();

  leerUART();

  const unsigned long ahora = millis();

  if (ahora - ultimoControl >= CONTROL_MS) {
    const float dt =
        (ahora - ultimoControl) / 1000.0f;

    ultimoControl = ahora;

    if (!atenderHomingX(ahora) &&
        !atenderMedirX(ahora) &&
        !atenderCeroX(ahora)) {
      controlar(dt, ahora);
    }
  }

  if (ahora - ultimaTelemetria >= TELEMETRIA_MS) {
    ultimaTelemetria = ahora;
    enviarTelemetria();
  }

  // Status every second; homing states: 0 searching, 1 ready, 2 failed.
  if (ahora - ultimoLog >= 1000) {
    ultimoLog = ahora;
    Serial.printf("[X] rx=%lu homing=%d switch=%s posX=%.1f pwm=%d refX=%.1f habilitado=%d manual=%d\n",
                  (unsigned long)bytesRx, (int)homing.estado,
                  digitalRead(CERO_X_PIN) == LOW ? "pressed" : "released",
                  posX, pwm, refX, habilitado, manual);
  }

  delay(1);
}
