import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/widgets.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

import '../api/api_config.dart';
import '../models/mensajeria.dart';

enum ConexionEnVivo { desconectado, conectando, conectado }

/// Lo que llega por /api/ws/mensajeria (ver `MensajeriaWebSocketHandler` en el backend).
sealed class EventoMensajeria {}

/// El canal quedó autenticado: buen momento para mandar la cola y refrescar la pantalla.
class CanalListo extends EventoMensajeria {}

class MensajeNuevo extends EventoMensajeria {
  MensajeNuevo(this.mensaje);
  final MensajeProveedor mensaje;
}

/// Lo que se perdió mientras no había conexión (respuesta al SYNC).
class MensajesRecuperados extends EventoMensajeria {
  MensajesRecuperados(this.mensajes);
  final List<MensajeProveedor> mensajes;
}

/// Checks: unos mensajes pasaron a entregado o leído.
class EstadoActualizado extends EventoMensajeria {
  EstadoActualizado(this.ids, this.estado);
  final List<int> ids;
  final EstadoMensaje estado;
}

/// Lo mínimo de un WebSocket. Existe para que las pruebas usen uno falso.
abstract class SocketMensajeria {
  Stream<dynamic> get entrada;
  Future<void> get listo;
  void enviar(String datos);
  void cerrar();
}

class _SocketReal implements SocketMensajeria {
  _SocketReal(Uri url) : _canal = WebSocketChannel.connect(url);
  final WebSocketChannel _canal;
  @override
  Stream<dynamic> get entrada => _canal.stream;
  @override
  Future<void> get listo => _canal.ready;
  @override
  void enviar(String datos) => _canal.sink.add(datos);
  @override
  void cerrar() => unawaited(_canal.sink.close());
}

/// El WebSocket real, para otros canales que usan la misma abstracción (p. ej. `CambiosEnVivo`).
SocketMensajeria abrirSocketReal(Uri url) => _SocketReal(url);

/// `http://host:8082/api/v1` → `ws://host:8082/api/ws/mensajeria`. En el build web la base es
/// relativa (`/api/v1`) y el canal va al mismo origen que sirve la app.
Uri urlCanalMensajeria(String baseUrl, {Uri? origen}) => urlCanal(baseUrl, 'mensajeria', origen: origen);

/// Igual que [urlCanalMensajeria] para cualquier canal de `/api/ws/`.
Uri urlCanal(String baseUrl, String canal, {Uri? origen}) {
  final base = Uri.parse(baseUrl);
  final absoluta = base.hasScheme ? base : (origen ?? Uri.base).resolveUri(base);
  final prefijo = absoluta.path.replaceFirst(RegExp(r'/api/v1/?$'), '');
  return Uri(
    scheme: absoluta.scheme == 'https' ? 'wss' : 'ws',
    host: absoluta.host,
    port: absoluta.hasPort ? absoluta.port : null,
    path: '$prefijo/api/ws/$canal',
  );
}

/// Canal en vivo de la mensajería del proveedor.
///
/// Enviar NO va por aquí: lo hace la cola offline de `SyncService` por el POST REST, idempotente
/// por clientUuid. Este canal solo recibe (mensajes y checks), confirma lo que llegó (ENTREGADO),
/// pide lo perdido al reconectar (SYNC desde el último id) y manda un latido para notar una
/// conexión muerta que el sistema todavía cree abierta — pasa al cambiar de antena o de wifi a datos.
class MensajeriaEnVivo extends ChangeNotifier {
  MensajeriaEnVivo({
    required Future<String?> Function() token,
    SocketMensajeria Function(Uri url)? abrirSocket,
    Uri? url,
    Stream<bool>? hayRed,
    this.escucharCicloDeVida = true,
    this.esperaBase = const Duration(seconds: 1),
    this.esperaMaxima = const Duration(seconds: 30),
    this.latido = const Duration(seconds: 25),
    this.sinRespuesta = const Duration(seconds: 10),
  }) : _token = token,
       _abrirSocket = abrirSocket ?? _SocketReal.new,
       _url = url,
       _hayRed = hayRed;

  final Future<String?> Function() _token;
  final SocketMensajeria Function(Uri url) _abrirSocket;
  final Uri? _url;
  final Stream<bool>? _hayRed;
  final bool escucharCicloDeVida;
  final Duration esperaBase;
  final Duration esperaMaxima;
  final Duration latido;
  final Duration sinRespuesta;

  /// Lo fija `main.dart`: al conectarse, la cola de salida intenta mandar lo pendiente.
  VoidCallback? alConectar;

  ConexionEnVivo estado = ConexionEnVivo.desconectado;
  bool get conectado => estado == ConexionEnVivo.conectado;

  final _eventos = StreamController<EventoMensajeria>.broadcast();
  Stream<EventoMensajeria> get eventos => _eventos.stream;

  int _usuarios = 0;
  int _intento = 0;
  int? _ultimoId;
  bool _cerrado = false;
  SocketMensajeria? _socket;
  StreamSubscription<dynamic>? _entrada;
  StreamSubscription<bool>? _red;
  AppLifecycleListener? _ciclo;
  Timer? _reconexion;
  Timer? _latido;
  Timer? _vigilancia;

  /// Espera antes del intento `n`: 1 s, 2 s, 4 s, 8 s… hasta 30 s.
  Duration esperaDelIntento(int n) {
    final ms = esperaBase.inMilliseconds * (1 << n.clamp(0, 20));
    return Duration(milliseconds: math.min(ms, esperaMaxima.inMilliseconds));
  }

  /// Varias pantallas pueden usar el canal: se abre con la primera y se cierra con la última.
  void iniciar() {
    _usuarios++;
    if (_usuarios > 1) return;
    _intento = 0;
    _red = (_hayRed ?? _cambiosDeRed()).listen((hay) {
      if (hay) reconectarYa();
    });
    if (escucharCicloDeVida) _ciclo = AppLifecycleListener(onResume: reconectarYa);
    unawaited(_abrir());
  }

  void detener() {
    if (_usuarios == 0) return;
    _usuarios--;
    if (_usuarios > 0) return;
    _red?.cancel();
    _ciclo?.dispose();
    _ciclo = null;
    _reconexion?.cancel();
    _descartar();
    // Puede entrar otra cuenta en el mismo teléfono: su historial no continúa el de esta.
    _ultimoId = null;
    _cambiarEstado(ConexionEnVivo.desconectado);
  }

  /// El último id que la pantalla ya tiene: desde ahí se pide el SYNC al reconectar.
  void registrarVistos(Iterable<int> ids) {
    for (final id in ids) {
      if (id > 0 && (_ultimoId == null || id > _ultimoId!)) _ultimoId = id;
    }
  }

  /// Volvió la señal o la app volvió a primer plano: no esperar al siguiente intento programado.
  void reconectarYa() {
    if (_usuarios == 0 || conectado) return;
    _reconexion?.cancel();
    _intento = 0;
    _descartar();
    unawaited(_abrir());
  }

  static Stream<bool> _cambiosDeRed() =>
      Connectivity().onConnectivityChanged.map((r) => !r.contains(ConnectivityResult.none));

  Future<void> _abrir() async {
    if (_usuarios == 0) return;
    final token = await _token();
    if (token == null || _usuarios == 0 || _socket != null) {
      if (token == null) _cambiarEstado(ConexionEnVivo.desconectado);
      return;
    }
    _cambiarEstado(ConexionEnVivo.conectando);
    final SocketMensajeria socket;
    try {
      socket = _abrirSocket(_url ?? urlCanalMensajeria(ApiConfig.baseUrl));
    } catch (_) {
      _programarReconexion();
      return;
    }
    _socket = socket;
    _entrada = socket.entrada.listen(
      (datos) => _recibir(socket, datos),
      onDone: () => _caido(socket),
      onError: (Object _) => _caido(socket),
      cancelOnError: true,
    );
    try {
      await socket.listo;
    } catch (_) {
      _caido(socket);
      return;
    }
    if (_socket != socket) return;
    socket.enviar(jsonEncode({'type': 'AUTH', 'token': token}));
  }

  void _recibir(SocketMensajeria socket, dynamic datos) {
    if (_socket != socket || datos is! String) return;
    final Map<String, dynamic> json;
    try {
      json = jsonDecode(datos) as Map<String, dynamic>;
    } catch (_) {
      return;
    }
    _vigilancia?.cancel();
    switch (json['type']) {
      case 'LISTO':
        _intento = 0;
        _cambiarEstado(ConexionEnVivo.conectado);
        _iniciarLatido();
        if (_ultimoId != null) _mandar({'type': 'SYNC', 'desdeId': _ultimoId});
        _emitir(CanalListo());
        alConectar?.call();
      case 'MENSAJE':
        final m = MensajeProveedor.fromJson(json['mensaje'] as Map<String, dynamic>);
        registrarVistos([m.id]);
        _acusar([m]);
        _emitir(MensajeNuevo(m));
      case 'SYNC':
        final mensajes = (json['mensajes'] as List)
            .map((m) => MensajeProveedor.fromJson(m as Map<String, dynamic>))
            .toList();
        registrarVistos(mensajes.map((m) => m.id));
        _acusar(mensajes);
        // Estuvo mucho tiempo fuera y no cupo todo en una tanda: se pide la siguiente.
        if (json['completo'] == false && _ultimoId != null) _mandar({'type': 'SYNC', 'desdeId': _ultimoId});
        _emitir(MensajesRecuperados(mensajes));
      case 'ESTADO':
        _emitir(EstadoActualizado(
          (json['ids'] as List).map((e) => (e as num).toInt()).toList(),
          EstadoMensaje.delServidor(json['estado'] as String?),
        ));
    }
  }

  /// Doble check gris para quien los escribió: llegaron a este teléfono.
  void _acusar(List<MensajeProveedor> mensajes) {
    final ids = [for (final m in mensajes) if (!m.mio && m.estado == EstadoMensaje.enviado) m.id];
    if (ids.isNotEmpty) _mandar({'type': 'ENTREGADO', 'ids': ids});
  }

  void _mandar(Map<String, dynamic> mensaje) {
    try {
      _socket?.enviar(jsonEncode(mensaje));
    } catch (_) {
      // Si el envío falla, el cierre del socket se encarga de reconectar.
    }
  }

  void _iniciarLatido() {
    _latido?.cancel();
    _latido = Timer.periodic(latido, (_) {
      _mandar({'type': 'PING'});
      _vigilancia?.cancel();
      _vigilancia = Timer(sinRespuesta, () {
        final socket = _socket;
        if (socket != null) _caido(socket);
      });
    });
  }

  void _caido(SocketMensajeria socket) {
    if (_socket != socket) return;
    _descartar();
    _cambiarEstado(ConexionEnVivo.desconectado);
    _programarReconexion();
  }

  void _programarReconexion() {
    if (_usuarios == 0) return;
    _reconexion?.cancel();
    _reconexion = Timer(esperaDelIntento(_intento++), () => unawaited(_abrir()));
  }

  void _descartar() {
    _latido?.cancel();
    _vigilancia?.cancel();
    unawaited(_entrada?.cancel());
    _entrada = null;
    final socket = _socket;
    _socket = null;
    try {
      socket?.cerrar();
    } catch (_) {
      // ya estaba cerrado
    }
  }

  void _emitir(EventoMensajeria evento) {
    if (!_eventos.isClosed) _eventos.add(evento);
  }

  void _cambiarEstado(ConexionEnVivo nuevo) {
    if (estado == nuevo || _cerrado) return;
    estado = nuevo;
    notifyListeners();
  }

  @override
  void dispose() {
    _cerrado = true;
    _red?.cancel();
    _ciclo?.dispose();
    _reconexion?.cancel();
    _descartar();
    _eventos.close();
    super.dispose();
  }
}
