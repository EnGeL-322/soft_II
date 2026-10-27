import 'dart:math' as math;

import '../models/calidad.dart';

/// Una línea de texto reconocida por el OCR con su posición en la foto.
///
/// Es un tipo propio y no el `TextLine` de ML Kit a propósito: así el parser sigue siendo puro Dart
/// (se prueba con `flutter test` sin cámara ni plugin) y la pantalla solo traduce de uno a otro.
class LineaOcr {
  /// Línea recta (sin inclinación), a partir de su caja.
  const LineaOcr(this.texto, {required double izquierda, required double arriba, required double abajo})
      : centroX = izquierda,
        centroY = (arriba + abajo) / 2,
        alto = abajo - arriba,
        angulo = 0;

  /// Línea con las cuatro esquinas que devuelve ML Kit (`TextLine.cornerPoints`, en orden
  /// arriba-izq, arriba-der, abajo-der, abajo-izq). A diferencia de la caja, las esquinas conservan
  /// la inclinación real de la línea: es lo que permite enderezar una foto tomada torcida.
  factory LineaOcr.conEsquinas(String texto, List<(double, double)> esquinas) {
    if (esquinas.length != 4) {
      final ys = esquinas.map((p) => p.$2);
      return LineaOcr(texto, izquierda: esquinas.map((p) => p.$1).reduce(math.min), arriba: ys.reduce(math.min), abajo: ys.reduce(math.max));
    }
    final (x0, y0) = esquinas[0];
    final (x1, y1) = esquinas[1];
    final (x3, y3) = esquinas[3];
    final cx = esquinas.map((p) => p.$1).reduce((a, b) => a + b) / 4;
    final cy = esquinas.map((p) => p.$2).reduce((a, b) => a + b) / 4;
    return LineaOcr._(texto, cx, cy, math.sqrt(math.pow(x3 - x0, 2) + math.pow(y3 - y0, 2)), math.atan2(y1 - y0, x1 - x0));
  }

  const LineaOcr._(this.texto, this.centroX, this.centroY, this.alto, this.angulo);

  final String texto;
  final double centroX;
  final double centroY;
  final double alto;

  /// Inclinación de la línea en radianes (0 = horizontal).
  final double angulo;
}

/// Extrae valores de un voucher de analizador de leche a partir del texto que devuelve el OCR.
///
/// Puro Dart (sin `dart:io` ni plugins): se prueba con `flutter test` sin cámara ni dispositivo.
///
/// **Por qué no basta con `RecognizedText.text`.** Un voucher es una tabla de dos columnas
/// (etiqueta a la izquierda, valor a la derecha). ML Kit agrupa el texto en bloques por cercanía y
/// suele devolver toda la columna de etiquetas en un bloque y toda la de valores en otro, así que
/// `resultado.text` queda como "Temp\nGrasa\nDensidad\n...\n12.2 C\n4.05 %\n31.4". Buscar
/// "Densidad 31.4" en ese texto no encuentra nada: era la causa de que el OCR "no leyera". Por eso
/// [reconstruirFilas] vuelve a juntar en una fila las líneas que están a la misma altura.
///
/// SmartScan AI y LACTOMAT (muestras reales en CLAUDE.md sección 10) usan etiquetas en español; se
/// aceptan también las equivalentes en inglés que imprimen otros analizadores (Lactoscan, Ekomilk).
/// Un campo que no calza queda en blanco para que el testeador lo complete: el OCR propone y nunca
/// decide nada por sí solo (RN-14). Los valores se devuelven tal como aparecen, sin "corregir" los
/// atípicos (RN-13).
class VoucherOcrParser {
  VoucherOcrParser._();

  static final _sn = RegExp(r'(?:\bS\s?/?\s?N|N[°º]?\s*serie|Serial)\b[\s.:#=]*([A-Z0-9-]{3,})', caseSensitive: false);

  /// Etiquetas de cada campo, tolerantes a las confusiones de OCR que se vieron con ML Kit real en
  /// las fotos de W.jpeg/W1.jpeg torcidas o de baja resolución: "pensidad" por "Densidad",
  /// "Tota! solidos", "Sa les", "SNGwe". Lo que va DESPUÉS de la etiqueta se trata aparte (ver
  /// [_valorTras]), así que aquí solo importa reconocer de qué campo es la fila.
  static final _etiquetas = <String, RegExp>{
    'temperatura': RegExp(r'\bTemp[a-z]*', caseSensitive: false),
    'grasa': RegExp(r'\b(?:Gr[a4]s[a4]|Fat)', caseSensitive: false),
    'sng': RegExp(r'\b(?:S\.?\s?N\.?\s?[GF]|S[oó]lidos?\s*no\s*grasos?)', caseSensitive: false),
    'densidad': RegExp(r'(?:\b[DdPpbO]?[e3]ns\s?[i1l!|]\s?d\s?ad|\bDensity|\bDens\b)', caseSensitive: false),
    'solidosTotales': RegExp(
      // "Total so lidos": ML Kit a veces parte la palabra con un espacio.
      r'\b(?:Tota[l!1|I]?\s*s\s?[oó0]\s?l\s?[i1l!|]\s?d\s?[o0]s|S[oó]lidos\s*totales|Total\s*solids|TS\b)',
      caseSensitive: false,
    ),
    'aguaAnadida': RegExp(r'\b(?:Agua\s*a[nñ]?[a4]d[i1l]da|Added\s*water|Agua)', caseSensitive: false),
    'puntoCongelacion': RegExp(
      r'\b(?:Punto\s*(?:de\s*)?cong[a-z]*|Freezing\s*point|F\.?\s?P\.?|Pto\.?\s*cong[a-z]*)',
      caseSensitive: false,
    ),
  };

  /// Solo estos campos pueden ser negativos. En los demás, un guion es ruido de los puntos guía
  /// ("Grasa.-...4.05") y no un signo, así que no se lee.
  static const _conSigno = {'temperatura', 'puntoCongelacion'};

  /// Punto de entrada con las líneas y su posición (lo que se usa con ML Kit en el teléfono).
  static LecturaVoucherOcr parsearLineas(List<LineaOcr> lineas, {String? textoCrudo}) {
    final filas = reconstruirFilas(lineas);
    return parsear(filas.join('\n'), textoCrudo: textoCrudo ?? lineas.map((l) => l.texto).join('\n'));
  }

  /// Punto de entrada con texto plano (una fila por línea).
  static LecturaVoucherOcr parsear(String texto, {String? textoCrudo}) {
    final normalizado = _normalizar(texto);
    // Se busca fila por fila; el valor tiene que estar en la misma fila que su etiqueta (nunca se
    // toma un número de la fila de al lado: eso sería inventar el emparejamiento).
    final filas = normalizado.split('\n');
    double? numero(String clave) {
      for (final fila in filas) {
        final v = _valorTras(fila, _etiquetas[clave]!, conSigno: _conSigno.contains(clave));
        if (v != null) return v;
      }
      return null;
    }

    String? sn;
    for (final fila in filas) {
      sn = _sn.firstMatch(fila)?.group(1);
      if (sn != null) break;
    }

    final puntoCongelacion = numero('puntoCongelacion');
    return LecturaVoucherOcr(
      analizadorSn: sn,
      temperatura: numero('temperatura'),
      grasa: numero('grasa'),
      solidosNoGrasos: numero('sng'),
      densidad: numero('densidad'),
      solidosTotales: numero('solidosTotales'),
      aguaAnadidaPorcentaje: numero('aguaAnadida'),
      puntoCongelacion: puntoCongelacion,
      advertencias: [
        // La leche se congela bajo cero siempre: un valor positivo es casi seguro un signo menos que
        // el OCR no vio. No se corrige solo (RN-13: el valor queda como se leyó); se avisa.
        if (puntoCongelacion != null && puntoCongelacion > 0)
          'El punto de congelación se leyó positivo (${puntoCongelacion.toString()}). Revisa si en el voucher lleva signo menos.',
      ],
      textoCrudo: textoCrudo ?? texto,
    );
  }

  /// Primer número que aparece después de la etiqueta en la misma fila, saltando el ruido que el
  /// OCR mete en los puntos guía ("Densidad....r ...31.4", "SNG... d8.95X"). El ruido no puede
  /// cruzar un salto de línea ni ser demasiado largo, para no llegar al valor de otro campo.
  static double? _valorTras(String fila, RegExp etiqueta, {required bool conSigno}) {
    for (final m in etiqueta.allMatches(fila)) {
      final resto = fila.substring(m.end);
      final numero = RegExp(conSigno ? r'^[^\d\n-]{0,40}?(-?\s?\d+(?:[.,]\d+)?)' : r'^[^\d\n]{0,40}?(\d+(?:[.,]\d+)?)')
          .firstMatch(resto)
          ?.group(1);
      if (numero != null) return double.tryParse(numero.replaceAll(' ', '').replaceAll(',', '.'));
    }
    return null;
  }

  /// Junta en una sola fila las líneas que el OCR separó pero que están a la misma altura en la
  /// foto, ordenadas de izquierda a derecha. Dos líneas son de la misma fila si sus centros
  /// verticales distan menos de la mitad de la altura de la línea más alta.
  ///
  /// Antes de agrupar se **endereza** la foto: se toma la inclinación mediana de las líneas (la
  /// mediana ignora una línea suelta mal leída) y se rotan los centros en sentido contrario. Sin esto,
  /// en una foto tomada algo torcida —lo normal en campo— la columna de valores queda más arriba o
  /// más abajo que su etiqueta y cada valor se empareja con la fila vecina (se comprobó con ML Kit
  /// real sobre W.jpeg: "SNG" recibía el valor de "Grasa").
  static List<String> reconstruirFilas(List<LineaOcr> lineas) {
    final utiles = lineas.where((l) => l.texto.trim().isNotEmpty).toList();
    if (utiles.isEmpty) return const [];
    final angulos = utiles.map((l) => l.angulo).toList()..sort();
    final a = angulos[angulos.length ~/ 2];
    final (seno, coseno) = (math.sin(a), math.cos(a));
    // Coordenadas en el sistema de la foto enderezada.
    double yRecta(LineaOcr l) => l.centroY * coseno - l.centroX * seno;
    double xRecta(LineaOcr l) => l.centroX * coseno + l.centroY * seno;

    final ordenadas = [...utiles]..sort((p, q) => yRecta(p).compareTo(yRecta(q)));
    final filas = <List<LineaOcr>>[];
    for (final linea in ordenadas) {
      final fila = filas.isEmpty ? null : filas.last;
      if (fila != null) {
        final centro = fila.map(yRecta).reduce((p, q) => p + q) / fila.length;
        final alto = fila.map((l) => l.alto).followedBy([linea.alto]).reduce(math.max);
        if ((yRecta(linea) - centro).abs() <= alto / 2) {
          fila.add(linea);
          continue;
        }
      }
      filas.add([linea]);
    }
    return [
      for (final fila in filas) (fila..sort((p, q) => xRecta(p).compareTo(xRecta(q)))).map((l) => l.texto.trim()).join(' '),
    ];
  }

  static String _normalizar(String texto) => texto
      // Guiones que el OCR confunde con el signo menos.
      .replaceAll(RegExp(r'[‐-―−~]'), '-')
      // "O" leída en lugar de "0" dentro de un número ("3O.5", "-O.562").
      .replaceAllMapped(RegExp(r'(?<=[\d\-.,])[Oo](?=[\d.,])|(?<=-)[Oo](?=[.,]\d)'), (_) => '0')
      .replaceAll('\r', '');
}
