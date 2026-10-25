# Encoder Reading and P, PI, and PID Control Code Appendix

Select exactly one mode in `include/Config.h`. P is active in the current firmware; comment `CONTROL_MODE_P` and uncomment `CONTROL_MODE_PI` or `CONTROL_MODE_PID` to change modes. The control task uses a 10 ms period. PI and PID include conditional integral anti-windup. The gains from the supplied practice PID are `KP = 4.5122`, `KI = 21.0020`, and `KD = 0.1834`. The original example uses an analog input and DAC output for a different plant; this firmware applies its PID equation and gains to the motor encoder and BTS7960. These gains have not been validated on that motor.
## src/main.cpp

```cpp
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
```

## src/ControlPosicion.cpp

```cpp
#include <Arduino.h>
#include "Config.h"
#include "ComunicacionPagina.h"
#include "ControlPosicion.h"

namespace {
volatile long encoderCount = 0;
portMUX_TYPE bloqueoEncoder = portMUX_INITIALIZER_UNLOCKED;

void IRAM_ATTR leerEncoder() {
  const int sentido = digitalRead(ENC_B) == HIGH ? 1 : -1;
  portENTER_CRITICAL_ISR(&bloqueoEncoder);
  encoderCount += sentido;
  portEXIT_CRITICAL_ISR(&bloqueoEncoder);
}

long leerConteo() {
  portENTER_CRITICAL(&bloqueoEncoder);
  const long conteo = encoderCount;
  portEXIT_CRITICAL(&bloqueoEncoder);
  return conteo;
}

void detenerMotor() {
  ledcWrite(CANAL_RPWM, 0);
  ledcWrite(CANAL_LPWM, 0);
  digitalWrite(R_EN, LOW);
  digitalWrite(L_EN, LOW);
}

void moverMotor(float salida) {
  const int pwm = lroundf(fabsf(salida));
  if (pwm == 0) {
    detenerMotor();
    return;
  }
  ledcWrite(CANAL_RPWM, 0);
  ledcWrite(CANAL_LPWM, 0);
  digitalWrite(R_EN, HIGH);
  digitalWrite(L_EN, HIGH);
  if (salida > 0.0f) {
    ledcWrite(CANAL_RPWM, pwm);
  } else {
    ledcWrite(CANAL_LPWM, pwm);
  }
}

void tareaControl(void*) {
  TickType_t ultimoDespertar = xTaskGetTickCount();
  const TickType_t periodo = pdMS_TO_TICKS(CONTROL_PERIOD_MS);
  const float dt = CONTROL_PERIOD_MS / 1000.0f;
  unsigned long tiempoAnterior = millis();
  long conteoAnterior = leerConteo();
  float integral = 0.0f;
  float errorAnterior = 0.0f;

  for (;;) {
    vTaskDelayUntil(&ultimoDespertar, periodo);
    const long conteoActual = leerConteo();
    const float angulo = (conteoActual / CPR) * 360.0f;
    const auto comando = ComunicacionPagina::leerComando();
    const float referencia = comando.referencia;
    const float error = referencia - angulo;
    float salida = 0.0f;

    if (comando.habilitado) {
      const float integralPropuesta = integral + error * dt;
      const float derivada = (error - errorAnterior) / dt;

#if defined(CONTROL_MODE_P)
      salida = KP * error;

#elif defined(CONTROL_MODE_PI)
      const float salidaPropuesta = KP * error + KI * integralPropuesta;
      const bool integralEmpujaSaturacion =
          (salidaPropuesta > 255.0f && error > 0.0f) ||
          (salidaPropuesta < -255.0f && error < 0.0f);
      if (!integralEmpujaSaturacion) {
        integral = integralPropuesta;
      }
      salida = KP * error + KI * integral;

#elif defined(CONTROL_MODE_PID)
      const float salidaPropuesta =
          KP * error + KI * integralPropuesta + KD * derivada;
      const bool integralEmpujaSaturacion =
          (salidaPropuesta > 255.0f && error > 0.0f) ||
          (salidaPropuesta < -255.0f && error < 0.0f);
      if (!integralEmpujaSaturacion) {
        integral = integralPropuesta;
      }
      salida = KP * error + KI * integral + KD * derivada;
#endif

      salida = constrain(salida, -255.0f, 255.0f);
      errorAnterior = error;
      moverMotor(salida);
    } else {
      integral = 0.0f;
      errorAnterior = 0.0f;
      detenerMotor();
    }
    ComunicacionPagina::actualizarTelemetria(referencia, angulo, error, salida);
    const unsigned long ahora = millis();
    const unsigned long transcurrido = ahora - tiempoAnterior;
    if (transcurrido >= TELEMETRY_PERIOD_MS) {
      const long pulsosNuevos = conteoActual - conteoAnterior;
      const float rpm = fabsf((pulsosNuevos / CPR) * 60000.0f / transcurrido);
      Serial.printf("Ref: %.1f | Angle: %.2f | RPM: %.2f | Error: %.2f | U: %.1f\\n",
                    referencia, angulo, rpm, error, salida);
      conteoAnterior = conteoActual;
      tiempoAnterior = ahora;
    }
  }
}
}

namespace ControlPosicion {
void iniciarHardware() {
  pinMode(R_EN, OUTPUT);
  pinMode(L_EN, OUTPUT);
  digitalWrite(R_EN, LOW);
  digitalWrite(L_EN, LOW);
  ledcSetup(CANAL_RPWM, FRECUENCIA_PWM, RESOLUCION_PWM);
  ledcSetup(CANAL_LPWM, FRECUENCIA_PWM, RESOLUCION_PWM);
  ledcAttachPin(RPWM, CANAL_RPWM);
  ledcAttachPin(LPWM, CANAL_LPWM);
  detenerMotor();
  pinMode(ENC_A, INPUT_PULLUP);
  pinMode(ENC_B, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(ENC_A), leerEncoder, RISING);
}

void iniciarTarea() {
  const BaseType_t resultado = xTaskCreatePinnedToCore(
      tareaControl, "ControlPosicion", 4096, nullptr, 5, nullptr, 1);
  if (resultado != pdPASS) {
    detenerMotor();
    Serial.println("[Control] Could not start task; motor stopped.");
  }
}
}
```

## src/ControlPosicion.h

```cpp
#pragma once

namespace ControlPosicion {
void iniciarHardware();
void iniciarTarea();
}
```

## src/ComunicacionPagina.cpp

```cpp
#include "ComunicacionPagina.h"
#include "Config.h"
#include <WiFi.h>
#include <WiFiClientSecure.h>
#include <PubSubClient.h>
#include <ArduinoJson.h>
#include <time.h>

namespace {

WiFiClient clienteLocal;
WiFiClientSecure clienteSeguro;
PubSubClient mqtt;

// Control and network tasks share this data.
portMUX_TYPE bloqueoDatos = portMUX_INITIALIZER_UNLOCKED;

float referencia = 0.0f;
float referenciaTelemetria = 0.0f;
bool hayReferencia = false;
float posicionActual = 0.0f;
float errorActual = 0.0f;
float salidaActual = 0.0f;
bool mqttConectado = false;
bool paroEmergencia = false;
unsigned long ultimoComandoMs = 0;

char temaComandos[64];
char temaTelemetria[64];
char temaEstado[64];
bool certificadoConfigurado = false;

void recibirComando(char* tema, byte* contenido, unsigned int longitud) {
  if (strcmp(tema, temaComandos) != 0 || longitud == 0 || longitud > 128) return;

  JsonDocument comando;
  if (deserializeJson(comando, contenido, longitud)) return;

  const char* tipo = comando["type"] | "";

  if (strcmp(tipo, "position") == 0) {
    if (!comando["value"].is<float>() && !comando["value"].is<int>()) return;

    const float nuevaReferencia = comando["value"].as<float>();
    if (!isfinite(nuevaReferencia) ||
        nuevaReferencia < MIN_POSITION_DEG || nuevaReferencia > MAX_POSITION_DEG) return;

    portENTER_CRITICAL(&bloqueoDatos);
    if (!paroEmergencia) {
      referencia = nuevaReferencia;
      hayReferencia = true;
      ultimoComandoMs = millis();
    }
    portEXIT_CRITICAL(&bloqueoDatos);
  } else if (strcmp(tipo, "heartbeat") == 0) {
    portENTER_CRITICAL(&bloqueoDatos);
    if (hayReferencia && !paroEmergencia) {
      ultimoComandoMs = millis();
    }
    portEXIT_CRITICAL(&bloqueoDatos);
  } else if (strcmp(tipo, "estop") == 0 || strcmp(tipo, "stop") == 0) {
    portENTER_CRITICAL(&bloqueoDatos);
    paroEmergencia = true;
    portEXIT_CRITICAL(&bloqueoDatos);
  } else if (strcmp(tipo, "reset") == 0) {
    portENTER_CRITICAL(&bloqueoDatos);
    paroEmergencia = false;
    referencia = 0.0f;
    hayReferencia = false;
    ultimoComandoMs = 0;
    portEXIT_CRITICAL(&bloqueoDatos);
  }
}

void enviarTelemetria(unsigned long ahora) {
  portENTER_CRITICAL(&bloqueoDatos);
  const float ref = referenciaTelemetria;
  const float posicion = posicionActual;
  const float error = errorActual;
  const float salida = salidaActual;
  portEXIT_CRITICAL(&bloqueoDatos);

  JsonDocument telemetria;
  telemetria["deviceId"] = DEVICE_ID;
  telemetria["setpoint"] = ref;
  telemetria["position"] = posicion;
  telemetria["error"] = error;
  telemetria["pwm"] = salida * (100.0f / 255.0f);
  telemetria["timestamp"] = ahora;

  char contenido[256];
  const size_t longitud = serializeJson(telemetria, contenido, sizeof(contenido));
  mqtt.publish(temaTelemetria, reinterpret_cast<const uint8_t*>(contenido), longitud, false);
}

void tareaComunicacion(void*) {
  unsigned long ultimoIntentoWifi = 0;
  unsigned long ultimoIntentoMqtt = 0;
  unsigned long ultimaTelemetria = 0;
  bool relojIniciado = false;

  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(recibirComando);
  mqtt.setBufferSize(512);
  mqtt.setSocketTimeout(2);

  for (;;) {
    const unsigned long ahora = millis();

    if (WiFi.status() != WL_CONNECTED) {
      if (mqtt.connected()) mqtt.disconnect();
      portENTER_CRITICAL(&bloqueoDatos);
      mqttConectado = false;
      portEXIT_CRITICAL(&bloqueoDatos);

      if (ahora - ultimoIntentoWifi >= WIFI_RETRY_MS) {
        ultimoIntentoWifi = ahora;
        Serial.printf("[WiFi] Reconnecting; status: %d\n", WiFi.status());
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      }
      vTaskDelay(pdMS_TO_TICKS(100));
      continue;
    }

    if (MQTT_USE_TLS && time(nullptr) < 1700000000) {
      if (!relojIniciado) {
        configTime(0, 0, "pool.ntp.org", "time.nist.gov");
        Serial.println("[TLS] Synchronizing time...");
        relojIniciado = true;
      }
      vTaskDelay(pdMS_TO_TICKS(100));
      continue;
    }

    if (MQTT_USE_TLS && !certificadoConfigurado) {
      vTaskDelay(pdMS_TO_TICKS(500));
      continue;
    }

    if (!mqtt.connected() && ahora - ultimoIntentoMqtt >= MQTT_RETRY_MS) {
      ultimoIntentoMqtt = ahora;
      Serial.printf("[MQTT] Connecting to %s:%d; ESP32 IP: %s\n",
                    MQTT_HOST, MQTT_PORT, WiFi.localIP().toString().c_str());
      const bool conectado = mqtt.connect(
          DEVICE_ID, MQTT_USERNAME, MQTT_PASSWORD,
          temaEstado, 1, true, "offline", false);

      if (conectado) {
        mqtt.subscribe(temaComandos, 1);
        mqtt.publish(temaEstado, "online", true);
        Serial.printf("[MQTT] %s connected\n", DEVICE_ID);
      } else {
        Serial.printf("[MQTT] Connection failed; code: %d\n", mqtt.state());
      }

      portENTER_CRITICAL(&bloqueoDatos);
      mqttConectado = conectado;
      portEXIT_CRITICAL(&bloqueoDatos);
    }

    if (mqtt.connected()) {
      mqtt.loop();
      if (ahora - ultimaTelemetria >= TELEMETRY_PERIOD_MS) {
        ultimaTelemetria = ahora;
        enviarTelemetria(ahora);
      }
    }

    const bool conectado = mqtt.connected();
    portENTER_CRITICAL(&bloqueoDatos);
    mqttConectado = conectado;
    portEXIT_CRITICAL(&bloqueoDatos);
    vTaskDelay(pdMS_TO_TICKS(10));
  }
}

}  // namespace

namespace ComunicacionPagina {

void iniciar() {
  snprintf(temaComandos, sizeof(temaComandos), "motor/%s/command", DEVICE_ID);
  snprintf(temaTelemetria, sizeof(temaTelemetria), "motor/%s/telemetry", DEVICE_ID);
  snprintf(temaEstado, sizeof(temaEstado), "motor/%s/status", DEVICE_ID);

  if (!MQTT_USE_TLS) {
    mqtt.setClient(clienteLocal);
    Serial.println("[MQTT] Local connection without TLS.");
  } else {
    mqtt.setClient(clienteSeguro);
    if (MQTT_ROOT_CA_PEM[0] == '\0') {
      Serial.println("Set MQTT_ROOT_CA_PEM in include/Secrets.h to connect using TLS.");
    } else {
      clienteSeguro.setCACert(MQTT_ROOT_CA_PEM);
      certificadoConfigurado = true;
    }
  }

  // Run networking on the other core to avoid blocking control.
  xTaskCreatePinnedToCore(tareaComunicacion, "WiFiMQTT", 8192, nullptr, 2, nullptr, 0);
}

Comando leerComando() {
  portENTER_CRITICAL(&bloqueoDatos);
  const unsigned long ahora = millis();
  Comando comando{
      referencia,
      hayReferencia && mqttConectado && !paroEmergencia &&
          ahora - ultimoComandoMs <= STALE_COMMAND_TIMEOUT_MS};
  portEXIT_CRITICAL(&bloqueoDatos);
  return comando;
}

void actualizarTelemetria(float ref, float posicion, float error, float salida) {
  // Store readings for the network task to transmit later.
  portENTER_CRITICAL(&bloqueoDatos);
  referenciaTelemetria = ref;
  posicionActual = posicion;
  errorActual = error;
  salidaActual = salida;
  portEXIT_CRITICAL(&bloqueoDatos);
}

}  // namespace ComunicacionPagina
```

## src/ComunicacionPagina.h

```cpp
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
```

## include/Config.h

```cpp
#pragma once

#include <Arduino.h>

// Credentials and the broker certificate belong in Secrets.h.
#if __has_include("Secrets.h")
#include "Secrets.h"
#endif

#ifndef WIFI_SSID
#define WIFI_SSID "YOUR_WIFI_SSID"
#endif
#ifndef WIFI_PASSWORD
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
#endif
#ifndef MQTT_HOST
#define MQTT_HOST "YOUR_MQTT_HOST"
#endif
#ifndef MQTT_PORT
#define MQTT_PORT 8883
#endif
#ifndef MQTT_USE_TLS
#define MQTT_USE_TLS true
#endif
#ifndef MQTT_USERNAME
#define MQTT_USERNAME "YOUR_MQTT_USERNAME"
#endif
#ifndef MQTT_PASSWORD
#define MQTT_PASSWORD "YOUR_MQTT_PASSWORD"
#endif
#ifndef DEVICE_ID
#define DEVICE_ID "motor-01"
#endif
#ifndef MQTT_ROOT_CA_PEM
#define MQTT_ROOT_CA_PEM ""
#endif

constexpr int ENC_A = 32;
constexpr int ENC_B = 33;
constexpr int RPWM = 25;
constexpr int LPWM = 26;
constexpr int R_EN = 27;
constexpr int L_EN = 14;
constexpr int CANAL_RPWM = 0;
constexpr int CANAL_LPWM = 1;
constexpr int FRECUENCIA_PWM = 5000;
constexpr int RESOLUCION_PWM = 8;
constexpr unsigned long CONTROL_PERIOD_MS = 10;
constexpr unsigned long TELEMETRY_PERIOD_MS = 100;
constexpr unsigned long STALE_COMMAND_TIMEOUT_MS = 5000;
constexpr unsigned long WIFI_RETRY_MS = 5000;
constexpr unsigned long MQTT_RETRY_MS = 5000;

// Enable exactly one controller and leave the other defines commented.
#define CONTROL_MODE_P
//#define CONTROL_MODE_PI
//#define CONTROL_MODE_PID

#if (defined(CONTROL_MODE_P) + defined(CONTROL_MODE_PI) + defined(CONTROL_MODE_PID)) != 1
#error "Enable exactly one mode: CONTROL_MODE_P, CONTROL_MODE_PI, or CONTROL_MODE_PID"
#endif

// Gains copied from the supplied practice PID; validate them on the motor.
constexpr float KP = 4.5122f;
constexpr float KI = 21.0020f;
constexpr float KD = 0.1834f;
constexpr float PPR = 11.0f;
constexpr float REDUCCION = 34.0f;
// Count A rising edges as in the motor implementation. Second scale adjustment: indicated 180 degrees corresponded to about 90 physical degrees.
constexpr float CPR = PPR * REDUCCION * 4.0f;  // 1496 counts per revolution.
constexpr float MIN_POSITION_DEG = -180.0f;
constexpr float MAX_POSITION_DEG = 180.0f;
```

## platformio.ini

```ini
[env:esp32dev]
platform = espressif32
board = esp32dev
framework = arduino
monitor_speed = 115200

lib_deps =
    knolleary/PubSubClient @ ^2.8
    bblanchon/ArduinoJson @ ^7.0.0
```
