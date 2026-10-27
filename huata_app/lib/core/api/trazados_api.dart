import '../models/trazado.dart';
import 'api_client.dart';
import 'rutas_api.dart';

/// "Pintar ruta del día" del acopiador (V27) contra `/trazados-acopiador`.
///
/// Guardar exige `acopio.write` (el permiso de escritura de campo que ya tiene el ACOPIADOR) y no
/// `rutas.write`: administrar el catálogo de rutas sigue siendo cosa de la web.
class TrazadosApi {
  TrazadosApi(this._client);
  final ApiClient _client;

  Future<MiTrazado> mio({DateTime? fecha}) async {
    final r = await _client.dio.get(
      '/trazados-acopiador/me',
      queryParameters: {if (fecha != null) 'fecha': fecha.toIsoDate()},
    );
    return MiTrazado.fromJson(r.data as Map<String, dynamic>);
  }

  Future<TrazadoAcopiador> guardar(Map<String, dynamic> cuerpo) async {
    final r = await _client.dio.put('/trazados-acopiador/me', data: cuerpo);
    return TrazadoAcopiador.fromJson(r.data as Map<String, dynamic>);
  }

  Future<void> borrar({DateTime? fecha}) => _client.dio.delete(
    '/trazados-acopiador/me',
    queryParameters: {if (fecha != null) 'fecha': fecha.toIsoDate()},
  );

  /// El cuerpo que espera `PUT /trazados-acopiador/me`. Se arma aparte de [guardar] porque también
  /// se guarda tal cual en la cola offline: lo que se reintenta tiene que ser exactamente lo que se
  /// iba a enviar, no algo reconstruido después.
  static Map<String, dynamic> cuerpo({
    required DateTime fecha,
    required bool sigueRutaDesignada,
    required List<TrazadoPunto> puntos,
    String? observacion,
  }) => {
    'fecha': fecha.toIsoDate(),
    'sigueRutaDesignada': sigueRutaDesignada,
    if (observacion != null && observacion.trim().isNotEmpty) 'observacion': observacion.trim(),
    'puntos': puntos.map((p) => p.toJson()).toList(),
  };
}
