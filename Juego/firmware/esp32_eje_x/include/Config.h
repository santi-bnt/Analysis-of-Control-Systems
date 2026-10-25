#pragma once

#include <Arduino.h>

// Earlier ESP32 X-axis controller; S3 reads the LDR array and runs BLE/game logic. UART replaces
// Wi-Fi/MQTT. Original rack-and-pinion/GT2 20-tooth BIQU B1 mechanism advances 40 mm/rev. PID
// works in motor degrees; convert incoming mm to degrees and reported position back to mm.
// Original motor pin assignments follow.
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
constexpr unsigned long TELEMETRY_PERIOD_MS = 100;   // USB serial monitor. Crossed Serial2 UART: local TX to S3 RX, local RX from S3 TX, common GND.
constexpr int UART_RX = 16;
constexpr int UART_TX = 17;
constexpr unsigned long UART_BAUD = 115200;
// Position telemetry interval to the S3.
constexpr unsigned long UART_TELEMETRY_MS = 50;
// Disable motor after this interval without S3 commands; S3 sends at 20 Hz.
constexpr unsigned long STALE_COMMAND_TIMEOUT_MS = 500;

// Enable exactly one controller and leave the other defines commented.
// #define CONTROL_MODE_P
// #define CONTROL_MODE_PI
#define CONTROL_MODE_PID

#if (defined(CONTROL_MODE_P) + defined(CONTROL_MODE_PI) + defined(CONTROL_MODE_PID)) != 1
#error "Enable exactly one mode: CONTROL_MODE_P, CONTROL_MODE_PI, or CONTROL_MODE_PID"
#endif

// Gains copied from the supplied practice PID; validate them on the motor.
constexpr float KP = 4.5122f;
constexpr float KI = 21.0020f;
constexpr float KD = 0.1834f;
constexpr float PPR = 11.0f;
constexpr float REDUCCION = 34.0f;
// Count A rising edges as in the original motor implementation. Second scale adjustment:
// indicated 180 degrees corresponded to about 90 physical degrees.
constexpr float CPR = PPR * REDUCCION * 4.0f;  // 1496 counts per revolution. Linear travel per output-shaft revolution is the pulley/pinion circumference; include any additional gearing between motor and pinion.
constexpr float MM_POR_VUELTA = 40.0f;
// Incremental encoder startup position is 0 mm: start at minimum travel. Reject setpoints
// outside this range.
constexpr float MIN_POSITION_MM = 0.0f;
constexpr float MAX_POSITION_MM = 180.0f;

constexpr float gradosDeMm(float mm) { return mm * 360.0f / MM_POR_VUELTA; }
constexpr float mmDeGrados(float grados) { return grados * MM_POR_VUELTA / 360.0f; }
