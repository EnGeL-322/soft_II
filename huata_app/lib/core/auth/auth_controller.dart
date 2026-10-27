import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart';

import '../api/api_client.dart';
import '../api/api_exception.dart';
import 'session.dart';
import 'token_store.dart';

enum EstadoSesion { cargando, autenticado, noAutenticado }

/// Estado de sesión único para toda la app (equivalente a `auth.service.ts` en la web): login,
/// restaurar sesión al abrir la app, y cerrar sesión cuando el `ApiClient` detecta un 401.
class AuthController extends ChangeNotifier {
  AuthController(this._apiClient, this._tokenStore) {
    _apiClient.onNoAutenticado = cerrarSesion;
  }

  final ApiClient _apiClient;
  final TokenStore _tokenStore;

  EstadoSesion estado = EstadoSesion.cargando;
  Session? sesion;
  String? errorLogin;

  /// true si se entró con la sesión guardada porque no había red. Se apaga en cuanto `/auth/me`
  /// responde (al volver a primer plano con señal, ver [refrescarPermisos]).
  bool sinConexion = false;

  /// Se llama una vez al arrancar `main()`: si hay un token guardado, lo valida contra `/auth/me`
  /// en vez de asumir que sigue vigente (puede haber expirado, o el usuario haber sido desactivado).
  ///
  /// Solo un rechazo del servidor (401/403) cierra la sesión. Sin señal, tiempo agotado o servidor
  /// caído se entra con la última sesión guardada: antes cualquier error borraba el token y el
  /// acopiador que abría la app en ruta sin cobertura quedaba fuera, sin poder registrar nada.
  Future<void> restaurar() async {
    final token = await _tokenStore.read();
    if (token == null) {
      estado = EstadoSesion.noAutenticado;
      notifyListeners();
      return;
    }
    try {
      final respuesta = await _apiClient.dio.get('/auth/me');
      sesion = Session.fromJson(respuesta.data as Map<String, dynamic>);
      await _tokenStore.guardarSesion(sesion!);
      sinConexion = false;
      estado = EstadoSesion.autenticado;
    } on DioException catch (e) {
      final guardada = await _tokenStore.leerSesion();
      if (_esRechazo(e) || guardada == null) {
        // Rechazado, o una instalación anterior que aún no guardó su sesión: no hay con qué entrar.
        await _tokenStore.clear();
        sesion = null;
        estado = EstadoSesion.noAutenticado;
      } else {
        sesion = guardada;
        sinConexion = true;
        estado = EstadoSesion.autenticado;
      }
    }
    notifyListeners();
  }

  /// El servidor respondió y dijo que no: token vencido o inválido, o usuario desactivado.
  static bool _esRechazo(DioException e) {
    final status = e.response?.statusCode;
    return status == 401 || status == 403;
  }

  Future<bool> iniciarSesion(String username, String password) async {
    errorLogin = null;
    notifyListeners();
    try {
      final respuesta = await _apiClient.dio.post(
        '/auth/login',
        data: {'username': username, 'password': password},
      );
      final datos = respuesta.data as Map<String, dynamic>;
      await _tokenStore.write(datos['token'] as String);
      sesion = Session.fromJson(datos);
      await _tokenStore.guardarSesion(sesion!);
      sinConexion = false;
      estado = EstadoSesion.autenticado;
      notifyListeners();
      return true;
    } on DioException catch (e) {
      // Iniciar sesión sí necesita al servidor (es quien verifica la contraseña). Después de entrar
      // una vez, la app vuelve a abrir sin señal con la sesión guardada; solo cerrar sesión la borra.
      errorLogin = e.response == null
          ? 'Sin conexión: inicia sesión cuando tengas señal. Si ya habías entrado en este celular '
              'y no cerraste sesión, la app abre sin señal.'
          : ApiException.from(e).message;
      estado = EstadoSesion.noAutenticado;
      notifyListeners();
      return false;
    }
  }

  /// Re-lee roles y permisos con la sesión ya abierta (la app vuelve a primer plano). Así un permiso
  /// concedido o quitado desde la web se nota sin cerrar sesión. Sin red no cambia nada: se conserva
  /// la sesión tal cual (la app debe seguir funcionando offline); un 401 lo resuelve `ApiClient`.
  Future<void> refrescarPermisos() async {
    if (sesion == null) return;
    try {
      final respuesta = await _apiClient.dio.get('/auth/me');
      sesion = Session.fromJson(respuesta.data as Map<String, dynamic>);
      await _tokenStore.guardarSesion(sesion!);
      sinConexion = false;
      notifyListeners();
    } on DioException {
      // Sin conexión o backend caído: se sigue con los permisos que ya se tenían.
    }
  }

  /// La persona cambió su usuario desde "Mi cuenta": el servidor entregó otro token (el anterior ya no
  /// vale porque su usuario no existe). Se guarda y se sigue trabajando, sin pasar por el login.
  Future<void> aplicarSesionNueva(Map<String, dynamic> datos) async {
    await _tokenStore.write(datos['token'] as String);
    sesion = Session.fromJson(datos);
    await _tokenStore.guardarSesion(sesion!);
    sinConexion = false;
    estado = EstadoSesion.autenticado;
    notifyListeners();
  }

  Future<void> cerrarSesion() async {
    await _tokenStore.clear();
    sesion = null;
    sinConexion = false;
    estado = EstadoSesion.noAutenticado;
    notifyListeners();
  }
}
