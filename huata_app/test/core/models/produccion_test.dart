import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/models/produccion.dart';

void main() {
  group('Receta.fromJson', () {
    test('lee la receta vigente con sus ítems', () {
      final r = Receta.fromJson({
        'id': 3,
        'productoId': 7,
        'productoNombre': 'Yogur natural',
        'vigenteDesde': '2026-01-01',
        'vigenteHasta': null,
        'rendimientoLitrosPorUnidad': 6.0,
        'observaciones': null,
        'items': [
          {'materiaPrimaId': 1, 'materiaPrimaNombre': 'Leche apta', 'unidadMedida': 'LITRO', 'cantidadPorUnidad': 6.0},
          {'materiaPrimaId': 2, 'materiaPrimaNombre': 'Sal', 'unidadMedida': 'KILOGRAMO', 'cantidadPorUnidad': 0.035},
        ],
      });

      expect(r.id, 3);
      expect(r.productoId, 7);
      expect(r.productoNombre, 'Yogur natural');
      expect(r.items, hasLength(2));
      expect(r.items.first.materiaPrimaNombre, 'Leche apta');
    });
  });

  group('Producto.fromJson', () {
    test('lee el producto del catálogo y asume "unidad" si no viene la unidad', () {
      final p = Producto.fromJson({'id': 4, 'nombre': 'Yogur natural', 'unidadVenta': 'botella 1 L', 'activo': true});
      expect(p.nombre, 'Yogur natural');
      expect(p.unidadVenta, 'botella 1 L');
      expect(Producto.fromJson({'id': 5, 'nombre': 'Manjar'}).unidadVenta, 'unidad');
    });
  });

  group('Simulacion.fromJson', () {
    test('marca alcanzaTodo en false si algún ítem no alcanza (RF-66)', () {
      final s = Simulacion.fromJson({
        'recetaId': 3,
        'cantidadObjetivo': 100,
        'alcanzaTodo': false,
        'items': [
          {
            'materiaPrimaId': 1,
            'materiaPrimaNombre': 'Leche apta',
            'unidadMedida': 'LITRO',
            'cantidadRequerida': 600.0,
            'stockActual': 400.0,
            'alcanza': false,
          },
        ],
      });

      expect(s.alcanzaTodo, isFalse);
      expect(s.items.single.alcanza, isFalse);
      expect(s.items.single.cantidadRequerida, 600.0);
    });
  });

  group('TrabajadorProduccion.fromJson', () {
    test('lee id y nombre', () {
      final t = TrabajadorProduccion.fromJson({'id': 7, 'nombre': 'Juana Mamani'});
      expect(t.id, 7);
      expect(t.nombre, 'Juana Mamani');
    });
  });

  // El consumo ya no se manda: el backend descuenta la receta × la cantidad a producir.
  group('ManoObraItem.toJson', () {
    test('serializa solo lo que el backend espera (LoteQuesoRequest)', () {
      final mano = ManoObraItem(trabajadorId: 7, monto: 45.0);

      expect(mano.toJson(), {'trabajadorId': 7, 'monto': 45.0});
    });
  });
}
