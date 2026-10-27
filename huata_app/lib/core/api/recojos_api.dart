import '../models/recojo.dart';
import 'api_client.dart';

class RecojosApi {
  RecojosApi(this._client);
  final ApiClient _client;

  /// Recojos de una ruta en una fecha (acopiador: para calcular blanco/verde/rojo por proveedor,
  /// RF-75) o filtrados por proveedor (productor de queso / trazabilidad).
  Future<List<Recojo>> listar({int? rutaId, int? productorId, DateTime? desde, DateTime? hasta, int size = 200}) {
    return _todasLasPaginas('/recojos', {
      'rutaId': ?rutaId,
      'productorId': ?productorId,
      if (desde != null) 'desde': desde.toUtc().toIso8601String(),
      if (hasta != null) 'hasta': hasta.toUtc().toIso8601String(),
    }, size);
  }

  /// "Mis entregas" — app del proveedor (RF-78/79), no exige acopio.read.
  Future<List<Recojo>> misRecojos({DateTime? desde, DateTime? hasta, int size = 200}) {
    return _todasLasPaginas('/recojos/me', {
      if (desde != null) 'desde': desde.toUtc().toIso8601String(),
      if (hasta != null) 'hasta': hasta.toUtc().toIso8601String(),
    }, size);
  }

  /// El backend limita cada página a [paginaMaxima] filas (`common/Paginas`). Pedir una sola
  /// página con un tamaño grande dejaba la lista cortada sin avisar: un proveedor con mucho
  /// historial veía menos entregas de las que tiene. Aquí se recorren todas las páginas.
  static const paginaMaxima = 500;

  /// Tope de seguridad: con 40 páginas de 500 son 20 000 recojos; más que eso no cabe en una
  /// pantalla del teléfono y es señal de un filtro mal armado, no de datos reales.
  static const _paginasMaximas = 40;

  Future<List<Recojo>> _todasLasPaginas(String ruta, Map<String, dynamic> filtros, int size) async {
    final tamano = size.clamp(1, paginaMaxima);
    final todos = <Recojo>[];
    for (var pagina = 0; pagina < _paginasMaximas; pagina++) {
      final r = await _client.dio.get(ruta, queryParameters: {...filtros, 'page': pagina, 'size': tamano});
      final datos = r.data as Map<String, dynamic>;
      final content = datos['content'] as List;
      todos.addAll(content.map((e) => Recojo.fromJson(e as Map<String, dynamic>)));
      final totalPaginas = (datos['totalPages'] as num?)?.toInt() ?? 1;
      if (content.isEmpty || pagina + 1 >= totalPaginas) break;
    }
    return todos;
  }

  Future<Recojo> registrar(RecojoRequest request) async {
    final r = await _client.dio.post('/recojos', data: request.toJson());
    return Recojo.fromJson(r.data as Map<String, dynamic>);
  }

  /// Corrige un recojo ya registrado (pedido del cliente, 2026-09-24): un proveedor entrega leche
  /// una sola vez al día, así que esta es la vía para arreglar un litros mal tecleado sin chocar
  /// contra esa regla creando un segundo recojo. Solo en línea a propósito: corrige un id que ya
  /// existe en el servidor, así que no tiene sentido encolarla offline como un registro nuevo.
  Future<Recojo> corregir(int recojoId, RecojoCorreccionRequest request) async {
    final r = await _client.dio.patch('/recojos/$recojoId/corregir', data: request.toJson());
    return Recojo.fromJson(r.data as Map<String, dynamic>);
  }

  Future<ReporteAcopiador> miReporte({required String periodo, DateTime? fecha}) async {
    final referencia = fecha ?? DateTime.now();
    final fechaTexto = '${referencia.year.toString().padLeft(4, '0')}-${referencia.month.toString().padLeft(2, '0')}-${referencia.day.toString().padLeft(2, '0')}';
    final r = await _client.dio.get(
      '/recojos/me/acopiador/reporte',
      queryParameters: {'periodo': periodo, 'fecha': fechaTexto},
    );
    return ReporteAcopiador.fromJson(r.data as Map<String, dynamic>);
  }
}
