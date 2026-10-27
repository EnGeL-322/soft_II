import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';

import '../../../core/api/configuracion_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

/// Vista "Precios y calidad" de la web (parametros.read): el precio por litro vigente y su historial,
/// las reglas de calidad (retiro, leche observada) y las del recojo en ruta (geocerca, distancia). Con
/// parametros.write se cambian; nada se modifica sin confirmar, y el precio exige además el código
/// de verificación. Las tarifas nunca se cambian por suposición (CLAUDE.md §8).
class PreciosCalidadScreen extends StatefulWidget {
  const PreciosCalidadScreen({super.key});

  @override
  State<PreciosCalidadScreen> createState() => _PreciosCalidadScreenState();
}

class _PreciosCalidadScreenState extends State<PreciosCalidadScreen> with EnVivo<PreciosCalidadScreen> {
  bool _cargando = true;
  String? _error;
  List<TarifaLeche>? _tarifas;
  ReglasLeche? _calidad;
  ReglasAcopioConfig? _acopio;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'parametros', 'pagos'}, () => _cargar(silencioso: true));
    _cargar();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    final api = context.read<ConfiguracionApi>();
    final verTarifas = puede(context, 'pagos.read') || puede(context, 'parametros.write');
    try {
      final calidad = await api.reglasLeche();
      final acopio = await api.reglasAcopio();
      List<TarifaLeche>? tarifas;
      if (verTarifas) {
        try {
          tarifas = await api.tarifas();
        } catch (_) {}
      }
      if (!mounted) return;
      setState(() {
        _calidad = calidad;
        _acopio = acopio;
        _tarifas = tarifas?..sort((a, b) => b.vigenteDesde.compareTo(a.vigenteDesde));
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

  TarifaLeche? get _vigente {
    final hoy = isoDia(DateTime.now());
    return _tarifas
        ?.where((t) => t.vigenteDesde.compareTo(hoy) <= 0 && (t.vigenteHasta == null || t.vigenteHasta!.compareTo(hoy) >= 0))
        .firstOrNull;
  }

  Future<void> _guardar(Future<void> Function(ConfiguracionApi api) accion, String exito) async {
    try {
      await accion(context.read<ConfiguracionApi>());
      if (!mounted) return;
      avisar(context, exito);
      _cargar(silencioso: true);
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    }
  }

  Future<void> _nuevoPrecio() async {
    final v = _vigente;
    final normal = TextEditingController(text: v?.precioNormal.toStringAsFixed(2) ?? '');
    final observada = TextEditingController(text: v?.precioObservada.toStringAsFixed(2) ?? '');
    final excelencia = TextEditingController(text: v?.precioExcelencia?.toStringAsFixed(2) ?? '');
    DateTime desde = DateTime.now().add(const Duration(days: 1));
    final numero = [FilteringTextInputFormatter.allow(RegExp(r'[0-9.,]'))];
    double? leer(TextEditingController c) => double.tryParse(c.text.replaceAll(',', '.'));

    final seguir = await showDialog<bool>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setLocal) => AlertDialog(
          title: const Text('Nuevo precio por litro'),
          content: SingleChildScrollView(
            child: Column(mainAxisSize: MainAxisSize.min, children: [
              TextField(controller: normal, keyboardType: const TextInputType.numberWithOptions(decimal: true), inputFormatters: numero, decoration: const InputDecoration(labelText: 'Leche normal', prefixText: 'S/ ')),
              TextField(controller: observada, keyboardType: const TextInputType.numberWithOptions(decimal: true), inputFormatters: numero, decoration: const InputDecoration(labelText: 'Leche observada', prefixText: 'S/ ')),
              TextField(controller: excelencia, keyboardType: const TextInputType.numberWithOptions(decimal: true), inputFormatters: numero, decoration: const InputDecoration(labelText: 'Excelencia (opcional)', prefixText: 'S/ ')),
              const SizedBox(height: 8),
              OutlinedButton.icon(
                icon: const Icon(Icons.calendar_month_outlined),
                label: Text('Rige desde ${fechaCorta(isoDia(desde))}'),
                onPressed: () async {
                  final f = await showDatePicker(context: ctx, initialDate: desde, firstDate: DateTime.now(), lastDate: DateTime(2100));
                  if (f != null) setLocal(() => desde = f);
                },
              ),
            ]),
          ),
          actions: [
            TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
            FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Continuar')),
          ],
        ),
      ),
    );
    if (seguir != true || !mounted) return;
    final pn = leer(normal), po = leer(observada), pe = leer(excelencia);
    if (pn == null || pn <= 0 || po == null || po < 0) {
      avisar(context, 'Escribe el precio normal y el de leche observada.');
      return;
    }
    final ok = await confirmar(
      context,
      titulo: 'Confirmar nuevo precio',
      mensaje: 'Normal ${soles(pn)} · observada ${soles(po)}${pe != null ? ' · excelencia ${soles(pe)}' : ''}, desde '
          '${fechaCorta(isoDia(desde))}. Se enviará un código de verificación al responsable.',
      si: 'Enviar código',
    );
    if (!ok || !mounted) return;
    final String destino;
    try {
      destino = await context.read<ConfiguracionApi>().solicitarCodigoPrecios();
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
      return;
    }
    if (!mounted) return;
    final codigo = TextEditingController();
    final enviar = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Código de verificación'),
        content: Column(mainAxisSize: MainAxisSize.min, children: [
          Text('Escribe el código de 6 dígitos enviado a $destino.'),
          TextField(
            controller: codigo,
            autofocus: true,
            keyboardType: TextInputType.number,
            maxLength: 6,
            inputFormatters: [FilteringTextInputFormatter.digitsOnly],
            decoration: const InputDecoration(labelText: 'Código'),
          ),
        ]),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Guardar precio')),
        ],
      ),
    );
    if (enviar != true || !mounted) return;
    if (!RegExp(r'^\d{6}$').hasMatch(codigo.text)) {
      avisar(context, 'El código tiene 6 dígitos.');
      return;
    }
    await _guardar(
      (api) => api.nuevaTarifa(precioNormal: pn, precioObservada: po, precioExcelencia: pe, vigenteDesde: desde, codigo: codigo.text),
      'Precio guardado. Rige desde ${fechaCorta(isoDia(desde))}.',
    );
  }

  Future<void> _cambiarCalidad(ReglasLeche nueva, String detalle) async {
    final ok = await confirmar(context, titulo: 'Cambiar regla de calidad', mensaje: detalle);
    if (!ok || !mounted) return;
    await _guardar((api) => api.guardarReglasLeche(nueva), 'Regla de calidad guardada.');
  }

  Future<void> _cambiarAcopio(ReglasAcopioConfig nueva, String detalle) async {
    final ok = await confirmar(context, titulo: 'Cambiar regla de acopio', mensaje: detalle);
    if (!ok || !mounted) return;
    await _guardar((api) => api.guardarReglasAcopio(nueva), 'Regla de acopio guardada.');
  }

  Future<int?> _pedirNumero(String titulo, String etiqueta, num actual) async {
    final ctrl = TextEditingController(text: '$actual');
    final ok = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: Text(titulo),
        content: TextField(
          controller: ctrl,
          autofocus: true,
          keyboardType: TextInputType.number,
          inputFormatters: [FilteringTextInputFormatter.digitsOnly],
          decoration: InputDecoration(labelText: etiqueta),
        ),
        actions: [
          TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
          FilledButton(onPressed: () => Navigator.pop(ctx, true), child: const Text('Continuar')),
        ],
      ),
    );
    return ok == true ? int.tryParse(ctrl.text) : null;
  }

  @override
  Widget build(BuildContext context) {
    final escribe = puede(context, 'parametros.write');
    final v = _vigente;
    final c = _calidad;
    final a = _acopio;
    return HuataScaffold(
      titulo: 'Precios y calidad',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        hijos: [
          if (_tarifas != null) ...[
            SeccionCard(
              icono: Icons.payments_outlined,
              titulo: 'Precio por litro',
              subtitulo: v == null ? 'No hay un precio vigente hoy.' : 'Vigente desde ${fechaCorta(v.vigenteDesde)}',
              child: Column(children: [
                if (v != null) ...[
                  FilaDato('Leche normal', soles(v.precioNormal), destacado: true),
                  FilaDato('Leche observada', soles(v.precioObservada)),
                  FilaDato('Excelencia', soles(v.precioExcelencia)),
                ],
                if (escribe) ...[
                  const SizedBox(height: 10),
                  SizedBox(
                    width: double.infinity,
                    child: OutlinedButton.icon(onPressed: _nuevoPrecio, icon: const Icon(Icons.edit_outlined), label: const Text('Programar nuevo precio')),
                  ),
                ],
                if (_tarifas!.length > 1) ...[
                  const Divider(height: 24),
                  for (final t in _tarifas!.take(8))
                    FilaDato(
                      '${fechaCorta(t.vigenteDesde)}${t.vigenteHasta != null ? ' – ${fechaCorta(t.vigenteHasta)}' : ''}',
                      '${soles(t.precioNormal)} / ${soles(t.precioObservada)}',
                    ),
                ],
              ]),
            ),
            const SizedBox(height: 12),
          ],
          if (c != null)
            SeccionCard(
              icono: Icons.verified_outlined,
              titulo: 'Reglas de calidad',
              child: Column(children: [
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Detecciones para retirar a un proveedor'),
                  trailing: Text('${c.deteccionesParaRetiro}', style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
                  onTap: escribe
                      ? () async {
                          final n = await _pedirNumero('Detecciones para retiro', 'Cantidad', c.deteccionesParaRetiro);
                          if (n == null || n < 1 || !mounted) return;
                          final nueva = ReglasLeche.fromJson(c.toJson())..deteccionesParaRetiro = n;
                          _cambiarCalidad(nueva, 'Retirar al proveedor tras $n detección(es) de mala calidad.');
                        }
                      : null,
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('La leche observada cuenta como mala calidad'),
                  value: c.observadaEsMalaCalidad,
                  onChanged: escribe
                      ? (x) => _cambiarCalidad(ReglasLeche.fromJson(c.toJson())..observadaEsMalaCalidad = x,
                          x ? 'La leche observada contará como mala calidad.' : 'La leche observada no contará como mala calidad.')
                      : null,
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('La leche observada entra al inventario'),
                  value: c.observadaEntraAInventario,
                  onChanged: escribe
                      ? (x) => _cambiarCalidad(ReglasLeche.fromJson(c.toJson())..observadaEntraAInventario = x,
                          x ? 'La leche observada entrará al inventario.' : 'La leche observada no entrará al inventario.')
                      : null,
                ),
                if (c.costoLecheLitro != null) FilaDato('Costo de la leche para producción', '${soles(c.costoLecheLitro)} / L'),
              ]),
            ),
          const SizedBox(height: 12),
          if (a != null)
            SeccionCard(
              icono: Icons.route_outlined,
              titulo: 'Reglas del recojo en ruta',
              subtitulo: 'Lo que la app del acopiador exige antes de registrar leche.',
              child: Column(children: [
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Exigir estar dentro de la zona (geocerca)'),
                  value: a.exigirGeocerca,
                  onChanged: escribe
                      ? (x) => _cambiarAcopio(ReglasAcopioConfig.fromJson(a.toJson())..exigirGeocerca = x,
                          x ? 'El acopiador solo podrá registrar dentro de su zona.' : 'El acopiador podrá registrar fuera de su zona.')
                      : null,
                ),
                SwitchListTile(
                  contentPadding: EdgeInsets.zero,
                  title: const Text('Exigir estar cerca del proveedor'),
                  subtitle: Text('Hasta ${a.distanciaMaximaMetros.toStringAsFixed(0)} m'),
                  value: a.exigirDistancia,
                  onChanged: escribe
                      ? (x) => _cambiarAcopio(ReglasAcopioConfig.fromJson(a.toJson())..exigirDistancia = x,
                          x ? 'El acopiador deberá estar a menos de ${a.distanciaMaximaMetros.toStringAsFixed(0)} m del proveedor.' : 'No se exigirá distancia al proveedor.')
                      : null,
                ),
                if (escribe)
                  Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      icon: const Icon(Icons.straighten),
                      label: const Text('Cambiar distancia máxima'),
                      onPressed: () async {
                        final n = await _pedirNumero('Distancia máxima', 'Metros', a.distanciaMaximaMetros.round());
                        if (n == null || n < 10 || !mounted) return;
                        _cambiarAcopio(ReglasAcopioConfig.fromJson(a.toJson())..distanciaMaximaMetros = n.toDouble(), 'Distancia máxima al proveedor: $n m.');
                      },
                    ),
                  ),
              ]),
            ),
          if (!escribe)
            const Padding(
              padding: EdgeInsets.only(top: 12),
              child: Text('Solo consulta: los cambios los hace quien tiene permiso para modificar precios y reglas.', style: TextStyle(color: HuataColors.muted, fontSize: 12.5)),
            ),
        ],
      ),
    );
  }
}
