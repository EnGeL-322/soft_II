import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/portal_api.dart';
import 'package:huata_app/core/models/liquidacion.dart';

void main() {
  group('Liquidacion.fromJson', () {
    test('lee un pago cerrado del backend', () {
      final l = Liquidacion.fromJson({
        'id': 1,
        'productorId': 5,
        'productorNombre': 'Proveedor Demo A',
        'periodoInicio': '2026-09-07',
        'periodoFin': '2026-09-13',
        'litrosTotales': 337.1,
        'montoTotal': 538.67,
        'ajustesTotal': 0.0,
        'montoNeto': 538.67,
        'estado': 'CLOSED',
        'detalle': [],
        'ajustes': [],
      });

      expect(l.periodoInicio, '2026-09-07');
      expect(l.montoNeto, 538.67);
      expect(l.estado, 'CLOSED');
    });
  });

  group('CobroEstimado.fromJson', () {
    test('lee el estimado del ciclo en curso, con zona opcional', () {
      final e = CobroEstimado.fromJson({
        'proveedorId': 1,
        'proveedorCodigo': 'DEMO-001',
        'proveedorNombre': 'Proveedor Demo A',
        'zona': 'COLLANA I',
        'excelencia': false,
        'litrosEntregados': 301.7,
        'litrosRechazados': 0.0,
        'litrosPendientes': 301.7,
        'tarifaAplicada': 1.56,
        'importePendiente': 471.37,
        'liquidado': false,
      });

      expect(e.zona, 'COLLANA I');
      expect(e.importePendiente, 471.37);
      expect(e.liquidado, isFalse);
    });

    test('la zona puede venir nula (proveedor sin ruta/zona principal)', () {
      final e = CobroEstimado.fromJson({
        'proveedorId': 1,
        'proveedorCodigo': 'DEMO-001',
        'proveedorNombre': 'Proveedor Demo A',
        'zona': null,
        'excelencia': false,
        'litrosEntregados': 0.0,
        'litrosRechazados': 0.0,
        'litrosPendientes': 0.0,
        'tarifaAplicada': 0.0,
        'importePendiente': 0.0,
        'liquidado': false,
      });

      expect(e.zona, isNull);
      expect(e.importePendiente, 0.0);
    });
  });

  test('ResumenPagoPeriodo separa lo pagado de lo pendiente', () {
    final r = ResumenPagoPeriodo.fromJson({
      'desde': '2026-09-01',
      'hasta': '2026-09-30',
      'litrosProducidos': 420.0,
      'litrosPagados': 300.0,
      'litrosPendientes': 120.0,
      'montoPagado': 510.0,
      'montoPendiente': 204.0,
      'entregas': 12,
      'aceptadas': 10,
      'observadas': 1,
      'rechazadas': 1,
    });

    expect(r.montoPagado, 510.0);
    expect(r.montoPendiente, 204.0);
    expect(r.litrosProducidos, 420.0);
  });

  group('ResumenPublicoHoy.fromJson', () {
    test('trae zonas e incidencias del día público', () {
      final r = ResumenPublicoHoy.fromJson({
        'fecha': '2026-09-16',
        'zonas': [
          {'zona': 'COLLANA I', 'controles': 3, 'aceptadas': 2, 'observadas': 1, 'rechazadas': 0, 'estado': 'OBSERVADA'},
        ],
        'incidencias': [
          {'productorCodigo': 'DEMO-007', 'zona': 'Moro Viejo', 'tipo': 'Adulteración', 'fecha': '2026-09-16'},
        ],
      });

      expect(r.zonas, hasLength(1));
      expect(r.zonas.first.estado, 'OBSERVADA');
      expect(r.incidencias, hasLength(1));
      expect(r.incidencias.first.productorCodigo, 'DEMO-007');
    });

    test('sin controles ni incidencias hoy, ambas listas quedan vacías (no se inventa nada)', () {
      final r = ResumenPublicoHoy.fromJson({'fecha': '2026-09-16', 'zonas': [], 'incidencias': []});

      expect(r.zonas, isEmpty);
      expect(r.incidencias, isEmpty);
    });
  });
}
