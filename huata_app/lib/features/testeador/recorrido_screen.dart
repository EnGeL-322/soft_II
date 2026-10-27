import 'dart:async';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart' as ll;
import 'package:provider/provider.dart';

import '../../core/api/productores_api.dart';
import '../../core/api/ruteo_api.dart';
import '../../core/api/rutas_api.dart';
import '../../core/local/local_store.dart';
import '../../core/mapa/recorrido_mapa.dart';
import '../../core/models/productor.dart';
import '../../core/models/ruta.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import 'voucher_screen.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Tipo bajo el que se guarda el historial de recorridos del testeador en la cola local
/// ([LocalStore]). Se reutiliza esa cola (pensada para escrituras pendientes de sincronizar) como
/// bitácora local a propósito: cada fila queda con `clientUuid` = fecha del día, así que guardar de
/// nuevo el mismo día actualiza esa fila en vez de duplicarla (mismo `ConflictAlgorithm.replace` que
/// ya usa `encolar`). No se llama `eliminar`: estas filas no esperan una confirmación del servidor,
/// son historial local puro (punto 3 de PROXIMA_FASE_PROMPT.md — el GPS del testeador hoy solo vive
/// en el dispositivo, así que el historial tampoco puede pretender venir del backend).
const _tipoHistorialRecorrido = 'historial_recorrido_testeador';

/// App del testeador / rol CALIDAD (10-11, RF-84-87): ruta planificada o recorrido libre —con
/// opción de orden aleatorio—, mapa con la ruta planificada y el recorrido real del día, historial
/// local de recorridos, y captura de voucher por proveedor.
///
/// GPS: se registra localmente (lista en memoria, visible en el mapa) pero NO se envía al backend
/// todavía. `POST /api/v1/ubicaciones` solo acepta acopiadores (ver
/// `core/api/ubicaciones_api.dart`): la tabla `ubicaciones_gps` tiene una FK obligatoria a
/// `acopiador_id`, no a un actor genérico. Extender eso es un cambio de modelo de datos del backend
/// (ítem 5 de PROXIMA_FASE_PROMPT.md, con decisión de diseño pendiente de tomar aparte) — queda
/// como pendiente explícito, no resuelto en silencio.
class RecorridoTesteadorScreen extends StatefulWidget {
  const RecorridoTesteadorScreen({super.key});

  @override
  State<RecorridoTesteadorScreen> createState() => _RecorridoTesteadorScreenState();
}

class _RecorridoTesteadorScreenState extends State<RecorridoTesteadorScreen> with EnVivo<RecorridoTesteadorScreen> {
  bool _cargandoRutas = true;
  List<Ruta> _rutas = [];
  Ruta? _rutaSeleccionada;
  bool _modoLibre = false;

  bool _cargandoProveedores = false;
  List<Productor> _proveedores = [];
  final Set<int> _testeados = {};
  String? _error;

  bool _cargandoPuntosPlan = false;
  List<RutaPunto> _puntosPlanificados = [];

  bool _compartiendoGps = false;
  StreamSubscription<Position>? _gpsSub;
  final List<Position> _puntosRecorrido = [];

  List<PendienteSync> _historial = [];
  final _random = Random();

  List<ZonaPoligono> _poligonos = [];

  @override
  void initState() {
    super.initState();
    // En vivo: rutas nuevas o cambiadas desde la web aparecen sin salir de la pantalla.
    recargarEnVivo({'rutas'}, () => _cargarRutas(silencioso: true));
    _cargarRutas();
    _cargarHistorial();
    _cargarPoligonos();
  }

  Future<void> _cargarPoligonos() async {
    try {
      final poligonos = await context.read<RutasApi>().poligonosDeZona();
      if (mounted) setState(() => _poligonos = poligonos);
    } catch (_) {
      // El polígono es un dato decorativo del mapa (territorio de la zona); si falla, el mapa sigue
      // funcionando solo con los puntos y el recorrido GPS.
    }
  }

  @override
  void dispose() {
    _gpsSub?.cancel();
    super.dispose();
  }

  Future<void> _cargarHistorial() async {
    final store = context.read<LocalStore>();
    await store.init();
    final historial = await store.pendientes(_tipoHistorialRecorrido);
    if (mounted) {
      setState(() => _historial = historial..sort((a, b) => b.creadoEn.compareTo(a.creadoEn)));
    }
  }

  Future<void> _guardarEnHistorial() async {
    final store = context.read<LocalStore>();
    final hoy = DateTime.now();
    final fechaIso = hoy.toIsoDate();
    await store.encolar(_tipoHistorialRecorrido, fechaIso, {
      'fecha': fechaIso,
      'modo': _modoLibre ? 'Libre' : (_rutaSeleccionada?.nombre ?? 'Planificada'),
      'puntos': _puntosRecorrido.length,
      'testeados': _testeados.length,
    });
    await _cargarHistorial();
  }

  Future<void> _cargarRutas({bool silencioso = false}) async {
    if (!silencioso) setState(() => _cargandoRutas = true);
    try {
      final rutas = await context.read<RutasApi>().listar();
      if (!mounted) return;
      setState(() {
        _rutas = rutas;
        _cargandoRutas = false;
      });
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargandoRutas = false;
      });
    }
  }

  Future<void> _elegirRuta(Ruta ruta) async {
    setState(() {
      _rutaSeleccionada = ruta;
      _modoLibre = false;
      _cargandoProveedores = true;
      _cargandoPuntosPlan = true;
      _testeados.clear();
      _error = null;
    });
    try {
      final rutasApi = context.read<RutasApi>();
      final proveedores = await rutasApi.proveedoresDeRuta(ruta.id);
      final puntos = await rutasApi.puntosDeRuta(ruta.id);
      setState(() {
        _proveedores = proveedores
            .map(
              (p) => Productor(
                id: p.id,
                codigo: p.codigo,
                nombres: p.nombres,
                apellidos: p.apellidos,
                estado: 'ACTIVO',
                excelencia: false,
                activo: true,
              ),
            )
            .toList();
        _puntosPlanificados = puntos..sort((a, b) => a.secuencia.compareTo(b.secuencia));
        _cargandoProveedores = false;
        _cargandoPuntosPlan = false;
      });
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _cargandoProveedores = false;
        _cargandoPuntosPlan = false;
      });
    }
  }

  Future<void> _elegirRecorridoLibre() async {
    setState(() {
      _rutaSeleccionada = null;
      _modoLibre = true;
      _cargandoProveedores = true;
      _puntosPlanificados = [];
      _testeados.clear();
      _error = null;
    });
    try {
      final proveedores = await context.read<ProductoresApi>().listar(size: 100);
      setState(() {
        _proveedores = proveedores;
        _cargandoProveedores = false;
      });
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _cargandoProveedores = false;
      });
    }
  }

  /// "Recorrido aleatorio": generar un orden aleatorio de los proveedores para no seguir el orden
  /// fijo del acopiador (detalle nuevo de la sección 36 de CLAUDE.md / punto 2 del prompt). Es
  /// puramente de presentación en el cliente: no cambia nada en el backend ni en la ruta planificada.
  void _mezclarOrden() {
    setState(() => _proveedores.shuffle(_random));
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(const SnackBar(content: Text('Orden aleatorio generado para esta sesión.')));
  }

  Future<void> _alternarGps() async {
    if (_compartiendoGps) {
      await _gpsSub?.cancel();
      setState(() => _compartiendoGps = false);
      if (_puntosRecorrido.isNotEmpty) await _guardarEnHistorial();
      return;
    }
    var permiso = await Geolocator.checkPermission();
    if (permiso == LocationPermission.denied) permiso = await Geolocator.requestPermission();
    if (permiso == LocationPermission.denied || permiso == LocationPermission.deniedForever) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Se necesita permiso de ubicación.')));
      }
      return;
    }
    _gpsSub =
        Geolocator.getPositionStream(
          locationSettings: const LocationSettings(
            accuracy: LocationAccuracy.high,
            distanceFilter: distanciaEntrePuntosGpsMetros,
          ),
        ).listen((posicion) {
          setState(() => _puntosRecorrido.add(posicion));
        });
    setState(() => _compartiendoGps = true);
  }

  /// Entrega directa en planta: el proveedor trae la leche y el testeador la recibe con su análisis.
  Future<void> _entregaDirecta() async {
    final api = context.read<ProductoresApi>();
    List<Productor> todos;
    try {
      todos = await api.listar(size: 200);
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
      return;
    }
    if (!mounted) return;
    final elegido = await showModalBottomSheet<Productor>(
      context: context,
      isScrollControlled: true,
      builder: (ctx) => _SelectorProveedor(proveedores: todos),
    );
    if (elegido == null || !mounted) return;
    await Navigator.of(
      context,
    ).push(MaterialPageRoute(builder: (_) => VoucherScreen(proveedor: elegido, entregaDirecta: true)));
  }

  void _abrirVoucher(Productor proveedor) {
    Navigator.of(context)
        .push(MaterialPageRoute(builder: (_) => VoucherScreen(proveedor: proveedor)))
        .then((_) => setState(() => _testeados.add(proveedor.id)));
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Testeo de leche',
      anchoCompleto: true,
      body: _cargandoRutas
          ? const Center(child: CircularProgressIndicator())
          : RefreshIndicator(
              onRefresh: () async {
                await _cargarRutas();
                await _cargarHistorial();
              },
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  SeccionCard(
                    titulo: 'Entrega directa en planta',
                    subtitulo: 'Un proveedor trajo su leche: registra los litros y su análisis con el voucher.',
                    child: SizedBox(
                      width: double.infinity,
                      child: OutlinedButton.icon(
                        onPressed: _entregaDirecta,
                        icon: const Icon(Icons.local_shipping_outlined),
                        label: const Text('Registrar entrega en planta'),
                      ),
                    ),
                  ),
                  const SizedBox(height: 12),
                  SeccionCard(
                    titulo: 'Recorrido de hoy',
                    subtitulo: 'Ruta planificada, recorrido libre, o un orden aleatorio entre proveedores (RF-86).',
                    child: Column(
                      children: [
                        Wrap(
                          spacing: 8,
                          runSpacing: 8,
                          children: [
                            for (final ruta in _rutas)
                              ChoiceChip(
                                label: Text(ruta.nombre),
                                selected: _rutaSeleccionada?.id == ruta.id,
                                onSelected: (_) => _elegirRuta(ruta),
                              ),
                            ChoiceChip(
                              label: const Text('Recorrido libre'),
                              selected: _modoLibre,
                              onSelected: (_) => _elegirRecorridoLibre(),
                            ),
                          ],
                        ),
                        const SizedBox(height: 12),
                        Row(
                          children: [
                            Expanded(
                              child: OutlinedButton.icon(
                                onPressed: _alternarGps,
                                icon: Icon(_compartiendoGps ? Icons.gps_fixed : Icons.gps_not_fixed),
                                label: Text(
                                  _compartiendoGps
                                      ? 'GPS activo (${_puntosRecorrido.length} pts)'
                                      : 'Iniciar recorrido GPS',
                                ),
                              ),
                            ),
                            const SizedBox(width: 8),
                            IconButton(
                              tooltip: 'Recorrido aleatorio',
                              onPressed: _proveedores.isEmpty ? null : _mezclarOrden,
                              icon: const Icon(Icons.shuffle),
                            ),
                          ],
                        ),
                      ],
                    ),
                  ),
                  const SizedBox(height: 16),
                  if (_error != null) EstadoAviso(mensaje: _error!, esError: true),
                  if (_puntosPlanificados.isNotEmpty || _puntosRecorrido.isNotEmpty || _compartiendoGps) ...[
                    SeccionCard(
                      titulo: 'Mapa',
                      subtitulo: 'Azul: ruta planificada · Verde: recorrido real de hoy',
                      padding: EdgeInsets.zero,
                      child: SizedBox(
                        height: 260,
                        child: _cargandoPuntosPlan
                            ? const Center(child: CircularProgressIndicator())
                            : _MapaRecorrido(
                                planificados: _puntosPlanificados,
                                recorridoReal: _puntosRecorrido,
                                poligonos: _poligonos,
                              ),
                      ),
                    ),
                    const SizedBox(height: 16),
                  ],
                  if (_cargandoProveedores)
                    const Center(
                      child: Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator()),
                    )
                  else if (_proveedores.isNotEmpty) ...[
                    const Text('Proveedores a testear', style: TextStyle(fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    ..._proveedores.map(
                      (p) => Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          title: Text(p.nombreCompleto),
                          subtitle: Text(p.codigo),
                          leading: _testeados.contains(p.id)
                              ? const Icon(Icons.check_circle, color: HuataColors.green)
                              : const Icon(Icons.radio_button_unchecked, color: HuataColors.muted),
                          trailing: const Icon(Icons.camera_alt_outlined),
                          onTap: () => _abrirVoucher(p),
                        ),
                      ),
                    ),
                  ],
                  if (_historial.isNotEmpty) ...[
                    const SizedBox(height: 20),
                    const Text(
                      'Historial de mis recorridos (este dispositivo)',
                      style: TextStyle(fontWeight: FontWeight.bold),
                    ),
                    const SizedBox(height: 8),
                    ..._historial.map(
                      (h) => Card(
                        margin: const EdgeInsets.only(bottom: 8),
                        child: ListTile(
                          title: Text('${h.payload['fecha']} · ${h.payload['modo']}'),
                          subtitle: Text('${h.payload['puntos']} puntos GPS · ${h.payload['testeados']} testeados'),
                        ),
                      ),
                    ),
                  ],
                ],
              ),
            ),
    );
  }
}

/// Dibuja en un mapa (OpenStreetMap vía flutter_map) los polígonos de zona, la ruta planificada (por
/// calles reales cuando Valhalla responde, línea recta como respaldo) y el recorrido GPS real del
/// día, tal como pide RF-87/punto 2 del prompt. Sin puntos, muestra un mapa centrado en la zona de
/// referencia del proyecto (Puno) en vez de fallar.
class _MapaRecorrido extends StatefulWidget {
  const _MapaRecorrido({required this.planificados, required this.recorridoReal, this.poligonos = const []});

  final List<RutaPunto> planificados;
  final List<Position> recorridoReal;
  final List<ZonaPoligono> poligonos;

  @override
  State<_MapaRecorrido> createState() => _MapaRecorridoState();
}

class _MapaRecorridoState extends State<_MapaRecorrido> {
  static const _centroPorDefecto = ll.LatLng(-15.48, -70.10);

  List<ll.LatLng> _rutaReal = [];
  String _claveVigente = '';

  @override
  void initState() {
    super.initState();
    _calcularRutaReal();
  }

  @override
  void didUpdateWidget(covariant _MapaRecorrido oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.planificados != widget.planificados) _calcularRutaReal();
  }

  Future<void> _calcularRutaReal() async {
    final puntos = widget.planificados.map((p) => ll.LatLng(p.latitud, p.longitud)).toList();
    if (puntos.length < 2) {
      setState(() {
        _rutaReal = [];
        _claveVigente = '';
      });
      return;
    }
    final clave = puntos.map((p) => '${p.latitude},${p.longitude}').join(';');
    _claveVigente = clave;
    final real = await context.read<RuteoApi>().calcularRuta(puntos);
    // Si mientras esperábamos la respuesta la ruta cambió (otra ruta seleccionada), esta ya no aplica.
    if (!mounted || clave != _claveVigente || real.length < 2) return;
    setState(() => _rutaReal = real);
  }

  @override
  Widget build(BuildContext context) {
    final puntosPlan = widget.planificados.map((p) => ll.LatLng(p.latitud, p.longitud)).toList();
    final puntosReal = widget.recorridoReal.map((p) => ll.LatLng(p.latitude, p.longitude)).toList();
    final centro = puntosReal.isNotEmpty
        ? puntosReal.last
        : (puntosPlan.isNotEmpty ? puntosPlan.first : _centroPorDefecto);
    final lineaPlan = _rutaReal.length > 1 ? _rutaReal : puntosPlan;
    final modo = context.watch<PreferenciasMapa>().modo;

    return ClipRRect(
      borderRadius: const BorderRadius.vertical(bottom: Radius.circular(12)),
      child: FlutterMap(
        options: MapOptions(initialCenter: centro, initialZoom: 14),
        children: [
          TileLayer(
            urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
            userAgentPackageName: 'org.ramiro.huata_app',
          ),
          if (widget.poligonos.isNotEmpty)
            PolygonLayer(
              polygons: widget.poligonos
                  .where((z) => z.puntos.length > 2)
                  .map(
                    (z) => Polygon(
                      points: z.puntos.map((p) => ll.LatLng(p.latitud, p.longitud)).toList(),
                      color: _colorDeZona(z.color).withValues(alpha: 0.12),
                      borderColor: _colorDeZona(z.color),
                      borderStrokeWidth: 2,
                    ),
                  )
                  .toList(),
            ),
          if (lineaPlan.length > 1)
            PolylineLayer(
              polylines: [Polyline(points: lineaPlan, strokeWidth: 4, color: HuataColors.blue)],
            ),
          ...capasRecorrido(puntosReal, modo, color: HuataColors.green),
          MarkerLayer(
            markers: [
              for (final p in puntosPlan)
                Marker(
                  point: p,
                  width: 14,
                  height: 14,
                  child: const _PuntoMapa(color: HuataColors.blue),
                ),
            ],
          ),
          Align(
            alignment: Alignment.topCenter,
            child: Card(
              margin: const EdgeInsets.all(8),
              child: SelectorModoRecorrido(modo: modo, onCambio: (m) => context.read<PreferenciasMapa>().cambiar(m)),
            ),
          ),
        ],
      ),
    );
  }
}

Color _colorDeZona(String? hex) {
  if (hex == null || hex.isEmpty) return HuataColors.green;
  final limpio = hex.replaceFirst('#', '');
  final valor = int.tryParse(limpio.length == 6 ? 'FF$limpio' : limpio, radix: 16);
  return valor != null ? Color(valor) : HuataColors.green;
}

class _PuntoMapa extends StatelessWidget {
  const _PuntoMapa({required this.color});
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Container(
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 2),
      ),
    );
  }
}

/// Lista de proveedores con búsqueda, para elegir a quién se le recibe una entrega directa.
class _SelectorProveedor extends StatefulWidget {
  const _SelectorProveedor({required this.proveedores});
  final List<Productor> proveedores;

  @override
  State<_SelectorProveedor> createState() => _SelectorProveedorState();
}

class _SelectorProveedorState extends State<_SelectorProveedor> {
  String _filtro = '';

  @override
  Widget build(BuildContext context) {
    final f = _filtro.toLowerCase();
    final lista = widget.proveedores
        .where((p) => f.isEmpty || p.nombreCompleto.toLowerCase().contains(f) || p.codigo.toLowerCase().contains(f))
        .toList();
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(bottom: MediaQuery.of(context).viewInsets.bottom),
        child: SizedBox(
          height: MediaQuery.of(context).size.height * 0.75,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(16),
                child: TextField(
                  autofocus: true,
                  decoration: const InputDecoration(labelText: 'Buscar proveedor', prefixIcon: Icon(Icons.search)),
                  onChanged: (v) => setState(() => _filtro = v.trim()),
                ),
              ),
              Expanded(
                child: ListView.builder(
                  itemCount: lista.length,
                  itemBuilder: (_, i) => ListTile(
                    title: Text(lista[i].nombreCompleto),
                    subtitle: Text(lista[i].codigo),
                    onTap: () => Navigator.of(context).pop(lista[i]),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
