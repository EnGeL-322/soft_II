import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';

import 'voucher_ocr_parser.dart';

/// Traduce el resultado de ML Kit a las líneas que entiende [VoucherOcrParser].
///
/// Se usan las esquinas (`cornerPoints`) y no la caja (`boundingBox`): la caja es siempre
/// horizontal y pierde la inclinación de una foto tomada torcida, que es justo lo que el parser
/// necesita para enderezarla. Vive aparte del parser para que este siga siendo Dart puro.
List<LineaOcr> lineasDeMlKit(RecognizedText resultado) => [
      for (final bloque in resultado.blocks)
        for (final linea in bloque.lines)
          linea.cornerPoints.length == 4
              ? LineaOcr.conEsquinas(linea.text, [for (final p in linea.cornerPoints) (p.x.toDouble(), p.y.toDouble())])
              : LineaOcr(linea.text, izquierda: linea.boundingBox.left, arriba: linea.boundingBox.top, abajo: linea.boundingBox.bottom),
    ];

/// Confianza media que informa ML Kit por línea (0 a 1). Solo Android la entrega; en iOS queda
/// nula y así se guarda, sin inventar un valor (RNF-20).
double? confianzaPromedio(RecognizedText resultado) {
  final valores = [
    for (final bloque in resultado.blocks)
      for (final linea in bloque.lines)
        if (linea.confidence != null) linea.confidence!,
  ];
  if (valores.isEmpty) return null;
  return valores.reduce((a, b) => a + b) / valores.length;
}
