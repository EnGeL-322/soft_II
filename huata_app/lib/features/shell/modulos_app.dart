import 'package:flutter/material.dart';

import '../../core/auth/session.dart';
import '../../core/models/calidad.dart';
import '../acopiador/ruta_dia_screen.dart';
import '../administrador/admin_mobile_screen.dart';
import '../calidad/aprobacion_screen.dart';
import '../cuenta/cuenta_screen.dart';
import '../oficina/acopio/calidad_consulta_screen.dart';
import '../oficina/acopio/panel_screen.dart';
import '../oficina/acopio/recepcion_screen.dart';
import '../oficina/acopio/recojos_screen.dart';
import '../oficina/configuracion/precios_calidad_screen.dart';
import '../oficina/personas/acopiadores_screen.dart';
import '../oficina/personas/cambios_zona_screen.dart';
import '../oficina/personas/proveedores_screen.dart';
import '../oficina/personas/rutas_mapa_screen.dart';
import '../oficina/personas/zonas_screen.dart';
import '../oficina/produccion/compras_screen.dart';
import '../oficina/produccion/inventario_screen.dart';
import '../oficina/produccion/recetas_screen.dart';
import '../oficina/ventas_pagos/contabilidad_screen.dart';
import '../oficina/ventas_pagos/pagos_screen.dart';
import '../oficina/ventas_pagos/ventas_screen.dart';
import '../productor_queso/lotes_screen.dart';
import '../proveedor/cambio_zona_screen.dart';
import '../proveedor/historial_produccion_screen.dart';
import '../proveedor/pagos_proveedor_screen.dart';
import '../proveedor/resumen_screen.dart';
import '../testeador/recorrido_screen.dart';
import 'menu_otras_funciones.dart';

/// Grupos de la barra, los mismos de la web (`navegacion.ts`), más el trabajo de campo y la cuenta.
enum GrupoModulo {
  campo('Mi trabajo'),
  acopio('Acopio'),
  planta('Planta'),
  personas('Personas y rutas'),
  produccion('Producción'),
  ventasPagos('Ventas y pagos'),
  configuracion('Configuración'),
  cuenta('Cuenta');

  const GrupoModulo(this.titulo);
  final String titulo;
}

/// Un módulo de la barra de navegación de la app.
///
/// La lista vive aquí y en un solo lugar: añadir un módulo es añadir una entrada (su pantalla, su
/// ícono y quién lo ve). Quién lo ve sale de los roles y permisos que devuelve el backend, los mismos
/// que se asignan en la web en "Roles y vistas": asignarle una vista a un usuario hace aparecer el
/// módulo en el celular al momento (canal de cambios), sin publicar una versión nueva de la app.
class ModuloApp {
  const ModuloApp({
    required this.id,
    required this.titulo,
    required this.icono,
    required this.iconoActivo,
    required this.pantalla,
    required this.visible,
    this.grupo = GrupoModulo.campo,
    this.tituloCorto,
  });

  final String id;
  final String titulo;
  final IconData icono;
  final IconData iconoActivo;
  final Widget Function() pantalla;
  final bool Function(Session s) visible;
  final GrupoModulo grupo;

  /// Nombre para la barra inferior del teléfono, donde no cabe el título completo.
  final String? tituloCorto;

  String get tituloEnBarra => tituloCorto ?? titulo;
}

/// Roles de campo: su app es su trabajo del día (la ruta, el testeo, sus entregas).
const _rolesDeCampo = {RolesMovil.acopiador, RolesMovil.testeadorCampo, RolesMovil.proveedor};

/// Permisos de lectura que los roles de campo traen porque su propia pantalla los necesita (cargar la
/// ruta, los proveedores, sus recojos). Para ellos NO abren las vistas de oficina: un acopiador no
/// debe encontrarse "Recojos" o "Rutas y mapa" que nadie le asignó.
const _lecturasTecnicasDeCampo = {'panel.read', 'acopio.read', 'rutas.read', 'proveedores.read', 'calidad.read'};

/// ¿Ve esta vista de la web? Igual que el menú de la web: basta con su permiso de acceso. Para quien
/// solo tiene roles de campo, las lecturas técnicas de su rol no cuentan (ver arriba).
bool veVista(Session s, String permiso) {
  if (!s.puede(permiso)) return false;
  final soloCampo = s.roles.isNotEmpty && s.roles.every(_rolesDeCampo.contains);
  return !(soloCampo && _lecturasTecnicasDeCampo.contains(permiso));
}

/// Orden de la barra: primero lo que cada persona hace todos los días; "Mi cuenta" siempre al final.
final List<ModuloApp> catalogoModulos = [
  // ---------------------------------------------------------------- trabajo de campo
  ModuloApp(
    id: 'resumen',
    titulo: 'Inicio',
    icono: Icons.home_outlined,
    iconoActivo: Icons.home,
    pantalla: () => const ResumenProveedorScreen(),
    visible: (s) => s.tieneRol(RolesMovil.proveedor),
  ),
  ModuloApp(
    id: 'pagos',
    titulo: 'Pagos y cobros',
    tituloCorto: 'Pagos',
    icono: Icons.payments_outlined,
    iconoActivo: Icons.payments,
    pantalla: () => const PagosProveedorScreen(),
    visible: (s) => s.tieneRol(RolesMovil.proveedor),
  ),
  ModuloApp(
    id: 'historial',
    titulo: 'Historial de producción',
    tituloCorto: 'Historial',
    icono: Icons.water_drop_outlined,
    iconoActivo: Icons.water_drop,
    pantalla: () => const HistorialProduccionScreen(),
    visible: (s) => s.tieneRol(RolesMovil.proveedor),
  ),
  ModuloApp(
    id: 'zona',
    titulo: 'Cambio de zona',
    tituloCorto: 'Zona',
    icono: Icons.edit_location_alt_outlined,
    iconoActivo: Icons.edit_location_alt,
    pantalla: () => const CambioZonaScreen(),
    visible: (s) => s.tieneRol(RolesMovil.proveedor),
  ),
  ModuloApp(
    id: 'ruta',
    titulo: 'Mi ruta',
    icono: Icons.route_outlined,
    iconoActivo: Icons.route,
    pantalla: () => const RutaDiaScreen(),
    visible: (s) => funcionesDisponibles(s).contains(FuncionMovil.rutaDia),
  ),
  ModuloApp(
    id: 'testeo',
    titulo: 'Testeo',
    icono: Icons.science_outlined,
    iconoActivo: Icons.science,
    pantalla: () => const RecorridoTesteadorScreen(),
    visible: (s) =>
        s.tieneRol(RolesMovil.testeador) ||
        s.tieneRol(RolesMovil.testeadorCampo) ||
        funcionesDisponibles(s).contains(FuncionMovil.testeo),
  ),
  ModuloApp(
    id: 'aprobar',
    titulo: 'Aprobar análisis',
    tituloCorto: 'Aprobar',
    icono: Icons.fact_check_outlined,
    iconoActivo: Icons.fact_check,
    pantalla: () => const AprobacionCalidadScreen(),
    visible: (s) => s.puede(permisoAprobarCalidad),
  ),

  // ---------------------------------------------------------------- vistas de la web: Acopio
  ModuloApp(
    id: 'panel',
    titulo: 'Inicio',
    grupo: GrupoModulo.acopio,
    icono: Icons.dashboard_outlined,
    iconoActivo: Icons.dashboard,
    pantalla: () => const PanelScreen(),
    visible: (s) => veVista(s, 'panel.read'),
  ),
  ModuloApp(
    id: 'calidad',
    titulo: 'Calidad',
    grupo: GrupoModulo.acopio,
    icono: Icons.verified_outlined,
    iconoActivo: Icons.verified,
    pantalla: () => const CalidadConsultaScreen(),
    visible: (s) => veVista(s, 'calidad.read'),
  ),
  ModuloApp(
    id: 'recojos',
    titulo: 'Recojos en ruta',
    tituloCorto: 'Recojos',
    grupo: GrupoModulo.acopio,
    icono: Icons.local_shipping_outlined,
    iconoActivo: Icons.local_shipping,
    pantalla: () => const RecojosScreen(),
    visible: (s) => veVista(s, 'acopio.read'),
  ),
  ModuloApp(
    id: 'recepcion',
    titulo: 'Recepción de rutas',
    tituloCorto: 'Recepción',
    grupo: GrupoModulo.planta,
    icono: Icons.warehouse_outlined,
    iconoActivo: Icons.warehouse,
    pantalla: () => const RecepcionScreen(),
    visible: (s) => veVista(s, 'acopio.read'),
  ),

  // ---------------------------------------------------------------- vistas de la web: Personas y rutas
  ModuloApp(
    id: 'usuarios',
    titulo: 'Usuarios',
    grupo: GrupoModulo.personas,
    icono: Icons.manage_accounts_outlined,
    iconoActivo: Icons.manage_accounts,
    pantalla: () => const AdminMobileScreen(),
    visible: (s) => s.tieneRol(RolesMovil.administrador) || veVista(s, 'usuarios.read'),
  ),
  ModuloApp(
    id: 'proveedores',
    titulo: 'Proveedores',
    grupo: GrupoModulo.personas,
    icono: Icons.groups_outlined,
    iconoActivo: Icons.groups,
    pantalla: () => const ProveedoresScreen(),
    visible: (s) => veVista(s, 'proveedores.read'),
  ),
  ModuloApp(
    id: 'acopiadores',
    titulo: 'Acopiadores',
    grupo: GrupoModulo.personas,
    icono: Icons.badge_outlined,
    iconoActivo: Icons.badge,
    pantalla: () => const AcopiadoresScreen(),
    visible: (s) => veVista(s, 'acopiadores.read'),
  ),
  ModuloApp(
    id: 'rutas',
    titulo: 'Rutas y mapa',
    tituloCorto: 'Rutas',
    grupo: GrupoModulo.personas,
    icono: Icons.map_outlined,
    iconoActivo: Icons.map,
    pantalla: () => const RutasMapaScreen(),
    visible: (s) => veVista(s, 'rutas.read'),
  ),
  ModuloApp(
    id: 'zonas',
    titulo: 'Zonas',
    grupo: GrupoModulo.personas,
    icono: Icons.place_outlined,
    iconoActivo: Icons.place,
    pantalla: () => const ZonasScreen(),
    visible: (s) => veVista(s, 'zonas.read'),
  ),
  ModuloApp(
    id: 'cambios-zona',
    titulo: 'Cambios de zona',
    tituloCorto: 'Cambios',
    grupo: GrupoModulo.personas,
    icono: Icons.swap_horiz,
    iconoActivo: Icons.swap_horiz,
    pantalla: () => const CambiosZonaScreen(),
    visible: (s) => veVista(s, 'proveedores.read'),
  ),

  // ---------------------------------------------------------------- vistas de la web: Producción
  ModuloApp(
    id: 'recetas',
    titulo: 'Recetas y productos',
    tituloCorto: 'Recetas',
    grupo: GrupoModulo.produccion,
    icono: Icons.menu_book_outlined,
    iconoActivo: Icons.menu_book,
    pantalla: () => const RecetasScreen(),
    visible: (s) => veVista(s, 'recetas.read'),
  ),
  ModuloApp(
    id: 'produccion',
    titulo: 'Producción',
    grupo: GrupoModulo.produccion,
    icono: Icons.factory_outlined,
    iconoActivo: Icons.factory,
    pantalla: () => const LotesProduccionScreen(),
    // Como el menú de la web: basta con ver producción; registrar lotes exige escritura.
    visible: (s) => s.tieneRol(RolesMovil.productorQueso) || veVista(s, 'produccion.read'),
  ),

  ModuloApp(
    id: 'compras',
    titulo: 'Compras',
    grupo: GrupoModulo.produccion,
    icono: Icons.shopping_cart_outlined,
    iconoActivo: Icons.shopping_cart,
    pantalla: () => const ComprasScreen(),
    visible: (s) => veVista(s, 'compras.read'),
  ),
  ModuloApp(
    id: 'inventario',
    titulo: 'Inventario',
    grupo: GrupoModulo.produccion,
    icono: Icons.inventory_2_outlined,
    iconoActivo: Icons.inventory_2,
    pantalla: () => const InventarioScreen(),
    visible: (s) => veVista(s, 'inventario.read'),
  ),

  // ---------------------------------------------------------------- vistas de la web: Ventas y pagos
  ModuloApp(
    id: 'ventas',
    titulo: 'Ventas',
    grupo: GrupoModulo.ventasPagos,
    icono: Icons.point_of_sale_outlined,
    iconoActivo: Icons.point_of_sale,
    pantalla: () => const VentasScreen(),
    visible: (s) => veVista(s, 'quesos.read'),
  ),
  ModuloApp(
    // No es el "Pagos y cobros" del proveedor (id 'pagos'): es la planilla de oficina.
    id: 'pagos-oficina',
    titulo: 'Pagos a proveedores',
    tituloCorto: 'Pagos',
    grupo: GrupoModulo.ventasPagos,
    icono: Icons.account_balance_wallet_outlined,
    iconoActivo: Icons.account_balance_wallet,
    pantalla: () => const PagosOficinaScreen(),
    visible: (s) => veVista(s, 'pagos.read'),
  ),
  ModuloApp(
    id: 'contabilidad',
    titulo: 'Contabilidad',
    grupo: GrupoModulo.ventasPagos,
    icono: Icons.calculate_outlined,
    iconoActivo: Icons.calculate,
    pantalla: () => const ContabilidadScreen(),
    visible: (s) => veVista(s, 'contabilidad.read'),
  ),

  // ---------------------------------------------------------------- vistas de la web: Configuración
  ModuloApp(
    id: 'precios-calidad',
    titulo: 'Precios y calidad',
    tituloCorto: 'Precios',
    grupo: GrupoModulo.configuracion,
    icono: Icons.tune_outlined,
    iconoActivo: Icons.tune,
    pantalla: () => const PreciosCalidadScreen(),
    visible: (s) => veVista(s, 'parametros.read'),
  ),

  // ---------------------------------------------------------------- cuenta
  ModuloApp(
    id: 'cuenta',
    titulo: 'Mi cuenta',
    grupo: GrupoModulo.cuenta,
    icono: Icons.person_outline,
    iconoActivo: Icons.person,
    pantalla: () => const CuentaScreen(),
    visible: (_) => true,
  ),
];

List<ModuloApp> modulosDisponibles(Session sesion) => catalogoModulos.where((m) => m.visible(sesion)).toList();
