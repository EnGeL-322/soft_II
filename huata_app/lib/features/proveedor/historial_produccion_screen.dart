import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/recojos_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/recojo.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/graficos.dart';
import '../../shared/huata_scaffold.dart';
import 'proveedor_comun.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Historial de producción del proveedor (RF-78/RF-79), en su propio módulo de la barra: la leche
/// entregada por día en el periodo elegido, su calidad y quién hizo el acopio.
class HistorialProduccionScreen extends StatefulWidget {
  const HistorialProduccionScreen({super.key});

  @override
  State<HistorialProduccionScreen> createState() => _HistorialProduccionScreenState();
}

class _HistorialProduccionScreenState extends State<HistorialProduccionScreen> with EnVivo<HistorialProduccionScreen> {
  bool _cargando = true;
  String? _error;
  List<Recojo> _recojos = [];
  String _periodo = 'SEMANA';
  DateTime _fecha = DateTime.now();

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'acopio', 'calidad'}, () => _cargar(silencioso: true));
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
      final recojos = await context.read<RecojosApi>().misRecojos(size: 500);
      if (!mounted) return;
      setState(() {
        _recojos = recojos..sort((a, b) => b.fechaHora.compareTo(a.fechaHora));
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

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Historial de producción',
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: _cargando
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? ErrorConReintento(mensaje: _error!, onReintentar: _cargar)
            : _contenido(),
      ),
    );
  }

  Widget _contenido() {
    final rango = rangoPeriodo(_periodo, _fecha);
    final delPeriodo = _recojos.where((r) {
      final local = r.fechaHora.toLocal();
      final dia = DateTime(local.year, local.month, local.day);
      return r.estado != 'CANCELLED' && r.litros > 0 && !dia.isBefore(rango.$1) && !dia.isAfter(rango.$2);
    }).toList();
    final porDia = <String, List<Recojo>>{};
    for (final recojo in delPeriodo) {
      porDia.putIfAbsent(fechaIso(recojo.fechaHora), () => []).add(recojo);
    }
    final total = delPeriodo.fold(0.0, (s, r) => s + r.litros);
    final promedio = porDia.isEmpty ? 0.0 : total / porDia.length;
    int cuantas(String resultado) => delPeriodo.where((r) => (r.resultadoCalidad ?? 'PENDIENTE') == resultado).length;
    final buenas = cuantas('ACEPTADA');
    final observadas = cuantas('OBSERVADA');
    final malas = cuantas('RECHAZADA');
    final pendientes = delPeriodo.length - buenas - observadas - malas;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const CabeceraPantalla(
          icono: Icons.water_drop_outlined,
          titulo: 'Historial de producción',
          detalle: 'Tu leche entregada por día, su calidad y quién la recogió.',
        ),
        SeccionCard(
          icono: Icons.event_note_outlined,
          titulo: 'Periodo',
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SelectorPeriodo(
              periodo: _periodo,
              desde: rango.$1,
              hasta: rango.$2,
              onPeriodo: (p) => setState(() => _periodo = p),
              onFecha: () async {
                final fecha = await elegirFechaPeriodo(context, _fecha);
                if (fecha != null) setState(() => _fecha = fecha);
              },
            ),
            const SizedBox(height: 14),
            Row(children: [
              Expanded(child: DatoClave(etiqueta: 'Total', valor: '${total.toStringAsFixed(1)} L')),
              const SizedBox(width: 8),
              Expanded(child: DatoClave(etiqueta: 'Días', valor: '${porDia.length}')),
              const SizedBox(width: 8),
              Expanded(child: DatoClave(etiqueta: 'Promedio/día', valor: '${promedio.toStringAsFixed(1)} L')),
            ]),
          ]),
        ),
        if (_periodo != 'DIA') ...[
          const SizedBox(height: 12),
          SeccionCard(
            icono: Icons.bar_chart_rounded,
            titulo: 'Litros por día',
            subtitulo: 'El color de cada barra es la calidad de ese día. Mantén pulsada una barra para ver el detalle.',
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              GraficoBarras(barras: barrasPorDia(delPeriodo, rango.$1, rango.$2), alto: 160),
              const SizedBox(height: 8),
              const LeyendaGrafico(elementos: leyendaCalidad),
            ]),
          ),
        ],
        if (delPeriodo.isNotEmpty) ...[
          const SizedBox(height: 12),
          SeccionCard(
            icono: Icons.verified_outlined,
            titulo: 'Calidad del periodo',
            child: GraficoDona(
              textoCentro: 'entregas',
              tramos: [
                TramoDona(etiqueta: 'Buena', valor: buenas, color: colorCalidad('ACEPTADA')),
                TramoDona(etiqueta: 'Observada', valor: observadas, color: colorCalidad('OBSERVADA')),
                TramoDona(etiqueta: 'Mala', valor: malas, color: colorCalidad('RECHAZADA')),
                TramoDona(etiqueta: 'Pendiente', valor: pendientes, color: colorCalidad('PENDIENTE')),
              ],
            ),
          ),
        ],
        const SizedBox(height: 12),
        EncabezadoSeccion('Entregas por día'),
        if (porDia.isEmpty)
          const SeccionCard(
            child: Text('Sin producción en el periodo seleccionado.', style: TextStyle(color: HuataColors.muted)),
          )
        else
          ...porDia.entries.map((e) => DiaProduccionCard(fecha: e.key, recojos: e.value)),
      ],
    );
  }
}
