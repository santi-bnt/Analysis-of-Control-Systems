import 'dart:typed_data';

import 'package:flutter_test/flutter_test.dart';
import 'package:gato_cartesiano/ble/game_protocol.dart';
import 'package:gato_cartesiano/ble/pose_frame.dart';

const _g = BoardGeometry(
  xMin: 0,
  xMax: 180,
  yMin: 0,
  yMax: 180,
  x0: 30,
  xStep: 60,
  y0: 30,
  yStep: 60,
);

void main() {
  test('mirror reflects across the board and swaps hands', () {
    final d = ByteData(PoseFrame.length)
      ..setUint8(0, PoseFrame.header)
      ..setUint8(1, 1)
      ..setInt16(2, 40, Endian.little) // Head x coordinate.
      ..setInt16(4, -300, Endian.little) // Head y coordinate on the local side.
      ..setInt16(6, 450, Endian.little) // altura
      ..setInt8(8, 0)
      ..setInt8(9, 127) // Looking toward the board (+Y).
      ..setInt16(11, 10, Endian.little) // Left-hand x coordinate.
      ..setInt16(17, 70, Endian.little) // Right-hand x coordinate.
      ..setUint8(29, 1 | 0x10 | 4); // Only left hand present, closed, with ball.
    final m = PoseFrame.mirror(d.buffer.asUint8List(), _g)!;
    final r = ByteData.sublistView(m);
    expect(m[1], PoseFrame.mirrorId);
    expect(r.getInt16(2, Endian.little), 140); // 180 - 40
    expect(r.getInt16(4, Endian.little), 480); // 180 + 300
    expect(r.getInt16(6, Endian.little), 450);
    expect(r.getInt8(9), -127);
    expect(r.getInt16(11, Endian.little), 110); // Mirrored right hand.
    expect(r.getInt16(17, Endian.little), 170); // Mirrored left hand.
    expect(m[29], 2 | 0x20 | 4); // Now the right hand is closed.
  });

  test('ignores non-pose frames', () {
    expect(PoseFrame.mirror([1, 2, 3], _g), isNull);
  });
}
