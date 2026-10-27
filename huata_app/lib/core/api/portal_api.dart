import 'api_client.dart';

class HistorialPublico {
  HistorialPublico({required this.fecha, required this.resultado, this.litros});
  final String fecha;
  final String resultado;
  final double? litros;

  factory HistorialPublico.fromJson(Map<String, dynamic> j) => HistorialPublico(
    fecha: j['fecha'] as String,
    resultado: j['resultado'] as String,
    litros: (j['litros'] as num?)?.toDouble(),
  );
}

class ConsultaPublica {
  ConsultaPublica({required this.codigo, this.zona, required this.estado, required this.historial});
  final String codigo;
  final String? zona;
  final String estado;
  final List<HistorialPublico> historial;

  factory ConsultaPublica.fromJson(Map<String, dynamic> j) => ConsultaPublica(
    codigo: j['codigo'] as String,
    zona: j['zona'] as String?,
    estado: j['estado'] as String,
    historial: (j['historial'] as List).map((e) => HistorialPublico.fromJson(e as Map<String, dynamic>)).toList(),
  );
}

/// RF-48: calidad de una zona en el día. `estado` es APTA | OBSERVADA | ADULTERACION.
class ZonaCalidadPublica {
  ZonaCalidadPublica({required this.zona, required this.controles, required this.estado});
  final String zona;
  final int controles;
  final String estado;

  factory ZonaCalidadPublica.fromJson(Map<String, dynamic> j) =>
      ZonaCalidadPublica(zona: j['zona'] as String, controles: j['controles'] as int, estado: j['estado'] as String);
}

/// RF-49: proveedor con una incidencia de calidad confirmada hoy. Solo código, nunca nombre completo.
class IncidenciaPublica {
  IncidenciaPublica({required this.productorCodigo, this.zona, required this.tipo, required this.fecha});
  final String productorCodigo;
  final String? zona;
  final String tipo;
  final String fecha;

  factory IncidenciaPublica.fromJson(Map<String, dynamic> j) => IncidenciaPublica(
    productorCodigo: j['productorCodigo'] as String,
    zona: j['zona'] as String?,
    tipo: j['tipo'] as String,
    fecha: j['fecha'] as String,
  );
}

class ResumenPublicoHoy {
  ResumenPublicoHoy({required this.fecha, required this.zonas, required this.incidencias});
  final String fecha;
  final List<ZonaCalidadPublica> zonas;
  final List<IncidenciaPublica> incidencias;

  factory ResumenPublicoHoy.fromJson(Map<String, dynamic> j) => ResumenPublicoHoy(
    fecha: j['fecha'] as String,
    zonas: (j['zonas'] as List).map((e) => ZonaCalidadPublica.fromJson(e as Map<String, dynamic>)).toList(),
    incidencias: (j['incidencias'] as List).map((e) => IncidenciaPublica.fromJson(e as Map<String, dynamic>)).toList(),
  );
}

/// `GET /api/v1/public/**` — mismos endpoints sin login que usa el portal web.
class PortalApi {
  PortalApi(this._client);
  final ApiClient _client;

  Future<ConsultaPublica> consultar(String codigo) async {
    final r = await _client.dio.get('/public/productores/$codigo/calidad');
    return ConsultaPublica.fromJson(r.data as Map<String, dynamic>);
  }

  /// RF-49 + aviso activo a los demás proveedores (item 4 de PROXIMA_FASE_PROMPT.md): la app lo
  /// consulta al abrir y muestra un banner si hay alguna incidencia hoy. Sin push todavía — decisión
  /// tomada con el usuario: endpoint público + banner al abrir, no una integración de notificaciones.
  Future<ResumenPublicoHoy> incidenciasDeHoy() async {
    final r = await _client.dio.get('/public/incidencias/hoy');
    return ResumenPublicoHoy.fromJson(r.data as Map<String, dynamic>);
  }
}
