import 'package:flutter/foundation.dart';

/// Misma API que consume la web (`huata-web/src/app/core/config/api-config.ts`): un solo backend
/// Spring Boot bajo `/api/v1`.
///
/// - Compilado para `flutter build web` y servido por el contenedor nginx propio (ver
///   `huata_app/Dockerfile`): usa origen relativo, igual que hace `huata-web/nginx.conf` con el
///   backend — así no hay que hornear una URL fija en el bundle.
/// - El APK (Android/iOS) apunta por defecto al backend público de pruebas (`_tunelPruebas`), para
///   poder probar en un teléfono físico sin depender de la red local. Para otro backend (emulador
///   `10.0.2.2`, PC en la misma red, servidor real) compila con:
///   `flutter build apk --dart-define=HUATA_API_BASE_URL=https://tu-servidor/api/v1`.
class ApiConfig {
  ApiConfig._();

  static const _override = String.fromEnvironment('HUATA_API_BASE_URL');

  /// Túnel Cloudflare temporal que expone este backend para probar el APK desde cualquier celular.
  /// Si el túnel cambia, actualiza esta constante (o compila con `--dart-define`, que tiene
  /// prioridad).
  static const _tunelPruebas = 'https://anyone-ash-bias-drop.trycloudflare.com/api/v1';

  static String get baseUrl {
    if (kIsWeb) return _override.isNotEmpty ? _override : '/api/v1';
    return _override.isNotEmpty ? _override : _tunelPruebas;
  }
}
