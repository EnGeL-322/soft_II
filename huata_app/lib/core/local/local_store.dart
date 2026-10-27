/// Cola offline genérica: cada fila es una escritura pendiente (un recojo, un punto GPS) guardada
/// bajo su `clientUuid`, en JSON, hasta que el servidor la confirme (RF-17/18, RNF-07).
///
/// Puro Dart, sin imports de plataforma: la implementación real se elige en tiempo de compilación
/// (ver `local_store_factory.dart`) — SQLite de verdad en Android/iOS/desktop (`sqflite`), y una
/// cola en memoria en el build web, porque `sqflite` no tiene implementación para web y esta app
/// solo necesita compilar y navegarse ahí (login, listas, formularios), no persistir offline: un
/// tab de navegador no sustituye el almacenamiento local de un teléfono en el campo.
abstract class LocalStore {
  Future<void> init();

  Future<void> encolar(String tipo, String clientUuid, Map<String, dynamic> payload);

  Future<List<PendienteSync>> pendientes(String tipo);

  Future<void> eliminar(String tipo, String clientUuid);

  Future<void> marcarError(String tipo, String clientUuid, String error);

  /// Vuelve a dejar la fila como pendiente sin error (el usuario pidió reintentar a mano). Conserva
  /// `creadoEn`, así que no pierde su lugar en el orden de envío.
  Future<void> limpiarError(String tipo, String clientUuid);
}

class PendienteSync {
  PendienteSync({required this.clientUuid, required this.payload, required this.creadoEn, this.ultimoError});

  final String clientUuid;
  final Map<String, dynamic> payload;
  final DateTime creadoEn;
  final String? ultimoError;
}
