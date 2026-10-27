/// Espejo de `RecetaItemResponse`/`RecetaResponse` (backend `produccion.RecetaDtos`). Solo lectura
/// para el rol Productor de queso (RN: no administra la receta maestra ni sus costos).
class RecetaItem {
  RecetaItem({
    required this.materiaPrimaId,
    required this.materiaPrimaNombre,
    required this.unidadMedida,
    required this.cantidadPorUnidad,
  });

  final int materiaPrimaId;
  final String materiaPrimaNombre;
  final String unidadMedida;
  final double cantidadPorUnidad;

  factory RecetaItem.fromJson(Map<String, dynamic> j) => RecetaItem(
    materiaPrimaId: j['materiaPrimaId'] as int,
    materiaPrimaNombre: j['materiaPrimaNombre'] as String,
    unidadMedida: j['unidadMedida'] as String,
    cantidadPorUnidad: (j['cantidadPorUnidad'] as num).toDouble(),
  );
}

class Receta {
  Receta({required this.id, required this.productoId, required this.productoNombre, required this.items});

  final int id;
  final int productoId;
  final String productoNombre;
  final List<RecetaItem> items;

  factory Receta.fromJson(Map<String, dynamic> j) => Receta(
    id: j['id'] as int,
    productoId: j['productoId'] as int,
    productoNombre: j['productoNombre'] as String,
    items: (j['items'] as List).map((e) => RecetaItem.fromJson(e as Map<String, dynamic>)).toList(),
  );
}

/// Espejo de `ProductoResponse`: lo que la planta fabrica (queso, yogur...). El catálogo lo
/// administra la web; esta app solo lo lee para elegir qué se produce.
class Producto {
  Producto({required this.id, required this.nombre, required this.unidadVenta});

  final int id;
  final String nombre;
  final String unidadVenta;

  factory Producto.fromJson(Map<String, dynamic> j) => Producto(
    id: j['id'] as int,
    nombre: j['nombre'] as String,
    unidadVenta: j['unidadVenta'] as String? ?? 'unidad',
  );
}

/// Espejo de `SimulacionItemResponse`/`SimulacionResponse` (RF-66): cuánta materia prima hace falta
/// para la cantidad objetivo y si el stock actual alcanza.
class SimulacionItem {
  SimulacionItem({
    required this.materiaPrimaId,
    required this.materiaPrimaNombre,
    required this.unidadMedida,
    required this.cantidadRequerida,
    required this.stockActual,
    required this.alcanza,
  });

  final int materiaPrimaId;
  final String materiaPrimaNombre;
  final String unidadMedida;
  final double cantidadRequerida;
  final double stockActual;
  final bool alcanza;

  factory SimulacionItem.fromJson(Map<String, dynamic> j) => SimulacionItem(
    materiaPrimaId: j['materiaPrimaId'] as int,
    materiaPrimaNombre: j['materiaPrimaNombre'] as String,
    unidadMedida: j['unidadMedida'] as String,
    cantidadRequerida: (j['cantidadRequerida'] as num).toDouble(),
    stockActual: (j['stockActual'] as num).toDouble(),
    alcanza: j['alcanza'] as bool,
  );
}

class Simulacion {
  Simulacion({required this.recetaId, required this.cantidadObjetivo, required this.items, required this.alcanzaTodo});

  final int recetaId;
  final int cantidadObjetivo;
  final List<SimulacionItem> items;
  final bool alcanzaTodo;

  factory Simulacion.fromJson(Map<String, dynamic> j) => Simulacion(
    recetaId: j['recetaId'] as int,
    cantidadObjetivo: j['cantidadObjetivo'] as int,
    items: (j['items'] as List).map((e) => SimulacionItem.fromJson(e as Map<String, dynamic>)).toList(),
    alcanzaTodo: j['alcanzaTodo'] as bool,
  );
}

/// Espejo de `TrabajadorProduccionResponse`: personal activo que puede asignarse a un lote (mano de
/// obra, RF-62). No es inventario físico, es costo/recurso de producción.
class TrabajadorProduccion {
  TrabajadorProduccion({required this.id, required this.nombre});

  final int id;
  final String nombre;

  factory TrabajadorProduccion.fromJson(Map<String, dynamic> j) =>
      TrabajadorProduccion(id: j['id'] as int, nombre: j['nombre'] as String);
}

/// Una fila de mano de obra en el formulario de "registrar lote": qué trabajador y cuánto se le pagó.
class ManoObraItem {
  ManoObraItem({required this.trabajadorId, required this.monto});
  final int trabajadorId;
  final double monto;

  Map<String, dynamic> toJson() => {'trabajadorId': trabajadorId, 'monto': monto};
}

/// Un ítem de consumo real de materia prima (prellenado por la simulación, editable).
