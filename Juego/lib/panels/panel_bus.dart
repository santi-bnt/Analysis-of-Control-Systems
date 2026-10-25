import 'package:flutter/services.dart';

/// PanelBus messages connect separate Flutter 2D and native Kotlin 3D activities. Native
/// PanelBus relays between their separate memory spaces. 2D owns BLE; 3D requests actions.
/// 3D→2D: hello requests state; move carries x/y mm; fire launches/drops; newGame starts;
/// calibrate starts/cancels; touch carries step; pose carries local PoseFrame data. 2D→3D:
/// poses carries remote data; game carries GameState.toMap(). Native openGame3d/closeGame3d
/// open/close 3D; loadPrefs/savePrefs persist player and mode integers.
class PanelBus {
  PanelBus._();

  static const _channel = MethodChannel('control_angulos/panels');

  static const hello = 'hello';
  static const move = 'move';
  static const fire = 'fire';
  static const newGame = 'newGame';
  static const calibrate = 'calibrate';
  static const touch = 'touch';
  static const pose = 'pose';
  static const poses = 'poses';
  static const game = 'game';
  static const openGame3d = 'openGame3d';
  static const closeGame3d = 'closeGame3d';

  static void listen(void Function(String method, Map args) onMessage) {
    _channel.setMethodCallHandler((call) async {
      onMessage(call.method, (call.arguments as Map?) ?? const {});
    });
  }

  static Future<Map<String, Object?>> loadPrefs() async =>
      await _channel.invokeMapMethod<String, Object?>('loadPrefs') ?? {};

  static void savePrefs(Map<String, int> prefs) =>
      _channel.invokeMethod<void>('savePrefs', prefs).ignore();

  static void send(String method, [Map<String, Object?>? args]) {
    // Messages are simply lost if the other panel is closed.
    _channel.invokeMethod<void>(method, args).ignore();
  }
}
