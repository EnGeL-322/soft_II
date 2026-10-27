import 'package:flutter/material.dart';
import 'package:flutter_secure_storage_platform_interface/flutter_secure_storage_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/api/cuenta_api.dart';
import 'package:huata_app/core/auth/auth_controller.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:huata_app/features/cuenta/hojas_cuenta.dart';
import 'package:provider/provider.dart';

class _Storage extends FlutterSecureStoragePlatform {
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

/// Monta una hoja con sus dependencias; las peticiones no llegan a salir porque la validación falla antes.
Future<void> _montar(WidgetTester tester, Widget hoja) async {
  FlutterSecureStoragePlatform.instance = _Storage();
  final tokens = TokenStore();
  final api = ApiClient(tokens);
  await tester.pumpWidget(MultiProvider(
    providers: [
      Provider<CuentaApi>.value(value: CuentaApi(api)),
      ChangeNotifierProvider<AuthController>(create: (_) => AuthController(api, tokens)),
    ],
    child: MaterialApp(home: Scaffold(body: SingleChildScrollView(child: hoja))),
  ));
}

void main() {
  group('fuerza de la contraseña', () {
    test('por debajo del mínimo siempre es débil', () {
      expect(medirClave(''), FuerzaClave.vacia);
      expect(medirClave('Ab1!'), FuerzaClave.debil);
    });
    test('mezclar tipos de caracteres la sube', () {
      expect(medirClave('abcdefgh'), FuerzaClave.debil);
      expect(medirClave('abcdefg1'), FuerzaClave.media);
      expect(medirClave('Abcdef1!'), FuerzaClave.fuerte);
    });
  });

  testWidgets('cambiar contraseña: valida mínimo y que la confirmación coincida', (tester) async {
    await _montar(tester, const HojaCambiarClave());
    final campos = find.byType(TextFormField);
    await tester.enterText(campos.at(0), 'actual123');
    await tester.enterText(campos.at(1), 'corta');
    await tester.enterText(campos.at(2), 'otra');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Cambiar contraseña'));
    await tester.pump();
    expect(find.text('Mínimo ${CuentaApi.claveMinima} caracteres'), findsOneWidget);
    expect(find.text('No coincide con la nueva contraseña'), findsOneWidget);
  });

  testWidgets('cambiar contraseña: la nueva no puede ser la actual', (tester) async {
    await _montar(tester, const HojaCambiarClave());
    final campos = find.byType(TextFormField);
    await tester.enterText(campos.at(0), 'MismaClave1');
    await tester.enterText(campos.at(1), 'MismaClave1');
    await tester.enterText(campos.at(2), 'MismaClave1');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Cambiar contraseña'));
    await tester.pump();
    expect(find.text('Debe ser distinta de la actual'), findsOneWidget);
  });

  testWidgets('cambiar usuario: no acepta espacios ni el mismo usuario', (tester) async {
    await _montar(tester, const HojaCambiarUsuario(actual: 'ana@huata.pe'));
    final campos = find.byType(TextFormField);
    await tester.enterText(campos.at(0), 'con espacio');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Cambiar usuario'));
    await tester.pump();
    expect(find.text('Entre 3 y 100 caracteres, sin espacios'), findsOneWidget);

    await tester.enterText(campos.at(0), 'ANA@huata.pe');
    await tester.tap(find.widgetWithText(ElevatedButton, 'Cambiar usuario'));
    await tester.pump();
    expect(find.text('Ese ya es tu usuario'), findsOneWidget);
  });

  testWidgets('el proveedor ve su nombre bloqueado', (tester) async {
    await _montar(
      tester,
      const HojaDatosPersonales(
        cuenta: MiCuenta(nombreCompleto: 'Juan Pérez', username: 'juan', puedeEditarNombre: false),
      ),
    );
    final nombre = tester.widget<TextFormField>(find.byType(TextFormField).first);
    expect(nombre.enabled, isFalse);
    expect(find.textContaining('lo corrige la planta'), findsOneWidget);
  });

  test('tras cambiar el usuario, la sesión nueva queda guardada sin pasar por el login', () async {
    FlutterSecureStoragePlatform.instance = _Storage();
    final tokens = TokenStore();
    final auth = AuthController(ApiClient(tokens), tokens);
    await auth.aplicarSesionNueva({
      'token': 'nuevo-token',
      'nombreCompleto': 'Ana',
      'username': 'ana.nueva',
      'roles': ['CALIDAD'],
      'permisos': <String>[],
    });
    expect(await tokens.read(), 'nuevo-token');
    expect((await tokens.leerSesion())?.username, 'ana.nueva');
    expect(auth.estado, EstadoSesion.autenticado);
  });
}
