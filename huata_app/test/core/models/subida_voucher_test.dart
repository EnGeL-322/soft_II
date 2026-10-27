import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/models/calidad.dart';

void main() {
  // La subida pendiente vive como JSON en la cola offline: si no vuelve igual, la foto se sube
  // colgada del control equivocado o sin su texto OCR.
  test('SubidaVoucher sobrevive la cola offline sin perder datos', () {
    final original = SubidaVoucher(
      controlId: 42,
      rutaFoto: '/data/vouchers/a.jpg',
      clientUuid: 'c0ffee-1',
      textoOcr: 'Densidad.....23.9',
      valoresOcr: '{"densidad":23.9}',
      confianzaOcr: 0.91,
    );
    final vuelta = SubidaVoucher.fromJson(jsonDecode(jsonEncode(original.toJson())) as Map<String, dynamic>);

    expect(vuelta.controlId, 42);
    expect(vuelta.rutaFoto, '/data/vouchers/a.jpg');
    expect(vuelta.clientUuid, 'c0ffee-1');
    expect(vuelta.textoOcr, 'Densidad.....23.9');
    expect(vuelta.valoresOcr, '{"densidad":23.9}');
    expect(vuelta.confianzaOcr, 0.91);
  });

  test('sin texto OCR ni confianza no se envían campos vacíos', () {
    final json = SubidaVoucher(controlId: 1, rutaFoto: 'f.jpg', clientUuid: 'u').toJson();
    expect(json.containsKey('textoOcr'), isFalse);
    expect(json.containsKey('confianzaOcr'), isFalse);
  });

  test('los valores propuestos por el OCR solo incluyen lo que se leyó', () {
    final l = LecturaVoucherOcr(densidad: 23.9, puntoCongelacion: -0.392);
    expect(l.valoresPropuestos(), {'densidad': 23.9, 'puntoCongelacion': -0.392});
  });
}
