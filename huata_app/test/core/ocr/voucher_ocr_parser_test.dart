import 'dart:math' as math;

import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/ocr/voucher_ocr_parser.dart';

void main() {
  group('VoucherOcrParser', () {
    // Muestra real, CLAUDE.md sección 10 ("SmartScan AI - 2026-08-19 05:53").
    test('lee un voucher SmartScan AI', () {
      const texto =
          'SmartScan AI - 2026-08-19 05:53: SN 48359; Mode 1; Temp 12.2 C; Grasa 4.05%; SNG 8.95%; '
          'Densidad 31.4; Proteína 3.40%; Lactosa 4.87%; Sales 0.7%; Total sólidos 13.0%; '
          'Agua añadida 0.0%; Punto cong. -0.562 C; pH 11.1.';

      final lectura = VoucherOcrParser.parsear(texto);

      expect(lectura.analizadorSn, '48359');
      expect(lectura.temperatura, 12.2);
      expect(lectura.densidad, 31.4);
      expect(lectura.puntoCongelacion, -0.562);
    });

    // Muestra real, CLAUDE.md sección 10 ("LACTOMAT - 2026-08-19 06:21").
    test('lee un voucher LACTOMAT', () {
      const texto =
          'LACTOMAT - 2026-08-19 06:21: SN 49730; Mode 1; Temp 16.9 C; Grasa 2.3%; SNG 6.63%; '
          'Densidad 23.9; Proteína 2.5%; Lactosa 3.6%; Sales 0.4%; Total sólidos 9.0%; '
          'Agua añadida 23.0%; Punto cong. -0.392 C; pH 11.5.';

      final lectura = VoucherOcrParser.parsear(texto);

      expect(lectura.analizadorSn, '49730');
      expect(lectura.temperatura, 16.9);
      expect(lectura.densidad, 23.9);
      expect(lectura.puntoCongelacion, -0.392);
    });

    test('lee también grasa, SNG, sólidos totales y agua añadida', () {
      const texto =
          'SmartScan AI - 2026-08-19 05:53: SN 48359; Mode 1; Temp 12.2 C; Grasa 4.05%; SNG 8.95%; '
          'Densidad 31.4; Proteína 3.40%; Lactosa 4.87%; Sales 0.7%; Total sólidos 13.0%; '
          'Agua añadida 0.0%; Punto cong. -0.562 C; pH 11.1.';

      final lectura = VoucherOcrParser.parsear(texto);

      expect(lectura.grasa, 4.05);
      expect(lectura.solidosNoGrasos, 8.95);
      expect(lectura.solidosTotales, 13.0);
      expect(lectura.aguaAnadidaPorcentaje, 0.0);
      expect(lectura.camposLeidos, 7);
    });

    // Así devuelve ML Kit un voucher real: la columna de etiquetas en un bloque y la de valores en
    // otro. `RecognizedText.text` queda con todas las etiquetas primero y todos los valores después,
    // y el parser anterior no encontraba nada. Con la posición de cada línea se rearman las filas.
    test('reconstruye las filas cuando el OCR separa etiquetas y valores en columnas', () {
      LineaOcr l(String t, double x, double y) => LineaOcr(t, izquierda: x, arriba: y, abajo: y + 20);
      final lineas = [
        // Columna izquierda (bloque 1)
        l('SN', 10, 0), l('Temp', 10, 30), l('Grasa', 10, 60), l('SNG', 10, 90), l('Densidad', 10, 120),
        l('Total sólidos', 10, 150), l('Agua añadida', 10, 180), l('Punto cong.', 10, 210),
        // Columna derecha (bloque 2), con leve desalineación vertical como en una foto real
        l('49730', 200, 2), l('16.9 C', 200, 31), l('2.3 %', 200, 62), l('6.63 %', 200, 89), l('23.9', 200, 123),
        l('9.0 %', 200, 151), l('23.0 %', 200, 178), l('–0.392 C', 200, 212),
      ];

      final plano = VoucherOcrParser.parsear(lineas.map((x) => x.texto).join('\n'));
      expect(plano.densidad, isNull, reason: 'sin posiciones el valor no queda junto a su etiqueta');

      final lectura = VoucherOcrParser.parsearLineas(lineas);
      expect(lectura.analizadorSn, '49730');
      expect(lectura.temperatura, 16.9);
      expect(lectura.grasa, 2.3);
      expect(lectura.solidosNoGrasos, 6.63);
      expect(lectura.densidad, 23.9);
      expect(lectura.solidosTotales, 9.0);
      expect(lectura.aguaAnadidaPorcentaje, 23.0);
      // El guion largo que devuelve el OCR se lee como signo menos, sin cambiar el valor (RN-13).
      expect(lectura.puntoCongelacion, -0.392);
    });

    test('acepta etiquetas en inglés y coma decimal', () {
      const texto = 'S/N: A1234\nTemperature: 14,5\nFat 3,8\nSNF 8,6\nDensity 30,2\nFreezing point -0,540\nAdded water 0,0';
      final lectura = VoucherOcrParser.parsear(texto);

      expect(lectura.analizadorSn, 'A1234');
      expect(lectura.temperatura, 14.5);
      expect(lectura.grasa, 3.8);
      expect(lectura.solidosNoGrasos, 8.6);
      expect(lectura.densidad, 30.2);
      expect(lectura.puntoCongelacion, -0.54);
      expect(lectura.aguaAnadidaPorcentaje, 0.0);
    });

    test('corrige la letra O leída en lugar de cero dentro de un número', () {
      final lectura = VoucherOcrParser.parsear('Densidad 3O.5\nPunto cong. -O.551');
      expect(lectura.densidad, 30.5);
      expect(lectura.puntoCongelacion, -0.551);
    });

    test('un valor atípico se conserva tal como aparece (RN-13)', () {
      final lectura = VoucherOcrParser.parsear('Densidad 23.9\npH 11.5');
      expect(lectura.densidad, 23.9);
    });

    // Filas tal como las devolvió ML Kit real en el emulador sobre W.jpeg / W1.jpeg giradas ±7° y a
    // media resolución: ruido de OCR dentro de los puntos guía y etiquetas mal leídas.
    test('tolera el ruido real del OCR entre la etiqueta y el valor', () {
      const filas = 'SN: 48359 Moce:\n'
          'Temp. 12.2 C\n'
          'Grasa. ......... 4.05%\n'
          'SNG... d8.95X\n'
          'Densidad....r ...31.4\n'
          'Tota! so lidos.....13.0%\n'
          'Agua anadida...* 0.0%\n'
          'Punto cong..... .-0.562 C';
      final l = VoucherOcrParser.parsear(filas);
      expect(l.analizadorSn, '48359');
      expect(l.temperatura, 12.2);
      expect(l.grasa, 4.05);
      expect(l.solidosNoGrasos, 8.95);
      expect(l.densidad, 31.4);
      expect(l.solidosTotales, 13.0);
      expect(l.aguaAnadidaPorcentaje, 0.0);
      expect(l.puntoCongelacion, -0.562);
      expect(l.advertencias, isEmpty);
    });

    test('reconoce "pensidad" (D leída como p) y "SNGwe"', () {
      final l = VoucherOcrParser.parsear('pensidad.. se..23.9\nSNGwe 6.63*');
      expect(l.densidad, 23.9);
      expect(l.solidosNoGrasos, 6.63);
    });

    test('un guion en los puntos guía no vuelve negativo un campo que no lleva signo', () {
      expect(VoucherOcrParser.parsear('Grasa.-....4.05%').grasa, 4.05);
    });

    test('nunca toma el valor de la fila de al lado', () {
      final l = VoucherOcrParser.parsear('SNG.\n6.63%\nDensidad....23.9');
      expect(l.solidosNoGrasos, isNull);
      expect(l.densidad, 23.9);
    });

    test('punto de congelación positivo: se conserva como se leyó y se avisa (RN-13)', () {
      final l = VoucherOcrParser.parsear('Punto cong.....0.392 C');
      expect(l.puntoCongelacion, 0.392);
      expect(l.advertencias.single, contains('signo menos'));
    });

    // La foto inclinada: la columna de valores sube hacia la derecha. Con la caja horizontal cada
    // valor caía en la fila de arriba; con las esquinas rotadas (lo que da ML Kit) se endereza.
    test('endereza una foto inclinada antes de armar las filas', () {
      const grados = 7.0;
      final a = grados * math.pi / 180;
      LineaOcr girada(String t, double x, double y, double ancho) {
        (double, double) rot(double px, double py) => (px * math.cos(a) - py * math.sin(a), px * math.sin(a) + py * math.cos(a));
        return LineaOcr.conEsquinas(t, [rot(x, y), rot(x + ancho, y), rot(x + ancho, y + 20), rot(x, y + 20)]);
      }

      final lineas = [
        girada('Grasa.', 0, 0, 60), girada('SNG.', 0, 30, 40), girada('Densidad.....', 0, 60, 120),
        girada('2.3%', 300, 0, 50), girada('6.63%', 300, 30, 60), girada('23.9', 300, 60, 50),
      ];
      final l = VoucherOcrParser.parsearLineas(lineas);
      expect(l.grasa, 2.3);
      expect(l.solidosNoGrasos, 6.63);
      expect(l.densidad, 23.9);
    });

    test('un texto sin las etiquetas esperadas no inventa valores', () {
      final lectura = VoucherOcrParser.parsear('foto borrosa, texto ilegible');

      expect(lectura.analizadorSn, isNull);
      expect(lectura.temperatura, isNull);
      expect(lectura.densidad, isNull);
      expect(lectura.puntoCongelacion, isNull);
    });
  });
}
