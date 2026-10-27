import 'dart:async';
import 'dart:io';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:dio/dio.dart';
import 'package:flutter/widgets.dart';
import 'package:uuid/uuid.dart';

import '../api/api_client.dart';
import '../api/calidad_api.dart';
import '../local/local_store.dart';
import '../models/calidad.dart';
import '../models/mensajeria.dart';

/// Nombres de cola que usa esta app. GPS del testeador sigue sin encolarse porque no tiene
/// endpoint en el backend (ver `ubicaciones_api.dart`): no se encola lo que nunca podría enviarse.
class TipoCola {
  static const recojo = 'recojo';
  static const ubicacion = 'ubicacion';

  /// Recorrido que el acopiador pintó para un día (V27). Se encola con la fecha dentro del payload,
  /// así que al sincronizar horas después queda en el día en que se pintó y no en el del envío.
  static const trazado = 'trazado';

  /// Foto del voucher de un control ya guardado (RF-85): la foto queda en el teléfono hasta que
  /// el servidor confirma que la recibió.
  static const voucher = 'voucher';

  /// Mensaje del proveedor a la planta escrito sin señal (bandeja de salida, como WhatsApp). Va bajo
  /// su clientUuid, que el backend usa para no guardarlo dos veces si el reintento llega repetido.
  static const mensaje = 'mensaje';
}

/// Un rechazo que no se arregla reintentando (la petición está mal o no tiene permiso). Sin
/// respuesta (sin señal, tiempo agotado), 401 (sesión vencida: se reintenta al volver a entrar),
/// 408, 409, 429 y 5xx se reintentan solos.
bool esRechazoDefinitivo(DioException e) {
  final status = e.response?.statusCode;
  if (status == null) return false;
  if (status == 401 || status == 408 || status == 409 || status == 429 || status >= 500) return false;
  return status >= 400;
}

/// Espera entre reintentos de la bandeja de salida: 2 s, 4 s, 8 s… hasta 1 minuto. Un mensaje es
/// algo que la persona está mirando, no puede esperar el barrido de respaldo de varios minutos.
Duration esperaReintentoMensaje(int intento) =>
    Duration(seconds: (2 << intento.clamp(0, 5)).clamp(2, 60));

/// Cuántos puntos GPS van como máximo en un envío. Un acopiador que estuvo toda la jornada sin
/// cobertura puede acumular cientos; mandarlos en una sola petición gigante es justo lo que falla
/// en una conexión rural que acaba de volver.
const _maxPuntosPorLote = 100;

/// Clave con la que cada pendiente de la cola guarda quién lo registró. Se quita antes de enviarlo.
const _claveAutor = '_autor';

/// Sincronización automática sin botón manual (11, RF-18): escucha conectividad y, al recuperar
/// señal, reenvía lo pendiente de la cola. También corre un barrido periódico de respaldo por si el
/// evento de conectividad no llega a tiempo (pasa en algunos Android en segundo plano).
///
/// El registro es idempotente por `clientUuid` (ver `RecojoService.registrar` en el backend), así
/// que un reintento duplicado tras un corte a medio camino no crea un recojo repetido.
class SyncService extends ChangeNotifier {
  /// `usuarioActual` dice quién tiene la sesión abierta. Todo lo que entra a la cola (recojos, GPS,
  /// trazados, vouchers, mensajes) lleva a su autor y solo se envía con la sesión de esa misma
  /// persona: si alguien cierra sesión con pendientes y entra otra cuenta en el mismo teléfono, lo
  /// pendiente no debe salir a nombre de la otra (un trazado iría a `/trazados-acopiador/me` del
  /// otro acopiador, un recojo quedaría registrado por él). Espera en el teléfono hasta que su
  /// dueño vuelva a entrar.
  SyncService(this._localStore, this._apiClient, {String? Function()? usuarioActual})
    : _usuarioActual = usuarioActual ?? (() => null);

  final LocalStore _localStore;
  final ApiClient _apiClient;
  final String? Function() _usuarioActual;

  StreamSubscription<List<ConnectivityResult>>? _conexionSub;
  Timer? _respaldo;
  Timer? _reintentoMensajes;
  int _intentoMensajes = 0;
  AppLifecycleListener? _lifecycleListener;
  Future<void>? _enCurso;
  bool _otraVuelta = false;
  int pendientesRecojo = 0;
  int pendientesUbicacion = 0;
  int pendientesTrazado = 0;
  int pendientesVoucher = 0;

  /// Mensajes de la sesión actual que siguen en la cola (pendientes o con error), en orden.
  List<MensajeProveedor> colaMensajes = const [];
  int get pendientesMensaje => colaMensajes.where((m) => m.estado == EstadoMensaje.pendiente).length;

  final _confirmados = StreamController<MensajeProveedor>.broadcast();

  /// Cada mensaje de la cola que el servidor confirmó, ya con su id y su estado.
  Stream<MensajeProveedor> get mensajesConfirmados => _confirmados.stream;

  bool get sincronizando => _enCurso != null;

  /// Espera entre barridos de respaldo cuando sigue sin haber señal. Empieza en 2 min y se dobla
  /// (hasta el tope) mientras la cola no baje a cero, para no gastar batería/datos reintentando cada
  /// 2 minutos toda la jornada; se reinicia apenas hay un evento de conectividad o la cola queda vacía.
  Duration _intervaloRespaldo = const Duration(minutes: 2);
  static const _intervaloRespaldoBase = Duration(minutes: 2);
  static const _intervaloRespaldoMax = Duration(minutes: 15);

  Future<void> iniciar() async {
    await _localStore.init();
    await _actualizarContador();
    _conexionSub = Connectivity().onConnectivityChanged.listen((estado) {
      if (!estado.contains(ConnectivityResult.none)) {
        _intervaloRespaldo = _intervaloRespaldoBase;
        sincronizar();
      }
    });
    // Al volver a primer plano (RF-18): el usuario puede haber recuperado señal mientras la app
    // estaba en segundo plano y el SO pausó los timers.
    _lifecycleListener = AppLifecycleListener(onResume: () => sincronizar());
    _programarRespaldo();
    unawaited(sincronizar());
  }

  void _programarRespaldo() {
    _respaldo?.cancel();
    _respaldo = Timer(_intervaloRespaldo, () async {
      await sincronizar();
      final quedaAlgoPendiente =
          pendientesRecojo > 0 || pendientesUbicacion > 0 || pendientesTrazado > 0 || pendientesMensaje > 0;
      _intervaloRespaldo = quedaAlgoPendiente
          ? Duration(seconds: (_intervaloRespaldo.inSeconds * 2).clamp(_intervaloRespaldoBase.inSeconds, _intervaloRespaldoMax.inSeconds))
          : _intervaloRespaldoBase;
      _programarRespaldo();
    });
  }

  /// Comprobación ligera de conexión REAL (RF-17/18): en zonas rurales puede haber wifi/datos
  /// encendidos sin salida a internet, y `connectivity_plus` no distingue eso. Un GET corto y barato
  /// ya autenticado, sin efectos secundarios, con timeout reducido para no dejar al acopiador
  /// esperando si de verdad no hay señal.
  Future<bool> hayConexionReal() async {
    try {
      await _apiClient.dio.get('/auth/me', options: Options(receiveTimeout: const Duration(seconds: 4)));
      return true;
    } on DioException catch (e) {
      // Cualquier respuesta del servidor (incluido un error de negocio) significa que SÍ hay
      // conexión; solo los errores de red/tiempo agotado cuentan como "no hay señal real".
      return e.type != DioExceptionType.connectionError &&
          e.type != DioExceptionType.connectionTimeout &&
          e.type != DioExceptionType.sendTimeout &&
          e.type != DioExceptionType.receiveTimeout;
    }
  }

  /// El pendiente con su autor, para no enviarlo después con la sesión de otra persona.
  Map<String, dynamic> _conAutor(Map<String, dynamic> payload) => {...payload, _claveAutor: _usuarioActual()};

  /// Se envía si lo registró quien tiene la sesión abierta. Un pendiente sin autor (encolado por una
  /// versión anterior de la app) se sigue enviando como antes, para no dejarlo varado.
  bool _esDeLaSesion(PendienteSync item) {
    final autor = item.payload[_claveAutor];
    return autor == null || autor == _usuarioActual();
  }

  /// Lo que viaja al servidor: el payload tal como se registró, sin la marca de autor.
  static Map<String, dynamic> _paraEnviar(Map<String, dynamic> payload) =>
      Map.of(payload)..remove(_claveAutor);

  Future<List<PendienteSync>> _pendientesDeLaSesion(String tipo) async =>
      (await _localStore.pendientes(tipo)).where(_esDeLaSesion).toList();

  Future<void> encolarRecojo(String clientUuid, Map<String, dynamic> payload) async {
    await _localStore.encolar(TipoCola.recojo, clientUuid, _conAutor(payload));
    await _actualizarContador();
    unawaited(sincronizar());
  }

  /// Guarda un punto GPS que no se pudo enviar. No dispara sincronización: si acaba de fallar el
  /// envío es porque no hay señal, y reintentar en ese mismo instante solo gasta batería. Lo
  /// recogen el evento de conectividad o el barrido periódico.
  Future<void> encolarUbicacion(String clientUuid, Map<String, dynamic> punto) async {
    await _localStore.encolar(TipoCola.ubicacion, clientUuid, _conAutor(punto));
    await _actualizarContador();
  }

  /// El recorrido pintado se encola bajo la fecha, no bajo un UUID nuevo: volver a pintar el mismo
  /// día tiene que reemplazar lo que espera en la cola. Si cada guardado generara su propia clave,
  /// al recuperar señal se enviarían todas las versiones intermedias del dibujo, una tras otra.
  Future<void> encolarTrazado(String fechaIso, Map<String, dynamic> cuerpo) async {
    await _localStore.encolar(TipoCola.trazado, 'trazado-$fechaIso', _conAutor(cuerpo));
    await _actualizarContador();
    unawaited(sincronizar());
  }

  /// Foto del voucher que no se pudo subir. Se encola bajo su propio clientUuid, que es también la
  /// llave de idempotencia del backend: si la subida llegó pero se cortó la respuesta, el reintento
  /// no crea una segunda evidencia.
  Future<void> encolarVoucher(SubidaVoucher subida) async {
    await _localStore.encolar(TipoCola.voucher, subida.clientUuid, _conAutor(subida.toJson()));
    await _actualizarContador();
  }

  /// Escribe el mensaje en la cola ANTES de intentar mandarlo: se ve al instante con reloj y no se
  /// pierde si la app se cierra o el teléfono se apaga antes de que haya señal.
  Future<String> encolarMensaje(String cuerpo) async {
    final clientUuid = const Uuid().v4();
    await _localStore.encolar(TipoCola.mensaje, clientUuid, {
      'cuerpo': cuerpo,
      'clientUuid': clientUuid,
      'usuario': _usuarioActual(),
    });
    await _actualizarContador();
    notifyListeners();
    unawaited(sincronizar());
    return clientUuid;
  }

  /// Un mensaje que el servidor rechazó vuelve a la cola para un nuevo intento, en su mismo lugar.
  Future<void> reintentarMensaje(String clientUuid) async {
    await _localStore.limpiarError(TipoCola.mensaje, clientUuid);
    _intentoMensajes = 0;
    await _actualizarContador();
    notifyListeners();
    unawaited(sincronizar());
  }

  Future<void> eliminarMensaje(String clientUuid) async {
    await _localStore.eliminar(TipoCola.mensaje, clientUuid);
    await _actualizarContador();
    notifyListeners();
  }

  /// Si ya hay un barrido en curso no se abre otro en paralelo (mandaría el mismo mensaje dos veces),
  /// pero tampoco se ignora el pedido: se hace otra vuelta al terminar, para que lo encolado durante
  /// el barrido no espere al próximo evento de conectividad.
  Future<void> sincronizar() {
    final enCurso = _enCurso;
    if (enCurso != null) {
      _otraVuelta = true;
      return enCurso;
    }
    final vuelta = _vueltas().whenComplete(() => _enCurso = null);
    _enCurso = vuelta;
    return vuelta;
  }

  Future<void> _vueltas() async {
    notifyListeners();
    do {
      _otraVuelta = false;
      await _unaVuelta();
    } while (_otraVuelta);
  }

  Future<void> _unaVuelta() async {
    try {
      final pendientes = await _pendientesDeLaSesion(TipoCola.recojo);
      for (final item in pendientes) {
        try {
          await _apiClient.dio.post('/recojos', data: _paraEnviar(item.payload));
          await _localStore.eliminar(TipoCola.recojo, item.clientUuid);
        } on DioException catch (e) {
          final esDeConexion =
              e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout;
          if (esDeConexion) {
            // Sigue sin señal: se deja en la cola para el próximo intento, sin marcar error.
            break;
          }
          // El servidor respondió (4xx/5xx real, no de red): queda registrado el motivo para que la
          // pantalla de "pendientes" lo muestre, pero no se descarta solo — el acopiador decide.
          final mensaje = (e.response?.data is Map ? (e.response?.data as Map)['message'] : null) ?? e.message;
          await _localStore.marcarError(TipoCola.recojo, item.clientUuid, mensaje.toString());
        }
      }
      await _sincronizarMensajes();
      await _sincronizarUbicaciones();
      await _sincronizarTrazados();
      await _sincronizarVouchers();
    } finally {
      await _actualizarContador();
      notifyListeners();
    }
  }

  /// Uno por uno y en el orden en que se escribieron. Un rechazo definitivo queda marcado (la
  /// persona decide si reintenta o lo borra) y no frena a los demás; un fallo de red corta la vuelta
  /// para no desordenar la conversación, y se reintenta con espera creciente.
  Future<void> _sincronizarMensajes() async {
    final usuario = _usuarioActual();
    if (usuario == null) return;
    for (final item in await _localStore.pendientes(TipoCola.mensaje)) {
      if (item.ultimoError != null || item.payload['usuario'] != usuario) continue;
      try {
        final r = await _apiClient.dio.post(
          '/mensajeria/mi-conversacion/mensajes',
          data: {'cuerpo': item.payload['cuerpo'], 'clientUuid': item.clientUuid},
        );
        await _localStore.eliminar(TipoCola.mensaje, item.clientUuid);
        _intentoMensajes = 0;
        _reintentoMensajes?.cancel();
        _confirmados.add(MensajeProveedor.fromJson(r.data as Map<String, dynamic>));
      } on DioException catch (e) {
        if (!esRechazoDefinitivo(e)) {
          _reintentoMensajes?.cancel();
          _reintentoMensajes = Timer(esperaReintentoMensaje(_intentoMensajes++), () => sincronizar());
          return;
        }
        final mensaje = (e.response?.data is Map ? (e.response?.data as Map)['message'] : null) ?? 'No se pudo enviar.';
        await _localStore.marcarError(TipoCola.mensaje, item.clientUuid, mensaje.toString());
      }
    }
  }

  /// Los puntos GPS van por lotes y en orden de captura, para que el recorrido se reconstruya tal
  /// como ocurrió. Se borran de la cola solo cuando el servidor confirma el lote entero.
  Future<void> _sincronizarUbicaciones() async {
    var pendientes = await _pendientesDeLaSesion(TipoCola.ubicacion);
    while (pendientes.isNotEmpty) {
      final lote = pendientes.take(_maxPuntosPorLote).toList();
      try {
        await _apiClient.dio.post(
          '/ubicaciones/lote',
          data: {'puntos': lote.map((p) => _paraEnviar(p.payload)).toList()},
        );
        for (final punto in lote) {
          await _localStore.eliminar(TipoCola.ubicacion, punto.clientUuid);
        }
      } on DioException catch (e) {
        final esDeConexion =
            e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout;
        if (esDeConexion) {
          return; // Sigue sin señal: el recorrido espera en la cola, no se descarta.
        }
        // El servidor rechazó el lote (p. ej. reloj del dispositivo adelantado). Se anota el motivo
        // en cada punto y se corta: reintentar el mismo lote en bucle no lo va a arreglar.
        final mensaje = (e.response?.data is Map ? (e.response?.data as Map)['message'] : null) ?? e.message;
        for (final punto in lote) {
          await _localStore.marcarError(TipoCola.ubicacion, punto.clientUuid, mensaje.toString());
        }
        return;
      }
      pendientes = pendientes.skip(lote.length).toList();
    }
  }

  /// El recorrido pintado se envía con PUT y es idempotente por (acopiador, fecha) en el backend, así
  /// que un reintento tras un corte a medio envío no crea un segundo trazado del mismo día.
  Future<void> _sincronizarTrazados() async {
    for (final item in await _pendientesDeLaSesion(TipoCola.trazado)) {
      try {
        await _apiClient.dio.put('/trazados-acopiador/me', data: _paraEnviar(item.payload));
        await _localStore.eliminar(TipoCola.trazado, item.clientUuid);
      } on DioException catch (e) {
        final esDeConexion =
            e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout;
        if (esDeConexion) {
          break; // Sigue sin señal: el recorrido espera en la cola.
        }
        final mensaje = (e.response?.data is Map ? (e.response?.data as Map)['message'] : null) ?? e.message;
        await _localStore.marcarError(TipoCola.trazado, item.clientUuid, mensaje.toString());
      }
    }
  }

  /// Las fotos van al final: pesan más que todo lo demás junto, y en una conexión que acaba de
  /// volver conviene que primero lleguen los recojos, que son los que mueven la liquidación.
  Future<void> _sincronizarVouchers() async {
    final api = CalidadApi(_apiClient);
    for (final item in await _pendientesDeLaSesion(TipoCola.voucher)) {
      final subida = SubidaVoucher.fromJson(_paraEnviar(item.payload));
      final foto = File(subida.rutaFoto);
      if (!foto.existsSync()) {
        // La foto se borró del teléfono (limpieza del sistema, reinstalación): ya no hay nada que
        // subir. Se deja constancia en la cola en vez de reintentar para siempre.
        await _localStore.marcarError(TipoCola.voucher, item.clientUuid, 'La foto ya no está en el teléfono.');
        continue;
      }
      try {
        await api.subirVoucher(subida);
        await _localStore.eliminar(TipoCola.voucher, item.clientUuid);
        await foto.delete();
      } on DioException catch (e) {
        final reintentable = e.type == DioExceptionType.connectionError ||
            e.type == DioExceptionType.connectionTimeout ||
            e.type == DioExceptionType.sendTimeout ||
            e.type == DioExceptionType.receiveTimeout ||
            e.response?.statusCode == 503;
        if (reintentable) {
          break; // Sin señal o almacén caído: la foto sigue esperando en el teléfono.
        }
        final mensaje = (e.response?.data is Map ? (e.response?.data as Map)['message'] : null) ?? e.message;
        await _localStore.marcarError(TipoCola.voucher, item.clientUuid, mensaje.toString());
      }
    }
  }

  Future<void> _actualizarContador() async {
    // Solo lo de quien tiene la sesión: lo de otra cuenta no lo puede enviar ni corregir esta persona.
    pendientesRecojo = (await _pendientesDeLaSesion(TipoCola.recojo)).length;
    pendientesUbicacion = (await _pendientesDeLaSesion(TipoCola.ubicacion)).length;
    pendientesTrazado = (await _pendientesDeLaSesion(TipoCola.trazado)).length;
    pendientesVoucher = (await _pendientesDeLaSesion(TipoCola.voucher)).length;
    final usuario = _usuarioActual();
    colaMensajes = [
      for (final item in await _localStore.pendientes(TipoCola.mensaje))
        if (usuario != null && item.payload['usuario'] == usuario)
          MensajeProveedor.enCola(
            clientUuid: item.clientUuid,
            cuerpo: item.payload['cuerpo'] as String,
            creadoEn: item.creadoEn,
            error: item.ultimoError,
          ),
    ];
  }

  @override
  void dispose() {
    _conexionSub?.cancel();
    _respaldo?.cancel();
    _reintentoMensajes?.cancel();
    _confirmados.close();
    _lifecycleListener?.dispose();
    super.dispose();
  }
}
