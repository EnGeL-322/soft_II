/// Espejo de `RutaPuntoResponse` (geometría planificada, 2.1.3-2.1.5).
class RutaPunto {
  RutaPunto({required this.id, required this.secuencia, required this.latitud, required this.longitud});

  final int id;
  final int secuencia;
  final double latitud;
  final double longitud;

  factory RutaPunto.fromJson(Map<String, dynamic> j) => RutaPunto(
    id: j['id'] as int,
    secuencia: j['secuencia'] as int,
    latitud: (j['latitud'] as num).toDouble(),
    longitud: (j['longitud'] as num).toDouble(),
  );
}

/// Espejo de `ProveedorDeRutaResponse` (2.1.6): "a que proveedores voy hoy, en orden".
class ProveedorDeRuta {
  ProveedorDeRuta({
    required this.id,
    required this.codigo,
    required this.nombres,
    required this.apellidos,
    this.ordenEnRuta,
    this.latitud,
    this.longitud,
    this.estado,
  });

  final int id;
  final String codigo;
  final String nombres;
  final String apellidos;
  final int? ordenEnRuta;
  // Última ubicación conocida del proveedor (RF-59); el backend ya la manda (ProveedorDeRutaResponse)
  // pero esta app no la leía, así que el mapa de la ruta nunca podía dibujar al proveedor.
  final double? latitud;
  final double? longitud;

  /// ACTIVO, PENDIENTE_APROBACION, RETIRADO... Null si el backend es anterior a este campo.
  final String? estado;

  /// Solo un proveedor ACTIVO puede entregar leche (el backend rechaza el resto).
  bool get puedeEntregar => estado == null || estado == 'ACTIVO';

  String get nombreCompleto => '$nombres $apellidos';

  factory ProveedorDeRuta.fromJson(Map<String, dynamic> j) => ProveedorDeRuta(
    id: j['id'] as int,
    codigo: j['codigo'] as String,
    nombres: j['nombres'] as String,
    apellidos: j['apellidos'] as String,
    ordenEnRuta: j['ordenEnRuta'] as int?,
    latitud: (j['latitud'] as num?)?.toDouble(),
    longitud: (j['longitud'] as num?)?.toDouble(),
    estado: j['estado'] as String?,
  );
}

/// Espejo de `AsignacionRutaResponse`: qué ruta le toca a un acopiador en una fecha.
class AsignacionRuta {
  AsignacionRuta({
    required this.id,
    required this.rutaId,
    required this.rutaNombre,
    required this.acopiadorId,
    required this.acopiadorNombre,
    required this.fecha,
    this.zona,
    this.movilidad,
    this.observacion,
  });

  final int id;
  final int rutaId;
  final String rutaNombre;
  final int acopiadorId;
  final String acopiadorNombre;
  final String fecha;

  /// Zona de la ruta: delimita el polígono dentro del cual el acopiador debe permanecer para poder
  /// registrar leche (geocerca). Null si la ruta no tiene zona asignada.
  final String? zona;

  final String? movilidad;
  final String? observacion;

  factory AsignacionRuta.fromJson(Map<String, dynamic> j) => AsignacionRuta(
    id: j['id'] as int,
    rutaId: j['rutaId'] as int,
    rutaNombre: j['rutaNombre'] as String,
    acopiadorId: j['acopiadorId'] as int,
    acopiadorNombre: j['acopiadorNombre'] as String,
    fecha: j['fecha'] as String,
    zona: j['zona'] as String?,
    movilidad: j['movilidad'] as String?,
    observacion: j['observacion'] as String?,
  );
}

/// Espejo de `RutaResponse` (listado simple, para "ruta planificada o recorrido libre" del testeador).
class Ruta {
  Ruta({required this.id, required this.nombre});

  final int id;
  final String nombre;

  factory Ruta.fromJson(Map<String, dynamic> j) => Ruta(id: j['id'] as int, nombre: j['nombre'] as String);
}

/// Espejo de `ZonaPoligonoPuntoResponse`: un vértice ordenado del polígono de una zona.
class ZonaPoligonoPunto {
  ZonaPoligonoPunto({required this.secuencia, required this.latitud, required this.longitud});

  final int secuencia;
  final double latitud;
  final double longitud;

  factory ZonaPoligonoPunto.fromJson(Map<String, dynamic> j) => ZonaPoligonoPunto(
    secuencia: j['secuencia'] as int,
    latitud: (j['latitud'] as num).toDouble(),
    longitud: (j['longitud'] as num).toDouble(),
  );
}

/// Espejo de `PuntoEstablecidoResponse`: punto de referencia que administración crea a mano en el
/// mapa (pedido del cliente), con acopiador y proveedor asociados opcionales. Se lee por
/// `GET /puntos-establecidos/me` (mismo permiso `rutas.read` que el resto de esta pantalla) y se
/// dibuja junto al trazo de la ruta, igual que los proveedores ubicados.
///
/// `rutaId`, `proveedorId`, `proveedorEstado` y `proveedorOrdenEnRuta` son los que permiten **enlazar**
/// el punto con la lista de paradas del día: antes no se leían, así que el mapa pintaba el punto
/// suelto y sin número aunque su proveedor sí estuviera en la lista (ver `RutaDiaScreen`).
class PuntoEstablecido {
  PuntoEstablecido({
    required this.id,
    required this.nombre,
    required this.latitud,
    required this.longitud,
    this.rutaId,
    this.acopiadorId,
    this.proveedorNombre,
    this.activo = true,
    this.version = 0,
    this.actualizadoEn,
    this.rutaNombre,
    this.acopiadorNombre,
    this.proveedorId,
    this.proveedorCodigo,
    this.proveedorEstado,
    this.proveedorOrdenEnRuta,
    this.observacion,
  });

  final int id;
  final String nombre;
  final double latitud;
  final double longitud;
  final int? rutaId;
  final int? acopiadorId;
  final String? proveedorNombre;
  final bool activo;
  final int version;
  final DateTime? actualizadoEn;
  final String? rutaNombre;
  final String? acopiadorNombre;
  final int? proveedorId;
  final String? proveedorCodigo;

  /// ACTIVO, PENDIENTE_APROBACION, RETIRADO... Null si el punto no tiene proveedor o si el backend
  /// es anterior a este campo.
  final String? proveedorEstado;

  /// Número con el que se pinta el proveedor enlazado. Null si el punto no tiene proveedor.
  final int? proveedorOrdenEnRuta;

  final String? observacion;

  /// Un punto sin proveedor es solo una referencia del mapa. Con proveedor, es una parada que
  /// administración marcó a mano y que el acopiador tiene que ver aunque todavía no pueda entregar.
  bool get tieneProveedor => proveedorId != null;

  /// Solo un proveedor ACTIVO puede entregar leche (el backend rechaza el resto).
  bool get proveedorEntregable => proveedorEstado == null || proveedorEstado == 'ACTIVO';

  factory PuntoEstablecido.fromJson(Map<String, dynamic> j) => PuntoEstablecido(
    id: j['id'] as int,
    nombre: j['nombre'] as String,
    latitud: (j['latitud'] as num).toDouble(),
    longitud: (j['longitud'] as num).toDouble(),
    rutaId: j['rutaId'] as int?,
    acopiadorId: j['acopiadorId'] as int?,
    proveedorNombre: j['proveedorNombre'] as String?,
    activo: j['activo'] as bool? ?? true,
    version: (j['version'] as num?)?.toInt() ?? 0,
    actualizadoEn: DateTime.tryParse(j['actualizadoEn'] as String? ?? ''),
    rutaNombre: j['rutaNombre'] as String?,
    acopiadorNombre: j['acopiadorNombre'] as String?,
    proveedorId: j['proveedorId'] as int?,
    proveedorCodigo: j['proveedorCodigo'] as String?,
    proveedorEstado: j['proveedorEstado'] as String?,
    proveedorOrdenEnRuta: j['proveedorOrdenEnRuta'] as int?,
    observacion: j['observacion'] as String?,
  );
}

/// Espejo de `ZonaPoligonoResponse`: territorio real de una zona, para dibujarlo en el mapa igual
/// que en huata-web (mismo endpoint `GET /zonas/poligonos`, mismo permiso `rutas.read`).
class ZonaPoligono {
  ZonaPoligono({required this.id, required this.zona, this.color, required this.puntos});

  final int id;
  final String zona;
  final String? color;
  final List<ZonaPoligonoPunto> puntos;

  factory ZonaPoligono.fromJson(Map<String, dynamic> j) => ZonaPoligono(
    id: j['id'] as int,
    zona: j['zona'] as String,
    color: j['color'] as String?,
    puntos: (j['puntos'] as List).map((e) => ZonaPoligonoPunto.fromJson(e as Map<String, dynamic>)).toList(),
  );
}
