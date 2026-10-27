import 'package:flutter/material.dart';

import '../../core/models/ruta.dart';
import '../../core/sync/sync_service.dart';
import '../../core/theme/huata_theme.dart';
import 'parada_ruta.dart';

/// Cabecera de la jornada: ruta y fecha, litros acopiados, avance de las paradas y el botón del
/// recorrido GPS. Es lo único fijo arriba; el resto de la pantalla es mapa o lista.
class CabeceraJornada extends StatelessWidget {
  const CabeceraJornada({
    super.key,
    required this.asignacion,
    required this.paradas,
    required this.totalLitros,
    required this.sync,
    required this.compartiendoGps,
    required this.onAlternarGps,
    this.compacta = false,
  });

  final AsignacionRuta asignacion;
  final List<ParadaRuta> paradas;
  final double totalLitros;
  final SyncService sync;
  final bool compartiendoGps;
  final VoidCallback onAlternarGps;

  /// En la vista de mapa la cabecera se reduce a una franja para que el mapa sea el protagonista.
  final bool compacta;

  @override
  Widget build(BuildContext context) {
    final total = paradas.length;
    final acopiados = paradas.where((p) => p.estado == EstadoVisita.acopiado).length;
    final noEntregados = paradas.where((p) => p.estado == EstadoVisita.noEntregado).length;
    final visitados = acopiados + noEntregados;
    final avance = total == 0 ? 0.0 : visitados / total;
    if (compacta) {
      return _CabeceraCompacta(
        asignacion: asignacion,
        visitados: visitados,
        total: total,
        avance: avance,
        totalLitros: totalLitros,
        sync: sync,
        compartiendoGps: compartiendoGps,
        onAlternarGps: onAlternarGps,
      );
    }
    final detalleRuta = [
      if ((asignacion.zona ?? '').isNotEmpty) asignacion.zona!,
      if ((asignacion.movilidad ?? '').isNotEmpty) asignacion.movilidad!,
    ].join(' · ');

    return Container(
      padding: const EdgeInsets.fromLTRB(16, 14, 16, 14),
      decoration: BoxDecoration(
        gradient: HuataMarca.degradado,
        borderRadius: BorderRadius.circular(20),
        boxShadow: const [BoxShadow(color: Color(0x331D4F91), blurRadius: 16, offset: Offset(0, 6))],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      'RUTA DE HOY · ${fechaLarga(DateTime.now()).toUpperCase()}',
                      style: const TextStyle(color: Color(0xB3FFFFFF), fontSize: 10.5, fontWeight: FontWeight.w700, letterSpacing: 0.7),
                    ),
                    const SizedBox(height: 3),
                    Text(
                      asignacion.rutaNombre,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontSize: 18, fontWeight: FontWeight.w700, letterSpacing: -0.2),
                    ),
                    if (detalleRuta.isNotEmpty)
                      Text(
                        detalleRuta,
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                        style: const TextStyle(color: Color(0xCCFFFFFF), fontSize: 12.5),
                      ),
                  ],
                ),
              ),
              const SizedBox(width: 8),
              IndicadorSincronizacion(sync: sync),
            ],
          ),
          const SizedBox(height: 14),
          Row(
            crossAxisAlignment: CrossAxisAlignment.end,
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    FittedBox(
                      fit: BoxFit.scaleDown,
                      alignment: Alignment.centerLeft,
                      child: Text(
                        formatoLitros(totalLitros),
                        style: const TextStyle(color: Colors.white, fontSize: 30, fontWeight: FontWeight.w800, letterSpacing: -0.8, height: 1.05),
                      ),
                    ),
                    const Text('acopiados hoy', style: TextStyle(color: Color(0xCCFFFFFF), fontSize: 12)),
                  ],
                ),
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Text(
                    '$visitados/$total',
                    style: const TextStyle(color: Colors.white, fontSize: 20, fontWeight: FontWeight.w800),
                  ),
                  const Text('paradas visitadas', style: TextStyle(color: Color(0xCCFFFFFF), fontSize: 12)),
                ],
              ),
            ],
          ),
          const SizedBox(height: 10),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: TweenAnimationBuilder<double>(
              tween: Tween(begin: 0, end: avance),
              duration: const Duration(milliseconds: 500),
              curve: Curves.easeOutCubic,
              builder: (_, valor, _) => LinearProgressIndicator(
                value: valor,
                minHeight: 7,
                backgroundColor: const Color(0x33FFFFFF),
                valueColor: const AlwaysStoppedAnimation(Color(0xFF7DD3FC)),
              ),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              _Conteo(color: const Color(0xFF7DD3FC), texto: '$acopiados acopiados'),
              const SizedBox(width: 12),
              _Conteo(color: const Color(0xFFFF9C9C), texto: '$noEntregados no entregaron'),
              const SizedBox(width: 12),
              _Conteo(color: Colors.white, texto: '${total - visitados} pendientes'),
            ],
          ),
          const SizedBox(height: 12),
          _BotonRecorrido(activo: compartiendoGps, onPressed: onAlternarGps),
        ],
      ),
    );
  }
}

class _CabeceraCompacta extends StatelessWidget {
  const _CabeceraCompacta({
    required this.asignacion,
    required this.visitados,
    required this.total,
    required this.avance,
    required this.totalLitros,
    required this.sync,
    required this.compartiendoGps,
    required this.onAlternarGps,
  });

  final AsignacionRuta asignacion;
  final int visitados;
  final int total;
  final double avance;
  final double totalLitros;
  final SyncService sync;
  final bool compartiendoGps;
  final VoidCallback onAlternarGps;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.fromLTRB(14, 10, 10, 10),
      decoration: BoxDecoration(
        gradient: HuataMarca.degradado,
        borderRadius: BorderRadius.circular(18),
        boxShadow: const [BoxShadow(color: Color(0x331D4F91), blurRadius: 14, offset: Offset(0, 5))],
      ),
      child: Column(
        children: [
          Row(
            children: [
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      asignacion.rutaNombre,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Colors.white, fontSize: 15, fontWeight: FontWeight.w700),
                    ),
                    const SizedBox(height: 1),
                    Text(
                      '${formatoLitros(totalLitros)} · $visitados de $total paradas',
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(color: Color(0xD9FFFFFF), fontSize: 12.5, fontWeight: FontWeight.w500),
                    ),
                  ],
                ),
              ),
              IndicadorSincronizacion(sync: sync),
              const SizedBox(width: 8),
              Tooltip(
                message: compartiendoGps ? 'Recorrido en curso. Toca para detener.' : 'Iniciar recorrido',
                child: Material(
                  color: compartiendoGps ? const Color(0x26FFFFFF) : Colors.white,
                  shape: const CircleBorder(),
                  child: InkWell(
                    customBorder: const CircleBorder(),
                    onTap: onAlternarGps,
                    child: SizedBox(
                      width: 40,
                      height: 40,
                      child: compartiendoGps
                          ? const Center(child: _PuntoEnVivo())
                          : const Icon(Icons.play_arrow_rounded, color: HuataColors.green),
                    ),
                  ),
                ),
              ),
            ],
          ),
          const SizedBox(height: 9),
          ClipRRect(
            borderRadius: BorderRadius.circular(999),
            child: LinearProgressIndicator(
              value: avance,
              minHeight: 5,
              backgroundColor: const Color(0x33FFFFFF),
              valueColor: const AlwaysStoppedAnimation(Color(0xFF7DD3FC)),
            ),
          ),
        ],
      ),
    );
  }
}

class _Conteo extends StatelessWidget {
  const _Conteo({required this.color, required this.texto});
  final Color color;
  final String texto;

  @override
  Widget build(BuildContext context) {
    return Flexible(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 8, height: 8, decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
          const SizedBox(width: 5),
          Flexible(
            child: Text(
              texto,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Color(0xE6FFFFFF), fontSize: 11.5, fontWeight: FontWeight.w500),
            ),
          ),
        ],
      ),
    );
  }
}

/// Iniciar recorrido (blanco, acción principal) o recorrido en curso (con punto que late).
class _BotonRecorrido extends StatelessWidget {
  const _BotonRecorrido({required this.activo, required this.onPressed});
  final bool activo;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    if (!activo) {
      return SizedBox(
        width: double.infinity,
        child: FilledButton.icon(
          onPressed: onPressed,
          style: FilledButton.styleFrom(
            backgroundColor: Colors.white,
            foregroundColor: HuataColors.green,
            minimumSize: const Size(0, 46),
          ),
          icon: const Icon(Icons.play_arrow_rounded),
          label: const Text('Iniciar recorrido'),
        ),
      );
    }
    return Material(
      color: const Color(0x26FFFFFF),
      borderRadius: BorderRadius.circular(12),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: onPressed,
        child: Container(
          height: 46,
          padding: const EdgeInsets.symmetric(horizontal: 14),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(12),
            border: Border.all(color: const Color(0x4DFFFFFF)),
          ),
          child: const Row(
            children: [
              _PuntoEnVivo(),
              SizedBox(width: 10),
              Expanded(
                child: Text(
                  'Recorrido en curso · compartiendo ubicación',
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 13.5),
                ),
              ),
              Text('Detener', style: TextStyle(color: Color(0xCCFFFFFF), fontWeight: FontWeight.w600, fontSize: 13)),
            ],
          ),
        ),
      ),
    );
  }
}

class _PuntoEnVivo extends StatefulWidget {
  const _PuntoEnVivo();

  @override
  State<_PuntoEnVivo> createState() => _PuntoEnVivoState();
}

class _PuntoEnVivoState extends State<_PuntoEnVivo> with SingleTickerProviderStateMixin {
  late final AnimationController _control = AnimationController(vsync: this, duration: const Duration(milliseconds: 1400))
    ..repeat();

  @override
  void dispose() {
    _control.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: 18,
      height: 18,
      child: AnimatedBuilder(
        animation: _control,
        builder: (_, _) => Stack(
          alignment: Alignment.center,
          children: [
            Transform.scale(
              scale: 0.5 + _control.value,
              child: Container(
                width: 18,
                height: 18,
                decoration: BoxDecoration(
                  shape: BoxShape.circle,
                  color: const Color(0xFF7DD3FC).withValues(alpha: 0.5 * (1 - _control.value)),
                ),
              ),
            ),
            Container(
              width: 9,
              height: 9,
              decoration: const BoxDecoration(color: Color(0xFF7DD3FC), shape: BoxShape.circle),
            ),
          ],
        ),
      ),
    );
  }
}

/// Estado de la sincronización automática (no es un botón: la app sube sola lo pendiente al
/// recuperar señal). Al tocarlo explica qué está guardado en el teléfono.
class IndicadorSincronizacion extends StatelessWidget {
  const IndicadorSincronizacion({super.key, required this.sync});
  final SyncService sync;

  @override
  Widget build(BuildContext context) {
    final recojos = sync.pendientesRecojo;
    final puntos = sync.pendientesUbicacion;
    final trazado = sync.pendientesTrazado;
    final hayPendientes = recojos > 0 || puntos > 0 || trazado > 0;
    final (icono, texto, fondo) = sync.sincronizando
        ? (Icons.cloud_upload_outlined, 'Enviando…', const Color(0x33FFFFFF))
        : hayPendientes
        ? (Icons.cloud_off_outlined, recojos > 0 ? '$recojos sin enviar' : 'Sin señal', const Color(0x66A15C14))
        : (Icons.cloud_done_outlined, 'Al día', const Color(0x26FFFFFF));

    final detalle = hayPendientes
        ? [
            if (recojos > 0) '$recojos recojo(s)',
            if (puntos > 0) '$puntos punto(s) del recorrido',
            if (trazado > 0) 'el recorrido pintado',
          ].join(', ')
        : null;

    return Tooltip(
      triggerMode: TooltipTriggerMode.tap,
      showDuration: const Duration(seconds: 4),
      message: detalle == null
          ? 'Todo lo registrado ya llegó a la planta.'
          : 'Guardado en el teléfono: $detalle. Se enviará solo cuando haya señal.',
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 250),
        padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
        decoration: BoxDecoration(
          color: fondo,
          borderRadius: BorderRadius.circular(999),
          border: Border.all(color: const Color(0x40FFFFFF)),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icono, size: 15, color: Colors.white),
            const SizedBox(width: 5),
            Text(texto, style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600)),
          ],
        ),
      ),
    );
  }
}
