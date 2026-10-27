import 'dart:typed_data';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/calidad_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/calidad.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Análisis por aprobar (V49/V50): los que cumplen el estándar de calidad se aprueban solos; aquí
/// quedan los que tienen advertencia, y se aprueban desde la app, no en la web. La ve
/// quien tiene el permiso calidad.aprobar: la testeadora (desde su pantalla) y cualquier otra
/// persona a la que se le conceda desde la web (en ese caso es su pantalla de inicio).
class AprobacionCalidadScreen extends StatefulWidget {
  const AprobacionCalidadScreen({super.key});

  @override
  State<AprobacionCalidadScreen> createState() => _AprobacionCalidadScreenState();
}

class _AprobacionCalidadScreenState extends State<AprobacionCalidadScreen> with EnVivo<AprobacionCalidadScreen> {
  List<AnalisisPorAprobar> _pendientes = [];
  /// Los ya aprobados de la última semana (también los que se aprobaron solos): la pantalla nunca
  /// queda vacía cuando todo cumple el estándar, y se ve qué se analizó.
  List<AnalisisPorAprobar> _aprobados = [];
  bool _cargando = true;
  String? _error;
  int? _aprobando;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'calidad', 'acopio'}, () => _cargar(silencioso: true));
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
      final api = context.read<CalidadApi>();
      final lista = await api.porAprobar();
      List<AnalisisPorAprobar> aprobados = _aprobados;
      try {
        aprobados = (await api.recientes(dias: 7)).where((a) => a.aprobado).toList();
      } catch (_) {
        // Backend anterior a este endpoint: se muestran solo los pendientes.
      }
      if (!mounted) return;
      setState(() {
        _pendientes = lista;
        _aprobados = aprobados;
        _cargando = false;
      });
    } catch (e) {
      // En vivo: si la recarga falla (sin señal), se queda lo que ya se veía.
      if (silencioso) return;
      if (!mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  /// Solo se quita de la lista cuando el servidor confirma la aprobación.
  Future<void> _aprobar(AnalisisPorAprobar a) async {
    final api = context.read<CalidadApi>();
    final messenger = ScaffoldMessenger.of(context);
    setState(() => _aprobando = a.id);
    try {
      await api.aprobar(a.id);
      if (!mounted) return;
      setState(() {
        _pendientes.removeWhere((x) => x.id == a.id);
        _aprobando = null;
      });
      _cargar(silencioso: true);
      messenger.showSnackBar(SnackBar(content: Text('Análisis de ${a.productorNombre} aprobado.')));
    } catch (e) {
      if (!mounted) return;
      setState(() => _aprobando = null);
      messenger.showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
    }
  }

  Future<void> _verVoucher(AnalisisPorAprobar a) async {
    final api = context.read<CalidadApi>();
    await showDialog<void>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Voucher · ${a.productorNombre}'),
        content: FutureBuilder<Uint8List?>(
          future: api.fotoVoucher(a.id),
          builder: (_, snap) {
            if (snap.connectionState != ConnectionState.done) {
              return const SizedBox(height: 120, child: Center(child: CircularProgressIndicator()));
            }
            if (snap.hasError) return Text(mensajeDeError(snap.error!));
            if (snap.data == null) return const Text('Este análisis no tiene foto del voucher.');
            return InteractiveViewer(child: Image.memory(snap.data!));
          },
        ),
        actions: [TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('Cerrar'))],
      ),
    );
  }

  static String _texto(String resultado) =>
      {ResultadoCalidad.aceptada: 'Buena', ResultadoCalidad.observada: 'Observada', ResultadoCalidad.rechazada: 'Rechazada'}[resultado] ??
      resultado;

  static Color _color(String resultado) => resultado == ResultadoCalidad.aceptada
      ? HuataColors.green
      : resultado == ResultadoCalidad.observada
          ? HuataColors.amber
          : HuataColors.red;

  static String _fecha(DateTime? f) {
    if (f == null) return '—';
    String d(int n) => n.toString().padLeft(2, '0');
    return '${d(f.day)}/${d(f.month)} ${d(f.hour)}:${d(f.minute)}';
  }

  static String _quienAprobo(String? por) => por == null ? '' : (por == 'SISTEMA' ? 'Aprobado solo (cumple el estándar)' : 'Aprobó: $por');

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Aprobar análisis',
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            if (_error != null) EstadoAviso(mensaje: _error!, esError: true),
            if (_cargando)
              const Padding(padding: EdgeInsets.all(32), child: Center(child: CircularProgressIndicator()))
            else ...[
              _titulo('Por aprobar (${_pendientes.length})'),
              if (_pendientes.isEmpty)
                const EstadoAviso(mensaje: 'No hay análisis con advertencia: los que cumplen el estándar se aprueban solos.')
              else
                for (final a in _pendientes) _tarjeta(a, pendiente: true),
              const SizedBox(height: 12),
              _titulo('Aprobados · últimos 7 días (${_aprobados.length})'),
              if (_aprobados.isEmpty)
                const EstadoAviso(mensaje: 'Aún no hay análisis registrados en los últimos 7 días.')
              else
                for (final a in _aprobados) _tarjeta(a, pendiente: false),
            ],
          ],
        ),
      ),
    );
  }

  Widget _titulo(String texto) => Padding(
        padding: const EdgeInsets.only(bottom: 8, left: 2),
        child: Text(texto, style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w700)),
      );

  Widget _tarjeta(AnalisisPorAprobar a, {required bool pendiente}) => Card(
        margin: const EdgeInsets.only(bottom: 10),
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Expanded(
                    child: Text(a.productorNombre, style: const TextStyle(fontWeight: FontWeight.bold)),
                  ),
                  Text(_texto(a.resultado), style: TextStyle(color: _color(a.resultado), fontWeight: FontWeight.w600)),
                ],
              ),
              const SizedBox(height: 4),
              Text('${a.productorCodigo ?? ''} · ${a.origen} · ${_fecha(a.fechaHora)}',
                  style: const TextStyle(color: HuataColors.muted, fontSize: 13)),
              if (a.advertencia != null) ...[
                const SizedBox(height: 8),
                Container(
                  width: double.infinity,
                  padding: const EdgeInsets.all(8),
                  decoration: BoxDecoration(
                    color: HuataColors.amberSoft,
                    borderRadius: BorderRadius.circular(8),
                  ),
                  child: Text('No cumple el estándar: ${a.advertencia}',
                      style: const TextStyle(color: HuataColors.amber, fontSize: 13)),
                ),
              ],
              const SizedBox(height: 6),
              Text(
                [
                  if (a.densidad != null) 'Densidad ${a.densidad!.toStringAsFixed(a.densidad! < 2 ? 4 : 1)}',
                  if (a.grasa != null) 'Grasa ${a.grasa!.toStringAsFixed(2)} %',
                  if (a.temperatura != null) '${a.temperatura!.toStringAsFixed(1)} °C',
                  if (a.responsable != null) 'Registró: ${a.responsable}',
                ].join(' · '),
                style: const TextStyle(fontSize: 13),
              ),
              if (!pendiente) ...[
                const SizedBox(height: 4),
                Text(_quienAprobo(a.aprobadoPor), style: const TextStyle(color: HuataColors.green, fontSize: 13)),
              ],
              const SizedBox(height: 10),
              Row(
                children: [
                  OutlinedButton.icon(
                    onPressed: () => _verVoucher(a),
                    icon: const Icon(Icons.receipt_long_outlined, size: 18),
                    label: const Text('Voucher'),
                  ),
                  const Spacer(),
                  if (pendiente)
                    ElevatedButton(
                      onPressed: _aprobando == null ? () => _aprobar(a) : null,
                      child: _aprobando == a.id
                          ? const SizedBox(
                              height: 18,
                              width: 18,
                              child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                          : const Text('Aprobar'),
                    ),
                ],
              ),
            ],
          ),
        ),
      );
}
