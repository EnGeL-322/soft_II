import 'package:dio/dio.dart';

import '../models/reglas_acopio.dart';
import '../models/ruta.dart';
import 'api_client.dart';

class RutasApi {
  RutasApi(this._client);
  final ApiClient _client;

  Future<List<AsignacionRuta>> asignaciones({required DateTime fecha, int? acopiadorId}) async {
    final r = await _client.dio.get(
      '/asignaciones-ruta',
      queryParameters: {'fecha': _fecha(fecha), 'acopiadorId': ?acopiadorId},
    );
    return (r.data as List).map((e) => AsignacionRuta.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<ProveedorDeRuta>> proveedoresDeRuta(int rutaId) async {
    final r = await _client.dio.get('/rutas/$rutaId/proveedores');
    return (r.data as List).map((e) => ProveedorDeRuta.fromJson(e as Map<String, dynamic>)).toList();
  }

  Future<List<RutaPunto>> puntosDeRuta(int rutaId) async {
    final r = await _client.dio.get('/rutas/$rutaId/puntos');
    return (r.data as List).map((e) => RutaPunto.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Para el testeador: elegir "ruta planificada" en vez de recorrido libre (RF-86).
  Future<List<Ruta>> listar({String q = ''}) async {
    final r = await _client.dio.get('/rutas', queryParameters: {'q': q, 'page': 0, 'size': 100});
    final content = (r.data as Map<String, dynamic>)['content'] as List;
    return content.map((e) => Ruta.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Polígonos de zona (territorio real), mismo endpoint que ya dibuja huata-web en "Rutas y
  /// movilidad". Requiere `rutas.read`, que ya tienen tanto ACOPIADOR como CALIDAD.
  Future<List<ZonaPoligono>> poligonosDeZona() async {
    final r = await _client.dio.get('/zonas/poligonos');
    return (r.data as List).map((e) => ZonaPoligono.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Reglas de ubicación del acopio (geocerca y distancia). Solo exige sesión.
  Future<ReglasAcopio> reglasAcopio() async {
    final r = await _client.dio.get('/reglas-acopio');
    return ReglasAcopio.fromJson(r.data as Map<String, dynamic>);
  }

  /// "Puntos establecidos" (pedido del cliente): los que le tocan a este acopiador, por su ruta del
  /// día o por asignación directa. Mismo permiso `rutas.read` que el resto de esta pantalla.
  Future<List<PuntoEstablecido>> puntosEstablecidosDeMiAcopiador() async {
    final r = await _client.dio.get('/puntos-establecidos/me');
    return (r.data as List).map((e) => PuntoEstablecido.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Snapshot completo: lista activa y bajas versionadas. Las bajas solo sirven para descartar
  /// eventos viejos; PuntosSocketService nunca las muestra en lista ni en mapa.
  Future<List<PuntoEstablecido>> snapshotPuntosEstablecidos() async {
    final r = await _client.dio.get('/puntos-establecidos/me/snapshot');
    final datos = r.data as Map<String, dynamic>;
    return [
      ...datos['puntos'] as List,
      ...datos['bajas'] as List,
    ].map((e) => PuntoEstablecido.fromJson(e as Map<String, dynamic>)).toList();
  }

  String _fecha(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}

/// Se comparte con el resto de las APIs: todas necesitan "hoy" en formato `YYYY-MM-DD` para los
/// query params `LocalDate` del backend.
extension FechaIso on DateTime {
  String toIsoDate() => '$year-${month.toString().padLeft(2, '0')}-${day.toString().padLeft(2, '0')}';
}

/// Traduce un [DioException] a un mensaje legible sin duplicar el parseo en cada pantalla.
String mensajeDeError(Object error) {
  if (error is DioException) {
    final data = error.response?.data;
    if (data is Map && data['message'] is String) return data['message'] as String;
    if (error.type == DioExceptionType.connectionError || error.type == DioExceptionType.connectionTimeout) {
      return 'Sin conexión con el servidor.';
    }
  }
  return 'No se pudo completar la solicitud.';
}
