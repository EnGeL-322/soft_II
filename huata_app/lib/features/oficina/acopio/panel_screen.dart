import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/acopio_oficina_api.dart';
import '../../../core/api/calidad_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/acopio_oficina.dart';
import '../../../core/models/calidad.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/graficos.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';
import '../../proveedor/proveedor_comun.dart' show CalidadChip, colorCalidad;

/// Vista "Inicio" de la web (panel.read): el día de un vistazo — litros por ruta, calidad, pendientes
/// y el cierre de la jornada.
class PanelScreen extends StatefulWidget {
  const PanelScreen({super.key});

  @override
  State<PanelScreen> createState() => _PanelScreenState();
}

class _PanelScreenState extends State<PanelScreen> with EnVivo<PanelScreen> {
  DateTime _dia = DateTime.now();
  bool _cargando = true;
  String? _error;
  ResumenOperacion? _resumen;
  TotalesDia? _totales;
  /// Análisis de calidad del día, uno por uno (null si la sesión no tiene calidad.read).
  List<AnalisisPorAprobar>? _analisis;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'acopio', 'calidad', 'pagos', 'proveedores'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final api = context.read<AcopioOficinaApi>();
    final dia = _dia;
    try {
      final resumen = await api.resumen(dia);
      TotalesDia? totales;
      try {
        totales = await api.totales(dia);
      } catch (_) {}
      List<AnalisisPorAprobar>? analisis;
      try {
        analisis = await _analisisDelDia(dia);
      } catch (_) {}
      if (!mounted || dia != _dia) return;
      setState(() {
        _resumen = resumen;
        _totales = totales;
        _analisis = analisis;
        _cargando = false;
        _error = null;
      });
    } catch (e) {
      if (silencioso || !mounted) return;
      setState(() {
        _error = mensajeDeError(e);
        _cargando = false;
      });
    }
  }

  /// El backend da los análisis de los últimos N días; se piden hasta el día elegido y se filtra.
  Future<List<AnalisisPorAprobar>> _analisisDelDia(DateTime dia) async {
    final hoy = DateUtils.dateOnly(DateTime.now());
    final elegido = DateUtils.dateOnly(dia);
    final dias = hoy.difference(elegido).inDays + 1;
    if (dias < 1 || dias > 60) return const [];
    final lista = await context.read<CalidadApi>().recientes(dias: dias);
    return lista.where((a) => a.fechaHora != null && DateUtils.isSameDay(a.fechaHora, elegido)).toList();
  }

  void _cambiarDia(DateTime d) {
    setState(() => _dia = d);
    _cargar();
  }

  @override
  Widget build(BuildContext context) {
    final r = _resumen;
    return HuataScaffold(
      titulo: 'Inicio',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        cabecera: [SelectorDia(dia: _dia, alCambiar: _cambiarDia)],
        hijos: r == null ? const [] : _contenido(r),
      ),
    );
  }

  List<Widget> _contenido(ResumenOperacion r) {
    final t = _totales;
    final analizados = r.entregasAceptadas + r.entregasRechazadas;
    final rutas = [...r.rutas]..sort((a, b) => b.litros.compareTo(a.litros));
    return [
      Row(children: [
        Expanded(child: DatoClave(etiqueta: 'Litros del día', valor: litros(r.litrosTotales), icono: Icons.water_drop_outlined)),
        const SizedBox(width: 8),
        Expanded(child: DatoClave(etiqueta: 'Proveedores', valor: '${r.proveedores}', icono: Icons.groups_outlined)),
      ]),
      const SizedBox(height: 8),
      Row(children: [
        Expanded(
          child: DatoClave(
            etiqueta: 'Sin análisis',
            valor: '${r.pendientesCalidad}',
            color: r.pendientesCalidad > 0 ? HuataColors.amber : null,
            fondo: r.pendientesCalidad > 0 ? HuataColors.amberSoft : null,
          ),
        ),
        const SizedBox(width: 8),
        Expanded(
          child: DatoClave(
            etiqueta: 'Liquidaciones pendientes',
            valor: '${r.liquidacionesPendientes}',
            color: r.liquidacionesPendientes > 0 ? HuataColors.blue : null,
            fondo: r.liquidacionesPendientes > 0 ? HuataColors.blueSoft : null,
          ),
        ),
      ]),
      if (r.compraEstimada != null) ...[
        const SizedBox(height: 8),
        DatoClave(etiqueta: 'Compra estimada del día', valor: soles(r.compraEstimada), icono: Icons.payments_outlined),
      ],
      const SizedBox(height: 12),
      if (t != null)
        SeccionCard(
          icono: Icons.compare_arrows,
          titulo: 'Recogido frente a recibido en planta',
          subtitulo: t.rutasSinRecibir.isEmpty
              ? 'Todas las rutas tienen su recepción anotada.'
              : 'Falta anotar la llegada de: ${t.rutasSinRecibir.join(', ')}',
          child: Column(children: [
            FilaDato('Recogido (rutas)', litros(t.recogido)),
            FilaDato('Recibido en planta', litros(t.recibidoPlanta)),
            // Una diferencia se registra tal cual: no se asume pérdida ni robo (CLAUDE.md §5).
            FilaDato('Diferencia', litros(t.recogido - t.recibidoPlanta), destacado: true),
          ]),
        ),
      if (t != null) const SizedBox(height: 12),
      SeccionCard(
        icono: Icons.verified_outlined,
        titulo: 'Calidad del día',
        subtitulo: 'Por entregas; abajo, los litros de cada calidad.',
        child: Column(children: [
          GraficoDona(
            textoCentro: 'entregas',
            tramos: [
              TramoDona(etiqueta: 'Buena', valor: r.entregasBuenas, color: colorCalidad('ACEPTADA')),
              TramoDona(etiqueta: 'Observada', valor: r.entregasObservadas, color: colorCalidad('OBSERVADA')),
              TramoDona(etiqueta: 'Mala', valor: r.entregasRechazadas, color: colorCalidad('RECHAZADA')),
              TramoDona(etiqueta: 'Sin análisis', valor: r.pendientesCalidad, color: colorCalidad('PENDIENTE')),
            ],
          ),
          const SizedBox(height: 10),
          FilaDato('Litros de buena calidad', litros(r.litrosBuenos)),
          FilaDato('Litros observados', litros(r.litrosObservados)),
          FilaDato('Litros de mala calidad', litros(r.litrosRechazados)),
        ]),
      ),
      if (analizados == 0 && (_analisis?.isEmpty ?? true))
        const Padding(
          padding: EdgeInsets.only(top: 6, left: 4),
          child: Text('Aún no hay análisis de este día.', style: TextStyle(color: HuataColors.muted, fontSize: 12.5)),
        ),
      if (_analisis != null && _analisis!.isNotEmpty) ...[
        const SizedBox(height: 12),
        SeccionCard(
          icono: Icons.science_outlined,
          titulo: 'Análisis de calidad (${_analisis!.length})',
          subtitulo: _subtituloAnalisis(_analisis!),
          child: Column(children: [
            for (final a in _analisis!)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                title: Text(a.productorNombre, style: const TextStyle(fontWeight: FontWeight.w600)),
                subtitle: Text([
                  fechaHoraCorta(a.fechaHora?.toIso8601String()),
                  if (a.densidad != null) 'Dens. ${a.densidad!.toStringAsFixed(a.densidad! < 2 ? 4 : 1)}',
                  if (a.grasa != null) 'Grasa ${a.grasa!.toStringAsFixed(2)} %',
                  a.aprobado ? 'aprobado' : 'por aprobar',
                ].join(' · ')),
                trailing: CalidadChip(resultado: a.resultado),
              ),
          ]),
        ),
      ],
      const SizedBox(height: 12),
      SeccionCard(
        icono: Icons.bar_chart_rounded,
        titulo: 'Litros por ruta',
        subtitulo: rutas.isEmpty ? 'Sin recojos registrados este día.' : null,
        child: rutas.isEmpty
            ? const SizedBox.shrink()
            : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (rutas.length <= 10)
                  GraficoBarras(barras: [
                    for (final ruta in rutas)
                      BarraGrafico(
                        etiqueta: _corto(ruta.nombre),
                        valor: ruta.litros,
                        color: HuataColors.green2,
                        detalle: '${ruta.nombre}: ${litros(ruta.litros)}, ${ruta.recepciones} entregas',
                      ),
                  ]),
                const SizedBox(height: 8),
                for (final ruta in rutas)
                  FilaDato('${ruta.nombre} · ${ruta.recepciones} entregas', litros(ruta.litros)),
              ]),
      ),
      const SizedBox(height: 12),
      if (r.cierre != null)
        EstadoAviso(
          esError: false,
          mensaje: r.cierre!.cerrado
              ? 'Jornada cerrada: sistema ${litros(r.cierre!.litrosSistema)} · planta ${litros(r.cierre!.litrosPlanta)}.'
              : 'La jornada de este día sigue abierta.',
        ),
    ];
  }

  static String _subtituloAnalisis(List<AnalisisPorAprobar> lista) {
    final pendientes = lista.where((a) => !a.aprobado).length;
    return pendientes == 0 ? 'Todos aprobados.' : '$pendientes por aprobar en "Aprobar análisis".';
  }

  static String _corto(String nombre) => nombre.length <= 8 ? nombre : '${nombre.substring(0, 7)}…';
}
