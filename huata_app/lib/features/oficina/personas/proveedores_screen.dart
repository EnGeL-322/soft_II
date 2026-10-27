import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/personas_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/productor.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

/// Pestaña "Proveedores" de Usuarios en la web (proveedores.read): el padrón. Con proveedores.write se
/// aprueba, se cambia el estado y la marca de Excelencia (siempre con motivo, que queda auditado). El
/// alta completa con DNI y firma sigue en la web.
class ProveedoresScreen extends StatefulWidget {
  const ProveedoresScreen({super.key});

  @override
  State<ProveedoresScreen> createState() => _ProveedoresScreenState();
}

class _ProveedoresScreenState extends State<ProveedoresScreen> with EnVivo<ProveedoresScreen> {
  String _q = '';
  String? _estado;
  Timer? _espera;
  bool _cargando = true;
  bool _cargandoMas = false;
  String? _error;
  List<Productor> _filas = [];
  int _total = 0;
  int _pagina = 0;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'proveedores'}, () => _cargar(silencioso: true));
    _cargar();
  }

  @override
  void dispose() {
    _espera?.cancel();
    super.dispose();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final r = await context.read<PersonasApi>().proveedores(q: _q, estado: _estado, tamano: silencioso ? ((_pagina + 1) * 40).clamp(1, 500) : 40);
      if (!mounted) return;
      setState(() {
        _filas = r.filas;
        _total = r.total;
        if (!silencioso) _pagina = 0;
        _cargando = false;
        _error = null;
      });
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  Future<void> _mas() async {
    setState(() => _cargandoMas = true);
    try {
      final r = await context.read<PersonasApi>().proveedores(q: _q, estado: _estado, pagina: _pagina + 1);
      if (!mounted) return;
      setState(() {
        _filas = [..._filas, ...r.filas];
        _pagina++;
      });
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _cargandoMas = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Proveedores',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay proveedores con estos filtros.',
        cabecera: [
          CampoBusqueda(
            pista: 'Nombre, código o DNI',
            alCambiar: (v) {
              _espera?.cancel();
              _espera = Timer(const Duration(milliseconds: 400), () {
                _q = v;
                _cargar();
              });
            },
          ),
          FiltroPildoras<String?>(
            opciones: [(null, 'Todos'), for (final e in estadosProveedor.entries) (e.key, e.value)],
            valor: _estado,
            alCambiar: (v) {
              setState(() => _estado = v);
              _cargar();
            },
          ),
          if (!_cargando && _error == null)
            Padding(
              padding: const EdgeInsets.only(bottom: 8, left: 4),
              child: Text('$_total proveedor(es)', style: const TextStyle(color: HuataColors.muted, fontSize: 12.5)),
            ),
        ],
        hijos: [
          for (final p in _filas)
            FilaTarjeta(
              icono: p.excelencia ? Icons.workspace_premium_outlined : Icons.person_outline,
              titulo: p.nombreCompleto,
              subtitulo: [p.codigo, if (p.zonaPrincipal != null) p.zonaPrincipal!, if (p.rutaNombre != null) p.rutaNombre!].join(' · '),
              derecha: EstadoProveedorChip(estado: p.estado),
              onTap: () async {
                await Navigator.of(context).push(MaterialPageRoute(builder: (_) => FichaProveedorScreen(id: p.id, nombre: p.nombreCompleto)));
                if (mounted) _cargar(silencioso: true);
              },
            ),
          if (_filas.length < _total)
            OutlinedButton(onPressed: _cargandoMas ? null : _mas, child: Text(_cargandoMas ? 'Cargando…' : 'Ver más (${_total - _filas.length})')),
        ],
      ),
    );
  }
}

class EstadoProveedorChip extends StatelessWidget {
  const EstadoProveedorChip({super.key, required this.estado});
  final String estado;

  @override
  Widget build(BuildContext context) {
    final (color, fondo) = switch (estado) {
      'ACTIVO' => (HuataColors.green, HuataColors.greenSoft),
      'PENDIENTE_APROBACION' || 'EN_REVISION' => (HuataColors.amber, HuataColors.amberSoft),
      'RETIRADO' => (HuataColors.red, HuataColors.redSoft),
      _ => (HuataColors.gray, HuataColors.graySoft),
    };
    return EtiquetaEstado(texto: estadosProveedor[estado] ?? estado, color: color, fondo: fondo);
  }
}

class FichaProveedorScreen extends StatefulWidget {
  const FichaProveedorScreen({super.key, required this.id, required this.nombre});
  final int id;
  final String nombre;

  @override
  State<FichaProveedorScreen> createState() => _FichaProveedorScreenState();
}

class _FichaProveedorScreenState extends State<FichaProveedorScreen> with EnVivo<FichaProveedorScreen> {
  Productor? _p;
  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'proveedores'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final p = await context.read<PersonasApi>().proveedor(widget.id);
      if (!mounted) return;
      setState(() {
        _p = p;
        _cargando = false;
        _error = null;
      });
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  Future<String?> _pedirMotivo(String titulo, String detalle) async {
    final motivo = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(titulo),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text(detalle),
          const SizedBox(height: 8),
          TextField(controller: motivo, autofocus: true, maxLength: 300, decoration: const InputDecoration(labelText: 'Motivo (queda en el historial)')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Confirmar')),
        ],
      ),
    );
    if (ok != true) return null;
    if (motivo.text.trim().length < 3) {
      if (mounted) avisar(context, 'Escribe el motivo.');
      return null;
    }
    return motivo.text;
  }

  Future<void> _cambiarEstado() async {
    final p = _p!;
    final nuevo = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          for (final e in estadosProveedor.entries)
            if (e.key != p.estado && e.key != 'PENDIENTE_APROBACION')
              ListTile(title: Text(e.key == 'ACTIVO' && p.estado == 'PENDIENTE_APROBACION' ? 'Aprobar (activar)' : e.value), onTap: () => Navigator.pop(ctx, e.key)),
        ]),
      ),
    );
    if (nuevo == null || !mounted) return;
    final motivo = await _pedirMotivo(
      'Cambiar estado',
      '${p.nombreCompleto}: ${estadosProveedor[p.estado]} → ${estadosProveedor[nuevo]}.',
    );
    if (motivo == null || !mounted) return;
    try {
      await context.read<PersonasApi>().cambiarEstadoProveedor(p.id, nuevo, motivo);
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  Future<void> _excelencia() async {
    final p = _p!;
    final motivo = await _pedirMotivo(
      p.excelencia ? 'Retirar Excelencia' : 'Conceder Excelencia',
      p.excelencia
          ? '${p.nombreCompleto} deja de cobrar la tarifa preferencial.'
          : '${p.nombreCompleto} cobra la tarifa preferencial mientras su ciclo salga sin incidencias.',
    );
    if (motivo == null || !mounted) return;
    try {
      await context.read<PersonasApi>().cambiarExcelencia(p.id, excelencia: !p.excelencia, motivo: motivo);
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = _p;
    final escribe = puede(context, 'proveedores.write');
    return HuataScaffold(
      titulo: p?.nombreCompleto ?? widget.nombre,
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        hijos: p == null
            ? const []
            : [
                SeccionCard(
                  icono: p.excelencia ? Icons.workspace_premium_outlined : Icons.person_outline,
                  titulo: p.nombreCompleto,
                  subtitulo: '${p.codigo}${p.excelencia ? ' · Excelencia' : ''}',
                  child: Column(children: [
                    Align(alignment: Alignment.centerLeft, child: EstadoProveedorChip(estado: p.estado)),
                    const SizedBox(height: 10),
                    if (p.documentoIdentidad != null) FilaDato('DNI', p.documentoIdentidad!),
                    if (p.telefono != null) FilaDato('Teléfono', p.telefono!),
                    FilaDato('Zona', p.zonaPrincipal ?? '—'),
                    FilaDato('Ruta', p.rutaNombre ?? '—'),
                    if (p.ordenEnRuta != null) FilaDato('Orden en la ruta', '${p.ordenEnRuta}'),
                    FilaDato('Acopiador', p.acopiadorResponsableNombre ?? '—'),
                    if (p.notas != null && p.notas!.isNotEmpty) FilaDato('Notas', p.notas!),
                  ]),
                ),
                if (escribe) ...[
                  const SizedBox(height: 12),
                  FilledButton.icon(
                    style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(48)),
                    onPressed: _cambiarEstado,
                    icon: const Icon(Icons.swap_horiz),
                    label: Text(p.estado == 'PENDIENTE_APROBACION' ? 'Aprobar o cambiar estado' : 'Cambiar estado'),
                  ),
                  const SizedBox(height: 8),
                  OutlinedButton.icon(
                    style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(48)),
                    onPressed: _excelencia,
                    icon: const Icon(Icons.workspace_premium_outlined),
                    label: Text(p.excelencia ? 'Retirar Excelencia' : 'Conceder Excelencia'),
                  ),
                ],
              ],
      ),
    );
  }
}
