import 'package:flutter/material.dart';

import '../../core/models/acopiador.dart';
import '../../core/models/recojo.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/graficos.dart';
import '../../shared/huata_scaffold.dart';
import 'parada_ruta.dart';

/// Vista "Resumen": mi zona y mi reporte del día o de la semana.
class VistaResumen extends StatelessWidget {
  const VistaResumen({
    super.key,
    required this.yo,
    required this.reporte,
    required this.periodo,
    required this.onPeriodo,
    this.cabecera,
    this.aviso,
  });

  final Acopiador yo;
  final ReporteAcopiador? reporte;
  final String periodo;
  final ValueChanged<String> onPeriodo;

  /// Cabecera de la jornada (si hay ruta hoy).
  final Widget? cabecera;

  /// Mensaje destacado arriba (p. ej. "sin ruta asignada para hoy").
  final Widget? aviso;

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      children: [
        if (cabecera != null) ...[cabecera!, const SizedBox(height: 16)],
        if (aviso != null) ...[aviso!, const SizedBox(height: 16)],
        _TarjetaZona(yo: yo),
        const SizedBox(height: 16),
        SeccionCard(
          titulo: 'Mi reporte',
          subtitulo: reporte == null
              ? null
              : reporte!.desde == reporte!.hasta
              ? fechaCortaIso(reporte!.desde)
              : '${fechaCortaIso(reporte!.desde)} – ${fechaCortaIso(reporte!.hasta)}',
          icono: Icons.insights_outlined,
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              SegmentedButton<String>(
                showSelectedIcon: false,
                segments: const [
                  ButtonSegment(value: 'DIA', label: Text('Hoy'), icon: Icon(Icons.today_outlined, size: 18)),
                  ButtonSegment(value: 'SEMANA', label: Text('Semana'), icon: Icon(Icons.date_range_outlined, size: 18)),
                ],
                selected: {periodo},
                onSelectionChanged: (v) => onPeriodo(v.first),
              ),
              const SizedBox(height: 14),
              if (reporte == null)
                const Padding(
                  padding: EdgeInsets.all(24),
                  child: Center(child: CircularProgressIndicator()),
                )
              else ...[
                GridView.count(
                  crossAxisCount: 2,
                  shrinkWrap: true,
                  physics: const NeverScrollableScrollPhysics(),
                  mainAxisSpacing: 10,
                  crossAxisSpacing: 10,
                  childAspectRatio: 1.9,
                  children: [
                    DatoClave(
                      etiqueta: 'Litros',
                      valor: formatoLitros(reporte!.litros),
                      icono: Icons.water_drop_outlined,
                      color: HuataColors.green,
                      fondo: HuataColors.greenSoft,
                    ),
                    DatoClave(etiqueta: 'Recepciones', valor: '${reporte!.recepciones}', icono: Icons.inventory_2_outlined),
                    DatoClave(etiqueta: 'Visitados', valor: '${reporte!.proveedoresVisitados}', icono: Icons.people_outline),
                    DatoClave(
                      etiqueta: 'No entregaron',
                      valor: '${reporte!.noEntregados}',
                      icono: Icons.block_outlined,
                      color: reporte!.noEntregados > 0 ? HuataColors.red : null,
                      fondo: reporte!.noEntregados > 0 ? HuataColors.redSoft : null,
                    ),
                  ],
                ),
                if (periodo == 'SEMANA' && reporte!.dias.isNotEmpty) ...[
                  const SizedBox(height: 18),
                  GraficoBarras(
                    alto: 110,
                    barras: [
                      for (final d in reporte!.dias)
                        BarraGrafico(
                          etiqueta: diaCortoIso(d.fecha),
                          valor: d.litros,
                          color: HuataColors.green2,
                          detalle: '${fechaCortaIso(d.fecha)}: ${formatoLitros(d.litros)} · ${d.recepciones} recojos',
                        ),
                    ],
                  ),
                  const SizedBox(height: 8),
                  const Divider(),
                  for (final d in reporte!.dias.reversed) _FilaDia(dia: d),
                ],
              ],
            ],
          ),
        ),
      ],
    );
  }
}

class _TarjetaZona extends StatelessWidget {
  const _TarjetaZona({required this.yo});
  final Acopiador yo;

  @override
  Widget build(BuildContext context) {
    return SeccionCard(
      child: Row(
        children: [
          Container(
            width: 46,
            height: 46,
            decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(14)),
            child: const Icon(Icons.location_on_outlined, color: HuataColors.green),
          ),
          const SizedBox(width: 14),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Text('MI ZONA DE ACOPIO',
                    style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.6, color: HuataColors.muted)),
                const SizedBox(height: 2),
                Text(yo.zona ?? 'Sin zona asignada', style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
                Text(yo.rutaNombre ?? 'Sin ruta habitual', style: const TextStyle(color: HuataColors.muted, fontSize: 13)),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _FilaDia extends StatelessWidget {
  const _FilaDia({required this.dia});
  final ReporteAcopiadorDia dia;

  @override
  Widget build(BuildContext context) {
    final fecha = DateTime.tryParse(dia.fecha);
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 8),
      child: Row(
        children: [
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  fecha == null ? dia.fecha : fechaLarga(fecha),
                  style: const TextStyle(fontWeight: FontWeight.w600),
                ),
                Text(
                  '${dia.recepciones} recojos · ${dia.proveedoresVisitados} visitados'
                  '${dia.noEntregados > 0 ? ' · ${dia.noEntregados} no entregaron' : ''}',
                  style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                ),
              ],
            ),
          ),
          Text(formatoLitros(dia.litros), style: const TextStyle(fontWeight: FontWeight.w800)),
        ],
      ),
    );
  }
}
