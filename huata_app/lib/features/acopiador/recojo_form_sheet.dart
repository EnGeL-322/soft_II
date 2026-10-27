import 'package:flutter/material.dart';

import '../../core/models/ruta.dart';
import '../../core/theme/huata_theme.dart';

class RecojoFormResultado {
  RecojoFormResultado({required this.noEntregado, this.litros, this.motivo});
  final bool noEntregado;
  final double? litros;
  final String? motivo;
}

/// Formulario compacto por proveedor (6.3, RF-75/76): litros si entregó, o motivo si no. Nada más
/// se pide acá — precio, tarifa y calidad detallada no son parte de este flujo (los resuelve la web
/// de recepción/calidad, no la app del acopiador).
class RecojoFormSheet extends StatefulWidget {
  const RecojoFormSheet({super.key, required this.proveedor});
  final ProveedorDeRuta proveedor;

  @override
  State<RecojoFormSheet> createState() => _RecojoFormSheetState();
}

class _RecojoFormSheetState extends State<RecojoFormSheet> {
  /// Atajos para escribir el motivo: solo rellenan el texto, que sigue siendo libre y editable.
  static const _motivosFrecuentes = ['Proveedor ausente', 'No tenía leche', 'Leche descartada', 'No quiso entregar'];

  final _formKey = GlobalKey<FormState>();
  final _litrosCtrl = TextEditingController();
  final _motivoCtrl = TextEditingController();
  bool _noEntregado = false;

  @override
  void dispose() {
    _litrosCtrl.dispose();
    _motivoCtrl.dispose();
    super.dispose();
  }

  double? get _litros {
    final n = double.tryParse(_litrosCtrl.text.replaceAll(',', '.'));
    return n == null || n <= 0 ? null : n;
  }

  void _guardar() {
    if (!_formKey.currentState!.validate()) return;
    Navigator.of(context).pop(
      RecojoFormResultado(
        noEntregado: _noEntregado,
        litros: _noEntregado ? null : _litros,
        motivo: _noEntregado ? _motivoCtrl.text.trim() : null,
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final p = widget.proveedor;
    final litros = _litros;
    final textoBoton = _noEntregado
        ? 'Registrar no entrega'
        : litros == null
        ? 'Registrar entrega'
        : 'Registrar ${litros.toStringAsFixed(1)} L';
    return Padding(
      padding: EdgeInsets.only(left: 20, right: 20, bottom: MediaQuery.of(context).viewInsets.bottom + 20),
      child: Form(
        key: _formKey,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Container(
                    width: 46,
                    height: 46,
                    alignment: Alignment.center,
                    decoration: BoxDecoration(color: HuataColors.blueSoft, borderRadius: BorderRadius.circular(14)),
                    child: Text(
                      p.ordenEnRuta?.toString() ?? '–',
                      style: const TextStyle(color: HuataColors.blue, fontWeight: FontWeight.w800, fontSize: 16),
                    ),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(p.nombreCompleto, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 18)),
                        Text(p.codigo, style: const TextStyle(color: HuataColors.muted)),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 18),
              SegmentedButton<bool>(
                showSelectedIcon: false,
                segments: const [
                  ButtonSegment(value: false, label: Text('Entregó leche'), icon: Icon(Icons.water_drop_outlined, size: 18)),
                  ButtonSegment(value: true, label: Text('No entregó'), icon: Icon(Icons.block_outlined, size: 18)),
                ],
                selected: {_noEntregado},
                onSelectionChanged: (v) => setState(() => _noEntregado = v.first),
              ),
              const SizedBox(height: 18),
              if (!_noEntregado) ...[
                const Text('Litros recibidos', style: TextStyle(fontWeight: FontWeight.w600, color: HuataColors.gray)),
                const SizedBox(height: 8),
                TextFormField(
                  controller: _litrosCtrl,
                  autofocus: true,
                  textAlign: TextAlign.center,
                  keyboardType: const TextInputType.numberWithOptions(decimal: true),
                  textInputAction: TextInputAction.done,
                  onChanged: (_) => setState(() {}),
                  onFieldSubmitted: (_) => _guardar(),
                  style: const TextStyle(fontSize: 34, fontWeight: FontWeight.w800, letterSpacing: -0.5),
                  decoration: const InputDecoration(
                    hintText: '0.0',
                    suffixText: 'L',
                    suffixStyle: TextStyle(fontSize: 20, fontWeight: FontWeight.w700, color: HuataColors.muted),
                    contentPadding: EdgeInsets.symmetric(horizontal: 16, vertical: 18),
                  ),
                  validator: (v) {
                    final n = double.tryParse((v ?? '').replaceAll(',', '.'));
                    if (n == null || n <= 0) return 'Ingresa los litros recibidos';
                    return null;
                  },
                ),
              ] else ...[
                const Text('¿Por qué no entregó?', style: TextStyle(fontWeight: FontWeight.w600, color: HuataColors.gray)),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final m in _motivosFrecuentes)
                      ChoiceChip(
                        label: Text(m),
                        selected: _motivoCtrl.text.trim() == m,
                        onSelected: (_) => setState(() => _motivoCtrl.text = m),
                      ),
                  ],
                ),
                const SizedBox(height: 12),
                TextFormField(
                  controller: _motivoCtrl,
                  onChanged: (_) => setState(() {}),
                  minLines: 1,
                  maxLines: 3,
                  decoration: const InputDecoration(labelText: 'Motivo (obligatorio)'),
                  validator: (v) => (v == null || v.trim().isEmpty) ? 'El motivo es obligatorio' : null,
                ),
              ],
              const SizedBox(height: 12),
              const Row(
                children: [
                  Icon(Icons.cloud_done_outlined, size: 16, color: HuataColors.muted),
                  SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      'Si no hay señal se guarda en el teléfono y se envía solo.',
                      style: TextStyle(color: HuataColors.muted, fontSize: 12.5),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 16),
              FilledButton.icon(
                onPressed: _guardar,
                style: FilledButton.styleFrom(
                  minimumSize: const Size.fromHeight(52),
                  backgroundColor: _noEntregado ? HuataColors.red : HuataColors.green,
                ),
                icon: Icon(_noEntregado ? Icons.block_outlined : Icons.check_rounded),
                label: Text(textoBoton),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
