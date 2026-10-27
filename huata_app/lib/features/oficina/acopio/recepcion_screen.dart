import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/api/acopio_oficina_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/acopio_oficina.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';
import '../../proveedor/proveedor_comun.dart' show CalidadChip;

/// Vista "Recepción de rutas" de la web (acopio.read): cuánto recogió cada ruta y cuánto llegó a
/// planta. Con planta.recepcion se anota (o corrige) lo recibido. La diferencia se registra tal cual:
/// no se asume pérdida ni robo (CLAUDE.md §5).
class RecepcionScreen extends StatefulWidget {
  const RecepcionScreen({super.key});

  @override
  State<RecepcionScreen> createState() => _RecepcionScreenState();
}

class _RecepcionScreenState extends State<RecepcionScreen> with EnVivo<RecepcionScreen> {
  DateTime _dia = DateTime.now();
  bool _cargando = true;
  String? _error;
  List<ConciliacionRuta> _rutas = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'acopio'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final dia = _dia;
    try {
      final rutas = await context.read<AcopioOficinaApi>().conciliaciones(dia);
      if (!mounted || dia != _dia) return;
      setState(() {
        // Las entregas directas ya están en planta: una ruta que solo tiene esas no se "recibe".
        _rutas = rutas.where((r) => r.requiereRecepcion).toList()..sort((a, b) => a.rutaNombre.compareTo(b.rutaNombre));
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

  Future<void> _anotar(ConciliacionRuta ruta) async {
    final litrosCtrl = TextEditingController(text: ruta.litrosRecibidosPlanta?.toStringAsFixed(1) ?? '');
    final obsCtrl = TextEditingController(text: ruta.observacion ?? '');
    final guardar = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text('Llegada de ${ruta.rutaNombre}'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('En ruta: ${litros(ruta.litrosEnRuta)}', style: const TextStyle(color: HuataColors.muted)),
          const SizedBox(height: 12),
          TextField(
            controller: litrosCtrl,
            autofocus: true,
            keyboardType: const TextInputType.numberWithOptions(decimal: true),
            inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
            decoration: const InputDecoration(labelText: 'Litros recibidos en planta', suffixText: 'L'),
          ),
          const SizedBox(height: 8),
          TextField(
            controller: obsCtrl,
            maxLength: 300,
            decoration: const InputDecoration(labelText: 'Observación (opcional)'),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Guardar')),
        ],
      ),
    );
    final valor = double.tryParse(litrosCtrl.text.replaceAll(',', '.'));
    if (guardar != true || !mounted) return;
    if (valor == null || valor < 0) {
      avisar(context, 'Escribe los litros recibidos (un número mayor o igual a 0).');
      return;
    }
    final ok = await confirmar(
      context,
      titulo: 'Confirmar recepción',
      mensaje: '${ruta.rutaNombre}: ${litros(valor)} recibidos en planta '
          '(en ruta ${litros(ruta.litrosEnRuta)}).',
    );
    if (!ok || !mounted) return;
    try {
      await context.read<AcopioOficinaApi>().registrarRecepcion(
        dia: _dia,
        rutaId: ruta.rutaId,
        acopiadorId: ruta.acopiadorId,
        litrosRecibidos: valor,
        observacion: obsCtrl.text,
      );
      if (!mounted) return;
      avisar(context, 'Recepción guardada.');
      _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final puedeAnotar = puede(context, 'planta.recepcion');
    final enRuta = _rutas.fold(0.0, (s, r) => s + r.litrosEnRuta);
    final recibido = _rutas.fold(0.0, (s, r) => s + (r.litrosRecibidosPlanta ?? 0));
    final pendientes = _rutas.where((r) => !r.recibida && r.litrosEnRuta > 0).length;
    return HuataScaffold(
      titulo: 'Recepción de rutas',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay rutas con recojos este día.',
        cabecera: [
          SelectorDia(
            dia: _dia,
            alCambiar: (d) {
              setState(() => _dia = d);
              _cargar();
            },
          ),
          if (!_cargando && _error == null && _rutas.isNotEmpty) ...[
            Row(children: [
              Expanded(child: DatoClave(etiqueta: 'En ruta', valor: litros(enRuta))),
              const SizedBox(width: 8),
              Expanded(child: DatoClave(etiqueta: 'Recibido', valor: litros(recibido))),
              const SizedBox(width: 8),
              Expanded(
                child: DatoClave(
                  etiqueta: 'Por anotar',
                  valor: '$pendientes',
                  color: pendientes > 0 ? HuataColors.amber : null,
                  fondo: pendientes > 0 ? HuataColors.amberSoft : null,
                ),
              ),
            ]),
            const SizedBox(height: 12),
          ],
        ],
        hijos: [for (final r in _rutas) _TarjetaRuta(ruta: r, alAnotar: puedeAnotar ? () => _anotar(r) : null)],
      ),
    );
  }
}

class _TarjetaRuta extends StatelessWidget {
  const _TarjetaRuta({required this.ruta, this.alAnotar});
  final ConciliacionRuta ruta;
  final VoidCallback? alAnotar;

  @override
  Widget build(BuildContext context) {
    final r = ruta;
    return Card(
      margin: const EdgeInsets.only(bottom: 10),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        shape: const Border(),
        collapsedShape: const Border(),
        title: Text(r.rutaNombre, style: const TextStyle(fontWeight: FontWeight.w700)),
        subtitle: Text([
          if (r.zona != null) r.zona!,
          if (r.acopiadorNombre != null) r.acopiadorNombre!,
          '${r.proveedoresAcopiados} entregas',
        ].join(' · ')),
        trailing: EtiquetaEstado(
          texto: r.recibida ? 'Recibida' : 'Por anotar',
          color: r.recibida ? HuataColors.green : HuataColors.amber,
          fondo: r.recibida ? HuataColors.greenSoft : HuataColors.amberSoft,
        ),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        children: [
          FilaDato('En ruta (camión)', litros(r.litrosEnRuta)),
          if (r.litrosEntregadosEnPlanta > 0) FilaDato('Entregas directas (ya en planta)', litros(r.litrosEntregadosEnPlanta)),
          FilaDato('Recibido en planta', litros(r.litrosRecibidosPlanta), destacado: true),
          if (r.diferencia != null) FilaDato('Diferencia', litros(r.diferencia)),
          if (r.proveedoresNoEntregaron > 0) FilaDato('No entregaron', '${r.proveedoresNoEntregaron}'),
          if (r.responsable != null) FilaDato('Recibió', r.responsable!),
          if (r.observacion != null) FilaDato('Observación', r.observacion!),
          if (alAnotar != null)
            Align(
              alignment: Alignment.centerRight,
              child: Padding(
                padding: const EdgeInsets.only(top: 8),
                child: FilledButton.icon(
                  onPressed: alAnotar,
                  icon: Icon(r.recibida ? Icons.edit_outlined : Icons.add),
                  label: Text(r.recibida ? 'Corregir llegada' : 'Anotar llegada'),
                ),
              ),
            ),
          if (r.detalle.isNotEmpty) ...[
            const Divider(height: 24),
            for (final v in r.detalle)
              ListTile(
                dense: true,
                contentPadding: EdgeInsets.zero,
                title: Text(v.productorNombre ?? v.productorCodigo ?? 'Proveedor'),
                subtitle: Text(v.estadoVisita == 'NO_ENTREGADO'
                    ? 'No entregó${v.motivoNoEntrega != null ? ': ${v.motivoNoEntrega}' : ''}'
                    : '${litros(v.litros)} · ${fechaHoraCorta(v.fechaHora)}'),
                trailing: v.estadoVisita == 'NO_ENTREGADO' ? null : CalidadChip(resultado: v.calidad ?? 'PENDIENTE'),
              ),
          ],
        ],
      ),
    );
  }
}
