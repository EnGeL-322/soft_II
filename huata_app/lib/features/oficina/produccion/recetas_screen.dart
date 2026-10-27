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

/// Vista "Recetas y productos" de la web (recetas.read): los productos lácteos configurables (queso,
/// yogurt, manjar…) y la receta vigente de cada uno. Con recetas.write se crean y editan productos y
/// se publica una nueva versión de la receta (la anterior deja de regir; no se pisa el historial).
class RecetasScreen extends StatefulWidget {
  const RecetasScreen({super.key});

  @override
  State<RecetasScreen> createState() => _RecetasScreenState();
}

class _RecetasScreenState extends State<RecetasScreen> with EnVivo<RecetasScreen> {
  bool _cargando = true;
  String? _error;
  List<ProductoCatalogo> _productos = [];
  List<RecetaCompleta> _recetas = [];
  bool _verInactivos = false;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'produccion', 'inventario'}, () => _cargar(silencioso: true));
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
      final productos = await api.productos();
      final recetas = await api.recetas();
      if (!mounted) return;
      setState(() {
        _productos = productos..sort((a, b) => a.nombre.compareTo(b.nombre));
        _recetas = recetas;
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

  /// La receta que rige hoy: la más reciente ya vigente y sin fecha de fin pasada.
  RecetaCompleta? _vigente(int productoId) {
    final hoy = isoDia(DateTime.now());
    final candidatas = _recetas
        .where((r) => r.productoId == productoId && r.vigenteDesde.compareTo(hoy) <= 0 && (r.vigenteHasta == null || r.vigenteHasta!.compareTo(hoy) >= 0))
        .toList()
      ..sort((a, b) => b.vigenteDesde.compareTo(a.vigenteDesde));
    return candidatas.firstOrNull;
  }

  Future<void> _editarProducto([ProductoCatalogo? p]) async {
    final nombre = TextEditingController(text: p?.nombre ?? '');
    final unidad = TextEditingController(text: p?.unidadVenta ?? 'unidad');
    final descripcion = TextEditingController(text: p?.descripcion ?? '');
    final vida = TextEditingController(text: p?.vidaUtilDias?.toString() ?? '');
    final guardar = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(p == null ? 'Nuevo producto' : 'Editar ${p.nombre}'),
        content: SingleChildScrollView(
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            TextField(controller: nombre, autofocus: true, textCapitalization: TextCapitalization.sentences, decoration: const InputDecoration(labelText: 'Nombre (queso, yogurt, manjar…)')),
            TextField(controller: unidad, decoration: const InputDecoration(labelText: 'Se vende por (unidad, botella 1 L…)')),
            TextField(
              controller: vida,
              keyboardType: TextInputType.number,
              inputFormatters: [FilteringTextInputFormatter.digitsOnly],
              decoration: const InputDecoration(labelText: 'Días que dura (opcional)'),
            ),
            TextField(controller: descripcion, decoration: const InputDecoration(labelText: 'Descripción (opcional)')),
          ]),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Guardar')),
        ],
      ),
    );
    if (guardar != true || !mounted) return;
    if (nombre.text.trim().length < 2 || unidad.text.trim().isEmpty) {
      avisar(context, 'Escribe el nombre y cómo se vende.');
      return;
    }
    final ok = await confirmar(context, titulo: 'Confirmar', mensaje: '${p == null ? 'Crear' : 'Guardar'} "${nombre.text.trim()}".');
    if (!ok || !mounted) return;
    try {
      await context.read<ProduccionOficinaApi>().guardarProducto(
        id: p?.id,
        nombre: nombre.text,
        unidadVenta: unidad.text,
        descripcion: descripcion.text,
        vidaUtilDias: int.tryParse(vida.text),
      );
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  Future<void> _cambiarEstado(ProductoCatalogo p) async {
    final ok = await confirmar(
      context,
      titulo: p.activo ? 'Dar de baja' : 'Reactivar',
      mensaje: p.activo
          ? '"${p.nombre}" deja de ofrecerse para producir y vender. Su historial se conserva.'
          : '"${p.nombre}" vuelve a estar disponible.',
      si: p.activo ? 'Dar de baja' : 'Reactivar',
    );
    if (!ok || !mounted) return;
    try {
      await context.read<ProduccionOficinaApi>().cambiarEstadoProducto(p.id, activo: !p.activo);
      if (mounted) _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  Future<void> _nuevaReceta(ProductoCatalogo p) async {
    final ok = await Navigator.of(context).push<bool>(
      MaterialPageRoute(builder: (_) => _NuevaReceta(producto: p, base: _vigente(p.id))),
    );
    if (ok == true && mounted) _cargar(silencioso: true);
  }

  @override
  Widget build(BuildContext context) {
    final escribe = puede(context, 'recetas.write');
    final visibles = _productos.where((p) => _verInactivos || p.activo).toList();
    return HuataScaffold(
      titulo: 'Recetas y productos',
      floatingActionButton: escribe
          ? FloatingActionButton.extended(onPressed: _editarProducto, icon: const Icon(Icons.add), label: const Text('Producto'))
          : null,
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'Todavía no hay productos.',
        cabecera: [
          if (_productos.any((p) => !p.activo))
            SwitchListTile(
              contentPadding: EdgeInsets.zero,
              title: const Text('Mostrar productos dados de baja'),
              value: _verInactivos,
              onChanged: (v) => setState(() => _verInactivos = v),
            ),
        ],
        hijos: [
          for (final p in visibles) ...[
            _TarjetaProducto(
              producto: p,
              receta: _vigente(p.id),
              alEditar: escribe ? () => _editarProducto(p) : null,
              alCambiarEstado: escribe ? () => _cambiarEstado(p) : null,
              alNuevaReceta: escribe && p.activo ? () => _nuevaReceta(p) : null,
            ),
            const SizedBox(height: 10),
          ],
          if (escribe) const SizedBox(height: 64),
        ],
      ),
    );
  }
}

class _TarjetaProducto extends StatelessWidget {
  const _TarjetaProducto({required this.producto, this.receta, this.alEditar, this.alCambiarEstado, this.alNuevaReceta});
  final ProductoCatalogo producto;
  final RecetaCompleta? receta;
  final VoidCallback? alEditar;
  final VoidCallback? alCambiarEstado;
  final VoidCallback? alNuevaReceta;

  @override
  Widget build(BuildContext context) {
    final p = producto;
    final r = receta;
    return SeccionCard(
      icono: Icons.bakery_dining_outlined,
      titulo: p.nombre,
      subtitulo: [
        'Se vende por ${p.unidadVenta}',
        if (p.vidaUtilDias != null) 'dura ${p.vidaUtilDias} días',
        if (!p.activo) 'DADO DE BAJA',
      ].join(' · '),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        if (r == null)
          const Text('Sin receta vigente: no se puede producir hasta crearla.', style: TextStyle(color: HuataColors.amber))
        else ...[
          Text(
            'Receta vigente desde ${fechaCorta(r.vigenteDesde)}'
            '${r.rendimientoLitrosPorUnidad != null ? ' · ${r.rendimientoLitrosPorUnidad!.toStringAsFixed(2)} L de leche por ${p.unidadVenta}' : ''}',
            style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
          ),
          const SizedBox(height: 6),
          for (final i in r.items)
            FilaDato(i.materiaPrimaNombre, '${i.cantidadPorUnidad.toStringAsFixed(3)} ${unidadesMedida[i.unidadMedida] ?? i.unidadMedida} / ${p.unidadVenta}'),
        ],
        if (alEditar != null || alNuevaReceta != null) ...[
          const SizedBox(height: 8),
          Wrap(spacing: 8, children: [
            if (alNuevaReceta != null)
              OutlinedButton.icon(onPressed: alNuevaReceta, icon: const Icon(Icons.post_add), label: Text(r == null ? 'Crear receta' : 'Nueva versión')),
            if (alEditar != null) TextButton.icon(onPressed: alEditar, icon: const Icon(Icons.edit_outlined), label: const Text('Editar')),
            if (alCambiarEstado != null)
              TextButton.icon(
                onPressed: alCambiarEstado,
                icon: Icon(p.activo ? Icons.block : Icons.restart_alt),
                label: Text(p.activo ? 'Dar de baja' : 'Reactivar'),
              ),
          ]),
        ],
      ]),
    );
  }
}

class _InsumoNuevo {
  _InsumoNuevo({this.materiaId, String cantidad = ''}) : cantidad = TextEditingController(text: cantidad);
  int? materiaId;
  final TextEditingController cantidad;
  double get valor => double.tryParse(cantidad.text.replaceAll(',', '.')) ?? 0;
}

class _NuevaReceta extends StatefulWidget {
  const _NuevaReceta({required this.producto, this.base});
  final ProductoCatalogo producto;
  final RecetaCompleta? base;

  @override
  State<_NuevaReceta> createState() => _NuevaRecetaState();
}

class _NuevaRecetaState extends State<_NuevaReceta> {
  List<MateriaPrima> _materias = [];
  late final List<_InsumoNuevo> _insumos = widget.base == null
      ? [_InsumoNuevo()]
      : [for (final i in widget.base!.items) _InsumoNuevo(materiaId: i.materiaPrimaId, cantidad: i.cantidadPorUnidad.toString())];
  late final _rendimiento = TextEditingController(text: widget.base?.rendimientoLitrosPorUnidad?.toString() ?? '');
  final _observaciones = TextEditingController();
  DateTime _desde = DateTime.now();
  bool _cargando = true;
  bool _guardando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  Future<void> _cargar() async {
    try {
      final m = await context.read<ProduccionOficinaApi>().materiasPrimas();
      if (!mounted) return;
      setState(() {
        _materias = m.where((x) => x.activo).toList()..sort((a, b) => a.nombre.compareTo(b.nombre));
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

  Future<void> _guardar() async {
    final validos = _insumos.where((i) => i.materiaId != null && i.valor > 0).toList();
    if (validos.isEmpty) {
      avisar(context, 'Agrega al menos un insumo con su cantidad por unidad.');
      return;
    }
    final ok = await confirmar(
      context,
      titulo: 'Publicar receta',
      mensaje: '${widget.producto.nombre}: ${validos.length} insumo(s), vigente desde ${fechaCorta(isoDia(_desde))}. '
          '${widget.base != null ? 'La versión anterior deja de regir; su historial se conserva.' : ''}',
      si: 'Publicar',
    );
    if (!ok || !mounted) return;
    setState(() => _guardando = true);
    try {
      await context.read<ProduccionOficinaApi>().crearReceta(
        productoId: widget.producto.id,
        vigenteDesde: _desde,
        rendimientoLitrosPorUnidad: double.tryParse(_rendimiento.text.replaceAll(',', '.')),
        observaciones: _observaciones.text,
        items: [for (final i in validos) (materiaPrimaId: i.materiaId!, cantidadPorUnidad: i.valor)],
      );
      if (!mounted) return;
      avisar(context, 'Receta publicada.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _guardando = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final numero = [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))];
    return HuataScaffold(
      titulo: 'Receta de ${widget.producto.nombre}',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        hijos: [
          SeccionCard(
            icono: Icons.event_outlined,
            titulo: 'Vigencia',
            child: Column(children: [
              OutlinedButton.icon(
                icon: const Icon(Icons.calendar_month_outlined),
                label: Text('Rige desde: ${fechaCorta(isoDia(_desde))}'),
                onPressed: () async {
                  final f = await showDatePicker(context: context, initialDate: _desde, firstDate: DateTime(2020), lastDate: DateTime(2100));
                  if (f != null) setState(() => _desde = f);
                },
              ),
              TextField(
                controller: _rendimiento,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                inputFormatters: numero,
                decoration: InputDecoration(labelText: 'Litros de leche por ${widget.producto.unidadVenta} (opcional)', suffixText: 'L'),
              ),
              TextField(controller: _observaciones, decoration: const InputDecoration(labelText: 'Observaciones (opcional)')),
            ]),
          ),
          const SizedBox(height: 12),
          SeccionCard(
            icono: Icons.list_alt,
            titulo: 'Insumos por ${widget.producto.unidadVenta}',
            child: Column(children: [
              for (final (n, i) in _insumos.indexed)
                Row(children: [
                  Expanded(
                    flex: 3,
                    child: DropdownButtonFormField<int>(
                      initialValue: _materias.any((m) => m.id == i.materiaId) ? i.materiaId : null,
                      isExpanded: true,
                      decoration: InputDecoration(labelText: 'Insumo ${n + 1}'),
                      items: [for (final m in _materias) DropdownMenuItem(value: m.id, child: Text('${m.nombre} (${m.unidad})', overflow: TextOverflow.ellipsis))],
                      onChanged: (id) => setState(() => i.materiaId = id),
                    ),
                  ),
                  const SizedBox(width: 8),
                  Expanded(
                    flex: 2,
                    child: TextField(
                      controller: i.cantidad,
                      keyboardType: const TextInputType.numberWithOptions(decimal: true),
                      inputFormatters: numero,
                      decoration: const InputDecoration(labelText: 'Cantidad'),
                    ),
                  ),
                  if (_insumos.length > 1)
                    IconButton(onPressed: () => setState(() => _insumos.removeAt(n)), icon: const Icon(Icons.close)),
                ]),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(onPressed: () => setState(() => _insumos.add(_InsumoNuevo())), icon: const Icon(Icons.add), label: const Text('Otro insumo')),
              ),
            ]),
          ),
          const SizedBox(height: 12),
          FilledButton.icon(
            style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            onPressed: _guardando ? null : _guardar,
            icon: const Icon(Icons.check),
            label: Text(_guardando ? 'Publicando…' : 'Publicar receta'),
          ),
        ],
      ),
    );
  }
}
