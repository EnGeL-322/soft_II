import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_map_marker_cluster/flutter_map_marker_cluster.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart' as ll;
import 'package:provider/provider.dart';

import '../../core/api/ruteo_api.dart';
import '../../core/mapa/recorrido_mapa.dart';
import '../../core/models/ruta.dart';
import '../../core/theme/huata_theme.dart';
import 'parada_ruta.dart';

/// Qué dibuja el mapa. El acopiador lo cambia desde la hoja "Capas" para reducir el ruido cuando la
/// ruta tiene muchos proveedores.
class CapasMapa {
  const CapasMapa({
    this.soloPendientes = false,
    this.puntosEstablecidos = true,
    this.recorrido = true,
    this.ruta = true,
    this.zona = true,
  });

  final bool soloPendientes;
  final bool puntosEstablecidos;
  final bool recorrido;
  final bool ruta;
  final bool zona;

  CapasMapa copyWith({bool? soloPendientes, bool? puntosEstablecidos, bool? recorrido, bool? ruta, bool? zona}) =>
      CapasMapa(
        soloPendientes: soloPendientes ?? this.soloPendientes,
        puntosEstablecidos: puntosEstablecidos ?? this.puntosEstablecidos,
        recorrido: recorrido ?? this.recorrido,
        ruta: ruta ?? this.ruta,
        zona: zona ?? this.zona,
      );
}

/// Vista "Mapa": el mapa ocupa todo el espacio, con los controles flotando y la siguiente parada
/// anclada abajo.
class VistaMapa extends StatefulWidget {
  const VistaMapa({
    super.key,
    required this.zona,
    required this.puntosRuta,
    required this.poligonos,
    required this.puntosEstablecidos,
    required this.sincronizandoPuntos,
    required this.puntosGps,
    required this.paradas,
    required this.siguiente,
    required this.onRegistrar,
  });

  final String? zona;
  final List<RutaPunto> puntosRuta;
  final List<ZonaPoligono> poligonos;
  final List<PuntoEstablecido> puntosEstablecidos;
  final bool sincronizandoPuntos;
  final List<Position> puntosGps;
  final List<ParadaRuta> paradas;
  final ParadaRuta? siguiente;
  final void Function(ParadaRuta) onRegistrar;

  @override
  State<VistaMapa> createState() => _VistaMapaState();
}

class _VistaMapaState extends State<VistaMapa> {
  CapasMapa _capas = const CapasMapa();

  Future<void> _abrirCapas() async {
    await showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (_) => _HojaCapas(
        inicial: _capas,
        onCambio: (c) => setState(() => _capas = c),
      ),
    );
  }

  double? get _distanciaSiguiente {
    final s = widget.siguiente?.proveedor;
    if (s == null || s.latitud == null || s.longitud == null || widget.puntosGps.isEmpty) return null;
    final yo = widget.puntosGps.last;
    return Geolocator.distanceBetween(yo.latitude, yo.longitude, s.latitud!, s.longitud!);
  }

  @override
  Widget build(BuildContext context) {
    final pendientes = widget.paradas.where((p) => p.estado == EstadoVisita.pendiente).length;
    return ClipRRect(
      borderRadius: BorderRadius.circular(18),
      child: Stack(
        children: [
          Positioned.fill(
            child: MapaRutaAcopiador(
              puntosRuta: widget.puntosRuta,
              puntosGps: widget.puntosGps,
              poligonos: _capas.zona ? widget.poligonos : const [],
              puntosEstablecidos: _capas.puntosEstablecidos ? widget.puntosEstablecidos : const [],
              paradas: widget.paradas,
              siguiente: widget.siguiente,
              soloPendientes: _capas.soloPendientes,
              verRecorrido: _capas.recorrido,
              verRuta: _capas.ruta,
              onAbrirCapas: _abrirCapas,
            ),
          ),
          if (widget.sincronizandoPuntos)
            const Positioned(top: 10, left: 10, child: _ChipFlotante(cargando: true, texto: 'Actualizando puntos…')),
          Positioned(
            left: 10,
            right: 10,
            bottom: 10,
            child: widget.siguiente != null
                ? TarjetaSiguienteParada(
                    parada: widget.siguiente!,
                    zona: widget.zona,
                    distanciaMetros: _distanciaSiguiente,
                    restantes: pendientes,
                    onRegistrar: () => widget.onRegistrar(widget.siguiente!),
                  )
                : _TarjetaRutaCompleta(sinParadas: widget.paradas.isEmpty),
          ),
        ],
      ),
    );
  }
}

class _ChipFlotante extends StatelessWidget {
  const _ChipFlotante({required this.texto, this.cargando = false});
  final String texto;
  final bool cargando;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(999),
        boxShadow: HuataMarca.sombra,
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          if (cargando) ...[
            const SizedBox(width: 12, height: 12, child: CircularProgressIndicator(strokeWidth: 2)),
            const SizedBox(width: 7),
          ],
          Text(texto, style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600)),
        ],
      ),
    );
  }
}

/// La parada pendiente más próxima en el orden de visita, con acceso directo al registro.
class TarjetaSiguienteParada extends StatelessWidget {
  const TarjetaSiguienteParada({
    super.key,
    required this.parada,
    required this.zona,
    required this.distanciaMetros,
    required this.restantes,
    required this.onRegistrar,
  });

  final ParadaRuta parada;
  final String? zona;
  final double? distanciaMetros;
  final int restantes;
  final VoidCallback onRegistrar;

  @override
  Widget build(BuildContext context) {
    final p = parada.proveedor;
    final detalle = [
      p.codigo,
      if ((zona ?? '').isNotEmpty) zona!,
      if (distanciaMetros != null) 'a ${formatoDistancia(distanciaMetros!)}',
    ].join(' · ');
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        boxShadow: const [
          BoxShadow(color: Color(0x290F172A), blurRadius: 20, offset: Offset(0, 8)),
          BoxShadow(color: Color(0x0F0F172A), blurRadius: 3, offset: Offset(0, 1)),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              const Text(
                'SIGUIENTE PARADA',
                style: TextStyle(color: HuataColors.blue, fontSize: 10.5, fontWeight: FontWeight.w800, letterSpacing: 0.8),
              ),
              const Spacer(),
              Text(
                restantes == 1 ? 'Última pendiente' : 'Quedan $restantes',
                style: const TextStyle(color: HuataColors.muted, fontSize: 11.5, fontWeight: FontWeight.w600),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: [
              Container(
                width: 44,
                height: 44,
                alignment: Alignment.center,
                decoration: BoxDecoration(color: HuataColors.blue, borderRadius: BorderRadius.circular(12)),
                child: Text(
                  parada.numero,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.w800, fontSize: 16),
                ),
              ),
              const SizedBox(width: 12),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      p.nombreCompleto,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15.5),
                    ),
                    const SizedBox(height: 2),
                    Text(
                      detalle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                    ),
                    if (parada.testeoHoy != null) ...[
                      const SizedBox(height: 3),
                      EtiquetaTesteo(resultado: parada.testeoHoy!),
                    ],
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 12),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: onRegistrar,
              icon: const Icon(Icons.water_drop_outlined, size: 20),
              label: const Text('Registrar visita'),
            ),
          ),
        ],
      ),
    );
  }
}

class _TarjetaRutaCompleta extends StatelessWidget {
  const _TarjetaRutaCompleta({required this.sinParadas});
  final bool sinParadas;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: Colors.white,
        borderRadius: BorderRadius.circular(18),
        boxShadow: HuataMarca.sombra,
      ),
      child: Row(
        children: [
          Container(
            width: 40,
            height: 40,
            decoration: const BoxDecoration(color: HuataColors.greenSoft, shape: BoxShape.circle),
            child: Icon(sinParadas ? Icons.info_outline : Icons.check_rounded, color: HuataColors.green),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  sinParadas ? 'Ruta sin proveedores' : 'Ruta completada',
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15),
                ),
                Text(
                  sinParadas
                      ? 'Administración todavía no asignó proveedores a esta ruta.'
                      : 'Visitaste a todos los proveedores habilitados. Lleva la leche a planta.',
                  style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// Hoja "Capas del mapa": qué se dibuja, cómo se ve el recorrido y la leyenda de colores.
class _HojaCapas extends StatefulWidget {
  const _HojaCapas({required this.inicial, required this.onCambio});
  final CapasMapa inicial;
  final ValueChanged<CapasMapa> onCambio;

  @override
  State<_HojaCapas> createState() => _HojaCapasState();
}

class _HojaCapasState extends State<_HojaCapas> {
  late CapasMapa _capas = widget.inicial;

  void _cambiar(CapasMapa c) {
    setState(() => _capas = c);
    widget.onCambio(c);
  }

  Widget _interruptor(IconData icono, String titulo, String detalle, bool valor, ValueChanged<bool> cb) {
    return SwitchListTile(
      contentPadding: EdgeInsets.zero,
      secondary: Icon(icono, color: HuataColors.gray),
      title: Text(titulo),
      subtitle: Text(detalle),
      value: valor,
      onChanged: cb,
    );
  }

  @override
  Widget build(BuildContext context) {
    final preferencias = context.watch<PreferenciasMapa>();
    return SafeArea(
      child: SingleChildScrollView(
        padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Capas del mapa', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w700)),
            const SizedBox(height: 4),
            _interruptor(Icons.filter_alt_outlined, 'Solo pendientes', 'Oculta a quienes ya visitaste', _capas.soloPendientes,
                (v) => _cambiar(_capas.copyWith(soloPendientes: v))),
            _interruptor(Icons.route_outlined, 'Ruta planificada', 'Camino sugerido entre paradas', _capas.ruta,
                (v) => _cambiar(_capas.copyWith(ruta: v))),
            _interruptor(Icons.timeline, 'Mi recorrido de hoy', 'Lo que registró tu GPS', _capas.recorrido,
                (v) => _cambiar(_capas.copyWith(recorrido: v))),
            _interruptor(Icons.pentagon_outlined, 'Límite de la zona', 'Territorio de tu zona', _capas.zona,
                (v) => _cambiar(_capas.copyWith(zona: v))),
            _interruptor(Icons.place_outlined, 'Puntos establecidos', 'Referencias fijas de administración',
                _capas.puntosEstablecidos, (v) => _cambiar(_capas.copyWith(puntosEstablecidos: v))),
            const Divider(height: 24),
            const Text('Ver mi recorrido como', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 8),
            SizedBox(
              width: double.infinity,
              child: SegmentedButton<ModoRecorrido>(
                showSelectedIcon: false,
                segments: [
                  for (final m in ModoRecorrido.values)
                    ButtonSegment(value: m, label: Text(m.etiqueta), icon: Icon(m.icono, size: 18)),
                ],
                selected: {preferencias.modo},
                onSelectionChanged: (s) => preferencias.cambiar(s.first),
              ),
            ),
            const Divider(height: 28),
            const Text('Leyenda', style: TextStyle(fontWeight: FontWeight.w600)),
            const SizedBox(height: 10),
            const Wrap(
              spacing: 16,
              runSpacing: 10,
              children: [
                _ItemLeyenda(color: HuataColors.blue, texto: 'Pendiente'),
                _ItemLeyenda(color: HuataColors.estadoAcopiado, texto: 'Acopiado'),
                _ItemLeyenda(color: HuataColors.estadoNoEntregado, texto: 'No entregó'),
                _ItemLeyenda(color: HuataColors.amber, texto: 'Punto establecido', rombo: true),
                _ItemLeyenda(color: HuataColors.green2, texto: 'Grupo de proveedores'),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

class _ItemLeyenda extends StatelessWidget {
  const _ItemLeyenda({required this.color, required this.texto, this.rombo = false});
  final Color color;
  final String texto;
  final bool rombo;

  @override
  Widget build(BuildContext context) {
    final marca = Container(
      width: 12,
      height: 12,
      decoration: BoxDecoration(
        color: color,
        shape: rombo ? BoxShape.rectangle : BoxShape.circle,
        borderRadius: rombo ? BorderRadius.circular(2) : null,
        border: Border.all(color: Colors.white, width: 1.5),
        boxShadow: const [BoxShadow(color: Color(0x330F172A), blurRadius: 2)],
      ),
    );
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        rombo ? Transform.rotate(angle: 0.785398, child: marca) : marca,
        const SizedBox(width: 6),
        Text(texto, style: const TextStyle(fontSize: 13)),
      ],
    );
  }
}

/// Mapa de la ruta del acopiador: polígono de zona (territorio real), ruta planificada (por calles
/// reales cuando Valhalla responde, línea recta como respaldo), proveedores numerados por orden de
/// visita (RF-59: solo los que ya tienen una ubicación guardada) y el recorrido GPS propio de hoy.
class MapaRutaAcopiador extends StatefulWidget {
  const MapaRutaAcopiador({
    super.key,
    required this.puntosRuta,
    required this.puntosGps,
    required this.poligonos,
    required this.puntosEstablecidos,
    required this.paradas,
    required this.siguiente,
    required this.soloPendientes,
    required this.verRecorrido,
    required this.verRuta,
    required this.onAbrirCapas,
  });

  final List<RutaPunto> puntosRuta;
  final List<Position> puntosGps;
  final List<ZonaPoligono> poligonos;
  final List<PuntoEstablecido> puntosEstablecidos;
  final List<ParadaRuta> paradas;
  final ParadaRuta? siguiente;
  final bool soloPendientes;
  final bool verRecorrido;
  final bool verRuta;
  final VoidCallback onAbrirCapas;

  @override
  State<MapaRutaAcopiador> createState() => _MapaRutaAcopiadorState();
}

class _MapaRutaAcopiadorState extends State<MapaRutaAcopiador> {
  static const _centroPorDefecto = ll.LatLng(-15.48, -70.10);

  final _mapa = MapController();
  List<ll.LatLng> _rutaReal = [];
  String _claveVigente = '';

  @override
  void initState() {
    super.initState();
    _calcularRutaReal();
  }

  @override
  void didUpdateWidget(covariant MapaRutaAcopiador oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.puntosRuta != widget.puntosRuta) _calcularRutaReal();
  }

  @override
  void dispose() {
    _mapa.dispose();
    super.dispose();
  }

  Future<void> _calcularRutaReal() async {
    final puntos = widget.puntosRuta.map((p) => ll.LatLng(p.latitud, p.longitud)).toList();
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
    if (!mounted || clave != _claveVigente || real.length < 2) return;
    setState(() => _rutaReal = real);
  }

  /// Centra en mi posición si el recorrido está en curso; si no, en la siguiente parada.
  void _centrar() {
    final s = widget.siguiente?.proveedor;
    final destino = widget.puntosGps.isNotEmpty
        ? ll.LatLng(widget.puntosGps.last.latitude, widget.puntosGps.last.longitude)
        : (s?.latitud != null && s?.longitud != null ? ll.LatLng(s!.latitud!, s.longitud!) : null);
    if (destino == null) return;
    _mapa.move(destino, _mapa.camera.zoom < 15 ? 15 : _mapa.camera.zoom);
  }

  /// Encuadra todas las paradas con ubicación.
  void _verTodo() {
    final puntos = [
      for (final p in widget.paradas)
        if (p.proveedor.latitud != null && p.proveedor.longitud != null) ll.LatLng(p.proveedor.latitud!, p.proveedor.longitud!),
      ...widget.puntosRuta.map((p) => ll.LatLng(p.latitud, p.longitud)),
    ];
    if (puntos.isEmpty) return;
    if (puntos.length == 1) {
      _mapa.move(puntos.first, 15);
      return;
    }
    _mapa.fitCamera(CameraFit.coordinates(
      coordinates: puntos,
      padding: const EdgeInsets.fromLTRB(40, 60, 40, 220),
      maxZoom: 16,
    ));
  }

  @override
  Widget build(BuildContext context) {
    final puntosPlan = widget.puntosRuta.map((p) => ll.LatLng(p.latitud, p.longitud)).toList();
    final puntosGps = widget.puntosGps.map((p) => ll.LatLng(p.latitude, p.longitude)).toList();
    final s = widget.siguiente?.proveedor;
    final centro = puntosGps.isNotEmpty
        ? puntosGps.last
        : (s?.latitud != null && s?.longitud != null
              ? ll.LatLng(s!.latitud!, s.longitud!)
              : (puntosPlan.isNotEmpty ? puntosPlan.first : _centroPorDefecto));
    final lineaPlan = _rutaReal.length > 1 ? _rutaReal : puntosPlan;
    final modo = context.watch<PreferenciasMapa>().modo;
    final siguienteId = widget.siguiente?.proveedor.id;

    final marcadoresProveedor = <Marker>[];
    for (final f in widget.paradas) {
      final p = f.proveedor;
      if (p.latitud == null || p.longitud == null) continue;
      if (widget.soloPendientes && f.estado != EstadoVisita.pendiente) continue;
      // La "siguiente parada" se dibuja aparte, con pulso, para que nunca quede oculta dentro de un
      // grupo aunque haya muchos proveedores juntos.
      if (p.id == siguienteId) continue;
      marcadoresProveedor.add(_marcadorProveedor(f));
    }

    return FlutterMap(
      mapController: _mapa,
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
                    color: _colorDeZona(z.color).withValues(alpha: 0.10),
                    borderColor: _colorDeZona(z.color),
                    borderStrokeWidth: 2,
                  ),
                )
                .toList(),
          ),
        if (widget.verRuta && lineaPlan.length > 1)
          PolylineLayer(
            polylines: [
              Polyline(
                points: lineaPlan,
                strokeWidth: 5,
                color: HuataColors.blue.withValues(alpha: 0.75),
                borderStrokeWidth: 1.5,
                borderColor: Colors.white,
              ),
            ],
          ),
        if (widget.verRecorrido) ...capasRecorrido(puntosGps, modo, color: HuataColors.green),
        // Los proveedores se agrupan por cercanía (como el markercluster de Leaflet del prototipo).
        // Al acercarse, el grupo se abre solo; la "siguiente parada" queda fuera del grupo.
        if (marcadoresProveedor.isNotEmpty)
          MarkerClusterLayerWidget(
            options: MarkerClusterLayerOptions(
              maxClusterRadius: 52,
              size: const Size(40, 40),
              alignment: Alignment.center,
              disableClusteringAtZoom: 16,
              showPolygon: false,
              markers: marcadoresProveedor,
              computeSize: (markers) {
                final n = markers.length;
                final lado = n < 10 ? 34.0 : (n < 50 ? 42.0 : 50.0);
                return Size(lado, lado);
              },
              builder: _burbujaCluster,
            ),
          ),
        if (s != null && s.latitud != null && s.longitud != null)
          MarkerLayer(markers: [_marcadorSiguiente(widget.siguiente!)]),
        // "Puntos establecidos" (pedido del cliente): referencia fija de administración, dibujada
        // como rombo ámbar (capa aparte, sin número) para no confundirse con las paradas.
        if (widget.puntosEstablecidos.isNotEmpty)
          MarkerLayer(
            markers: widget.puntosEstablecidos
                .map(
                  (punto) => Marker(
                    key: ValueKey('marcador-punto-${punto.id}'),
                    point: ll.LatLng(punto.latitud, punto.longitud),
                    width: 22,
                    height: 22,
                    child: Tooltip(
                      message: punto.nombre,
                      triggerMode: TooltipTriggerMode.tap,
                      child: Transform.rotate(
                        angle: 0.785398,
                        child: Container(
                          width: 14,
                          height: 14,
                          decoration: BoxDecoration(
                            color: HuataColors.amber,
                            borderRadius: BorderRadius.circular(3),
                            border: Border.all(color: Colors.white, width: 2),
                          ),
                        ),
                      ),
                    ),
                  ),
                )
                .toList(),
          ),
        if (puntosGps.isNotEmpty)
          MarkerLayer(
            markers: [
              Marker(
                point: puntosGps.last,
                width: 26,
                height: 26,
                child: Container(
                  decoration: BoxDecoration(
                    color: HuataColors.green,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 3),
                    boxShadow: const [BoxShadow(color: Color(0x661D4F91), blurRadius: 10, spreadRadius: 2)],
                  ),
                ),
              ),
            ],
          ),
        Align(
          alignment: Alignment.topRight,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                _BotonMapa(icono: Icons.layers_outlined, ayuda: 'Capas y leyenda', onTap: widget.onAbrirCapas),
                const SizedBox(height: 8),
                _BotonMapa(icono: Icons.my_location, ayuda: 'Centrar', onTap: _centrar),
                const SizedBox(height: 8),
                _BotonMapa(icono: Icons.zoom_out_map, ayuda: 'Ver toda la ruta', onTap: _verTodo),
              ],
            ),
          ),
        ),
      ],
    );
  }

  /// Pendiente con número, acopiado y no entregado como puntos con ícono: así una ruta con muchos
  /// proveedores no se ve como un muro de números. Con testeo de hoy lleva una insignia.
  Marker _marcadorProveedor(ParadaRuta parada) {
    final m = _marcadorBase(parada);
    final t = parada.testeoHoy;
    final (texto, color) = t == null ? ('', HuataColors.muted) : textoTesteo(t);
    return Marker(
      point: m.point,
      width: m.width + 12,
      height: m.height + 12,
      child: Tooltip(
        message: t == null
            ? '${parada.proveedor.nombreCompleto} · ${parada.estado.etiqueta}'
            : '${parada.proveedor.nombreCompleto} · ${parada.estado.etiqueta} · $texto',
        triggerMode: TooltipTriggerMode.tap,
        child: Stack(
          clipBehavior: Clip.none,
          alignment: Alignment.center,
          children: [
            SizedBox(width: m.width, height: m.height, child: m.child),
            if (t != null)
              Positioned(
                right: 0,
                top: 0,
                child: Container(
                  width: 14,
                  height: 14,
                  decoration: BoxDecoration(
                    color: t.isEmpty ? Colors.white : color,
                    shape: BoxShape.circle,
                    border: Border.all(color: t.isEmpty ? color : Colors.white, width: 1.5),
                  ),
                  child: Icon(Icons.science, size: 9, color: t.isEmpty ? color : Colors.white),
                ),
              ),
          ],
        ),
      ),
    );
  }

  Marker _marcadorBase(ParadaRuta parada) {
    final p = parada.proveedor;
    final point = ll.LatLng(p.latitud!, p.longitud!);
    if (parada.estado != EstadoVisita.pendiente) {
      return Marker(
        point: point,
        width: 20,
        height: 20,
        child: Container(
          decoration: BoxDecoration(
            color: parada.estado.color,
            shape: BoxShape.circle,
            border: Border.all(color: Colors.white, width: 2),
            boxShadow: const [BoxShadow(color: Color(0x330F172A), blurRadius: 3)],
          ),
          child: Icon(
            parada.estado == EstadoVisita.acopiado ? Icons.check : Icons.close,
            size: 11,
            color: Colors.white,
          ),
        ),
      );
    }
    return Marker(
      point: point,
      width: 28,
      height: 28,
      child: Container(
        decoration: BoxDecoration(
          color: Colors.white,
          shape: BoxShape.circle,
          border: Border.all(color: HuataColors.blue, width: 2.5),
          boxShadow: const [BoxShadow(color: Color(0x330F172A), blurRadius: 4)],
        ),
        child: Center(
          child: Text(
            parada.numero,
            style: const TextStyle(color: HuataColors.blue, fontSize: 11, fontWeight: FontWeight.w800),
          ),
        ),
      ),
    );
  }

  Marker _marcadorSiguiente(ParadaRuta parada) {
    final p = parada.proveedor;
    return Marker(
      point: ll.LatLng(p.latitud!, p.longitud!),
      width: 50,
      height: 50,
      child: _PulsoSiguiente(numero: parada.numero),
    );
  }

  Widget _burbujaCluster(BuildContext context, List<Marker> markers) {
    return DecoratedBox(
      decoration: BoxDecoration(
        color: HuataColors.green2,
        shape: BoxShape.circle,
        border: Border.all(color: Colors.white, width: 3),
        boxShadow: const [BoxShadow(color: Colors.black26, blurRadius: 6)],
      ),
      child: Center(
        child: Text(
          '${markers.length}',
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
        ),
      ),
    );
  }
}

class _BotonMapa extends StatelessWidget {
  const _BotonMapa({required this.icono, required this.ayuda, required this.onTap});
  final IconData icono;
  final String ayuda;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Tooltip(
      message: ayuda,
      child: Material(
        color: Colors.white,
        elevation: 2,
        shadowColor: const Color(0x400F172A),
        borderRadius: BorderRadius.circular(12),
        child: InkWell(
          borderRadius: BorderRadius.circular(12),
          onTap: onTap,
          child: SizedBox(width: 44, height: 44, child: Icon(icono, color: HuataColors.text, size: 22)),
        ),
      ),
    );
  }
}

/// Marcador de la "siguiente parada": número dentro de un anillo que late, para que el ojo lo
/// encuentre aunque el mapa tenga muchos proveedores.
class _PulsoSiguiente extends StatefulWidget {
  const _PulsoSiguiente({required this.numero});
  final String numero;

  @override
  State<_PulsoSiguiente> createState() => _PulsoSiguienteState();
}

class _PulsoSiguienteState extends State<_PulsoSiguiente> with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(vsync: this, duration: const Duration(milliseconds: 1700))
    ..repeat();

  @override
  void dispose() {
    _control.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return AnimatedBuilder(
      animation: _control,
      builder: (context, _) {
        final t = _control.value;
        return Stack(
          alignment: Alignment.center,
          children: [
            Transform.scale(
              scale: 0.6 + 0.7 * t,
              child: Container(
                width: 46,
                height: 46,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: HuataColors.blue.withValues(alpha: 0.25 * (1 - t)),
                  border: Border.all(color: HuataColors.blue.withValues(alpha: 0.9 * (1 - t)), width: 2.5),
                ),
              ),
            ),
            Container(
              width: 32,
              height: 32,
              decoration: BoxDecoration(
                color: HuataColors.blue,
                shape: BoxShape.circle,
                border: Border.all(color: Colors.white, width: 3),
                boxShadow: const [BoxShadow(color: Colors.black38, blurRadius: 6)],
              ),
              child: Center(
                child: Text(
                  widget.numero,
                  style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
                ),
              ),
            ),
          ],
        );
      },
    );
  }
}

Color _colorDeZona(String? hex) {
  if (hex == null || hex.isEmpty) return HuataColors.green;
  final limpio = hex.replaceFirst('#', '');
  final valor = int.tryParse(limpio.length == 6 ? 'FF$limpio' : limpio, radix: 16);
  return valor != null ? Color(valor) : HuataColors.green;
}
