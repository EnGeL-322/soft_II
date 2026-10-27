import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/productores_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/models/productor.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';
import 'solicitud_zona_sheet.dart';
import '../../core/tiempo_real/cambios_en_vivo.dart';

/// Módulo "Cambio de zona" del proveedor (2.5, RF-58/82, 7.4): su zona actual, el botón para pedir
/// el cambio y el historial de todas sus solicitudes con la respuesta de la planta.
///
/// La regla de una sola solicitud esperando respuesta es del backend (la misma del IVR); aquí solo
/// se refleja para no dejar enviar algo que el servidor va a rechazar.
class CambioZonaScreen extends StatefulWidget {
  const CambioZonaScreen({super.key});

  @override
  State<CambioZonaScreen> createState() => _CambioZonaScreenState();
}

class _CambioZonaScreenState extends State<CambioZonaScreen> with EnVivo<CambioZonaScreen> {
  bool _cargando = true;
  String? _error;
  Productor? _yo;
  List<SolicitudCambioZona> _solicitudes = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'proveedores', 'rutas'}, () => _cargar(silencioso: true));
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
      final api = context.read<ProductoresApi>();
      final yo = await api.yo();
      final solicitudes = await api.misSolicitudesZona();
      solicitudes.sort((a, b) => (b.fechaSolicitud ?? '').compareTo(a.fechaSolicitud ?? ''));
      if (!mounted) return;
      setState(() {
        _yo = yo;
        _solicitudes = solicitudes;
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

  SolicitudCambioZona? get _pendiente => _solicitudes.where((s) => s.estado == 'PENDIENTE').firstOrNull;

  Future<void> _solicitar() async {
    final yo = _yo;
    if (yo == null) return;
    final enviado = await showModalBottomSheet<bool>(
      context: context,
      isScrollControlled: true,
      builder: (_) => SolicitudZonaSheet(productorId: yo.id, zonaActual: yo.zonaPrincipal),
    );
    if (enviado == true && mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Solicitud de cambio de zona enviada. Administración la revisará.')),
      );
      _cargar();
    }
  }

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Cambio de zona',
      body: RefreshIndicator(
        onRefresh: _cargar,
        child: _cargando
            ? const Center(child: CircularProgressIndicator())
            : _error != null
            ? ListView(
                padding: const EdgeInsets.all(24),
                children: [
                  EstadoAviso(mensaje: _error!, esError: true),
                  OutlinedButton(onPressed: _cargar, child: const Text('Reintentar')),
                ],
              )
            : _contenido(),
      ),
    );
  }

  Widget _contenido() {
    final yo = _yo!;
    final pendiente = _pendiente;
    final aprobadas = _solicitudes.where((s) => s.estado == 'APROBADA').length;
    final rechazadas = _solicitudes.where((s) => s.estado == 'RECHAZADA').length;

    return ListView(
      // Siempre desplazable: así "jalar para actualizar" funciona aunque el historial esté vacío.
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.all(16),
      children: [
        SeccionCard(
          icono: Icons.place_outlined,
          titulo: 'Mi zona actual',
          subtitulo: 'La zona y ruta en la que hoy te recogen la leche',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(children: [
                Expanded(child: DatoClave(etiqueta: 'Zona', valor: yo.zonaPrincipal ?? '—')),
                const SizedBox(width: 8),
                Expanded(child: DatoClave(etiqueta: 'Ruta', valor: yo.rutaNombre ?? '—')),
              ]),
              const SizedBox(height: 14),
              if (pendiente != null)
                EstadoAviso(
                  mensaje: 'Tu pedido para pasar a ${pendiente.zonaSolicitada} está en revisión. '
                      'Podrás enviar otro cuando la planta responda.',
                ),
              ElevatedButton.icon(
                onPressed: pendiente == null ? _solicitar : null,
                icon: const Icon(Icons.edit_location_alt_outlined),
                label: const Text('Solicitar cambio de zona'),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),
        SeccionCard(
          icono: Icons.history,
          titulo: 'Historial de solicitudes',
          subtitulo: 'Todo lo que pediste y lo que respondió la planta',
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(children: [
                Expanded(child: DatoClave(etiqueta: 'Enviadas', valor: '${_solicitudes.length}')),
                const SizedBox(width: 8),
                Expanded(
                  child: DatoClave(
                    etiqueta: 'Aprobadas',
                    valor: '$aprobadas',
                    color: HuataColors.green,
                    fondo: HuataColors.greenSoft,
                  ),
                ),
                const SizedBox(width: 8),
                Expanded(
                  child: DatoClave(
                    etiqueta: 'Rechazadas',
                    valor: '$rechazadas',
                    color: HuataColors.red,
                    fondo: HuataColors.redSoft,
                  ),
                ),
              ]),
              const Divider(height: 28),
              if (_solicitudes.isEmpty)
                const Padding(
                  padding: EdgeInsets.symmetric(vertical: 12),
                  child: Text('Aún no has pedido ningún cambio de zona.', style: TextStyle(color: HuataColors.muted)),
                )
              else
                ..._solicitudes.map((s) => _SolicitudCard(solicitud: s)),
            ],
          ),
        ),
      ],
    );
  }
}

/// Una solicitud del historial: de qué zona a cuál, cuándo, el motivo y, si ya se decidió, quién y
/// con qué observación. Se abre para ver el detalle, como los días del historial de producción.
class _SolicitudCard extends StatelessWidget {
  const _SolicitudCard({required this.solicitud});
  final SolicitudCambioZona solicitud;

  @override
  Widget build(BuildContext context) {
    final s = solicitud;
    final detalle = <(IconData, String, String)>[
      (Icons.schedule, 'Enviada', _fechaHora(s.fechaSolicitud)),
      if (s.motivo != null && s.motivo!.isNotEmpty) (Icons.notes, 'Motivo', s.motivo!),
      if (s.fechaDecision != null) (Icons.event_available_outlined, 'Respondida', _fechaHora(s.fechaDecision)),
      if (s.aprobador != null) (Icons.person_outline, 'Revisó', s.aprobador!),
      if (s.observacionDecision != null && s.observacionDecision!.isNotEmpty)
        (Icons.comment_outlined, 'Observación', s.observacionDecision!),
    ];
    return Card(
      margin: const EdgeInsets.only(bottom: 8),
      clipBehavior: Clip.antiAlias,
      child: ExpansionTile(
        shape: const Border(),
        collapsedShape: const Border(),
        leading: Container(
          width: 36,
          height: 36,
          decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(10)),
          child: const Icon(Icons.swap_horiz, size: 19, color: HuataColors.green),
        ),
        title: Text(
          '${s.zonaActual ?? '—'} → ${s.zonaSolicitada}',
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        subtitle: Text(_fechaHora(s.fechaSolicitud)),
        trailing: _estado(s.estado),
        childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
        children: [
          for (final (icono, etiqueta, valor) in detalle)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 4),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Icon(icono, size: 16, color: HuataColors.muted),
                  const SizedBox(width: 8),
                  SizedBox(
                    width: 92,
                    child: Text(etiqueta, style: const TextStyle(color: HuataColors.muted, fontSize: 13)),
                  ),
                  Expanded(child: Text(valor, style: const TextStyle(fontSize: 13.5))),
                ],
              ),
            ),
        ],
      ),
    );
  }

  static Widget _estado(String estado) => switch (estado) {
    'APROBADA' => const EtiquetaEstado(texto: 'Aprobada', color: HuataColors.green, fondo: HuataColors.greenSoft),
    'RECHAZADA' => const EtiquetaEstado(texto: 'Rechazada', color: HuataColors.red, fondo: HuataColors.redSoft),
    'PENDIENTE' => const EtiquetaEstado(texto: 'En revisión', color: HuataColors.amber, fondo: HuataColors.amberSoft),
    _ => EtiquetaEstado(texto: estado, color: HuataColors.gray, fondo: HuataColors.graySoft),
  };

  /// El backend manda fecha-hora ISO; se muestra como dd/mm/aaaa hh:mm en hora local.
  static String _fechaHora(String? iso) {
    if (iso == null || iso.isEmpty) return '—';
    final d = DateTime.tryParse(iso)?.toLocal();
    if (d == null) return iso;
    String dos(int n) => n.toString().padLeft(2, '0');
    return '${dos(d.day)}/${dos(d.month)}/${d.year} ${dos(d.hour)}:${dos(d.minute)}';
  }
}
