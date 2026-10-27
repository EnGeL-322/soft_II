import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/produccion_api.dart';
import '../../core/api/quesos_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/produccion.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

const _origenesLeche = {'ACOPIO_DEL_DIA': 'Acopio del día', 'STOCK_CAMARA': 'Stock de cámara'};

/// Ejecución real de un lote (3.2/RF-67/RF-69), mismo criterio que `produccion-queso.component.ts` en
/// la web: receta vigente (solo lectura), cuánto se pone a producir y "Ver descuento" para saber qué
/// se va a descontar. Lo que se descuenta lo decide el backend con la receta (receta × cantidad): ya
/// no se edita a mano. El costo también lo calcula el backend — esta pantalla no toca la receta
/// maestra (ese rol no tiene `recetas.write`).
class RegistrarLoteScreen extends StatefulWidget {
  const RegistrarLoteScreen({super.key});

  @override
  State<RegistrarLoteScreen> createState() => _RegistrarLoteScreenState();
}

class _RegistrarLoteScreenState extends State<RegistrarLoteScreen> {
  /// Qué se va a producir. Decide la receta y, con ella, las materias primas que se consumen.
  List<Producto> _productos = [];
  int? _productoId;
  String _origenLeche = 'ACOPIO_DEL_DIA';
  final _fecha = DateTime.now();
  final _cantidadObjetivoCtrl = TextEditingController(text: '100');
  /// Opcional: si salieron menos de lo puesto a producir. Vacío = salió lo mismo.
  final _unidadesCtrl = TextEditingController();
  final _responsableCtrl = TextEditingController();
  final _observacionesCtrl = TextEditingController();

  Receta? _receta;
  bool _cargandoReceta = false;
  bool _sinReceta = false;

  /// Lo que se va a descontar (receta × cantidad), solo para verlo: no se edita.
  List<SimulacionItem> _descuento = [];
  bool _simulando = false;
  bool? _alcanzaTodo;

  List<TrabajadorProduccion> _trabajadores = [];
  final List<_FilaManoObra> _manoObra = [];

  bool _guardando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargarProductos();
    _cargarTrabajadores();
  }

  @override
  void dispose() {
    _cantidadObjetivoCtrl.dispose();
    _unidadesCtrl.dispose();
    _responsableCtrl.dispose();
    _observacionesCtrl.dispose();
    for (final f in _manoObra) {
      f.montoCtrl.dispose();
    }
    super.dispose();
  }

  Future<void> _cargarProductos() async {
    try {
      final productos = await context.read<ProduccionApi>().productos();
      if (!mounted) return;
      setState(() {
        _productos = productos;
        _productoId = productos.isNotEmpty ? productos.first.id : null;
      });
      await _cargarReceta();
    } catch (e) {
      if (mounted) setState(() => _error = mensajeDeError(e));
    }
  }

  Future<void> _cargarReceta() async {
    final productoId = _productoId;
    if (productoId == null) return;
    setState(() {
      _cargandoReceta = true;
      _sinReceta = false;
      _receta = null;
      _descuento = [];
      _alcanzaTodo = null;
    });
    try {
      final receta = await context.read<ProduccionApi>().recetaVigente(productoId, _fecha);
      setState(() {
        _receta = receta;
        _cargandoReceta = false;
      });
    } catch (_) {
      setState(() {
        _sinReceta = true;
        _cargandoReceta = false;
      });
    }
  }

  Future<void> _cargarTrabajadores() async {
    try {
      final t = await context.read<ProduccionApi>().trabajadores();
      if (mounted) setState(() => _trabajadores = t);
    } catch (_) {
      // El formulario igual sirve sin mano de obra precargada; se puede guardar sin trabajadores.
    }
  }

  Future<void> _simular() async {
    final receta = _receta;
    final cantidad = int.tryParse(_cantidadObjetivoCtrl.text);
    if (receta == null || cantidad == null || cantidad <= 0) {
      setState(() => _error = 'Indica una receta vigente y una cantidad objetivo mayor a cero.');
      return;
    }
    setState(() {
      _simulando = true;
      _error = null;
    });
    try {
      final sim = await context.read<ProduccionApi>().simular(receta.id, cantidad);
      setState(() {
        _descuento = sim.items;
        _alcanzaTodo = sim.alcanzaTodo;
        _simulando = false;
      });
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _simulando = false;
      });
    }
  }

  void _agregarManoObra() {
    setState(() => _manoObra.add(_FilaManoObra()));
  }

  void _quitarManoObra(int i) {
    setState(() {
      _manoObra[i].montoCtrl.dispose();
      _manoObra.removeAt(i);
    });
  }

  Future<void> _guardar() async {
    final receta = _receta;
    final cantidad = int.tryParse(_cantidadObjetivoCtrl.text);
    // Vacío = salió lo mismo que se puso a producir.
    final unidades = _unidadesCtrl.text.trim().isEmpty ? cantidad : int.tryParse(_unidadesCtrl.text);
    final responsable = _responsableCtrl.text.trim();
    if (_guardando || receta == null || cantidad == null || cantidad <= 0 || unidades == null || unidades <= 0 ||
        responsable.isEmpty) {
      setState(() => _error = 'Faltan datos: receta, cantidad a producir o responsable.');
      return;
    }

    final manoObra = _manoObra
        .where((f) => f.trabajadorId != null && double.tryParse(f.montoCtrl.text) != null)
        .map((f) => ManoObraItem(trabajadorId: f.trabajadorId!, monto: double.parse(f.montoCtrl.text)))
        .toList();

    setState(() {
      _guardando = true;
      _error = null;
    });
    try {
      final lote = await context.read<QuesosApi>().crearLote(
        fecha: _fecha,
        productoId: receta.productoId,
        origenLeche: _origenLeche,
        cantidadObjetivo: cantidad,
        unidadesProducidas: unidades,
        responsable: responsable,
        observaciones: _observacionesCtrl.text.trim(),
        recetaId: receta.id,
        manoObra: manoObra,
      );
      if (mounted) {
        Navigator.of(context).pop(
          'Lote ${lote.codigo} registrado: ${lote.unidadesProducidas} unidades'
          '${lote.costoUnitario != null ? ' · costo unitario S/ ${lote.costoUnitario!.toStringAsFixed(2)}' : ''}.',
        );
      }
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _guardando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Registrar producción',
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          SeccionCard(
            titulo: 'Datos del lote',
            child: Column(
              children: [
                DropdownButtonFormField<int>(
                  key: ValueKey(_productos.length),
                  initialValue: _productoId,
                  decoration: const InputDecoration(labelText: 'Producto a elaborar'),
                  items: _productos
                      .map((p) => DropdownMenuItem(value: p.id, child: Text('${p.nombre} (${p.unidadVenta})')))
                      .toList(),
                  onChanged: (v) {
                    if (v == null) return;
                    setState(() => _productoId = v);
                    _cargarReceta();
                  },
                ),
                const SizedBox(height: 8),
                DropdownButtonFormField<String>(
                  initialValue: _origenLeche,
                  decoration: const InputDecoration(labelText: 'Origen de la leche'),
                  items: _origenesLeche.entries.map((e) => DropdownMenuItem(value: e.key, child: Text(e.value))).toList(),
                  onChanged: (v) => setState(() => _origenLeche = v ?? _origenLeche),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _responsableCtrl,
                  decoration: const InputDecoration(labelText: 'Responsable'),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _unidadesCtrl,
                  keyboardType: TextInputType.number,
                  decoration: const InputDecoration(
                    labelText: '¿Salieron menos? (opcional)',
                    helperText: 'Vacío = salió lo mismo que se puso a producir.',
                  ),
                ),
                const SizedBox(height: 8),
                TextField(
                  controller: _observacionesCtrl,
                  decoration: const InputDecoration(labelText: 'Observaciones / incidencias (opcional)'),
                  maxLines: 2,
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
          SeccionCard(
            titulo: 'Receta vigente (solo lectura)',
            subtitulo: 'Se descuenta la receta × la cantidad a producir.',
            child: _cargandoReceta
                ? const Center(child: CircularProgressIndicator())
                : _sinReceta
                    ? const Text('No hay receta vigente para este producto.', style: TextStyle(color: HuataColors.red))
                    : Column(
                        children: [
                          ...(_receta?.items ?? []).map(
                            (i) => Padding(
                              padding: const EdgeInsets.only(bottom: 4),
                              child: Row(
                                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                children: [
                                  Text(i.materiaPrimaNombre),
                                  Text('${i.cantidadPorUnidad} ${i.unidadMedida}/u', style: const TextStyle(color: HuataColors.muted)),
                                ],
                              ),
                            ),
                          ),
                          const SizedBox(height: 8),
                          Row(
                            children: [
                              Expanded(
                                child: TextField(
                                  controller: _cantidadObjetivoCtrl,
                                  keyboardType: TextInputType.number,
                                  decoration: const InputDecoration(labelText: 'Cantidad a producir (u.)'),
                                  onChanged: (_) => setState(() {
                                    _descuento = [];
                                    _alcanzaTodo = null;
                                  }),
                                ),
                              ),
                              const SizedBox(width: 8),
                              ElevatedButton(
                                onPressed: (_receta == null || _simulando) ? null : _simular,
                                child: _simulando
                                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                                    : const Text('Ver descuento'),
                              ),
                            ],
                          ),
                        ],
                      ),
          ),
          if (_alcanzaTodo == false) ...[
            const SizedBox(height: 12),
            const EstadoAviso(esError: true, mensaje: 'No alcanza el almacén para esa cantidad. Produce menos o pide una compra.'),
          ],
          if (_descuento.isNotEmpty) ...[
            const SizedBox(height: 12),
            SeccionCard(
              titulo: 'Se descontará del almacén',
              child: Column(
                children: [
                  for (final d in _descuento)
                    Padding(
                      padding: const EdgeInsets.only(bottom: 6),
                      child: Row(
                        children: [
                          Icon(d.alcanza ? Icons.check_circle : Icons.cancel,
                              size: 18, color: d.alcanza ? HuataColors.green : HuataColors.red),
                          const SizedBox(width: 8),
                          Expanded(child: Text(d.materiaPrimaNombre)),
                          Text('${d.cantidadRequerida} ${d.unidadMedida.toLowerCase()}',
                              style: const TextStyle(fontWeight: FontWeight.bold)),
                        ],
                      ),
                    ),
                ],
              ),
            ),
          ],
          const SizedBox(height: 12),
          SeccionCard(
            titulo: 'Mano de obra',
            subtitulo: 'Trabajadores que participaron en el lote y su pago (RF-62).',
            child: Column(
              children: [
                for (int i = 0; i < _manoObra.length; i++)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 8),
                    child: Row(
                      children: [
                        Expanded(
                          flex: 2,
                          child: DropdownButtonFormField<int>(
                            initialValue: _manoObra[i].trabajadorId,
                            decoration: const InputDecoration(isDense: true, labelText: 'Trabajador'),
                            items: _trabajadores
                                .map((t) => DropdownMenuItem(value: t.id, child: Text(t.nombre, overflow: TextOverflow.ellipsis)))
                                .toList(),
                            onChanged: (v) => setState(() => _manoObra[i].trabajadorId = v),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: TextField(
                            controller: _manoObra[i].montoCtrl,
                            keyboardType: TextInputType.number,
                            decoration: const InputDecoration(isDense: true, labelText: 'S/'),
                          ),
                        ),
                        IconButton(icon: const Icon(Icons.close), onPressed: () => _quitarManoObra(i)),
                      ],
                    ),
                  ),
                Align(
                  alignment: Alignment.centerLeft,
                  child: TextButton.icon(onPressed: _agregarManoObra, icon: const Icon(Icons.add), label: const Text('Agregar trabajador')),
                ),
              ],
            ),
          ),
          if (_error != null) ...[const SizedBox(height: 12), EstadoAviso(mensaje: _error!, esError: true)],
          const SizedBox(height: 16),
          FilledButton(
            onPressed: _guardando ? null : _guardar,
            child: _guardando
                ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                : const Text('Guardar lote'),
          ),
        ],
      ),
    );
  }
}

class _FilaManoObra {
  int? trabajadorId;
  final montoCtrl = TextEditingController();
}
