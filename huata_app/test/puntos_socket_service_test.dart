import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/models/ruta.dart';
import 'package:huata_app/core/rutas/puntos_socket_service.dart';

class CanalFalso implements CanalPuntos {
  CanalFalso(this.alSuscribir, this.alEvento, this.alCaer);
  final void Function() alSuscribir;
  final void Function(EventoPunto) alEvento;
  final void Function() alCaer;
  bool cerrado = false;
  @override
  void iniciar() {}
  @override
  void cerrar() {
    cerrado = true;
  }
}

PuntoEstablecido punto(int id, int version, {bool activo = true, String nombre = 'Punto', int rutaId = 7}) =>
    PuntoEstablecido(
      id: id,
      nombre: nombre,
      latitud: -15,
      longitud: -70,
      rutaId: rutaId,
      activo: activo,
      version: version,
    );

void main() {
  late List<CanalFalso> canales;
  late PuntosSocketService servicio;
  late Future<List<PuntoEstablecido>> Function() cargar;
  late int lecturas;

  setUp(() {
    canales = [];
    lecturas = 0;
    cargar = () async => [];
    servicio = PuntosSocketService(
      token: () async => 'token-falso-no-se-registra',
      cargar: () {
        lecturas++;
        return cargar();
      },
      url: Uri.parse('ws://localhost/api/ws/puntos'),
      crearCanal:
          ({required url, required token, required rutaId, required alSuscribir, required alEvento, required alCaer}) {
            final canal = CanalFalso(alSuscribir, alEvento, alCaer);
            canales.add(canal);
            return canal;
          },
    );
  });
  tearDown(() => servicio.dispose());

  Future<void> abrir(WidgetTester tester) async {
    servicio.iniciar(rutaId: 7, acopiadorId: 9);
    await tester.pump();
  }

  testWidgets('un alta pasa de cuatro a cinco y moverla conserva el ID seleccionado', (tester) async {
    cargar = () async => List.generate(4, (i) => punto(i + 1, 1, nombre: 'Proveedor ${i + 1}'));
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    expect(servicio.puntos, hasLength(4));
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(5, 1, nombre: 'Proveedor 5')));
    await tester.pump();
    expect(servicio.puntos, hasLength(5));
    const seleccionado = 5;
    canales.last.alEvento(EventoPunto('PUNTO_ACTUALIZADO', PuntoEstablecido(
      id: seleccionado, nombre: 'Proveedor 5', latitud: -15.7, longitud: -69.9,
      rutaId: 7, activo: true, version: 2,
    )));
    await tester.pump();
    expect(servicio.puntos, hasLength(5));
    expect(servicio.puntos.singleWhere((p) => p.id == seleccionado).latitud, -15.7);
  });

  testWidgets('suscribe antes de REST y conserva el evento entre suscripción y snapshot', (tester) async {
    final respuesta = Completer<List<PuntoEstablecido>>();
    cargar = () => respuesta.future;
    await abrir(tester);
    expect(lecturas, 0);
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 0)));
    canales.last.alSuscribir();
    expect(lecturas, 1);
    respuesta.complete([]);
    await tester.pump();
    expect(servicio.puntos.map((p) => p.id), [1]);
  });

  testWidgets('desactiva/elimina y descarta duplicados o eventos desordenados', (tester) async {
    cargar = () async => [punto(1, 0)];
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    canales.last.alEvento(EventoPunto('PUNTO_DESACTIVADO', punto(1, 2, activo: false)));
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 1)));
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 2)));
    expect(servicio.puntos, isEmpty);
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 3)));
    canales.last.alEvento(EventoPunto('PUNTO_ACTUALIZADO', punto(1, 4, nombre: 'Nuevo')));
    expect(servicio.puntos.single.nombre, 'Nuevo');
    canales.last.alEvento(EventoPunto('PUNTO_ELIMINADO', punto(1, 5, activo: false)));
    expect(servicio.puntos, isEmpty);
  });

  testWidgets('un snapshot atrasado no resucita un punto desactivado durante REST', (tester) async {
    final respuesta = Completer<List<PuntoEstablecido>>();
    cargar = () => respuesta.future;
    await abrir(tester);
    canales.last.alSuscribir();
    canales.last.alEvento(EventoPunto('PUNTO_DESACTIVADO', punto(1, 1, activo: false)));
    respuesta.complete([punto(1, 0)]);
    await tester.pump();
    expect(servicio.puntos, isEmpty);
  });

  testWidgets('reconecta con backoff y reemplaza todo tras confirmar nueva suscripción', (tester) async {
    cargar = () async => [punto(1, 0)];
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    canales.last.alCaer();
    expect(servicio.puntos.length, 1);
    await tester.pump(const Duration(milliseconds: 999));
    expect(canales.length, 1);
    await tester.pump(const Duration(milliseconds: 1));
    expect(canales.length, 2);
    expect(lecturas, 1);
    cargar = () async => [];
    canales.last.alSuscribir();
    await tester.pump();
    expect(lecturas, 2);
    expect(servicio.puntos, isEmpty);
    expect(List.generate(7, servicio.esperaDelIntento).map((d) => d.inSeconds), [1, 2, 4, 8, 16, 30, 30]);
  });

  testWidgets('REST fallido mantiene pantalla y reintenta sin polling tras éxito', (tester) async {
    cargar = () async => [punto(1, 0)];
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    cargar = () async => throw Exception('sin red');
    await servicio.resincronizar();
    expect(servicio.puntos.length, 1);
    cargar = () async => [punto(2, 0)];
    await tester.pump(const Duration(seconds: 1));
    expect(servicio.puntos.single.id, 2);
    final peticiones = lecturas;
    await tester.pump(const Duration(minutes: 2));
    expect(lecturas, peticiones);
  });

  testWidgets('resumed renueva conexión y logout ignora callbacks REST y socket pendientes', (tester) async {
    cargar = () async => [punto(1, 0)];
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    servicio.reconectar();
    await tester.pump();
    expect(canales.first.cerrado, isTrue);
    final respuesta = Completer<List<PuntoEstablecido>>();
    cargar = () => respuesta.future;
    canales.last.alSuscribir();
    servicio.detener();
    respuesta.complete([punto(2, 0)]);
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(3, 0)));
    await tester.pump();
    expect(canales.last.cerrado, isTrue);
    expect(servicio.puntos.single.id, 1);
    await tester.pump(const Duration(minutes: 1));
    expect(canales.length, 2);
  });

  testWidgets('renumera por nombre y retira puntos movidos a otra ruta', (tester) async {
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 0, nombre: 'Z')));
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(2, 0, nombre: 'A')));
    expect(servicio.puntos.map((p) => p.id), [2, 1]);
    canales.last.alEvento(EventoPunto('PUNTO_ACTUALIZADO', punto(2, 1, rutaId: 8)));
    expect(servicio.puntos.map((p) => p.id), [1]);
  });

  testWidgets('bajas de REST impiden resucitar con un evento anterior a la reconexión', (tester) async {
    cargar = () async => [punto(1, 4, activo: false)];
    await abrir(tester);
    canales.last.alSuscribir();
    await tester.pump();
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 3)));
    expect(servicio.puntos, isEmpty);
    canales.last.alEvento(EventoPunto('PUNTO_ACTIVADO', punto(1, 5)));
    expect(servicio.puntos.single.id, 1);
  });
}
