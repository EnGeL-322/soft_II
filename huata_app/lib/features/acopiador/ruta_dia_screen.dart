import 'dart:async';

import 'package:connectivity_plus/connectivity_plus.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:dio/dio.dart';
import 'package:geolocator/geolocator.dart';
import 'package:provider/provider.dart';
import 'package:uuid/uuid.dart';

import '../../core/api/acopiadores_api.dart';
import '../../core/api/calidad_api.dart';
import '../../core/api/recojos_api.dart';
import '../../core/api/rutas_api.dart';
import '../../core/api/ubicaciones_api.dart';
import '../../core/auth/auth_controller.dart';
import '../../core/auth/token_store.dart';
import '../../core/rutas/puntos_socket_service.dart';
import '../../core/mapa/geocerca.dart';
import '../../core/models/reglas_acopio.dart';
import '../../core/mapa/recorrido_mapa.dart';
import '../../core/models/acopiador.dart';
import '../../core/models/recojo.dart';
import '../../core/models/ruta.dart';
import '../../core/sync/sync_service.dart';
import '../../shared/huata_scaffold.dart';
import 'cabecera_jornada.dart';
import 'parada_ruta.dart';
import 'pintar_ruta_screen.dart';
import 'recojo_correccion_sheet.dart';
import 'recojo_form_sheet.dart';
import 'vista_mapa.dart';
import 'vista_paradas.dart';
import 'vista_resumen.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Vistas de la pantalla del acopiador: una tarea a la vez.
enum _Vista { paradas, mapa, resumen }

/// Pantalla principal del acopiador (11, RF-12, RF-52, RF-75-77): ruta del día, proveedores en
/// orden, estado blanco/verde/rojo por proveedor y total acumulado arriba. La lógica (offline, GPS,
/// geocerca, correcciones) vive aquí; la presentación está en `cabecera_jornada.dart`,
/// `vista_mapa.dart`, `vista_paradas.dart` y `vista_resumen.dart`.
class RutaDiaScreen extends StatefulWidget {
  const RutaDiaScreen({super.key});

  @override
  State<RutaDiaScreen> createState() => _RutaDiaScreenState();
}

class _RutaDiaScreenState extends State<RutaDiaScreen>
    with WidgetsBindingObserver, EnVivo<RutaDiaScreen> {
  bool _cargando = true;
  String? _error;
  Acopiador? _yo;
  ReporteAcopiador? _reporte;
  String _periodoReporte = 'DIA';
  AsignacionRuta? _asignacionHoy;
  List<ParadaRuta> _filas = [];
  _Vista _vista = _Vista.paradas;
  bool _compartiendoGps = false;
  StreamSubscription<Position>? _gpsSub;
  Timer? _latidoGps;
  DateTime? _ultimoEnvioGps;
  final List<Position> _puntosGps = [];
  List<RutaPunto> _puntosRuta = [];
  List<ZonaPoligono> _poligonos = [];
  List<PuntoEstablecido> _puntosEstablecidos = [];
  int _consultaProveedores = 0;
  bool _proveedoresPendientes = false;
  late final PuntosSocketService _puntosSocket;
  late final AuthController _auth;
  StreamSubscription<EventoPunto>? _puntosSub;
  StreamSubscription<List<ConnectivityResult>>? _puntosRedSub;

  late final SyncService _sync;
  int _pendientesAntes = 0;
  // true mientras una sincronización la disparó esta pantalla ("corregir"): así el aviso ambiente de
  // abajo no duplica el mensaje que ese flujo ya muestra por su cuenta. La sincronización normal es
  // automática (no hay botón manual): la hace SyncService al recuperar señal.
  bool _sincronizacionIniciadaPorUi = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _auth = context.read<AuthController>();
    _auth.addListener(_alCambiarSesion);
    _puntosSocket = PuntosSocketService(
      token: context.read<TokenStore>().read,
      cargar: context.read<RutasApi>().snapshotPuntosEstablecidos,
    );
    _puntosSocket.addListener(_alCambiarPuntos);
    _puntosSub = _puntosSocket.eventos.listen((_) => _alCambiarPuntos());
    _puntosRedSub = Connectivity().onConnectivityChanged.listen((redes) {
      if (redes.any((red) => red != ConnectivityResult.none)) {
        _puntosSocket.reconectar();
      }
    });
    _sync = context.read<SyncService>();
    _pendientesAntes = _sync.pendientesRecojo;
    // En vivo (la web cambió la ruta, un proveedor, otro recojo...). Con recojos en la cola offline NO se
    // recarga: el servidor aún no los tiene y la lista los mostraría como pendientes (riesgo de
    // registrarlos dos veces). Al vaciarse la cola, _alCambiarSync ya recarga.
    recargarEnVivo({'rutas', 'acopio', 'proveedores', 'calidad'}, () {
      if (_sync.pendientesRecojo == 0 && !_sync.sincronizando) _cargar(silencioso: true);
    });
    _sync.addListener(_alCambiarSync);
    _cargar();
  }

  @override
  void dispose() {
    WidgetsBinding.instance.removeObserver(this);
    _auth.removeListener(_alCambiarSesion);
    _puntosSub?.cancel();
    _puntosRedSub?.cancel();
    _puntosSocket.removeListener(_alCambiarPuntos);
    _puntosSocket.dispose();
    _sync.removeListener(_alCambiarSync);
    _gpsSub?.cancel();
    _latidoGps?.cancel();
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) _puntosSocket.reconectar();
  }

  void _alCambiarSesion() {
    if (_auth.estado != EstadoSesion.autenticado) _puntosSocket.detener();
  }

  void _alCambiarPuntos() {
    if (!mounted) return;
    final nuevos = _puntosSocket.puntos;
    String identidad(List<PuntoEstablecido> puntos) => puntos
        .map(
          (p) =>
              '${p.id}:${p.version}:${p.latitud}:${p.longitud}:${p.proveedorId}',
        )
        .join('|');
    final cambiaron = identidad(nuevos) != identidad(_puntosEstablecidos);
    setState(() => _puntosEstablecidos = nuevos);
    if (cambiaron || (_proveedoresPendientes && !_puntosSocket.sincronizando)) {
      unawaited(_refrescarProveedoresDeRuta());
    }
  }

  /// Un evento de punto también cambia ubicación/alta del proveedor. No reinicia el socket
  /// ni la jornada: mantiene los recojos y la selección de puntos por sus IDs existentes.
  Future<void> _refrescarProveedoresDeRuta() async {
    final rutaId = _asignacionHoy?.rutaId;
    if (rutaId == null || _auth.estado != EstadoSesion.autenticado) return;
    final consulta = ++_consultaProveedores;
    try {
      final proveedores =
          (await context.read<RutasApi>().proveedoresDeRuta(rutaId))
              .where(
                (p) => p.puedeEntregar || p.estado == 'PENDIENTE_APROBACION',
              )
              .toList();
      if (!mounted ||
          consulta != _consultaProveedores ||
          _asignacionHoy?.rutaId != rutaId ||
          _auth.estado != EstadoSesion.autenticado) {
        return;
      }
      _proveedoresPendientes = false;
      final anteriores = {for (final f in _filas) f.proveedor.id: f};
      setState(
        () => _filas = proveedores.map((p) {
          final anterior = anteriores[p.id];
          return ParadaRuta(
            p,
            anterior?.estado ?? EstadoVisita.pendiente,
            anterior?.recojo,
          )..testeoHoy = anterior?.testeoHoy;
        }).toList(),
      );
    } catch (_) {
      if (mounted && consulta == _consultaProveedores) {
        _proveedoresPendientes = true;
      }
      // Conserva la jornada existente. Al reconectar se recibe otro snapshot y se reintenta.
    }
  }

  /// Avisa cuando una sincronización EN SEGUNDO PLANO (conectividad recuperada, barrido periódico,
  /// vuelta a primer plano) termina de subir lo pendiente, y refresca la lista para que se vean los
  /// ids/estados reales.
  void _alCambiarSync() {
    final ahora = _sync.pendientesRecojo;
    if (_pendientesAntes > 0 &&
        ahora == 0 &&
        !_sync.sincronizando &&
        !_sincronizacionIniciadaPorUi) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(const SnackBar(content: Text('Recojos sincronizados')));
        _cargar();
      }
    }
    _pendientesAntes = ahora;
  }

  Future<void> _cargar({bool silencioso = false}) async {
    ++_consultaProveedores;
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final acopiadoresApi = context.read<AcopiadoresApi>();
      final rutasApi = context.read<RutasApi>();
      final recojosApi = context.read<RecojosApi>();

      final yo = await acopiadoresApi.yo();
      final reporte = await recojosApi.miReporte(periodo: _periodoReporte);
      final hoy = DateTime.now();
      final asignaciones = await rutasApi.asignaciones(
        fecha: hoy,
        acopiadorId: yo.id,
      );
      if (!mounted || _auth.estado != EstadoSesion.autenticado) return;

      if (asignaciones.isEmpty && yo.rutaId == null) {
        _puntosSocket.detener();
        setState(() {
          _yo = yo;
          _reporte = reporte;
          _asignacionHoy = null;
          _filas = [];
          _puntosRuta = [];
          _cargando = false;
        });
        return;
      }

      // Al cambiar la zona/ruta habitual puede no existir todavía una fila de planificación para
      // hoy. La ruta del perfil es la fuente vigente y evita dejar la aplicación en blanco.
      final asignacion = asignaciones.isNotEmpty
          ? asignaciones.first
          : AsignacionRuta(
              id: -yo.id,
              rutaId: yo.rutaId!,
              rutaNombre: yo.rutaNombre ?? 'Ruta asignada',
              acopiadorId: yo.id,
              acopiadorNombre: yo.nombreCompleto,
              fecha:
                  '${hoy.year}-${hoy.month.toString().padLeft(2, '0')}-${hoy.day.toString().padLeft(2, '0')}',
              observacion: 'Ruta habitual vigente',
            );
      // Identificar la ruta requiere REST, pero los PUNTOS se cargan exclusivamente después de
      // confirmar ambas suscripciones STOMP. No hay carga inicial de puntos antes del socket.
      _puntosSocket.iniciar(rutaId: asignacion.rutaId, acopiadorId: yo.id);
      // Los pendientes son visibles al agregarlos desde la web. El formulario de recojo sigue
      // exigiendo puedeEntregar; los retirados y en revisión no se incluyen.
      final proveedores = (await rutasApi.proveedoresDeRuta(asignacion.rutaId))
          .where((p) => p.puedeEntregar || p.estado == 'PENDIENTE_APROBACION')
          .toList();
      final puntosRuta = await rutasApi.puntosDeRuta(asignacion.rutaId);
      final inicioDia = DateTime(hoy.year, hoy.month, hoy.day);
      final finDia = inicioDia.add(const Duration(days: 1));
      final recojosHoy = await recojosApi.listar(
        rutaId: asignacion.rutaId,
        desde: inicioDia,
        hasta: finDia,
        size: 500,
      );

      final filas = proveedores.map((p) {
        final recojo = recojosHoy
            .where((r) => r.productorId == p.id && r.estado != 'CANCELLED')
            .firstOrNull;
        final estado = recojo == null
            ? EstadoVisita.pendiente
            : (recojo.estadoVisita == 'NO_ENTREGADO'
                  ? EstadoVisita.noEntregado
                  : EstadoVisita.acopiado);
        return ParadaRuta(p, estado, recojo);
      }).toList();

      // Solo para mostrar quién ya fue testeado hoy; si falla, la lista se ve igual que antes.
      try {
        if (!mounted) return;
        final analisisHoy = await context.read<CalidadApi>().recientes(dias: 1);
        for (final f in filas) {
          f.testeoHoy = analisisHoy.where((a) => a.productorId == f.proveedor.id).firstOrNull?.resultado ?? '';
        }
      } catch (_) {}

      if (!mounted) return;
      setState(() {
        _yo = yo;
        _reporte = reporte;
        _asignacionHoy = asignacion;
        _filas = filas;
        _puntosRuta = puntosRuta
          ..sort((a, b) => a.secuencia.compareTo(b.secuencia));
        _cargando = false;
      });
      _cargarPoligonos();
      unawaited(_refrescarProveedoresDeRuta());
    } catch (e) {
      // En vivo: si la recarga falla (sin señal), se queda lo que ya se veía.
      if (silencioso) return;
      if (!mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  /// Geocerca y distancia vigentes. Se piden al cargar la ruta; si el servidor no responde
  /// (sin señal), rigen las estrictas.
  ReglasAcopio _reglas = ReglasAcopio.estrictas;

  Future<void> _cargarReglas() async {
    try {
      final reglas = await context.read<RutasApi>().reglasAcopio();
      if (mounted) setState(() => _reglas = reglas);
    } catch (_) {
      // Sin respuesta se conservan las últimas conocidas (o las estrictas).
    }
  }

  Future<void> _cargarPoligonos() async {
    unawaited(_cargarReglas());
    try {
      final poligonos = await context.read<RutasApi>().poligonosDeZona();
      if (mounted) setState(() => _poligonos = poligonos);
    } catch (_) {
      // El polígono es un dato decorativo del mapa (territorio de la zona); si falla, el mapa sigue
      // funcionando solo con los puntos de la ruta y el recorrido GPS.
    }
  }

  /// Polígono de la zona de la ruta del día, o null si la ruta no tiene zona o la zona no tiene
  /// polígono dibujado. Sin polígono no se puede exigir geocerca.
  ZonaPoligono? get _poligonoDeLaRuta {
    final zona = _asignacionHoy?.zona;
    if (zona == null || zona.trim().isEmpty) return null;
    final buscada = zona.trim().toLowerCase();
    for (final p in _poligonos) {
      if (p.zona.trim().toLowerCase() == buscada) return p;
    }
    return null;
  }

  /// true si la coordenada está dentro de la zona de la ruta (o si no hay polígono que exigir).
  bool _dentroDeLaZona(double latitud, double longitud) {
    // Geocerca apagada en "Precios y calidad" (p. ej. para una demostración): todo cuenta como dentro.
    if (!_reglas.exigirGeocerca) return true;
    final poligono = _poligonoDeLaRuta;
    if (poligono == null) return true;
    return dentroDePoligono(latitud, longitud, poligono.puntos);
  }

  /// true si el recorrido está en curso y la última posición GPS cayó fuera de la zona: mientras dure,
  /// el acopiador no puede registrar leche hasta volver a entrar.
  bool get _fueraDeLaZona {
    if (!_compartiendoGps || _puntosGps.isEmpty) return false;
    final p = _puntosGps.last;
    return !_dentroDeLaZona(p.latitude, p.longitude);
  }

  double get _totalAcumulado => _filas
      .where((f) => f.estado == EstadoVisita.acopiado)
      .fold(0.0, (s, f) => s + (f.recojo?.litros ?? 0));

  /// "Siguiente parada": la primera pendiente (entrega habilitada) en orden de visita. Es la que
  /// destaca el mapa y la que ofrece la tarjeta "Registrar recojo" de abajo.
  ParadaRuta? get _siguientePendiente {
    final pendientes = _filas
        .where(
          (f) =>
              f.estado == EstadoVisita.pendiente &&
              f.proveedor.puedeEntregar,
        )
        .toList()
      ..sort(
        (a, b) => (a.proveedor.ordenEnRuta ?? 1 << 30).compareTo(
          b.proveedor.ordenEnRuta ?? 1 << 30,
        ),
      );
    return pendientes.isEmpty ? null : pendientes.first;
  }

  Future<void> _cambiarPeriodo(String periodo) async {
    setState(() => _periodoReporte = periodo);
    try {
      final reporte = await context.read<RecojosApi>().miReporte(
        periodo: periodo,
      );
      if (mounted) setState(() => _reporte = reporte);
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
      }
    }
  }

  /// Distancia maxima antes de avisar/bloquear (pedido del cliente, 2026-09-24): sale de las reglas
  /// de acopio del servidor (RECOJO_DISTANCIA_MAXIMA_METROS), 200 m si todavia no se conocen.
  double get _distanciaMaximaMetros => _reglas.distanciaMaximaMetros;

  /// Mejor esfuerzo: null si no hay permiso, GPS apagado, o no hay lectura a tiempo. Nunca lanza --
  /// un fallo de GPS no debe impedir registrar el recojo (ver _abrirFormulario).
  Future<Position?> _posicionActual() async {
    try {
      var permiso = await Geolocator.checkPermission();
      if (permiso == LocationPermission.denied) {
        permiso = await Geolocator.requestPermission();
      }
      if (permiso == LocationPermission.denied ||
          permiso == LocationPermission.deniedForever) {
        return null;
      }
      if (!await Geolocator.isLocationServiceEnabled()) {
        return null;
      }
      return await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(
          accuracy: LocationAccuracy.high,
          timeLimit: Duration(seconds: 8),
        ),
      );
    } catch (_) {
      return null;
    }
  }

  Future<void> _abrirFormulario(ParadaRuta fila) async {
    if (!fila.proveedor.puedeEntregar) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text(
            'Proveedor pendiente de aprobación. Todavía no puede entregar leche.',
          ),
        ),
      );
      return;
    }
    // Un proveedor entrega leche una sola vez al dia (regla ya vigente en el backend,
    // RecojoService.registrar): volver a tocar una fila ya acopiada NO puede reabrir el formulario
    // de registro, porque el backend rechazaria un segundo recojo y la app se quedaba reintentando
    // esa escritura para siempre en la cola offline sin que el acopiador se enterara de nada.
    // Corregir la fila es la via oficial (pedido del cliente, 2026-09-24).
    if (fila.estado == EstadoVisita.acopiado && fila.recojo != null) {
      await _abrirCorreccion(fila);
      return;
    }
    if (fila.estado == EstadoVisita.noEntregado) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('La visita de este proveedor ya fue registrada hoy.'),
        ),
      );
      return;
    }

    // Se leen antes del primer await: si el acopiador sale de la pantalla mientras se obtiene el
    // GPS, el recojo igual tiene que enviarse o encolarse, y el context ya no sería válido.
    final recojosApi = context.read<RecojosApi>();
    final syncService = context.read<SyncService>();

    final resultado = await showModalBottomSheet<RecojoFormResultado>(
      context: context,
      isScrollControlled: true,
      builder: (_) => RecojoFormSheet(proveedor: fila.proveedor),
    );
    if (resultado == null) return;

    final posicion = await _posicionActual();

    // Geocerca de zona: si la ruta tiene zona con polígono dibujado y el GPS cae fuera, no se
    // registra leche. El backend aplica la misma regla, así que esto no es solo un aviso de UI.
    if (posicion != null && !_dentroDeLaZona(posicion.latitude, posicion.longitude)) {
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (_) => AlertDialog(
          title: const Text('Estás fuera de tu zona'),
          content: Text(
            'El GPS marca que estás fuera de la zona ${_asignacionHoy?.zona ?? ''}. '
            'Vuelve a tu zona para poder registrar leche. Si el polígono está mal, avisa a un '
            'supervisor.',
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(context).pop(),
              child: const Text('Entendido'),
            ),
          ],
        ),
      );
      return;
    }

    final proveedorLat = fila.proveedor.latitud;
    final proveedorLon = fila.proveedor.longitud;
    double? distanciaM;
    if (posicion != null && proveedorLat != null && proveedorLon != null) {
      distanciaM = Geolocator.distanceBetween(
        posicion.latitude,
        posicion.longitude,
        proveedorLat,
        proveedorLon,
      );
    }

    if (_reglas.exigirDistancia && distanciaM != null && distanciaM >= _distanciaMaximaMetros) {
      final estado = await Connectivity().checkConnectivity();
      final enLinea = !estado.contains(ConnectivityResult.none);
      if (enLinea) {
        // En linea: se puede avisar de inmediato y el acopiador puede acercarse y reintentar. Sin
        // conexion no hay como confirmar nada en el momento (RN-17/18) -- se sigue de largo y el
        // backend lo marca para revision al sincronizar, en vez de bloquear una entrega real.
        if (!mounted) return;
        await showDialog<void>(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('Estás lejos del proveedor'),
            content: Text(
              'Tu GPS marca ${distanciaM!.round()} m de ${fila.proveedor.nombreCompleto}. Acércate al '
              'punto registrado (hasta unos ${_distanciaMaximaMetros.round()} m) o avisa a un '
              'supervisor si el punto está mal ubicado.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Entendido'),
              ),
            ],
          ),
        );
        return;
      }
    }

    final clientUuid = const Uuid().v4();
    final request = RecojoRequest(
      clientUuid: clientUuid,
      productorId: fila.proveedor.id,
      acopiadorId: _yo!.id,
      rutaId: _asignacionHoy!.rutaId,
      fechaHora: DateTime.now(),
      litros: resultado.noEntregado ? 0 : resultado.litros!,
      fuente: 'RUTA',
      estadoVisita: resultado.noEntregado ? 'NO_ENTREGADO' : 'ACOPIADO',
      motivoNoEntrega: resultado.noEntregado ? resultado.motivo : null,
      latitud: posicion?.latitude,
      longitud: posicion?.longitude,
    );

    // Optimista: la fila cambia de color de inmediato aunque la escritura quede en cola offline.
    if (mounted) {
      setState(() {
        fila.estado = resultado.noEntregado
            ? EstadoVisita.noEntregado
            : EstadoVisita.acopiado;
        fila.recojo = Recojo(
          id: -1,
          productorId: fila.proveedor.id,
          productorNombre: fila.proveedor.nombreCompleto,
          fechaHora: request.fechaHora,
          litros: request.litros,
          estado: 'PENDING_SYNC',
          estadoVisita: request.estadoVisita,
          motivoNoEntrega: request.motivoNoEntrega,
        );
      });
    }

    final nombre = fila.proveedor.nombreCompleto;
    final confirmacion = resultado.noEntregado
        ? 'No entrega registrada: $nombre'
        : '${formatoLitros(request.litros)} registrados: $nombre';
    try {
      await recojosApi.registrar(request);
      _avisar(confirmacion, icono: Icons.check_circle_outline);
      // Recarga desde el servidor: tras salir y volver a entrar el estado se calcula con el dato
      // persistido, no solo con el cambio optimista de esta pantalla. Silenciosa: la fila ya cambió.
      await _cargar(silencioso: true);
    } on DioException catch (e) {
      final esDeConexion =
          e.type == DioExceptionType.connectionError ||
          e.type == DioExceptionType.connectionTimeout ||
          e.type == DioExceptionType.receiveTimeout ||
          e.type == DioExceptionType.sendTimeout;
      if (esDeConexion) {
        // Solo la ausencia real de red va a la cola offline. Un 400/403/409 ya recibió una
        // respuesta del servidor y reintentarlo después no arreglaría el dato.
        await syncService.encolarRecojo(clientUuid, request.toJson());
        _avisar('Sin señal: guardado en el teléfono. Se enviará solo.', icono: Icons.cloud_off_outlined);
        return;
      }
      if (mounted) {
        setState(() {
          fila.estado = EstadoVisita.pendiente;
          fila.recojo = null;
        });
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          fila.estado = EstadoVisita.pendiente;
          fila.recojo = null;
        });
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
      }
    }
  }

  /// Corrige un recojo ya registrado (pedido del cliente, 2026-09-24). El recojo tiene que existir ya
  /// en el servidor: si todavía está en la cola offline (id local -1) pero SÍ hay conexión real, se
  /// sincroniza primero y se sigue con la corrección; el bloqueo ("todavía no se sincronizó") solo
  /// aparece cuando de verdad no hay señal.
  Future<void> _abrirCorreccion(ParadaRuta fila) async {
    final proveedorId = fila.proveedor.id;
    var recojo = fila.recojo!;
    final recojosApi = context.read<RecojosApi>();

    if (recojo.id == -1) {
      if (!await _sync.hayConexionReal()) {
        if (!mounted) return;
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Este recojo todavía no se sincronizó. Espera a que tengas señal antes de corregirlo.',
            ),
          ),
        );
        return;
      }
      // Hay conexión real: se sincroniza este recojo ahora. sincronizar() solo confirma el envío --
      // el id real que asigna el servidor recién llega con la siguiente carga de la ruta.
      _sincronizacionIniciadaPorUi = true;
      await _sync.sincronizar();
      await _cargar();
      _sincronizacionIniciadaPorUi = false;
      if (!mounted) return;
      final actualizada = _filas
          .where((f) => f.proveedor.id == proveedorId)
          .firstOrNull;
      if (actualizada?.recojo == null || actualizada!.recojo!.id == -1) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'No se pudo sincronizar este recojo todavía. Vuelve a intentarlo en un momento.',
            ),
          ),
        );
        return;
      }
      recojo = actualizada.recojo!;
    }

    final resultado = await showModalBottomSheet<RecojoCorreccionResultado>(
      context: context,
      isScrollControlled: true,
      builder: (_) => RecojoCorreccionSheet(recojo: recojo),
    );
    if (resultado == null) return;

    try {
      final corregido = await recojosApi.corregir(
        recojo.id,
        RecojoCorreccionRequest(
          litros: resultado.litros,
          motivo: resultado.motivo,
        ),
      );
      if (!mounted) return;
      final filaVigente = _filas
          .where((f) => f.proveedor.id == proveedorId)
          .firstOrNull;
      if (filaVigente != null) setState(() => filaVigente.recojo = corregido);
      _avisar('Corrección guardada. El recojo queda observado para revisión.', icono: Icons.edit_note);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text('No se pudo corregir: ${mensajeDeError(e)}')),
      );
    }
  }

  /// Abre "Pintar ruta del día". Al volver se recarga: el trazado nuevo cambia lo que dibuja el mapa
  /// de esta pantalla.
  Future<void> _abrirPintarRuta() async {
    final cambio = await Navigator.of(
      context,
    ).push<bool>(MaterialPageRoute(builder: (_) => const PintarRutaScreen()));
    if (cambio == true) await _cargar();
  }

  Future<void> _alternarGps() async {
    // Se resuelven antes del primer await: leer el context después de un await es lo que avisa
    // `use_build_context_synchronously`, y aquí hay varios (permiso, servicio de ubicación).
    final ubicacionesApi = context.read<UbicacionesApi>();
    final sync = context.read<SyncService>();
    if (_compartiendoGps) {
      // Detener corta lo que ve la planta: se confirma para que no pase por un toque accidental.
      final detener = await showDialog<bool>(
        context: context,
        builder: (ctx) => AlertDialog(
          icon: const Icon(Icons.gps_off_outlined),
          title: const Text('¿Detener el recorrido?'),
          content: const Text(
            'La planta dejará de ver tu ubicación. Lo ya registrado se conserva y puedes volver a '
            'iniciarlo cuando quieras.',
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Seguir en ruta')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Detener')),
          ],
        ),
      );
      if (detener != true || !mounted) return;
      _latidoGps?.cancel();
      await _gpsSub?.cancel();
      setState(() => _compartiendoGps = false);
      return;
    }
    final permiso = await Geolocator.checkPermission();
    var permisoFinal = permiso;
    if (permiso == LocationPermission.denied) {
      permisoFinal = await Geolocator.requestPermission();
    }
    if (permisoFinal == LocationPermission.denied ||
        permisoFinal == LocationPermission.deniedForever) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text(
              'Se necesita permiso de ubicación para compartir el recorrido.',
            ),
          ),
        );
      }
      return;
    }
    if (!await Geolocator.isLocationServiceEnabled()) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Activa el GPS del dispositivo.')),
        );
      }
      return;
    }

    // Geocerca: el recorrido solo empieza dentro de la zona de la ruta. Fuera de ella no se puede
    // acopiar leche, así que es mejor no arrancar y avisar en vez de dejarlo trabajar en vano.
    final posicionInicial = await _posicionActual();
    if (posicionInicial != null &&
        !_dentroDeLaZona(posicionInicial.latitude, posicionInicial.longitude)) {
      if (mounted) {
        await showDialog<void>(
          context: context,
          builder: (_) => AlertDialog(
            title: const Text('Fuera de tu zona'),
            content: Text(
              'Estás fuera de la zona ${_asignacionHoy?.zona ?? ''}. Acércate a tu zona para '
              'iniciar el recorrido: solo podrás registrar leche dentro de ella.',
            ),
            actions: [
              TextButton(
                onPressed: () => Navigator.of(context).pop(),
                child: const Text('Entendido'),
              ),
            ],
          ),
        );
      }
      return;
    }

    _gpsSub = Geolocator.getPositionStream(locationSettings: _ajustesGps()).listen((
      posicion,
    ) async {
      // Se guarda localmente para dibujar el recorrido en el mapa aunque el envío al backend falle:
      // el mapa muestra lo que el dispositivo realmente registró hoy.
      setState(() => _puntosGps.add(posicion));

      // La hora de captura viaja con el punto. Si este se queda en la cola hasta que vuelva la
      // cobertura, el recorrido se dibuja con la hora en que se pasó por ahí, no con la del envío.
      final punto = UbicacionesApi.punto(
        latitud: posicion.latitude,
        longitud: posicion.longitude,
        capturadoEn: posicion.timestamp,
        precisionMetros: posicion.accuracy,
        velocidadMs: posicion.speed,
      );
      try {
        await ubicacionesApi.reportar(punto);
        _ultimoEnvioGps = DateTime.now();
      } catch (_) {
        // Sin señal: a la cola (RF-17/RF-18, RN-18). Antes el punto se perdía aquí y el recorrido
        // aparecía cortado en la web justo en los tramos sin cobertura, que son los que interesan.
        await sync.encolarUbicacion(
          '${posicion.timestamp.toUtc().toIso8601String()}-${posicion.latitude}-${posicion.longitude}',
          punto,
        );
      }
    });

    // Latido: el filtro de 20 m no manda nada mientras el acopiador está parado en un proveedor, y
    // una vuelta de acopio dura horas. La web oculta a quien lleva unos minutos sin reportar (ver
    // VENTANA_GPS_VIVO_MS en rutas.component.ts), así que se reenvía la última posición conocida con
    // la hora actual para que siga figurando como conectado. No se encola si falla: un latido
    // atrasado no dice nada, y los puntos reales ya viajan por la cola.
    _latidoGps = Timer.periodic(const Duration(minutes: 2), (_) async {
      if (_puntosGps.isEmpty) return;
      final ultimoEnvio = _ultimoEnvioGps;
      if (ultimoEnvio != null &&
          DateTime.now().difference(ultimoEnvio) < const Duration(minutes: 2)) {
        return;
      }
      final ultima = _puntosGps.last;
      try {
        await ubicacionesApi.reportar(
          UbicacionesApi.punto(
            latitud: ultima.latitude,
            longitud: ultima.longitude,
            capturadoEn: DateTime.now(),
            precisionMetros: ultima.accuracy,
            velocidadMs: 0,
          ),
        );
        _ultimoEnvioGps = DateTime.now();
      } catch (_) {
        // Sin señal: la web lo mostrará como desconectado hasta que vuelva la cobertura.
      }
    });
    setState(() => _compartiendoGps = true);
    _avisar('Recorrido iniciado: la planta ya ve tu ubicación.', icono: Icons.gps_fixed);
  }

  /// RN-19: se reduce la frecuencia cuando no hay movimiento usando un filtro de distancia (un punto
  /// cada [distanciaEntrePuntosGpsMetros], 10 m por pedido del cliente) en vez de un intervalo fijo,
  /// así no se gasta batería/datos parado en un punto.
  ///
  /// Una vuelta de acopio dura 2-3 horas y el acopiador bloquea el celular entre proveedor y
  /// proveedor. Sin servicio en primer plano Android corta el GPS (y el latido) al bloquear la
  /// pantalla, y la web lo mostraría desconectado aunque siga en ruta. La notificación fija es lo que
  /// Android exige para seguir leyendo la ubicación en segundo plano.
  LocationSettings _ajustesGps() {
    const precision = LocationAccuracy.high;
    const distancia = distanciaEntrePuntosGpsMetros;
    if (kIsWeb) {
      return const LocationSettings(
        accuracy: precision,
        distanceFilter: distancia,
      );
    }
    switch (defaultTargetPlatform) {
      case TargetPlatform.android:
        return AndroidSettings(
          accuracy: precision,
          distanceFilter: distancia,
          foregroundNotificationConfig: const ForegroundNotificationConfig(
            notificationTitle: 'Huata Lácteos · Ruta de acopio',
            notificationText:
                'Compartiendo tu ubicación con la planta mientras dure la ruta.',
            notificationChannelName: 'Ubicación en ruta',
            setOngoing: true,
            enableWakeLock: true,
          ),
        );
      case TargetPlatform.iOS:
        return AppleSettings(
          accuracy: precision,
          distanceFilter: distancia,
          activityType: ActivityType.otherNavigation,
          pauseLocationUpdatesAutomatically: false,
          allowBackgroundLocationUpdates: true,
          showBackgroundLocationIndicator: true,
        );
      default:
        return const LocationSettings(
          accuracy: precision,
          distanceFilter: distancia,
        );
    }
  }

  void _avisar(String texto, {IconData? icono}) {
    if (!mounted) return;
    ScaffoldMessenger.of(context)
      ..hideCurrentSnackBar()
      ..showSnackBar(
        SnackBar(
          content: Row(
            children: [
              if (icono != null) ...[Icon(icono, color: Colors.white, size: 20), const SizedBox(width: 10)],
              Expanded(child: Text(texto)),
            ],
          ),
        ),
      );
  }

  Widget _cabecera(SyncService sync, {bool compacta = false}) => CabeceraJornada(
    asignacion: _asignacionHoy!,
    paradas: _filas,
    totalLitros: _totalAcumulado,
    sync: sync,
    compartiendoGps: _compartiendoGps,
    onAlternarGps: _alternarGps,
    compacta: compacta,
  );

  @override
  Widget build(BuildContext context) {
    final sync = context.watch<SyncService>();
    return HuataScaffold(
      titulo: 'Mi ruta',
      anchoCompleto: true,
      actions: [
        PopupMenuButton<String>(
          tooltip: 'Más opciones',
          icon: const Icon(Icons.more_vert),
          enabled: !_cargando,
          onSelected: (opcion) {
            if (opcion == 'actualizar') _cargar();
            if (opcion == 'pintar') _abrirPintarRuta();
          },
          itemBuilder: (_) => [
            const PopupMenuItem(
              value: 'actualizar',
              child: ListTile(
                contentPadding: EdgeInsets.zero,
                leading: Icon(Icons.refresh),
                title: Text('Actualizar ruta'),
                subtitle: Text('Por si administración hizo cambios'),
              ),
            ),
            if (_asignacionHoy != null)
              const PopupMenuItem(
                value: 'pintar',
                child: ListTile(
                  contentPadding: EdgeInsets.zero,
                  leading: Icon(Icons.edit_road),
                  title: Text('Pintar recorrido del día'),
                  subtitle: Text('Dibujar el camino que hiciste'),
                ),
              ),
          ],
        ),
      ],
      body: _cuerpo(sync),
    );
  }

  Widget _cuerpo(SyncService sync) {
    if (_cargando) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: SingleChildScrollView(
          child: EstadoVacio(
            icono: Icons.cloud_off_outlined,
            titulo: 'No se pudo cargar tu ruta',
            mensaje: _error!,
            accion: FilledButton.icon(
              onPressed: _cargar,
              icon: const Icon(Icons.refresh),
              label: const Text('Reintentar'),
            ),
          ),
        ),
      );
    }
    if (_asignacionHoy == null) {
      return RefreshIndicator(
        onRefresh: _cargar,
        child: VistaResumen(
          yo: _yo!,
          reporte: _reporte,
          periodo: _periodoReporte,
          onPeriodo: _cambiarPeriodo,
          aviso: const SeccionCard(
            child: EstadoVacio(
              icono: Icons.event_busy_outlined,
              titulo: 'Sin ruta asignada para hoy',
              mensaje: 'Cuando administración te asigne una ruta aparecerá aquí. Desliza hacia abajo para actualizar.',
            ),
          ),
        ),
      );
    }

    final contenido = switch (_vista) {
      _Vista.mapa => Padding(
        padding: const EdgeInsets.fromLTRB(16, 4, 16, 12),
        child: Column(
          children: [
            _cabecera(sync, compacta: true),
            const SizedBox(height: 10),
            Expanded(
              child: VistaMapa(
                zona: _asignacionHoy!.zona,
                puntosRuta: _puntosRuta,
                poligonos: _poligonos,
                puntosEstablecidos: _puntosEstablecidos,
                sincronizandoPuntos: _puntosSocket.sincronizando,
                puntosGps: _puntosGps,
                paradas: _filas,
                siguiente: _siguientePendiente,
                onRegistrar: _abrirFormulario,
              ),
            ),
          ],
        ),
      ),
      _Vista.paradas => RefreshIndicator(
        onRefresh: _cargar,
        child: VistaParadas(cabecera: _cabecera(sync), paradas: _filas, onTocar: _abrirFormulario),
      ),
      _Vista.resumen => RefreshIndicator(
        onRefresh: _cargar,
        child: VistaResumen(
          yo: _yo!,
          reporte: _reporte,
          periodo: _periodoReporte,
          onPeriodo: _cambiarPeriodo,
          cabecera: _cabecera(sync),
        ),
      ),
    };

    final pendientes = _filas.where((f) => f.estado == EstadoVisita.pendiente).length;
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
          child: SizedBox(
            width: double.infinity,
            child: SegmentedButton<_Vista>(
              showSelectedIcon: false,
              segments: [
                ButtonSegment(
                  value: _Vista.paradas,
                  label: Text(pendientes > 0 ? 'Paradas · $pendientes' : 'Paradas'),
                  icon: const Icon(Icons.format_list_bulleted, size: 18),
                ),
                const ButtonSegment(value: _Vista.mapa, label: Text('Mapa'), icon: Icon(Icons.map_outlined, size: 18)),
                const ButtonSegment(
                  value: _Vista.resumen,
                  label: Text('Resumen'),
                  icon: Icon(Icons.insights_outlined, size: 18),
                ),
              ],
              selected: {_vista},
              onSelectionChanged: (v) => setState(() => _vista = v.first),
            ),
          ),
        ),
        if (_fueraDeLaZona)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: EstadoAviso(
              esError: true,
              mensaje: 'Estás fuera de la zona ${_asignacionHoy!.zona ?? ''}. '
                  'No podrás registrar leche hasta volver a entrar.',
            ),
          ),
        Expanded(child: contenido),
      ],
    );
  }
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
}
