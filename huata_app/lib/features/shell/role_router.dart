import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/auth/auth_controller.dart';
import '../../core/auth/session.dart';
import '../../core/theme/huata_theme.dart';
import 'app_shell.dart';
import 'modulos_app.dart';

/// Después del login: la barra de módulos que corresponde a los roles y permisos de la sesión.
/// Quien tiene varios roles (acopiador y testeador) ve los módulos de todos, no solo el primero.
class RoleRouter extends StatefulWidget {
  const RoleRouter({super.key});

  @override
  State<RoleRouter> createState() => _RoleRouterState();
}

/// Al volver la app a primer plano se re-leen los permisos: lo concedido o quitado desde la web
/// (p. ej. `calidad.aprobar`) se nota sin cerrar sesión.
class _RoleRouterState extends State<RoleRouter> with WidgetsBindingObserver {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      context.read<AuthController>().refrescarPermisos();
    }
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthController>();
    final sesion = auth.sesion;
    if (sesion == null) return const SizedBox.shrink();
    return AvisoSinConexion(child: _pantallaPrincipal(sesion));
  }

  /// Los módulos salen de los roles y permisos de la sesión (ver `modulos_app.dart`). "Mi cuenta"
  /// está siempre; si es lo único, esta app no tiene nada para el rol y se dice claramente.
  Widget _pantallaPrincipal(Session sesion) {
    final modulos = modulosDisponibles(sesion);
    if (modulos.every((m) => m.id == 'cuenta')) return _RolNoSoportado(sesion: sesion);
    return AppShell(modulos: modulos);
  }
}

/// Franja fija arriba mientras se trabaja con la sesión guardada por falta de señal (la app abrió
/// sin conexión). No hay botón de sincronizar (CLAUDE.md §5): mientras dure, cada minuto se intenta
/// re-validar la sesión, y al lograrlo la franja desaparece sola.
class AvisoSinConexion extends StatefulWidget {
  const AvisoSinConexion({super.key, required this.child});
  final Widget child;

  @override
  State<AvisoSinConexion> createState() => _AvisoSinConexionState();
}

class _AvisoSinConexionState extends State<AvisoSinConexion> {
  Timer? _reintento;

  @override
  void dispose() {
    _reintento?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final auth = context.watch<AuthController>();
    if (!auth.sinConexion) {
      _reintento?.cancel();
      _reintento = null;
      return widget.child;
    }
    _reintento ??= Timer.periodic(const Duration(minutes: 1), (_) => auth.refrescarPermisos());
    return Column(
      children: [
        Material(
          color: HuataColors.amberSoft,
          child: SafeArea(
            bottom: false,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
              child: Row(
                children: const [
                  Icon(Icons.cloud_off_outlined, size: 18, color: HuataColors.amber),
                  SizedBox(width: 8),
                  Expanded(
                    child: Text(
                      'Sin conexión: trabajas con tu última sesión. Lo que registres se enviará solo '
                      'al volver la señal.',
                      style: TextStyle(fontSize: 13, color: HuataColors.amber),
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
        Expanded(child: MediaQuery.removePadding(context: context, removeTop: true, child: widget.child)),
      ],
    );
  }
}

class _RolNoSoportado extends StatelessWidget {
  const _RolNoSoportado({required this.sesion});
  final Session sesion;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.info_outline, size: 48, color: HuataColors.muted),
                const SizedBox(height: 16),
                Text(
                  'Tu rol (${sesion.roles.join(", ")}) no tiene una experiencia en esta app móvil.',
                  textAlign: TextAlign.center,
                ),
                const SizedBox(height: 8),
                const Text(
                  'Esta app cubre Acopiador, Proveedor, Testeador, Calidad y Producción. '
                  'Para tu rol, usa la web de Huata Lácteos.',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: HuataColors.muted),
                ),
                const SizedBox(height: 20),
                OutlinedButton(
                  onPressed: () => context.read<AuthController>().cerrarSesion(),
                  child: const Text('Cerrar sesión'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
