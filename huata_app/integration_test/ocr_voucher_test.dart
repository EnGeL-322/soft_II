import 'dart:io';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:google_mlkit_text_recognition/google_mlkit_text_recognition.dart';
import 'package:huata_app/core/models/calidad.dart';
import 'package:huata_app/core/ocr/mlkit_lineas.dart';
import 'package:huata_app/core/ocr/voucher_ocr_parser.dart';
import 'package:integration_test/integration_test.dart';
import 'package:path_provider/path_provider.dart';

/// OCR de extremo a extremo: ML Kit real, en un emulador o teléfono, sobre las fotos reales de
/// voucher que hay en la raíz del repositorio (W.jpeg = LACTOMAT, W1.jpeg = SmartScan AI).
///
/// Las pruebas de `test/` solo ejercitan el parser con texto armado a mano; esta es la que prueba
/// que la cadena completa (foto -> ML Kit -> líneas con posición -> parser) lee lo que el voucher
/// dice. Las fotos no se empaquetan en la app: se descargan desde la PC.
///
/// Cómo correrla:
///   1. En la raíz del repositorio: `python -m http.server 8765`
///   2. `flutter test integration_test/ocr_voucher_test.dart -d <emulador>`
///      (10.0.2.2 es la PC vista desde el emulador de Android; en un teléfono real, pasar
///      `--dart-define=HOST_FOTOS=http://<ip-de-la-pc>:8765`).
void main() {
  IntegrationTestWidgetsFlutterBinding.ensureInitialized();

  const host = String.fromEnvironment('HOST_FOTOS', defaultValue: 'http://10.0.2.2:8765');

  Future<File> descargar(String nombre) async {
    final dir = await getTemporaryDirectory();
    final destino = File('${dir.path}/$nombre');
    await Dio().download('$host/$nombre', destino.path);
    return destino;
  }

  Future<LecturaVoucherOcr> leer(String nombre) async {
    final foto = await descargar(nombre);
    final recognizer = TextRecognizer(script: TextRecognitionScript.latin);
    try {
      final resultado = await recognizer.processImage(InputImage.fromFilePath(foto.path));
      final lineas = lineasDeMlKit(resultado);
      // Se imprime todo: si un campo falla, esto es lo que hace falta para ajustar el parser.
      // ignore: avoid_print
      print('==== $nombre: texto crudo ====\n${resultado.text}\n==== filas reconstruidas ====\n'
          '${VoucherOcrParser.reconstruirFilas(lineas).join('\n')}');
      return VoucherOcrParser.parsearLineas(lineas, textoCrudo: resultado.text);
    } finally {
      await recognizer.close();
    }
  }

  testWidgets('LACTOMAT (W.jpeg): lee los valores reales del voucher', (tester) async {
    final l = await leer('W.jpeg');
    expect(l.analizadorSn, '49730');
    expect(l.temperatura, 16.9);
    expect(l.grasa, 2.3);
    expect(l.solidosNoGrasos, 6.63);
    expect(l.densidad, 23.9);
    expect(l.solidosTotales, 9.0);
    expect(l.aguaAnadidaPorcentaje, 23.0);
    expect(l.puntoCongelacion, -0.392);
  });

  testWidgets('SmartScan AI (W1.jpeg): lee los valores reales del voucher', (tester) async {
    final l = await leer('W1.jpeg');
    expect(l.analizadorSn, '48359');
    expect(l.temperatura, 12.2);
    expect(l.grasa, 4.05);
    expect(l.solidosNoGrasos, 8.95);
    expect(l.densidad, 31.4);
    expect(l.solidosTotales, 13.0);
    expect(l.aguaAnadidaPorcentaje, 0.0);
    expect(l.puntoCongelacion, -0.562);
  });
}
