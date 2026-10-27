import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:huata_app/core/mensajeria/mensajeria_en_vivo.dart';
import 'package:huata_app/core/tiempo_real/cambios_en_vivo.dart';

class _SocketFalso implements SocketMensajeria {
  final entradaCtrl = StreamController<dynamic>();
  final enviados = <Map<String, dynamic>>[];
  bool cerrado = false;

  @override
  Stream<dynamic> get entrada => entradaCtrl.stream;
  @override
  Future<void> get listo async {}
  @override
  void enviar(String datos) => enviados.add(jsonDecode(datos) as Map<String, dynamic>);
  @override
  void cerrar() {
    cerrado = true;
    entradaCtrl.close();
  }

  void llega(Map<String, dynamic> m) => entradaCtrl.add(jsonEncode(m));
}

void main() {
  late List<_SocketFalso> sockets;
  late CambiosEnVivo canal;

  setUp(() {
    sockets = [];
    canal = CambiosEnVivo(
      token: () async => 'jwt',
      abrirSocket: (_) {
        final s = _SocketFalso();
        sockets.add(s);
        return s;
      },
      url: Uri.parse('ws://x/api/ws/cambios'),
      hayRed: const Stream.empty(),
      escucharCicloDeVida: false,
      esperaBase: const Duration(milliseconds: 10),
    );
  });

  tearDown(() => canal.dispose());

  Map<String, dynamic> cambio(String modulo, String entidad, int id) =>
      {'modulo': modulo, 'entidad': entidad, 'id': id, 'accion': 'ACTUALIZADO'};

  test('se presenta con el token y queda conectado al recibir LISTO', () async {
    canal.iniciar();
    await pumpEventQueue();
    expect(sockets.single.enviados.first, {'type': 'AUTH', 'token': 'jwt'});
    sockets.single.llega({'type': 'LISTO'});
    await pumpEventQueue();
    expect(canal.conectado, isTrue);
  });

  test('solo avisa de los módulos pedidos y agrupa los cambios seguidos en una recarga', () async {
    final recibidos = <List<CambioPublico>>[];
    canal.iniciar();
    final sub = canal.de({'pagos'}, espera: const Duration(milliseconds: 20)).listen(recibidos.add);
    await pumpEventQueue();
    sockets.single.llega({'type': 'LISTO'});
    sockets.single.llega({
      'type': 'CAMBIOS',
      'cambios': [cambio('pagos', 'Liquidacion', 1), cambio('rutas', 'Ruta', 9)],
    });
    sockets.single.llega({
      'type': 'CAMBIOS',
      'cambios': [cambio('pagos', 'Liquidacion', 2)],
    });
    await Future<void>.delayed(const Duration(milliseconds: 60));
    expect(recibidos, hasLength(1));
    expect(recibidos.single.map((c) => c.id), [1, 2]);
    await sub.cancel();
  });

  test('al reconectar tras un corte pide recargar (lo de mientras no llegó)', () async {
    final recibidos = <List<CambioPublico>>[];
    canal.iniciar();
    final sub = canal.de({'acopio'}).listen(recibidos.add);
    await pumpEventQueue();
    sockets.first.llega({'type': 'LISTO'});
    await pumpEventQueue();
    expect(recibidos, isEmpty, reason: 'la primera conexión no es una reconexión');

    await sockets.first.entradaCtrl.close();
    await Future<void>.delayed(const Duration(milliseconds: 40));
    expect(sockets, hasLength(2));
    sockets.last.llega({'type': 'LISTO'});
    await pumpEventQueue();
    expect(recibidos, [isEmpty]);
    await sub.cancel();
  });

  test('si cambian los permisos de la cuenta, avisa para releerlos', () async {
    var releidos = 0;
    canal.alCambiarAcceso = () => releidos++;
    canal.iniciar();
    await pumpEventQueue();
    sockets.single.llega({'type': 'LISTO'});
    sockets.single.llega({
      'type': 'CAMBIOS',
      'cambios': [cambio('usuarios', 'UsuarioPermiso', 4)],
    });
    await pumpEventQueue();
    expect(releidos, 1);
  });

  test('el canal se cierra cuando la última pantalla deja de usarlo', () async {
    canal.iniciar();
    canal.iniciar();
    await pumpEventQueue();
    canal.detener();
    expect(sockets.single.cerrado, isFalse);
    canal.detener();
    expect(sockets.single.cerrado, isTrue);
  });
}
