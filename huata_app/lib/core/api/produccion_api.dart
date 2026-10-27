import '../models/produccion.dart';
import 'api_client.dart';
import 'rutas_api.dart' show FechaIso;

/// Receta (solo lectura) y simulación de requerimientos (3.4/RF-65/RF-66), y el catálogo de
/// trabajadores de producción (RF-62) — mismos endpoints que usa `produccion-queso.component.ts`
/// en la web, para que el cálculo de costo no tenga una tercera implementación.
class ProduccionApi {
  ProduccionApi(this._client);
  final ApiClient _client;

  /// Productos activos del catálogo: lo que se puede producir hoy.
  Future<List<Producto>> productos() async {
    final r = await _client.dio.get('/productos');
    return (r.data as List).map((e) => Producto.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<Receta> recetaVigente(int productoId, DateTime fecha) async {
    final r = await _client.dio.get(
      '/produccion/recetas/vigente',
      queryParameters: {'productoId': productoId, 'fecha': fecha.toIsoDate()},
    );
    return Receta.fromJson(r.data as Map<String, dynamic>);
  }

  Future<Simulacion> simular(int recetaId, int cantidadObjetivo) async {
    final r = await _client.dio.post(
      '/produccion/recetas/$recetaId/simular',
      data: {'cantidadObjetivo': cantidadObjetivo},
    );
    return Simulacion.fromJson(r.data as Map<String, dynamic>);
  }

  Future<List<TrabajadorProduccion>> trabajadores() async {
    final r = await _client.dio.get('/produccion/trabajadores');
    return (r.data as List).map((e) => TrabajadorProduccion.fromJson(e as Map<String, dynamic>)).toList();
  }
}
