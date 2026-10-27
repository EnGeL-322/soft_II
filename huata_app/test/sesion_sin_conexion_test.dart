import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_secure_storage_platform_interface/flutter_secure_storage_platform_interface.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/auth/auth_controller.dart';
import 'package:huata_app/core/auth/session.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

class _StorageEnMemoria extends FlutterSecureStoragePlatform with MockPlatformInterfaceMixin {
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

/// Respuesta fija para cualquier petición: un código HTTP con cuerpo JSON, o "sin red" (null).
class _Red implements HttpClientAdapter {
  _Red(this.status, [this.cuerpo = const {}]);
  final int? status;
  final Map<String, dynamic> cuerpo;

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (status == null) {
      throw DioException.connectionError(requestOptions: options, reason: 'sin señal');
    }
    return ResponseBody.fromString(jsonEncode(cuerpo), status!, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }

  @override
  void close({bool force = false}) {}
}

final _sesionAcopiador = Session(
  nombreCompleto: 'Acopiador Demo',
  username: 'acopiador.demo@huata.pe',
  roles: ['ACOPIADOR'],
  permisos: ['acopio.write'],
);

Future<(AuthController, TokenStore, ApiClient)> _app({bool conSesionGuardada = true}) async {
  FlutterSecureStoragePlatform.instance = _StorageEnMemoria();
  final tokens = TokenStore();
  await tokens.write('jwt-guardado');
  if (conSesionGuardada) await tokens.guardarSesion(_sesionAcopiador);
  final api = ApiClient(tokens);
  return (AuthController(api, tokens), tokens, api);
}

void main() {
  test('sin señal al abrir la app entra con la sesión guardada y no borra el token', () async {
    final (auth, tokens, api) = await _app();
    api.dio.httpClientAdapter = _Red(null);

    await auth.restaurar();

    expect(auth.estado, EstadoSesion.autenticado);
    expect(auth.sinConexion, isTrue);
    expect(auth.sesion?.username, 'acopiador.demo@huata.pe');
    expect(auth.sesion?.puede('acopio.write'), isTrue);
    expect(await tokens.read(), 'jwt-guardado');
  });

  test('si el servidor rechaza el token (401) cierra la sesión aunque haya una guardada', () async {
    final (auth, tokens, api) = await _app();
    api.dio.httpClientAdapter = _Red(401, {'message': 'Token vencido'});

    await auth.restaurar();

    expect(auth.estado, EstadoSesion.noAutenticado);
    expect(await tokens.read(), isNull);
    expect(await tokens.leerSesion(), isNull);
  });

  test('con señal actualiza y guarda los permisos que manda el servidor', () async {
    final (auth, tokens, api) = await _app();
    api.dio.httpClientAdapter = _Red(200, {
      'nombreCompleto': 'Acopiador Demo',
      'username': 'acopiador.demo@huata.pe',
      'roles': ['ACOPIADOR'],
      'permisos': ['acopio.write', 'calidad.aprobar'],
    });

    await auth.restaurar();

    expect(auth.sinConexion, isFalse);
    expect(auth.sesion?.puede('calidad.aprobar'), isTrue);
    expect((await tokens.leerSesion())?.puede('calidad.aprobar'), isTrue);
  });

  test('una instalación anterior sin sesión guardada y sin señal va al login', () async {
    final (auth, _, api) = await _app(conSesionGuardada: false);
    api.dio.httpClientAdapter = _Red(null);

    await auth.restaurar();

    expect(auth.estado, EstadoSesion.noAutenticado);
  });

  test('iniciar sesión sin señal explica que hace falta conexión', () async {
    final (auth, _, api) = await _app(conSesionGuardada: false);
    api.dio.httpClientAdapter = _Red(null);

    final ok = await auth.iniciarSesion('acopiador.demo@huata.pe', 'x');

    expect(ok, isFalse);
    expect(auth.errorLogin, contains('Sin conexión'));
  });

  test('cerrar sesión borra la sesión guardada: sin red ya no se puede volver a entrar', () async {
    final (auth, tokens, _) = await _app();

    await auth.cerrarSesion();

    expect(await tokens.read(), isNull);
    expect(await tokens.leerSesion(), isNull);
  });
}
