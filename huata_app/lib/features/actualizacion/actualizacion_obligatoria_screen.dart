import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/actualizacion/actualizacion_service.dart';
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

/// La versión instalada es menor que la mínima que acepta el servidor: no se deja trabajar, porque lo
/// que registre podría no entenderlo el backend. Lo pendiente de enviar no se pierde: sigue en el
/// teléfono y sale apenas se instale la versión nueva.
class ActualizacionObligatoriaScreen extends StatelessWidget {
  const ActualizacionObligatoriaScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final act = context.watch<ActualizacionService>();
    return Scaffold(
      body: DecoratedBox(
        decoration: const BoxDecoration(gradient: HuataMarca.degradado),
        child: SafeArea(
          child: Center(
            child: SingleChildScrollView(
              padding: const EdgeInsets.all(24),
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 420),
                child: Container(
                  padding: const EdgeInsets.all(24),
                  decoration: BoxDecoration(color: HuataColors.panel, borderRadius: BorderRadius.circular(22)),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Center(child: LogoHuata(tamano: 56, claro: false)),
                      const SizedBox(height: 18),
                      const Text(
                        'Actualiza la aplicación',
                        textAlign: TextAlign.center,
                        style: TextStyle(fontSize: 21, fontWeight: FontWeight.w800),
                      ),
                      const SizedBox(height: 8),
                      Text(
                        'Tu versión (${act.versionInstalada ?? '—'}) ya no es compatible. '
                        'Instala la ${act.publicada?.versionActual ?? 'nueva'} para seguir trabajando. '
                        'Lo que tengas por enviar no se pierde.',
                        textAlign: TextAlign.center,
                        style: const TextStyle(color: HuataColors.muted, height: 1.45),
                      ),
                      if (act.publicada?.notas != null) ...[
                        const SizedBox(height: 14),
                        EstadoAviso(mensaje: act.publicada!.notas!),
                      ],
                      const SizedBox(height: 20),
                      ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                        onPressed: act.descargar,
                        icon: const Icon(Icons.download),
                        label: const Text('Descargar e instalar'),
                      ),
                      const SizedBox(height: 8),
                      TextButton(
                        onPressed: act.revisando ? null : act.revisar,
                        child: Text(act.revisando ? 'Revisando…' : 'Ya la instalé, volver a revisar'),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
