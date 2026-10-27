import 'api_client.dart';

/// Mis datos de cuenta (`GET /api/v1/cuenta`).
class MiCuenta {
  const MiCuenta({
    required this.nombreCompleto,
    required this.username,
    this.email,
    this.telefono,
    this.roles = const [],
    this.ultimoAcceso,
    this.puedeEditarNombre = true,
  });

  final String nombreCompleto;
  final String username;
  final String? email;
  final String? telefono;
  final List<String> roles;
  final DateTime? ultimoAcceso;

  /// Proveedor y acopiador no: su nombre identifica sus pagos y lo corrige la planta en su ficha.
  final bool puedeEditarNombre;

  factory MiCuenta.fromJson(Map<String, dynamic> j) => MiCuenta(
    nombreCompleto: j['nombreCompleto'] as String,
    username: j['username'] as String,
    email: j['email'] as String?,
    telefono: j['telefono'] as String?,
    roles: ((j['roles'] as List?) ?? const []).cast<String>(),
    ultimoAcceso: j['ultimoAcceso'] == null ? null : DateTime.tryParse(j['ultimoAcceso'] as String),
    puedeEditarNombre: j['puedeEditarNombre'] as bool? ?? true,
  );
}

/// `/api/v1/cuenta`: lo que cada persona cambia de su propia cuenta. Siempre la del token.
///
/// No se encola sin señal (a diferencia de recojos y ubicaciones): cambiar la contraseña o el
/// usuario necesita que el servidor confirme la contraseña actual en ese momento.
class CuentaApi {
  CuentaApi(this._client);
  final ApiClient _client;

  /// Mínimo de caracteres de la contraseña; el backend aplica la misma regla.
  static const claveMinima = 8;

  Future<MiCuenta> obtener() async {
    final r = await _client.dio.get('/cuenta');
    return MiCuenta.fromJson(r.data as Map<String, dynamic>);
  }

  Future<MiCuenta> actualizarDatos({String? nombreCompleto, String? email, String? telefono}) async {
    final r = await _client.dio.put('/cuenta/datos', data: {
      'nombreCompleto': nombreCompleto,
      'email': email,
      'telefono': telefono,
    });
    return MiCuenta.fromJson(r.data as Map<String, dynamic>);
  }

  Future<void> cambiarClave({required String actual, required String nueva}) =>
      _client.dio.put('/cuenta/clave', data: {'actual': actual, 'nueva': nueva});

  /// Devuelve la sesión nueva (token incluido): el token anterior deja de valer.
  Future<Map<String, dynamic>> cambiarUsuario({required String clave, required String nuevoUsuario}) async {
    final r = await _client.dio.put('/cuenta/usuario', data: {'clave': clave, 'nuevoUsuario': nuevoUsuario});
    return r.data as Map<String, dynamic>;
  }
}

/// Qué tan fuerte es una contraseña, para orientar mientras se escribe (la regla dura es el mínimo).
enum FuerzaClave { vacia, debil, media, fuerte }

FuerzaClave medirClave(String clave) {
  if (clave.isEmpty) return FuerzaClave.vacia;
  if (clave.length < CuentaApi.claveMinima) return FuerzaClave.debil;
  var puntos = 0;
  if (RegExp(r'[a-z]').hasMatch(clave) && RegExp(r'[A-Z]').hasMatch(clave)) puntos++;
  if (RegExp(r'\d').hasMatch(clave)) puntos++;
  if (RegExp(r'[^A-Za-z0-9]').hasMatch(clave)) puntos++;
  if (clave.length >= 12) puntos++;
  return puntos >= 3 ? FuerzaClave.fuerte : puntos >= 1 ? FuerzaClave.media : FuerzaClave.debil;
}
