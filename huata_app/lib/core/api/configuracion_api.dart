import 'api_client.dart';

double _d(Object? v) => (v as num?)?.toDouble() ?? 0;
double? _dn(Object? v) => (v as num?)?.toDouble();

/// Precio por litro vigente en un período (`GET /tarifas-leche`, pagos.read).
class TarifaLeche {
  TarifaLeche({required this.precioNormal, required this.precioObservada, this.precioExcelencia, required this.vigenteDesde, this.vigenteHasta});
  final double precioNormal;
  final double precioObservada;
  final double? precioExcelencia;
  final String vigenteDesde;
  final String? vigenteHasta;

  factory TarifaLeche.fromJson(Map<String, dynamic> j) => TarifaLeche(
    precioNormal: _d(j['precioNormal']),
    precioObservada: _d(j['precioObservada']),
    precioExcelencia: _dn(j['precioExcelencia']),
    vigenteDesde: j['vigenteDesde'] as String? ?? '',
    vigenteHasta: j['vigenteHasta'] as String?,
  );
}

/// Reglas de calidad configurables (`/reglas-leche`, parametros.read).
class ReglasLeche {
  ReglasLeche({required this.deteccionesParaRetiro, required this.observadaEsMalaCalidad, required this.observadaEntraAInventario, this.costoLecheLitro});
  int deteccionesParaRetiro;
  bool observadaEsMalaCalidad;
  bool observadaEntraAInventario;
  double? costoLecheLitro;

  factory ReglasLeche.fromJson(Map<String, dynamic> j) => ReglasLeche(
    deteccionesParaRetiro: (j['deteccionesParaRetiro'] as num?)?.toInt() ?? 0,
    observadaEsMalaCalidad: j['observadaEsMalaCalidad'] as bool? ?? false,
    observadaEntraAInventario: j['observadaEntraAInventario'] as bool? ?? false,
    costoLecheLitro: _dn(j['costoLecheLitro']),
  );

  Map<String, dynamic> toJson() => {
    'deteccionesParaRetiro': deteccionesParaRetiro,
    'observadaEsMalaCalidad': observadaEsMalaCalidad,
    'observadaEntraAInventario': observadaEntraAInventario,
    'costoLecheLitro': costoLecheLitro,
  };
}

/// Reglas del recojo en ruta (`/reglas-acopio`): geocerca de la zona y distancia al proveedor.
class ReglasAcopioConfig {
  ReglasAcopioConfig({required this.exigirGeocerca, required this.exigirDistancia, required this.distanciaMaximaMetros});
  bool exigirGeocerca;
  bool exigirDistancia;
  double distanciaMaximaMetros;

  factory ReglasAcopioConfig.fromJson(Map<String, dynamic> j) => ReglasAcopioConfig(
    exigirGeocerca: j['exigirGeocerca'] as bool? ?? false,
    exigirDistancia: j['exigirDistancia'] as bool? ?? false,
    distanciaMaximaMetros: _d(j['distanciaMaximaMetros']),
  );

  Map<String, dynamic> toJson() => {
    'exigirGeocerca': exigirGeocerca,
    'exigirDistancia': exigirDistancia,
    'distanciaMaximaMetros': distanciaMaximaMetros,
  };
}

/// Vista "Precios y calidad" de la web, con los mismos endpoints. Cambiar el precio de la leche exige
/// el código de verificación que se envía al responsable (como en la web).
class ConfiguracionApi {
  ConfiguracionApi(this._client);
  final ApiClient _client;

  static String _dia(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

  Future<List<TarifaLeche>> tarifas() async =>
      ((await _client.dio.get('/tarifas-leche')).data as List).map((e) => TarifaLeche.fromJson(e as Map<String, dynamic>)).toList();

  Future<ReglasLeche> reglasLeche() async => ReglasLeche.fromJson((await _client.dio.get('/reglas-leche')).data as Map<String, dynamic>);

  Future<void> guardarReglasLeche(ReglasLeche r) => _client.dio.put('/reglas-leche', data: r.toJson());

  Future<ReglasAcopioConfig> reglasAcopio() async =>
      ReglasAcopioConfig.fromJson((await _client.dio.get('/reglas-acopio')).data as Map<String, dynamic>);

  Future<void> guardarReglasAcopio(ReglasAcopioConfig r) => _client.dio.put('/reglas-acopio', data: r.toJson());

  /// Envía el código de 6 dígitos para cambiar precios. Devuelve a dónde se envió (enmascarado).
  Future<String> solicitarCodigoPrecios() async {
    final r = await _client.dio.post('/verificacion/codigo', data: {'proposito': 'PRECIOS'});
    final datos = r.data as Map<String, dynamic>;
    return datos['destinatarioEnmascarado'] as String? ?? 'el responsable';
  }

  Future<void> nuevaTarifa({
    required double precioNormal,
    required double precioObservada,
    double? precioExcelencia,
    required DateTime vigenteDesde,
    required String codigo,
  }) => _client.dio.post('/tarifas-leche', data: {
    'precioNormal': precioNormal,
    'precioObservada': precioObservada,
    'precioExcelencia': ?precioExcelencia,
    'vigenteDesde': _dia(vigenteDesde),
    'codigo': codigo,
  });
}
