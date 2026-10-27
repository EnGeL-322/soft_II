import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/liquidaciones_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/liquidacion.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import 'proveedor_comun.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Pagos y cobros del proveedor (RF-80/RF-81), en su propio módulo de la barra: cuánto se le pagó y
/// cuánto tiene pendiente en el periodo elegido, y el historial de sus liquidaciones.
///
/// Los montos los calcula el backend con la configuración vigente; aquí solo se muestran.
class PagosProveedorScreen extends StatefulWidget {
  const PagosProveedorScreen({super.key});

  @override
  State<PagosProveedorScreen> createState() => _PagosProveedorScreenState();
}

class _PagosProveedorScreenState extends State<PagosProveedorScreen> with EnVivo<PagosProveedorScreen> {
  bool _cargando = true;
  String? _error;
  ResumenPagoPeriodo? _resumen;
  bool _cargandoResumen = false;
  String _periodo = 'SEMANA';
  DateTime _fecha = DateTime.now();
  List<Liquidacion> _pagos = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'pagos', 'acopio', 'calidad'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final api = context.read<LiquidacionesApi>();
    final rango = rangoPeriodo(_periodo, _fecha);
    try {
      final pagos = await api.misLiquidaciones(size: 100);
      // El resumen del periodo es secundario: si falla, el historial igual se muestra.
      ResumenPagoPeriodo? resumen;
      try {
        resumen = await api.miResumen(desde: rango.$1, hasta: rango.$2);
      } catch (_) {}
      if (!mounted) return;
      setState(() {
        _pagos = pagos..sort((a, b) => b.periodoFin.compareTo(a.periodoFin));
        _resumen = resumen;
        _cargando = false;
      });
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

  Future<void> _cambiarPeriodo({String? periodo, DateTime? fecha}) async {
    if (periodo != null) _periodo = periodo;
    if (fecha != null) _fecha = fecha;
    setState(() => _cargandoResumen = true);
    final api = context.read<LiquidacionesApi>();
    final rango = rangoPeriodo(_periodo, _fecha);
    try {
      final resumen = await api.miResumen(desde: rango.$1, hasta: rango.$2);
      if (mounted) setState(() => _resumen = resumen);
    } catch (e) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(mensajeDeError(e))));
    } finally {
      if (mounted) setState(() => _cargandoResumen = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Pagos y cobros',
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: _cargando
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? ErrorConReintento(mensaje: _error!, onReintentar: _cargar)
            : _contenido(),
      ),
    );
  }

  Widget _contenido() {
    final rango = rangoPeriodo(_periodo, _fecha);
    final resumen = _resumen;
    final pagados = _pagos.where((p) => p.estado == 'CLOSED').toList();
    final totalPagado = pagados.fold(0.0, (s, p) => s + p.montoNeto);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const CabeceraPantalla(
          icono: Icons.payments_outlined,
          titulo: 'Pagos y cobros',
          detalle: 'Lo que ya recibiste y lo que tienes por cobrar.',
        ),
        SeccionCard(
          icono: Icons.event_note_outlined,
          titulo: 'Resumen del periodo',
          subtitulo: 'Elige el día, la semana o el mes que quieres revisar',
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            SelectorPeriodo(
              periodo: _periodo,
              desde: rango.$1,
              hasta: rango.$2,
              onPeriodo: (p) => _cambiarPeriodo(periodo: p),
              onFecha: () async {
                final fecha = await elegirFechaPeriodo(context, _fecha);
                if (fecha != null) _cambiarPeriodo(fecha: fecha);
              },
            ),
            if (_cargandoResumen) const Padding(padding: EdgeInsets.only(top: 12), child: LinearProgressIndicator()),
            const SizedBox(height: 14),
            if (resumen == null)
              const Text('No se pudo calcular este periodo.', style: TextStyle(color: HuataColors.muted))
            else ...[
              Row(children: [
                Expanded(child: MontoCard(etiqueta: 'Ya pagado', monto: resumen.montoPagado, pagado: true)),
                const SizedBox(width: 8),
                Expanded(child: MontoCard(etiqueta: 'Por cobrar', monto: resumen.montoPendiente, pagado: false)),
              ]),
              const SizedBox(height: 12),
              Row(children: [
                Expanded(child: DatoClave(etiqueta: 'Entregado', valor: '${resumen.litrosProducidos.toStringAsFixed(1)} L')),
                const SizedBox(width: 8),
                Expanded(child: DatoClave(etiqueta: 'Pagados', valor: '${resumen.litrosPagados.toStringAsFixed(1)} L')),
                const SizedBox(width: 8),
                Expanded(child: DatoClave(etiqueta: 'Pendientes', valor: '${resumen.litrosPendientes.toStringAsFixed(1)} L')),
              ]),
            ],
          ]),
        ),
        const SizedBox(height: 12),
        SeccionCard(
          icono: Icons.receipt_long_outlined,
          titulo: 'Historial de pagos',
          subtitulo: pagados.isEmpty
              ? 'Tus liquidaciones, de la más reciente a la más antigua'
              : '${pagados.length} pago(s) recibido(s) · S/ ${totalPagado.toStringAsFixed(2)} en total',
          child: _pagos.isEmpty
              ? const Padding(
                  padding: EdgeInsets.symmetric(vertical: 8),
                  child: Text('Todavía no tienes liquidaciones registradas.', style: TextStyle(color: HuataColors.muted)),
                )
              : Column(children: [
                  for (var i = 0; i < _pagos.length; i++) ...[
                    if (i > 0) const Divider(height: 1),
                    _FilaPago(pago: _pagos[i]),
                  ],
                ]),
        ),
      ],
    );
  }
}

/// Una liquidación: el periodo, los litros, el neto recibido y su estado.
class _FilaPago extends StatelessWidget {
  const _FilaPago({required this.pago});
  final Liquidacion pago;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 10),
    child: Row(children: [
      const IconoRedondo(icono: Icons.receipt_long_outlined),
      const SizedBox(width: 12),
      Expanded(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(
            'S/ ${pago.montoNeto.toStringAsFixed(2)}',
            style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16, letterSpacing: -0.2),
          ),
          const SizedBox(height: 2),
          Text(
            '${fechaLegible(pago.periodoInicio)} – ${fechaLegible(pago.periodoFin)} · '
            '${pago.litrosTotales.toStringAsFixed(1)} L',
            style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
          ),
        ]),
      ),
      const SizedBox(width: 8),
      EstadoPagoChip(estado: pago.estado),
    ]),
  );
}
