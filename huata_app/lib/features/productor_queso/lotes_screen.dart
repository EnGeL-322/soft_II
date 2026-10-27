import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/quesos_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/lote_queso.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import '../../shared/oficina.dart' show puede;
import 'registrar_lote_screen.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Vista del Productor de queso (13, RF-88): lotes asignados/historial y ejecución real de un lote
/// nuevo (`RegistrarLoteScreen`, botón "+"), sin poder crear recetas ni cambiar costos ni precios —
/// esos botones directamente no existen en esta pantalla, no se ocultan con un permiso: este rol
/// tiene `produccion.write` (crear/cerrar lotes) pero nunca `recetas.write` ni `compras.write`.
class LotesProduccionScreen extends StatefulWidget {
  const LotesProduccionScreen({super.key});

  @override
  State<LotesProduccionScreen> createState() => _LotesProduccionScreenState();
}

class _LotesProduccionScreenState extends State<LotesProduccionScreen> with EnVivo<LotesProduccionScreen> {
  bool _cargando = true;
  String? _error;
  List<LoteQueso> _lotes = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'produccion', 'ventas'}, () => _cargar(silencioso: true));
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
      final hoy = DateTime.now();
      final desde = DateTime(hoy.year, hoy.month, hoy.day).subtract(const Duration(days: 30));
      final lotes = await context.read<QuesosApi>().lotes(desde: desde, hasta: hoy);
      setState(() {
        _lotes = lotes..sort((a, b) => b.fecha.compareTo(a.fecha));
        _cargando = false;
      });
    } catch (e) {
      // En vivo: si la recarga falla (sin señal), se queda lo que ya se veía.
      if (silencioso) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  Future<void> _abrirRegistrarLote() async {
    final mensaje = await Navigator.of(context).push<String>(
      MaterialPageRoute(builder: (_) => const RegistrarLoteScreen()),
    );
    if (mensaje != null && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(mensaje)));
      _cargar();
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Lotes de producción',
      // Con solo "ver producción" no se registra: el backend lo rechazaría igual.
      floatingActionButton: puede(context, 'produccion.write') || puede(context, 'quesos.write')
          ? FloatingActionButton.extended(
              onPressed: _abrirRegistrarLote,
              icon: const Icon(Icons.add),
              label: const Text('Registrar producción'),
            )
          : null,
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: _cargando
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(_error!, textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      OutlinedButton(onPressed: _cargar, child: const Text('Reintentar')),
                    ],
                  ),
                ),
              )
            : _lotes.isEmpty
            ? const Center(
                child: Padding(
                  padding: EdgeInsets.all(24),
                  child: Text('Sin lotes registrados en los últimos 30 días.', style: TextStyle(color: HuataColors.muted)),
                ),
              )
            : ListView.builder(
                padding: const EdgeInsets.all(16),
                itemCount: _lotes.length,
                itemBuilder: (context, i) => _LoteCard(lote: _lotes[i]),
              ),
      ),
    );
  }
}

class _LoteCard extends StatelessWidget {
  const _LoteCard({required this.lote});
  final LoteQueso lote;

  @override
  Widget build(BuildContext context) {
    return SeccionCard(
      titulo: '${lote.codigo} · ${lote.productoNombre}',
      subtitulo: lote.fecha,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          _FilaDato('Unidades producidas', '${lote.unidadesProducidas}'),
          if (lote.litrosDestinados != null) _FilaDato('Litros destinados', '${lote.litrosDestinados!.toStringAsFixed(1)} L'),
          if (lote.merma != null) _FilaDato('Merma', '${lote.merma}'),
          _FilaDato('Stock disponible', '${lote.stock}'),
          if (lote.costoUnitario != null) _FilaDato('Costo unitario', 'S/ ${lote.costoUnitario!.toStringAsFixed(2)}'),
          if (lote.responsable != null) _FilaDato('Responsable', lote.responsable!),
        ],
      ),
    );
  }
}

class _FilaDato extends StatelessWidget {
  const _FilaDato(this.etiqueta, this.valor);
  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [
          Text(etiqueta, style: const TextStyle(color: HuataColors.muted)),
          Text(valor, style: const TextStyle(fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}
