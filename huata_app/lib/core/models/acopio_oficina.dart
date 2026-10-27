/// Espejos de lo que usa la web en Acopio (Inicio, Recepción de rutas, Calidad). Solo los campos que
/// muestran las pantallas de la app.
library;

double _d(Object? v) => (v as num?)?.toDouble() ?? 0;
double? _dn(Object? v) => (v as num?)?.toDouble();
int _i(Object? v) => (v as num?)?.toInt() ?? 0;

/// `GET /operacion/{fecha}` (panel.read). Ojo, como en el backend (`OperacionService.ResumenDia`):
/// `litrosAceptados` INCLUYE la leche observada; los conteos van aparte.
class ResumenOperacion {
  ResumenOperacion({
    required this.litrosAceptados,
    required this.litrosObservados,
    required this.litrosRechazados,
    required this.entregasAceptadas,
    required this.entregasObservadas,
    required this.entregasRechazadas,
    required this.proveedores,
    required this.pendientesCalidad,
    required this.liquidacionesPendientes,
    this.compraEstimada,
    required this.rutas,
    this.cierre,
  });

  final double litrosAceptados;
  final double litrosObservados;
  final double litrosRechazados;
  final int entregasAceptadas;
  final int entregasObservadas;
  final int entregasRechazadas;
  final int proveedores;

  /// Entregas del día que todavía no tienen análisis de calidad.
  final int pendientesCalidad;
  final int liquidacionesPendientes;

  /// Lo que costaría la leche aceptada del día a la tarifa vigente (lo calcula el backend).
  final double? compraEstimada;
  final List<LitrosDeRuta> rutas;
  final CierreAcopio? cierre;

  double get litrosTotales => rutas.fold(0, (s, r) => s + r.litros);
  int get entregasBuenas => entregasAceptadas - entregasObservadas;
  double get litrosBuenos => litrosAceptados - litrosObservados;

  factory ResumenOperacion.fromJson(Map<String, dynamic> j) => ResumenOperacion(
    litrosAceptados: _d(j['aceptados']),
    litrosObservados: _d(j['observados']),
    litrosRechazados: _d(j['rechazados']),
    entregasAceptadas: _i(j['recepcionesAceptadas']),
    entregasObservadas: _i(j['controlesObservados']),
    entregasRechazadas: _i(j['entregasRechazadas']),
    proveedores: _i(j['proveedores']),
    pendientesCalidad: _i(j['pendientesCalidad']),
    liquidacionesPendientes: _i(j['liquidacionesPendientes']),
    compraEstimada: _dn(j['compraEstimada']),
    rutas: (j['rutas'] as List? ?? const [])
        .map((e) => LitrosDeRuta.fromJson(e as Map<String, dynamic>))
        .toList(),
    cierre: j['cierre'] is Map<String, dynamic> ? CierreAcopio.fromJson(j['cierre'] as Map<String, dynamic>) : null,
  );
}

class LitrosDeRuta {
  LitrosDeRuta({required this.nombre, required this.litros, required this.recepciones});
  final String nombre;
  final double litros;
  final int recepciones;

  factory LitrosDeRuta.fromJson(Map<String, dynamic> j) =>
      LitrosDeRuta(nombre: j['nombre'] as String? ?? '—', litros: _d(j['litros']), recepciones: _i(j['recepciones']));
}

class CierreAcopio {
  CierreAcopio({required this.cerrado, required this.litrosSistema, required this.litrosPlanta, this.observacion});
  final bool cerrado;
  final double litrosSistema;
  final double litrosPlanta;
  final String? observacion;

  factory CierreAcopio.fromJson(Map<String, dynamic> j) => CierreAcopio(
    cerrado: j['cerrado'] as bool? ?? false,
    litrosSistema: _d(j['litrosSistema']),
    litrosPlanta: _d(j['litrosPlanta']),
    observacion: j['observacion'] as String?,
  );
}

/// `GET /operacion/{fecha}/totales`: recogido vs recibido en planta.
class TotalesDia {
  TotalesDia({required this.recogido, required this.recibidoPlanta, required this.rutasSinRecibir});
  final double recogido;
  final double recibidoPlanta;
  final List<String> rutasSinRecibir;

  factory TotalesDia.fromJson(Map<String, dynamic> j) => TotalesDia(
    recogido: _d(j['recogido']),
    recibidoPlanta: _d(j['recibidoPlanta']),
    rutasSinRecibir: (j['rutasSinRecibir'] as List? ?? const []).cast<String>(),
  );
}

/// `GET /conciliaciones-ruta?fecha=` (acopio.read): lo recogido por ruta frente a lo recibido.
class ConciliacionRuta {
  ConciliacionRuta({
    required this.rutaId,
    required this.rutaNombre,
    this.zona,
    this.acopiadorId,
    this.acopiadorNombre,
    required this.litrosEnRuta,
    required this.litrosEntregadosEnPlanta,
    required this.proveedoresAcopiados,
    required this.proveedoresNoEntregaron,
    this.litrosRecibidosPlanta,
    this.diferencia,
    this.responsable,
    this.observacion,
    required this.detalle,
    this.requiereRecepcion = true,
  });

  final int rutaId;
  final String rutaNombre;
  final String? zona;
  final int? acopiadorId;
  final String? acopiadorNombre;
  final double litrosEnRuta;
  final double litrosEntregadosEnPlanta;
  final int proveedoresAcopiados;
  final int proveedoresNoEntregaron;
  final double? litrosRecibidosPlanta;
  final double? diferencia;
  final String? responsable;
  final String? observacion;
  final List<VisitaConciliacion> detalle;

  /// False si solo hubo entregas directas: ya están en planta, no hay llegada que anotar.
  final bool requiereRecepcion;

  bool get recibida => litrosRecibidosPlanta != null;

  factory ConciliacionRuta.fromJson(Map<String, dynamic> j) => ConciliacionRuta(
    rutaId: _i(j['rutaId']),
    rutaNombre: j['rutaNombre'] as String? ?? '—',
    zona: j['zona'] as String?,
    acopiadorId: (j['acopiadorId'] as num?)?.toInt(),
    acopiadorNombre: j['acopiadorNombre'] as String?,
    litrosEnRuta: _d(j['litrosEnRuta']),
    litrosEntregadosEnPlanta: _d(j['litrosEntregadosEnPlanta']),
    proveedoresAcopiados: _i(j['proveedoresAcopiados']),
    proveedoresNoEntregaron: _i(j['proveedoresNoEntregaron']),
    litrosRecibidosPlanta: _dn(j['litrosRecibidosPlanta']),
    diferencia: _dn(j['diferencia']),
    responsable: j['responsable'] as String?,
    observacion: j['observacion'] as String?,
    detalle: (j['detalle'] as List? ?? const [])
        .map((e) => VisitaConciliacion.fromJson(e as Map<String, dynamic>))
        .toList(),
    requiereRecepcion: j['requiereRecepcion'] as bool? ?? true,
  );
}

class VisitaConciliacion {
  VisitaConciliacion({
    this.productorNombre,
    this.productorCodigo,
    required this.fechaHora,
    required this.litros,
    this.estadoVisita,
    this.motivoNoEntrega,
    this.calidad,
  });

  final String? productorNombre;
  final String? productorCodigo;
  final String fechaHora;
  final double litros;
  final String? estadoVisita;
  final String? motivoNoEntrega;
  final String? calidad;

  factory VisitaConciliacion.fromJson(Map<String, dynamic> j) => VisitaConciliacion(
    productorNombre: j['productorNombre'] as String?,
    productorCodigo: j['productorCodigo'] as String?,
    fechaHora: j['fechaHora'] as String? ?? '',
    litros: _d(j['litros']),
    estadoVisita: j['estadoVisita'] as String?,
    motivoNoEntrega: j['motivoNoEntrega'] as String?,
    calidad: j['calidad'] as String?,
  );
}

/// `GET /calidad/incidencias` (calidad.read).
class IncidenciaCalidad {
  IncidenciaCalidad({
    required this.id,
    required this.productorId,
    required this.productorNombre,
    required this.tipo,
    this.descripcion,
    required this.fecha,
    required this.estado,
  });

  final int id;
  final int productorId;
  final String productorNombre;
  final String tipo;
  final String? descripcion;
  final String fecha;
  final String estado; // ABIERTA | EN_SEGUIMIENTO | CERRADA

  factory IncidenciaCalidad.fromJson(Map<String, dynamic> j) => IncidenciaCalidad(
    id: _i(j['id']),
    productorId: _i(j['productorId']),
    productorNombre: j['productorNombre'] as String? ?? '—',
    tipo: j['tipo'] as String? ?? '',
    descripcion: j['descripcion'] as String?,
    fecha: j['fecha'] as String? ?? '',
    estado: j['estado'] as String? ?? 'ABIERTA',
  );
}

/// Un control de calidad del historial de un proveedor (`GET /calidad/historial/{id}`).
class ControlCalidadResumen {
  ControlCalidadResumen({
    required this.id,
    required this.resultado,
    required this.fecha,
    this.densidad,
    this.grasa,
    this.temperatura,
    this.responsable,
    this.motivoRechazo,
    this.aprobadoPor,
  });

  final int id;
  final String resultado;
  final String fecha;
  final double? densidad;
  final double? grasa;
  final double? temperatura;
  final String? responsable;
  final String? motivoRechazo;
  final String? aprobadoPor;

  factory ControlCalidadResumen.fromJson(Map<String, dynamic> j) => ControlCalidadResumen(
    id: _i(j['id']),
    resultado: j['resultado'] as String? ?? '',
    fecha: (j['fechaHora'] ?? j['fecha']) as String? ?? '',
    densidad: _dn(j['densidad']),
    grasa: _dn(j['grasa']),
    temperatura: _dn(j['temperatura']),
    responsable: j['responsable'] as String?,
    motivoRechazo: j['motivoRechazo'] as String?,
    aprobadoPor: j['aprobadoPor'] as String?,
  );
}
