/// Espejo de `RecojoResponse` (backend `org.ramiro.huata.recojo.RecojoDtos`).
class Recojo {
  Recojo({
    required this.id,
    this.clientUuid,
    required this.productorId,
    required this.productorNombre,
    this.acopiadorId,
    this.acopiadorNombre,
    this.rutaId,
    this.rutaNombre,
    required this.fechaHora,
    required this.litros,
    this.temperatura,
    required this.estado,
    this.motivoCancelacion,
    this.fuente,
    this.numeroEnvases,
    this.estadoVisita,
    this.motivoNoEntrega,
    this.distanciaProveedorM,
    this.gpsFueraDeRango = false,
    this.observado = false,
    this.motivoObservacion,
    this.resultadoCalidad,
  });

  final int id;
  final String? clientUuid;
  final int productorId;
  final String productorNombre;
  final int? acopiadorId;
  final String? acopiadorNombre;
  final int? rutaId;
  final String? rutaNombre;
  final DateTime fechaHora;
  final double litros;
  final double? temperatura;
  final String estado;
  final String? motivoCancelacion;
  final String? fuente;
  final int? numeroEnvases;
  final String? estadoVisita; // ACOPIADO | NO_ENTREGADO | null (=> blanco/pendiente)
  final String? motivoNoEntrega;
  // Distancia (m) entre el GPS del acopiador y la ultima ubicacion conocida del proveedor al
  // registrar (pedido del cliente, 2026-09-24). Null si no se pudo comparar (falta alguno de los
  // dos puntos); gpsFueraDeRango solo es true si se superó RECOJO_DISTANCIA_MAXIMA_METROS.
  final double? distanciaProveedorM;
  final bool gpsFueraDeRango;
  // Corrección con justificación (pedido del cliente, 2026-09-24): un proveedor entrega leche una
  // sola vez al día, así que corregir un litros mal tecleado usa este mismo recojo en vez de crear
  // otro. observado queda en true apenas se corrige una vez; motivoObservacion trae la justificación.
  final bool observado;
  final String? motivoObservacion;
  final String? resultadoCalidad;

  factory Recojo.fromJson(Map<String, dynamic> j) => Recojo(
    id: j['id'] as int,
    clientUuid: j['clientUuid'] as String?,
    productorId: j['productorId'] as int,
    productorNombre: j['productorNombre'] as String,
    acopiadorId: j['acopiadorId'] as int?,
    acopiadorNombre: j['acopiadorNombre'] as String?,
    rutaId: j['rutaId'] as int?,
    rutaNombre: j['rutaNombre'] as String?,
    fechaHora: DateTime.parse(j['fechaHora'] as String),
    litros: (j['litros'] as num).toDouble(),
    temperatura: (j['temperatura'] as num?)?.toDouble(),
    estado: j['estado'] as String,
    motivoCancelacion: j['motivoCancelacion'] as String?,
    fuente: j['fuente'] as String?,
    numeroEnvases: j['numeroEnvases'] as int?,
    estadoVisita: j['estadoVisita'] as String?,
    motivoNoEntrega: j['motivoNoEntrega'] as String?,
    distanciaProveedorM: (j['distanciaProveedorM'] as num?)?.toDouble(),
    gpsFueraDeRango: j['gpsFueraDeRango'] as bool? ?? false,
    observado: j['observado'] as bool? ?? false,
    motivoObservacion: j['motivoObservacion'] as String?,
    resultadoCalidad: j['resultadoCalidad'] as String?,
  );
}

/// Lo que se envía a `PATCH /api/v1/recojos/{id}/corregir` (espejo de `RecojoCorreccionRequest`).
/// Corrige el recojo existente en vez de crear uno nuevo: un proveedor entrega leche una sola vez al
/// día (RecojoService.registrar), así que reintentar "registrar de nuevo" para arreglar un litros mal
/// tecleado siempre choca contra esa regla. El motivo es obligatorio -- no hay correccion silenciosa
/// de un numero que alimenta la liquidacion -- y el backend deja el recojo marcado `observado`.
class RecojoCorreccionRequest {
  RecojoCorreccionRequest({required this.litros, required this.motivo});
  final double litros;
  final String motivo;

  Map<String, dynamic> toJson() => {'litros': litros, 'motivo': motivo};
}

/// Lo que se envía a `POST /api/v1/recojos` (espejo de `RecojoRequest`). `clientUuid` es la clave de
/// idempotencia que ya soporta el backend (`Recojo.clientUuid`): si la cola offline reintenta este
/// mismo registro tras recuperar señal, el servidor no crea un duplicado.
class RecojoRequest {
  RecojoRequest({
    required this.clientUuid,
    required this.productorId,
    this.acopiadorId,
    this.rutaId,
    required this.fechaHora,
    required this.litros,
    this.fuente,
    this.estadoVisita,
    this.motivoNoEntrega,
    this.latitud,
    this.longitud,
  });

  final String clientUuid;
  final int productorId;
  final int? acopiadorId;
  final int? rutaId;
  final DateTime fechaHora;
  final double litros;
  final String? fuente;
  final String? estadoVisita;
  final String? motivoNoEntrega;
  // GPS del acopiador al momento de registrar (pedido del cliente, 2026-09-24): null cuando no se
  // pudo obtener (sin permiso, sin senal GPS). El backend no rechaza el recojo por esto, pero sin
  // estos dos datos tampoco puede calcular la distancia contra la ubicacion del proveedor.
  final double? latitud;
  final double? longitud;

  Map<String, dynamic> toJson() => {
    'clientUuid': clientUuid,
    'productorId': productorId,
    if (acopiadorId != null) 'acopiadorId': acopiadorId,
    if (rutaId != null) 'rutaId': rutaId,
    'fechaHora': fechaHora.toUtc().toIso8601String(),
    'litros': litros,
    if (fuente != null) 'fuente': fuente,
    if (estadoVisita != null) 'estadoVisita': estadoVisita,
    if (motivoNoEntrega != null) 'motivoNoEntrega': motivoNoEntrega,
    if (latitud != null) 'latitud': latitud,
    if (longitud != null) 'longitud': longitud,
  };
}

class ReporteAcopiadorDia {
  ReporteAcopiadorDia({required this.fecha, required this.litros, required this.recepciones, required this.proveedoresVisitados, required this.noEntregados});
  final String fecha;
  final double litros;
  final int recepciones;
  final int proveedoresVisitados;
  final int noEntregados;

  factory ReporteAcopiadorDia.fromJson(Map<String, dynamic> j) => ReporteAcopiadorDia(
    fecha: j['fecha'] as String,
    litros: (j['litros'] as num).toDouble(),
    recepciones: j['recepciones'] as int,
    proveedoresVisitados: j['proveedoresVisitados'] as int,
    noEntregados: j['noEntregados'] as int,
  );
}

class ReporteAcopiador {
  ReporteAcopiador({required this.desde, required this.hasta, required this.litros, required this.recepciones, required this.proveedoresVisitados, required this.noEntregados, required this.dias});
  final String desde;
  final String hasta;
  final double litros;
  final int recepciones;
  final int proveedoresVisitados;
  final int noEntregados;
  final List<ReporteAcopiadorDia> dias;

  factory ReporteAcopiador.fromJson(Map<String, dynamic> j) => ReporteAcopiador(
    desde: j['desde'] as String,
    hasta: j['hasta'] as String,
    litros: (j['litros'] as num).toDouble(),
    recepciones: j['recepciones'] as int,
    proveedoresVisitados: j['proveedoresVisitados'] as int,
    noEntregados: j['noEntregados'] as int,
    dias: ((j['dias'] as List?) ?? const []).map((e) => ReporteAcopiadorDia.fromJson(e as Map<String, dynamic>)).toList(),
  );
}
