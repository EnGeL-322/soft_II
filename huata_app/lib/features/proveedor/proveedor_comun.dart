import 'package:flutter/material.dart';

import '../../core/models/recojo.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/graficos.dart';

/// Piezas que comparten las pantallas del proveedor (Inicio, Pagos y cobros, Historial de
/// producción): el selector de periodo, las cifras y las etiquetas de estado se ven igual en todas.

/// Rango del periodo que contiene a [referencia]: el día, la semana (lunes a domingo) o el mes.
(DateTime, DateTime) rangoPeriodo(String periodo, DateTime referencia) {
  final base = DateTime(referencia.year, referencia.month, referencia.day);
  if (periodo == 'DIA') return (base, base);
  if (periodo == 'MES') {
    return (DateTime(base.year, base.month, 1), DateTime(base.year, base.month + 1, 0));
  }
  final lunes = base.subtract(Duration(days: base.weekday - DateTime.monday));
  return (lunes, lunes.add(const Duration(days: 6)));
}

bool mismoDia(DateTime a, DateTime b) {
  final local = a.toLocal();
  return local.year == b.year && local.month == b.month && local.day == b.day;
}

String fechaIso(DateTime d) {
  final local = d.toLocal();
  return '${local.year}-${local.month.toString().padLeft(2, '0')}-${local.day.toString().padLeft(2, '0')}';
}

/// "2026-10-23" → "23/10/2026". Si no es una fecha, se muestra tal cual.
String fechaLegible(String iso) {
  final d = DateTime.tryParse(iso);
  if (d == null) return iso;
  return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';
}

Future<DateTime?> elegirFechaPeriodo(BuildContext context, DateTime actual) => showDatePicker(
  context: context,
  initialDate: actual,
  firstDate: DateTime(2020),
  lastDate: DateTime.now().add(const Duration(days: 1)),
);

/// Calidad de un grupo de entregas (un día): manda la peor. Sin análisis de todas, queda pendiente.
String calidadDeEntregas(List<Recojo> recojos) {
  final resultados = recojos.map((r) => r.resultadoCalidad).toList();
  if (resultados.contains('RECHAZADA')) return 'RECHAZADA';
  if (resultados.contains('OBSERVADA')) return 'OBSERVADA';
  if (resultados.isNotEmpty && resultados.every((r) => r == 'ACEPTADA')) return 'ACEPTADA';
  return 'PENDIENTE';
}

/// Mismos colores que las etiquetas de calidad (Buena, Observada, Mala, Pendiente).
Color colorCalidad(String resultado) => switch (resultado) {
  'ACEPTADA' => HuataColors.green2,
  'OBSERVADA' => const Color(0xFFE0A030),
  'RECHAZADA' => HuataColors.red,
  _ => const Color(0xFF94A3B8),
};

String etiquetaCalidad(String resultado) => switch (resultado) {
  'ACEPTADA' => 'buena',
  'OBSERVADA' => 'observada',
  'RECHAZADA' => 'mala',
  _ => 'pendiente',
};

const leyendaCalidad = [
  ('Buena', HuataColors.green2),
  ('Observada', Color(0xFFE0A030)),
  ('Mala', HuataColors.red),
  ('Pendiente', Color(0xFF94A3B8)),
];

const _diasCortos = ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom'];

/// Una barra por día entre [desde] y [hasta] (también los días sin entrega), con los litros del día
/// y el color de su calidad. Hasta una semana se rotula con el día de la semana; un mes, con el
/// número del día cada cinco días para que no se amontone.
List<BarraGrafico> barrasPorDia(List<Recojo> recojos, DateTime desde, DateTime hasta) {
  final porDia = <String, List<Recojo>>{};
  for (final r in recojos) {
    if (r.estado == 'CANCELLED' || r.litros <= 0) continue;
    porDia.putIfAbsent(fechaIso(r.fechaHora), () => []).add(r);
  }
  final dias = hasta.difference(desde).inDays + 1;
  return [
    for (var i = 0; i < dias; i++)
      () {
        final d = DateTime(desde.year, desde.month, desde.day + i);
        final delDia = porDia[fechaIso(d)] ?? const <Recojo>[];
        final litros = delDia.fold(0.0, (s, r) => s + r.litros);
        final calidad = calidadDeEntregas(delDia);
        final fecha = '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}';
        return BarraGrafico(
          etiqueta: dias <= 7 ? _diasCortos[d.weekday - 1] : (d.day == 1 || d.day % 5 == 0 ? '${d.day}' : ''),
          valor: litros,
          color: colorCalidad(calidad),
          detalle: litros > 0
              ? '${_diasCortos[d.weekday - 1]} $fecha: ${litros.toStringAsFixed(1)} L, ${etiquetaCalidad(calidad)}'
              : '${_diasCortos[d.weekday - 1]} $fecha: sin entrega',
        );
      }(),
  ];
}

/// Cabecera de una pantalla del proveedor: ícono, título y una línea que dice para qué sirve.
class CabeceraPantalla extends StatelessWidget {
  const CabeceraPantalla({super.key, required this.icono, required this.titulo, required this.detalle});
  final IconData icono;
  final String titulo;
  final String detalle;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.fromLTRB(4, 4, 4, 16),
    child: Row(children: [
      Container(
        width: 44,
        height: 44,
        decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(12)),
        child: Icon(icono, color: HuataColors.green),
      ),
      const SizedBox(width: 12),
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(titulo, style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w800, letterSpacing: -0.3)),
          const SizedBox(height: 2),
          Text(detalle, style: const TextStyle(color: HuataColors.muted, fontSize: 13.5)),
        ]),
      ),
    ]),
  );
}

/// Error de carga con su botón de reintento (sin conexión, servidor caído).
class ErrorConReintento extends StatelessWidget {
  const ErrorConReintento({super.key, required this.mensaje, required this.onReintentar});
  final String mensaje;
  final VoidCallback onReintentar;

  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(mainAxisSize: MainAxisSize.min, children: [
        Text(mensaje, textAlign: TextAlign.center),
        const SizedBox(height: 12),
        OutlinedButton(onPressed: onReintentar, child: const Text('Reintentar')),
      ]),
    ),
  );
}

class SelectorPeriodo extends StatelessWidget {
  const SelectorPeriodo({super.key, required this.periodo,
    required this.desde,
    required this.hasta,
    required this.onPeriodo,
    required this.onFecha,
  });
  final String periodo;
  final DateTime desde;
  final DateTime hasta;
  final ValueChanged<String> onPeriodo;
  final VoidCallback onFecha;

  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    SizedBox(
      width: double.infinity,
      child: SegmentedButton<String>(
        showSelectedIcon: false,
        segments: const [
          ButtonSegment(value: 'DIA', label: Text('Día')),
          ButtonSegment(value: 'SEMANA', label: Text('Semana')),
          ButtonSegment(value: 'MES', label: Text('Mes')),
        ],
        selected: {periodo},
        onSelectionChanged: (v) => onPeriodo(v.first),
      ),
    ),
    const SizedBox(height: 10),
    SizedBox(
      width: double.infinity,
      child: OutlinedButton.icon(
        onPressed: onFecha,
        icon: const Icon(Icons.calendar_month_outlined),
        label: Text('${_fecha(desde)} – ${_fecha(hasta)}'),
      ),
    ),
  ]);

  static String _fecha(DateTime d) =>
      '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')}/${d.year}';
}

class MontoCard extends StatelessWidget {
  const MontoCard({super.key, required this.etiqueta, required this.monto, required this.pagado});
  final String etiqueta;
  final double monto;
  final bool pagado;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.all(14),
    decoration: BoxDecoration(
      color: pagado ? HuataColors.greenSoft : HuataColors.blueSoft,
      borderRadius: BorderRadius.circular(14),
    ),
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Icon(pagado ? Icons.check_circle_outline : Icons.schedule, size: 16,
            color: pagado ? HuataColors.green : HuataColors.blue),
        const SizedBox(width: 6),
        Text(etiqueta.toUpperCase(),
            style: TextStyle(fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.6,
                color: pagado ? HuataColors.green : HuataColors.blue)),
      ]),
      const SizedBox(height: 8),
      FittedBox(
        fit: BoxFit.scaleDown,
        alignment: Alignment.centerLeft,
        child: Text(
          'S/ ${monto.toStringAsFixed(2)}',
          style: TextStyle(fontSize: 24, fontWeight: FontWeight.w800, letterSpacing: -0.5,
              color: pagado ? HuataColors.green : HuataColors.blue),
        ),
      ),
    ]),
  );
}

class EstadoPagoChip extends StatelessWidget {
  const EstadoPagoChip({super.key, required this.estado});
  final String estado;

  @override
  Widget build(BuildContext context) {
    final bien = estado == 'CLOSED' || estado == 'APROBADA';
    final mal = estado == 'RECHAZADA';
    final color = bien ? HuataColors.green : mal ? HuataColors.red : HuataColors.blue;
    final texto = switch (estado) {
      'CLOSED' => 'Pagado',
      'CALCULATED' => 'Calculado',
      'REVIEWED' => 'Revisado',
      'DRAFT' => 'Borrador',
      'PENDIENTE' => 'Pendiente',
      'APROBADA' => 'Aprobada',
      'RECHAZADA' => 'Rechazada',
      _ => estado,
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(color: color.withValues(alpha: 0.1), borderRadius: BorderRadius.circular(999)),
      child: Text(texto, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w700)),
    );
  }
}

class DiaProduccionCard extends StatelessWidget {
  const DiaProduccionCard({super.key, required this.fecha, required this.recojos});
  final String fecha;
  final List<Recojo> recojos;

  @override
  Widget build(BuildContext context) {
    final litros = recojos.fold(0.0, (s, r) => s + r.litros);
    final acopiadores = recojos.map((r) => r.acopiadorNombre ?? 'Sin identificar').toSet().join(', ');
    final calidad = calidadDeEntregas(recojos);
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        shape: const Border(),
        collapsedShape: const Border(),
        leading: const IconoRedondo(icono: Icons.calendar_today_outlined),
        title: Text('${fechaLegible(fecha)} · ${litros.toStringAsFixed(1)} L', style: const TextStyle(fontWeight: FontWeight.bold)),
        subtitle: Text('Acopió: $acopiadores'),
        trailing: CalidadChip(resultado: calidad),
        children: recojos
            .map((r) => ListTile(
                  dense: true,
                  leading: const Icon(Icons.local_drink_outlined),
                  title: Text('${r.litros.toStringAsFixed(1)} L · ${_hora(r.fechaHora)}'),
                  subtitle: Text('Acopiador: ${r.acopiadorNombre ?? 'Sin identificar'}'),
                  trailing: CalidadChip(resultado: r.resultadoCalidad ?? 'PENDIENTE'),
                ))
            .toList(),
      ),
    );
  }

  String _hora(DateTime d) {
    final local = d.toLocal();
    return '${local.hour.toString().padLeft(2, '0')}:${local.minute.toString().padLeft(2, '0')}';
  }
}

class CalidadChip extends StatelessWidget {
  const CalidadChip({super.key, required this.resultado});
  final String resultado;

  @override
  Widget build(BuildContext context) {
    final (texto, color, fondo) = switch (resultado) {
      'ACEPTADA' => ('Buena', HuataColors.ok, HuataColors.okSoft),
      'OBSERVADA' => ('Observada', HuataColors.amber, HuataColors.amberSoft),
      'RECHAZADA' => ('Mala', HuataColors.red, HuataColors.redSoft),
      _ => ('Pendiente', HuataColors.gray, HuataColors.graySoft),
    };
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
      decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(999)),
      child: Text(texto, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w700)),
    );
  }
}

class IconoRedondo extends StatelessWidget {
  const IconoRedondo({super.key, required this.icono});
  final IconData icono;

  @override
  Widget build(BuildContext context) => Container(
    width: 36,
    height: 36,
    decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(10)),
    child: Icon(icono, size: 19, color: HuataColors.green),
  );
}
