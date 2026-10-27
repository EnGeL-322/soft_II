import 'api_client.dart';

/// GPS del acopiador en ruta (RF-13/RF-17, RN-17-19). `POST /api/v1/ubicaciones` solo existe para el
/// rol ACOPIADOR: el backend resuelve el `Acopiador` a partir del usuario autenticado
/// (`UbicacionGpsService.registrar`) y la tabla `ubicaciones_gps` tiene una FK NOT NULL a
/// `acopiador_id`, no a un actor genérico. El testeador (rol CALIDAD) NO tiene este endpoint
/// disponible — ver la nota en `features/testeador/recorrido_screen.dart` sobre por qué su
/// recorrido GPS solo se guarda localmente en esta versión.
class UbicacionesApi {
  UbicacionesApi(this._client);
  final ApiClient _client;

  /// Cuerpo de un punto. `fecha` es la hora de CAPTURA y viaja siempre: un punto que estuvo en la
  /// cola sin cobertura se envía minutos u horas después, y sin ella el recorrido quedaría dibujado
  /// con la hora de envío en vez de la del recorrido real.
  static Map<String, dynamic> punto({
    required double latitud,
    required double longitud,
    required DateTime capturadoEn,
    double? precisionMetros,
    double? velocidadMs,
  }) {
    return {
      'latitud': latitud,
      'longitud': longitud,
      'fecha': capturadoEn.toUtc().toIso8601String(),
      'precisionMetros': ?precisionMetros,
      'velocidadMs': ?velocidadMs,
    };
  }

  Future<void> reportar(Map<String, dynamic> punto) {
    return _client.dio.post('/ubicaciones', data: punto);
  }

  /// Envía de una vez los puntos que quedaron pendientes sin cobertura (RN-18: por lotes, para no
  /// hacer una petición por punto en cuanto vuelve la señal). El backend ignora los que ya tenía,
  /// así que reintentar un lote cortado a medias no duplica el recorrido.
  Future<void> reportarLote(List<Map<String, dynamic>> puntos) {
    return _client.dio.post('/ubicaciones/lote', data: {'puntos': puntos});
  }
}
