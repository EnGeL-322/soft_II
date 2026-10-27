import 'dart:math' as math;

import 'package:flutter/material.dart';

import '../core/theme/huata_theme.dart';

/// Gráficos simples hechos con widgets de Flutter (sin librería externa): barras por día y dona por
/// categoría. Cada barra y cada tramo lleva su texto para lectores de pantalla.

class BarraGrafico {
  const BarraGrafico({required this.etiqueta, required this.valor, required this.color, this.detalle});

  /// Texto bajo la barra ("L", "12"...). Vacío para no mostrarlo (meses con muchos días).
  final String etiqueta;
  final double valor;
  final Color color;

  /// Texto completo para el tooltip y lectores de pantalla ("Lun 20/10: 42.0 L, buena").
  final String? detalle;
}

/// Barras verticales con el valor encima de cada una (si caben) y la etiqueta debajo.
class GraficoBarras extends StatelessWidget {
  const GraficoBarras({super.key, required this.barras, this.unidad = 'L', this.alto = 150});

  final List<BarraGrafico> barras;
  final String unidad;
  final double alto;

  @override
  Widget build(BuildContext context) {
    final maximo = barras.fold(0.0, (m, b) => math.max(m, b.valor));
    final conValores = barras.length <= 10;
    return SizedBox(
      // Alto de las barras + el valor encima (~16) + la etiqueta debajo (16) + separaciones.
      height: alto + 48,
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.end,
        children: [
          for (final b in barras)
            Expanded(
              child: Semantics(
                label: b.detalle ?? '${b.etiqueta}: ${b.valor.toStringAsFixed(1)} $unidad',
                excludeSemantics: true,
                child: Tooltip(
                  message: b.detalle ?? '${b.valor.toStringAsFixed(1)} $unidad',
                  child: Padding(
                    padding: EdgeInsets.symmetric(horizontal: barras.length > 14 ? 1 : 4),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        if (conValores)
                          FittedBox(
                            fit: BoxFit.scaleDown,
                            child: Text(
                              b.valor > 0 ? b.valor.toStringAsFixed(b.valor >= 100 ? 0 : 1) : '',
                              style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700, color: HuataColors.text),
                            ),
                          ),
                        const SizedBox(height: 3),
                        TweenAnimationBuilder<double>(
                          tween: Tween(begin: 0, end: maximo == 0 ? 0 : b.valor / maximo),
                          duration: const Duration(milliseconds: 450),
                          curve: Curves.easeOutCubic,
                          builder: (_, f, _) => Container(
                            // Un día sin entrega se ve como una raya, no como un hueco.
                            height: math.max(3, alto * f),
                            decoration: BoxDecoration(
                              color: b.valor > 0 ? b.color : HuataColors.line,
                              borderRadius: const BorderRadius.vertical(top: Radius.circular(6)),
                            ),
                          ),
                        ),
                        const SizedBox(height: 6),
                        SizedBox(
                          height: 16,
                          child: FittedBox(
                            fit: BoxFit.scaleDown,
                            child: Text(b.etiqueta, style: const TextStyle(fontSize: 11.5, color: HuataColors.muted)),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class TramoDona {
  const TramoDona({required this.etiqueta, required this.valor, required this.color});
  final String etiqueta;
  final int valor;
  final Color color;
}

/// Dona con el total al centro y la leyenda a la derecha (cantidad y porcentaje por tramo).
class GraficoDona extends StatelessWidget {
  const GraficoDona({super.key, required this.tramos, required this.textoCentro, this.tamano = 120});

  final List<TramoDona> tramos;
  final String textoCentro;
  final double tamano;

  @override
  Widget build(BuildContext context) {
    final total = tramos.fold(0, (s, t) => s + t.valor);
    return Row(
      children: [
        Semantics(
          label: tramos.map((t) => '${t.etiqueta}: ${t.valor}').join(', '),
          excludeSemantics: true,
          child: SizedBox(
            width: tamano,
            height: tamano,
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: 1),
              duration: const Duration(milliseconds: 600),
              curve: Curves.easeOutCubic,
              builder: (_, avance, child) => CustomPaint(
                painter: _PintorDona(tramos: tramos, total: total, avance: avance),
                child: child,
              ),
              child: Center(
                child: Column(mainAxisSize: MainAxisSize.min, children: [
                  Text('$total', style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, letterSpacing: -0.4)),
                  Text(textoCentro, style: const TextStyle(fontSize: 11, color: HuataColors.muted)),
                ]),
              ),
            ),
          ),
        ),
        const SizedBox(width: 18),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              for (final t in tramos)
                Padding(
                  padding: const EdgeInsets.symmetric(vertical: 4),
                  child: Row(children: [
                    Container(
                      width: 10,
                      height: 10,
                      decoration: BoxDecoration(color: t.color, borderRadius: BorderRadius.circular(3)),
                    ),
                    const SizedBox(width: 8),
                    Expanded(child: Text(t.etiqueta, style: const TextStyle(fontSize: 13.5))),
                    Text('${t.valor}', style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 13.5)),
                    SizedBox(
                      width: 46,
                      child: Text(
                        total == 0 ? '' : '${(t.valor * 100 / total).round()}%',
                        textAlign: TextAlign.right,
                        style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                      ),
                    ),
                  ]),
                ),
            ],
          ),
        ),
      ],
    );
  }
}

class _PintorDona extends CustomPainter {
  _PintorDona({required this.tramos, required this.total, required this.avance});
  final List<TramoDona> tramos;
  final int total;
  final double avance;

  @override
  void paint(Canvas canvas, Size size) {
    const grosor = 16.0;
    final rect = Rect.fromCircle(center: size.center(Offset.zero), radius: size.shortestSide / 2 - grosor / 2);
    final pincel = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = grosor
      ..strokeCap = StrokeCap.butt;
    if (total == 0) {
      canvas.drawArc(rect, 0, 2 * math.pi, false, pincel..color = HuataColors.line);
      return;
    }
    // Un pequeño espacio entre tramos para que se distingan sin depender solo del color.
    final conValor = tramos.where((t) => t.valor > 0).length;
    final hueco = conValor > 1 ? 0.04 : 0.0;
    var inicio = -math.pi / 2;
    for (final t in tramos) {
      if (t.valor == 0) continue;
      final barrido = 2 * math.pi * t.valor / total * avance;
      canvas.drawArc(rect, inicio + hueco / 2, math.max(0, barrido - hueco), false, pincel..color = t.color);
      inicio += barrido;
    }
  }

  @override
  bool shouldRepaint(_PintorDona old) => old.avance != avance || old.total != total || old.tramos != tramos;
}

/// Leyenda horizontal para los colores de un gráfico de barras.
class LeyendaGrafico extends StatelessWidget {
  const LeyendaGrafico({super.key, required this.elementos});
  final List<(String, Color)> elementos;

  @override
  Widget build(BuildContext context) => Wrap(
    spacing: 14,
    runSpacing: 6,
    children: [
      for (final (texto, color) in elementos)
        Row(mainAxisSize: MainAxisSize.min, children: [
          Container(width: 10, height: 10, decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(3))),
          const SizedBox(width: 6),
          Text(texto, style: const TextStyle(fontSize: 12, color: HuataColors.muted)),
        ]),
    ],
  );
}
