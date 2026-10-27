// Punto de entrada de `flutter drive` para correr integration_test/ en modo release: así se prueba
// el OCR con R8 activo, que es donde fallaría si se quitaran clases de ML Kit (ver proguard-rules.pro).
import 'package:integration_test/integration_test_driver.dart';

Future<void> main() => integrationDriver();
