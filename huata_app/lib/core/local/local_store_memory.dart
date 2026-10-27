import 'local_store.dart';

/// Implementación web (sin persistencia real): alcanza para que la app compile y se navegue en el
/// build `flutter build web` dockerizado, sin arrastrar `sqflite` (que no soporta web) al bundle.
class LocalStoreImpl implements LocalStore {
  final Map<String, Map<String, PendienteSync>> _porTipo = {};

  @override
  Future<void> init() async {}

  @override
  Future<void> encolar(String tipo, String clientUuid, Map<String, dynamic> payload) async {
    (_porTipo[tipo] ??= {})[clientUuid] = PendienteSync(
      clientUuid: clientUuid,
      payload: payload,
      creadoEn: DateTime.now(),
    );
  }

  @override
  Future<List<PendienteSync>> pendientes(String tipo) async => (_porTipo[tipo] ?? {}).values.toList();

  @override
  Future<void> eliminar(String tipo, String clientUuid) async {
    _porTipo[tipo]?.remove(clientUuid);
  }

  @override
  Future<void> marcarError(String tipo, String clientUuid, String error) async {
    final actual = _porTipo[tipo]?[clientUuid];
    if (actual != null) {
      _porTipo[tipo]![clientUuid] = PendienteSync(
        clientUuid: actual.clientUuid,
        payload: actual.payload,
        creadoEn: actual.creadoEn,
        ultimoError: error,
      );
    }
  }

  @override
  Future<void> limpiarError(String tipo, String clientUuid) async {
    final actual = _porTipo[tipo]?[clientUuid];
    if (actual != null) {
      _porTipo[tipo]![clientUuid] = PendienteSync(
        clientUuid: actual.clientUuid,
        payload: actual.payload,
        creadoEn: actual.creadoEn,
      );
    }
  }
}
