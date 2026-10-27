import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/api/produccion_oficina_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/produccion_oficina.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

/// Vista "Inventario" de la web (inventario.read): existencias de insumos, producto terminado y los
/// movimientos (kardex). Con inventario.write se crean y editan materias primas.
class InventarioScreen extends StatefulWidget {
  const InventarioScreen({super.key});

  @override
  State<InventarioScreen> createState() => _InventarioScreenState();
}

class _InventarioScreenState extends State<InventarioScreen> {
  int _vista = 0;

  @override
  Widget build(BuildContext context) {
    // Como la web: el producto terminado se ve con permiso de ventas o de producción.
    final terminado = puede(context, 'quesos.read') || puede(context, 'produccion.read');
    final vistas = [
      (Icons.inventory_2_outlined, 'Insumos', const _Existencias()),
      if (terminado) (Icons.storefront_outlined, 'Terminado', const _Terminado()),
      (Icons.swap_vert, 'Movimientos', const _Kardex()),
    ];
    final actual = _vista.clamp(0, vistas.length - 1);
    return HuataScaffold(
      titulo: 'Inventario',
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<int>(
              showSelectedIcon: false,
              segments: [for (var i = 0; i < vistas.length; i++) ButtonSegment(value: i, label: Text(vistas[i].$2), icon: Icon(vistas[i].$1))],
              selected: {actual},
              onSelectionChanged: (v) => setState(() => _vista = v.first),
            ),
          ),
        ),
        Expanded(child: IndexedStack(index: actual, children: [for (final v in vistas) v.$3])),
      ]),
    );
  }
}

class _Existencias extends StatefulWidget {
  const _Existencias();

  @override
  State<_Existencias> createState() => _ExistenciasState();
}

class _ExistenciasState extends State<_Existencias> with EnVivo<_Existencias> {
  bool _cargando = true;
  String? _error;
  List<MateriaPrima> _materias = [];
  String _q = '';

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'inventario', 'compras', 'produccion', 'acopio'}, () => _cargar(silencioso: true));
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
      final m = await context.read<ProduccionOficinaApi>().materiasPrimas();
      if (!mounted) return;
      setState(() {
        _materias = m..sort((a, b) => a.nombre.compareTo(b.nombre));
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

  Future<void> _editar([MateriaPrima? m]) async {
    final nombre = TextEditingController(text: m?.nombre ?? '');
    final costo = TextEditingController(text: m?.costoUnitarioReferencial?.toStringAsFixed(2) ?? '');
    String unidad = m?.unidadMedida ?? 'KG';
    final guardar = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: Text(m == null ? 'Nueva materia prima' : 'Editar ${m.nombre}'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: nombre, autofocus: true, textCapitalization: TextCapitalization.sentences, decoration: const InputDecoration(labelText: 'Nombre')),
            DropdownButtonFormField<String>(
              initialValue: unidad,
              decoration: const InputDecoration(labelText: 'Unidad'),
              items: [for (final e in unidadesMedida.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
              onChanged: (v) => setLocal(() => unidad = v ?? unidad),
            ),
            TextField(
              controller: costo,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
              decoration: const InputDecoration(labelText: 'Costo referencial (opcional)', prefixText: 'S/ '),
            ),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Guardar')),
          ],
        ),
      ),
    );
    if (guardar != true || !mounted) return;
    if (nombre.text.trim().length < 2) {
      avisar(context, 'Escribe el nombre de la materia prima.');
      return;
    }
    final ok = await confirmar(context, titulo: 'Confirmar', mensaje: '${m == null ? 'Crear' : 'Guardar'} "${nombre.text.trim()}" (${unidadesMedida[unidad]}).');
    if (!ok || !mounted) return;
    try {
      await context.read<ProduccionOficinaApi>().guardarMateriaPrima(
        id: m?.id,
        nombre: nombre.text,
        unidadMedida: unidad,
        costo: double.tryParse(costo.text.replaceAll(',', '.')),
      );
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  /// Qué hacer con un insumo: ajustar su stock o editarlo (la leche del acopio no se edita).
  Future<void> _opciones(MateriaPrima m) async {
    final opcion = await showModalBottomSheet<String>(
      context: context,
      builder: (ctx) => SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, children: [
          ListTile(title: Text(m.nombre, style: const TextStyle(fontWeight: FontWeight.w700))),
          ListTile(
            leading: const Icon(Icons.tune),
            title: const Text('Ajustar stock'),
            subtitle: const Text('Merma, conteo físico, leche descartada'),
            onTap: () => Navigator.pop(ctx, 'ajustar'),
          ),
          if (!m.lecheAcopio)
            ListTile(leading: const Icon(Icons.edit_outlined), title: const Text('Editar'), onTap: () => Navigator.pop(ctx, 'editar')),
        ]),
      ),
    );
    if (!mounted) return;
    if (opcion == 'ajustar') await _ajustar(m);
    if (opcion == 'editar') await _editar(m);
  }

  static const _motivosAjuste = ['Merma', 'Conteo físico', 'Leche descartada', 'Vencido o dañado', 'Corrección de registro'];

  Future<void> _ajustar(MateriaPrima m) async {
    final cantidad = TextEditingController();
    final motivo = TextEditingController();
    var salida = true;
    final guardar = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) {
          final n = double.tryParse(cantidad.text.replaceAll(',', '.'));
          final queda = n == null ? null : (salida ? m.stock - n : m.stock + n);
          return AlertDialog(
            title: Text('Ajustar ${m.nombre}'),
            content: SingleChildScrollView(
              child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                Text('Hay ${m.stock.toStringAsFixed(m.stock % 1 == 0 ? 0 : 2)} ${m.unidad} en el almacén.',
                    style: const TextStyle(color: HuataColors.muted)),
                const SizedBox(height: 12),
                SegmentedButton<bool>(
                  showSelectedIcon: false,
                  segments: const [
                    ButtonSegment(value: true, label: Text('Sale')),
                    ButtonSegment(value: false, label: Text('Entra')),
                  ],
                  selected: {salida},
                  onSelectionChanged: (v) => setLocal(() => salida = v.first),
                ),
                TextField(
                  controller: cantidad,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
                  onChanged: (_) => setLocal(() {}),
                  decoration: InputDecoration(labelText: 'Cantidad', suffixText: m.unidad),
                ),
                const SizedBox(height: 10),
                Wrap(spacing: 6, runSpacing: 6, children: [
                  for (final mo in _motivosAjuste)
                    ChoiceChip(label: Text(mo), selected: motivo.text == mo, onSelected: (_) => setLocal(() => motivo.text = mo)),
                ]),
                TextField(controller: motivo, onChanged: (_) => setLocal(() {}), decoration: const InputDecoration(labelText: 'Motivo')),
                if (queda != null) ...[
                  const SizedBox(height: 10),
                  Text('Quedarán ${queda.toStringAsFixed(queda % 1 == 0 ? 0 : 2)} ${m.unidad}.',
                      style: TextStyle(fontWeight: FontWeight.w700, color: queda < 0 ? HuataColors.red : HuataColors.green)),
                ],
              ]),
            ),
            actions: [
              TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
              FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Guardar ajuste')),
            ],
          );
        },
      ),
    );
    if (guardar != true || !mounted) return;
    final n = double.tryParse(cantidad.text.replaceAll(',', '.'));
    if (n == null || n <= 0) {
      avisar(context, 'Indica la cantidad.');
      return;
    }
    if (motivo.text.trim().length < 3) {
      avisar(context, 'Indica el motivo del ajuste.');
      return;
    }
    try {
      await context.read<ProduccionOficinaApi>().ajustarStock(materiaPrimaId: m.id, salida: salida, cantidad: n, motivo: motivo.text);
      if (!mounted) return;
      avisar(context, 'Stock de ${m.nombre} ajustado.');
      _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final escribe = puede(context, 'inventario.write');
    final q = _q.trim().toLowerCase();
    final visibles = _materias.where((m) => m.activo && (q.isEmpty || m.nombre.toLowerCase().contains(q))).toList();
    return Stack(children: [
      CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay materias primas registradas.',
        cabecera: [CampoBusqueda(alCambiar: (v) => setState(() => _q = v), pista: 'Insumo')],
        hijos: [
          for (final m in visibles)
            FilaTarjeta(
              icono: m.lecheAcopio ? Icons.water_drop_outlined : Icons.inventory_2_outlined,
              titulo: m.nombre,
              subtitulo: [
                if (m.costoUnitarioReferencial != null) '${soles(m.costoUnitarioReferencial)} / ${m.unidad}',
                if (m.lecheAcopio) 'Entra sola al recibir la leche en planta',
              ].join(' · '),
              derecha: Text(
                '${m.stock.toStringAsFixed(m.stock % 1 == 0 ? 0 : 2)} ${m.unidad}',
                style: TextStyle(fontWeight: FontWeight.w800, color: m.stock <= 0 ? HuataColors.red : null),
              ),
              onTap: escribe ? () => _opciones(m) : null,
            ),
          if (escribe) const SizedBox(height: 72),
        ],
      ),
      if (escribe)
        Positioned(
          right: 16,
          bottom: 16,
          child: FloatingActionButton.extended(onPressed: _editar, icon: const Icon(Icons.add), label: const Text('Materia prima')),
        ),
    ]);
  }
}

class _Terminado extends StatefulWidget {
  const _Terminado();

  @override
  State<_Terminado> createState() => _TerminadoState();
}

class _TerminadoState extends State<_Terminado> with EnVivo<_Terminado> {
  bool _cargando = true;
  String? _error;
  List<LoteTerminado> _lotes = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'produccion', 'ventas', 'inventario'}, () => _cargar(silencioso: true));
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
      final l = await context.read<ProduccionOficinaApi>().lotesTerminados();
      if (!mounted) return;
      setState(() {
        _lotes = l.where((x) => x.stock > 0).toList()..sort((a, b) => (a.fechaVencimiento ?? '9').compareTo(b.fechaVencimiento ?? '9'));
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
    final hoy = isoDia(DateTime.now());
    final total = _lotes.fold(0, (s, l) => s + l.stock);
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'No hay producto terminado en stock.',
      cabecera: [
        if (!_cargando && _lotes.isNotEmpty) ...[
          DatoClave(etiqueta: 'Unidades en stock', valor: '$total', icono: Icons.storefront_outlined),
          const SizedBox(height: 12),
        ],
      ],
      hijos: [
        for (final l in _lotes)
          FilaTarjeta(
            icono: Icons.inventory_outlined,
            titulo: '${l.productoNombre} · ${l.codigo}',
            subtitulo: [
              'Producido ${fechaCorta(l.fecha)}',
              if (l.fechaVencimiento != null) 'vence ${fechaCorta(l.fechaVencimiento)}',
              if (l.costoUnitario != null) 'costo ${soles(l.costoUnitario)}',
            ].join(' · '),
            derecha: l.fechaVencimiento != null && l.fechaVencimiento!.compareTo(hoy) < 0
                ? const EtiquetaEstado(texto: 'Vencido', color: HuataColors.red, fondo: HuataColors.redSoft)
                : Text('${l.stock} ${l.unidadVenta ?? 'u'}', style: const TextStyle(fontWeight: FontWeight.w800)),
          ),
      ],
    );
  }
}

class _Kardex extends StatefulWidget {
  const _Kardex();

  @override
  State<_Kardex> createState() => _KardexState();
}

class _KardexState extends State<_Kardex> with EnVivo<_Kardex> {
  bool _cargando = true;
  bool _cargandoMas = false;
  String? _error;
  List<MovimientoInventario> _filas = [];
  List<MateriaPrima> _materias = [];
  int? _materia;
  int _total = 0;
  int _pagina = 0;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'inventario', 'compras', 'produccion', 'ventas', 'acopio'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final api = context.read<ProduccionOficinaApi>();
    try {
      final r = await api.kardex(materiaPrimaId: _materia);
      if (_materias.isEmpty) {
        try {
          _materias = await api.materiasPrimas();
        } catch (_) {}
      }
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
      final r = await context.read<ProduccionOficinaApi>().kardex(materiaPrimaId: _materia, pagina: _pagina + 1);
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
      vacio: 'Sin movimientos.',
      cabecera: [
        if (_materias.isNotEmpty)
          Padding(
            padding: const EdgeInsets.only(bottom: 12),
            child: DropdownButtonFormField<int?>(
              initialValue: _materia,
              isExpanded: true,
              decoration: const InputDecoration(labelText: 'Insumo', isDense: true),
              items: [
                const DropdownMenuItem<int?>(value: null, child: Text('Todos los insumos')),
                for (final m in _materias) DropdownMenuItem<int?>(value: m.id, child: Text(m.nombre)),
              ],
              onChanged: (v) {
                setState(() => _materia = v);
                _cargar();
              },
            ),
          ),
      ],
      hijos: [
        for (final m in _filas)
          FilaTarjeta(
            icono: m.esEntrada ? Icons.south_west : Icons.north_east,
            titulo: '${m.materiaPrimaNombre ?? m.loteQuesoCodigo ?? '—'} · ${m.esEntrada ? '+' : '−'}${m.cantidad.abs().toStringAsFixed(2)}',
            subtitulo: [tiposMovimiento[m.tipo] ?? m.tipo, fechaHoraCorta(m.fecha), m.usuario, if (m.observaciones != null) m.observaciones!].join(' · '),
          ),
        if (_filas.length < _total)
          OutlinedButton(onPressed: _cargandoMas ? null : _mas, child: Text(_cargandoMas ? 'Cargando…' : 'Ver más (${_total - _filas.length})')),
      ],
    );
  }
}
