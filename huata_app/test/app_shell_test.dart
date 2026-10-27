import 'package:flutter/material.dart';
import 'package:flutter_secure_storage_platform_interface/flutter_secure_storage_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/actualizacion/actualizacion_service.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:huata_app/features/shell/app_shell.dart';
import 'package:huata_app/features/shell/modulos_app.dart';
import 'package:huata_app/shared/huata_scaffold.dart';
import 'package:provider/provider.dart';

class _Storage extends FlutterSecureStoragePlatform {
  @override
  Future<bool> containsKey({required String key, required Map<String, String> options}) async => false;
  @override
  Future<void> delete({required String key, required Map<String, String> options}) async {}
  @override
  Future<void> deleteAll({required Map<String, String> options}) async {}
  @override
  Future<String?> read({required String key, required Map<String, String> options}) async => null;
  @override
  Future<Map<String, String>> readAll({required Map<String, String> options}) async => {};
  @override
  Future<void> write({required String key, required String value, required Map<String, String> options}) async {}
}

/// Módulos de prueba: la pantalla solo dice su nombre y cuenta cuántas veces se construyó.
final _construcciones = <String, int>{};
List<ModuloApp> _modulos(int n) => [
  for (var i = 0; i < n; i++)
    ModuloApp(
      id: 'm$i',
      titulo: 'Módulo $i',
      icono: Icons.circle_outlined,
      iconoActivo: Icons.circle,
      pantalla: () {
        _construcciones.update('m$i', (v) => v + 1, ifAbsent: () => 1);
        return Center(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Text('pantalla $i'),
            Builder(
              builder: (ctx) => TextButton(onPressed: () => NavegacionModulos.de(ctx)!.ir('m2'), child: Text('ir a 2 desde $i')),
            ),
          ]),
        );
      },
      visible: (_) => true,
    ),
];

Future<void> _montar(WidgetTester tester, int n, Size tamano) async {
  FlutterSecureStoragePlatform.instance = _Storage();
  tester.view.physicalSize = tamano;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  _construcciones.clear();
  await tester.pumpWidget(ChangeNotifierProvider<ActualizacionService>(
    create: (_) => ActualizacionService(ApiClient(TokenStore()), versionInstalada: () async => '1.0.0'),
    child: MaterialApp(home: AppShell(modulos: _modulos(n))),
  ));
}

void main() {
  testWidgets('en el teléfono los módulos van en la barra inferior', (tester) async {
    await _montar(tester, 3, const Size(400, 800));
    expect(find.byType(NavigationBar), findsOneWidget);
    expect(find.text('pantalla 0'), findsOneWidget);
    await tester.tap(find.text('Módulo 2'));
    await tester.pumpAndSettle();
    expect(find.text('pantalla 2'), findsOneWidget);
  });

  testWidgets('solo se construye lo que se abre, y una sola vez', (tester) async {
    await _montar(tester, 3, const Size(400, 800));
    expect(_construcciones, {'m0': 1});
    await tester.tap(find.text('Módulo 1'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Módulo 0'));
    await tester.pumpAndSettle();
    expect(_construcciones, {'m0': 1, 'm1': 1});
  });

  testWidgets('con más de 5 módulos, los que no caben van en "Más"', (tester) async {
    await _montar(tester, 7, const Size(400, 800));
    expect(find.text('Más'), findsOneWidget);
    await tester.tap(find.text('Más'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Módulo 6'));
    await tester.pumpAndSettle();
    expect(find.text('pantalla 6'), findsOneWidget);
  });

  testWidgets('en pantalla ancha usa la barra lateral', (tester) async {
    await _montar(tester, 3, const Size(1200, 800));
    expect(find.byType(NavigationBar), findsNothing);
    expect(find.text('Huata Lácteos'), findsOneWidget);
    expect(find.text('Módulo 1'), findsOneWidget);
  });

  testWidgets('una pantalla puede abrir otro módulo de la barra (atajos de Inicio)', (tester) async {
    await _montar(tester, 3, const Size(400, 800));
    await tester.tap(find.text('ir a 2 desde 0'));
    await tester.pumpAndSettle();
    expect(find.text('pantalla 2'), findsOneWidget);
  });
}
