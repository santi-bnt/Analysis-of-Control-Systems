import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../ble/game_link.dart';
import '../ble/game_protocol.dart';
import '../panels/panel_bus.dart';
import '../platform.dart';
import '../theme/expressive_theme.dart';
import 'widgets/board_view.dart';
import 'widgets/device_sheet.dart';

/// Quest connection/player/status panel drives GameActivity3D over PanelBus: sends state and
/// receives head moves. Phone is a read-only 2D spectator.
class GamePage extends StatefulWidget {
  const GamePage({super.key});

  @override
  State<GamePage> createState() => _GamePageState();
}

/// 3D play mode; its index is sent in mode.
enum PlayMode {
  /// Slingshot launches a ball and moves the head toward the predicted landing point.
  slingshot('Slingshot', 'Pull back the slingshot ball and release it.'),

  /// Claw machine: joystick moves the head and the button drops the claw.
  claw('Claw machine', 'Move the head with the joystick and press the button.');

  const PlayMode(this.label, this.hint);
  final String label;
  final String hint;
}

class _GamePageState extends State<GamePage> {
  final _link = GameLink();

  /// Piece assigned to this headset/phone.
  Player _me = Player.x;
  PlayMode _mode = PlayMode.slingshot;

  // Session score. Null _lastPhase means no game has been observed yet, preventing an
  // already-finished game from counting on connection.
  final _wins = {Player.x: 0, Player.o: 0};
  int _draws = 0;
  Phase? _lastPhase;

  void _countResult() {
    final s = _link.state;
    if (s != null &&
        s.phase == Phase.over &&
        _lastPhase != null &&
        _lastPhase != Phase.over) {
      if (s.isDraw) {
        _draws++;
      } else {
        _wins[s.winnerPlayer] = (_wins[s.winnerPlayer] ?? 0) + 1;
      }
    }
    _lastPhase = s?.phase;
  }

  String get _scoreText =>
      'X ${_wins[Player.x]} · O ${_wins[Player.o]} · draws $_draws';

  void _savePrefs() =>
      PanelBus.savePrefs({'me': _me.code, 'mode': _mode.index});

  @override
  void initState() {
    super.initState();
    _link.addListener(_countResult);
    if (isQuest) {
      PanelBus.loadPrefs().then((p) {
        if (!mounted) return;
        final mode = p['mode'];
        setState(() {
          _me = p['me'] == Player.o.code ? Player.o : Player.x;
          if (mode is int && mode >= 0 && mode < PlayMode.values.length) {
            _mode = PlayMode.values[mode];
          }
        });
        _publishState();
      });
      PanelBus.listen(_onPanelMessage);
      _link.addListener(_publishState);
      _link.addListener(_open3dOnConnect);
      _link.onPose = (f) => PanelBus.send(PanelBus.poses, {'data': f});
    }
  }

  bool _wasConnected = false;

  /// Quest plays in 3D; connection or simulation opens it automatically.
  void _open3dOnConnect() {
    if (_link.isConnected && !_wasConnected) PanelBus.send(PanelBus.openGame3d);
    _wasConnected = _link.isConnected;
  }

  @override
  void dispose() {
    _link.dispose();
    super.dispose();
  }

  bool get _canMove {
    final s = _link.state;
    return isQuest &&
        _link.isConnected &&
        s != null &&
        s.phase == Phase.turn &&
        (s.turn == _me || _link.isDemo);
  }

  // One simulated device plays both pieces.
  Player get _player => _link.isDemo ? _link.state!.turn : _me;

  void _move(double x, double y) {
    if (_canMove) _link.move(_player, x, y);
  }

  void _fire() {
    if (_canMove) _link.fire(_player);
  }

  Future<void> _pickDevice() async {
    final device = await showDeviceSheet(context);
    if (device == null) return;
    await _link.connect(device);
  }

  // Quest 3D board.

  void _onPanelMessage(String method, Map args) {
    switch (method) {
      case PanelBus.hello:
        _publishState();
      case PanelBus.move:
        _move((args['x'] as num).toDouble(), (args['y'] as num).toDouble());
      case PanelBus.fire:
        _fire();
      case PanelBus.newGame:
        _link.newGame();
      case PanelBus.calibrate:
        _link.calibrate(_player);
      case PanelBus.touch:
        _link.touch(_player, (args['step'] as num).toInt());
      case PanelBus.pose:
        if (args['data'] case final Uint8List data) _link.sendPose(data);
    }
  }

  void _publishState() {
    if (!isQuest) return;
    PanelBus.send(PanelBus.game, {
      ...?_link.state?.toMap(),
      'connected': _link.isConnected,
      // Simulation lets the active player move in 3D.
      'me': (_link.isDemo ? _link.state?.turn ?? _me : _me).code,
      'mode': _mode.index,
      'status': _statusText(_link.state),
      'score': _scoreText,
    });
  }

  // --- Text ----------------------------------------------------------------

  String _who(Player p) => isQuest && p == _me ? '${p.symbol} (you)' : p.symbol;

  String _statusText(GameState? s) {
    if (!_link.isConnected || s == null) return 'Disconnected';
    return switch (s.phase) {
      Phase.waiting => 'Select New game to start',
      Phase.turn =>
        '${_who(s.turn)} to play · ${s.timeLeft.ceil()} s'
            '${s.turn == _me ? '' : ' — wait'}',
      Phase.resolving => 'Time is up: reading sensors…',
      Phase.over => s.isDraw ? 'Draw' : '${_who(s.winnerPlayer)} wins!',
    };
  }

  String? _lastShotText(GameState s) {
    final who = _who(s.lastPlayer);
    final cell = s.lastCell == null ? '' : ' (cell ${s.lastCell! + 1})';
    return switch (s.lastResult) {
      ShotResult.none => null,
      ShotResult.placed => '$who placed a piece$cell',
      ShotResult.missed => '$who missed every cell',
      ShotResult.occupied => '$who hit an occupied cell$cell',
    };
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      // Quest cards float over passthrough on a transparent background.
      backgroundColor: isQuest ? Colors.transparent : null,
      body: SafeArea(
        child: ListenableBuilder(
          listenable: _link,
          builder: (context, _) => Center(
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 640),
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  _Title(),
                  const SizedBox(height: 16),
                  _ConnectionCard(
                    link: _link,
                    onConnect: _pickDevice,
                    onDemo: isQuest ? _link.startDemo : null,
                    onDisconnect: _link.disconnect,
                  ),
                  if (isQuest) ...[
                    const SizedBox(height: 16),
                    _Section(
                      title: 'Your piece',
                      child: SegmentedButton<Player>(
                        segments: const [
                          ButtonSegment(
                            value: Player.x,
                            label: Text('Player X'),
                          ),
                          ButtonSegment(
                            value: Player.o,
                            label: Text('Player O'),
                          ),
                        ],
                        selected: {_me},
                        showSelectedIcon: false,
                        onSelectionChanged: (s) {
                          setState(() => _me = s.first);
                          _savePrefs();
                          _publishState();
                        },
                      ),
                    ),
                    const SizedBox(height: 16),
                    _Section(
                      title: 'Game mode',
                      child: SegmentedButton<PlayMode>(
                        segments: [
                          for (final m in PlayMode.values)
                            ButtonSegment(value: m, label: Text(m.label)),
                        ],
                        selected: {_mode},
                        showSelectedIcon: false,
                        onSelectionChanged: (s) {
                          setState(() => _mode = s.first);
                          _savePrefs();
                          _publishState();
                        },
                      ),
                    ),
                  ],
                  const SizedBox(height: 16),
                  ..._game(context),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _game(BuildContext context) {
    final s = _link.state;
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;
    if (!_link.isConnected || s == null) return const [];

    final last = _lastShotText(s);
    return [
      _Section(
        title: 'Game',
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text(_statusText(s), style: text.headlineSmall),
            const SizedBox(height: 4),
            Text(
              'Score: $_scoreText',
              style: text.titleMedium?.copyWith(color: scheme.primary),
            ),
            if (s.phase == Phase.turn) ...[
              const SizedBox(height: 8),
              LinearProgressIndicator(
                value: s.turnSeconds == 0 ? 0 : s.timeLeft / s.turnSeconds,
                borderRadius: ExpressiveShapes.pill,
              ),
            ],
            if (last != null) ...[
              const SizedBox(height: 8),
              Text(
                last,
                style: text.bodyLarge?.copyWith(color: scheme.onSurfaceVariant),
              ),
            ],
            if (!s.yAxisLinked) ...[
              const SizedBox(height: 8),
              Text(
                'The X-axis ESP32 is not responding over UART: the X axis is '
                'unavailable.',
                style: text.bodyMedium?.copyWith(color: scheme.error),
              ),
            ],
            if (!isQuest) ...[
              const SizedBox(height: 16),
              BoardView(state: s),
              const SizedBox(height: 8),
              Text(
                'Spectator only: play from the Quest headsets.',
                style: text.bodyMedium?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
            ],
            if (isQuest) ...[
              const SizedBox(height: 8),
              Text(
                'Play on the 3D board. ${_mode.hint}',
                style: text.bodyMedium?.copyWith(
                  color: scheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 16),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  FilledButton.icon(
                    onPressed: () {
                      HapticFeedback.mediumImpact();
                      _link.newGame();
                    },
                    icon: const Icon(Icons.restart_alt_rounded),
                    label: const Text('New game'),
                  ),
                  FilledButton.tonalIcon(
                    onPressed: () => PanelBus.send(PanelBus.openGame3d),
                    icon: const Icon(Icons.view_in_ar_rounded),
                    label: const Text('Open 3D board'),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    ];
  }
}

class _Title extends StatelessWidget {
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Align(
      alignment: Alignment.centerLeft,
      child: Material(
        color: scheme.primaryContainer,
        borderRadius: ExpressiveShapes.pill,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Image.asset(
                'assets/logo.png',
                height: 36,
                color: scheme.onPrimaryContainer,
                semanticLabel: 'Logo',
              ),
              const SizedBox(width: 12),
              Text(
                'Tic-Cat-Toe',
                style: Theme.of(context).textTheme.headlineSmall
                    ?.copyWith(color: scheme.onPrimaryContainer),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ConnectionCard extends StatelessWidget {
  const _ConnectionCard({
    required this.link,
    required this.onConnect,
    this.onDemo,
    required this.onDisconnect,
  });

  final GameLink link;
  final VoidCallback onConnect;
  final VoidCallback? onDemo;
  final VoidCallback onDisconnect;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final text = Theme.of(context).textTheme;

    final (bg, fg, icon, title) = switch (link.status) {
      LinkStatus.connected => (
        scheme.tertiaryContainer,
        scheme.onTertiaryContainer,
        Icons.bluetooth_connected_rounded,
        link.deviceName,
      ),
      LinkStatus.connecting => (
        scheme.secondaryContainer,
        scheme.onSecondaryContainer,
        Icons.bluetooth_searching_rounded,
        'Connecting…',
      ),
      LinkStatus.disconnected => (
        scheme.surfaceContainerHigh,
        scheme.onSurface,
        Icons.bluetooth_disabled_rounded,
        'Disconnected',
      ),
    };
    final subtitle = switch (link.status) {
      LinkStatus.connected =>
        link.isDemo
            ? 'Without ESP: you play both pieces'
            : 'Connected to the machine',
      LinkStatus.connecting => link.deviceName,
      LinkStatus.disconnected =>
        link.error ?? 'Find ${GameProtocol.deviceName} to start',
    };

    return AnimatedContainer(
      duration: ExpressiveMotion.normal,
      curve: ExpressiveMotion.effects,
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: bg,
        borderRadius: ExpressiveShapes.asymmetric,
      ),
      child: Row(
        children: [
          link.status == LinkStatus.connecting
              ? SizedBox.square(
                  dimension: 32,
                  child: CircularProgressIndicator(strokeWidth: 3, color: fg),
                )
              : Icon(icon, size: 32, color: fg),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(title, style: text.titleLarge?.copyWith(color: fg)),
                Text(
                  subtitle,
                  style: text.bodyMedium?.copyWith(
                    color:
                        link.status == LinkStatus.disconnected &&
                            link.error != null
                        ? scheme.error
                        : fg.withValues(alpha: 0.8),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(width: 8),
          switch (link.status) {
            LinkStatus.connected => OutlinedButton(
              onPressed: onDisconnect,
              child: const Text('Disconnect'),
            ),
            LinkStatus.connecting => const SizedBox.shrink(),
            LinkStatus.disconnected => Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                FilledButton(
                  onPressed: onConnect,
                  child: const Text('Connect'),
                ),
                if (onDemo != null) ...[
                  const SizedBox(height: 4),
                  TextButton(
                    onPressed: onDemo,
                    child: const Text('Try without ESP'),
                  ),
                ],
              ],
            ),
          },
        ],
      ),
    );
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.child});
  final String title;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Material(
      color: scheme.surfaceContainerLow,
      borderRadius: ExpressiveShapes.large,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 16, 20, 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              title,
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                color: scheme.primary,
                fontWeight: FontWeight.w700,
              ),
            ),
            const SizedBox(height: 12),
            child,
          ],
        ),
      ),
    );
  }
}
