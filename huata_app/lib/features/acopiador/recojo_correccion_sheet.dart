import 'package:flutter/material.dart';

import '../../core/models/recojo.dart';
import '../../core/theme/huata_theme.dart';

class RecojoCorreccionResultado {
  RecojoCorreccionResultado({required this.litros, required this.motivo});
  final double litros;
  final String motivo;
}

/// Corrige un recojo ya registrado (pedido del cliente, 2026-09-24): un proveedor entrega leche una
/// sola vez al día, así que volver a tocar la fila de un proveedor ya acopiado NO puede abrir el
/// formulario de registro de nuevo (crearía un segundo recojo que el backend rechaza). Esta es la
/// vía para arreglar un litros mal tecleado: exige una justificación y el recojo queda marcado
/// "observado" para que calidad/administración lo revise, en vez de cambiar el número en silencio.
class RecojoCorreccionSheet extends StatefulWidget {
  const RecojoCorreccionSheet({super.key, required this.recojo});
  final Recojo recojo;

  @override
  State<RecojoCorreccionSheet> createState() => _RecojoCorreccionSheetState();
}

class _RecojoCorreccionSheetState extends State<RecojoCorreccionSheet> {
  final _formKey = GlobalKey<FormState>();
  late final _litrosCtrl = TextEditingController(text: widget.recojo.litros.toStringAsFixed(1));
  final _motivoCtrl = TextEditingController();

  @override
  void dispose() {
    _litrosCtrl.dispose();
    _motivoCtrl.dispose();
    super.dispose();
  }

  void _guardar() {
    if (!_formKey.currentState!.validate()) return;
    Navigator.of(context).pop(
      RecojoCorreccionResultado(
        litros: double.parse(_litrosCtrl.text.replaceAll(',', '.')),
        motivo: _motivoCtrl.text.trim(),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final r = widget.recojo;
    final hora = '${r.fechaHora.toLocal().hour.toString().padLeft(2, '0')}:'
        '${r.fechaHora.toLocal().minute.toString().padLeft(2, '0')}';
    return Padding(
      padding: EdgeInsets.only(left: 20, right: 20, bottom: MediaQuery.of(context).viewInsets.bottom + 20),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const Text('Corregir entrega', style: TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
              const SizedBox(height: 2),
              Text(r.productorNombre, style: const TextStyle(color: HuataColors.muted)),
              const SizedBox(height: 14),
              Container(
                padding: const EdgeInsets.all(14),
                decoration: BoxDecoration(color: HuataColors.panel2, borderRadius: BorderRadius.circular(14)),
                child: Row(
                  children: [
                    const Icon(Icons.water_drop_outlined, color: HuataColors.green),
                    const SizedBox(width: 10),
                    const Expanded(child: Text('Registrado hoy', style: TextStyle(color: HuataColors.gray))),
                    Text(
                      '${r.litros.toStringAsFixed(1)} L',
                      style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16),
                    ),
                    Text('  ·  $hora', style: const TextStyle(color: HuataColors.muted)),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              TextFormField(
                controller: _litrosCtrl,
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
                style: const TextStyle(fontSize: 20, fontWeight: FontWeight.w700),
                decoration: const InputDecoration(labelText: 'Litros correctos', suffixText: 'L'),
                validator: (v) {
                  final n = double.tryParse((v ?? '').replaceAll(',', '.'));
                  if (n == null || n <= 0) return 'Ingresa los litros correctos';
                  return null;
                },
              ),
              const SizedBox(height: 12),
              TextFormField(
                controller: _motivoCtrl,
                decoration: const InputDecoration(labelText: 'Justificación (obligatoria)', hintText: 'Ej.: anoté 52 en vez de 25'),
                minLines: 2,
                maxLines: 3,
                validator: (v) => (v == null || v.trim().length < 5) ? 'Explica por qué corriges este recojo' : null,
              ),
              const SizedBox(height: 12),
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: HuataColors.amberSoft,
                  borderRadius: BorderRadius.circular(12),
                  border: const Border(left: BorderSide(color: HuataColors.amber, width: 3)),
                ),
                child: const Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Icon(Icons.info_outline, size: 18, color: HuataColors.amber),
                    SizedBox(width: 8),
                    Expanded(
                      child: Text(
                        'Corrige solo si te equivocaste al anotar. El recojo quedará observado para que '
                        'administración lo revise.',
                        style: TextStyle(color: HuataColors.amber, fontSize: 13, height: 1.35),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _guardar,
                style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                icon: const Icon(Icons.edit_note),
                label: const Text('Guardar corrección'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
