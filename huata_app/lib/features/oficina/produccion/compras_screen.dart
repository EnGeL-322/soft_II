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

/// Vista "Compras" de la web (compras.read): compras de insumos. Con compras.write se registran (entran
/// al inventario y generan su asiento en el backend) y se dan de alta proveedores de compra.
class ComprasScreen extends StatefulWidget {
  const ComprasScreen({super.key});

  @override
  State<ComprasScreen> createState() => _ComprasScreenState();
}

class _ComprasScreenState extends State<ComprasScreen> with EnVivo<ComprasScreen> {
  bool _cargando = true;
  bool _cargandoMas = false;
  String? _error;
  List<Compra> _compras = [];
  int _total = 0;
  int _pagina = 0;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'compras'}, () => _cargar(silencioso: true));
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
      final r = await context.read<ProduccionOficinaApi>().compras();
      if (!mounted) return;
      setState(() {
        _compras = r.filas;
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
      final r = await context.read<ProduccionOficinaApi>().compras(pagina: _pagina + 1);
      if (!mounted) return;
      setState(() {
        _compras = [..._compras, ...r.filas];
        _pagina++;
      });
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _cargandoMas = false);
    }
  }

  Future<void> _registrar() async {
    final ok = await Navigator.of(context).push<bool>(MaterialPageRoute(builder: (_) => const _RegistrarCompra()));
    if (ok == true && mounted) _cargar(silencioso: true);
  }

  @override
  Widget build(BuildContext context) {
    final escribe = puede(context, 'compras.write');
    final totalMostrado = _compras.where((c) => c.activo).fold(0.0, (s, c) => s + c.total);
    return HuataScaffold(
      titulo: 'Compras',
      floatingActionButton: escribe
          ? FloatingActionButton.extended(onPressed: _registrar, icon: const Icon(Icons.add), label: const Text('Registrar compra'))
          : null,
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'Todavía no hay compras registradas.',
        cabecera: [
          if (!_cargando && _compras.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 10, left: 4),
              child: Text('$_total compra(s) · ${soles(totalMostrado)} en las mostradas', style: const TextStyle(color: HuataColors.muted, fontSize: 12.5)),
            ),
        ],
        hijos: [
          for (final c in _compras)
            Card(
              margin: const EdgeInsets.only(bottom: 8),
              clipBehavior: Clip.antiAlias,
              child: ExpansionTile(
                shape: const Border(),
                collapsedShape: const Border(),
                leading: Icon(Icons.shopping_cart_outlined, color: c.activo ? HuataColors.green : HuataColors.muted),
                title: Text(
                  '${c.proveedorNombre} · ${soles(c.total)}',
                  style: TextStyle(
                    fontWeight: FontWeight.w700,
                    decoration: c.activo ? null : TextDecoration.lineThrough,
                    color: c.activo ? null : HuataColors.muted,
                  ),
                ),
                subtitle: Text([
                  if (!c.activo) 'Anulada',
                  fechaCorta(c.fecha),
                  if (c.documento != null) c.documento!,
                  '${c.items.length} insumo(s)',
                ].join(' · ')),
                childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                children: [
                  for (final i in c.items)
                    FilaDato('${i.materiaPrimaNombre} · ${i.cantidad.toStringAsFixed(2)} × ${soles(i.costoUnitario)}', soles(i.total)),
                  if (c.observaciones != null) FilaDato('Observaciones', c.observaciones!),
                  if (!c.activo && c.motivoAnulacion != null) FilaDato('Motivo de anulación', c.motivoAnulacion!),
                ],
              ),
            ),
          if (_compras.length < _total)
            OutlinedButton(onPressed: _cargandoMas ? null : _mas, child: Text(_cargandoMas ? 'Cargando…' : 'Ver más (${_total - _compras.length})')),
          if (escribe) const SizedBox(height: 72),
        ],
      ),
    );
  }
}

class _Linea {
  MateriaPrima? materia;
  final cantidad = TextEditingController();
  final costo = TextEditingController();

  double get valorCantidad => double.tryParse(cantidad.text.replaceAll(',', '.')) ?? 0;
  double get valorCosto => double.tryParse(costo.text.replaceAll(',', '.')) ?? 0;
  double get total => valorCantidad * valorCosto;
}

class _RegistrarCompra extends StatefulWidget {
  const _RegistrarCompra();

  @override
  State<_RegistrarCompra> createState() => _RegistrarCompraState();
}

class _RegistrarCompraState extends State<_RegistrarCompra> {
  List<ProveedorCompra> _proveedores = [];
  List<MateriaPrima> _materias = [];
  ProveedorCompra? _proveedor;
  DateTime _fecha = DateTime.now();
  final _documento = TextEditingController();
  final _observaciones = TextEditingController();
  final _lineas = [_Linea()];
  bool _cargando = true;
  bool _guardando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    final api = context.read<ProduccionOficinaApi>();
    try {
      final p = await api.proveedoresCompra();
      final m = await api.materiasPrimas();
      if (!mounted) return;
      setState(() {
        _proveedores = p..sort((a, b) => a.nombre.compareTo(b.nombre));
        // La leche del acopio no se compra: entra sola al recibirla en planta.
        _materias = m.where((x) => x.activo && !x.lecheAcopio).toList()..sort((a, b) => a.nombre.compareTo(b.nombre));
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  Future<void> _nuevoProveedor() async {
    final nombre = TextEditingController();
    final contacto = TextEditingController();
    final documento = TextEditingController();
    final guardar = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Nuevo proveedor de compra'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          TextField(controller: nombre, autofocus: true, textCapitalization: TextCapitalization.words, decoration: const InputDecoration(labelText: 'Nombre')),
          TextField(controller: contacto, decoration: const InputDecoration(labelText: 'Contacto (opcional)')),
          TextField(controller: documento, decoration: const InputDecoration(labelText: 'RUC / DNI (opcional)')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Crear')),
        ],
      ),
    );
    if (guardar != true || !mounted || nombre.text.trim().length < 2) return;
    try {
      final p = await context.read<ProduccionOficinaApi>().crearProveedorCompra(
        nombre: nombre.text,
        contacto: contacto.text,
        documento: documento.text,
      );
      if (!mounted) return;
      setState(() {
        _proveedores = [..._proveedores, p];
        _proveedor = p;
      });
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  Future<void> _guardar() async {
    final validas = _lineas.where((l) => l.materia != null && l.valorCantidad > 0 && l.valorCosto >= 0).toList();
    if (_proveedor == null || validas.isEmpty) {
      avisar(context, 'Elige el proveedor y al menos un insumo con cantidad y costo.');
      return;
    }
    final total = validas.fold(0.0, (s, l) => s + l.total);
    final ok = await confirmar(
      context,
      titulo: 'Confirmar compra',
      mensaje: '${_proveedor!.nombre} · ${validas.length} insumo(s) · ${soles(total)} · ${fechaCorta(isoDia(_fecha))}. '
          'Entra al inventario al guardar.',
      si: 'Registrar compra',
    );
    if (!ok || !mounted) return;
    setState(() => _guardando = true);
    try {
      await context.read<ProduccionOficinaApi>().registrarCompra(
        proveedorCompraId: _proveedor!.id,
        fecha: _fecha,
        documento: _documento.text,
        observaciones: _observaciones.text,
        items: [for (final l in validas) (materiaPrimaId: l.materia!.id, cantidad: l.valorCantidad, costoUnitario: l.valorCosto)],
      );
      if (!mounted) return;
      avisar(context, 'Compra registrada: ${soles(total)}.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final total = _lineas.fold(0.0, (s, l) => s + l.total);
    final numero = [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))];
    return HuataScaffold(
      titulo: 'Registrar compra',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        hijos: [
          SeccionCard(
            icono: Icons.store_outlined,
            titulo: 'Proveedor y documento',
            child: Column(children: [
              Row(children: [
                Expanded(
                  child: DropdownButtonFormField<int>(
                    initialValue: _proveedor?.id,
                    isExpanded: true,
                    decoration: const InputDecoration(labelText: 'Proveedor'),
                    items: [for (final p in _proveedores) DropdownMenuItem(value: p.id, child: Text(p.nombre, overflow: TextOverflow.ellipsis))],
                    onChanged: (id) => setState(() => _proveedor = _proveedores.firstWhere((p) => p.id == id)),
                  ),
                ),
                IconButton(tooltip: 'Nuevo proveedor', onPressed: _nuevoProveedor, icon: const Icon(Icons.person_add_alt)),
              ]),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                icon: const Icon(Icons.calendar_month_outlined),
                label: Text('Fecha: ${fechaCorta(isoDia(_fecha))}'),
                onPressed: () async {
                  final f = await showDatePicker(context: context, initialDate: _fecha, firstDate: DateTime(2020), lastDate: DateTime.now());
                  if (f != null) setState(() => _fecha = f);
                },
              ),
              TextField(controller: _documento, decoration: const InputDecoration(labelText: 'N.° de factura o boleta (opcional)')),
              TextField(controller: _observaciones, decoration: const InputDecoration(labelText: 'Observaciones (opcional)')),
            ]),
          ),
          const SizedBox(height: 12),
          SeccionCard(
            icono: Icons.inventory_2_outlined,
            titulo: 'Insumos',
            child: Column(children: [
              for (final (i, l) in _lineas.indexed) ...[
                if (i > 0) const Divider(height: 24),
                DropdownButtonFormField<int>(
                  initialValue: l.materia?.id,
                  isExpanded: true,
                  decoration: InputDecoration(
                    labelText: 'Insumo ${i + 1}',
                    suffixIcon: _lineas.length > 1
                        ? IconButton(icon: const Icon(Icons.close), onPressed: () => setState(() => _lineas.removeAt(i)))
                        : null,
                  ),
                  items: [for (final m in _materias) DropdownMenuItem(value: m.id, child: Text('${m.nombre} (${m.unidad})'))],
                  onChanged: (id) => setState(() {
                    l.materia = _materias.firstWhere((m) => m.id == id);
                    if (l.costo.text.isEmpty && l.materia!.costoUnitarioReferencial != null) {
                      l.costo.text = l.materia!.costoUnitarioReferencial!.toStringAsFixed(2);
                    }
                  }),
                ),
                Row(children: [
                  Expanded(
                    child: TextField(
                      controller: l.cantidad,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: numero,
                      decoration: InputDecoration(labelText: 'Cantidad', suffixText: l.materia?.unidad),
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: TextField(
                      controller: l.costo,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: numero,
                      decoration: const InputDecoration(labelText: 'Costo unitario', prefixText: 'S/ '),
                      onChanged: (_) => setState(() {}),
                    ),
                  ),
                ]),
              ],
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(onPressed: () => setState(() => _lineas.add(_Linea())), icon: const Icon(Icons.add), label: const Text('Otro insumo')),
              ),
              FilaDato('Total', soles(total), destacado: true),
            ]),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: _guardando ? null : _guardar,
            icon: const Icon(Icons.check),
            label: Text(_guardando ? 'Guardando…' : 'Registrar compra'),
          ),
        ],
      ),
    );
  }
}
