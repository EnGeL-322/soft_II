import 'package:dio/dio.dart';

import '../auth/token_store.dart';
import 'api_config.dart';

/// Un solo Dio para toda la app, igual que el `HttpClient` + interceptor de la web
/// (`auth.interceptor.ts`): agrega el `Authorization: Bearer` en cada request y avisa con
/// [onNoAutenticado] cuando el backend responde 401, para que la sesión se cierre desde un solo
/// lugar en vez de que cada pantalla tenga que revisarlo.
class ApiClient {
  ApiClient(this._tokenStore) {
    dio = Dio(BaseOptions(baseUrl: ApiConfig.baseUrl, connectTimeout: const Duration(seconds: 15)));
    dio.interceptors.add(
      InterceptorsWrapper(
        onRequest: (options, handler) async {
          final token = await _tokenStore.read();
          if (token != null) {
            options.headers['Authorization'] = 'Bearer $token';
          }
          handler.next(options);
        },
        onError: (error, handler) {
          if (error.response?.statusCode == 401) {
            onNoAutenticado?.call();
          }
          handler.next(error);
        },
      ),
    );
  }

  late final Dio dio;
  final TokenStore _tokenStore;

  /// Lo fija `AuthController` al arrancar la app: cierra la sesión local cuando el token ya venció
  /// o el backend lo rechazó, sin que `ApiClient` necesite conocer `AuthController` (evita el ciclo).
  void Function()? onNoAutenticado;
}
