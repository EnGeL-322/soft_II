/// Espejos de lo que usa la web en Ventas y pagos (Pagos, Ventas, Contabilidad). Solo los campos que
/// muestran las pantallas de la app.
library;

double _d(Object? v) => (v as num?)?.toDouble() ?? 0;
int _i(Object? v) => (v as num?)?.toInt() ?? 0;
int? _in(Object? v) => (v as num?)?.toInt();
List<T> _lista<T>(Object? v, T Function(Map<String, dynamic>) de) =>
    (v as List? ?? const []).map((e) => de(e as Map<String, dynamic>)).toList();

/// Liquidación semanal: jueves a miércoles; se paga el viernes (CLAUDE.md §8). Mismo cálculo que la
/// web (`ciclos.ts`): retrocede hasta el jueves de la semana en curso.
class CicloPago {
  CicloPago(this.inicio) : fin = inicio.add(const Duration(days: 6));
  final DateTime inicio;
  final DateTime fin;

  static const _meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic'];

  String get etiqueta {
    String dia(DateTime d) => '${d.day.toString().padLeft(2, '0')} ${_meses[d.month - 1]}';
    return '${dia(inicio)} — ${dia(fin)} ${fin.year}';
  }

  static List<CicloPago> recientes([int cuantos = 26, DateTime? hoy]) {
    final h = hoy ?? DateTime.now();
    final base = DateTime(h.year, h.month, h.day);
    // weekday: lunes=1 … jueves=4 … domingo=7. Días desde el último jueves: (weekday + 3) % 7.
    final jueves = base.subtract(Duration(days: (base.weekday + 3) % 7));
    return [for (var i = 0; i < cuantos; i++) CicloPago(jueves.subtract(Duration(days: 7 * i)))];
  }
}

/// `GET /liquidaciones/planilla` (pagos.read): una fila por proveedor del ciclo.
class FilaPlanilla {
  FilaPlanilla({
    required this.proveedorId,
    required this.proveedorCodigo,
    required this.proveedorNombre,
    this.zona,
    required this.excelencia,
    required this.litrosEntregados,
    required this.litrosRechazados,
    required this.litrosPendientes,
    required this.tarifaAplicada,
    required this.importePendiente,
    required this.liquidado,
    this.liquidacionId,
    required this.tieneLiquidacionCerrada,
  });

  final int proveedorId;
  final String proveedorCodigo;
  final String proveedorNombre;
  final String? zona;
  final bool excelencia;
  final double litrosEntregados;
  final double litrosRechazados;
  final double litrosPendientes;
  final double tarifaAplicada;
  final double importePendiente;
  final bool liquidado;
  final int? liquidacionId;
  final bool tieneLiquidacionCerrada;

  factory FilaPlanilla.fromJson(Map<String, dynamic> j) => FilaPlanilla(
    proveedorId: _i(j['proveedorId']),
    proveedorCodigo: j['proveedorCodigo'] as String? ?? '',
    proveedorNombre: [j['proveedorNombre'], j['proveedorApellidos']].whereType<String>().join(' ').trim(),
    zona: j['zona'] as String?,
    excelencia: j['excelencia'] as bool? ?? false,
    litrosEntregados: _d(j['litrosEntregados']),
    litrosRechazados: _d(j['litrosRechazados']),
    litrosPendientes: _d(j['litrosPendientes']),
    tarifaAplicada: _d(j['tarifaAplicada']),
    importePendiente: _d(j['importePendiente']),
    liquidado: j['liquidado'] as bool? ?? false,
    liquidacionId: _in(j['liquidacionId']),
    tieneLiquidacionCerrada: j['tieneLiquidacionCerrada'] as bool? ?? false,
  );
}

/// Liquidación completa (`GET /liquidaciones/{id}`), con detalle y ajustes.
class LiquidacionCompleta {
  LiquidacionCompleta({
    required this.id,
    required this.productorId,
    required this.productorNombre,
    required this.periodoInicio,
    required this.periodoFin,
    required this.litrosTotales,
    required this.montoTotal,
    required this.ajustesTotal,
    required this.montoNeto,
    required this.estado,
    required this.detalle,
    required this.ajustes,
  });

  final int id;
  final int productorId;
  final String productorNombre;
  final String periodoInicio;
  final String periodoFin;
  final double litrosTotales;
  final double montoTotal;
  final double ajustesTotal;
  final double montoNeto;
  final String estado; // DRAFT | CALCULATED | REVIEWED | CLOSED
  final List<DetalleLiquidacion> detalle;
  final List<AjusteLiquidacion> ajustes;

  bool get pagada => estado == 'CLOSED';

  factory LiquidacionCompleta.fromJson(Map<String, dynamic> j) => LiquidacionCompleta(
    id: _i(j['id']),
    productorId: _i(j['productorId']),
    productorNombre: j['productorNombre'] as String? ?? '—',
    periodoInicio: j['periodoInicio'] as String? ?? '',
    periodoFin: j['periodoFin'] as String? ?? '',
    litrosTotales: _d(j['litrosTotales']),
    montoTotal: _d(j['montoTotal']),
    ajustesTotal: _d(j['ajustesTotal']),
    montoNeto: _d(j['montoNeto']),
    estado: j['estado'] as String? ?? '',
    detalle: _lista(j['detalle'], DetalleLiquidacion.fromJson),
    ajustes: _lista(j['ajustes'], AjusteLiquidacion.fromJson),
  );
}

class DetalleLiquidacion {
  DetalleLiquidacion({required this.fechaHora, required this.litros, required this.precioAplicado, required this.subtotal});
  final String fechaHora;
  final double litros;
  final double precioAplicado;
  final double subtotal;

  factory DetalleLiquidacion.fromJson(Map<String, dynamic> j) => DetalleLiquidacion(
    fechaHora: j['fechaHora'] as String? ?? '',
    litros: _d(j['litros']),
    precioAplicado: _d(j['precioAplicado']),
    subtotal: _d(j['subtotal']),
  );
}

/// Tipos de ajuste de la web (`TipoAjuste`). El signo lo pone el backend según el tipo.
const tiposDeAjuste = {
  'DESCUENTO_QUESO': 'Descuento por queso',
  'PENALIDAD': 'Penalidad',
  'ADELANTO': 'Adelanto',
  'BONIFICACION': 'Bonificación',
};

class AjusteLiquidacion {
  AjusteLiquidacion({required this.id, required this.tipo, required this.montoConSigno, this.motivo});
  final int id;
  final String tipo;
  final double montoConSigno;
  final String? motivo;

  factory AjusteLiquidacion.fromJson(Map<String, dynamic> j) => AjusteLiquidacion(
    id: _i(j['id']),
    tipo: j['tipo'] as String? ?? '',
    montoConSigno: _d(j['montoConSigno'] ?? j['monto']),
    motivo: j['motivo'] as String?,
  );
}

/// `GET /quesos/ventas` (quesos.read).
class Venta {
  Venta({
    required this.id,
    required this.loteCodigo,
    required this.productoNombre,
    required this.fecha,
    required this.cliente,
    required this.unidades,
    required this.tipoComprador,
    required this.precioUnitario,
    required this.total,
    required this.condicion,
    required this.medioCobro,
    required this.estado,
    required this.anulada,
    this.motivoAnulacion,
  });

  final int id;
  final String loteCodigo;
  final String productoNombre;
  final String fecha;
  final String cliente;
  final int unidades;
  final String tipoComprador;
  final double precioUnitario;
  final double total;
  final String condicion;
  final String medioCobro;
  final String estado; // COBRADO | POR_COBRAR
  final bool anulada;
  final String? motivoAnulacion;

  bool get porCobrar => !anulada && estado != 'COBRADO';

  factory Venta.fromJson(Map<String, dynamic> j) => Venta(
    id: _i(j['id']),
    loteCodigo: j['loteCodigo'] as String? ?? '',
    productoNombre: j['productoNombre'] as String? ?? '',
    fecha: j['fecha'] as String? ?? '',
    cliente: j['cliente'] as String? ?? '',
    unidades: _i(j['unidades']),
    tipoComprador: j['tipoComprador'] as String? ?? '',
    precioUnitario: _d(j['precioUnitario']),
    total: _d(j['total']),
    condicion: j['condicion'] as String? ?? '',
    medioCobro: j['medioCobro'] as String? ?? '',
    estado: j['estado'] as String? ?? '',
    anulada: j['anulada'] as bool? ?? false,
    motivoAnulacion: j['motivoAnulacion'] as String?,
  );
}

class ResumenVentas {
  ResumenVentas({required this.ventaAcumulada, required this.operaciones, required this.stockDisponible, required this.porCobrar});
  final double ventaAcumulada;
  final int operaciones;
  final int stockDisponible;
  final double porCobrar;

  factory ResumenVentas.fromJson(Map<String, dynamic> j) => ResumenVentas(
    ventaAcumulada: _d(j['ventaAcumulada']),
    operaciones: _i(j['operaciones']),
    stockDisponible: _i(j['stockDisponible']),
    porCobrar: _d(j['porCobrar']),
  );
}

/// Lote con stock para vender (`GET /quesos/lotes/con-stock`).
class LoteParaVenta {
  LoteParaVenta({
    required this.id,
    required this.codigo,
    required this.productoId,
    required this.productoNombre,
    required this.stock,
    this.unidadVenta,
    this.fechaVencimiento,
  });

  final int id;
  final String codigo;
  final int productoId;
  final String productoNombre;
  final int stock;
  final String? unidadVenta;
  final String? fechaVencimiento;

  factory LoteParaVenta.fromJson(Map<String, dynamic> j) => LoteParaVenta(
    id: _i(j['id']),
    codigo: j['codigo'] as String? ?? '',
    productoId: _i(j['productoId']),
    productoNombre: j['productoNombre'] as String? ?? '',
    stock: _i(j['stock']),
    unidadVenta: j['unidadVenta'] as String?,
    fechaVencimiento: j['fechaVencimiento'] as String?,
  );
}

/// Lista de precios por producto y período (`GET /quesos/tarifas`).
class TarifaProducto {
  TarifaProducto({
    required this.productoId,
    required this.productoNombre,
    required this.precioPublicoGeneral,
    required this.precioMayorista,
    required this.precioProveedor,
    required this.vigenteDesde,
    this.vigenteHasta,
    this.descripcion,
    required this.promocion,
  });

  final int productoId;
  final String productoNombre;
  final double precioPublicoGeneral;
  final double precioMayorista;
  final double precioProveedor;
  final String vigenteDesde;
  final String? vigenteHasta;
  final String? descripcion;
  final bool promocion;

  double precioPara(String tipoComprador) => switch (tipoComprador) {
    'MAYORISTA' => precioMayorista,
    'PROVEEDOR' => precioProveedor,
    _ => precioPublicoGeneral,
  };

  factory TarifaProducto.fromJson(Map<String, dynamic> j) => TarifaProducto(
    productoId: _i(j['productoId']),
    productoNombre: j['productoNombre'] as String? ?? '',
    precioPublicoGeneral: _d(j['precioPublicoGeneral']),
    precioMayorista: _d(j['precioMayorista']),
    precioProveedor: _d(j['precioProveedor']),
    vigenteDesde: j['vigenteDesde'] as String? ?? '',
    vigenteHasta: j['vigenteHasta'] as String?,
    descripcion: j['descripcion'] as String?,
    promocion: j['promocion'] as bool? ?? false,
  );
}

class ClienteVentas {
  ClienteVentas({
    required this.cliente,
    required this.compras,
    required this.unidades,
    required this.total,
    required this.porCobrar,
    required this.ultimaCompra,
    required this.productos,
  });

  final String cliente;
  final int compras;
  final int unidades;
  final double total;
  final double porCobrar;
  final String ultimaCompra;
  final List<String> productos;

  factory ClienteVentas.fromJson(Map<String, dynamic> j) => ClienteVentas(
    cliente: j['cliente'] as String? ?? '',
    compras: _i(j['compras']),
    unidades: _i(j['unidades']),
    total: _d(j['total']),
    porCobrar: _d(j['porCobrar']),
    ultimaCompra: j['ultimaCompra'] as String? ?? '',
    productos: (j['productos'] as List? ?? const []).cast<String>(),
  );
}

/// Contabilidad (contabilidad.read): libro diario y mayor.
class AsientoContable {
  AsientoContable({
    required this.id,
    required this.fecha,
    required this.glosa,
    required this.origenTipo,
    required this.lineas,
    required this.totalDebe,
    required this.totalHaber,
  });

  final int id;
  final String fecha;
  final String glosa;
  final String origenTipo;
  final List<LineaAsiento> lineas;
  final double totalDebe;
  final double totalHaber;

  factory AsientoContable.fromJson(Map<String, dynamic> j) => AsientoContable(
    id: _i(j['id']),
    fecha: j['fecha'] as String? ?? '',
    glosa: j['glosa'] as String? ?? '',
    origenTipo: j['origenTipo'] as String? ?? '',
    lineas: _lista(j['lineas'], LineaAsiento.fromJson),
    totalDebe: _d(j['totalDebe']),
    totalHaber: _d(j['totalHaber']),
  );
}

class LineaAsiento {
  LineaAsiento({required this.codigoCuenta, required this.nombreCuenta, required this.debe, required this.haber});
  final String codigoCuenta;
  final String nombreCuenta;
  final double debe;
  final double haber;

  factory LineaAsiento.fromJson(Map<String, dynamic> j) => LineaAsiento(
    codigoCuenta: j['codigoCuenta'] as String? ?? '',
    nombreCuenta: j['nombreCuenta'] as String? ?? '',
    debe: _d(j['debe']),
    haber: _d(j['haber']),
  );
}

class CuentaContable {
  CuentaContable({required this.codigo, required this.nombre, required this.tipo});
  final String codigo;
  final String nombre;
  final String tipo;

  factory CuentaContable.fromJson(Map<String, dynamic> j) => CuentaContable(
    codigo: j['codigo'] as String? ?? '',
    nombre: j['nombre'] as String? ?? '',
    tipo: j['tipo'] as String? ?? '',
  );
}

class LibroMayor {
  LibroMayor({required this.codigoCuenta, required this.nombreCuenta, required this.movimientos, required this.saldoFinal});
  final String codigoCuenta;
  final String nombreCuenta;
  final List<MovimientoMayor> movimientos;
  final double saldoFinal;

  factory LibroMayor.fromJson(Map<String, dynamic> j) => LibroMayor(
    codigoCuenta: j['codigoCuenta'] as String? ?? '',
    nombreCuenta: j['nombreCuenta'] as String? ?? '',
    movimientos: _lista(j['movimientos'], MovimientoMayor.fromJson),
    saldoFinal: _d(j['saldoFinal']),
  );
}

class MovimientoMayor {
  MovimientoMayor({required this.fecha, required this.glosa, required this.debe, required this.haber, required this.saldo});
  final String fecha;
  final String glosa;
  final double debe;
  final double haber;
  final double saldo;

  factory MovimientoMayor.fromJson(Map<String, dynamic> j) => MovimientoMayor(
    fecha: j['fecha'] as String? ?? '',
    glosa: j['glosa'] as String? ?? '',
    debe: _d(j['debe']),
    haber: _d(j['haber']),
    saldo: _d(j['saldo']),
  );
}
