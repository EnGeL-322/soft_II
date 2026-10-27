import 'package:flutter/foundation.dart';
import 'package:flutter/widgets.dart';
import 'package:package_info_plus/package_info_plus.dart';
import 'package:url_launcher/url_launcher.dart';

import '../api/api_client.dart';

/// Versión del APK publicada en el servidor (`/public/app-movil/version`).
class VersionPublicada {
  const VersionPublicada({this.versionActual, this.versionMinima, this.urlDescarga, this.notas});

  final String? versionActual;
  final String? versionMinima;
  final String? urlDescarga;
  final String? notas;

  factory VersionPublicada.fromJson(Map<String, dynamic> j) => VersionPublicada(
    versionActual: j['versionActual'] as String?,
    versionMinima: j['versionMinima'] as String?,
    urlDescarga: j['urlDescarga'] as String?,
    notas: j['notas'] as String?,
  );
}

enum EstadoActualizacion { sinRevisar, alDia, disponible, obligatoria }

/// Compara versiones `1.2.3` (ignora el `+build` y lo que no sea número). Negativo si a < b.
int compararVersiones(String a, String b) {
  List<int> partes(String v) =>
      v.split('+').first.split('.').map((p) => int.tryParse(p.replaceAll(RegExp(r'[^0-9]'), '')) ?? 0).toList();
  final pa = partes(a);
  final pb = partes(b);
  for (var i = 0; i < 3; i++) {
    final x = i < pa.length ? pa[i] : 0;
    final y = i < pb.length ? pb[i] : 0;
    if (x != y) return x.compareTo(y);
  }
  return 0;
}

/// Qué hacer con la versión instalada frente a la publicada.
EstadoActualizacion evaluarActualizacion(String instalada, VersionPublicada publicada) {
  final minima = publicada.versionMinima;
  final actual = publicada.versionActual;
  final hayDescarga = publicada.urlDescarga != null;
  if (minima != null && hayDescarga && compararVersiones(instalada, minima) < 0) {
    return EstadoActualizacion.obligatoria;
  }
  if (actual != null && hayDescarga && compararVersiones(instalada, actual) < 0) {
    return EstadoActualizacion.disponible;
  }
  return EstadoActualizacion.alDia;
}

/// Actualizaciones de la app entregada como APK directo. Dos caminos, a propósito:
///  - Parches de código Dart: Shorebird los baja solo al abrir y los aplica al siguiente arranque.
///    No pasa por aquí ni requiere que la persona haga nada.
///  - Versión nueva del APK (cambió algo nativo): el servidor dice cuál es; aquí se compara y se
///    ofrece descargarla. Si la instalada es menor que la mínima, la app no deja trabajar.
///
/// Se revisa al abrir y cada vez que la app vuelve a primer plano. Sin señal no pasa nada: se queda
/// con lo último que supo y lo intenta de nuevo la próxima vez (no hay botón obligatorio de revisar).
class ActualizacionService extends ChangeNotifier with WidgetsBindingObserver {
  ActualizacionService(this._api, {Future<String> Function()? versionInstalada})
    : _leerVersion = versionInstalada ?? _versionDelPaquete;

  final ApiClient _api;
  final Future<String> Function() _leerVersion;

  String? versionInstalada;
  VersionPublicada? publicada;
  EstadoActualizacion estado = EstadoActualizacion.sinRevisar;
  bool revisando = false;

  static Future<String> _versionDelPaquete() async {
    final info = await PackageInfo.fromPlatform();
    return info.buildNumber.isEmpty ? info.version : '${info.version}+${info.buildNumber}';
  }

  void iniciar() {
    WidgetsBinding.instance.addObserver(this);
    revisar();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) revisar();
  }

  Future<void> revisar() async {
    if (revisando) return;
    revisando = true;
    notifyListeners();
    try {
      versionInstalada ??= await _leerVersion();
      // En el navegador siempre se sirve la última versión: no hay APK que actualizar.
      if (kIsWeb) {
        estado = EstadoActualizacion.alDia;
        return;
      }
      final r = await _api.dio.get<Map<String, dynamic>>('/public/app-movil/version');
      publicada = VersionPublicada.fromJson(r.data ?? const {});
      estado = evaluarActualizacion(versionInstalada!, publicada!);
    } catch (_) {
      // Sin conexión o servidor caído: se conserva el último estado conocido.
    } finally {
      revisando = false;
      notifyListeners();
    }
  }

  /// Abre la descarga del APK en el navegador del teléfono; Android pide confirmar la instalación.
  Future<bool> descargar() async {
    final url = publicada?.urlDescarga;
    if (url == null) return false;
    return launchUrl(Uri.parse(url), mode: LaunchMode.externalApplication);
  }
}
