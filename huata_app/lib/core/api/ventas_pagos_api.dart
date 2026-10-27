import '../models/ventas_pagos.dart';
import 'api_client.dart';

/// Lo que usan las vistas Pagos, Ventas y Contabilidad, con los mismos endpoints que la web. Los
/// montos los calcula siempre el backend con la configuración vigente; aquí solo se muestran.
class VentasPagosApi {
  VentasPagosApi(this._client);
  final ApiClient _client;

  static String _dia(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  // ---------------------------------------------------------------- Pagos (pagos.read / pagos.write)

  Future<List<FilaPlanilla>> planilla(CicloPago ciclo) async {
    final r = await _client.dio.get('/liquidaciones/planilla', queryParameters: {
      'periodoInicio': _dia(ciclo.inicio),
      'periodoFin': _dia(ciclo.fin),
    });
    return (r.data as List).map((e) => FilaPlanilla.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<LiquidacionCompleta> liquidacion(int id) async {
    final r = await _client.dio.get('/liquidaciones/$id');
    return LiquidacionCompleta.fromJson(r.data as Map<String, dynamic>);
  }

  /// Crea (o recalcula) la liquidación del proveedor en el ciclo.
  Future<LiquidacionCompleta> calcular(int productorId, CicloPago ciclo) async {
    final r = await _client.dio.post('/liquidaciones/calcular', data: {
      'productorId': productorId,
      'periodoInicio': _dia(ciclo.inicio),
      'periodoFin': _dia(ciclo.fin),
    });
    return LiquidacionCompleta.fromJson(r.data as Map<String, dynamic>);
  }

  /// Registrar el pago es cerrar la liquidación (CLOSED), igual que "Pagar" en la web.
  Future<LiquidacionCompleta> pagar(int liquidacionId) async {
    final r = await _client.dio.patch('/liquidaciones/$liquidacionId/estado', data: {'estado': 'CLOSED'});
    return LiquidacionCompleta.fromJson(r.data as Map<String, dynamic>);
  }

  Future<LiquidacionCompleta> agregarAjuste(int liquidacionId, {required String tipo, required double monto, String? motivo}) async {
    final r = await _client.dio.post('/liquidaciones/$liquidacionId/ajustes', data: {
      'tipo': tipo,
      'monto': monto,
      if (motivo != null && motivo.trim().isNotEmpty) 'motivo': motivo.trim(),
    });
    return LiquidacionCompleta.fromJson(r.data as Map<String, dynamic>);
  }

  Future<LiquidacionCompleta> quitarAjuste(int liquidacionId, int ajusteId) async {
    final r = await _client.dio.delete('/liquidaciones/$liquidacionId/ajustes/$ajusteId');
    return LiquidacionCompleta.fromJson(r.data as Map<String, dynamic>);
  }

  /// Historial de pagos: liquidaciones cerradas, de la más reciente a la más antigua.
  Future<({List<LiquidacionCompleta> filas, int total})> pagadas({int pagina = 0, int tamano = 30}) async {
    final r = await _client.dio.get('/liquidaciones', queryParameters: {'estado': 'CLOSED', 'page': pagina, 'size': tamano});
    final datos = r.data as Map<String, dynamic>;
    return (
      filas: (datos['content'] as List).map((e) => LiquidacionCompleta.fromJson(e as Map<String, dynamic>)).toList(),
      total: (datos['totalElements'] as num?)?.toInt() ?? 0,
    );
  }

  // ---------------------------------------------------------------- Ventas (quesos.read / ventas.*)

  Future<List<Venta>> ventas() async {
    final r = await _client.dio.get('/quesos/ventas');
    return (r.data as List).map((e) => Venta.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<ResumenVentas> resumenVentas() async {
    final r = await _client.dio.get('/quesos/ventas/resumen');
    return ResumenVentas.fromJson(r.data as Map<String, dynamic>);
  }

  Future<List<LoteParaVenta>> lotesConStock() async {
    final r = await _client.dio.get('/quesos/lotes/con-stock');
    return (r.data as List).map((e) => LoteParaVenta.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<TarifaProducto>> tarifas() async {
    final r = await _client.dio.get('/quesos/tarifas');
    return (r.data as List).map((e) => TarifaProducto.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// La tarifa que rige hoy para el producto (para mostrar el precio; el backend lo vuelve a poner).
  Future<TarifaProducto?> tarifaVigente(int productoId) async {
    try {
      final r = await _client.dio.get('/quesos/tarifas/vigente', queryParameters: {'productoId': productoId});
      return r.data is Map<String, dynamic> ? TarifaProducto.fromJson(r.data as Map<String, dynamic>) : null;
    } catch (_) {
      return null;
    }
  }

  /// El precio no se envía: lo pone el backend con la tarifa vigente del tipo de comprador.
  Future<Venta> registrarVenta({
    required int loteId,
    required String cliente,
    required int unidades,
    required String tipoComprador,
    required String condicion,
    required String medioCobro,
  }) async {
    final r = await _client.dio.post('/quesos/ventas', data: {
      'loteId': loteId,
      'cliente': cliente.trim(),
      'unidades': unidades,
      'tipoComprador': tipoComprador,
      'condicion': condicion,
      'medioCobro': medioCobro,
    });
    return Venta.fromJson(r.data as Map<String, dynamic>);
  }

  /// Venta por producto: el servidor saca primero lo que vence antes, repartido entre lotes.
  Future<List<Venta>> venderPorProducto({
    required int productoId,
    required String cliente,
    required int unidades,
    required String tipoComprador,
    required String condicion,
    required String medioCobro,
  }) async {
    final r = await _client.dio.post('/quesos/ventas/por-producto', data: {
      'productoId': productoId,
      'cliente': cliente.trim(),
      'unidades': unidades,
      'tipoComprador': tipoComprador,
      'condicion': condicion,
      'medioCobro': medioCobro,
    });
    return (r.data as List).map((e) => Venta.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> marcarCobrada(int ventaId) => _client.dio.patch('/quesos/ventas/$ventaId/cobrada', data: {});

  Future<void> anularVenta(int ventaId, String motivo) =>
      _client.dio.post('/quesos/ventas/$ventaId/anular', data: {'motivo': motivo.trim()});

  Future<List<ClienteVentas>> clientes() async {
    final r = await _client.dio.get('/quesos/ventas/clientes');
    return (r.data as List).map((e) => ClienteVentas.fromJson(e as Map<String, dynamic>)).toList();
  }

  // ---------------------------------------------------------------- Contabilidad (contabilidad.read)

  Future<List<AsientoContable>> libroDiario({DateTime? desde, DateTime? hasta}) async {
    final r = await _client.dio.get('/contabilidad/libro-diario', queryParameters: {
      if (desde != null) 'desde': _dia(desde),
      if (hasta != null) 'hasta': _dia(hasta),
    });
    return (r.data as List).map((e) => AsientoContable.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<CuentaContable>> cuentasConMovimiento() async {
    final r = await _client.dio.get('/contabilidad/libro-mayor/cuentas');
    return (r.data as List).map((e) => CuentaContable.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<LibroMayor> libroMayor(String cuenta, {DateTime? desde, DateTime? hasta}) async {
    final r = await _client.dio.get('/contabilidad/libro-mayor', queryParameters: {
      'cuenta': cuenta,
      if (desde != null) 'desde': _dia(desde),
      if (hasta != null) 'hasta': _dia(hasta),
    });
    return LibroMayor.fromJson(r.data as Map<String, dynamic>);
  }
}
