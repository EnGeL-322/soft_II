/// Espejo de `ProductorResponse` (backend `org.ramiro.huata.productores.ProductorDtos`).
class Productor {
  Productor({
    required this.id,
    required this.codigo,
    required this.nombres,
    required this.apellidos,
    this.documentoIdentidad,
    this.telefono,
    this.rutaId,
    this.rutaNombre,
    required this.estado,
    this.notas,
    required this.excelencia,
    required this.activo,
    this.acopiadorResponsableId,
    this.acopiadorResponsableNombre,
    this.zonaPrincipal,
    this.ordenEnRuta,
  });

  final int id;
  final String codigo;
  final String nombres;
  final String apellidos;
  final String? documentoIdentidad;
  final String? telefono;
  final int? rutaId;
  final String? rutaNombre;
  final String estado;
  final String? notas;
  final bool excelencia;
  final bool activo;
  final int? acopiadorResponsableId;
  final String? acopiadorResponsableNombre;
  final String? zonaPrincipal;
  final int? ordenEnRuta;

  String get nombreCompleto => '$nombres $apellidos';

  factory Productor.fromJson(Map<String, dynamic> j) => Productor(
    id: j['id'] as int,
    codigo: j['codigo'] as String,
    nombres: j['nombres'] as String,
    apellidos: j['apellidos'] as String,
    documentoIdentidad: j['documentoIdentidad'] as String?,
    telefono: j['telefono'] as String?,
    rutaId: j['rutaId'] as int?,
    rutaNombre: j['rutaNombre'] as String?,
    estado: j['estado'] as String,
    notas: j['notas'] as String?,
    excelencia: j['excelencia'] as bool? ?? false,
    activo: j['activo'] as bool? ?? true,
    acopiadorResponsableId: j['acopiadorResponsableId'] as int?,
    acopiadorResponsableNombre: j['acopiadorResponsableNombre'] as String?,
    zonaPrincipal: j['zonaPrincipal'] as String?,
    ordenEnRuta: j['ordenEnRuta'] as int?,
  );
}

/// Espejo de `ProductorUbicacionResponse`/`Request` (2.6).
class ProductorUbicacion {
  ProductorUbicacion({
    required this.latitud,
    required this.longitud,
    this.precisionMetros,
    required this.tipo,
    this.fecha,
  });

  final double latitud;
  final double longitud;
  final double? precisionMetros;
  final String tipo; // CASA | ACTUAL
  final String? fecha;

  Map<String, dynamic> toJson() => {
    'latitud': latitud,
    'longitud': longitud,
    if (precisionMetros != null) 'precisionMetros': precisionMetros,
    'tipo': tipo,
  };
}

/// Espejo de `SolicitudCambioZonaResponse`.
class SolicitudCambioZona {
  SolicitudCambioZona({
    required this.id,
    required this.productorId,
    required this.productorNombre,
    this.zonaActual,
    required this.zonaSolicitada,
    this.motivo,
    required this.estado,
    this.fechaSolicitud,
    this.fechaDecision,
    this.aprobador,
    this.observacionDecision,
  });

  final int id;
  final int productorId;
  final String productorNombre;
  final String? zonaActual;
  final String zonaSolicitada;
  final String? motivo;
  final String estado; // PENDIENTE | APROBADA | RECHAZADA
  final String? fechaSolicitud;
  final String? fechaDecision;
  final String? aprobador;
  final String? observacionDecision;

  factory SolicitudCambioZona.fromJson(Map<String, dynamic> j) => SolicitudCambioZona(
    id: j['id'] as int,
    productorId: j['productorId'] as int,
    productorNombre: j['productorNombre'] as String,
    zonaActual: j['zonaActual'] as String?,
    zonaSolicitada: j['zonaSolicitada'] as String,
    motivo: j['motivo'] as String?,
    estado: j['estado'] as String,
    fechaSolicitud: j['fechaSolicitud'] as String?,
    fechaDecision: j['fechaDecision'] as String?,
    aprobador: j['aprobador'] as String?,
    observacionDecision: j['observacionDecision'] as String?,
  );
}
