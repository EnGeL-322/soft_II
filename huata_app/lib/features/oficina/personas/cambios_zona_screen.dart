import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/personas_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/productor.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

const _estados = {'PENDIENTE': 'Pendientes', 'APROBADA': 'Aprobadas', 'RECHAZADA': 'Rechazadas'};

/// Vista "Cambios de zona" de la web (proveedores.read): lo que piden los proveedores desde su app.
/// Con proveedores.write se aprueba o rechaza; el proveedor lo ve al momento en su celular.
class CambiosZonaScreen extends StatefulWidget {
  const CambiosZonaScreen({super.key});

  @override
  State<CambiosZonaScreen> createState() => _CambiosZonaScreenState();
}

class _CambiosZonaScreenState extends State<CambiosZonaScreen> with EnVivo<CambiosZonaScreen> {
  String? _estado = 'PENDIENTE';
  bool _cargando = true;
  String? _error;
  List<SolicitudCambioZona> _filas = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'proveedores'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final s = await context.read<PersonasApi>().solicitudesZona(estado: _estado);
      if (!mounted) return;
      setState(() {
        _filas = s..sort((a, b) => (b.fechaSolicitud ?? '').compareTo(a.fechaSolicitud ?? ''));
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

  Future<void> _decidir(SolicitudCambioZona s, {required bool aprobar}) async {
    final obs = TextEditingController();
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(aprobar ? 'Aprobar cambio de zona' : 'Rechazar cambio de zona'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('${s.productorNombre}: ${s.zonaActual ?? 'sin zona'} → ${s.zonaSolicitada}.'),
          const SizedBox(height: 8),
          TextField(controller: obs, maxLength: 300, decoration: InputDecoration(labelText: aprobar ? 'Observación (opcional)' : 'Motivo del rechazo')),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(
            style: aprobar ? null : FilledButton.styleFrom(backgroundColor: HuataColors.red),
            onPressed: () => Navigator.pop(ctx, true),
            child: Text(aprobar ? 'Aprobar' : 'Rechazar'),
          ),
        ],
      ),
    );
    if (ok != true || !mounted) return;
    try {
      await context.read<PersonasApi>().decidirSolicitudZona(s.id, aprobar: aprobar, observacion: obs.text);
      if (!mounted) return;
      avisar(context, aprobar ? 'Cambio de zona aprobado.' : 'Solicitud rechazada.');
      _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  @override
  Widget build(BuildContext context) {
    final escribe = puede(context, 'proveedores.write');
    return HuataScaffold(
      titulo: 'Cambios de zona',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay solicitudes en este estado.',
        cabecera: [
          FiltroPildoras<String?>(
            opciones: [for (final e in _estados.entries) (e.key, e.value), (null, 'Todas')],
            valor: _estado,
            alCambiar: (v) {
              setState(() => _estado = v);
              _cargar();
            },
          ),
        ],
        hijos: [
          for (final s in _filas)
            SeccionCard(
              icono: Icons.swap_horiz,
              titulo: s.productorNombre,
              subtitulo: '${s.zonaActual ?? 'Sin zona'} → ${s.zonaSolicitada} · ${fechaCorta(s.fechaSolicitud)}',
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                if (s.motivo != null && s.motivo!.isNotEmpty) FilaDato('Motivo', s.motivo!),
                if (s.estado != 'PENDIENTE') ...[
                  FilaDato('Decisión', s.estado == 'APROBADA' ? 'Aprobada' : 'Rechazada', color: s.estado == 'APROBADA' ? HuataColors.green : HuataColors.red),
                  if (s.aprobador != null) FilaDato('Por', '${s.aprobador} · ${fechaCorta(s.fechaDecision)}'),
                  if (s.observacionDecision != null) FilaDato('Observación', s.observacionDecision!),
                ],
                if (escribe && s.estado == 'PENDIENTE') ...[
                  const SizedBox(height: 8),
                  Row(children: [
                    Expanded(child: OutlinedButton(onPressed: () => _decidir(s, aprobar: false), child: const Text('Rechazar'))),
                    const SizedBox(width: 8),
                    Expanded(child: FilledButton(onPressed: () => _decidir(s, aprobar: true), child: const Text('Aprobar'))),
                  ]),
                ],
              ]),
            ),
        ].expand((w) => [w, const SizedBox(height: 10)]).toList(),
      ),
    );
  }
}
