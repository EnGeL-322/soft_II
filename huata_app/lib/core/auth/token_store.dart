import 'dart:convert';

import 'package:flutter_secure_storage/flutter_secure_storage.dart';

import 'session.dart';

/// Guarda el JWT fuera de `shared_preferences` (Keychain en iOS, Keystore/EncryptedSharedPreferences
/// en Android): es el mismo criterio de "no exponer el token en texto plano" que ya sigue la web al
/// no persistirlo en una cookie no-httpOnly.
///
/// Junto al token se guarda la última sesión conocida (nombre, roles, permisos): sin ella, abrir la
/// app sin señal no tenía con qué saber quién es el usuario y lo mandaba al login, donde sin conexión
/// no se puede entrar (CLAUDE.md §5: la app debe funcionar offline).
class TokenStore {
  TokenStore() : _storage = const FlutterSecureStorage();

  final FlutterSecureStorage _storage;
  static const _key = 'huata_jwt';
  static const _keySesion = 'huata_sesion';

  Future<String?> read() => _storage.read(key: _key);

  Future<void> write(String token) => _storage.write(key: _key, value: token);

  Future<Session?> leerSesion() async {
    final raw = await _storage.read(key: _keySesion);
    if (raw == null) return null;
    try {
      return Session.fromJson(jsonDecode(raw) as Map<String, dynamic>);
    } catch (_) {
      // Forma antigua o dañada: se trata como si no hubiera sesión guardada.
      return null;
    }
  }

  Future<void> guardarSesion(Session sesion) =>
      _storage.write(key: _keySesion, value: jsonEncode(sesion.toJson()));

  /// Cerrar sesión borra las dos cosas: sin token no hay sesión offline que restaurar.
  Future<void> clear() async {
    await _storage.delete(key: _key);
    await _storage.delete(key: _keySesion);
  }
}
