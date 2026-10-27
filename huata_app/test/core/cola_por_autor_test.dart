// Lo registrado sin señal sale solo con la sesión de quien lo registró. Si el acopiador A cierra
// sesión con pendientes y entra B en el mismo teléfono, nada de A debe enviarse con la cuenta de B:
// el recojo quedaría registrado por B y el trazado iría a `/trazados-acopiador/me` de B.
import 'dart:convert';
import 'dart:typed_data';

import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/api/api_client.dart';
import 'package:huata_app/core/auth/token_store.dart';
import 'package:huata_app/core/local/local_store_memory.dart';
import 'package:huata_app/core/sync/sync_service.dart';

class _TokenFalso extends TokenStore {
  @override
  Future<String?> read() async => 'tok';
}

class _Servidor implements HttpClientAdapter {
  bool hayRed = false;
  final recibidos = <String, List<Map<String, dynamic>>>{};

  @override
  Future<ResponseBody> fetch(RequestOptions options, Stream<Uint8List>? requestStream, Future<void>? cancelFuture) async {
    if (!hayRed) throw DioException.connectionError(requestOptions: options, reason: 'sin señal');
    recibidos.putIfAbsent(options.path, () => []).add(Map<String, dynamic>.from(options.data as Map));
    return ResponseBody.fromString(jsonEncode({'ok': true}), 200, headers: {
      Headers.contentTypeHeader: [Headers.jsonContentType],
    });
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late _Servidor servidor;
  late SyncService sync;
  String? usuario;

  setUp(() {
    servidor = _Servidor();
    final api = ApiClient(_TokenFalso());
    api.dio.httpClientAdapter = servidor;
    usuario = 'acopiador.a@huata.pe';
    sync = SyncService(LocalStoreImpl(), api, usuarioActual: () => usuario);
  });

  tearDown(() => sync.dispose());

  test('lo pendiente de A no sale con la sesión de B y sí sale cuando A vuelve', () async {
    await sync.encolarRecojo('r-1', {'clientUuid': 'r-1', 'productorId': 5, 'litros': 20.0});
    await sync.encolarTrazado('2026-10-20', {'fecha': '2026-10-20', 'puntos': []});
    await sync.sincronizar(); // sin señal: queda todo en el teléfono

    usuario = 'acopiador.b@huata.pe'; // A cerró sesión y entró B
    servidor.hayRed = true;
    await sync.sincronizar();

    expect(servidor.recibidos, isEmpty);
    expect(sync.pendientesRecojo, 0, reason: 'B no ve ni puede enviar lo de A');

    usuario = 'acopiador.a@huata.pe'; // vuelve A
    await sync.sincronizar();

    expect(servidor.recibidos['/recojos'], hasLength(1));
    expect(servidor.recibidos['/trazados-acopiador/me'], hasLength(1));
    expect(sync.pendientesRecojo, 0);
  });

  test('al servidor no le llega la marca interna de autor', () async {
    servidor.hayRed = true;
    await sync.encolarRecojo('r-2', {'clientUuid': 'r-2', 'litros': 10.0});
    await sync.sincronizar();

    expect(servidor.recibidos['/recojos']!.single.keys, isNot(contains('_autor')));
    expect(servidor.recibidos['/recojos']!.single['litros'], 10.0);
  });
}
