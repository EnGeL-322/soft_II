import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../core/api/productores_api.dart';
import '../../core/api/rutas_api.dart' show mensajeDeError;
import '../../core/theme/huata_theme.dart';
import '../../shared/huata_scaffold.dart';

/// Formulario de cambio de zona del proveedor (2.5, RF-58/82). La zona se elige de la lista que da
/// el backend (rutas activas menos la suya), la misma que ve la web al aprobar y el IVR: así no
/// llegan nombres escritos a mano que luego no coinciden con ninguna ruta.
class SolicitudZonaSheet extends StatefulWidget {
  const SolicitudZonaSheet({super.key, required this.productorId, this.zonaActual});
  final int productorId;
  final String? zonaActual;

  @override
  State<SolicitudZonaSheet> createState() => _SolicitudZonaSheetState();
}

class _SolicitudZonaSheetState extends State<SolicitudZonaSheet> {
  final _formKey = GlobalKey<FormState>();
  final _motivoCtrl = TextEditingController();
  List<String>? _zonas;
  String? _zonaElegida;
  bool _cargando = true;
  bool _enviando = false;
  String? _error;

  @override
  void initState() {
    super.initState();
    _cargarZonas();
  }

  @override
  void dispose() {
    _motivoCtrl.dispose();
    super.dispose();
  }

  Future<void> _cargarZonas() async {
    setState(() {
      _cargando = true;
      _error = null;
    });
    try {
      final zonas = await context.read<ProductoresApi>().zonasSolicitables(widget.productorId);
      if (!mounted) return;
      setState(() {
        _zonas = zonas;
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

  Future<void> _enviar() async {
    if (!_formKey.currentState!.validate()) return;
    setState(() {
      _enviando = true;
      _error = null;
    });
    try {
      await context.read<ProductoresApi>().crearSolicitudZona(
        widget.productorId,
        zonaSolicitada: _zonaElegida!,
        motivo: _motivoCtrl.text.trim(),
      );
      if (mounted) Navigator.of(context).pop(true);
    } catch (e) {
      setState(() {
        _error = mensajeDeError(e);
        _enviando = false;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final zonas = _zonas;
    final sinZonas = zonas != null && zonas.isEmpty;
    return Padding(
      padding: EdgeInsets.only(left: 20, right: 20, top: 20, bottom: MediaQuery.of(context).viewInsets.bottom + 20),
      child: Form(
        key: _formKey,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            const Text('Solicitar cambio de zona', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            if (widget.zonaActual != null)
              Padding(
                padding: const EdgeInsets.only(top: 4),
                child: Text('Zona actual: ${widget.zonaActual}', style: const TextStyle(color: HuataColors.muted)),
              ),
            const SizedBox(height: 16),
            if (_cargando)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: CircularProgressIndicator()),
              )
            else if (zonas == null)
              // No se pudo traer la lista: sin ella no hay qué elegir, se ofrece reintentar.
              OutlinedButton.icon(
                onPressed: _cargarZonas,
                icon: const Icon(Icons.refresh),
                label: const Text('Volver a cargar las zonas'),
              )
            else if (sinZonas)
              const EstadoAviso(mensaje: 'Por ahora no hay otras zonas disponibles. Consulta en la planta.')
            else ...[
              DropdownButtonFormField<String>(
                initialValue: _zonaElegida,
                isExpanded: true,
                decoration: const InputDecoration(labelText: 'Zona solicitada', prefixIcon: Icon(Icons.map_outlined)),
                items: [for (final z in zonas) DropdownMenuItem(value: z, child: Text(z))],
                onChanged: _enviando ? null : (z) => setState(() => _zonaElegida = z),
                validator: (z) => z == null ? 'Elige la zona a la que quieres pasar' : null,
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _motivoCtrl,
                maxLines: 3,
                enabled: !_enviando,
                decoration: const InputDecoration(labelText: 'Motivo (opcional)'),
              ),
            ],
            if (_error != null) ...[
              const SizedBox(height: 12),
              EstadoAviso(mensaje: _error!, esError: true),
            ],
            const SizedBox(height: 8),
            ElevatedButton(
              onPressed: _enviando || _cargando || zonas == null || sinZonas ? null : _enviar,
              child: _enviando
                  ? const SizedBox(height: 18, width: 18, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                  : const Text('Enviar solicitud'),
            ),
          ],
        ),
      ),
    );
  }
}
