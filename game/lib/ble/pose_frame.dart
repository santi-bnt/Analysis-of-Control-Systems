import 'dart:typed_data';

import 'game_protocol.dart';

/// Headset pose for the remote avatar (head, hands, ball) on the pose characteristic.
/// GameActivity3D builds/renders it; app and ESP only relay it. 30 bytes, little-endian, board
/// x/y in machine mm and height h in calibrated table mm. [0] D0; [1] player (1 X, 2 O, FF
/// simulation mirror); [2..7] head x/y/h int16; [8..10] viewing direction int8 scaled by 127;
/// [11..16] left hand; [17..22] right hand; [23..28] ball; [29] flags: bit0 left hand, bit1
/// right hand, bit2 ball, bit3 lowered claw, bit4 left fist, bit5 right fist, bit6 claw mode.
class PoseFrame {
  PoseFrame._();

  static const header = 0xD0;
  static const length = 30;
  static const mirrorId = 0xFF;

  static const _points = [2, 11, 17, 23];

  /// Simulation has no remote headset: mirror the local pose across the board as a facing opponent.
  static Uint8List? mirror(List<int> frame, BoardGeometry g) {
    if (frame.length != length || frame[0] != header) return null;
    final out = Uint8List.fromList(frame);
    final d = ByteData.sublistView(out);
    final cx = g.left + g.right;
    final cy = g.bottom + g.top;
    for (final at in _points) {
      d.setInt16(
        at,
        (cx - d.getInt16(at, Endian.little)).round(),
        Endian.little,
      );
      d.setInt16(
        at + 2,
        (cy - d.getInt16(at + 2, Endian.little)).round(),
        Endian.little,
      );
    }
    d.setInt8(8, -d.getInt8(8).clamp(-127, 127));
    d.setInt8(9, -d.getInt8(9).clamp(-127, 127));
    // Swap hands: local left becomes mirrored right.
    final left = out.sublist(11, 17);
    out.setRange(11, 17, out.sublist(17, 23));
    out.setRange(17, 23, left);
    final f = out[29];
    out[29] = (f & ~0x33) | ((f & 0x11) << 1) | ((f & 0x22) >> 1);
    out[1] = mirrorId;
    return out;
  }
}
