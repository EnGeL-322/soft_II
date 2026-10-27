import 'dart:async';
import 'dart:convert';
import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:stomp_dart_client/stomp_dart_client.dart';

import '../api/api_config.dart';
import '../models/ruta.dart';

class EventoPunto {
  const EventoPunto(this.tipo, this.punto);
  final String tipo;
  final PuntoEstablecido punto;

  factory EventoPunto.fromJson(Map<String, dynamic> json) {
    final tipo = json['tipo'] as String;
    if (!const {'PUNTO_ACTIVADO', 'PUNTO_DESACTIVADO', 'PUNTO_ACTUALIZADO', 'PUNTO_ELIMINADO'}.contains(tipo)) {
      throw const FormatException('Evento de punto desconocido');
    }
    return EventoPunto(tipo, PuntoEstablecido.fromJson(json['punto'] as Map<String, dynamic>));
  }

  bool get retira => tipo == 'PUNTO_DESACTIVADO' || tipo == 'PUNTO_ELIMINADO' || !punto.activo;
}

/// Abstracción del transporte para probar pérdidas de red y carreras con REST sin un teléfono.
abstract class CanalPuntos {
  void iniciar();
  void cerrar();
}

typedef FabricaCanalPuntos =
    CanalPuntos Function({
      required Uri url,
      required String token,
      required int rutaId,
      required VoidCallback alSuscribir,
      required void Function(EventoPunto) alEvento,
      required VoidCallback alCaer,
    });

class _CanalStomp implements CanalPuntos {
  _CanalStomp({
    required Uri url,
    required String token,
    required int rutaId,
    required VoidCallback alSuscribir,
    required void Function(EventoPunto) alEvento,
    required VoidCallback alCaer,
  }) {
    final recibos = <String>{'ruta', 'directos'};
    void recibir(StompFrame frame) {
      try {
        if (frame.body != null) alEvento(EventoPunto.fromJson(jsonDecode(frame.body!) as Map<String, dynamic>));
      } on FormatException {
        // Un frame ilegible exige snapshot completo: nunca imprimir el frame ni sus cabeceras.
        alCaer();
      } on TypeError {
        alCaer();
      }
    }

    _cliente = StompClient(
      config: StompConfig(
        url: (kIsWeb ? url.replace(queryParameters: {'token': token}) : url).toString(),
        webSocketConnectHeaders: kIsWeb ? null : {'Authorization': 'Bearer $token'},
        reconnectDelay: Duration.zero, // El servicio gobierna el backoff exponencial.
        connectionTimeout: const Duration(seconds: 15),
        heartbeatIncoming: const Duration(seconds: 10),
        heartbeatOutgoing: const Duration(seconds: 10),
        onConnect: (_) {
          _cliente.subscribe(
            destination: '/topic/rutas/$rutaId/puntos',
            headers: {'receipt': 'ruta'},
            callback: recibir,
          );
          _cliente.subscribe(destination: '/user/queue/puntos', headers: {'receipt': 'directos'}, callback: recibir);
        },
        onUnhandledReceipt: (frame) {
          recibos.remove(frame.headers['receipt-id']);
          if (recibos.isEmpty) alSuscribir();
        },
        onWebSocketError: (_) => alCaer(),
        onWebSocketDone: alCaer,
        onStompError: (_) => alCaer(),
        onDisconnect: (_) => alCaer(),
        // No onDebugMessage: las cabeceras pueden contener credenciales.
      ),
    );
  }

  late final StompClient _cliente;
  @override
  void iniciar() => _cliente.activate();
  @override
  void cerrar() => _cliente.deactivate();
}

/// Provider/ChangeNotifier como el resto del proyecto. REST solo al suscribir/resumir/reintentar;
/// jamás hay un temporizador de polling. Mantiene tombstones de versión al retirar un punto.
class PuntosSocketService extends ChangeNotifier {
  PuntosSocketService({
    required Future<String?> Function() token,
    required Future<List<PuntoEstablecido>> Function() cargar,
    FabricaCanalPuntos? crearCanal,
    Uri? url,
    this.esperaBase = const Duration(seconds: 1),
    this.esperaMaxima = const Duration(seconds: 30),
  }) : _token = token,
       _cargar = cargar,
       _crearCanal = crearCanal ?? _CanalStomp.new,
       _url = url;

  final Future<String?> Function() _token;
  final Future<List<PuntoEstablecido>> Function() _cargar;
  final FabricaCanalPuntos _crearCanal;
  final Uri? _url;
  final Duration esperaBase;
  final Duration esperaMaxima;
  final _eventos = StreamController<EventoPunto>.broadcast();
  Stream<EventoPunto> get eventos => _eventos.stream;
  List<PuntoEstablecido> _puntos = [];
  List<PuntoEstablecido> get puntos => List.unmodifiable(_puntos);
  final Map<int, int> _versiones = {};
  final Map<int, EventoPunto> _duranteCarga = {};
  bool conectado = false;
  bool sincronizando = false;
  bool _activo = false;
  bool _disposed = false;
  bool _esperandoSnapshot = false;
  int? _rutaId;
  int? _acopiadorId;
  int _generacion = 0;
  int _peticion = 0;
  int _intento = 0;
  int _intentoRest = 0;
  CanalPuntos? _canal;
  Timer? _reconexion;
  Timer? _reintentoRest;
  Timer? _limiteConexion;

  Duration esperaDelIntento(int n) =>
      Duration(milliseconds: math.min(esperaBase.inMilliseconds * (1 << n.clamp(0, 20)), esperaMaxima.inMilliseconds));

  void iniciar({required int rutaId, required int acopiadorId}) {
    if (_activo && _rutaId == rutaId && _acopiadorId == acopiadorId) {
      if (conectado) unawaited(resincronizar());
      return;
    }
    detener();
    _rutaId = rutaId;
    _acopiadorId = acopiadorId;
    _versiones.clear();
    _duranteCarga.clear();
    _activo = true;
    _intento = 0;
    unawaited(_abrir());
  }

  Uri get _socketUrl {
    if (_url != null) return _url;
    final base = Uri.base.resolve(ApiConfig.baseUrl);
    return base.replace(scheme: base.scheme == 'https' ? 'wss' : 'ws', path: '/api/ws/puntos', query: '', fragment: '');
  }

  Future<void> _abrir() async {
    if (!_activo || _disposed) return;
    final generacion = ++_generacion;
    _esperandoSnapshot = true;
    _duranteCarga.clear();
    try {
      final token = await _token();
      if (!_vigente(generacion)) return;
      if (token == null) {
        detener();
        return;
      }
      _canal = _crearCanal(
        url: _socketUrl,
        token: token,
        rutaId: _rutaId!,
        alSuscribir: () {
          if (!_vigente(generacion)) return;
          _limiteConexion?.cancel();
          conectado = true;
          _intento = 0;
          notifyListeners();
          unawaited(resincronizar());
        },
        alEvento: (evento) {
          if (!_vigente(generacion)) return;
          final version = _versiones[evento.punto.id];
          if (version != null && evento.punto.version <= version) return;
          _versiones[evento.punto.id] = evento.punto.version;
          if (_esperandoSnapshot) _duranteCarga[evento.punto.id] = evento;
          _aplicar(evento);
          _eventos.add(evento);
          notifyListeners();
        },
        alCaer: () => _caer(generacion),
      );
      _limiteConexion = Timer(const Duration(seconds: 20), () => _caer(generacion));
      _canal!.iniciar();
    } catch (_) {
      _caer(generacion);
    }
  }

  bool _vigente(int generacion) => _activo && !_disposed && generacion == _generacion;
  bool _corresponde(PuntoEstablecido p) => p.activo && (p.rutaId == _rutaId || p.acopiadorId == _acopiadorId);

  void _aplicar(EventoPunto evento) {
    final p = evento.punto;
    _puntos = _puntos.where((e) => e.id != p.id).toList();
    if (!evento.retira && _corresponde(p)) _puntos.add(p);
    _ordenar();
  }

  void _ordenar() => _puntos.sort((a, b) {
    final nombre = a.nombre.toLowerCase().compareTo(b.nombre.toLowerCase());
    return nombre == 0 ? a.id.compareTo(b.id) : nombre;
  });

  Future<void> resincronizar() async {
    if (!_activo || !conectado || sincronizando || _disposed) return;
    final generacion = _generacion;
    final peticion = ++_peticion;
    sincronizando = true;
    _esperandoSnapshot = true;
    _reintentoRest?.cancel();
    notifyListeners();
    try {
      final snapshot = await _cargar();
      if (!_vigente(generacion) || peticion != _peticion) return;
      // Reemplazar solo al llegar REST, y volver a aplicar cambios ocurridos durante la petición.
      // Un snapshot antiguo no puede sobreescribir una actualización o baja más nueva.
      _puntos = snapshot.where(_corresponde).toList();
      for (final p in snapshot) {
        _versiones[p.id] = math.max(_versiones[p.id] ?? -1, p.version);
      }
      for (final evento in _duranteCarga.values) {
        final enSnapshot = snapshot.where((p) => p.id == evento.punto.id).firstOrNull;
        if (enSnapshot == null || evento.punto.version > enSnapshot.version) _aplicar(evento);
      }
      _duranteCarga.clear();
      _esperandoSnapshot = false;
      _intentoRest = 0;
      _ordenar();
    } catch (_) {
      if (!_vigente(generacion) || peticion != _peticion) return;
      // No borrar lo visible. Reintentar solamente esta resincronización fallida.
      _reintentoRest = Timer(esperaDelIntento(_intentoRest++), () => unawaited(resincronizar()));
    } finally {
      if (_vigente(generacion) && peticion == _peticion) {
        sincronizando = false;
        notifyListeners();
      }
    }
  }

  void _caer(int generacion) {
    if (!_vigente(generacion)) return;
    ++_generacion; // Invalida callbacks/replies de la conexión perdida.
    ++_peticion;
    conectado = false;
    sincronizando = false;
    _limiteConexion?.cancel();
    _reintentoRest?.cancel();
    final anterior = _canal;
    _canal = null;
    anterior?.cerrar();
    _reconexion?.cancel();
    _reconexion = Timer(esperaDelIntento(_intento++), () => unawaited(_abrir()));
    notifyListeners();
  }

  /// Al volver a primer plano se renueva el socket; el ACK dispara después el snapshot REST.
  void reconectar() {
    if (!_activo || _disposed) return;
    ++_generacion;
    ++_peticion;
    _reconexion?.cancel();
    _limiteConexion?.cancel();
    _reintentoRest?.cancel();
    conectado = false;
    sincronizando = false;
    _canal?.cerrar();
    _canal = null;
    unawaited(_abrir());
  }

  void detener() {
    _activo = false;
    ++_generacion;
    ++_peticion;
    conectado = false;
    sincronizando = false;
    _reconexion?.cancel();
    _reintentoRest?.cancel();
    _limiteConexion?.cancel();
    _canal?.cerrar();
    _canal = null;
  }

  @override
  void dispose() {
    detener();
    _disposed = true;
    unawaited(_eventos.close());
    super.dispose();
  }
}
