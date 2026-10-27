import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/acopio_oficina_api.dart';
import '../../../core/api/productores_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/acopio_oficina.dart';
import '../../../core/models/productor.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';
import '../../proveedor/proveedor_comun.dart' show CalidadChip;

/// Vista "Calidad" de la web (calidad.read): incidencias abiertas y su seguimiento, y el historial de
/// análisis de cada proveedor. Registrar análisis y aprobarlos son los módulos Testeo y Aprobar.
class CalidadConsultaScreen extends StatefulWidget {
  const CalidadConsultaScreen({super.key});

  @override
  State<CalidadConsultaScreen> createState() => _CalidadConsultaScreenState();
}

class _CalidadConsultaScreenState extends State<CalidadConsultaScreen> {
  int _vista = 0;

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Calidad',
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<int>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: 0, label: Text('Incidencias'), icon: Icon(Icons.report_outlined)),
                ButtonSegment(value: 1, label: Text('Por proveedor'), icon: Icon(Icons.person_search_outlined)),
              ],
              selected: {_vista},
              onSelectionChanged: (v) => setState(() => _vista = v.first),
            ),
          ),
        ),
        Expanded(child: IndexedStack(index: _vista, children: const [_Incidencias(), _HistorialProveedor()])),
      ]),
    );
  }
}

const _estados = {'ABIERTA': 'Abierta', 'EN_SEGUIMIENTO': 'En seguimiento', 'CERRADA': 'Cerrada'};

class _Incidencias extends StatefulWidget {
  const _Incidencias();

  @override
  State<_Incidencias> createState() => _IncidenciasState();
}

class _IncidenciasState extends State<_Incidencias> with EnVivo<_Incidencias> {
  bool _cargando = true;
  String? _error;
  List<IncidenciaCalidad> _todas = [];
  String? _estado = 'ABIERTA';

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'calidad'}, () => _cargar(silencioso: true));
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
      final lista = await context.read<AcopioOficinaApi>().incidencias();
      if (!mounted) return;
      setState(() {
        _todas = lista..sort((a, b) => b.fecha.compareTo(a.fecha));
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

  Future<void> _cambiarEstado(IncidenciaCalidad i) async {
    final nuevo = await showModalBottomSheet<String>(
      context: context,
      showDragHandle: true,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          for (final e in _estados.entries)
            ListTile(
              title: Text(e.value),
              selected: e.key == i.estado,
              trailing: e.key == i.estado ? const Icon(Icons.check) : null,
              onTap: () => Navigator.pop(ctx, e.key),
            ),
        ]),
      ),
    );
    if (nuevo == null || nuevo == i.estado || !mounted) return;
    final ok = await confirmar(
      context,
      titulo: 'Cambiar estado',
      mensaje: 'Incidencia de ${i.productorNombre}: ${_estados[i.estado]} → ${_estados[nuevo]}.',
    );
    if (!ok || !mounted) return;
    try {
      await context.read<AcopioOficinaApi>().cambiarEstadoIncidencia(i.id, nuevo);
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final puedeEditar = puede(context, 'calidad.write');
    final visibles = _estado == null ? _todas : _todas.where((i) => i.estado == _estado).toList();
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'No hay incidencias en este estado.',
      cabecera: [
        FiltroPildoras<String?>(
          opciones: [for (final e in _estados.entries) (e.key, e.value), (null, 'Todas')],
          valor: _estado,
          alCambiar: (v) => setState(() => _estado = v),
        ),
      ],
      hijos: [
        for (final i in visibles)
          FilaTarjeta(
            icono: Icons.report_outlined,
            titulo: '${i.productorNombre} · ${i.tipo}',
            subtitulo: [fechaCorta(i.fecha), if (i.descripcion != null) i.descripcion!].join(' · '),
            derecha: EtiquetaEstado(
              texto: _estados[i.estado] ?? i.estado,
              color: i.estado == 'CERRADA' ? HuataColors.green : HuataColors.amber,
              fondo: i.estado == 'CERRADA' ? HuataColors.greenSoft : HuataColors.amberSoft,
            ),
            onTap: puedeEditar ? () => _cambiarEstado(i) : null,
          ),
      ],
    );
  }
}

class _HistorialProveedor extends StatefulWidget {
  const _HistorialProveedor();

  @override
  State<_HistorialProveedor> createState() => _HistorialProveedorState();
}

class _HistorialProveedorState extends State<_HistorialProveedor> with EnVivo<_HistorialProveedor> {
  Timer? _espera;
  List<Productor> _encontrados = [];
  Productor? _elegido;
  List<ControlCalidadResumen> _historial = [];
  bool _cargando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'calidad'}, () {
      if (_elegido != null) _abrir(_elegido!, silencioso: true);
    });
  }

  @override
  void dispose() {
    _espera?.cancel();
    super.dispose();
  }

  void _buscar(String q) {
    _espera?.cancel();
    _espera = Timer(const Duration(milliseconds: 350), () async {
      if (q.trim().length < 2) {
        setState(() => _encontrados = []);
        return;
      }
      try {
        final lista = await context.read<ProductoresApi>().listar(q: q.trim(), size: 20);
        if (mounted) setState(() => _encontrados = lista);
      } catch (e) {
        if (mounted) avisar(context, mensajeDeError(e));
      }
    });
  }

  Future<void> _abrir(Productor p, {bool silencioso = false}) async {
    setState(() {
      _elegido = p;
      _encontrados = [];
      if (!silencioso) {
        _cargando = true;
        _error = null;
      }
    });
    try {
      final h = await context.read<AcopioOficinaApi>().historialCalidad(p.id);
      if (!mounted || _elegido?.id != p.id) return;
      setState(() {
        _historial = h..sort((a, b) => b.fecha.compareTo(a.fecha));
        _cargando = false;
      });
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final p = _elegido;
    final buenas = _historial.where((c) => c.resultado == 'ACEPTADA').length;
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: () async {
        if (p != null) await _abrir(p);
      },
      vacio: p == null ? 'Busca un proveedor por nombre o código.' : 'Este proveedor no tiene análisis registrados.',
      cabecera: [
        CampoBusqueda(alCambiar: _buscar, pista: 'Nombre o código del proveedor'),
        for (final e in _encontrados)
          FilaTarjeta(
            icono: Icons.person_outline,
            titulo: e.nombreCompleto,
            subtitulo: [e.codigo, if (e.zonaPrincipal != null) e.zonaPrincipal!].join(' · '),
            onTap: () => _abrir(e),
          ),
        if (p != null && _encontrados.isEmpty && !_cargando && _historial.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 10),
            child: SeccionCard(
              titulo: p.nombreCompleto,
              subtitulo: '${p.codigo} · ${_historial.length} análisis',
              child: Row(children: [
                Expanded(child: DatoClave(etiqueta: 'Buenos', valor: '$buenas', color: HuataColors.green, fondo: HuataColors.greenSoft)),
                const SizedBox(width: 8),
                Expanded(
                  child: DatoClave(
                    etiqueta: 'Con observación o malos',
                    valor: '${_historial.length - buenas}',
                    color: HuataColors.amber,
                    fondo: HuataColors.amberSoft,
                  ),
                ),
              ]),
            ),
          ),
      ],
      hijos: p == null || _encontrados.isNotEmpty
          ? const []
          : [
              for (final c in _historial)
                FilaTarjeta(
                  icono: Icons.science_outlined,
                  titulo: fechaHoraCorta(c.fecha),
                  subtitulo: [
                    if (c.densidad != null) 'Densidad ${c.densidad!.toStringAsFixed(1)}',
                    if (c.grasa != null) 'Grasa ${c.grasa!.toStringAsFixed(1)} %',
                    if (c.temperatura != null) '${c.temperatura!.toStringAsFixed(1)} °C',
                    if (c.motivoRechazo != null) c.motivoRechazo!,
                    if (c.responsable != null) c.responsable!,
                  ].join(' · '),
                  derecha: CalidadChip(resultado: c.resultado),
                ),
            ],
    );
  }
}
