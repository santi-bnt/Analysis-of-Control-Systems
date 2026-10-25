import 'package:dynamic_color/dynamic_color.dart';
import 'package:flutter/material.dart';

import 'platform.dart';
import 'theme/expressive_theme.dart';
import 'ui/game_page.dart';

void main() => runApp(const GatoApp());

class GatoApp extends StatelessWidget {
  const GatoApp({super.key});

  @override
  Widget build(BuildContext context) {
    // Quest always uses the default pink palette.
    if (isQuest) return _app(null, null);
    // Use Android 12+ wallpaper colors when available; otherwise pink.
    return DynamicColorBuilder(
      builder: (lightDynamic, darkDynamic) =>
          _app(lightDynamic?.harmonized(), darkDynamic?.harmonized()),
    );
  }

  Widget _app(ColorScheme? light, ColorScheme? dark) => MaterialApp(
    title: 'Tic-Cat-Toe',
    debugShowCheckedModeBanner: false,
    theme: buildExpressiveTheme(
      brightness: Brightness.light,
      dynamicScheme: light,
    ),
    darkTheme: buildExpressiveTheme(
      brightness: Brightness.dark,
      dynamicScheme: dark,
    ),
    home: const GamePage(),
  );
}
