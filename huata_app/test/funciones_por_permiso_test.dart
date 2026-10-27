import 'package:flutter/material.dart';
import 'package:flutter_secure_storage_platform_interface/flutter_secure_storage_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/auth/auth_controller.dart';
import 'package:huata_app/core/auth/session.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:huata_app/features/shell/menu_otras_funciones.dart';
import 'package:huata_app/features/shell/role_router.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:provider/provider.dart';

class _Storage extends FlutterSecureStoragePlatform with MockPlatformInterfaceMixin {
  final Map<String, String> v = {};
  @override
  Future<bool> containsKey({required String key, required Map<String, String> options}) async => v.containsKey(key);
  @override
  Future<void> delete({required String key, required Map<String, String> options}) async => v.remove(key);
  @override
  Future<void> deleteAll({required Map<String, String> options}) async => v.clear();
  @override
  Future<String?> read({required String key, required Map<String, String> options}) async => v[key];
  @override
  Future<Map<String, String>> readAll({required Map<String, String> options}) async => Map.of(v);
  @override
  Future<void> write({required String key, required String value, required Map<String, String> options}) async =>
      v[key] = value;
}

Session _sesion(List<String> roles, List<String> permisos) =>
    Session(nombreCompleto: 'X', username: 'x@huata.pe', roles: roles, permisos: permisos);

void main() {
  group('funciones por permiso (no solo por rol)', () {
    test('un administrador con todos los permisos puede registrar análisis y ver producción', () {
      final f = funcionesDisponibles(_sesion(
        ['ADMINISTRADOR'],
        ['calidad.write', 'rutas.read', 'proveedores.read', 'produccion.read', 'produccion.write', 'usuarios.read'],
      ));
      expect(f, containsAll([FuncionMovil.testeo, FuncionMovil.produccion, FuncionMovil.usuarios]));
      expect(f, isNot(contains(FuncionMovil.rutaDia)), reason: 'la ruta del día es de una ficha de acopiador');
    });

    test('acopiador que además es testeador tiene las dos experiencias', () {
      final f = funcionesDisponibles(_sesion(
        ['ACOPIADOR', 'TESTEADOR'],
        ['acopio.write', 'calidad.write', 'rutas.read', 'proveedores.read'],
      ));
      expect(f, containsAll([FuncionMovil.rutaDia, FuncionMovil.testeo]));
    });

    test('sin permisos de escritura no ofrece nada', () {
      expect(funcionesDisponibles(_sesion(['GERENCIA'], ['calidad.read', 'produccion.read'])), isEmpty);
    });
  });

  testWidgets('con la sesión guardada por falta de señal se muestra el aviso', (tester) async {
    FlutterSecureStoragePlatform.instance = _Storage();
    final tokens = TokenStore();
    final auth = AuthController(ApiClient(tokens), tokens)
      ..sesion = _sesion(['ACOPIADOR'], [])
      ..sinConexion = true;
    await tester.pumpWidget(ChangeNotifierProvider<AuthController>.value(
      value: auth,
      child: const MaterialApp(home: AvisoSinConexion(child: Scaffold(body: Text('pantalla')))),
    ));
    expect(find.textContaining('Sin conexión'), findsOneWidget);
    expect(find.text('pantalla'), findsOneWidget);

    auth
      ..sinConexion = false
      ..notifyListeners();
    await tester.pump();
    expect(find.textContaining('Sin conexión'), findsNothing);
  });
}
