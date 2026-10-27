import '../../core/auth/session.dart';

/// Funciones que se conceden por permiso además del rol (ver `modulos_app.dart`).
enum FuncionMovil { testeo, produccion, rutaDia, usuarios }

/// Lo que puede abrir esta sesión por sus permisos, no solo por su rol.
///
/// Quien tiene más de un rol (acopiador y testeador) o permisos concedidos aparte (un administrador
/// con calidad.write) ve esas funciones como módulos de la barra de navegación. Se reutilizan las
/// mismas pantallas: no hay copias por rol.
List<FuncionMovil> funcionesDisponibles(Session sesion) => [
      // Registrar análisis: la pantalla del testeador lista rutas y proveedores para elegir a quién.
      if (sesion.puede('calidad.write') && sesion.puede('rutas.read') && sesion.puede('proveedores.read'))
        FuncionMovil.testeo,
      if (sesion.puede('produccion.read') && sesion.puede('produccion.write')) FuncionMovil.produccion,
      // La ruta del día es la del acopiador que inició sesión (su ficha y su asignación), no basta un permiso.
      if (sesion.tieneRol(RolesMovil.acopiador)) FuncionMovil.rutaDia,
      if (sesion.puede('usuarios.read')) FuncionMovil.usuarios,
    ];
