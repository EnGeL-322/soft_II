import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/productores_api.dart';
import 'package:huata_app/core/models/productor.dart';
import 'package:huata_app/features/proveedor/solicitud_zona_sheet.dart';
import 'package:provider/provider.dart';

/// Solo lo que usa el formulario: la lista de zonas y el envío.
class _ApiFalsa implements ProductoresApi {
  _ApiFalsa(this.zonas);
  final List<String> zonas;
  String? enviada;

  @override
  Future<List<String>> zonasSolicitables(int productorId) async => zonas;

  @override
  Future<SolicitudCambioZona> crearSolicitudZona(int productorId, {required String zonaSolicitada, String? motivo}) async {
    enviada = zonaSolicitada;
    throw StateError('no hace falta la respuesta en esta prueba');
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

Future<void> _abrir(WidgetTester tester, _ApiFalsa api) async {
  await tester.pumpWidget(
    Provider<ProductoresApi>.value(
      value: api,
      child: const MaterialApp(
        home: Scaffold(body: SolicitudZonaSheet(productorId: 1, zonaActual: 'FAON')),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('la zona se elige de la lista del backend, no se escribe', (tester) async {
    final api = _ApiFalsa(['COLLANA I', 'YASIN']);
    await _abrir(tester, api);

    expect(find.byType(DropdownButtonFormField<String>), findsOneWidget);
    // Solo el motivo es texto libre.
    expect(find.byType(TextFormField), findsOneWidget);

    await tester.tap(find.text('Enviar solicitud'));
    await tester.pumpAndSettle();
    expect(find.text('Elige la zona a la que quieres pasar'), findsOneWidget);
    expect(api.enviada, isNull);

    await tester.tap(find.byType(DropdownButtonFormField<String>));
    await tester.pumpAndSettle();
    await tester.tap(find.text('YASIN').last);
    await tester.pumpAndSettle();
    await tester.tap(find.text('Enviar solicitud'));
    await tester.pumpAndSettle();
    expect(api.enviada, 'YASIN');
  });

  testWidgets('sin otras zonas lo dice y no deja enviar', (tester) async {
    await _abrir(tester, _ApiFalsa([]));

    expect(find.textContaining('no hay otras zonas'), findsOneWidget);
    final boton = tester.widget<ElevatedButton>(find.widgetWithText(ElevatedButton, 'Enviar solicitud'));
    expect(boton.onPressed, isNull);
  });
}
