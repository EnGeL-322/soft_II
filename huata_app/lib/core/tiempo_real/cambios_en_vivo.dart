import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';

import '../api/api_config.dart';
import '../mensajeria/mensajeria_en_vivo.dart' show ConexionEnVivo, SocketMensajeria, abrirSocketReal, urlCanal;

/// Lo único que llega por /api/ws/cambios: qué cambió (ver `CambiosWebSocketHandler` en el backend).
/// Nunca datos: la pantalla los vuelve a pedir por la API, que valida los permisos.
class CambioPublico {
  const CambioPublico({required this.modulo, required this.entidad, required this.id, required this.accion});

  final String modulo;
  final String entidad;
  final int id;
  final String accion;

  factory CambioPublico.fromJson(Map<String, dynamic> j) => CambioPublico(
    modulo: j['modulo'] as String? ?? '',
    entidad: j['entidad'] as String? ?? '',
    id: (j['id'] as num?)?.toInt() ?? 0,
    accion: j['accion'] as String? ?? '',
  );
}

/// Entidades cuyo cambio puede tocar los permisos de esta misma sesión.
const _entidadesDeAcceso = {'Usuario', 'UsuarioPermiso', 'Rol', 'Vista', 'Modulo'};

/// Canal de cambios: cuando alguien guarda algo (en la web o en otro celular), las pantallas abiertas
/// que lo muestran se recargan solas.
///
/// Mismo comportamiento de red que la mensajería: reconecta con espera creciente, al volver la señal y
/// al volver la app a primer plano, y manda un latido para notar una conexión muerta. Al reconectar,
/// cada pantalla recarga una vez por lo que pudo perderse durante el corte (no hay "SYNC" que pedir:
/// los datos están en la API).
class CambiosEnVivo extends ChangeNotifier {
  CambiosEnVivo({
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
       _abrirSocket = abrirSocket ?? abrirSocketReal,
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

  /// Lo fija `main.dart`: cambiaron roles o permisos de esta cuenta, hay que releerlos.
  VoidCallback? alCambiarAcceso;

  ConexionEnVivo estado = ConexionEnVivo.desconectado;
  bool get conectado => estado == ConexionEnVivo.conectado;

  final _cambios = StreamController<List<CambioPublico>>.broadcast();
  final _reconectado = StreamController<void>.broadcast();

  int _usuarios = 0;
  int _intento = 0;
  bool _yaConecto = false;
  bool _cerrado = false;
  SocketMensajeria? _socket;
  StreamSubscription<dynamic>? _entrada;
  StreamSubscription<bool>? _red;
  AppLifecycleListener? _ciclo;
  Timer? _reconexion;
  Timer? _latido;
  Timer? _vigilancia;

  Duration esperaDelIntento(int n) {
    final ms = esperaBase.inMilliseconds * (1 << n.clamp(0, 20));
    return Duration(milliseconds: math.min(ms, esperaMaxima.inMilliseconds));
  }

  /// Los cambios de esos módulos, agrupados: varios guardados seguidos producen una sola recarga.
  /// Emite también (lista vacía) al reconectar tras un corte.
  Stream<List<CambioPublico>> de(Set<String> modulos, {Duration espera = const Duration(milliseconds: 600)}) {
    late StreamController<List<CambioPublico>> salida;
    final pendientes = <CambioPublico>[];
    Timer? agrupar;
    StreamSubscription<List<CambioPublico>>? subCambios;
    StreamSubscription<void>? subReconexion;
    salida = StreamController<List<CambioPublico>>(
      onListen: () {
        subCambios = _cambios.stream.listen((cambios) {
          final suyos = cambios.where((c) => modulos.contains(c.modulo));
          if (suyos.isEmpty) return;
          pendientes.addAll(suyos);
          agrupar?.cancel();
          agrupar = Timer(espera, () {
            if (pendientes.isEmpty || salida.isClosed) return;
            salida.add(List.of(pendientes));
            pendientes.clear();
          });
        });
        subReconexion = _reconectado.stream.listen((_) {
          if (!salida.isClosed) salida.add(const []);
        });
      },
      onCancel: () {
        agrupar?.cancel();
        unawaited(subCambios?.cancel());
        unawaited(subReconexion?.cancel());
      },
    );
    return salida.stream;
  }

  /// Varias pantallas usan el canal: se abre con la primera y se cierra con la última.
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
    _yaConecto = false;
    _cambiarEstado(ConexionEnVivo.desconectado);
  }

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
      socket = _abrirSocket(_url ?? urlCanal(ApiConfig.baseUrl, 'cambios'));
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
        if (_yaConecto && !_reconectado.isClosed) _reconectado.add(null);
        _yaConecto = true;
      case 'CAMBIOS':
        final cambios = (json['cambios'] as List? ?? const [])
            .whereType<Map<String, dynamic>>()
            .map(CambioPublico.fromJson)
            .toList();
        if (cambios.any((c) => _entidadesDeAcceso.contains(c.entidad))) alCambiarAcceso?.call();
        if (cambios.isNotEmpty && !_cambios.isClosed) _cambios.add(cambios);
    }
  }

  void _iniciarLatido() {
    _latido?.cancel();
    _latido = Timer.periodic(latido, (_) {
      try {
        _socket?.enviar(jsonEncode({'type': 'PING'}));
      } catch (_) {
        // el cierre del socket se encarga de reconectar
      }
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
    _cambios.close();
    _reconectado.close();
    super.dispose();
  }
}

/// Para el `State` de una pantalla: recarga sola cuando cambia algo de esos módulos.
///
/// ```dart
/// class _MiPantallaState extends State<MiPantalla> with EnVivo<MiPantalla> {
///   @override
///   void initState() {
///     super.initState();
///     recargarEnVivo({'pagos'}, () => _cargar(silencioso: true));
///   }
/// }
/// ```
/// Sin el servicio en el árbol (pruebas de una pantalla suelta) no hace nada.
mixin EnVivo<T extends StatefulWidget> on State<T> {
  CambiosEnVivo? _canalEnVivo;
  StreamSubscription<List<CambioPublico>>? _suscripcionEnVivo;

  void recargarEnVivo(Set<String> modulos, VoidCallback recargar) {
    final CambiosEnVivo canal;
    try {
      canal = context.read<CambiosEnVivo>();
    } on ProviderNotFoundException {
      return;
    }
    _suscripcionEnVivo?.cancel();
    _canalEnVivo ??= canal..iniciar();
    _suscripcionEnVivo = canal.de(modulos).listen((_) {
      if (mounted) recargar();
    });
  }

  @override
  void dispose() {
    _suscripcionEnVivo?.cancel();
    _canalEnVivo?.detener();
    super.dispose();
  }
}
