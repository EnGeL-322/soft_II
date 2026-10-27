import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/api/ventas_pagos_api.dart';
import '../../../core/models/ventas_pagos.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

const _origenes = {
  'COMPRA': 'Compra',
  'PRODUCCION_CONSUMO': 'Consumo de producción',
  'PRODUCCION_MANO_OBRA': 'Mano de obra',
  'PRODUCCION_TERMINADO': 'Producto terminado',
  'VENTA': 'Venta',
  'VENTA_COSTO': 'Costo de venta',
  'ANULACION_VENTA': 'Anulación de venta',
};

/// Vista "Contabilidad" de la web (contabilidad.read): libro diario y libro mayor. Solo consulta: los
/// asientos los genera el sistema desde compras, producción y ventas.
class ContabilidadScreen extends StatefulWidget {
  const ContabilidadScreen({super.key});

  @override
  State<ContabilidadScreen> createState() => _ContabilidadScreenState();
}

class _ContabilidadScreenState extends State<ContabilidadScreen> {
  int _vista = 0;

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Contabilidad',
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<int>(
              showSelectedIcon: false,
              segments: const [
                ButtonSegment(value: 0, label: Text('Libro diario'), icon: Icon(Icons.menu_book_outlined)),
                ButtonSegment(value: 1, label: Text('Libro mayor'), icon: Icon(Icons.account_balance_outlined)),
              ],
              selected: {_vista},
              onSelectionChanged: (v) => setState(() => _vista = v.first),
            ),
          ),
        ),
        Expanded(child: IndexedStack(index: _vista, children: const [_Diario(), _Mayor()])),
      ]),
    );
  }
}

/// Mes elegido: del 1 al último día.
(DateTime, DateTime) _mes(DateTime ref) => (DateTime(ref.year, ref.month, 1), DateTime(ref.year, ref.month + 1, 0));

class _SelectorMes extends StatelessWidget {
  const _SelectorMes({required this.mes, required this.alCambiar});
  final DateTime mes;
  final ValueChanged<DateTime> alCambiar;

  static const _nombres = ['enero', 'febrero', 'marzo', 'abril', 'mayo', 'junio', 'julio', 'agosto', 'setiembre', 'octubre', 'noviembre', 'diciembre'];

  @override
  Widget build(BuildContext context) {
    final hoy = DateTime.now();
    final esActual = mes.year == hoy.year && mes.month == hoy.month;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(children: [
        IconButton.outlined(onPressed: () => alCambiar(DateTime(mes.year, mes.month - 1)), icon: const Icon(Icons.chevron_left)),
        Expanded(
          child: Text('${_nombres[mes.month - 1]} ${mes.year}', textAlign: TextAlign.center, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15)),
        ),
        IconButton.outlined(
          onPressed: esActual ? null : () => alCambiar(DateTime(mes.year, mes.month + 1)),
          icon: const Icon(Icons.chevron_right),
        ),
      ]),
    );
  }
}

class _Diario extends StatefulWidget {
  const _Diario();

  @override
  State<_Diario> createState() => _DiarioState();
}

class _DiarioState extends State<_Diario> with EnVivo<_Diario> {
  DateTime _mesActual = DateTime(DateTime.now().year, DateTime.now().month);
  bool _cargando = true;
  String? _error;
  List<AsientoContable> _asientos = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'contabilidad'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final (desde, hasta) = _mes(_mesActual);
    try {
      final a = await context.read<VentasPagosApi>().libroDiario(desde: desde, hasta: hasta);
      if (!mounted) return;
      setState(() {
        _asientos = a..sort((x, y) => y.fecha.compareTo(x.fecha));
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

  @override
  Widget build(BuildContext context) {
    final debe = _asientos.fold(0.0, (s, a) => s + a.totalDebe);
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'Sin asientos en este mes.',
      cabecera: [
        _SelectorMes(
          mes: _mesActual,
          alCambiar: (m) {
            setState(() => _mesActual = m);
            _cargar();
          },
        ),
        if (!_cargando && _asientos.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 10, left: 4),
            child: Text('${_asientos.length} asiento(s) · movimiento ${soles(debe)}', style: const TextStyle(color: HuataColors.muted, fontSize: 12.5)),
          ),
      ],
      hijos: [
        for (final a in _asientos)
          Card(
            margin: const EdgeInsets.only(bottom: 8),
            clipBehavior: Clip.antiAlias,
            child: ExpansionTile(
              shape: const Border(),
              collapsedShape: const Border(),
              title: Text(a.glosa, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
              subtitle: Text('${fechaCorta(a.fecha)} · ${_origenes[a.origenTipo] ?? a.origenTipo} · ${soles(a.totalDebe)}'),
              childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
              children: [
                for (final l in a.lineas)
                  FilaDato('${l.codigoCuenta} ${l.nombreCuenta}', l.debe > 0 ? 'Debe ${soles(l.debe)}' : 'Haber ${soles(l.haber)}'),
              ],
            ),
          ),
      ],
    );
  }
}

class _Mayor extends StatefulWidget {
  const _Mayor();

  @override
  State<_Mayor> createState() => _MayorState();
}

class _MayorState extends State<_Mayor> with EnVivo<_Mayor> {
  List<CuentaContable> _cuentas = [];
  String? _cuenta;
  LibroMayor? _mayor;
  bool _cargando = true;
  String? _error;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'contabilidad'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final api = context.read<VentasPagosApi>();
    try {
      final cuentas = await api.cuentasConMovimiento();
      final cuenta = _cuenta ?? (cuentas.isNotEmpty ? cuentas.first.codigo : null);
      final mayor = cuenta == null ? null : await api.libroMayor(cuenta);
      if (!mounted) return;
      setState(() {
        _cuentas = cuentas;
        _cuenta = cuenta;
        _mayor = mayor;
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

  @override
  Widget build(BuildContext context) {
    final m = _mayor;
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'Todavía no hay cuentas con movimiento.',
      cabecera: [
        if (_cuentas.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: DropdownButtonFormField<String>(
              initialValue: _cuenta,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Cuenta', isDense: true),
              items: [for (final c in _cuentas) DropdownMenuItem(value: c.codigo, child: Text('${c.codigo} · ${c.nombre}', overflow: TextOverflow.ellipsis))],
              onChanged: (c) {
                setState(() => _cuenta = c);
                _cargar();
              },
            ),
          ),
        if (m != null) ...[
          DatoClave(etiqueta: 'Saldo de ${m.nombreCuenta}', valor: soles(m.saldoFinal), icono: Icons.account_balance_outlined),
          const SizedBox(height: 12),
        ],
      ],
      hijos: [
        if (m != null)
          for (final mv in m.movimientos.reversed)
            FilaTarjeta(
              titulo: mv.glosa,
              subtitulo: '${fechaCorta(mv.fecha)} · ${mv.debe > 0 ? 'Debe ${soles(mv.debe)}' : 'Haber ${soles(mv.haber)}'}',
              derecha: Text(soles(mv.saldo), style: const TextStyle(fontWeight: FontWeight.w700)),
            ),
      ],
    );
  }
}
