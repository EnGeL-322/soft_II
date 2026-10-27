import '../models/liquidacion.dart';
import 'api_client.dart';

/// `GET /api/v1/liquidaciones/me` y `/me/estimado` (RF-80/RF-81): endpoints `/me` dedicados al
/// proveedor, no una reutilización de `pagos.read` — ese permiso no lo tiene el rol PROVEEDOR y no
/// debe tenerlo solo para ver lo propio (expondría la planilla completa a cualquier proveedor).
class LiquidacionesApi {
  LiquidacionesApi(this._client);
  final ApiClient _client;

  Future<List<Liquidacion>> misLiquidaciones({int size = 20}) async {
    final r = await _client.dio.get('/liquidaciones/me', queryParameters: {'page': 0, 'size': size});
    final content = (r.data as Map<String, dynamic>)['content'] as List;
    return content.map((e) => Liquidacion.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<CobroEstimado> miEstimado() async {
    final r = await _client.dio.get('/liquidaciones/me/estimado');
    return CobroEstimado.fromJson(r.data as Map<String, dynamic>);
  }

  Future<ResumenPagoPeriodo> miResumen({required DateTime desde, required DateTime hasta}) async {
    String fecha(DateTime d) =>
        '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
    final r = await _client.dio.get(
      '/liquidaciones/me/resumen',
      queryParameters: {'desde': fecha(desde), 'hasta': fecha(hasta)},
    );
    return ResumenPagoPeriodo.fromJson(r.data as Map<String, dynamic>);
  }
}
