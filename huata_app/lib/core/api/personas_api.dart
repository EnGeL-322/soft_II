import '../models/acopiador.dart';
import '../models/productor.dart';
import '../models/ruta.dart';
import 'api_client.dart';

double _d(Object? v) => (v as num?)?.toDouble() ?? 0;
int _i(Object? v) => (v as num?)?.toInt() ?? 0;

/// Estados del proveedor (regla 7.3). Toda transición exige un motivo, que queda auditado.
const estadosProveedor = {
  'PENDIENTE_APROBACION': 'Pendiente de aprobación',
  'ACTIVO': 'Activo',
  'EN_REVISION': 'En revisión',
  'INACTIVO': 'Inactivo',
  'RETIRADO': 'Retirado',
};

/// `GET /zonas/asignaciones` (zonas.read): quién trabaja en cada zona.
class ZonaAsignacion {
  ZonaAsignacion({required this.zona, required this.rutaId, required this.rutaNombre, required this.proveedores, required this.acopiadores});
  final String zona;
  final int rutaId;
  final String rutaNombre;
  final List<({String codigo, String nombre, String? telefono, int? orden})> proveedores;
  final List<String> acopiadores;

  factory ZonaAsignacion.fromJson(Map<String, dynamic> j) => ZonaAsignacion(
    zona: j['zona'] as String? ?? 'Sin zona',
    rutaId: _i(j['rutaId']),
    rutaNombre: j['rutaNombre'] as String? ?? '',
    proveedores: [
      for (final p in (j['proveedores'] as List? ?? const []).cast<Map<String, dynamic>>())
        (
          codigo: p['codigo'] as String? ?? '',
          nombre: '${p['nombres'] ?? ''} ${p['apellidos'] ?? ''}'.trim(),
          telefono: p['telefono'] as String?,
          orden: (p['ordenEnRuta'] as num?)?.toInt(),
        ),
    ],
    acopiadores: [
      for (final a in (j['acopiadores'] as List? ?? const []).cast<Map<String, dynamic>>())
        '${a['nombres'] ?? ''} ${a['apellidos'] ?? ''}'.trim(),
    ],
  );
}

/// `GET /rutas/plan-operativo?fecha=` (rutas.read): cada ruta del día, su acopiador y lo recogido.
class PlanRutaDia {
  PlanRutaDia({
    required this.rutaId,
    required this.rutaNombre,
    this.zona,
    this.acopiadorId,
    this.acopiadorNombre,
    this.movilidad,
    required this.estado,
    required this.litros,
    required this.recepciones,
    this.ultimoRegistro,
  });

  final int rutaId;
  final String rutaNombre;
  final String? zona;
  final int? acopiadorId;
  final String? acopiadorNombre;
  final String? movilidad;
  final String estado; // SIN_ASIGNAR | ASIGNADA | CON_REGISTROS
  final double litros;
  final int recepciones;
  final String? ultimoRegistro;

  factory PlanRutaDia.fromJson(Map<String, dynamic> j) => PlanRutaDia(
    rutaId: _i(j['rutaId']),
    rutaNombre: j['rutaNombre'] as String? ?? '',
    zona: j['zona'] as String?,
    acopiadorId: (j['acopiadorId'] as num?)?.toInt(),
    acopiadorNombre: j['acopiadorNombre'] as String?,
    movilidad: j['movilidad'] as String?,
    estado: j['estado'] as String? ?? 'SIN_ASIGNAR',
    litros: _d(j['litros']),
    recepciones: _i(j['recepciones']),
    ultimoRegistro: j['ultimoRegistro'] as String?,
  );
}

/// Última posición GPS de cada acopiador (`GET /ubicaciones/ultimas`, rutas.read). El GPS no es por
/// sí solo prueba absoluta (CLAUDE.md §6): se muestra como referencia.
class UbicacionAcopiador {
  UbicacionAcopiador({required this.acopiadorId, required this.acopiadorNombre, required this.latitud, required this.longitud, required this.fecha, this.precisionMetros});
  final int acopiadorId;
  final String acopiadorNombre;
  final double latitud;
  final double longitud;
  final String fecha;
  final double? precisionMetros;

  factory UbicacionAcopiador.fromJson(Map<String, dynamic> j) => UbicacionAcopiador(
    acopiadorId: _i(j['acopiadorId']),
    acopiadorNombre: j['acopiadorNombre'] as String? ?? '',
    latitud: _d(j['latitud']),
    longitud: _d(j['longitud']),
    fecha: j['fecha'] as String? ?? '',
    precisionMetros: (j['precisionMetros'] as num?)?.toDouble(),
  );
}

/// Lo que usan las vistas de Personas y rutas (Proveedores, Acopiadores, Cambios de zona, Zonas,
/// Rutas y mapa), con los mismos endpoints que la web.
class PersonasApi {
  PersonasApi(this._client);
  final ApiClient _client;

  static String _dia(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<({List<Productor> filas, int total})> proveedores({String q = '', String? estado, int pagina = 0, int tamano = 40}) async {
    final r = await _client.dio.get('/productores', queryParameters: {
      'page': pagina,
      'size': tamano,
      if (q.trim().isNotEmpty) 'q': q.trim(),
      'estado': ?estado,
    });
    final datos = r.data as Map<String, dynamic>;
    return (
      filas: (datos['content'] as List).map((e) => Productor.fromJson(e as Map<String, dynamic>)).toList(),
      total: (datos['totalElements'] as num?)?.toInt() ?? 0,
    );
  }

  Future<Productor> proveedor(int id) async =>
      Productor.fromJson((await _client.dio.get('/productores/$id')).data as Map<String, dynamic>);

  Future<void> cambiarEstadoProveedor(int id, String estado, String motivo) =>
      _client.dio.patch('/productores/$id/estado', data: {'estado': estado, 'motivo': motivo.trim()});

  Future<void> cambiarExcelencia(int id, {required bool excelencia, required String motivo}) =>
      _client.dio.patch('/productores/$id/excelencia', data: {'excelencia': excelencia, 'motivo': motivo.trim()});

  Future<({List<Acopiador> filas, int total})> acopiadores({String q = '', int pagina = 0, int tamano = 50}) async {
    final r = await _client.dio.get('/acopiadores', queryParameters: {'page': pagina, 'size': tamano, if (q.trim().isNotEmpty) 'q': q.trim()});
    final datos = r.data as Map<String, dynamic>;
    return (
      filas: (datos['content'] as List).map((e) => Acopiador.fromJson(e as Map<String, dynamic>)).toList(),
      total: (datos['totalElements'] as num?)?.toInt() ?? 0,
    );
  }

  Future<List<SolicitudCambioZona>> solicitudesZona({String? estado}) async {
    final r = await _client.dio.get('/productores/solicitudes-zona', queryParameters: {'estado': ?estado});
    return (r.data as List).map((e) => SolicitudCambioZona.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<void> decidirSolicitudZona(int id, {required bool aprobar, String? observacion}) =>
      _client.dio.patch('/productores/solicitudes-zona/$id', data: {
        'aprobar': aprobar,
        if (observacion != null && observacion.trim().isNotEmpty) 'observacion': observacion.trim(),
      });

  Future<List<ZonaAsignacion>> zonas() async =>
      ((await _client.dio.get('/zonas/asignaciones')).data as List).map((e) => ZonaAsignacion.fromJson(e as Map<String, dynamic>)).toList();

  Future<List<PlanRutaDia>> planOperativo(DateTime dia) async =>
      ((await _client.dio.get('/rutas/plan-operativo', queryParameters: {'fecha': _dia(dia)})).data as List)
          .map((e) => PlanRutaDia.fromJson(e as Map<String, dynamic>))
          .toList();

  Future<List<UbicacionAcopiador>> ultimasUbicaciones() async =>
      ((await _client.dio.get('/ubicaciones/ultimas')).data as List).map((e) => UbicacionAcopiador.fromJson(e as Map<String, dynamic>)).toList();

  Future<List<ProveedorDeRuta>> proveedoresDeRuta(int rutaId) async =>
      ((await _client.dio.get('/rutas/$rutaId/proveedores')).data as List).map((e) => ProveedorDeRuta.fromJson(e as Map<String, dynamic>)).toList();
}
