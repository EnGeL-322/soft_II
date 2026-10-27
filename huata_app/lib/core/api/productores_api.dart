import '../models/productor.dart';
import 'api_client.dart';

class ProductoresApi {
  ProductoresApi(this._client);
  final ApiClient _client;

  Future<Productor> yo() async {
    final r = await _client.dio.get('/productores/me');
    return Productor.fromJson(r.data as Map<String, dynamic>);
  }

  Future<Productor> obtener(int id) async {
    final r = await _client.dio.get('/productores/$id');
    return Productor.fromJson(r.data as Map<String, dynamic>);
  }

  /// Para el testeador en recorrido libre (RF-86): elegir a qué proveedor testear sin depender de
  /// una ruta planificada. Exige `proveedores.read`, que el rol CALIDAD sí tiene.
  Future<List<Productor>> listar({String q = '', int size = 50}) async {
    final r = await _client.dio.get('/productores', queryParameters: {'q': q, 'page': 0, 'size': size});
    final content = (r.data as Map<String, dynamic>)['content'] as List;
    return content.map((e) => Productor.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Zonas que el proveedor puede pedir: las de rutas activas menos la suya. Es el mismo catálogo
  /// que usan la web al aprobar y el IVR, así que el nombre elegido siempre coincide con una ruta.
  Future<List<String>> zonasSolicitables(int productorId) async {
    final r = await _client.dio.get('/productores/$productorId/zonas-solicitables');
    return (r.data as List).cast<String>();
  }

  /// El proveedor crea su propia solicitud (2.5).
  Future<SolicitudCambioZona> crearSolicitudZona(int productorId, {required String zonaSolicitada, String? motivo}) async {
    final r = await _client.dio.post(
      '/productores/$productorId/solicitudes-zona',
      data: {'zonaSolicitada': zonaSolicitada, if (motivo != null && motivo.isNotEmpty) 'motivo': motivo},
    );
    return SolicitudCambioZona.fromJson(r.data as Map<String, dynamic>);
  }

  /// Historial de las propias solicitudes de zona del proveedor (RF-58/7.4), vía
  /// `GET /productores/me/solicitudes-zona`: solo exige estar autenticado, no `proveedores.read`.
  Future<List<SolicitudCambioZona>> misSolicitudesZona() async {
    final r = await _client.dio.get('/productores/me/solicitudes-zona');
    return (r.data as List).map((e) => SolicitudCambioZona.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> registrarUbicacion(int productorId, ProductorUbicacion ubicacion) {
    return _client.dio.post('/productores/$productorId/ubicaciones', data: ubicacion.toJson());
  }
}
