import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/api/ventas_pagos_api.dart';
import '../../../core/models/ventas_pagos.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

const _compradores = {'PUBLICO_GENERAL': 'Público general', 'MAYORISTA': 'Mayorista', 'PROVEEDOR': 'Proveedor de leche'};
const _condiciones = {'CONTADO': 'Contado', 'CREDITO_7D': 'Crédito 7 días'};
const _medios = {'EFECTIVO': 'Efectivo', 'TRANSFERENCIA': 'Transferencia', 'YAPE_PLIN': 'Yape / Plin'};

/// Vista "Ventas" de la web (quesos.read), con las mismas cuatro partes: ventas y cobros, registrar
/// venta (ventas.registrar), historial de clientes y precios de venta.
class VentasScreen extends StatefulWidget {
  const VentasScreen({super.key});

  @override
  State<VentasScreen> createState() => _VentasScreenState();
}

class _VentasScreenState extends State<VentasScreen> {
  int _vista = 0;

  @override
  Widget build(BuildContext context) {
    final registra = puede(context, 'ventas.registrar');
    final vistas = [
      (Icons.receipt_outlined, 'Ventas', const _ListaVentas()),
      if (registra) (Icons.add_shopping_cart, 'Vender', _RegistrarVenta(alRegistrar: () => setState(() => _vista = 0))),
      (Icons.people_outline, 'Clientes', const _Clientes()),
      (Icons.sell_outlined, 'Precios', const _Precios()),
    ];
    final actual = _vista.clamp(0, vistas.length - 1);
    return HuataScaffold(
      titulo: 'Ventas',
      body: Column(children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<int>(
              showSelectedIcon: false,
              segments: [
                for (var i = 0; i < vistas.length; i++)
                  ButtonSegment(value: i, label: Text(vistas[i].$2), icon: Icon(vistas[i].$1)),
              ],
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

class _ListaVentas extends StatefulWidget {
  const _ListaVentas();

  @override
  State<_ListaVentas> createState() => _ListaVentasState();
}

class _ListaVentasState extends State<_ListaVentas> with EnVivo<_ListaVentas> {
  bool _cargando = true;
  String? _error;
  List<Venta> _ventas = [];
  ResumenVentas? _resumen;
  String _filtro = 'TODAS';
  String _q = '';

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'ventas', 'produccion'}, () => _cargar(silencioso: true));
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
      final ventas = await api.ventas();
      ResumenVentas? resumen;
      try {
        resumen = await api.resumenVentas();
      } catch (_) {}
      if (!mounted) return;
      setState(() {
        _ventas = ventas..sort((a, b) => b.fecha.compareTo(a.fecha));
        _resumen = resumen;
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

  Future<void> _cobrar(Venta v) async {
    final ok = await confirmar(context, titulo: 'Marcar como cobrada', mensaje: '${v.cliente} · ${soles(v.total)}.');
    if (!ok || !mounted) return;
    try {
      await context.read<VentasPagosApi>().marcarCobrada(v.id);
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  Future<void> _anular(Venta v) async {
    final motivoCtrl = TextEditingController();
    final seguir = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Anular venta'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('${v.cliente} · ${v.unidades} u de ${v.productoNombre} · ${soles(v.total)}. El stock vuelve al lote.'),
          const SizedBox(height: 8),
          TextField(controller: motivoCtrl, autofocus: true, maxLength: 200, decoration: const InputDecoration(labelText: 'Motivo')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Volver')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: HuataColors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Anular'),
          ),
        ],
      ),
    );
    if (seguir != true || !mounted) return;
    if (motivoCtrl.text.trim().length < 3) {
      avisar(context, 'Escribe el motivo de la anulación.');
      return;
    }
    try {
      await context.read<VentasPagosApi>().anularVenta(v.id, motivoCtrl.text);
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final cobra = puede(context, 'ventas.cobrar');
    final anula = puede(context, 'ventas.anular');
    final q = _q.trim().toLowerCase();
    final visibles = _ventas.where((v) {
      final pasa = switch (_filtro) {
        'POR_COBRAR' => v.porCobrar,
        'COBRADAS' => !v.anulada && !v.porCobrar,
        'ANULADAS' => v.anulada,
        _ => true,
      };
      return pasa && (q.isEmpty || v.cliente.toLowerCase().contains(q) || v.productoNombre.toLowerCase().contains(q));
    }).toList();
    final r = _resumen;
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'No hay ventas con este filtro.',
      cabecera: [
        if (r != null) ...[
          Row(children: [
            Expanded(child: DatoClave(etiqueta: 'Vendido', valor: soles(r.ventaAcumulada))),
            const SizedBox(width: 8),
            Expanded(
              child: DatoClave(
                etiqueta: 'Por cobrar',
                valor: soles(r.porCobrar),
                color: r.porCobrar > 0 ? HuataColors.amber : null,
                fondo: r.porCobrar > 0 ? HuataColors.amberSoft : null,
              ),
            ),
            const SizedBox(width: 8),
            Expanded(child: DatoClave(etiqueta: 'Stock', valor: '${r.stockDisponible} u')),
          ]),
          const SizedBox(height: 12),
        ],
        CampoBusqueda(alCambiar: (v) => setState(() => _q = v), pista: 'Cliente o producto'),
        FiltroPildoras<String>(
          opciones: const [('TODAS', 'Todas'), ('POR_COBRAR', 'Por cobrar'), ('COBRADAS', 'Cobradas'), ('ANULADAS', 'Anuladas')],
          valor: _filtro,
          alCambiar: (v) => setState(() => _filtro = v),
        ),
      ],
      hijos: [
        for (final v in visibles)
          FilaTarjeta(
            icono: Icons.receipt_outlined,
            titulo: '${v.cliente} · ${soles(v.total)}',
            subtitulo: [
              fechaCorta(v.fecha),
              '${v.unidades} u ${v.productoNombre}',
              _compradores[v.tipoComprador] ?? v.tipoComprador,
              _medios[v.medioCobro] ?? v.medioCobro,
            ].join(' · '),
            derecha: EtiquetaEstado(
              texto: v.anulada ? 'Anulada' : (v.porCobrar ? 'Por cobrar' : 'Cobrada'),
              color: v.anulada ? HuataColors.gray : (v.porCobrar ? HuataColors.amber : HuataColors.green),
              fondo: v.anulada ? HuataColors.graySoft : (v.porCobrar ? HuataColors.amberSoft : HuataColors.greenSoft),
            ),
            onTap: v.anulada || (!cobra && !anula)
                ? null
                : () => showModalBottomSheet<void>(
                    context: context,
                    showDragHandle: true,
                    builder: (ctx) => SafeArea(
                      child: Column(mainAxisSize: MainAxisSize.min, children: [
                        if (cobra && v.porCobrar)
                          ListTile(
                            leading: const Icon(Icons.check_circle_outline, color: HuataColors.green),
                            title: const Text('Marcar como cobrada'),
                            onTap: () {
                              Navigator.pop(ctx);
                              _cobrar(v);
                            },
                          ),
                        if (anula)
                          ListTile(
                            leading: const Icon(Icons.block, color: HuataColors.red),
                            title: const Text('Anular venta'),
                            onTap: () {
                              Navigator.pop(ctx);
                              _anular(v);
                            },
                          ),
                      ]),
                    ),
                  ),
          ),
      ],
    );
  }
}

class _RegistrarVenta extends StatefulWidget {
  const _RegistrarVenta({required this.alRegistrar});
  final VoidCallback alRegistrar;

  @override
  State<_RegistrarVenta> createState() => _RegistrarVentaState();
}

class _RegistrarVentaState extends State<_RegistrarVenta> with EnVivo<_RegistrarVenta> {
  List<LoteParaVenta> _lotes = [];
  /// Se elige el producto, no el lote: sale primero lo que vence antes (pedido del cliente, 2026-10-27).
  int? _productoId;
  TarifaProducto? _tarifa;

  /// Lotes del producto elegido, del que vence antes al que vence después (sin fecha al final).
  List<LoteParaVenta> get _lotesDelProducto {
    final l = _lotes.where((x) => x.productoId == _productoId && x.stock > 0).toList()
      ..sort((a, b) => (a.fechaVencimiento ?? '9999-12-31').compareTo(b.fechaVencimiento ?? '9999-12-31'));
    return l;
  }

  int get _stockDelProducto => _lotesDelProducto.fold(0, (s, l) => s + l.stock);

  /// Productos con stock: id → (nombre, unidad).
  Map<int, (String, String?)> get _productos => {
        for (final l in _lotes)
          if (l.stock > 0) l.productoId: (l.productoNombre, l.unidadVenta),
      };

  /// De qué lotes saldrá la venta.
  List<(LoteParaVenta, int)> get _reparto {
    var faltan = _cantidad;
    final r = <(LoteParaVenta, int)>[];
    for (final l in _lotesDelProducto) {
      if (faltan <= 0) break;
      final toma = faltan < l.stock ? faltan : l.stock;
      r.add((l, toma));
      faltan -= toma;
    }
    return r;
  }
  final _cliente = TextEditingController();
  final _unidades = TextEditingController();
  String _comprador = 'PUBLICO_GENERAL';
  String _condicion = 'CONTADO';
  String _medio = 'EFECTIVO';
  bool _cargando = true;
  bool _guardando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    // El stock de los lotes cambia con cada venta o producción de otros: se mantiene al día.
    recargarEnVivo({'ventas', 'produccion'}, () => _cargarLotes(silencioso: true));
    _cargarLotes();
  }

  @override
  void dispose() {
    _cliente.dispose();
    _unidades.dispose();
    super.dispose();
  }

  Future<void> _cargarLotes({bool silencioso = false}) async {
    try {
      final lotes = await context.read<VentasPagosApi>().lotesConStock();
      if (!mounted) return;
      setState(() {
        _lotes = lotes;
        final ids = {for (final l in lotes) if (l.stock > 0) l.productoId};
        if (!ids.contains(_productoId)) _productoId = ids.length == 1 ? ids.first : null;
        _cargando = false;
        _error = null;
      });
      if (_productoId != null && _tarifa?.productoId != _productoId) _cargarTarifa();
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  Future<void> _cargarTarifa() async {
    final productoId = _productoId;
    if (productoId == null) return;
    final t = await context.read<VentasPagosApi>().tarifaVigente(productoId);
    if (mounted && _productoId == productoId) setState(() => _tarifa = t);
  }

  int get _cantidad => int.tryParse(_unidades.text) ?? 0;
  double get _precio => _tarifa?.precioPara(_comprador) ?? 0;

  Future<void> _registrar() async {
    final productoId = _productoId;
    if (productoId == null || _cliente.text.trim().isEmpty || _cantidad <= 0) {
      avisar(context, 'Elige el producto, escribe el cliente y las unidades.');
      return;
    }
    final nombre = _productos[productoId]?.$1 ?? '';
    if (_cantidad > _stockDelProducto) {
      avisar(context, 'De $nombre solo hay $_stockDelProducto u en cámara.');
      return;
    }
    if (_precio <= 0) {
      avisar(context, 'Este producto no tiene precio vigente para ${_compradores[_comprador]}. Configúralo en Precios.');
      return;
    }
    final ok = await confirmar(
      context,
      titulo: 'Confirmar venta',
      mensaje: '${_cliente.text.trim()}: $_cantidad u de $nombre (${_reparto.map((r) => r.$1.codigo).join(', ')}) · '
          '${soles(_precio * _cantidad)} · ${_condiciones[_condicion]} · ${_medios[_medio]}.',
      si: 'Registrar venta',
    );
    if (!ok || !mounted) return;
    setState(() => _guardando = true);
    try {
      final ventas = await context.read<VentasPagosApi>().venderPorProducto(
        productoId: productoId,
        cliente: _cliente.text,
        unidades: _cantidad,
        tipoComprador: _comprador,
        condicion: _condicion,
        medioCobro: _medio,
      );
      if (!mounted) return;
      final total = ventas.fold(0.0, (s, v) => s + v.total);
      avisar(context, 'Venta registrada: $_cantidad u por ${soles(total)} (${ventas.first.porCobrar ? 'por cobrar' : 'cobrada'}).');
      _cliente.clear();
      _unidades.clear();
      widget.alRegistrar();
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final unidad = _productos[_productoId]?.$2;
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargarLotes,
      vacio: 'No hay lotes con stock para vender.',
      hijos: _lotes.isEmpty
          ? const []
          : [
              SeccionCard(
                icono: Icons.add_shopping_cart,
                titulo: 'Registrar venta',
                subtitulo: 'El precio lo pone la tarifa vigente según el tipo de comprador.',
                child: Column(children: [
                  DropdownButtonFormField<int>(
                    initialValue: _productoId,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Producto', helperText: 'Sale primero lo que vence antes'),
                    items: [
                      for (final e in _productos.entries)
                        DropdownMenuItem(
                          value: e.key,
                          child: Text(
                            '${e.value.$1} · ${_lotes.where((l) => l.productoId == e.key).fold(0, (s, l) => s + l.stock)} en cámara',
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                    ],
                    onChanged: (id) {
                      setState(() {
                        _productoId = id;
                        _tarifa = null;
                      });
                      _cargarTarifa();
                    },
                  ),
                  TextField(controller: _cliente, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Cliente')),
                  TextField(
                    controller: _unidades,
                    keyboardType: TextInputType.number,
                    inputFormatters: [FilteringTextInputFormatter.digitsOnly],
                    decoration: InputDecoration(
                      labelText: 'Unidades',
                      suffixText: unidad ?? 'u',
                      helperText: _productoId == null ? null : 'Máx. $_stockDelProducto',
                    ),
                    onChanged: (_) => setState(() {}),
                  ),
                  if (_reparto.isNotEmpty)
                    Padding(
                      padding: const EdgeInsets.only(top: 8),
                      child: Text(
                        'Sale de: ${_reparto.map((r) => '${r.$2} del lote ${r.$1.codigo}'
                            '${r.$1.fechaVencimiento != null ? ' (vence ${fechaCorta(r.$1.fechaVencimiento)})' : ''}').join(', ')}.',
                        style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                      ),
                    ),
                  DropdownButtonFormField<String>(
                    initialValue: _comprador,
                    decoration: const InputDecoration(labelText: 'Tipo de comprador'),
                    items: [for (final e in _compradores.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
                    onChanged: (v) => setState(() => _comprador = v ?? _comprador),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _condicion,
                    decoration: const InputDecoration(labelText: 'Condición'),
                    items: [for (final e in _condiciones.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
                    onChanged: (v) => setState(() => _condicion = v ?? _condicion),
                  ),
                  DropdownButtonFormField<String>(
                    initialValue: _medio,
                    decoration: const InputDecoration(labelText: 'Medio de cobro'),
                    items: [for (final e in _medios.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
                    onChanged: (v) => setState(() => _medio = v ?? _medio),
                  ),
                  const SizedBox(height: 14),
                  FilaDato('Precio por unidad', _tarifa == null ? 'Sin precio vigente' : soles(_precio)),
                  FilaDato('Total', soles(_precio * _cantidad), destacado: true),
                  const SizedBox(height: 12),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(50)),
                      onPressed: _guardando ? null : _registrar,
                      icon: const Icon(Icons.check),
                      label: Text(_guardando ? 'Registrando…' : 'Registrar venta'),
                    ),
                  ),
                ]),
              ),
            ],
    );
  }
}

class _Clientes extends StatefulWidget {
  const _Clientes();

  @override
  State<_Clientes> createState() => _ClientesState();
}

class _ClientesState extends State<_Clientes> with EnVivo<_Clientes> {
  bool _cargando = true;
  String? _error;
  List<ClienteVentas> _clientes = [];
  String _q = '';

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'ventas'}, () => _cargar(silencioso: true));
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
      final c = await context.read<VentasPagosApi>().clientes();
      if (!mounted) return;
      setState(() {
        _clientes = c..sort((a, b) => b.total.compareTo(a.total));
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
    final q = _q.trim().toLowerCase();
    final visibles = _clientes.where((c) => q.isEmpty || c.cliente.toLowerCase().contains(q)).toList();
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'Sin clientes todavía.',
      cabecera: [CampoBusqueda(alCambiar: (v) => setState(() => _q = v), pista: 'Cliente')],
      hijos: [
        for (final c in visibles)
          FilaTarjeta(
            icono: Icons.person_outline,
            titulo: '${c.cliente} · ${soles(c.total)}',
            subtitulo: '${c.compras} compra(s) · ${c.unidades} u · última ${fechaCorta(c.ultimaCompra)}'
                '${c.productos.isEmpty ? '' : ' · ${c.productos.join(', ')}'}',
            derecha: c.porCobrar > 0
                ? EtiquetaEstado(texto: 'Debe ${soles(c.porCobrar)}', color: HuataColors.amber, fondo: HuataColors.amberSoft)
                : null,
          ),
      ],
    );
  }
}

class _Precios extends StatefulWidget {
  const _Precios();

  @override
  State<_Precios> createState() => _PreciosState();
}

class _PreciosState extends State<_Precios> with EnVivo<_Precios> {
  bool _cargando = true;
  String? _error;
  List<TarifaProducto> _tarifas = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'ventas', 'parametros'}, () => _cargar(silencioso: true));
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
      final t = await context.read<VentasPagosApi>().tarifas();
      if (!mounted) return;
      setState(() {
        _tarifas = t..sort((a, b) => '${a.productoNombre}${b.vigenteDesde}'.compareTo('${b.productoNombre}${a.vigenteDesde}'));
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
    return CuerpoConsulta(
      cargando: _cargando,
      error: _error,
      alRecargar: _cargar,
      vacio: 'Todavía no hay precios de venta. Se configuran en la web (Ventas → Precios de venta).',
      hijos: [
        for (final t in _tarifas)
          SeccionCard(
            icono: t.promocion ? Icons.local_offer_outlined : Icons.sell_outlined,
            titulo: t.productoNombre,
            subtitulo: 'Desde ${fechaCorta(t.vigenteDesde)}'
                '${t.vigenteHasta != null ? ' hasta ${fechaCorta(t.vigenteHasta)}' : ''}'
                '${t.promocion ? ' · Promoción' : ''}'
                '${(t.vigenteHasta == null || t.vigenteHasta!.compareTo(hoy) >= 0) && t.vigenteDesde.compareTo(hoy) <= 0 ? ' · Vigente' : ''}',
            child: Column(children: [
              FilaDato('Público general', soles(t.precioPublicoGeneral)),
              FilaDato('Mayorista', soles(t.precioMayorista)),
              FilaDato('Proveedor de leche', soles(t.precioProveedor)),
              if (t.descripcion != null) FilaDato('Nota', t.descripcion!),
            ]),
          ),
      ].expand((w) => [w, const SizedBox(height: 10)]).toList(),
    );
  }
}
