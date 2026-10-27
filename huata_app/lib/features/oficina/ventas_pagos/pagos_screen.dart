import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/api/ventas_pagos_api.dart';
import '../../../core/models/ventas_pagos.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';
import 'liquidacion_screen.dart';

/// Vista "Pagos" de la web (pagos.read): la planilla del ciclo (jueves a miércoles; se paga el viernes)
/// con lo pendiente de cada proveedor, y el historial de pagos. Pagar abre la liquidación.
class PagosOficinaScreen extends StatefulWidget {
  const PagosOficinaScreen({super.key});

  @override
  State<PagosOficinaScreen> createState() => _PagosOficinaScreenState();
}

class _PagosOficinaScreenState extends State<PagosOficinaScreen> {
  int _vista = 0;

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Pagos',
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<int>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: 0, label: Text('Planilla'), icon: Icon(Icons.receipt_long_outlined)),
                ButtonSegment(value: 1, label: Text('Historial'), icon: Icon(Icons.history)),
              ],
              selected: {_vista},
              onSelectionChanged: (v) => setState(() => _vista = v.first),
            ),
          ),
        ),
        Expanded(child: IndexedStack(index: _vista, children: const [_Planilla(), _Historial()])),
      ]),
    );
  }
}

class _Planilla extends StatefulWidget {
  const _Planilla();

  @override
  State<_Planilla> createState() => _PlanillaState();
}

class _PlanillaState extends State<_Planilla> with EnVivo<_Planilla> {
  final _ciclos = CicloPago.recientes();
  late CicloPago _ciclo = _ciclos.first;
  String _q = '';
  String _filtro = 'PENDIENTE';
  bool _cargando = true;
  String? _error;
  List<FilaPlanilla> _filas = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'pagos', 'acopio', 'calidad'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final ciclo = _ciclo;
    try {
      final filas = await context.read<VentasPagosApi>().planilla(ciclo);
      if (!mounted || ciclo != _ciclo) return;
      setState(() {
        _filas = filas..sort((a, b) => a.proveedorNombre.compareTo(b.proveedorNombre));
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

  Future<void> _abrir(FilaPlanilla f) async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => LiquidacionScreen(fila: f, ciclo: _ciclo)));
    if (mounted) _cargar(silencioso: true);
  }

  @override
  Widget build(BuildContext context) {
    final q = _q.trim().toLowerCase();
    final visibles = _filas.where((f) {
      final coincide = q.isEmpty || f.proveedorNombre.toLowerCase().contains(q) || f.proveedorCodigo.toLowerCase().contains(q);
      final pasa = switch (_filtro) {
        'PENDIENTE' => !f.tieneLiquidacionCerrada && f.importePendiente > 0,
        'PAGADO' => f.tieneLiquidacionCerrada,
        _ => true,
      };
      return coincide && pasa;
    }).toList();
    final porPagar = _filas.where((f) => !f.tieneLiquidacionCerrada).fold(0.0, (s, f) => s + f.importePendiente);
    final pagados = _filas.where((f) => f.tieneLiquidacionCerrada).length;

    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'No hay proveedores con este filtro en el ciclo.',
      cabecera: [
        DropdownButtonFormField<CicloPago>(
          initialValue: _ciclo,
          isExpanded: true,
          decoration: const InputDecoration(labelText: 'Ciclo de liquidación (jueves a miércoles)', isDense: true),
          items: [for (final c in _ciclos) DropdownMenuItem(value: c, child: Text(c.etiqueta))],
          onChanged: (c) {
            if (c == null) return;
            setState(() => _ciclo = c);
            _cargar();
          },
        ),
        const SizedBox(height: 12),
        if (!_cargando && _error == null) ...[
          Row(children: [
            Expanded(child: MontoCardSimple(etiqueta: 'Por pagar', monto: porPagar)),
            const SizedBox(width: 8),
            Expanded(child: DatoClave(etiqueta: 'Pagados', valor: '$pagados de ${_filas.length}')),
          ]),
          const SizedBox(height: 12),
        ],
        CampoBusqueda(alCambiar: (v) => setState(() => _q = v), pista: 'Proveedor o código'),
        FiltroPildoras<String>(
          opciones: const [('PENDIENTE', 'Por pagar'), ('PAGADO', 'Pagados'), ('TODOS', 'Todos')],
          valor: _filtro,
          alCambiar: (v) => setState(() => _filtro = v),
        ),
      ],
      hijos: [
        for (final f in visibles)
          FilaTarjeta(
            icono: f.excelencia ? Icons.workspace_premium_outlined : Icons.person_outline,
            titulo: f.proveedorNombre,
            subtitulo: [f.proveedorCodigo, if (f.zona != null) f.zona!, litros(f.litrosEntregados)].join(' · '),
            derecha: f.tieneLiquidacionCerrada
                ? const EtiquetaEstado(texto: 'Pagado', color: HuataColors.green, fondo: HuataColors.greenSoft)
                : Text(soles(f.importePendiente), style: const TextStyle(fontWeight: FontWeight.w800)),
            onTap: () => _abrir(f),
          ),
      ],
    );
  }
}

/// Monto destacado en azul (lo pendiente).
class MontoCardSimple extends StatelessWidget {
  const MontoCardSimple({super.key, required this.etiqueta, required this.monto});
  final String etiqueta;
  final double monto;

  @override
  Widget build(BuildContext context) =>
      DatoClave(etiqueta: etiqueta, valor: soles(monto), color: HuataColors.blue, fondo: HuataColors.blueSoft);
}

class _Historial extends StatefulWidget {
  const _Historial();

  @override
  State<_Historial> createState() => _HistorialState();
}

class _HistorialState extends State<_Historial> with EnVivo<_Historial> {
  bool _cargando = true;
  bool _cargandoMas = false;
  String? _error;
  List<LiquidacionCompleta> _filas = [];
  int _total = 0;
  int _pagina = 0;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'pagos'}, () => _cargar(silencioso: true));
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
      final r = await context.read<VentasPagosApi>().pagadas();
      if (!mounted) return;
      setState(() {
        _filas = r.filas;
        _total = r.total;
        _pagina = 0;
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
      final r = await context.read<VentasPagosApi>().pagadas(pagina: _pagina + 1);
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
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'Todavía no hay pagos registrados.',
      hijos: [
        for (final l in _filas)
          FilaTarjeta(
            icono: Icons.receipt_long_outlined,
            titulo: '${l.productorNombre} · ${soles(l.montoNeto)}',
            subtitulo: '${fechaCorta(l.periodoInicio)} – ${fechaCorta(l.periodoFin)} · ${litros(l.litrosTotales)}',
            onTap: () => Navigator.of(context).push(
              MaterialPageRoute(builder: (_) => LiquidacionScreen.deId(l.id, titulo: l.productorNombre)),
            ),
          ),
        if (_filas.length < _total)
          OutlinedButton(
            onPressed: _cargandoMas ? null : _mas,
            child: Text(_cargandoMas ? 'Cargando…' : 'Ver más (${_total - _filas.length})'),
          ),
      ],
    );
  }
}
