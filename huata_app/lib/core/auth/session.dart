/// Espejo de `AuthController.LoginResponse` (backend, `org.ramiro.huata.auth`). Los "permisos" son
/// solo para la interfaz (mostrar/ocultar); la autorización real la hace el backend en cada endpoint
/// — igual advertencia que ya deja el propio backend en su javadoc.
class Session {
  Session({
    required this.nombreCompleto,
    required this.username,
    required this.roles,
    required this.permisos,
  });

  final String nombreCompleto;
  final String username;
  final List<String> roles;
  final List<String> permisos;

  factory Session.fromJson(Map<String, dynamic> json) => Session(
    nombreCompleto: json['nombreCompleto'] as String,
    username: json['username'] as String,
    roles: (json['roles'] as List).cast<String>(),
    permisos: (json['permisos'] as List).cast<String>(),
  );

  Map<String, dynamic> toJson() => {
    'nombreCompleto': nombreCompleto,
    'username': username,
    'roles': roles,
    'permisos': permisos,
  };

  bool tieneRol(String rol) => roles.contains(rol);
  bool puede(String permiso) => permisos.contains(permiso);
}

/// Roles con una experiencia móvil propia. El administrador dispone de una vista resumida de
/// usuarios y roles; los demás perfiles de escritorio siguen usando la web.
class RolesMovil {
  RolesMovil._();
  static const administrador = 'ADMINISTRADOR';
  static const acopiador = 'ACOPIADOR';
  static const proveedor = 'PROVEEDOR';
  /// Analista de calidad: hasta V46 era el único rol del testeo en campo.
  static const testeador = 'CALIDAD';
  /// V46: rol propio del testeador; abre la misma experiencia que CALIDAD.
  static const testeadorCampo = 'TESTEADOR';
  static const productorQueso = 'PRODUCTOR_QUESO';

  static const soportados = [administrador, acopiador, proveedor, testeador, testeadorCampo, productorQueso];
}
