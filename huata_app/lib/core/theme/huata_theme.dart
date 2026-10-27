import 'package:flutter/material.dart';

/// Misma paleta que `huata-web/src/tema-azul.css` (tema profesional 2026-09-16): slate neutro
/// + verde-azulado oscuro, sin el crema-pastel del prototipo original. Los nombres de acá reflejan
/// las variables CSS (`--bg`, `--panel`, `--green`, etc.) para que un cambio de marca se haga en un
/// solo lugar en cada plataforma.
class HuataColors {
  HuataColors._();

  // Tema azul (2026-10-27): azul institucional, blanco y celeste, igual que src/tema-azul.css de la
  // web. Los nombres green* se conservan porque toda la app los usa como "color principal".
  static const bg = Color(0xFFF3F7FC);
  static const panel = Color(0xFFFFFFFF);
  static const panel2 = Color(0xFFF6F9FD);
  static const text = Color(0xFF0F1F36);
  static const muted = Color(0xFF5A6B82);
  static const line = Color(0xFFDCE5F0);
  static const green = Color(0xFF1D4F91);
  static const green2 = Color(0xFF2F6FC0);
  static const greenSoft = Color(0xFFE8F0FA);
  static const blue = Color(0xFF0377B8);
  static const blueSoft = Color(0xFFE0F2FE);
  static const celeste = Color(0xFF0EA5E9);
  static const amber = Color(0xFFB45309);
  static const amberSoft = Color(0xFFFDF1E1);
  static const red = Color(0xFFB42318);
  static const redSoft = Color(0xFFFDECEA);
  static const purple = Color(0xFF3B5998);
  static const purpleSoft = Color(0xFFE9EEF8);
  static const gray = Color(0xFF45556B);
  static const graySoft = Color(0xFFEEF3F9);

  /// Verde SOLO con significado (apta, acopiado, cobrado): un estado no cambia de color porque
  /// cambie la marca.
  static const ok = Color(0xFF15803D);
  static const okSoft = Color(0xFFE6F6EC);

  /// Blanco/pendiente, verde/acopiado, rojo/no-entregado (RF-75): el mismo semáforo que pide el
  /// pliego para la ruta del acopiador, no un color de marca — se define aparte a propósito.
  static const estadoPendiente = Color(0xFFFFFFFF);
  static const estadoPendienteBorde = Color(0xFF94A3B8);
  static const estadoAcopiado = ok;
  static const estadoNoEntregado = red;
}

/// Cabecera de marca: azul marino que baja al azul institucional. La usan la barra superior y el
/// login, para que la app se reconozca como Huata desde la primera pantalla.
class HuataMarca {
  HuataMarca._();

  static const verdeProfundo = Color(0xFF0F2A4F);
  static const degradado = LinearGradient(
    begin: Alignment.topLeft,
    end: Alignment.bottomRight,
    colors: [verdeProfundo, HuataColors.green],
  );

  /// Sombra suave de las tarjetas: separa del fondo sin el borde gris "de formulario".
  static const sombra = [
    BoxShadow(color: Color(0x0F0F1F36), blurRadius: 12, offset: Offset(0, 4)),
    BoxShadow(color: Color(0x0A0F1F36), blurRadius: 2, offset: Offset(0, 1)),
  ];

  /// Ancho máximo del contenido: en tableta o navegador no se estira de borde a borde.
  static const anchoContenido = 760.0;
}

ThemeData buildHuataTheme() {
  final base = ThemeData(
    useMaterial3: true,
    colorScheme: ColorScheme.fromSeed(
      seedColor: HuataColors.green,
      primary: HuataColors.green,
      secondary: HuataColors.blue,
      error: HuataColors.red,
      surface: HuataColors.panel,
    ),
    scaffoldBackgroundColor: HuataColors.bg,
    fontFamily: 'Roboto',
    visualDensity: VisualDensity.standard,
  );
  final texto = base.textTheme
      .copyWith(
        headlineSmall: const TextStyle(fontSize: 24, fontWeight: FontWeight.w700, letterSpacing: -0.4, height: 1.2),
        titleLarge: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700, letterSpacing: -0.2),
        titleMedium: const TextStyle(fontSize: 16, fontWeight: FontWeight.w600, letterSpacing: -0.1),
        titleSmall: const TextStyle(fontSize: 14, fontWeight: FontWeight.w600),
        bodyLarge: const TextStyle(fontSize: 15, height: 1.4),
        bodyMedium: const TextStyle(fontSize: 14, height: 1.4),
        bodySmall: const TextStyle(fontSize: 12.5, height: 1.35, color: HuataColors.muted),
        labelLarge: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600, letterSpacing: 0.1),
        labelSmall: const TextStyle(fontSize: 11, fontWeight: FontWeight.w600, letterSpacing: 0.6),
      )
      .apply(bodyColor: HuataColors.text, displayColor: HuataColors.text);
  final borde = OutlineInputBorder(
    borderRadius: BorderRadius.circular(12),
    borderSide: const BorderSide(color: HuataColors.line),
  );
  return base.copyWith(
    textTheme: texto,
    appBarTheme: const AppBarTheme(
      backgroundColor: HuataColors.panel,
      foregroundColor: HuataColors.text,
      elevation: 0,
      scrolledUnderElevation: 0,
      surfaceTintColor: Colors.transparent,
      centerTitle: false,
      titleTextStyle: TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: HuataColors.text, letterSpacing: -0.2),
    ),
    cardTheme: CardThemeData(
      color: HuataColors.panel,
      elevation: 0,
      shadowColor: const Color(0x140F172A),
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: const BorderSide(color: Color(0xFFE8EDF3)),
      ),
      margin: EdgeInsets.zero,
    ),
    dividerTheme: const DividerThemeData(color: HuataColors.line, thickness: 1, space: 1),
    elevatedButtonTheme: ElevatedButtonThemeData(
      style: ElevatedButton.styleFrom(
        backgroundColor: HuataColors.green,
        foregroundColor: Colors.white,
        disabledBackgroundColor: HuataColors.line,
        elevation: 0,
        minimumSize: const Size(64, 48),
        padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 14),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    filledButtonTheme: FilledButtonThemeData(
      style: FilledButton.styleFrom(
        backgroundColor: HuataColors.green,
        minimumSize: const Size(64, 48),
        textStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    outlinedButtonTheme: OutlinedButtonThemeData(
      style: OutlinedButton.styleFrom(
        foregroundColor: HuataColors.text,
        backgroundColor: HuataColors.panel,
        side: const BorderSide(color: HuataColors.line),
        minimumSize: const Size(64, 46),
        padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 13),
        textStyle: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
    textButtonTheme: TextButtonThemeData(
      style: TextButton.styleFrom(
        foregroundColor: HuataColors.green,
        textStyle: const TextStyle(fontSize: 14.5, fontWeight: FontWeight.w600),
      ),
    ),
    segmentedButtonTheme: SegmentedButtonThemeData(
      style: ButtonStyle(
        backgroundColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? HuataColors.green : HuataColors.panel,
        ),
        foregroundColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? Colors.white : HuataColors.gray,
        ),
        iconColor: WidgetStateProperty.resolveWith(
          (s) => s.contains(WidgetState.selected) ? Colors.white : HuataColors.gray,
        ),
        side: const WidgetStatePropertyAll(BorderSide(color: HuataColors.line)),
        textStyle: const WidgetStatePropertyAll(TextStyle(fontSize: 13.5, fontWeight: FontWeight.w600)),
        shape: WidgetStatePropertyAll(RoundedRectangleBorder(borderRadius: BorderRadius.circular(10))),
      ),
    ),
    chipTheme: ChipThemeData(
      backgroundColor: HuataColors.panel,
      selectedColor: HuataColors.greenSoft,
      side: const BorderSide(color: HuataColors.line),
      labelStyle: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: HuataColors.text),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(999)),
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
    ),
    tabBarTheme: const TabBarThemeData(
      labelColor: HuataColors.green,
      unselectedLabelColor: HuataColors.muted,
      indicatorColor: HuataColors.green,
      indicatorSize: TabBarIndicatorSize.label,
      dividerColor: HuataColors.line,
      labelStyle: TextStyle(fontSize: 14, fontWeight: FontWeight.w700),
      unselectedLabelStyle: TextStyle(fontSize: 14, fontWeight: FontWeight.w500),
    ),
    listTileTheme: const ListTileThemeData(
      iconColor: HuataColors.gray,
      titleTextStyle: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: HuataColors.text),
      subtitleTextStyle: TextStyle(fontSize: 13, color: HuataColors.muted, height: 1.35),
      contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 2),
    ),
    floatingActionButtonTheme: FloatingActionButtonThemeData(
      backgroundColor: HuataColors.green,
      foregroundColor: Colors.white,
      elevation: 3,
      extendedTextStyle: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
    ),
    bottomSheetTheme: const BottomSheetThemeData(
      backgroundColor: HuataColors.panel,
      surfaceTintColor: Colors.transparent,
      showDragHandle: true,
      dragHandleColor: HuataColors.line,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.vertical(top: Radius.circular(22))),
    ),
    dialogTheme: DialogThemeData(
      backgroundColor: HuataColors.panel,
      surfaceTintColor: Colors.transparent,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(20)),
      titleTextStyle: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700, color: HuataColors.text),
    ),
    snackBarTheme: SnackBarThemeData(
      behavior: SnackBarBehavior.floating,
      backgroundColor: HuataColors.text,
      contentTextStyle: const TextStyle(fontSize: 14, color: Colors.white),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
    ),
    popupMenuTheme: PopupMenuThemeData(
      color: HuataColors.panel,
      surfaceTintColor: Colors.transparent,
      elevation: 6,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
    ),
    progressIndicatorTheme: const ProgressIndicatorThemeData(color: HuataColors.green),
    inputDecorationTheme: InputDecorationTheme(
      filled: true,
      fillColor: HuataColors.panel,
      border: borde,
      enabledBorder: borde,
      focusedBorder: borde.copyWith(borderSide: const BorderSide(color: HuataColors.green, width: 1.6)),
      errorBorder: borde.copyWith(borderSide: const BorderSide(color: HuataColors.red)),
      labelStyle: const TextStyle(color: HuataColors.muted),
      floatingLabelStyle: const TextStyle(color: HuataColors.green, fontWeight: FontWeight.w600),
      contentPadding: const EdgeInsets.symmetric(horizontal: 14, vertical: 14),
    ),
  );
}
