import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/models/recojo.dart';
import 'package:huata_app/features/proveedor/proveedor_comun.dart';
import 'package:huata_app/shared/graficos.dart';

Recojo _recojo(DateTime fecha, double litros, {String? calidad, String estado = 'REGISTERED'}) => Recojo(
  id: fecha.millisecondsSinceEpoch,
  productorId: 1,
  productorNombre: 'Ana',
  fechaHora: fecha,
  litros: litros,
  estado: estado,
  resultadoCalidad: calidad,
);

Widget _app(Widget hijo) => MaterialApp(home: Scaffold(body: Padding(padding: const EdgeInsets.all(16), child: hijo)));

void main() {
  group('barras por día', () {
    final lunes = DateTime(2026, 10, 19);
    final domingo = DateTime(2026, 10, 25);

    test('hay una barra por día, también los días sin entrega', () {
      final barras = barrasPorDia([_recojo(lunes.add(const Duration(hours: 7)), 40, calidad: 'ACEPTADA')], lunes, domingo);
      expect(barras.map((b) => b.etiqueta), ['Lun', 'Mar', 'Mié', 'Jue', 'Vie', 'Sáb', 'Dom']);
      expect(barras.first.valor, 40);
      expect(barras.skip(1).every((b) => b.valor == 0), isTrue);
      expect(barras[1].detalle, contains('sin entrega'));
    });

    test('suma las entregas del día, ignora las anuladas y pinta la peor calidad', () {
      final martes = lunes.add(const Duration(days: 1, hours: 7));
      final barras = barrasPorDia([
        _recojo(martes, 20, calidad: 'ACEPTADA'),
        _recojo(martes.add(const Duration(hours: 5)), 15, calidad: 'OBSERVADA'),
        _recojo(martes, 99, estado: 'CANCELLED'),
      ], lunes, domingo);
      expect(barras[1].valor, 35);
      expect(barras[1].color, colorCalidad('OBSERVADA'));
    });

    test('en un mes solo se rotulan algunos días para que no se amontonen', () {
      final barras = barrasPorDia([], DateTime(2026, 10, 1), DateTime(2026, 10, 31));
      expect(barras, hasLength(31));
      expect(barras.where((b) => b.etiqueta.isNotEmpty).map((b) => b.etiqueta), ['1', '5', '10', '15', '20', '25', '30']);
    });
  });

  testWidgets('el gráfico de barras muestra el valor de cada día', (tester) async {
    await tester.pumpWidget(_app(const GraficoBarras(barras: [
      BarraGrafico(etiqueta: 'Lun', valor: 42, color: Colors.green),
      BarraGrafico(etiqueta: 'Mar', valor: 0, color: Colors.green),
    ])));
    await tester.pumpAndSettle();
    expect(find.text('Lun'), findsOneWidget);
    expect(find.text('42.0'), findsOneWidget);
  });

  testWidgets('la dona muestra el total y el porcentaje de cada tramo', (tester) async {
    await tester.pumpWidget(_app(const GraficoDona(textoCentro: 'entregas', tramos: [
      TramoDona(etiqueta: 'Buena', valor: 3, color: Colors.green),
      TramoDona(etiqueta: 'Mala', valor: 1, color: Colors.red),
    ])));
    await tester.pumpAndSettle();
    expect(find.text('4'), findsOneWidget);
    expect(find.text('75%'), findsOneWidget);
    expect(find.text('25%'), findsOneWidget);
  });

  testWidgets('sin entregas la dona no falla ni muestra porcentajes', (tester) async {
    await tester.pumpWidget(_app(const GraficoDona(textoCentro: 'entregas', tramos: [
      TramoDona(etiqueta: 'Buena', valor: 0, color: Colors.green),
    ])));
    await tester.pumpAndSettle();
    expect(find.text('0'), findsWidgets);
    expect(find.textContaining('%'), findsNothing);
  });
}
