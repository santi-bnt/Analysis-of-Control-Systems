#pragma once

#include <Arduino.h>

// Las credenciales y el certificado del broker van en Secrets.h.
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

constexpr float KP = 2.2f;
constexpr float PPR = 11.0f;
constexpr float REDUCCION = 34.0f;
// Se cuenta un flanco RISING de A, igual que en el código del motor.
// Segundo ajuste: 180 grados indicados correspondían a unos 90 grados reales.
constexpr float CPR = PPR * REDUCCION * 4.0f;  // 1496 pulsos por vuelta
constexpr float MIN_POSITION_DEG = -180.0f;
constexpr float MAX_POSITION_DEG = 180.0f;
