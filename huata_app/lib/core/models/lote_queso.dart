/// Espejo de `LoteQuesoResponse` (backend `org.ramiro.huata.quesos.QueseriaDtos`). Solo los campos
/// que muestra la vista de solo-lectura del Productor de queso (13, RF-88): no incluye precios de
/// venta ni receta maestra, que ese rol no administra.
class LoteQueso {
  LoteQueso({
    required this.id,
    required this.codigo,
    required this.fecha,
    required this.productoNombre,
    this.litrosDestinados,
    required this.unidadesProducidas,
    required this.stock,
    this.responsable,
    this.observaciones,
    this.merma,
    this.costoUnitario,
  });

  final int id;
  final String codigo;
  final String fecha;
  /// Qué se produjo (queso fresco, yogur...). Antes era un tipo fijo de queso.
  final String productoNombre;
  final double? litrosDestinados;
  final int unidadesProducidas;
  final int stock;
  final String? responsable;
  final String? observaciones;
  final int? merma;
  final double? costoUnitario;

  factory LoteQueso.fromJson(Map<String, dynamic> j) => LoteQueso(
    id: j['id'] as int,
    codigo: j['codigo'] as String,
    fecha: j['fecha'] as String,
    productoNombre: j['productoNombre'] as String? ?? '—',
    litrosDestinados: (j['litrosDestinados'] as num?)?.toDouble(),
    unidadesProducidas: j['unidadesProducidas'] as int? ?? 0,
    stock: j['stock'] as int? ?? 0,
    responsable: j['responsable'] as String?,
    observaciones: j['observaciones'] as String?,
    merma: j['merma'] as int?,
    costoUnitario: (j['costoUnitario'] as num?)?.toDouble(),
  );
}
