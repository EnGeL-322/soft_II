// Smoke test: sin sesión guardada, la app debe arrancar mostrando el login (no el "Hello World"
// que genera `flutter create` y que este proyecto nunca tuvo).
import 'package:flutter_secure_storage_platform_interface/flutter_secure_storage_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';
import 'package:provider/provider.dart';

import 'package:huata_app/core/actualizacion/actualizacion_service.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/auth/auth_controller.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:huata_app/features/auth/login_screen.dart';
import 'package:huata_app/main.dart';

class _StorageEnMemoriaFalsa extends FlutterSecureStoragePlatform with MockPlatformInterfaceMixin {
  final Map<String, String> _valores = {};

  @override
  Future<void> write({required String key, required String value, required Map<String, String> options}) async {
    _valores[key] = value;
  }

  @override
  Future<String?> read({required String key, required Map<String, String> options}) async => _valores[key];

  @override
  Future<bool> containsKey({required String key, required Map<String, String> options}) async =>
      _valores.containsKey(key);

  @override
  Future<void> delete({required String key, required Map<String, String> options}) async {
    _valores.remove(key);
  }

  @override
  Future<void> deleteAll({required Map<String, String> options}) async => _valores.clear();

  @override
  Future<Map<String, String>> readAll({required Map<String, String> options}) async => Map.of(_valores);
}

void main() {
  testWidgets('sin token guardado, arranca en la pantalla de login', (WidgetTester tester) async {
    FlutterSecureStoragePlatform.instance = _StorageEnMemoriaFalsa();
    final tokenStore = TokenStore();
    final apiClient = ApiClient(tokenStore);

    await tester.pumpWidget(
      MultiProvider(
        providers: [
          ChangeNotifierProvider<AuthController>(create: (_) => AuthController(apiClient, tokenStore)..restaurar()),
          // Sin iniciar(): la prueba no consulta al servidor si hay versión nueva.
          ChangeNotifierProvider<ActualizacionService>(
            create: (_) => ActualizacionService(apiClient, versionInstalada: () async => '1.0.0'),
          ),
        ],
        child: const HuataApp(),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.byType(LoginScreen), findsOneWidget);
    expect(find.text('Ingresar'), findsOneWidget);
  });
}
