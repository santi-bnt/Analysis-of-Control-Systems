/// Material 3 Expressive-inspired theme. The original Flutter 3.47.5 SDK notes that native
/// Android spring transitions are only approximated in Flutter. Uses
/// DynamicSchemeVariant.vibrant to preserve default pink and dynamic_color for wallpaper
/// palettes. Large asymmetric corners, stronger title weight/size contrast, and overshooting
/// motion curves approximate the expressive design language.
library;

import 'package:flutter/material.dart';

/// Corner radii intentionally vary more and are rounder than classic Material 3.
class ExpressiveShapes {
  ExpressiveShapes._();

  static const small = BorderRadius.all(Radius.circular(12));
  static const medium = BorderRadius.all(Radius.circular(20));
  static const large = BorderRadius.all(Radius.circular(28));
  static const extraLarge = BorderRadius.all(Radius.circular(36));

  /// Pill shape for primary action buttons.
  static const pill = BorderRadius.all(Radius.circular(100));

  /// Asymmetric corners emphasize expressive shapes.
  static const asymmetric = BorderRadius.only(
    topLeft: Radius.circular(36),
    topRight: Radius.circular(12),
    bottomLeft: Radius.circular(12),
    bottomRight: Radius.circular(36),
  );
}

/// Flutter approximates Expressive springs with overshooting motion curves.
class ExpressiveMotion {
  ExpressiveMotion._();

  static const spatial = Curves.easeOutBack;
  static const effects = Curves.easeOutCubic;

  static const fast = Duration(milliseconds: 180);
  static const normal = Duration(milliseconds: 350);
  static const slow = Duration(milliseconds: 550);
}

ThemeData buildExpressiveTheme({
  required Brightness brightness,
  ColorScheme? dynamicScheme,
}) {
  // Use the Android 12+ wallpaper palette when supplied; otherwise default pink. vibrant
  // preserves the seed hue, while expressive rotates it from pink toward blue.
  final scheme =
      dynamicScheme ??
      ColorScheme.fromSeed(
        seedColor: const Color(0xFFE91E63),
        brightness: brightness,
        dynamicSchemeVariant: DynamicSchemeVariant.vibrant,
      );

  final base = ThemeData(colorScheme: scheme, useMaterial3: true);

  return base.copyWith(
    scaffoldBackgroundColor: scheme.surface,

    // Greater typographic weight contrast than classic Material 3.
    textTheme: base.textTheme.copyWith(
      displayLarge: base.textTheme.displayLarge?.copyWith(
        fontWeight: FontWeight.w800,
        letterSpacing: -1.0,
      ),
      headlineLarge: base.textTheme.headlineLarge?.copyWith(
        fontWeight: FontWeight.w700,
        letterSpacing: -0.5,
      ),
      headlineMedium: base.textTheme.headlineMedium?.copyWith(
        fontWeight: FontWeight.w700,
      ),
      titleLarge: base.textTheme.titleLarge?.copyWith(
        fontWeight: FontWeight.w600,
      ),
      labelLarge: base.textTheme.labelLarge?.copyWith(
        fontWeight: FontWeight.w600,
        letterSpacing: 0.1,
      ),
    ),

    // Large pill buttons provide generous touch targets.
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        shape: const RoundedRectangleBorder(
          borderRadius: ExpressiveShapes.pill,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 28, vertical: 18),
        textStyle: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        shape: const RoundedRectangleBorder(
          borderRadius: ExpressiveShapes.pill,
        ),
        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
      ),
    ),

    cardTheme: CardThemeData(
      shape: const RoundedRectangleBorder(borderRadius: ExpressiveShapes.large),
      color: scheme.surfaceContainer,
      elevation: 0,
      margin: EdgeInsets.zero,
      clipBehavior: Clip.antiAlias,
    ),

    // Large asymmetric FAB is the app's most expressive element.
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      shape: const RoundedRectangleBorder(
        borderRadius: ExpressiveShapes.asymmetric,
      ),
      backgroundColor: scheme.primaryContainer,
      foregroundColor: scheme.onPrimaryContainer,
      elevation: 0,
      focusElevation: 0,
      hoverElevation: 0,
      extendedPadding: const EdgeInsets.symmetric(horizontal: 28),
    ),

    appBarTheme: AppBarTheme(
      backgroundColor: scheme.surface,
      surfaceTintColor: Colors.transparent,
      scrolledUnderElevation: 0,
      centerTitle: false,
      titleTextStyle: base.textTheme.headlineMedium?.copyWith(
        color: scheme.onSurface,
        fontWeight: FontWeight.w700,
      ),
    ),

    chipTheme: ChipThemeData(
      shape: const RoundedRectangleBorder(borderRadius: ExpressiveShapes.small),
      side: BorderSide.none,
      backgroundColor: scheme.surfaceContainerHigh,
    ),

    listTileTheme: const ListTileThemeData(
      shape: RoundedRectangleBorder(borderRadius: ExpressiveShapes.medium),
      contentPadding: EdgeInsets.symmetric(horizontal: 20, vertical: 8),
    ),

    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      shape: const RoundedRectangleBorder(
        borderRadius: ExpressiveShapes.medium,
      ),
      backgroundColor: scheme.inverseSurface,
      contentTextStyle: TextStyle(color: scheme.onInverseSurface),
    ),

    dialogTheme: const DialogThemeData(
      shape: RoundedRectangleBorder(borderRadius: ExpressiveShapes.extraLarge),
    ),

    progressIndicatorTheme: ProgressIndicatorThemeData(
      color: scheme.primary,
      linearTrackColor: scheme.surfaceContainerHighest,
      linearMinHeight: 8,
    ),
  );
}
