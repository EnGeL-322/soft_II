import '../models/ruta.dart';

/// Geocerca de zona: comprueba si una coordenada cae dentro del poligono dibujado de una zona.
///
/// Mismo algoritmo (ray casting) y mismo criterio que `GeoUtils.dentroDePoligono` en el backend, para
/// que el aviso del movil y el rechazo del servidor coincidan. Un poligono con menos de 3 vertices no
/// delimita area, asi que no encierra nada y se devuelve false.
bool dentroDePoligono(
  double latitud,
  double longitud,
  List<ZonaPoligonoPunto> vertices,
) {
  if (vertices.length < 3) return false;
  var dentro = false;
  for (var i = 0, j = vertices.length - 1; i < vertices.length; j = i++) {
    final yi = vertices[i].latitud;
    final xi = vertices[i].longitud;
    final yj = vertices[j].latitud;
    final xj = vertices[j].longitud;
    final cruza =
        (yi > latitud) != (yj > latitud) &&
        longitud < (xj - xi) * (latitud - yi) / (yj - yi) + xi;
    if (cruza) dentro = !dentro;
  }
  return dentro;
}
