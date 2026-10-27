// Bandeja de salida del proveedor (como WhatsApp): se escribe sin señal, se ve al instante con
// reloj y sale sola y en orden cuando vuelve la conexión, sin duplicarse.
import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:huata_app/core/local/local_store_memory.dart';
import 'package:huata_app/core/models/mensajeria.dart';
import 'package:huata_app/core/sync/sync_service.dart';

class _TokenFalso extends TokenStore {
  @override
  Future<String?> read() async => 'tok';
}

/// Backend de mentira: guarda por clientUuid igual que `MensajeriaService.enviar` (idempotente).
class _ServidorFalso implements HttpClientAdapter {
  bool hayRed = false;
  String? rechazarCuerpo;
  bool cortarRespuesta = false;
  final recibidos = <Map<String, dynamic>>[];
  final guardados = <String, Map<String, dynamic>>{};
  int _siguienteId = 100;

  static final _json = {Headers.contentTypeHeader: [Headers.jsonContentType]};

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (!hayRed) throw DioException.connectionError(requestOptions: options, reason: 'sin señal');
    final cuerpo = Map<String, dynamic>.from(options.data as Map);
    if (cuerpo['cuerpo'] == rechazarCuerpo) {
      return ResponseBody.fromString(jsonEncode({'message': 'Rechazado'}), 422, headers: _json);
    }
    recibidos.add(cuerpo);
    final uuid = cuerpo['clientUuid'] as String;
    final mensaje = guardados.putIfAbsent(uuid, () => {
      'id': _siguienteId++,
      'autorId': 1,
      'autorNombre': 'Proveedor Demo',
      'mio': true,
      'cuerpo': cuerpo['cuerpo'],
      'creadoEn': '2026-10-01T10:00:00Z',
      'leidoEn': null,
      'productorId': 5,
      'clientUuid': uuid,
      'estado': 'ENVIADO',
    });
    if (cortarRespuesta) {
      // El servidor ya lo guardó, pero la señal se cortó antes de que llegara la respuesta.
      cortarRespuesta = false;
      throw DioException(requestOptions: options, type: DioExceptionType.receiveTimeout);
    }
    return ResponseBody.fromString(jsonEncode(mensaje), 200, headers: _json);
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late _ServidorFalso servidor;
  late SyncService sync;
  late String? usuario;
  late List<MensajeProveedor> confirmados;
  late StreamSubscription<MensajeProveedor> sub;

  setUp(() {
    servidor = _ServidorFalso();
    final api = ApiClient(_TokenFalso());
    api.dio.httpClientAdapter = servidor;
    usuario = 'proveedor.demo@huata.pe';
    sync = SyncService(LocalStoreImpl(), api, usuarioActual: () => usuario);
    confirmados = [];
    sub = sync.mensajesConfirmados.listen(confirmados.add);
  });

  tearDown(() async {
    await sub.cancel();
    sync.dispose();
  });

  test('sin señal el mensaje queda en la cola, a la vista y pendiente', () async {
    await sync.encolarMensaje('Hola, ¿pasan hoy?');
    await sync.sincronizar();

    expect(sync.colaMensajes, hasLength(1));
    expect(sync.colaMensajes.single.estado, EstadoMensaje.pendiente);
    expect(sync.colaMensajes.single.cuerpo, 'Hola, ¿pasan hoy?');
    expect(sync.pendientesMensaje, 1);
    expect(servidor.recibidos, isEmpty);
  });

  test('al volver la señal salen solos y en el orden en que se escribieron', () async {
    await sync.encolarMensaje('uno');
    await sync.encolarMensaje('dos');
    await sync.encolarMensaje('tres');
    await sync.sincronizar();
    expect(servidor.recibidos, isEmpty);

    servidor.hayRed = true;
    await sync.sincronizar();

    expect(servidor.recibidos.map((r) => r['cuerpo']), ['uno', 'dos', 'tres']);
    expect(sync.colaMensajes, isEmpty);
    expect(confirmados.map((m) => m.cuerpo), ['uno', 'dos', 'tres']);
    expect(confirmados.map((m) => m.id), [100, 101, 102]);
  });

  test('si la respuesta se pierde, el reintento con el mismo clientUuid no duplica', () async {
    servidor.hayRed = true;
    servidor.cortarRespuesta = true;

    final uuid = await sync.encolarMensaje('Llevo 40 litros');
    await sync.sincronizar();

    expect(servidor.recibidos.map((r) => r['clientUuid']), [uuid, uuid]);
    expect(servidor.guardados, hasLength(1));
    expect(confirmados, hasLength(1));
    expect(sync.colaMensajes, isEmpty);
  });

  test('un rechazo definitivo queda con error, no frena a los demás y se puede reintentar', () async {
    servidor.hayRed = true;
    servidor.rechazarCuerpo = 'malo';

    final malo = await sync.encolarMensaje('malo');
    await sync.encolarMensaje('bueno');
    await sync.sincronizar();

    expect(servidor.recibidos.map((r) => r['cuerpo']), ['bueno']);
    expect(sync.colaMensajes.single.estado, EstadoMensaje.error);
    expect(sync.colaMensajes.single.error, 'Rechazado');
    expect(sync.pendientesMensaje, 0);

    // Un barrido automático no lo vuelve a intentar: lo decide la persona.
    await sync.sincronizar();
    expect(servidor.recibidos, hasLength(1));

    servidor.rechazarCuerpo = null;
    await sync.reintentarMensaje(malo);
    await sync.sincronizar();
    expect(servidor.recibidos.map((r) => r['cuerpo']), ['bueno', 'malo']);
    expect(sync.colaMensajes, isEmpty);
  });

  test('se puede eliminar un mensaje que quedó con error', () async {
    servidor.hayRed = true;
    servidor.rechazarCuerpo = 'malo';
    final malo = await sync.encolarMensaje('malo');
    await sync.sincronizar();

    await sync.eliminarMensaje(malo);

    expect(sync.colaMensajes, isEmpty);
  });

  test('lo que escribió otra cuenta no sale con esta sesión', () async {
    await sync.encolarMensaje('de la cuenta A');
    usuario = 'otra.cuenta@huata.pe';
    servidor.hayRed = true;

    await sync.sincronizar();
    expect(servidor.recibidos, isEmpty);
    expect(sync.colaMensajes, isEmpty);

    usuario = 'proveedor.demo@huata.pe';
    await sync.sincronizar();
    expect(servidor.recibidos.single['cuerpo'], 'de la cuenta A');
  });

  test('sin red, 401, 409, 429 y 5xx se reintentan; validación y permiso no', () {
    DioException con(int? status) => DioException(
      requestOptions: RequestOptions(),
      response: status == null ? null : Response(requestOptions: RequestOptions(), statusCode: status),
    );
    expect([null, 401, 408, 409, 429, 500, 503].map((s) => esRechazoDefinitivo(con(s))), everyElement(isFalse));
    expect([400, 403, 404, 422].map((s) => esRechazoDefinitivo(con(s))), everyElement(isTrue));
    expect([0, 1, 2, 3, 4, 5, 9].map((n) => esperaReintentoMensaje(n).inSeconds), [2, 4, 8, 16, 32, 60, 60]);
  });

  test('el chat no pinta dos veces un mensaje que ya llegó al servidor', () {
    MensajeProveedor delServidor(int id, String uuid) => MensajeProveedor(
      id: id, autorNombre: 'Yo', mio: true, cuerpo: 'x', creadoEn: DateTime(2026), leidoEn: null, clientUuid: uuid);
    final enCola = [
      MensajeProveedor.enCola(clientUuid: 'A', cuerpo: 'x', creadoEn: DateTime(2026)),
      MensajeProveedor.enCola(clientUuid: 'B', cuerpo: 'y', creadoEn: DateTime(2026)),
    ];

    final visibles = mensajesParaMostrar([delServidor(7, 'A'), delServidor(5, 'Z'), delServidor(7, 'A')], enCola);

    expect(visibles.map((m) => m.id), [5, 7, -1]);
    expect(visibles.last.clientUuid, 'B');
    expect(visibles.last.estado, EstadoMensaje.pendiente);
  });
}
