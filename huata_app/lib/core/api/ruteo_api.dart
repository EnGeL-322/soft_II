import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:latlong2/latlong.dart';

/// Ruteo real por calles vía Valhalla (servidor público de OpenStreetMap/FOSSGIS, sin API key,
/// sin SLA garantizado — ver integracion-huata/README.md). Puerto Dart de
/// `huata-web/src/app/core/services/ruteo.service.ts`, mismo servidor y misma decodificación de
/// polyline6.
///
/// Usa un [Dio] propio, SIN pasar por [ApiClient]: ese cliente agrega el JWT de la sesión a toda
/// petición (interceptor de autenticación), y ese token no debe salir hacia un servidor público de
/// terceros.
class RuteoApi {
  RuteoApi() : _dio = Dio(BaseOptions(connectTimeout: const Duration(seconds: 15)));

  final Dio _dio;

  static const _url = 'https://valhalla1.openstreetmap.de/route';
  static const _maxUbicaciones = 10;

  /// Camino real por calles entre los puntos dados, en el orden dado. Nunca lanza: si el servicio
  /// público falla o no encuentra conexión vial, devuelve una lista vacía para que quien llama
  /// conserve la línea recta como respaldo.
  Future<List<LatLng>> calcularRuta(List<LatLng> puntos) async {
    if (puntos.length < 2) return [];
    try {
      return await _pedirRutaCompleta(puntos, excluirTrocha: true);
    } catch (_) {
      try {
        // Reintenta permitiendo trocha: puede no existir conexión 100% asfaltada entre los puntos.
        return await _pedirRutaCompleta(puntos, excluirTrocha: false);
      } catch (_) {
        return [];
      }
    }
  }

  Future<List<LatLng>> _pedirRutaCompleta(List<LatLng> puntos, {required bool excluirTrocha}) async {
    if (puntos.length <= _maxUbicaciones) return _pedirTramo(puntos, excluirTrocha: excluirTrocha);
    var geometria = <LatLng>[];
    var i = 0;
    while (i < puntos.length - 1) {
      final fin = (i + _maxUbicaciones - 1).clamp(0, puntos.length - 1);
      final tramo = await _pedirTramo(puntos.sublist(i, fin + 1), excluirTrocha: excluirTrocha);
      geometria = geometria.isEmpty ? tramo : [...geometria, ...tramo.skip(1)];
      i = fin;
    }
    return geometria;
  }

  Future<List<LatLng>> _pedirTramo(List<LatLng> puntos, {required bool excluirTrocha}) async {
    final cuerpo = {
      'locations': puntos.map((p) => {'lat': p.latitude, 'lon': p.longitude}).toList(),
      'costing': 'auto',
      'costing_options': {
        'auto': {'exclude_unpaved': excluirTrocha},
      },
      'units': 'kilometers',
    };
    final respuesta = await _dio.get(_url, queryParameters: {'json': jsonEncode(cuerpo)});
    final datos = respuesta.data is Map<String, dynamic>
        ? respuesta.data as Map<String, dynamic>
        : <String, dynamic>{};
    final legs = (datos['trip']?['legs'] as List?) ?? const [];
    final resultado = <LatLng>[];
    for (var i = 0; i < legs.length; i++) {
      final shape = _decodificarPolyline6(legs[i]['shape'] as String);
      resultado.addAll(i == 0 ? shape : shape.skip(1));
    }
    return resultado;
  }

  List<LatLng> _decodificarPolyline6(String texto) {
    var index = 0, lat = 0, lng = 0;
    final coords = <LatLng>[];
    while (index < texto.length) {
      var shift = 0, result = 0;
      int byte;
      do {
        byte = texto.codeUnitAt(index++) - 63;
        result |= (byte & 0x1f) << shift;
        shift += 5;
      } while (byte >= 0x20);
      lat += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
      shift = 0;
      result = 0;
      do {
        byte = texto.codeUnitAt(index++) - 63;
        result |= (byte & 0x1f) << shift;
        shift += 5;
      } while (byte >= 0x20);
      lng += (result & 1) != 0 ? ~(result >> 1) : (result >> 1);
      coords.add(LatLng(lat / 1e6, lng / 1e6));
    }
    return coords;
  }
}
