import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/models/calidad.dart';

void main() {
  test('lee el análisis pendiente y dice de dónde viene la leche', () {
    final directa = AnalisisPorAprobar.fromJson({
      'id': 7,
      'productorNombre': 'Proveedor Demo H',
      'productorCodigo': 'DEMO-008',
      'recojoId': 229,
      'lugarAnalisis': 'PLANTA',
      'resultado': 'ACEPTADA',
      'densidad': 1.0318,
      'grasa': 3.66,
      'fechaHora': '2026-10-14T15:32:00Z',
    });
    expect(directa.id, 7);
    expect(directa.origen, 'Entrega directa en planta');
    expect(directa.densidad, 1.0318);
    expect(directa.fechaHora, isNotNull);

    final enCampo = AnalisisPorAprobar.fromJson({
      'id': 8,
      'productorNombre': 'Proveedor Demo J',
      'recojoId': null,
      'lugarAnalisis': 'CAMPO',
      'resultado': 'OBSERVADA',
      'advertencia': 'Grasa 3 bajo el mínimo 3.2 %',
    });
    expect(enCampo.origen, 'Esperando recojo');
    expect(enCampo.advertencia, contains('Grasa'));
  });
}
