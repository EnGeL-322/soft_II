import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_controller.dart';
import '../core/auth/session.dart';
import '../core/theme/huata_theme.dart';

/// Barra de marca + menú de cuenta compartidos por todas las experiencias, para no repetirlos y para
/// que "salir" se comporte siempre igual. La barra es verde Huata con el nombre de quien usa la app:
/// el cliente pidió que se vea profesional y que cada persona sepa en qué cuenta está.
class HuataScaffold extends StatelessWidget {
  const HuataScaffold({
    super.key,
    required this.titulo,
    required this.body,
    this.actions,
    this.floatingActionButton,
    this.anchoCompleto = false,
  });

  final String titulo;
  final Widget body;
  final List<Widget>? actions;
  final Widget? floatingActionButton;

  /// Mapas y pantallas que deben ocupar todo el ancho. El resto se centra con un ancho máximo para
  /// que en tableta o navegador no se estire de borde a borde.
  final bool anchoCompleto;

  @override
  Widget build(BuildContext context) {
    final sesion = context.read<AuthController>().sesion;
    final puedeVolver = Navigator.of(context).canPop();
    return Scaffold(
      appBar: AppBar(
        toolbarHeight: 64,
        foregroundColor: Colors.white,
        backgroundColor: HuataMarca.verdeProfundo,
        iconTheme: const IconThemeData(color: Colors.white),
        actionsIconTheme: const IconThemeData(color: Colors.white),
        flexibleSpace: const DecoratedBox(decoration: BoxDecoration(gradient: HuataMarca.degradado)),
        titleSpacing: puedeVolver ? 0 : 16,
        title: Row(
          children: [
            if (!puedeVolver && !ConBarraLateral.de(context)) ...[const LogoHuata(tamano: 36), const SizedBox(width: 12)],
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisSize: MainAxisSize.min,
                children: [
                  Text(
                    titulo,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700, color: Colors.white),
                  ),
                  if (sesion != null)
                    Text(
                      '${sesion.nombreCompleto} · ${etiquetaRol(sesion)}',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12, color: Color(0xCCFFFFFF), fontWeight: FontWeight.w400),
                    ),
                ],
              ),
            ),
          ],
        ),
        actions: [
          ...?actions,
          _MenuCuenta(sesion: sesion),
          const SizedBox(width: 8),
        ],
      ),
      // SafeArea: en teléfonos con muesca/cámara perforada o barra de gestos inferior, el
      // contenido no debe quedar tapado por el recorte de pantalla. El AppBar ya resuelve el
      // borde superior por su cuenta; esto cubre el resto (abajo, y los costados en horizontal).
      body: SafeArea(
        child: anchoCompleto
            ? body
            : Align(
                alignment: Alignment.topCenter,
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: HuataMarca.anchoContenido),
                  child: body,
                ),
              ),
      ),
      floatingActionButton: floatingActionButton,
    );
  }
}

/// Lo pone `AppShell` cuando muestra la barra lateral, que ya lleva el logo: la barra superior no
/// lo repite.
class ConBarraLateral extends InheritedWidget {
  const ConBarraLateral({super.key, required super.child});

  static bool de(BuildContext context) => context.dependOnInheritedWidgetOfExactType<ConBarraLateral>() != null;

  @override
  bool updateShouldNotify(ConBarraLateral oldWidget) => false;
}

/// Lo pone `AppShell` para que una pantalla abra otro módulo de la barra (p. ej. Inicio → Pagos)
/// sin duplicarlo con un `Navigator.push`. Fuera del shell no existe y los atajos no hacen nada.
class NavegacionModulos extends InheritedWidget {
  const NavegacionModulos({super.key, required this.ir, required this.disponibles, required super.child});

  /// Abre el módulo con ese id, si la sesión lo tiene.
  final ValueChanged<String> ir;
  final Set<String> disponibles;

  static NavegacionModulos? de(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<NavegacionModulos>();

  @override
  bool updateShouldNotify(NavegacionModulos oldWidget) => !setEquals(disponibles, oldWidget.disponibles);
}

/// Nombre legible del rol principal para la barra ("Acopiador", no "ACOPIADOR").
String etiquetaRol(Session sesion) {
  const etiquetas = {
    RolesMovil.administrador: 'Administrador',
    RolesMovil.acopiador: 'Acopiador',
    RolesMovil.proveedor: 'Proveedor',
    RolesMovil.testeador: 'Calidad',
    RolesMovil.testeadorCampo: 'Testeador',
    RolesMovil.productorQueso: 'Producción',
  };
  for (final rol in RolesMovil.soportados) {
    if (sesion.tieneRol(rol)) return etiquetas[rol]!;
  }
  return sesion.roles.isEmpty ? 'Usuario' : _capitalizar(sesion.roles.first);
}

String _capitalizar(String rol) {
  final t = rol.replaceAll('_', ' ').toLowerCase();
  return t.isEmpty ? t : t[0].toUpperCase() + t.substring(1);
}

String iniciales(String nombre) {
  final partes = nombre.trim().split(RegExp(r'\s+')).where((p) => p.isNotEmpty).toList();
  if (partes.isEmpty) return '?';
  final dos = partes.length == 1 ? partes.first.substring(0, 1) : partes[0][0] + partes[1][0];
  return dos.toUpperCase();
}

class _MenuCuenta extends StatelessWidget {
  const _MenuCuenta({required this.sesion});
  final Session? sesion;

  @override
  Widget build(BuildContext context) {
    final s = sesion;
    return PopupMenuButton<String>(
      tooltip: 'Cerrar sesión',
      offset: const Offset(0, 52),
      onSelected: (_) => context.read<AuthController>().cerrarSesion(),
      itemBuilder: (_) => [
        if (s != null)
          PopupMenuItem<String>(
            enabled: false,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(s.nombreCompleto,
                    style: const TextStyle(fontWeight: FontWeight.w700, color: HuataColors.text, fontSize: 14.5)),
                const SizedBox(height: 2),
                Text(s.username, style: const TextStyle(color: HuataColors.muted, fontSize: 12.5)),
              ],
            ),
          ),
        if (s != null) const PopupMenuDivider(),
        const PopupMenuItem<String>(
          value: 'salir',
          child: Row(
            children: [
              Icon(Icons.logout, size: 20, color: HuataColors.red),
              SizedBox(width: 12),
              Text('Cerrar sesión', style: TextStyle(color: HuataColors.red, fontWeight: FontWeight.w600)),
            ],
          ),
        ),
      ],
      child: Padding(
        padding: const EdgeInsets.all(6),
        child: CircleAvatar(
          radius: 17,
          backgroundColor: const Color(0x33FFFFFF),
          child: Text(
            s == null ? '?' : iniciales(s.nombreCompleto),
            style: const TextStyle(color: Colors.white, fontSize: 13, fontWeight: FontWeight.w700),
          ),
        ),
      ),
    );
  }
}

/// Marca Huata: monograma en un cuadrado redondeado. Sobre fondo verde (barra, login).
class LogoHuata extends StatelessWidget {
  const LogoHuata({super.key, this.tamano = 40, this.claro = true});
  final double tamano;

  /// true = sobre fondo oscuro (blanco translúcido); false = sobre fondo claro (verde sólido).
  final bool claro;

  @override
  Widget build(BuildContext context) {
    return Container(
      width: tamano,
      height: tamano,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: claro ? const Color(0x26FFFFFF) : HuataColors.green,
        borderRadius: BorderRadius.circular(tamano * 0.28),
        border: claro ? Border.all(color: const Color(0x40FFFFFF)) : null,
      ),
      child: Icon(Icons.water_drop_rounded, color: Colors.white, size: tamano * 0.55),
    );
  }
}

/// Tarjeta de sección reutilizable (título + contenido), mismo look que `.card` en la web.
class SeccionCard extends StatelessWidget {
  const SeccionCard({super.key, this.titulo, this.subtitulo, required this.child, this.padding, this.icono});

  final String? titulo;
  final String? subtitulo;
  final Widget child;
  final EdgeInsets? padding;
  final IconData? icono;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: HuataColors.panel,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(color: const Color(0xFFE8EDF3)),
        boxShadow: HuataMarca.sombra,
      ),
      child: Padding(
        padding: padding ?? const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            if (titulo != null)
              Row(
                children: [
                  if (icono != null) ...[
                    Container(
                      width: 32,
                      height: 32,
                      decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(9)),
                      child: Icon(icono, size: 18, color: HuataColors.green),
                    ),
                    const SizedBox(width: 10),
                  ],
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(titulo!, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 16, letterSpacing: -0.1)),
                        if (subtitulo != null)
                          Padding(
                            padding: const EdgeInsets.only(top: 2),
                            child: Text(subtitulo!, style: const TextStyle(color: HuataColors.muted, fontSize: 13)),
                          ),
                      ],
                    ),
                  ),
                ],
              )
            else if (subtitulo != null)
              Text(subtitulo!, style: const TextStyle(color: HuataColors.muted, fontSize: 13)),
            if (titulo != null || subtitulo != null) const SizedBox(height: 14),
            child,
          ],
        ),
      ),
    );
  }
}

/// Cifra destacada (litros, soles, cantidades): etiqueta pequeña arriba y número grande.
class DatoClave extends StatelessWidget {
  const DatoClave({super.key, required this.etiqueta, required this.valor, this.icono, this.color, this.fondo});

  final String etiqueta;
  final String valor;
  final IconData? icono;
  final Color? color;
  final Color? fondo;

  @override
  Widget build(BuildContext context) {
    final c = color ?? HuataColors.text;
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: fondo ?? HuataColors.panel2,
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              if (icono != null) ...[Icon(icono, size: 16, color: c.withValues(alpha: 0.8)), const SizedBox(width: 6)],
              Flexible(
                child: Text(
                  etiqueta.toUpperCase(),
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.6, color: c.withValues(alpha: 0.75)),
                ),
              ),
            ],
          ),
          const SizedBox(height: 6),
          FittedBox(
            fit: BoxFit.scaleDown,
            alignment: Alignment.centerLeft,
            child: Text(valor, style: TextStyle(fontSize: 22, fontWeight: FontWeight.w800, color: c, letterSpacing: -0.4)),
          ),
        ],
      ),
    );
  }
}

/// Título de un bloque de la pantalla, fuera de las tarjetas.
class EncabezadoSeccion extends StatelessWidget {
  const EncabezadoSeccion(this.texto, {super.key, this.accion});
  final String texto;
  final Widget? accion;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 8, 4, 8),
      child: Row(
        children: [
          Expanded(
            child: Text(
              texto.toUpperCase(),
              style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, letterSpacing: 0.8, color: HuataColors.muted),
            ),
          ),
          ?accion,
        ],
      ),
    );
  }
}

class EstadoAviso extends StatelessWidget {
  const EstadoAviso({super.key, required this.mensaje, this.esError = false});

  final String mensaje;
  final bool esError;

  @override
  Widget build(BuildContext context) {
    final color = esError ? HuataColors.red : HuataColors.blue;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
      margin: const EdgeInsets.only(bottom: 12),
      decoration: BoxDecoration(
        color: esError ? HuataColors.redSoft : HuataColors.blueSoft,
        borderRadius: BorderRadius.circular(12),
        border: Border(left: BorderSide(color: color, width: 3)),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Icon(esError ? Icons.error_outline : Icons.info_outline, size: 18, color: color),
          const SizedBox(width: 10),
          Expanded(child: Text(mensaje, style: TextStyle(color: color, fontSize: 13.5, height: 1.35))),
        ],
      ),
    );
  }
}

/// Estado o etiqueta en forma de píldora (Aprobada, Pendiente, rol...).
class EtiquetaEstado extends StatelessWidget {
  const EtiquetaEstado({super.key, required this.texto, required this.color, required this.fondo});
  final String texto;
  final Color color;
  final Color fondo;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
    decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(999)),
    child: Text(texto, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w700)),
  );
}
