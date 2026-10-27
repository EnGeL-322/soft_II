/// Espejos de lo que usa la web en Producción (Inventario, Compras, Recetas y productos). Solo los
/// campos que muestran las pantallas de la app.
library;

double _d(Object? v) => (v as num?)?.toDouble() ?? 0;
double? _dn(Object? v) => (v as num?)?.toDouble();
int _i(Object? v) => (v as num?)?.toInt() ?? 0;
int? _in(Object? v) => (v as num?)?.toInt();
List<T> _lista<T>(Object? v, T Function(Map<String, dynamic>) de) =>
    (v as List? ?? const []).map((e) => de(e as Map<String, dynamic>)).toList();

/// Unidades de la web (`UnidadMedida`).
const unidadesMedida = {'KG': 'kg', 'LITRO': 'L', 'UNIDAD': 'u', 'GRAMO': 'g', 'ML': 'mL'};

class MateriaPrima {
  MateriaPrima({
    required this.id,
    required this.nombre,
    required this.unidadMedida,
    this.costoUnitarioReferencial,
    required this.stock,
    required this.activo,
    required this.lecheAcopio,
  });

  final int id;
  final String nombre;
  final String unidadMedida;
  final double? costoUnitarioReferencial;
  final double stock;
  final bool activo;

  /// La leche del acopio: entra sola cuando planta la recibe; no se compra ni se edita.
  final bool lecheAcopio;

  String get unidad => unidadesMedida[unidadMedida] ?? unidadMedida;

  factory MateriaPrima.fromJson(Map<String, dynamic> j) => MateriaPrima(
    id: _i(j['id']),
    nombre: j['nombre'] as String? ?? '',
    unidadMedida: j['unidadMedida'] as String? ?? 'UNIDAD',
    costoUnitarioReferencial: _dn(j['costoUnitarioReferencial']),
    stock: _d(j['stock']),
    activo: j['activo'] as bool? ?? true,
    lecheAcopio: j['lecheAcopio'] as bool? ?? false,
  );
}

const tiposMovimiento = {
  'ENTRADA_COMPRA': 'Compra',
  'SALIDA_PRODUCCION_CONSUMO': 'Consumo en producción',
  'ENTRADA_PRODUCCION_TERMINADO': 'Producto terminado',
  'SALIDA_VENTA': 'Venta',
  'ENTRADA_ANULACION_VENTA': 'Anulación de venta',
  'AJUSTE_ENTRADA': 'Ajuste (entrada)',
  'AJUSTE_SALIDA': 'Ajuste (salida)',
  'ENTRADA_ACOPIO': 'Leche del acopio',
};

class MovimientoInventario {
  MovimientoInventario({
    required this.id,
    required this.tipo,
    this.materiaPrimaNombre,
    this.loteQuesoCodigo,
    required this.cantidad,
    required this.fecha,
    required this.usuario,
    this.observaciones,
  });

  final int id;
  final String tipo;
  final String? materiaPrimaNombre;
  final String? loteQuesoCodigo;
  final double cantidad;
  final String fecha;
  final String usuario;
  final String? observaciones;

  bool get esEntrada => tipo.startsWith('ENTRADA') || tipo == 'AJUSTE_ENTRADA';

  factory MovimientoInventario.fromJson(Map<String, dynamic> j) => MovimientoInventario(
    id: _i(j['id']),
    tipo: j['tipo'] as String? ?? '',
    materiaPrimaNombre: j['materiaPrimaNombre'] as String?,
    loteQuesoCodigo: j['loteQuesoCodigo'] as String?,
    cantidad: _d(j['cantidad']),
    fecha: j['fecha'] as String? ?? '',
    usuario: j['usuario'] as String? ?? '',
    observaciones: j['observaciones'] as String?,
  );
}

class ProveedorCompra {
  ProveedorCompra({required this.id, required this.codigo, required this.nombre, this.contacto, this.documento});
  final int id;
  final String codigo;
  final String nombre;
  final String? contacto;
  final String? documento;

  factory ProveedorCompra.fromJson(Map<String, dynamic> j) => ProveedorCompra(
    id: _i(j['id']),
    codigo: j['codigo'] as String? ?? '',
    nombre: j['nombre'] as String? ?? '',
    contacto: j['contacto'] as String?,
    documento: j['documento'] as String?,
  );
}

class Compra {
  Compra({
    required this.id,
    required this.proveedorNombre,
    required this.fecha,
    this.documento,
    this.observaciones,
    required this.items,
    required this.total,
    this.activo = true,
    this.motivoAnulacion,
  });

  final int id;
  final String proveedorNombre;
  final String fecha;
  final String? documento;
  final String? observaciones;
  final List<CompraItem> items;
  final double total;

  /// false si se anuló en la web (V59): se sigue listando, tachada y con su motivo.
  final bool activo;
  final String? motivoAnulacion;

  factory Compra.fromJson(Map<String, dynamic> j) => Compra(
    id: _i(j['id']),
    proveedorNombre: j['proveedorCompraNombre'] as String? ?? '',
    fecha: j['fecha'] as String? ?? '',
    documento: j['documento'] as String?,
    observaciones: j['observaciones'] as String?,
    items: _lista(j['items'], CompraItem.fromJson),
    total: _d(j['total']),
    activo: j['activo'] as bool? ?? true,
    motivoAnulacion: j['motivoAnulacion'] as String?,
  );
}

class CompraItem {
  CompraItem({required this.materiaPrimaNombre, required this.cantidad, required this.costoUnitario, required this.total});
  final String materiaPrimaNombre;
  final double cantidad;
  final double costoUnitario;
  final double total;

  factory CompraItem.fromJson(Map<String, dynamic> j) => CompraItem(
    materiaPrimaNombre: j['materiaPrimaNombre'] as String? ?? '',
    cantidad: _d(j['cantidad']),
    costoUnitario: _d(j['costoUnitario']),
    total: _d(j['total']),
  );
}

/// Producto del catálogo (`GET /productos`), con su estado.
class ProductoCatalogo {
  ProductoCatalogo({
    required this.id,
    required this.nombre,
    required this.unidadVenta,
    this.descripcion,
    required this.activo,
    this.vidaUtilDias,
  });

  final int id;
  final String nombre;
  final String unidadVenta;
  final String? descripcion;
  final bool activo;
  final int? vidaUtilDias;

  factory ProductoCatalogo.fromJson(Map<String, dynamic> j) => ProductoCatalogo(
    id: _i(j['id']),
    nombre: j['nombre'] as String? ?? '',
    unidadVenta: j['unidadVenta'] as String? ?? 'unidad',
    descripcion: j['descripcion'] as String?,
    activo: j['activo'] as bool? ?? true,
    vidaUtilDias: _in(j['vidaUtilDias']),
  );
}

/// Receta versionada (`GET /produccion/recetas`): insumos por unidad producida.
class RecetaCompleta {
  RecetaCompleta({
    required this.id,
    required this.productoId,
    required this.productoNombre,
    required this.unidadVenta,
    required this.vigenteDesde,
    this.vigenteHasta,
    this.rendimientoLitrosPorUnidad,
    this.observaciones,
    required this.items,
  });

  final int id;
  final int productoId;
  final String productoNombre;
  final String unidadVenta;
  final String vigenteDesde;
  final String? vigenteHasta;
  final double? rendimientoLitrosPorUnidad;
  final String? observaciones;
  final List<InsumoReceta> items;

  factory RecetaCompleta.fromJson(Map<String, dynamic> j) => RecetaCompleta(
    id: _i(j['id']),
    productoId: _i(j['productoId']),
    productoNombre: j['productoNombre'] as String? ?? '',
    unidadVenta: j['unidadVenta'] as String? ?? 'unidad',
    vigenteDesde: j['vigenteDesde'] as String? ?? '',
    vigenteHasta: j['vigenteHasta'] as String?,
    rendimientoLitrosPorUnidad: _dn(j['rendimientoLitrosPorUnidad']),
    observaciones: j['observaciones'] as String?,
    items: _lista(j['items'], InsumoReceta.fromJson),
  );
}

class InsumoReceta {
  InsumoReceta({required this.materiaPrimaId, required this.materiaPrimaNombre, required this.unidadMedida, required this.cantidadPorUnidad});
  final int materiaPrimaId;
  final String materiaPrimaNombre;
  final String unidadMedida;
  final double cantidadPorUnidad;

  factory InsumoReceta.fromJson(Map<String, dynamic> j) => InsumoReceta(
    materiaPrimaId: _i(j['materiaPrimaId']),
    materiaPrimaNombre: j['materiaPrimaNombre'] as String? ?? '',
    unidadMedida: j['unidadMedida'] as String? ?? '',
    cantidadPorUnidad: _d(j['cantidadPorUnidad']),
  );
}

/// Lote de producto terminado con su stock (`GET /quesos/lotes`).
class LoteTerminado {
  LoteTerminado({
    required this.codigo,
    required this.fecha,
    required this.productoNombre,
    required this.unidadesProducidas,
    required this.stock,
    this.unidadVenta,
    this.costoUnitario,
    this.fechaVencimiento,
  });

  final String codigo;
  final String fecha;
  final String productoNombre;
  final int unidadesProducidas;
  final int stock;
  final String? unidadVenta;
  final double? costoUnitario;
  final String? fechaVencimiento;

  factory LoteTerminado.fromJson(Map<String, dynamic> j) => LoteTerminado(
    codigo: j['codigo'] as String? ?? '',
    fecha: j['fecha'] as String? ?? '',
    productoNombre: j['productoNombre'] as String? ?? '',
    unidadesProducidas: _i(j['unidadesProducidas']),
    stock: _i(j['stock']),
    unidadVenta: j['unidadVenta'] as String?,
    costoUnitario: _dn(j['costoUnitario']),
    fechaVencimiento: j['fechaVencimiento'] as String?,
  );
}
