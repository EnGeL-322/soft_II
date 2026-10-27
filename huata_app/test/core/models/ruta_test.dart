import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/models/ruta.dart';

void main() {
  group('ProveedorDeRuta.fromJson', () {
    test('lee latitud/longitud cuando el backend las manda', () {
      final p = ProveedorDeRuta.fromJson({
        'id': 1,
        'codigo': 'DEMO-001',
        'nombres': 'Proveedor',
        'apellidos': 'Demo A',
        'ordenEnRuta': 2,
        'latitud': -15.65,
        'longitud': -69.98,
        'tipoUbicacion': 'CASA',
        'fechaUbicacion': '2026-09-20',
      });

      expect(p.ordenEnRuta, 2);
      expect(p.latitud, -15.65);
      expect(p.longitud, -69.98);
    });

    test('sin ubicación registrada, latitud/longitud quedan nulas (no se inventan)', () {
      final p = ProveedorDeRuta.fromJson({
        'id': 2,
        'codigo': 'DEMO-002',
        'nombres': 'Proveedor',
        'apellidos': 'Demo B',
        'ordenEnRuta': null,
        'latitud': null,
        'longitud': null,
      });

      expect(p.latitud, isNull);
      expect(p.longitud, isNull);
    });
  });

  group('PuntoEstablecido.fromJson', () {
    test('lee nombre, coordenadas y los asociados opcionales', () {
      final p = PuntoEstablecido.fromJson({
        'id': 5,
        'nombre': 'Cruce principal',
        'latitud': -15.65,
        'longitud': -69.98,
        'rutaNombre': 'Ruta FAON',
        'acopiadorNombre': 'Juan Condori',
        'proveedorCodigo': 'DEMO-001',
        'observacion': 'Referencia del puente',
      });

      expect(p.id, 5);
      expect(p.nombre, 'Cruce principal');
      expect(p.latitud, -15.65);
      expect(p.longitud, -69.98);
      expect(p.rutaNombre, 'Ruta FAON');
      expect(p.acopiadorNombre, 'Juan Condori');
      expect(p.proveedorCodigo, 'DEMO-001');
    });

    test('sin acopiador ni proveedor asociado, esos campos quedan nulos', () {
      final p = PuntoEstablecido.fromJson({
        'id': 6,
        'nombre': 'Solo referencia',
        'latitud': -15.66,
        'longitud': -69.99,
      });

      expect(p.acopiadorNombre, isNull);
      expect(p.proveedorCodigo, isNull);
      expect(p.proveedorId, isNull);
      expect(p.tieneProveedor, isFalse);
    });

    // El enlace con la lista de paradas del día depende de estos campos: sin ellos el punto salía
    // anónimo en el mapa aunque su proveedor estuviera en la lista (bug 2026-10-06).
    test('lee rutaId, proveedorId, estado y orden para poder enlazarlo con la lista', () {
      final p = PuntoEstablecido.fromJson({
        'id': 7,
        'nombre': 'Parada del proveedor',
        'latitud': -15.65,
        'longitud': -70.06,
        'rutaId': 1,
        'proveedorId': 6,
        'proveedorCodigo': 'DEMO-006',
        'proveedorEstado': 'ACTIVO',
        'proveedorOrdenEnRuta': 1,
      });

      expect(p.rutaId, 1);
      expect(p.proveedorId, 6);
      expect(p.proveedorEstado, 'ACTIVO');
      expect(p.proveedorOrdenEnRuta, 1);
      expect(p.tieneProveedor, isTrue);
      expect(p.proveedorEntregable, isTrue);
    });

    test('un proveedor pendiente de aprobación no es entregable', () {
      final p = PuntoEstablecido.fromJson({
        'id': 8,
        'nombre': 'Pendiente',
        'latitud': -15.64,
        'longitud': -70.05,
        'proveedorId': 21,
        'proveedorEstado': 'PENDIENTE_APROBACION',
        'proveedorOrdenEnRuta': 1,
      });

      expect(p.tieneProveedor, isTrue);
      expect(p.proveedorEntregable, isFalse);
    });
  });
}
