import 'dart:async';

import 'package:flutter/foundation.dart';
import 'package:flutter_blue_plus/flutter_blue_plus.dart';

import 'game_protocol.dart';
import 'game_sim.dart';
import 'pose_frame.dart';

enum LinkStatus { disconnected, connecting, connected }

/// S3 game connection: receives game state and sends head moves. Dragging generates more
/// positions than BLE can send. Keep only the latest pending move while a write is in flight,
/// then transmit that position.
class GameLink extends ChangeNotifier {
  LinkStatus _status = LinkStatus.disconnected;
  LinkStatus get status => _status;
  bool get isConnected => _status == LinkStatus.connected;

  /// Hardware-free simulation via startDemo; null with the real machine.
  GameSim? _sim;
  Timer? _simTimer;
  bool get isDemo => _sim != null;

  BluetoothDevice? _device;
  BluetoothCharacteristic? _commandChar;
  BluetoothCharacteristic? _poseChar;
  StreamSubscription<List<int>>? _poseSub;

  /// Newly received remote headset pose; see PoseFrame.
  void Function(Uint8List frame)? onPose;
  StreamSubscription<BluetoothConnectionState>? _connSub;
  StreamSubscription<List<int>>? _stateSub;

  String? _error;
  String? get error => _error;

  GameState? _state;
  GameState? get state => _state;

  bool _writing = false;
  ({Player player, double x, double y})? _pendingMove;
  bool _pendingNewGame = false;
  Player? _pendingFire;
  final _pendingCalib = <Uint8List>[];
  Uint8List? _pendingPose;

  // Reconnect after unexpected link loss, but not after explicit Disconnect.
  static const _maxRetries = 10;
  BluetoothDevice? _lost;
  int _retries = 0;
  Timer? _retry;

  String get deviceName {
    if (isDemo) return 'Simulation';
    final d = _device;
    if (d == null) return '';
    return d.platformName.isNotEmpty ? d.platformName : d.remoteId.str;
  }

  Future<void> connect(BluetoothDevice device) async {
    _lost = null;
    _retries = 0;
    await disconnect();
    await _connect(device);
  }

  Future<void> _connect(BluetoothDevice device) async {
    _retry?.cancel();
    await _teardown();
    _device = device;
    _error = null;
    _setStatus(LinkStatus.connecting);

    try {
      // Nonprofit license for personal/educational use.
      await device.connect(
        license: License.nonprofit,
        timeout: const Duration(seconds: 12),
      );

      _connSub = device.connectionState.listen((s) {
        if (s == BluetoothConnectionState.disconnected && _device == device) {
          _commandChar = null;
          _state = null;
          _error = 'Connection lost; reconnecting…';
          _lost = device;
          _retries = 0;
          _setStatus(LinkStatus.disconnected);
          _scheduleRetry();
        }
      });

      final services = await device.discoverServices();
      final service = services
          .where((s) => s.uuid == GameProtocol.serviceUuid)
          .firstOrNull;
      final command = service?.characteristics
          .where((c) => c.uuid == GameProtocol.commandCharUuid)
          .firstOrNull;
      final state = service?.characteristics
          .where((c) => c.uuid == GameProtocol.stateCharUuid)
          .firstOrNull;
      if (command == null || state == null) {
        throw StateError('The device does not expose the game service');
      }

      _commandChar = command;
      final pose = service?.characteristics
          .where((c) => c.uuid == GameProtocol.poseCharUuid)
          .firstOrNull;
      if (pose != null) {
        _poseChar = pose;
        _poseSub = pose.onValueReceived.listen(
          (bytes) => onPose?.call(Uint8List.fromList(bytes)),
        );
        await pose.setNotifyValue(true);
      }
      _stateSub = state.onValueReceived.listen((bytes) {
        final s = GameProtocol.decodeState(bytes);
        if (s == null) return;
        _state = s;
        notifyListeners();
      });
      await state.setNotifyValue(true);
      await state.read();
      _lost = null;
      _setStatus(LinkStatus.connected);
    } catch (e) {
      _error = _lost == null
          ? _describe(e)
          : 'Connection lost; reconnecting…';
      await _teardown();
      _setStatus(LinkStatus.disconnected);
      _scheduleRetry();
    }
  }

  /// Retry every three seconds up to _maxRetries while an unexpected disconnect remains pending.
  void _scheduleRetry() {
    final d = _lost;
    if (d == null) return;
    if (_retries >= _maxRetries) {
      _lost = null;
      _error = 'Could not reconnect to ${GameProtocol.deviceName}';
      notifyListeners();
      return;
    }
    _retries++;
    _retry?.cancel();
    _retry = Timer(const Duration(seconds: 3), () {
      if (_lost == d) _connect(d);
    });
  }

  /// Play against GameSim without BLE hardware.
  Future<void> startDemo() async {
    await disconnect();
    final sim = _sim = GameSim();
    _state = sim.state;
    // 100 ms interval matches ESP state notifications.
    _simTimer = Timer.periodic(const Duration(milliseconds: 100), (_) {
      sim.tick(100);
      _state = sim.state;
      notifyListeners();
    });
    _setStatus(LinkStatus.connected);
  }

  Future<void> disconnect() async {
    _lost = null;
    _retry?.cancel();
    _error = null;
    await _teardown();
    _setStatus(LinkStatus.disconnected);
  }

  Future<void> _teardown() async {
    _simTimer?.cancel();
    _simTimer = null;
    _sim = null;
    await _connSub?.cancel();
    _connSub = null;
    await _stateSub?.cancel();
    _stateSub = null;
    await _poseSub?.cancel();
    _poseSub = null;
    _commandChar = null;
    _poseChar = null;
    _pendingPose = null;
    _state = null;
    _pendingMove = null;
    _pendingNewGame = false;
    _pendingFire = null;
    _pendingCalib.clear();
    final d = _device;
    if (d != null && d.isConnected) {
      try {
        await d.disconnect();
      } catch (_) {}
    }
  }

  /// Request a head move. Replace the pending move instead of queueing while another write is in flight.
  void move(Player player, double xMm, double yMm) {
    if (!isConnected) return;
    if (_sim != null) return _sim!.move(player, xMm, yMm);
    _pendingMove = (player: player, x: xMm, y: yMm);
    if (!_writing) _drain();
  }

  /// Fire the ball/claw after the latest move has been transmitted.
  void fire(Player player) {
    if (!isConnected) return;
    if (_sim != null) return _sim!.fire(player);
    _pendingFire = player;
    if (!_writing) _drain();
  }

  /// Player's Calibrate/Cancel button.
  void calibrate(Player player) {
    if (!isConnected) return;
    if (_sim != null) return _sim!.calibrate(player);
    _pendingCalib.add(GameProtocol.encodeCalibrate(player));
    if (!_writing) _drain();
  }

  /// Player touched the head at calibration step.
  void touch(Player player, int step) {
    if (!isConnected) return;
    if (_sim != null) return _sim!.touch(player, step);
    _pendingCalib.add(GameProtocol.encodeTouch(player, step));
    if (!_writing) _drain();
  }

  /// Send this headset's pose, replacing any pending pose.
  void sendPose(Uint8List frame) {
    if (!isConnected) return;
    if (_sim != null) {
      final g = _state?.geometry;
      final echo = g == null ? null : PoseFrame.mirror(frame, g);
      if (echo != null) onPose?.call(echo);
      return;
    }
    if (_poseChar == null) return;
    _pendingPose = frame;
    if (!_writing) _drain();
  }

  void newGame() {
    if (!isConnected) return;
    if (_sim != null) return _sim!.newGame();
    _pendingNewGame = true;
    if (!_writing) _drain();
  }

  Future<void> _drain() async {
    _writing = true;
    try {
      while (_pendingNewGame ||
          _pendingMove != null ||
          _pendingFire != null ||
          _pendingCalib.isNotEmpty ||
          _pendingPose != null) {
        final char = _commandChar;
        if (char == null) break;
        final pose = _pendingPose;
        if (pose != null &&
            !_pendingNewGame &&
            _pendingMove == null &&
            _pendingFire == null &&
            _pendingCalib.isEmpty) {
          // Pose traffic has lower priority than game-changing commands.
          _pendingPose = null;
          await _poseChar?.write(pose, withoutResponse: true);
          continue;
        }
        final List<int> frame;
        if (_pendingNewGame) {
          _pendingNewGame = false;
          frame = GameProtocol.encodeNewGame();
        } else if (_pendingMove != null) {
          final m = _pendingMove!;
          _pendingMove = null;
          frame = GameProtocol.encodeMove(m.player, m.x, m.y);
        } else if (_pendingFire != null) {
          frame = GameProtocol.encodeFire(_pendingFire!);
          _pendingFire = null;
        } else {
          frame = _pendingCalib.removeAt(0);
        }
        await char.write(frame, withoutResponse: true);
      }
    } catch (e) {
      _error = _describe(e);
      notifyListeners();
    } finally {
      _writing = false;
    }
  }

  void _setStatus(LinkStatus s) {
    _status = s;
    notifyListeners();
  }

  static String _describe(Object e) {
    if (e is FlutterBluePlusException) return e.description ?? e.toString();
    if (e is StateError) return e.message;
    return e.toString();
  }

  @override
  void dispose() {
    _lost = null;
    _retry?.cancel();
    _teardown();
    super.dispose();
  }
}
