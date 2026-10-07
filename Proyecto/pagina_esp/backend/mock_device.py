"""
Mock ESP32 Hardware Simulator
Simulates physical DC motor dynamics, encoder counting, PID loop, and MQTT over TLS.
Allows testing the entire stack (FastAPI backend + Next.js dashboard) without physical hardware.
"""

import time
import json
import ssl
import sys
import os
import math
import paho.mqtt.client as mqtt
from dotenv import load_dotenv

load_dotenv()

# Default configurations (can be overridden by environment variables)
MQTT_HOST = os.getenv("MQTT_HOST", "broker.emqx.io")
MQTT_PORT = int(os.getenv("MQTT_PORT", 8883))
MQTT_USERNAME = os.getenv("MQTT_USERNAME", "")
MQTT_PASSWORD = os.getenv("MQTT_PASSWORD", "")
MQTT_USE_TLS = os.getenv("MQTT_USE_TLS", "true").lower() == "true"
DEVICE_ID = os.getenv("DEVICE_ID", "motor-01")

TOPIC_COMMAND = f"motor/{DEVICE_ID}/command"
TOPIC_TELEMETRY = f"motor/{DEVICE_ID}/telemetry"
TOPIC_STATUS = f"motor/{DEVICE_ID}/status"

# Motor & Control Parameters
CONTROL_PERIOD_SEC = 0.010    # 10 ms control loop
TELEMETRY_PERIOD_SEC = 0.100  # 100 ms telemetry loop

# Physical DC Motor Model parameters:
# d(omega)/dt = (K_pwm * pwm - omega) / tau
MOTOR_TAU = 0.22              # Mechanical time constant in seconds
MOTOR_MAX_RPM = 300.0         # Max unloaded speed at 100% PWM
MOTOR_GAIN = MOTOR_MAX_RPM / 100.0

# PID Parameters (matching firmware)
KP = 0.65
KI = 1.80
KD = 0.02

# Internal State
target_setpoint = 0.0
simulated_rpm = 0.0
integral_error = 0.0
last_error = 0.0
applied_pwm = 0.0
estop_active = False
last_command_time = 0.0


def on_connect(client, userdata, flags, rc, properties=None):
    rc_code = rc if isinstance(rc, int) else getattr(rc, "value", rc)
    if rc_code == 0:
        print(f"[MOCK ESP32] Connected to MQTT broker as '{DEVICE_ID}'")
        # Publish online status (retained)
        client.publish(TOPIC_STATUS, "online", qos=1, retain=True)
        # Subscribe to commands
        client.subscribe(TOPIC_COMMAND, qos=1)
        print(f"[MOCK ESP32] Subscribed to {TOPIC_COMMAND}")
    else:
        print(f"[MOCK ESP32] Connection failed: {rc}")


def on_message(client, userdata, msg):
    global target_setpoint, estop_active, last_command_time
    try:
        data = json.loads(msg.payload.decode("utf-8"))
        cmd_type = data.get("type")
        val = data.get("value")

        if cmd_type == "setpoint":
            new_sp = float(val)
            if math.isfinite(new_sp) and 0.0 <= new_sp <= 350.0:
                if estop_active and new_sp != 0.0:
                    return
                target_setpoint = new_sp
                if new_sp == 0.0:
                    estop_active = False
                last_command_time = time.monotonic()
                print(f"[MOCK ESP32] Updated setpoint: {target_setpoint:.1f} RPM")
            else:
                print(f"[MOCK ESP32] Rejected out-of-range setpoint: {new_sp}")
        elif cmd_type in ["estop", "stop"]:
            estop_active = True
            target_setpoint = 0.0
            last_command_time = time.monotonic()
            print("[MOCK ESP32] EMERGENCY STOP TRIGGERED")
    except Exception as e:
        print(f"[MOCK ESP32] Invalid command message: {e}")


def main():
    global simulated_rpm, integral_error, last_error, applied_pwm

    try:
        client = mqtt.Client(
            callback_api_version=mqtt.CallbackAPIVersion.VERSION2,
            client_id=f"esp32-sim-{DEVICE_ID}"
        )
    except AttributeError:
        client = mqtt.Client(client_id=f"esp32-sim-{DEVICE_ID}")

    client.on_connect = on_connect
    client.on_message = on_message

    if MQTT_USERNAME:
        client.username_pw_set(MQTT_USERNAME, MQTT_PASSWORD)

    if MQTT_USE_TLS:
        ssl_ctx = ssl.create_default_context()
        client.tls_set_context(ssl_ctx)

    # Set Last Will and Testament
    client.will_set(TOPIC_STATUS, "offline", qos=1, retain=True)

    print(f"[MOCK ESP32] Connecting to {MQTT_HOST}:{MQTT_PORT}...")
    client.connect(MQTT_HOST, MQTT_PORT, keepalive=60)
    client.loop_start()

    last_control_time = time.time()
    last_telemetry_time = time.time()

    print("[MOCK ESP32] Simulation running. Press Ctrl+C to terminate.")

    try:
        while True:
            current_time = time.time()
            dt_control = current_time - last_control_time

            # 1. Deterministic Control Loop (~10ms)
            if dt_control >= CONTROL_PERIOD_SEC:
                last_control_time = current_time

                if (estop_active or target_setpoint <= 0.0 or
                        time.monotonic() - last_command_time > 5.0 or not client.is_connected()):
                    target_setpoint = 0.0
                    applied_pwm = 0.0
                    integral_error = 0.0
                    last_error = 0.0
                else:
                    error = target_setpoint - simulated_rpm
                    p_term = KP * error
                    integral_error += KI * error * dt_control
                    # Anti-windup clamping
                    integral_error = max(0.0, min(100.0, integral_error))
                    d_term = KD * (error - last_error) / dt_control
                    last_error = error

                    applied_pwm = max(0.0, min(100.0, p_term + integral_error + d_term))

                # Simulate physical motor dynamics (first-order differential equation with noise)
                target_motor_speed = applied_pwm * MOTOR_GAIN
                simulated_rpm += (target_motor_speed - simulated_rpm) * (dt_control / MOTOR_TAU)
                if simulated_rpm < 0.2 and applied_pwm == 0.0:
                    simulated_rpm = 0.0

            # 2. Telemetry Publishing (~100ms)
            dt_telemetry = current_time - last_telemetry_time
            if dt_telemetry >= TELEMETRY_PERIOD_SEC:
                last_telemetry_time = current_time
                error = target_setpoint - simulated_rpm
                telemetry = {
                    "deviceId": DEVICE_ID,
                    "setpoint": round(target_setpoint, 1),
                    "rpm": round(simulated_rpm, 1),
                    "error": round(error, 1),
                    "pwm": round(applied_pwm, 1),
                    "timestamp": int(current_time * 1000)
                }
                client.publish(TOPIC_TELEMETRY, json.dumps(telemetry), qos=0)

            time.sleep(0.002)

    except KeyboardInterrupt:
        print("\n[MOCK ESP32] Shutting down simulation...")
        client.publish(TOPIC_STATUS, "offline", qos=1, retain=True)
        time.sleep(0.5)
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
