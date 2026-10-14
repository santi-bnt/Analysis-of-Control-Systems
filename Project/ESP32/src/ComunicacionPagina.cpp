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

// El control y la red comparten estos datos desde dos tareas.
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
        Serial.printf("[WiFi] Reconectando; estado: %d\n", WiFi.status());
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
      }
      vTaskDelay(pdMS_TO_TICKS(100));
      continue;
    }

    if (MQTT_USE_TLS && time(nullptr) < 1700000000) {
      if (!relojIniciado) {
        configTime(0, 0, "pool.ntp.org", "time.nist.gov");
        Serial.println("[TLS] Sincronizando la hora...");
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
      Serial.printf("[MQTT] Conectando a %s:%d; IP ESP32: %s\n",
                    MQTT_HOST, MQTT_PORT, WiFi.localIP().toString().c_str());
      const bool conectado = mqtt.connect(
          DEVICE_ID, MQTT_USERNAME, MQTT_PASSWORD,
          temaEstado, 1, true, "offline", false);

      if (conectado) {
        mqtt.subscribe(temaComandos, 1);
        mqtt.publish(temaEstado, "online", true);
        Serial.printf("[MQTT] %s conectado\n", DEVICE_ID);
      } else {
        Serial.printf("[MQTT] Fallo de conexion; codigo: %d\n", mqtt.state());
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
    Serial.println("[MQTT] Conexion local sin TLS.");
  } else {
    mqtt.setClient(clienteSeguro);
    if (MQTT_ROOT_CA_PEM[0] == '\0') {
      Serial.println("Falta MQTT_ROOT_CA_PEM en include/Secrets.h para conectar con TLS.");
    } else {
      clienteSeguro.setCACert(MQTT_ROOT_CA_PEM);
      certificadoConfigurado = true;
    }
  }

  // La red corre en el otro núcleo para no bloquear el control.
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
  // Guardamos las lecturas; la tarea de red las envía después.
  portENTER_CRITICAL(&bloqueoDatos);
  referenciaTelemetria = ref;
  posicionActual = posicion;
  errorActual = error;
  salidaActual = salida;
  portEXIT_CRITICAL(&bloqueoDatos);
}

}  // namespace ComunicacionPagina
