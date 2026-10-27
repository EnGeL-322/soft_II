import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/api/ventas_pagos_api.dart';
import '../../../core/models/ventas_pagos.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

/// La liquidación de un proveedor en un ciclo: lo que se le paga, sus ajustes y el detalle por
/// entrega. Con pagos.write se calcula, se ajusta y se registra el pago (igual que "Pagar a un
/// proveedor" en la web). Los montos los calcula el backend; la app no los recalcula.
///
/// La boleta impresa y la reapertura con código siguen en la web (necesitan impresora y el código
/// que se envía al responsable).
class LiquidacionScreen extends StatefulWidget {
  const LiquidacionScreen({super.key, required FilaPlanilla this.fila, required CicloPago this.ciclo})
    : liquidacionId = null,
      titulo = null;

  const LiquidacionScreen.deId(int this.liquidacionId, {super.key, this.titulo}) : fila = null, ciclo = null;

  final FilaPlanilla? fila;
  final CicloPago? ciclo;
  final int? liquidacionId;
  final String? titulo;

  @override
  State<LiquidacionScreen> createState() => _LiquidacionScreenState();
}

class _LiquidacionScreenState extends State<LiquidacionScreen> with EnVivo<LiquidacionScreen> {
  bool _cargando = true;
  bool _ocupado = false;
  String? _error;
  LiquidacionCompleta? _liq;

  int? get _id => _liq?.id ?? widget.liquidacionId ?? widget.fila?.liquidacionId;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'pagos'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    final id = _id;
    if (id == null) {
      setState(() => _cargando = false);
      return;
    }
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final l = await context.read<VentasPagosApi>().liquidacion(id);
      if (!mounted) return;
      setState(() {
        _liq = l;
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

  Future<void> _hacer(Future<LiquidacionCompleta> Function(VentasPagosApi api) accion, String exito) async {
    setState(() => _ocupado = true);
    try {
      final l = await accion(context.read<VentasPagosApi>());
      if (!mounted) return;
      setState(() => _liq = l);
      avisar(context, exito);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _ocupado = false);
    }
  }

  Future<void> _calcular() async {
    final f = widget.fila!;
    await _hacer((api) => api.calcular(f.proveedorId, widget.ciclo!), 'Liquidación calculada.');
  }

  Future<void> _pagar() async {
    final l = _liq!;
    final ok = await confirmar(
      context,
      titulo: 'Confirmar pago al proveedor',
      mensaje: '${l.productorNombre} · ${soles(l.montoNeto)}. Periodo ${fechaCorta(l.periodoInicio)} al '
          '${fechaCorta(l.periodoFin)}. Al confirmar se registra el pago; la boleta se imprime desde la web.',
      si: 'Registrar pago',
    );
    if (!ok || !mounted) return;
    await _hacer((api) => api.pagar(l.id), 'Pago registrado: ${soles(l.montoNeto)}.');
  }

  Future<void> _agregarAjuste() async {
    final l = _liq!;
    String tipo = tiposDeAjuste.keys.first;
    final montoCtrl = TextEditingController();
    final motivoCtrl = TextEditingController();
    final guardar = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('Agregar ajuste'),
          content: Column(mainAxisSize: MainAxisSize.min, children: [
            DropdownButtonFormField<String>(
              initialValue: tipo,
              decoration: const InputDecoration(labelText: 'Tipo'),
              items: [for (final e in tiposDeAjuste.entries) DropdownMenuItem(value: e.key, child: Text(e.value))],
              onChanged: (v) => setLocal(() => tipo = v ?? tipo),
            ),
            TextField(
              controller: montoCtrl,
              keyboardType: const TextInputType.numberWithOptions(decimal: true),
              inputFormatters: [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))],
              decoration: const InputDecoration(labelText: 'Monto', prefixText: 'S/ '),
            ),
            TextField(controller: motivoCtrl, maxLength: 200, decoration: const InputDecoration(labelText: 'Motivo')),
          ]),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Agregar')),
          ],
        ),
      ),
    );
    if (guardar != true || !mounted) return;
    final monto = double.tryParse(montoCtrl.text.replaceAll(',', '.'));
    if (monto == null || monto <= 0) {
      avisar(context, 'Escribe un monto mayor a 0.');
      return;
    }
    final ok = await confirmar(
      context,
      titulo: 'Confirmar ajuste',
      mensaje: '${tiposDeAjuste[tipo]} de ${soles(monto)} para ${l.productorNombre}.',
    );
    if (!ok || !mounted) return;
    await _hacer((api) => api.agregarAjuste(l.id, tipo: tipo, monto: monto, motivo: motivoCtrl.text), 'Ajuste agregado.');
  }

  Future<void> _quitarAjuste(AjusteLiquidacion a) async {
    final ok = await confirmar(
      context,
      titulo: 'Quitar ajuste',
      mensaje: '${tiposDeAjuste[a.tipo] ?? a.tipo} de ${soles(a.montoConSigno.abs())}.',
      si: 'Quitar',
    );
    if (!ok || !mounted) return;
    await _hacer((api) => api.quitarAjuste(_liq!.id, a.id), 'Ajuste quitado.');
  }

  @override
  Widget build(BuildContext context) {
    final f = widget.fila;
    final l = _liq;
    final escribe = puede(context, 'pagos.write');
    final editable = escribe && l != null && !l.pagada && !_ocupado;
    return HuataScaffold(
      titulo: l?.productorNombre ?? f?.proveedorNombre ?? widget.titulo ?? 'Liquidación',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        hijos: [
          if (f != null && widget.ciclo != null)
            SeccionCard(
              icono: Icons.event_note_outlined,
              titulo: 'Ciclo ${widget.ciclo!.etiqueta}',
              subtitulo: [f.proveedorCodigo, if (f.zona != null) f.zona!, if (f.excelencia) 'Excelencia'].join(' · '),
              child: Column(children: [
                FilaDato('Entregado', litros(f.litrosEntregados)),
                if (f.litrosRechazados > 0) FilaDato('Rechazado', litros(f.litrosRechazados)),
                FilaDato('Pendiente de pago', litros(f.litrosPendientes)),
                FilaDato('Tarifa aplicada', soles(f.tarifaAplicada)),
                FilaDato('Importe pendiente', soles(f.importePendiente), destacado: true),
              ]),
            ),
          const SizedBox(height: 12),
          if (l == null)
            SeccionCard(
              icono: Icons.calculate_outlined,
              titulo: 'Sin liquidación todavía',
              subtitulo: escribe
                  ? 'Calcúlala para ver el monto con los ajustes y registrar el pago.'
                  : 'La calcula quien tiene permiso para registrar pagos.',
              child: escribe && f != null
                  ? SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        onPressed: _ocupado ? null : _calcular,
                        icon: const Icon(Icons.calculate_outlined),
                        label: Text(_ocupado ? 'Calculando…' : 'Calcular liquidación'),
                      ),
                    )
                  : const SizedBox.shrink(),
            )
          else ...[
            SeccionCard(
              icono: Icons.payments_outlined,
              titulo: 'A pagar',
              subtitulo: '${fechaCorta(l.periodoInicio)} – ${fechaCorta(l.periodoFin)}',
              child: Column(children: [
                FilaDato('Litros', litros(l.litrosTotales)),
                FilaDato('Monto bruto', soles(l.montoTotal)),
                FilaDato('Ajustes', soles(l.ajustesTotal)),
                FilaDato('Neto a pagar', soles(l.montoNeto), destacado: true),
                const SizedBox(height: 8),
                Align(
                  alignment: Alignment.centerLeft,
                  child: EtiquetaEstado(
                    texto: l.pagada ? 'Pagado' : 'Por pagar',
                    color: l.pagada ? HuataColors.green : HuataColors.blue,
                    fondo: l.pagada ? HuataColors.greenSoft : HuataColors.blueSoft,
                  ),
                ),
              ]),
            ),
            const SizedBox(height: 12),
            SeccionCard(
              icono: Icons.tune,
              titulo: 'Ajustes',
              subtitulo: l.ajustes.isEmpty ? 'Sin ajustes.' : null,
              child: Column(children: [
                for (final a in l.ajustes)
                  ListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(tiposDeAjuste[a.tipo] ?? a.tipo),
                    subtitle: a.motivo == null ? null : Text(a.motivo!),
                    trailing: Row(mainAxisSize: MainAxisSize.min, children: [
                      Text(soles(a.montoConSigno), style: const TextStyle(fontWeight: FontWeight.w700)),
                      if (editable)
                        IconButton(tooltip: 'Quitar', icon: const Icon(Icons.delete_outline), onPressed: () => _quitarAjuste(a)),
                    ]),
                  ),
                if (editable)
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(onPressed: _agregarAjuste, icon: const Icon(Icons.add), label: const Text('Agregar ajuste')),
                  ),
              ]),
            ),
            if (editable) ...[
              const SizedBox(height: 12),
              SizedBox(
                width: double.infinity,
                child: FilledButton.icon(
                  style: FilledButton.styleFrom(minimumSize: const Size.fromHeight(52)),
                  onPressed: _pagar,
                  icon: const Icon(Icons.check_circle_outline),
                  label: Text('Registrar pago · ${soles(l.montoNeto)}'),
                ),
              ),
            ],
            if (l.detalle.isNotEmpty) ...[
              const SizedBox(height: 12),
              const EncabezadoSeccion('Entregas liquidadas'),
              for (final d in l.detalle)
                FilaTarjeta(
                  titulo: '${litros(d.litros)} · ${soles(d.subtotal)}',
                  subtitulo: '${fechaHoraCorta(d.fechaHora)} · ${soles(d.precioAplicado)}/L',
                ),
            ],
          ],
        ],
      ),
    );
  }
}
