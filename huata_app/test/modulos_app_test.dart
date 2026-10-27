import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/actualizacion/actualizacion_service.dart';
import 'package:huata_app/core/auth/session.dart';
import 'package:huata_app/features/shell/modulos_app.dart';

Session _sesion(List<String> roles, List<String> permisos) =>
    Session(nombreCompleto: 'X', username: 'x@huata.pe', roles: roles, permisos: permisos);

List<String> _ids(Session s) => modulosDisponibles(s).map((m) => m.id).toList();

void main() {
  group('módulos de la barra según roles y permisos', () {
    test('el proveedor ve su inicio, pagos, historial, cambio de zona y su cuenta', () {
      expect(_ids(_sesion(['PROVEEDOR'], [])), ['resumen', 'pagos', 'historial', 'zona', 'cuenta']);
    });

    test('quien tiene dos roles ve los módulos de ambos, no solo el primero', () {
      final ids = _ids(_sesion(['ACOPIADOR', 'TESTEADOR'], ['acopio.write']));
      expect(ids, containsAllInOrder(['ruta', 'testeo', 'cuenta']));
    });

    test('aprobar análisis aparece por el permiso, sea cual sea el rol', () {
      expect(_ids(_sesion(['ADMINISTRADOR'], ['calidad.aprobar'])), contains('aprobar'));
      expect(_ids(_sesion(['ADMINISTRADOR'], ['usuarios.write'])), isNot(contains('aprobar')));
    });

    test('cada vista de la web asignada aparece en la app, con su mismo permiso', () {
      expect(_ids(_sesion(['GERENCIA'], ['calidad.read'])), ['calidad', 'cuenta']);
      expect(
        _ids(_sesion(['SUPERVISOR_PLANTA'], ['panel.read', 'acopio.read'])),
        ['panel', 'recojos', 'recepcion', 'cuenta'],
      );
    });

    test('sin ninguna vista, solo queda "Mi cuenta"', () {
      expect(_ids(_sesion(['GERENCIA'], [])), ['cuenta']);
    });

    test('las lecturas que el acopiador necesita para su ruta no le abren vistas de oficina', () {
      final ids = _ids(_sesion(['ACOPIADOR'], ['panel.read', 'acopio.read', 'rutas.read', 'proveedores.read', 'acopio.write']));
      expect(ids, ['ruta', 'cuenta']);
    });

    test('si además tiene un rol de oficina, ve esas vistas', () {
      final ids = _ids(_sesion(['ACOPIADOR', 'SUPERVISOR_PLANTA'], ['panel.read', 'acopio.read']));
      expect(ids, containsAll(['ruta', 'panel', 'recojos']));
    });

    test('producción se ve con solo "ver producción", como en la web', () {
      expect(_ids(_sesion(['GERENCIA'], ['produccion.read'])), contains('produccion'));
    });

    test('"Mi cuenta" va siempre al final', () {
      final ids = _ids(_sesion(['ADMINISTRADOR'], ['calidad.aprobar', 'produccion.read', 'produccion.write']));
      expect(ids.last, 'cuenta');
    });
  });

  group('actualizaciones del APK', () {
    test('compara versiones número a número e ignora el build', () {
      expect(compararVersiones('1.2.10', '1.2.9'), greaterThan(0));
      expect(compararVersiones('1.0.0+7', '1.0.0'), 0);
      expect(compararVersiones('1.0', '1.0.1'), lessThan(0));
    });

    test('por debajo de la mínima es obligatoria; por debajo de la actual, disponible', () {
      const pub = VersionPublicada(versionActual: '1.4.0', versionMinima: '1.2.0', urlDescarga: 'https://x/huata.apk');
      expect(evaluarActualizacion('1.1.9', pub), EstadoActualizacion.obligatoria);
      expect(evaluarActualizacion('1.3.0', pub), EstadoActualizacion.disponible);
      expect(evaluarActualizacion('1.4.0+3', pub), EstadoActualizacion.alDia);
    });

    test('sin URL de descarga no se pide actualizar (no habría de dónde)', () {
      const pub = VersionPublicada(versionActual: '9.0.0', versionMinima: '9.0.0');
      expect(evaluarActualizacion('1.0.0', pub), EstadoActualizacion.alDia);
    });
  });
}
