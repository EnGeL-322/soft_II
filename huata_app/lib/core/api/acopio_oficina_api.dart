import '../models/acopio_oficina.dart';
import '../models/recojo.dart';
import 'api_client.dart';

/// Lo que usan las vistas de oficina de Acopio, con los mismos endpoints que la web. Los permisos
/// los vuelve a exigir el backend (panel.read, acopio.read, calidad.read, planta.recepcion...).
class AcopioOficinaApi {
  AcopioOficinaApi(this._client);
  final ApiClient _client;

  static String _dia(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<ResumenOperacion> resumen(DateTime dia) async {
    final r = await _client.dio.get('/operacion/${_dia(dia)}');
    return ResumenOperacion.fromJson(r.data as Map<String, dynamic>);
  }

  Future<TotalesDia> totales(DateTime dia) async {
    final r = await _client.dio.get('/operacion/${_dia(dia)}/totales');
    return TotalesDia.fromJson(r.data as Map<String, dynamic>);
  }

  Future<List<ConciliacionRuta>> conciliaciones(DateTime dia) async {
    final r = await _client.dio.get('/conciliaciones-ruta', queryParameters: {'fecha': _dia(dia)});
    return (r.data as List).map((e) => ConciliacionRuta.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Registra cuánto llegó a planta de una ruta (planta.recepcion). Quién recibe lo pone el backend.
  Future<ConciliacionRuta> registrarRecepcion({
    required DateTime dia,
    required int rutaId,
    int? acopiadorId,
    required double litrosRecibidos,
    String? observacion,
  }) async {
    final r = await _client.dio.post('/conciliaciones-ruta', data: {
      'fecha': _dia(dia),
      'rutaId': rutaId,
      'acopiadorId': ?acopiadorId,
      'litrosRecibidosPlanta': litrosRecibidos,
      if (observacion != null && observacion.trim().isNotEmpty) 'observacion': observacion.trim(),
    });
    return ConciliacionRuta.fromJson(r.data as Map<String, dynamic>);
  }

  /// Una página de recojos (la web usa el mismo filtro). [resultado]: ACEPTADA, OBSERVADA,
  /// RECHAZADA o PENDIENTE; [fuente]: RUTA o PLANTA.
  Future<({List<Recojo> filas, int total})> recojos({
    required DateTime desde,
    required DateTime hasta,
    String q = '',
    String? fuente,
    String? resultado,
    int pagina = 0,
    int tamano = 50,
  }) async {
    final r = await _client.dio.get('/recojos', queryParameters: {
      'page': pagina,
      'size': tamano,
      'desde': desde.toUtc().toIso8601String(),
      'hasta': hasta.toUtc().toIso8601String(),
      if (q.trim().isNotEmpty) 'q': q.trim(),
      'fuente': ?fuente,
      'resultado': ?resultado,
    });
    final datos = r.data as Map<String, dynamic>;
    return (
      filas: (datos['content'] as List).map((e) => Recojo.fromJson(e as Map<String, dynamic>)).toList(),
      total: (datos['totalElements'] as num?)?.toInt() ?? 0,
    );
  }

  Future<List<IncidenciaCalidad>> incidencias() async {
    final r = await _client.dio.get('/calidad/incidencias');
    return (r.data as List).map((e) => IncidenciaCalidad.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// ABIERTA → EN_SEGUIMIENTO → CERRADA (calidad.write).
  Future<void> cambiarEstadoIncidencia(int id, String estado) =>
      _client.dio.patch('/calidad/incidencias/$id/estado', data: {'estado': estado});

  Future<List<ControlCalidadResumen>> historialCalidad(int productorId) async {
    final r = await _client.dio.get('/calidad/historial/$productorId');
    return (r.data as List).map((e) => ControlCalidadResumen.fromJson(e as Map<String, dynamic>)).toList();
  }
}
