import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/actualizacion/actualizacion_service.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import 'modulos_app.dart';

/// Estructura de la app con sesión: los módulos que la persona puede usar en una barra.
///  - Teléfono: barra inferior. Con más de 5 módulos, los 4 primeros y "Más" con el resto.
///  - Tableta o navegador ancho: barra lateral con el nombre de cada módulo.
///
/// Cada módulo se construye la primera vez que se abre y luego se conserva (no se pierde lo escrito
/// ni la posición al cambiar de pestaña). No se construyen todos al entrar: varias pantallas abren
/// GPS o el canal en vivo, y eso solo debe pasar si la persona las usa.
class AppShell extends StatefulWidget {
  const AppShell({super.key, required this.modulos});

  final List<ModuloApp> modulos;

  @override
  State<AppShell> createState() => _AppShellState();
}

class _AppShellState extends State<AppShell> {
  static const _maxEnBarra = 5;
  static const _anchoLateral = 840.0;

  int _actual = 0;
  final Map<String, Widget> _construidos = {};

  @override
  void didUpdateWidget(covariant AppShell anterior) {
    super.didUpdateWidget(anterior);
    // Cambiaron los permisos (p. ej. desde la web): se conserva el módulo abierto si sigue visible.
    final abierto = anterior.modulos.elementAtOrNull(_actual)?.id;
    final nuevo = widget.modulos.indexWhere((m) => m.id == abierto);
    _actual = nuevo >= 0 ? nuevo : 0;
    final ids = widget.modulos.map((m) => m.id).toSet();
    _construidos.removeWhere((id, _) => !ids.contains(id));
  }

  void _ir(int i) {
    if (i == _actual) return;
    setState(() => _actual = i);
  }

  @override
  Widget build(BuildContext context) {
    final modulos = widget.modulos;
    final actual = modulos[_actual];
    _construidos.putIfAbsent(actual.id, actual.pantalla);

    final contenido = NavegacionModulos(
      disponibles: {for (final m in modulos) m.id},
      ir: (id) {
        final i = widget.modulos.indexWhere((m) => m.id == id);
        if (i >= 0) _ir(i);
      },
      child: Column(
        children: [
          Expanded(
            child: IndexedStack(
              index: _actual,
              children: [for (final m in modulos) _construidos[m.id] ?? const SizedBox.shrink()],
            ),
          ),
          const BannerActualizacion(),
        ],
      ),
    );

    if (modulos.length < 2) return contenido;

    final ancho = MediaQuery.sizeOf(context).width;
    if (ancho >= _anchoLateral) {
      return Scaffold(
        body: Row(
          children: [
            _BarraLateral(modulos: modulos, actual: _actual, alElegir: _ir),
            const VerticalDivider(width: 1),
            Expanded(child: ConBarraLateral(child: contenido)),
          ],
        ),
      );
    }

    final enBarra = modulos.length <= _maxEnBarra ? modulos : modulos.take(_maxEnBarra - 1).toList();
    final hayMas = enBarra.length < modulos.length;
    final indiceBarra = _actual < enBarra.length ? _actual : enBarra.length; // "Más" si está en el resto
    return Scaffold(
      body: contenido,
      bottomNavigationBar: NavigationBar(
        height: 68,
        backgroundColor: HuataColors.panel,
        surfaceTintColor: Colors.transparent,
        indicatorColor: HuataColors.greenSoft,
        shadowColor: const Color(0x220F172A),
        elevation: 8,
        labelBehavior: NavigationDestinationLabelBehavior.alwaysShow,
        selectedIndex: indiceBarra,
        onDestinationSelected: (i) => i < enBarra.length ? _ir(i) : _abrirMas(enBarra.length),
        destinations: [
          for (final m in enBarra) _destino(m),
          if (hayMas)
            const NavigationDestination(
              icon: Icon(Icons.more_horiz),
              selectedIcon: Icon(Icons.more_horiz, color: HuataColors.green),
              label: 'Más',
            ),
        ],
      ),
    );
  }

  NavigationDestination _destino(ModuloApp m) => NavigationDestination(
    icon: Icon(m.icono, color: HuataColors.gray),
    selectedIcon: Icon(m.iconoActivo, color: HuataColors.green),
    label: m.tituloEnBarra,
    tooltip: m.titulo,
  );

  /// Con muchas vistas asignadas, "Más" las muestra agrupadas como el menú de la web y desplazables.
  Future<void> _abrirMas(int desde) async {
    final modulos = widget.modulos;
    final elegido = await showModalBottomSheet<int>(
      context: context,
      isScrollControlled: true,
      showDragHandle: true,
      builder: (ctx) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: modulos.length - desde > 6 ? 0.7 : 0.45,
        maxChildSize: 0.92,
        builder: (_, scroll) => ListView(
          controller: scroll,
          padding: const EdgeInsets.only(bottom: 16),
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(20, 0, 20, 4),
              child: Text('Más módulos', style: TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
            ),
            for (var i = desde; i < modulos.length; i++) ...[
              if (i == desde || modulos[i].grupo != modulos[i - 1].grupo)
                Padding(
                  padding: const EdgeInsets.fromLTRB(20, 14, 20, 4),
                  child: Text(
                    modulos[i].grupo.titulo.toUpperCase(),
                    style: const TextStyle(
                      fontSize: 11.5,
                      fontWeight: FontWeight.w700,
                      letterSpacing: 0.8,
                      color: HuataColors.muted,
                    ),
                  ),
                ),
              ListTile(
                leading: Icon(
                  i == _actual ? modulos[i].iconoActivo : modulos[i].icono,
                  color: i == _actual ? HuataColors.green : HuataColors.gray,
                ),
                title: Text(modulos[i].titulo),
                selected: i == _actual,
                selectedColor: HuataColors.green,
                trailing: const Icon(Icons.chevron_right, color: HuataColors.muted),
                onTap: () => Navigator.of(ctx).pop(i),
              ),
            ],
          ],
        ),
      ),
    );
    if (elegido != null) _ir(elegido);
  }
}

/// Barra lateral (tableta o navegador ancho): la marca arriba, los módulos de trabajo y "Mi cuenta"
/// aparte al pie, como en la web.
class _BarraLateral extends StatelessWidget {
  const _BarraLateral({required this.modulos, required this.actual, required this.alElegir});

  final List<ModuloApp> modulos;
  final int actual;
  final ValueChanged<int> alElegir;

  @override
  Widget build(BuildContext context) {
    final cuenta = modulos.indexWhere((m) => m.id == 'cuenta');
    return Container(
      width: 248,
      decoration: const BoxDecoration(gradient: HuataMarca.degradado),
      child: SafeArea(
        right: false,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Padding(
              padding: EdgeInsets.fromLTRB(18, 18, 18, 20),
              child: Row(
                children: [
                  LogoHuata(tamano: 36),
                  SizedBox(width: 10),
                  Expanded(
                    child: Text(
                      'Huata Lácteos',
                      overflow: TextOverflow.ellipsis,
                      style: TextStyle(color: Colors.white, fontSize: 16, fontWeight: FontWeight.w700),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: ListView(
                padding: EdgeInsets.zero,
                children: [
                  for (var i = 0; i < modulos.length; i++)
                    if (i != cuenta) ...[
                      // Un título por grupo, como el menú de la web.
                      if (i == 0 || modulos[i].grupo != modulos[i - 1].grupo)
                        Padding(
                          padding: EdgeInsets.only(top: i == 0 ? 0 : 12),
                          child: _TituloGrupo(modulos[i].grupo.titulo),
                        ),
                      _ItemLateral(modulo: modulos[i], activo: i == actual, alTocar: () => alElegir(i)),
                    ],
                ],
              ),
            ),
            if (cuenta >= 0) ...[
              const Divider(height: 1, color: Color(0x26FFFFFF)),
              const SizedBox(height: 8),
              _ItemLateral(modulo: modulos[cuenta], activo: cuenta == actual, alTocar: () => alElegir(cuenta)),
              const SizedBox(height: 10),
            ],
          ],
        ),
      ),
    );
  }
}

class _TituloGrupo extends StatelessWidget {
  const _TituloGrupo(this.texto);
  final String texto;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(22, 0, 18, 8),
    child: Text(
      texto.toUpperCase(),
      style: const TextStyle(color: Color(0x99FFFFFF), fontSize: 11, fontWeight: FontWeight.w700, letterSpacing: 0.9),
    ),
  );
}

/// Un módulo de la barra lateral. El activo se marca con fondo y una franja a la izquierda.
class _ItemLateral extends StatelessWidget {
  const _ItemLateral({required this.modulo, required this.activo, required this.alTocar});

  final ModuloApp modulo;
  final bool activo;
  final VoidCallback alTocar;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 2),
      child: Material(
        color: activo ? const Color(0x26FFFFFF) : Colors.transparent,
        borderRadius: BorderRadius.circular(10),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: alTocar,
          child: Row(
            children: [
              Container(width: 3, height: 44, color: activo ? Colors.white : Colors.transparent),
              const SizedBox(width: 11),
              Icon(
                activo ? modulo.iconoActivo : modulo.icono,
                size: 20,
                color: activo ? Colors.white : const Color(0xB3FFFFFF),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Text(
                  modulo.titulo,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    color: activo ? Colors.white : const Color(0xCCFFFFFF),
                    fontSize: 14.5,
                    fontWeight: activo ? FontWeight.w700 : FontWeight.w500,
                  ),
                ),
              ),
              const SizedBox(width: 8),
            ],
          ),
        ),
      ),
    );
  }
}

/// Franja "hay una versión nueva" encima de la barra de módulos. No bloquea: se puede seguir trabajando.
class BannerActualizacion extends StatelessWidget {
  const BannerActualizacion({super.key});

  @override
  Widget build(BuildContext context) {
    final act = context.watch<ActualizacionService>();
    if (act.estado != EstadoActualizacion.disponible) return const SizedBox.shrink();
    return Material(
      color: HuataColors.blueSoft,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(16, 8, 8, 8),
        child: Row(
          children: [
            const Icon(Icons.system_update_outlined, size: 20, color: HuataColors.blue),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                'Hay una versión nueva de la app (${act.publicada?.versionActual ?? ''}).',
                style: const TextStyle(color: HuataColors.blue, fontSize: 13.5, fontWeight: FontWeight.w500),
              ),
            ),
            TextButton(onPressed: act.descargar, child: const Text('Actualizar')),
          ],
        ),
      ),
    );
  }
}
