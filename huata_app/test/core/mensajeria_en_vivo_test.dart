// Canal en vivo de la mensajería (/api/ws/mensajeria): se presenta con el token, confirma lo que
// llega, se reconecta con espera creciente y al volver pide lo que se perdió.
import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/mensajeria/mensajeria_en_vivo.dart';
import 'package:huata_app/core/models/mensajeria.dart';

class _SocketFalso implements SocketMensajeria {
  final _entrada = StreamController<dynamic>();
  final enviados = <Map<String, dynamic>>[];
  bool cerrado = false;

  @override
  Stream<dynamic> get entrada => _entrada.stream;
  @override
  Future<void> get listo async {}
  @override
  void enviar(String datos) => enviados.add(jsonDecode(datos) as Map<String, dynamic>);
  @override
  void cerrar() => cerrado = true;

  void recibir(Map<String, dynamic> evento) => _entrada.add(jsonEncode(evento));
  void caer() => _entrada.close();
}

Future<void> _drenar() async {
  for (var i = 0; i < 10; i++) {
    await Future<void>.delayed(Duration.zero);
  }
}

Map<String, dynamic> _mensaje(int id, {bool mio = false, String estado = 'ENVIADO'}) => {
  'id': id, 'autorId': 2, 'autorNombre': 'Secretaría', 'mio': mio, 'cuerpo': 'm$id',
  'creadoEn': '2026-10-01T10:00:00Z', 'leidoEn': null, 'productorId': 5, 'clientUuid': null, 'estado': estado,
};

const _listo = {'type': 'LISTO', 'usuarioId': 9, 'planta': false, 'productorId': 5};

void main() {
  late List<_SocketFalso> sockets;
  late StreamController<bool> red;
  late MensajeriaEnVivo vivo;
  late List<EventoMensajeria> eventos;
  late int conexiones;

  setUp(() {
    sockets = [];
    red = StreamController<bool>();
    eventos = [];
    conexiones = 0;
    vivo = MensajeriaEnVivo(
      token: () async => 'tok',
      abrirSocket: (_) {
        final s = _SocketFalso();
        sockets.add(s);
        return s;
      },
      url: Uri.parse('ws://huata/api/ws/mensajeria'),
      hayRed: red.stream,
      escucharCicloDeVida: false,
      esperaBase: const Duration(milliseconds: 10),
      esperaMaxima: const Duration(milliseconds: 80),
      latido: const Duration(milliseconds: 40),
      sinRespuesta: const Duration(milliseconds: 20),
    )..alConectar = () => conexiones++;
    vivo.eventos.listen(eventos.add);
  });

  tearDown(() async {
    vivo.dispose();
    unawaited(red.close());
  });

  test('se presenta con el token y al recibir LISTO queda en vivo y avisa a la cola', () async {
    vivo.iniciar();
    await _drenar();

    expect(sockets.single.enviados.first, {'type': 'AUTH', 'token': 'tok'});
    sockets.single.recibir(_listo);
    await _drenar();

    expect(vivo.conectado, isTrue);
    expect(conexiones, 1);
    expect(eventos.whereType<CanalListo>(), hasLength(1));
  });

  test('confirma como entregado lo que manda la planta, no lo propio', () async {
    vivo.iniciar();
    await _drenar();
    sockets.single.recibir(_listo);
    sockets.single.recibir({'type': 'MENSAJE', 'mensaje': _mensaje(7)});
    sockets.single.recibir({'type': 'MENSAJE', 'mensaje': _mensaje(8, mio: true)});
    await _drenar();

    expect(sockets.single.enviados.where((m) => m['type'] == 'ENTREGADO'), [
      {'type': 'ENTREGADO', 'ids': [7]},
    ]);
    expect(eventos.whereType<MensajeNuevo>().map((e) => e.mensaje.id), [7, 8]);
  });

  test('los checks llegan como EstadoActualizado', () async {
    vivo.iniciar();
    await _drenar();
    sockets.single.recibir(_listo);
    sockets.single.recibir({'type': 'ESTADO', 'productorId': 5, 'ids': [3, 4], 'estado': 'LEIDO', 'en': '2026-10-01T10:00:00Z'});
    await _drenar();

    final estado = eventos.whereType<EstadoActualizado>().single;
    expect(estado.ids, [3, 4]);
    expect(estado.estado, EstadoMensaje.leido);
  });

  test('si se cae, reconecta solo y pide lo perdido desde el último id visto', () async {
    vivo.iniciar();
    await _drenar();
    sockets.single.recibir(_listo);
    sockets.single.recibir({'type': 'MENSAJE', 'mensaje': _mensaje(42)});
    await _drenar();

    sockets.single.caer();
    await _drenar();
    expect(vivo.conectado, isFalse);

    await Future<void>.delayed(const Duration(milliseconds: 25));
    expect(sockets, hasLength(2));
    sockets.last.recibir(_listo);
    await _drenar();

    expect(sockets.last.enviados, anyElement(equals({'type': 'SYNC', 'desdeId': 42})));
    expect(conexiones, 2);
  });

  test('cuando vuelve la red reconecta de inmediato, sin esperar el siguiente intento', () async {
    vivo.iniciar();
    await _drenar();
    sockets.single.caer();
    await _drenar();

    red.add(true);
    await _drenar();

    expect(sockets, hasLength(2));
  });

  test('si el latido no tiene respuesta, da la conexión por muerta', () async {
    vivo.iniciar();
    await _drenar();
    sockets.single.recibir(_listo);
    await _drenar();

    await Future<void>.delayed(const Duration(milliseconds: 75));

    expect(sockets.first.enviados, anyElement(equals({'type': 'PING'})));
    expect(sockets.first.cerrado, isTrue);
  });

  test('pide la siguiente tanda si el SYNC no vino completo', () async {
    vivo.iniciar();
    vivo.registrarVistos([1]);
    await _drenar();
    sockets.single.recibir(_listo);
    sockets.single.recibir({'type': 'SYNC', 'mensajes': [_mensaje(2), _mensaje(3)], 'completo': false});
    await _drenar();

    expect(sockets.single.enviados, anyElement(equals({'type': 'SYNC', 'desdeId': 3})));
    expect(sockets.single.enviados, anyElement(equals({'type': 'ENTREGADO', 'ids': [2, 3]})));
    expect(eventos.whereType<MensajesRecuperados>().single.mensajes.map((m) => m.id), [2, 3]);
  });

  test('la espera entre intentos crece y tiene tope', () {
    expect([0, 1, 2, 3, 4, 8].map((n) => vivo.esperaDelIntento(n).inMilliseconds), [10, 20, 40, 80, 80, 80]);
    final real = MensajeriaEnVivo(token: () async => null, escucharCicloDeVida: false);
    expect([0, 1, 2, 3, 4, 5, 6].map((n) => real.esperaDelIntento(n).inSeconds), [1, 2, 4, 8, 16, 30, 30]);
    real.dispose();
  });

  test('al detenerse no vuelve a conectar', () async {
    vivo.iniciar();
    await _drenar();
    vivo.detener();
    await Future<void>.delayed(const Duration(milliseconds: 40));

    expect(sockets, hasLength(1));
    expect(sockets.single.cerrado, isTrue);
  });

  test('la URL del canal sale de la base de la API', () {
    expect(urlCanalMensajeria('http://100.121.90.36:8082/api/v1').toString(), 'ws://100.121.90.36:8082/api/ws/mensajeria');
    expect(urlCanalMensajeria('https://huata.pe/api/v1').toString(), 'wss://huata.pe/api/ws/mensajeria');
    expect(urlCanalMensajeria('/api/v1', origen: Uri.parse('http://localhost:8083/')).toString(),
        'ws://localhost:8083/api/ws/mensajeria');
  });
}
