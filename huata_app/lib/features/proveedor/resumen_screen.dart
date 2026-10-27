import 'dart:async';

import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';
import 'package:provider/provider.dart';

import '../../core/api/liquidaciones_api.dart';
import '../../core/api/mensajeria_api.dart';
import '../../core/models/mensajeria.dart';
import '../../core/api/portal_api.dart';
import '../../core/api/productores_api.dart';
import '../../core/api/recojos_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/mensajeria/mensajeria_en_vivo.dart';
import '../../core/sync/sync_service.dart';
import '../../core/models/liquidacion.dart';
import '../../core/models/productor.dart';
import '../../core/models/recojo.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/graficos.dart';
import '../../shared/huata_scaffold.dart';
import 'mensajes_screen.dart';
import 'proveedor_comun.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Inicio del proveedor (12, RF-78-83): la leche de hoy y de la semana, aviso de mala calidad del día
/// propio y de la comunidad (RF-49/RF-83), mensajes y ubicación (2.6). Pagos y cobros, el historial
/// de producción y el cambio de zona son módulos propios de la barra (`pagos_proveedor_screen.dart`,
/// `historial_produccion_screen.dart`, `cambio_zona_screen.dart`).
class ResumenProveedorScreen extends StatefulWidget {
  const ResumenProveedorScreen({super.key});

  @override
  State<ResumenProveedorScreen> createState() => _ResumenProveedorScreenState();
}

class _ResumenProveedorScreenState extends State<ResumenProveedorScreen> with EnVivo<ResumenProveedorScreen> {
  bool _cargando = true;
  String? _error;
  Productor? _yo;
  List<Recojo> _historialRecojos = [];
  ConsultaPublica? _consultaPublica;
  ResumenPublicoHoy? _resumenHoy;
  bool _guardandoUbicacion = false;

  /// Mensajes de Huata (Secretaría/Administración): nuevos y últimos, sin marcarlos como leídos.
  ConversacionDetalle? _bandeja;

  /// Cobro estimado del ciclo en curso (RF-80). Informativo: si falla, la tarjeta no se muestra.
  CobroEstimado? _estimado;
  Timer? _actualizadorBandeja;
  late final MensajeriaEnVivo _vivo;
  StreamSubscription<EventoMensajeria>? _eventosMensajeria;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'acopio', 'calidad', 'pagos', 'proveedores'}, () => _cargar(silencioso: true));
    // Leer SyncService lo crea si todavía no existía: así los mensajes que quedaron en la cola de
    // una sesión anterior (app cerrada sin señal) salen apenas se abre la app.
    context.read<SyncService>();
    _vivo = context.read<MensajeriaEnVivo>()..iniciar();
    // Un aviso de la planta aparece al instante por el canal; la bandeja se recarga con el contador
    // que calcula el servidor (no se suma aquí, para no desfasarse de lo que dice la base).
    _eventosMensajeria = _vivo.eventos.listen((e) {
      if (e is CanalListo || e is MensajesRecuperados || (e is MensajeNuevo && !e.mensaje.mio)) _cargarBandeja();
    });
    _cargar();
    // Respaldo: solo mientras el canal en vivo está caído.
    _actualizadorBandeja = Timer.periodic(const Duration(seconds: 30), (_) {
      if (!_vivo.conectado) _cargarBandeja();
    });
  }

  @override
  void dispose() {
    _actualizadorBandeja?.cancel();
    _eventosMensajeria?.cancel();
    _vivo.detener();
    super.dispose();
  }

  Future<void> _cargarBandeja() async {
    try {
      final bandeja = await context.read<MensajeriaApi>().bandeja();
      if (mounted) setState(() => _bandeja = bandeja);
    } catch (_) {
      // Informativo: si falla, el resto del resumen sigue sirviendo.
    }
  }

  int get _mensajesNuevos => _bandeja?.conversacion.mensajesSinLeer ?? 0;

  /// Abrir la conversación marca los mensajes como leídos; al volver se actualiza el contador.
  Future<void> _abrirMensajes() async {
    await Navigator.of(context).push(MaterialPageRoute(builder: (_) => const MensajesProveedorScreen()));
    if (mounted) _cargarBandeja();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final productoresApi = context.read<ProductoresApi>();
      final recojosApi = context.read<RecojosApi>();
      final portalApi = context.read<PortalApi>();
      final mensajeriaApi = context.read<MensajeriaApi>();
      final liquidacionesApi = context.read<LiquidacionesApi>();

      final yo = await productoresApi.yo();
      final recojos = await recojosApi.misRecojos(size: 500);

      // Los avisos de calidad son informativos: si alguno falla, el resto de la
      // pantalla igual sirve (no se cae toda la pantalla por un endpoint secundario).
      ConsultaPublica? consulta;
      try {
        consulta = await portalApi.consultar(yo.codigo);
      } catch (_) {}
      ResumenPublicoHoy? resumenHoy;
      try {
        resumenHoy = await portalApi.incidenciasDeHoy();
      } catch (_) {}
      CobroEstimado? estimado;
      try {
        estimado = await liquidacionesApi.miEstimado();
      } catch (_) {}
      ConversacionDetalle? bandeja;
      try {
        bandeja = await mensajeriaApi.bandeja();
      } catch (_) {}

      setState(() {
        _bandeja = bandeja;
        _estimado = estimado;
        _yo = yo;
        _historialRecojos = recojos..sort((a, b) => b.fechaHora.compareTo(a.fechaHora));
        _consultaPublica = consulta;
        _resumenHoy = resumenHoy;
        _cargando = false;
      });
    } catch (e) {
      // En vivo: si la recarga falla (sin señal), se queda lo que ya se veía.
      if (silencioso) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  /// 2.6: guarda la ubicación actual del proveedor (casa o punto de entrega actual).
  Future<void> _guardarUbicacion() async {
    if (_yo == null || _guardandoUbicacion) return;
    final productoresApi = context.read<ProductoresApi>();

    final tipo = await showDialog<String>(
      context: context,
      builder: (ctx) => SimpleDialog(
        title: const Text('¿Qué punto es este?'),
        children: [
          SimpleDialogOption(onPressed: () => Navigator.pop(ctx, 'CASA'), child: const Text('Mi casa')),
          SimpleDialogOption(
            onPressed: () => Navigator.pop(ctx, 'ACTUAL'),
            child: const Text('Donde estoy ahora (punto de entrega)'),
          ),
        ],
      ),
    );
    if (tipo == null || !mounted) return;

    setState(() => _guardandoUbicacion = true);
    try {
      var permiso = await Geolocator.checkPermission();
      if (permiso == LocationPermission.denied) permiso = await Geolocator.requestPermission();
      if (permiso == LocationPermission.denied || permiso == LocationPermission.deniedForever) {
        throw Exception('Se necesita permiso de ubicación para guardar el punto.');
      }
      if (!await Geolocator.isLocationServiceEnabled()) {
        throw Exception('Activa el GPS del dispositivo.');
      }

      final posicion = await Geolocator.getCurrentPosition(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high),
      );
      await productoresApi.registrarUbicacion(
        _yo!.id,
        ProductorUbicacion(
          latitud: posicion.latitude,
          longitud: posicion.longitude,
          precisionMetros: posicion.accuracy,
          tipo: tipo,
        ),
      );
      if (mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text('Ubicación guardada (${tipo == 'CASA' ? 'mi casa' : 'punto actual'}).')));
      }
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
      }
    } finally {
      if (mounted) setState(() => _guardandoUbicacion = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Mi resumen',
      actions: [
        IconButton(
          tooltip: _mensajesNuevos > 0 ? '$_mensajesNuevos mensaje(s) nuevo(s) de Huata' : 'Mensajes con Huata',
          icon: Badge(
            isLabelVisible: _mensajesNuevos > 0,
            label: Text('$_mensajesNuevos'),
            child: const Icon(Icons.forum_outlined),
          ),
          onPressed: _abrirMensajes,
        ),
      ],
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: _cargando
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? Center(
                child: Padding(
                  padding: const EdgeInsets.all(24),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      Text(_error!, textAlign: TextAlign.center),
                      const SizedBox(height: 12),
                      OutlinedButton(onPressed: _cargar, child: const Text('Reintentar')),
                    ],
                  ),
                ),
              )
            : _buildContenido(),
      ),
    );
  }

  Widget _buildContenido() {
    final yo = _yo!;
    final hoy = DateTime.now();
    final deHoy = _historialRecojos.where((r) => mismoDia(r.fechaHora, hoy) && r.estado != 'CANCELLED').toList();
    final litrosHoy = deHoy.fold(0.0, (s, r) => s + r.litros);
    final inicioSemana = DateTime(hoy.year, hoy.month, hoy.day).subtract(const Duration(days: 6));
    final litrosSemana = _historialRecojos
        .where((r) => r.estado != 'CANCELLED' && !r.fechaHora.toLocal().isBefore(inicioSemana))
        .fold(0.0, (s, r) => s + r.litros);
    final avisoPropio = _consultaPublica?.historial
        .where((h) => h.fecha == fechaIso(hoy) && h.resultado != 'ACEPTADA')
        .firstOrNull;
    // RF-49/RF-83: aviso a la comunidad, no solo a mi propio historial. No se nombra a nadie más
    // que el código público ya autorizado; no se compara contra mi zona todavía porque el portal
    // público no siempre conoce la zona del proveedor observado.
    final incidenciasComunidad = (_resumenHoy?.incidencias ?? [])
        .where((i) => i.productorCodigo != yo.codigo)
        .toList();

    final nav = NavegacionModulos.de(context);
    final entregas = _historialRecojos.where((r) => r.estado != 'CANCELLED' && r.litros > 0).toList();
    final ultima = entregas.firstOrNull; // _historialRecojos ya viene del más reciente al más antiguo
    final semana = entregas.where((r) => !r.fechaHora.toLocal().isBefore(inicioSemana)).toList();
    final buenas = semana.where((r) => r.resultadoCalidad == 'ACEPTADA').length;
    final observadas = semana.where((r) => r.resultadoCalidad == 'OBSERVADA').length;
    final malas = semana.where((r) => r.resultadoCalidad == 'RECHAZADA').length;
    final pendientes = semana.length - buenas - observadas - malas;
    final barrasSemana = barrasPorDia(_historialRecojos, inicioSemana, DateTime(hoy.year, hoy.month, hoy.day));
    final ultimoMensaje = _bandeja?.mensajes.where((m) => !m.mio).lastOrNull;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        _CabeceraProveedor(
          nombre: yo.nombreCompleto,
          codigo: yo.codigo,
          zona: yo.zonaPrincipal ?? yo.rutaNombre ?? '—',
          litrosHoy: litrosHoy,
          litrosSemana: litrosSemana,
        ),
        if (avisoPropio != null) ...[
          const SizedBox(height: 12),
          EstadoAviso(
            esError: avisoPropio.resultado == 'RECHAZADA',
            mensaje: 'Aviso de calidad de hoy: ${_etiquetaResultado(avisoPropio.resultado)}.',
          ),
        ],
        if (incidenciasComunidad.isNotEmpty) ...[
          const SizedBox(height: 12),
          EstadoAviso(
            esError: true,
            mensaje: incidenciasComunidad.length == 1
                ? 'Hoy el proveedor ${incidenciasComunidad.first.productorCodigo} '
                      '${incidenciasComunidad.first.zona != null ? '(${incidenciasComunidad.first.zona}) ' : ''}'
                      'registró una incidencia de calidad (${incidenciasComunidad.first.tipo}).'
                : 'Hoy se registraron ${incidenciasComunidad.length} incidencias de calidad en la red de acopio.',
          ),
        ],
        if (_estimado != null) ...[
          const SizedBox(height: 12),
          _TarjetaCobro(
            estimado: _estimado!,
            onTap: nav?.disponibles.contains('pagos') == true ? () => nav!.ir('pagos') : null,
          ),
        ],
        const SizedBox(height: 12),
        _TarjetaInicio(
          icono: Icons.bar_chart_rounded,
          titulo: 'Tus entregas de los últimos 7 días',
          onTap: nav?.disponibles.contains('historial') == true ? () => nav!.ir('historial') : null,
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(
              '${litrosSemana.toStringAsFixed(1)} L en total · '
              '${barrasSemana.where((b) => b.valor > 0).length} de 7 días con entrega',
              style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
            ),
            const SizedBox(height: 12),
            GraficoBarras(barras: barrasSemana),
            const SizedBox(height: 8),
            const LeyendaGrafico(elementos: leyendaCalidad),
          ]),
        ),
        const SizedBox(height: 12),
        _TarjetaUltimaEntrega(
          entrega: ultima,
          onTap: nav?.disponibles.contains('historial') == true ? () => nav!.ir('historial') : null,
        ),
        const SizedBox(height: 12),
        _TarjetaCalidadSemana(buenas: buenas, observadas: observadas, malas: malas, pendientes: pendientes),
        const SizedBox(height: 12),
        _TarjetaMensajes(ultimo: ultimoMensaje, nuevos: _mensajesNuevos, onTap: _abrirMensajes),
        const SizedBox(height: 4),
        const EncabezadoSeccion('Accesos rápidos'),
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 10,
          crossAxisSpacing: 10,
          mainAxisExtent: 72,
          children: [
            if (nav?.disponibles.contains('pagos') == true)
              _AccesoRapido(
                icono: Icons.payments_outlined,
                titulo: 'Pagos y cobros',
                detalle: 'Lo pagado y por cobrar',
                onTap: () => nav!.ir('pagos'),
              ),
            if (nav?.disponibles.contains('historial') == true)
              _AccesoRapido(
                icono: Icons.water_drop_outlined,
                titulo: 'Historial',
                detalle: 'Mis entregas por día',
                onTap: () => nav!.ir('historial'),
              ),
            if (nav?.disponibles.contains('zona') == true)
              _AccesoRapido(
                icono: Icons.edit_location_alt_outlined,
                titulo: 'Cambio de zona',
                detalle: 'Pedir otra zona',
                onTap: () => nav!.ir('zona'),
              ),
            _AccesoRapido(
              icono: Icons.my_location_outlined,
              titulo: 'Mi ubicación',
              detalle: 'Guardar dónde estoy',
              ocupado: _guardandoUbicacion,
              onTap: _guardandoUbicacion ? null : _guardarUbicacion,
            ),
          ],
        ),
      ],
    );
  }

  String _etiquetaResultado(String r) => switch (r) {
    'OBSERVADA' => 'leche observada',
    'RECHAZADA' => 'leche rechazada',
    _ => r,
  };
}






/// Tarjeta superior: quién es, su código y zona, y la leche de hoy y de la semana a la vista.
class _CabeceraProveedor extends StatelessWidget {
  const _CabeceraProveedor({
    required this.nombre,
    required this.codigo,
    required this.zona,
    required this.litrosHoy,
    required this.litrosSemana,
  });
  final String nombre;
  final String codigo;
  final String zona;
  final double litrosHoy;
  final double litrosSemana;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(18),
      decoration: BoxDecoration(
        gradient: HuataMarca.degradado,
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [BoxShadow(color: Color(0x331D4F91), blurRadius: 18, offset: Offset(0, 8))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            _saludo(),
            style: const TextStyle(color: Color(0xCCFFFFFF), fontSize: 13.5, fontWeight: FontWeight.w500),
          ),
          const SizedBox(height: 2),
          Text(
            nombre,
            style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w700, letterSpacing: -0.3),
          ),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 6, children: [
            _Pastilla(icono: Icons.badge_outlined, texto: codigo),
            _Pastilla(icono: Icons.place_outlined, texto: zona),
          ]),
          const SizedBox(height: 18),
          Row(children: [
            Expanded(child: _CifraClara(etiqueta: 'Hoy', valor: '${litrosHoy.toStringAsFixed(1)} L')),
            Container(width: 1, height: 40, color: const Color(0x33FFFFFF)),
            const SizedBox(width: 16),
            Expanded(child: _CifraClara(etiqueta: 'Últimos 7 días', valor: '${litrosSemana.toStringAsFixed(1)} L')),
          ]),
        ],
      ),
    );
  }
}

class _Pastilla extends StatelessWidget {
  const _Pastilla({required this.icono, required this.texto});
  final IconData icono;
  final String texto;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 5),
    decoration: BoxDecoration(color: const Color(0x26FFFFFF), borderRadius: BorderRadius.circular(999)),
    child: Row(mainAxisSize: MainAxisSize.min, children: [
      Icon(icono, size: 14, color: Colors.white),
      const SizedBox(width: 5),
      Text(texto, style: const TextStyle(color: Colors.white, fontSize: 12.5, fontWeight: FontWeight.w600)),
    ]),
  );
}

class _CifraClara extends StatelessWidget {
  const _CifraClara({required this.etiqueta, required this.valor});
  final String etiqueta;
  final String valor;

  @override
  Widget build(BuildContext context) => Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
    Text(etiqueta.toUpperCase(),
        style: const TextStyle(color: Color(0xB3FFFFFF), fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.6)),
    const SizedBox(height: 4),
    FittedBox(
      fit: BoxFit.scaleDown,
      alignment: Alignment.centerLeft,
      child: Text(valor,
          style: const TextStyle(color: Colors.white, fontSize: 28, fontWeight: FontWeight.w800, letterSpacing: -0.6)),
    ),
  ]);
}

/// Botón grande con ícono, título y una línea de ayuda: se lee como acción, no como formulario.
class _AccesoRapido extends StatelessWidget {
  const _AccesoRapido({required this.icono, required this.titulo, required this.detalle, this.onTap, this.ocupado = false});
  final IconData icono;
  final String titulo;
  final String detalle;
  final VoidCallback? onTap;
  final bool ocupado;

  @override
  Widget build(BuildContext context) => Material(
    color: HuataColors.panel,
    borderRadius: BorderRadius.circular(16),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(14),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE8EDF3)),
        ),
        child: Row(children: [
          ocupado
              ? const SizedBox(
                  width: 36, height: 36,
                  child: Padding(padding: EdgeInsets.all(9), child: CircularProgressIndicator(strokeWidth: 2)))
              : IconoRedondo(icono: icono),
          const SizedBox(width: 10),
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(titulo, maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14)),
              Text(detalle, maxLines: 1, overflow: TextOverflow.ellipsis,
                  style: const TextStyle(color: HuataColors.muted, fontSize: 12)),
            ]),
          ),
        ]),
      ),
    ),
  );
}


String _saludo() {
  final h = DateTime.now().hour;
  return h < 12 ? 'Buenos días' : h < 19 ? 'Buenas tardes' : 'Buenas noches';
}

String _hora(DateTime d) {
  final l = d.toLocal();
  return '${l.hour.toString().padLeft(2, '0')}:${l.minute.toString().padLeft(2, '0')}';
}

/// Tarjeta de Inicio: ícono, título y contenido; con una flecha cuando lleva a otro módulo.
class _TarjetaInicio extends StatelessWidget {
  const _TarjetaInicio({required this.icono, required this.titulo, required this.child, this.onTap, this.extra});
  final IconData icono;
  final String titulo;
  final Widget child;
  final VoidCallback? onTap;
  final Widget? extra;

  @override
  Widget build(BuildContext context) => Material(
    color: HuataColors.panel,
    borderRadius: BorderRadius.circular(16),
    child: InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(16),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE8EDF3)),
        ),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            IconoRedondo(icono: icono),
            const SizedBox(width: 10),
            Expanded(child: Text(titulo, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 15.5))),
            ?extra,
            if (onTap != null) const Icon(Icons.chevron_right, color: HuataColors.muted),
          ]),
          const SizedBox(height: 12),
          child,
        ]),
      ),
    ),
  );
}

/// Cobro estimado del ciclo en curso. Lo calcula el backend; puede cambiar al cerrarse el ciclo.
class _TarjetaCobro extends StatelessWidget {
  const _TarjetaCobro({required this.estimado, this.onTap});
  final CobroEstimado estimado;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => _TarjetaInicio(
    icono: Icons.payments_outlined,
    titulo: 'Tu cobro de este ciclo',
    onTap: onTap,
    extra: estimado.liquidado
        ? const Padding(
            padding: EdgeInsets.only(right: 4),
            child: EtiquetaEstado(texto: 'Liquidado', color: HuataColors.green, fondo: HuataColors.greenSoft),
          )
        : null,
    child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Row(children: [
        Expanded(child: MontoCard(etiqueta: 'Por cobrar', monto: estimado.importePendiente, pagado: false)),
        const SizedBox(width: 8),
        Expanded(
          child: DatoClave(etiqueta: 'Litros entregados', valor: '${estimado.litrosEntregados.toStringAsFixed(1)} L'),
        ),
      ]),
      const SizedBox(height: 8),
      const Text(
        'Es un estimado: el monto final se confirma al cerrar el ciclo.',
        style: TextStyle(color: HuataColors.muted, fontSize: 12.5),
      ),
    ]),
  );
}

class _TarjetaUltimaEntrega extends StatelessWidget {
  const _TarjetaUltimaEntrega({required this.entrega, this.onTap});
  final Recojo? entrega;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final e = entrega;
    return _TarjetaInicio(
      icono: Icons.local_drink_outlined,
      titulo: 'Tu última entrega',
      onTap: onTap,
      child: e == null
          ? const Text('Todavía no tienes entregas registradas.', style: TextStyle(color: HuataColors.muted))
          : Row(children: [
              Expanded(
                child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                  Text(
                    '${e.litros.toStringAsFixed(1)} L',
                    style: const TextStyle(fontSize: 24, fontWeight: FontWeight.w800, letterSpacing: -0.4),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    '${fechaLegible(fechaIso(e.fechaHora))} · ${_hora(e.fechaHora)} · '
                    'Recogió: ${e.acopiadorNombre ?? 'Sin identificar'}',
                    style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                  ),
                ]),
              ),
              const SizedBox(width: 8),
              CalidadChip(resultado: e.resultadoCalidad ?? 'PENDIENTE'),
            ]),
    );
  }
}

/// Cuántas entregas de los últimos 7 días salieron buenas, observadas o malas.
class _TarjetaCalidadSemana extends StatelessWidget {
  const _TarjetaCalidadSemana({
    required this.buenas,
    required this.observadas,
    required this.malas,
    required this.pendientes,
  });
  final int buenas;
  final int observadas;
  final int malas;
  final int pendientes;

  @override
  Widget build(BuildContext context) => _TarjetaInicio(
    icono: Icons.verified_outlined,
    titulo: 'Calidad de los últimos 7 días',
    child: GraficoDona(
      textoCentro: 'entregas',
      tramos: [
        TramoDona(etiqueta: 'Buena', valor: buenas, color: colorCalidad('ACEPTADA')),
        TramoDona(etiqueta: 'Observada', valor: observadas, color: colorCalidad('OBSERVADA')),
        TramoDona(etiqueta: 'Mala', valor: malas, color: colorCalidad('RECHAZADA')),
        TramoDona(etiqueta: 'Pendiente', valor: pendientes, color: colorCalidad('PENDIENTE')),
      ],
    ),
  );
}

class _TarjetaMensajes extends StatelessWidget {
  const _TarjetaMensajes({required this.ultimo, required this.nuevos, required this.onTap});
  final MensajeProveedor? ultimo;
  final int nuevos;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final m = ultimo;
    return _TarjetaInicio(
      icono: Icons.forum_outlined,
      titulo: 'Mensajes de Huata',
      onTap: onTap,
      extra: nuevos > 0
          ? Padding(
              padding: const EdgeInsets.only(right: 4),
              child: EtiquetaEstado(texto: '$nuevos nuevo(s)', color: HuataColors.blue, fondo: HuataColors.blueSoft),
            )
          : null,
      child: m == null
          ? const Text('No tienes mensajes. Toca para escribir a la planta.', style: TextStyle(color: HuataColors.muted))
          : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(m.cuerpo, maxLines: 2, overflow: TextOverflow.ellipsis, style: const TextStyle(fontSize: 14, height: 1.35)),
              const SizedBox(height: 4),
              Text(
                '${m.autorNombre} · ${fechaLegible(fechaIso(m.creadoEn))} ${_hora(m.creadoEn)}',
                style: const TextStyle(color: HuataColors.muted, fontSize: 12),
              ),
            ]),
    );
  }
}

extension _FirstOrNull<T> on Iterable<T> {
  T? get firstOrNull => isEmpty ? null : first;
  T? get lastOrNull => isEmpty ? null : last;
}
