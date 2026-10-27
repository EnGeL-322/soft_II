import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/mapa/recorrido_mapa.dart';
import 'package:latlong2/latlong.dart' as ll;

void main() {
  final puntos = [
    const ll.LatLng(-15.60, -70.10),
    const ll.LatLng(-15.6001, -70.1001),
    const ll.LatLng(-15.6002, -70.1002),
  ];

  int circulos(List<Widget> capas) =>
      capas.whereType<CircleLayer>().fold(0, (s, c) => s + c.circles.length);

  test('los puntos del recorrido se guardan cada 10 metros', () {
    expect(distanciaEntrePuntosGpsMetros, 10);
  });

  test('en modo trazado se dibuja la línea y solo el punto actual', () {
    final capas = capasRecorrido(puntos, ModoRecorrido.trazado, color: Colors.green);
    expect(capas.whereType<PolylineLayer>(), hasLength(1));
    expect(circulos(capas), 1);
  });

  test('en modo puntos no hay línea y se ve cada punto', () {
    final capas = capasRecorrido(puntos, ModoRecorrido.puntos, color: Colors.green);
    expect(capas.whereType<PolylineLayer>(), isEmpty);
    expect(circulos(capas), 3);
  });

  test('en modo ambos se ven la línea y todos los puntos', () {
    final capas = capasRecorrido(puntos, ModoRecorrido.ambos, color: Colors.green);
    expect(capas.whereType<PolylineLayer>(), hasLength(1));
    expect(circulos(capas), 3);
  });

  test('sin recorrido no se dibuja nada', () {
    expect(capasRecorrido(const [], ModoRecorrido.ambos, color: Colors.green), isEmpty);
  });
}
