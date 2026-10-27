import '../models/produccion_oficina.dart';
import 'api_client.dart';

/// Lo que usan las vistas Inventario, Compras y Recetas y productos, con los mismos endpoints que la
/// web. Los costos y el stock los calcula el backend (kardex): la app no los recalcula.
class ProduccionOficinaApi {
  ProduccionOficinaApi(this._client);
  final ApiClient _client;

  static String _dia(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  List<T> _de<T>(Object? data, T Function(Map<String, dynamic>) de) =>
      (data as List).map((e) => de(e as Map<String, dynamic>)).toList();

  // ---------------------------------------------------------------- Inventario

  Future<List<MateriaPrima>> materiasPrimas() async =>
      _de((await _client.dio.get('/inventario/materias-primas')).data, MateriaPrima.fromJson);

  /// Crea (sin [id]) o actualiza una materia prima (inventario.write).
  Future<void> guardarMateriaPrima({int? id, required String nombre, required String unidadMedida, double? costo}) {
    final datos = {'nombre': nombre.trim(), 'unidadMedida': unidadMedida, 'costoUnitarioReferencial': ?costo};
    return id == null
        ? _client.dio.post('/inventario/materias-primas', data: datos)
        : _client.dio.put('/inventario/materias-primas/$id', data: datos);
  }

  Future<void> inactivarMateriaPrima(int id) => _client.dio.delete('/inventario/materias-primas/$id');

  /// Ajuste manual de stock (merma, conteo físico, leche descartada): exige motivo y queda en el kardex.
  Future<void> ajustarStock({required int materiaPrimaId, required bool salida, required double cantidad, required String motivo}) =>
      _client.dio.post('/inventario/ajustes', data: {
        'materiaPrimaId': materiaPrimaId,
        'tipo': salida ? 'SALIDA' : 'ENTRADA',
        'cantidad': cantidad,
        'motivo': motivo.trim(),
      });

  Future<({List<MovimientoInventario> filas, int total})> kardex({int? materiaPrimaId, int pagina = 0, int tamano = 40}) async {
    final r = await _client.dio.get('/inventario/kardex', queryParameters: {
      'page': pagina,
      'size': tamano,
      'materiaPrimaId': ?materiaPrimaId,
    });
    final datos = r.data as Map<String, dynamic>;
    return (filas: _de(datos['content'], MovimientoInventario.fromJson), total: (datos['totalElements'] as num?)?.toInt() ?? 0);
  }

  Future<List<LoteTerminado>> lotesTerminados() async => _de((await _client.dio.get('/quesos/lotes')).data, LoteTerminado.fromJson);

  // ---------------------------------------------------------------- Compras

  Future<List<ProveedorCompra>> proveedoresCompra() async =>
      _de((await _client.dio.get('/compras/proveedores')).data, ProveedorCompra.fromJson);

  Future<ProveedorCompra> crearProveedorCompra({required String nombre, String? contacto, String? documento}) async {
    final r = await _client.dio.post('/compras/proveedores', data: {
      'nombre': nombre.trim(),
      if (contacto != null && contacto.trim().isNotEmpty) 'contacto': contacto.trim(),
      if (documento != null && documento.trim().isNotEmpty) 'documento': documento.trim(),
    });
    return ProveedorCompra.fromJson(r.data as Map<String, dynamic>);
  }

  Future<({List<Compra> filas, int total})> compras({DateTime? desde, DateTime? hasta, int pagina = 0, int tamano = 30}) async {
    final r = await _client.dio.get('/compras', queryParameters: {
      'page': pagina,
      'size': tamano,
      if (desde != null) 'desde': _dia(desde),
      if (hasta != null) 'hasta': _dia(hasta),
    });
    final datos = r.data as Map<String, dynamic>;
    return (filas: _de(datos['content'], Compra.fromJson), total: (datos['totalElements'] as num?)?.toInt() ?? 0);
  }

  /// Registra la compra: entra al inventario (kardex) y genera su asiento contable en el backend.
  Future<Compra> registrarCompra({
    required int proveedorCompraId,
    required DateTime fecha,
    String? documento,
    String? observaciones,
    required List<({int materiaPrimaId, double cantidad, double costoUnitario})> items,
  }) async {
    final r = await _client.dio.post('/compras', data: {
      'proveedorCompraId': proveedorCompraId,
      'fecha': _dia(fecha),
      if (documento != null && documento.trim().isNotEmpty) 'documento': documento.trim(),
      if (observaciones != null && observaciones.trim().isNotEmpty) 'observaciones': observaciones.trim(),
      'items': [
        for (final i in items) {'materiaPrimaId': i.materiaPrimaId, 'cantidad': i.cantidad, 'costoUnitario': i.costoUnitario},
      ],
    });
    return Compra.fromJson(r.data as Map<String, dynamic>);
  }

  // ---------------------------------------------------------------- Recetas y productos

  Future<List<ProductoCatalogo>> productos({bool incluirInactivos = true}) async => _de(
    (await _client.dio.get('/productos', queryParameters: {'incluirInactivos': incluirInactivos})).data,
    ProductoCatalogo.fromJson,
  );

  Future<void> guardarProducto({int? id, required String nombre, required String unidadVenta, String? descripcion, int? vidaUtilDias}) {
    final datos = {
      'nombre': nombre.trim(),
      'unidadVenta': unidadVenta.trim(),
      if (descripcion != null && descripcion.trim().isNotEmpty) 'descripcion': descripcion.trim(),
      'vidaUtilDias': ?vidaUtilDias,
    };
    return id == null ? _client.dio.post('/productos', data: datos) : _client.dio.put('/productos/$id', data: datos);
  }

  Future<void> cambiarEstadoProducto(int id, {required bool activo}) =>
      _client.dio.patch('/productos/$id/${activo ? 'reactivar' : 'baja'}', data: {});

  Future<List<RecetaCompleta>> recetas() async => _de((await _client.dio.get('/produccion/recetas')).data, RecetaCompleta.fromJson);

  /// Nueva versión de la receta del producto (recetas.write): la anterior deja de regir.
  Future<void> crearReceta({
    required int productoId,
    required DateTime vigenteDesde,
    double? rendimientoLitrosPorUnidad,
    String? observaciones,
    required List<({int materiaPrimaId, double cantidadPorUnidad})> items,
  }) => _client.dio.post('/produccion/recetas', data: {
    'productoId': productoId,
    'vigenteDesde': _dia(vigenteDesde),
    'rendimientoLitrosPorUnidad': ?rendimientoLitrosPorUnidad,
    if (observaciones != null && observaciones.trim().isNotEmpty) 'observaciones': observaciones.trim(),
    'items': [for (final i in items) {'materiaPrimaId': i.materiaPrimaId, 'cantidadPorUnidad': i.cantidadPorUnidad}],
  });
}
