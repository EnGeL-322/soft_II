import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart' as ll;
import 'package:provider/provider.dart';

import '../../core/api/rutas_api.dart';
import '../../core/api/trazados_api.dart';
import '../../core/models/ruta.dart';
import '../../core/models/trazado.dart';
import '../../core/sync/sync_service.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

/// "Pintar ruta del día" (V27): el acopiador dibuja en el mapa el recorrido que va a hacer hoy, sea
/// porque no va por la ruta designada o porque va por ella igualmente y quiere dejarlo dibujado.
///
/// Se guarda aunque no haya señal: si el envío falla, el recorrido va a la cola offline y el
/// `SyncService` lo reintenta solo al recuperar cobertura (RF-17/RF-18), con la fecha en que se
/// pintó — no la del envío.
///
/// Lo que se pinta aquí **no** cambia por sí solo el recorrido predeterminado del acopiador: eso lo
/// decide administración desde la web ("Asignar como ruta predeterminada").
class PintarRutaScreen extends StatefulWidget {
  const PintarRutaScreen({super.key});

  @override
  State<PintarRutaScreen> createState() => _PintarRutaScreenState();
}

class _PintarRutaScreenState extends State<PintarRutaScreen> {
  static const _centroPorDefecto = ll.LatLng(-15.65, -69.98);

  bool _cargando = true;
  bool _guardando = false;
  String? _error;
  MiTrazado? _mio;
  List<ZonaPoligono> _poligonos = const [];
  List<ProveedorDeRuta> _proveedores = const [];

  /// El trazo en edición. Es la única fuente de lo que se va a guardar.
  final List<ll.LatLng> _puntos = [];
  bool _sigueRutaDesignada = true;
  final _observacion = TextEditingController();

  @override
  void initState() {
    super.initState();
    _cargar();
  }

  @override
  void dispose() {
    _observacion.dispose();
    super.dispose();
  }

  Future<void> _cargar() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    final trazadosApi = context.read<TrazadosApi>();
    final rutasApi = context.read<RutasApi>();
    try {
      final mio = await trazadosApi.mio();
      // El polígono de la zona y los proveedores son referencia para dibujar: sin ellos el acopiador
      // estaría pintando sobre un mapa vacío sin saber por dónde pasan sus paradas.
      final poligonos = await rutasApi.poligonosDeZona();
      final proveedores = await rutasApi.proveedoresDeRuta(mio.rutaId);
      if (!mounted) return;
      setState(() {
        _mio = mio;
        _poligonos = poligonos;
        _proveedores = proveedores;
        _sigueRutaDesignada = mio.delDia?.sigueRutaDesignada ?? true;
        _observacion.text = mio.delDia?.observacion ?? '';
        _puntos
          ..clear()
          ..addAll(mio.baseParaPintar.map((p) => ll.LatLng(p.latitud, p.longitud)));
        _cargando = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  void _agregar(ll.LatLng punto) => setState(() => _puntos.add(punto));

  void _deshacer() {
    if (_puntos.isNotEmpty) setState(() => _puntos.removeLast());
  }

  void _limpiar() => setState(() => _puntos.clear());

  /// Vuelve a partir de la geometría de la ruta designada, para el caso "hoy sí voy por la designada".
  void _usarRutaDesignada() {
    final base = _mio?.deLaRuta ?? const <TrazadoPunto>[];
    if (base.isEmpty) {
      _avisar('La ruta designada todavía no tiene recorrido dibujado.');
      return;
    }
    setState(() {
      _puntos
        ..clear()
        ..addAll(base.map((p) => ll.LatLng(p.latitud, p.longitud)));
      _sigueRutaDesignada = true;
    });
  }

  void _avisar(String mensaje) {
    if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(mensaje)));
  }

  Future<void> _guardar() async {
    final mio = _mio;
    if (mio == null) return;
    if (_puntos.length < 2) {
      _avisar('Marca al menos dos puntos en el mapa para trazar el recorrido.');
      return;
    }
    // Se resuelven antes del primer await: leer el context después es lo que avisa
    // `use_build_context_synchronously`.
    final trazadosApi = context.read<TrazadosApi>();
    final sync = context.read<SyncService>();

    final fecha = DateTime.parse(mio.fecha);
    final cuerpo = TrazadosApi.cuerpo(
      fecha: fecha,
      sigueRutaDesignada: _sigueRutaDesignada,
      observacion: _observacion.text,
      puntos: [
        for (var i = 0; i < _puntos.length; i++)
          TrazadoPunto(secuencia: i, latitud: _puntos[i].latitude, longitud: _puntos[i].longitude),
      ],
    );

    setState(() => _guardando = true);
    var enCola = false;
    try {
      await trazadosApi.guardar(cuerpo);
    } catch (_) {
      // Sin señal (u otro fallo transitorio): a la cola. El acopiador no tiene que volver a
      // dibujarlo ni recordar reenviarlo.
      await sync.encolarTrazado(mio.fecha, cuerpo);
      enCola = true;
    }
    if (!mounted) return;
    setState(() => _guardando = false);
    _avisar(
      enCola
          ? 'Recorrido guardado en el teléfono; se enviará al recuperar señal.'
          : 'Recorrido del día guardado.',
    );
    Navigator.of(context).pop(true);
  }

  Future<void> _borrar() async {
    final mio = _mio;
    if (mio == null || mio.delDia == null) return;
    final trazadosApi = context.read<TrazadosApi>();
    setState(() => _guardando = true);
    try {
      await trazadosApi.borrar(fecha: DateTime.parse(mio.fecha));
      if (!mounted) return;
      _avisar('Recorrido del día descartado.');
      Navigator.of(context).pop(true);
    } catch (e) {
      if (!mounted) return;
      setState(() => _guardando = false);
      _avisar(mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final mio = _mio;
    return HuataScaffold(
      titulo: 'Pintar ruta del día',
      anchoCompleto: true,
      body: _cargando
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? _ErrorSimple(mensaje: _error!, onReintentar: _cargar)
          : ListView(
              padding: const EdgeInsets.all(16),
              children: [
                SeccionCard(
                  titulo: mio!.rutaNombre,
                  subtitulo: [
                    if (mio.zona != null && mio.zona!.isNotEmpty) 'Zona ${mio.zona}',
                    mio.fecha,
                  ].join(' · '),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      SwitchListTile(
                        contentPadding: EdgeInsets.zero,
                        value: _sigueRutaDesignada,
                        onChanged: (v) => setState(() => _sigueRutaDesignada = v),
                        title: const Text('Hoy voy por la ruta designada'),
                        subtitle: Text(
                          _sigueRutaDesignada
                              ? 'Queda anotado que sigues el recorrido asignado.'
                              : 'Queda anotado que hoy vas por un recorrido distinto al asignado.',
                          style: const TextStyle(fontSize: 12, color: HuataColors.muted),
                        ),
                      ),
                      if (mio.predeterminado != null)
                        Padding(
                          padding: const EdgeInsets.only(top: 4),
                          child: Text(
                            'Tienes un recorrido predeterminado aprobado '
                            '(${mio.predeterminado!.puntos.length} puntos). Lo que pintes hoy no lo '
                            'cambia: eso lo decide administración.',
                            style: const TextStyle(fontSize: 12, color: HuataColors.muted),
                          ),
                        ),
                    ],
                  ),
                ),
                const SizedBox(height: 16),
                SeccionCard(
                  titulo: 'Toca el mapa para marcar el recorrido',
                  subtitulo:
                      'Cada toque agrega un punto al final. En azul tenue, la ruta designada; en '
                      'naranja, tus proveedores.',
                  padding: EdgeInsets.zero,
                  child: SizedBox(
                    height: 320,
                    child: _MapaPintado(
                      puntos: _puntos,
                      rutaDesignada: mio.deLaRuta,
                      poligonos: _poligonos,
                      proveedores: _proveedores,
                      centroInicial: _centroInicial(mio),
                      onTocar: _agregar,
                    ),
                  ),
                ),
                const SizedBox(height: 12),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    Chip(
                      avatar: const Icon(Icons.timeline, size: 16),
                      label: Text('${_puntos.length} punto(s)'),
                      backgroundColor: HuataColors.greenSoft,
                    ),
                    OutlinedButton.icon(
                      onPressed: _puntos.isEmpty ? null : _deshacer,
                      icon: const Icon(Icons.undo),
                      label: const Text('Deshacer'),
                    ),
                    OutlinedButton.icon(
                      onPressed: _puntos.isEmpty ? null : _limpiar,
                      icon: const Icon(Icons.layers_clear),
                      label: const Text('Limpiar'),
                    ),
                    OutlinedButton.icon(
                      onPressed: _usarRutaDesignada,
                      icon: const Icon(Icons.alt_route),
                      label: const Text('Usar la designada'),
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                TextField(
                  controller: _observacion,
                  maxLength: 500,
                  maxLines: 2,
                  decoration: const InputDecoration(
                    labelText: 'Observación (opcional)',
                    hintText: 'Por qué cambia el recorrido hoy: camino cerrado, lluvia, etc.',
                  ),
                ),
                const SizedBox(height: 8),
                FilledButton.icon(
                  onPressed: _guardando ? null : _guardar,
                  icon: const Icon(Icons.save),
                  label: Text(_guardando ? 'Guardando…' : 'Guardar recorrido de hoy'),
                ),
                if (mio.delDia != null)
                  Padding(
                    padding: const EdgeInsets.only(top: 8),
                    child: TextButton.icon(
                      onPressed: _guardando ? null : _borrar,
                      icon: const Icon(Icons.delete_outline, color: HuataColors.red),
                      label: const Text('Descartar el recorrido de hoy', style: TextStyle(color: HuataColors.red)),
                    ),
                  ),
              ],
            ),
    );
  }

  ll.LatLng _centroInicial(MiTrazado mio) {
    if (_puntos.isNotEmpty) return _puntos.first;
    if (mio.deLaRuta.isNotEmpty) {
      return ll.LatLng(mio.deLaRuta.first.latitud, mio.deLaRuta.first.longitud);
    }
    return _centroPorDefecto;
  }
}

/// Mapa de edición. El encuadre inicial se fija una sola vez a propósito: reencuadrar en cada toque
/// devolvería la vista a otro zoom justo mientras se dibuja (es el mismo error que ya se corrigió en
/// el mapa de la web, §36.6).
class _MapaPintado extends StatelessWidget {
  const _MapaPintado({
    required this.puntos,
    required this.rutaDesignada,
    required this.poligonos,
    required this.proveedores,
    required this.centroInicial,
    required this.onTocar,
  });

  final List<ll.LatLng> puntos;
  final List<TrazadoPunto> rutaDesignada;
  final List<ZonaPoligono> poligonos;
  final List<ProveedorDeRuta> proveedores;
  final ll.LatLng centroInicial;
  final ValueChanged<ll.LatLng> onTocar;

  @override
  Widget build(BuildContext context) {
    final designada = rutaDesignada.map((p) => ll.LatLng(p.latitud, p.longitud)).toList();
    final ubicados = proveedores
        .where((p) => p.latitud != null && p.longitud != null)
        .map((p) => ll.LatLng(p.latitud as double, p.longitud as double))
        .toList();

    return FlutterMap(
      options: MapOptions(
        initialCenter: centroInicial,
        initialZoom: 13,
        onTap: (_, punto) => onTocar(punto),
      ),
      children: [
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          userAgentPackageName: 'org.ramiro.huata_app',
        ),
        if (poligonos.isNotEmpty)
          PolygonLayer(
            polygons: poligonos
                .where((z) => z.puntos.length > 2)
                .map(
                  (z) => Polygon(
                    points: z.puntos.map((p) => ll.LatLng(p.latitud, p.longitud)).toList(),
                    color: HuataColors.green.withValues(alpha: 0.08),
                    borderColor: HuataColors.green.withValues(alpha: 0.5),
                    borderStrokeWidth: 1.5,
                  ),
                )
                .toList(),
          ),
        // La designada va debajo y tenue: es referencia, no lo que se está editando.
        if (designada.length > 1)
          PolylineLayer(
            polylines: [
              Polyline(points: designada, strokeWidth: 3, color: HuataColors.blue.withValues(alpha: 0.35)),
            ],
          ),
        if (ubicados.isNotEmpty)
          CircleLayer(
            circles: ubicados
                .map(
                  (p) => CircleMarker(
                    point: p,
                    radius: 6,
                    color: HuataColors.amber.withValues(alpha: 0.8),
                    borderColor: HuataColors.amber,
                    borderStrokeWidth: 1.5,
                  ),
                )
                .toList(),
          ),
        if (puntos.length > 1)
          PolylineLayer(polylines: [Polyline(points: puntos, strokeWidth: 4, color: HuataColors.green)]),
        if (puntos.isNotEmpty)
          MarkerLayer(
            markers: [
              for (var i = 0; i < puntos.length; i++)
                Marker(
                  point: puntos[i],
                  width: 22,
                  height: 22,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      // El primero se distingue: en un trazo cerrado, sin esto no se sabe por dónde
                      // empieza el recorrido.
                      color: i == 0 ? HuataColors.green2 : Colors.white,
                      shape: BoxShape.circle,
                      border: Border.all(color: HuataColors.green, width: 2),
                    ),
                    child: Center(
                      child: Text(
                        '${i + 1}',
                        style: TextStyle(
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                          color: i == 0 ? Colors.white : HuataColors.green,
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
      ],
    );
  }
}

class _ErrorSimple extends StatelessWidget {
  const _ErrorSimple({required this.mensaje, required this.onReintentar});

  final String mensaje;
  final VoidCallback onReintentar;

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 40, color: HuataColors.red),
            const SizedBox(height: 12),
            Text(mensaje, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            OutlinedButton(onPressed: onReintentar, child: const Text('Reintentar')),
          ],
        ),
      ),
    );
  }
}
