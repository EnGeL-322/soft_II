import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:provider/provider.dart';

import '../../../core/api/api_config.dart';
import '../../../core/api/personas_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/auth/token_store.dart';
import '../../../core/mensajeria/mensajeria_en_vivo.dart' show SocketMensajeria, abrirSocketReal, urlCanal;
import '../../../core/models/ruta.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

const _estadosPlan = {'SIN_ASIGNAR': 'Sin asignar', 'ASIGNADA': 'Asignada', 'CON_REGISTROS': 'En curso'};

/// Vista "Rutas y mapa" de la web (rutas.read), para supervisar desde el celular: el plan del día por
/// ruta (acopiador, litros, último registro) y la última posición de cada acopiador en el mapa, en
/// vivo por el canal de ubicaciones (el mismo que usa la web), y la vivienda de cada proveedor de las
/// rutas del día (real o simulada para la demostración). Dibujar y editar rutas sigue en la web.
///
/// El GPS es una referencia, no prueba absoluta (CLAUDE.md §6).
class RutasMapaScreen extends StatefulWidget {
  const RutasMapaScreen({super.key});

  @override
  State<RutasMapaScreen> createState() => _RutasMapaScreenState();
}

class _RutasMapaScreenState extends State<RutasMapaScreen> with EnVivo<RutasMapaScreen> {
  DateTime _dia = DateTime.now();
  bool _cargando = true;
  String? _error;
  List<PlanRutaDia> _plan = [];
  final Map<int, UbicacionAcopiador> _ubicaciones = {};
  /// Viviendas de los proveedores de las rutas del día, con el nombre de su ruta.
  List<({ProveedorDeRuta p, String ruta})> _viviendas = [];
  bool _encuadrado = false;
  final _mapa = MapController();

  SocketMensajeria? _socket;
  StreamSubscription<dynamic>? _entrada;
  Timer? _reintento;
  bool _vivo = false;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'rutas', 'acopio', 'acopiadores'}, () => _cargar(silencioso: true));
    _cargar();
    _conectarGps();
  }

  @override
  void dispose() {
    _reintento?.cancel();
    _entrada?.cancel();
    _socket?.cerrar();
    super.dispose();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final api = context.read<PersonasApi>();
    final dia = _dia;
    try {
      final plan = await api.planOperativo(dia);
      List<UbicacionAcopiador> ultimas = const [];
      try {
        ultimas = await api.ultimasUbicaciones();
      } catch (_) {}
      final viviendas = <({ProveedorDeRuta p, String ruta})>[];
      await Future.wait(plan.map((r) async {
        try {
          for (final p in await api.proveedoresDeRuta(r.rutaId)) {
            if (p.latitud != null && p.longitud != null) viviendas.add((p: p, ruta: r.rutaNombre));
          }
        } catch (_) {}
      }));
      if (!mounted || dia != _dia) return;
      setState(() {
        _viviendas = viviendas;
        _plan = plan..sort((a, b) => a.rutaNombre.compareTo(b.rutaNombre));
        for (final u in ultimas) {
          _ubicaciones[u.acopiadorId] = u;
        }
        _cargando = false;
        _error = null;
      });
      _encuadrar();
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  /// La primera vez que llegan puntos, el mapa se ajusta para que se vean todos (acopiadores y viviendas).
  void _encuadrar() {
    if (_encuadrado) return;
    final puntos = [
      for (final u in _ubicaciones.values) LatLng(u.latitud, u.longitud),
      for (final v in _viviendas) LatLng(v.p.latitud!, v.p.longitud!),
    ];
    if (puntos.isEmpty) return;
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      try {
        if (puntos.length == 1) {
          _mapa.move(puntos.first, 15);
        } else {
          _mapa.fitCamera(CameraFit.coordinates(coordinates: puntos, padding: const EdgeInsets.all(36), maxZoom: 16));
        }
        _encuadrado = true;
      } catch (_) {
        // El mapa aún no se dibujó: se reintenta en la próxima carga.
      }
    });
  }

  /// Canal `/api/ws/ubicaciones`: cada punto que reporta un acopiador mueve su marcador al momento.
  Future<void> _conectarGps() async {
    final token = await context.read<TokenStore>().read();
    if (token == null || !mounted) return;
    try {
      final socket = abrirSocketReal(urlCanal(ApiConfig.baseUrl, 'ubicaciones'));
      _socket = socket;
      _entrada = socket.entrada.listen(
        (datos) {
          if (datos is! String) return;
          try {
            final u = UbicacionAcopiador.fromJson(jsonDecode(datos) as Map<String, dynamic>);
            if (mounted) setState(() => _ubicaciones[u.acopiadorId] = u);
          } catch (_) {
            // mensaje fuera de contrato: se ignora
          }
        },
        onDone: _caido,
        onError: (Object _) => _caido(),
        cancelOnError: true,
      );
      await socket.listo;
      socket.enviar(jsonEncode({'type': 'AUTH', 'token': token}));
      if (mounted) setState(() => _vivo = true);
    } catch (_) {
      _caido();
    }
  }

  void _caido() {
    _entrada?.cancel();
    _socket = null;
    if (!mounted) return;
    setState(() => _vivo = false);
    _reintento?.cancel();
    _reintento = Timer(const Duration(seconds: 5), _conectarGps);
  }

  Future<void> _verProveedores(PlanRutaDia r) async {
    List<ProveedorDeRuta>? lista;
    String? error;
    try {
      lista = await context.read<PersonasApi>().proveedoresDeRuta(r.rutaId);
    } catch (e) {
      error = mensajeDeError(e);
    }
    if (!mounted) return;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      isScrollControlled: true,
      builder: (_) => DraggableScrollableSheet(
        expand: false,
        initialChildSize: 0.6,
        builder: (_, scroll) => ListView(controller: scroll, padding: const EdgeInsets.fromLTRB(16, 0, 16, 16), children: [
          Text(r.rutaNombre, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          if (error != null) Text(error),
          for (final p in [...?lista]..sort((a, b) => (a.ordenEnRuta ?? 999).compareTo(b.ordenEnRuta ?? 999)))
            ListTile(
              contentPadding: EdgeInsets.zero,
              leading: CircleAvatar(radius: 14, child: Text('${p.ordenEnRuta ?? '·'}', style: const TextStyle(fontSize: 12))),
              title: Text('${p.nombres} ${p.apellidos}'),
              subtitle: Text(p.codigo),
              onTap: p.latitud != null && p.longitud != null
                  ? () {
                      Navigator.pop(context);
                      _mapa.move(LatLng(p.latitud!, p.longitud!), 16);
                    }
                  : null,
            ),
        ]),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final marcadores = _ubicaciones.values.toList();
    final centro = marcadores.isNotEmpty
        ? LatLng(marcadores.first.latitud, marcadores.first.longitud)
        : _viviendas.isNotEmpty
            ? LatLng(_viviendas.first.p.latitud!, _viviendas.first.p.longitud!)
            : const LatLng(-15.84, -70.02);
    final recogido = _plan.fold(0.0, (s, r) => s + r.litros);
    return HuataScaffold(
      titulo: 'Rutas y mapa',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay rutas planificadas este día.',
        cabecera: [
          SizedBox(
            height: 280,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(16),
              child: Stack(children: [
                FlutterMap(
                  mapController: _mapa,
                  options: MapOptions(initialCenter: centro, initialZoom: 12),
                  children: [
                    TileLayer(urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png', userAgentPackageName: 'org.ramiro.huata_app'),
                    MarkerLayer(markers: [
                      for (final v in _viviendas)
                        Marker(
                          point: LatLng(v.p.latitud!, v.p.longitud!),
                          width: 30,
                          height: 30,
                          child: Tooltip(
                            message: '${v.p.nombreCompleto} · ${v.p.codigo} · ${v.ruta}',
                            triggerMode: TooltipTriggerMode.tap,
                            child: Container(
                              decoration: BoxDecoration(
                                color: Colors.white,
                                shape: BoxShape.circle,
                                border: Border.all(color: HuataColors.blue, width: 2),
                                boxShadow: HuataMarca.sombra,
                              ),
                              child: const Icon(Icons.home, color: HuataColors.blue, size: 17),
                            ),
                          ),
                        ),
                      for (final u in marcadores)
                        Marker(
                          point: LatLng(u.latitud, u.longitud),
                          width: 140,
                          height: 54,
                          child: Tooltip(
                            message: '${u.acopiadorNombre} · ${fechaHoraCorta(u.fecha)}',
                            child: Column(children: [
                              Container(
                                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                                decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(6), boxShadow: HuataMarca.sombra),
                                child: Text(u.acopiadorNombre.split(' ').first, style: const TextStyle(fontSize: 11, fontWeight: FontWeight.w700)),
                              ),
                              const Icon(Icons.local_shipping, color: HuataColors.green, size: 28),
                            ]),
                          ),
                        ),
                    ]),
                  ],
                ),
                Positioned(
                  left: 8,
                  top: 8,
                  child: EtiquetaEstado(
                    texto: _vivo ? 'GPS en vivo' : 'Reconectando GPS…',
                    color: _vivo ? HuataColors.green : HuataColors.amber,
                    fondo: _vivo ? HuataColors.greenSoft : HuataColors.amberSoft,
                  ),
                ),
                if (_viviendas.isNotEmpty)
                  Positioned(
                    right: 8,
                    top: 8,
                    child: EtiquetaEstado(
                      texto: '${_viviendas.length} proveedores',
                      color: HuataColors.blue,
                      fondo: HuataColors.blueSoft,
                    ),
                  ),
              ]),
            ),
          ),
          const SizedBox(height: 12),
          SelectorDia(
            dia: _dia,
            alCambiar: (d) {
              setState(() => _dia = d);
              _cargar();
            },
          ),
          if (!_cargando && _plan.isNotEmpty) ...[
            Row(children: [
              Expanded(child: DatoClave(etiqueta: 'Rutas', valor: '${_plan.length}')),
              const SizedBox(width: 8),
              Expanded(child: DatoClave(etiqueta: 'Sin acopiador', valor: '${_plan.where((r) => r.estado == 'SIN_ASIGNAR').length}')),
              const SizedBox(width: 8),
              Expanded(child: DatoClave(etiqueta: 'Recogido', valor: litros(recogido))),
            ]),
            const SizedBox(height: 12),
          ],
        ],
        hijos: [
          for (final r in _plan)
            FilaTarjeta(
              icono: Icons.route_outlined,
              titulo: r.rutaNombre,
              subtitulo: [
                if (r.zona != null) r.zona!,
                r.acopiadorNombre ?? 'Sin acopiador',
                if (r.recepciones > 0) '${r.recepciones} entregas · ${litros(r.litros)}',
                if (r.ultimoRegistro != null) 'último ${fechaHoraCorta(r.ultimoRegistro)}',
              ].join(' · '),
              derecha: EtiquetaEstado(
                texto: _estadosPlan[r.estado] ?? r.estado,
                color: r.estado == 'SIN_ASIGNAR' ? HuataColors.amber : (r.estado == 'CON_REGISTROS' ? HuataColors.green : HuataColors.blue),
                fondo: r.estado == 'SIN_ASIGNAR' ? HuataColors.amberSoft : (r.estado == 'CON_REGISTROS' ? HuataColors.greenSoft : HuataColors.blueSoft),
              ),
              onTap: () {
                final u = r.acopiadorId == null ? null : _ubicaciones[r.acopiadorId];
                if (u != null) _mapa.move(LatLng(u.latitud, u.longitud), 15);
                _verProveedores(r);
              },
            ),
        ],
      ),
    );
  }
}

