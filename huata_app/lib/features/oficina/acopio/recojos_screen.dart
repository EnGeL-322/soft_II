import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/acopio_oficina_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/recojo.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';
import '../../proveedor/proveedor_comun.dart' show CalidadChip;

/// Vista "Recojos en ruta" de la web (acopio.read): consulta de lo que registran los acopiadores en
/// ruta y lo que entregan los proveedores en planta. Solo consulta, como en la web: el recojo nace en
/// la app del acopiador o en "Entregas directas".
class RecojosScreen extends StatefulWidget {
  const RecojosScreen({super.key});

  @override
  State<RecojosScreen> createState() => _RecojosScreenState();
}

class _RecojosScreenState extends State<RecojosScreen> with EnVivo<RecojosScreen> {
  static const _tamano = 50;

  DateTime _dia = DateTime.now();
  String? _fuente;
  String? _resultado;
  String _q = '';
  Timer? _espera;

  bool _cargando = true;
  bool _cargandoMas = false;
  String? _error;
  List<Recojo> _filas = [];
  int _total = 0;
  int _pagina = 0;

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'acopio', 'calidad'}, () => _cargar(silencioso: true));
    _cargar();
  }

  @override
  void dispose() {
    _espera?.cancel();
    super.dispose();
  }

  (DateTime, DateTime) get _rango {
    final inicio = DateTime(_dia.year, _dia.month, _dia.day);
    return (inicio, inicio.add(const Duration(days: 1)));
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final (desde, hasta) = _rango;
      // En vivo se recargan las páginas ya abiertas, para no perder la posición de la lista.
      final paginas = silencioso ? _pagina + 1 : 1;
      final r = await context.read<AcopioOficinaApi>().recojos(
        desde: desde,
        hasta: hasta,
        q: _q,
        fuente: _fuente,
        resultado: _resultado,
        tamano: (_tamano * paginas).clamp(1, 500),
      );
      if (!mounted) return;
      setState(() {
        _filas = r.filas;
        _total = r.total;
        _pagina = paginas - 1;
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

  Future<void> _cargarMas() async {
    setState(() => _cargandoMas = true);
    try {
      final (desde, hasta) = _rango;
      final r = await context.read<AcopioOficinaApi>().recojos(
        desde: desde,
        hasta: hasta,
        q: _q,
        fuente: _fuente,
        resultado: _resultado,
        pagina: _pagina + 1,
        tamano: _tamano,
      );
      if (!mounted) return;
      setState(() {
        _filas = [..._filas, ...r.filas];
        _total = r.total;
        _pagina++;
      });
    } catch (e) {
      if (mounted) avisar(context, mensajeDeError(e));
    } finally {
      if (mounted) setState(() => _cargandoMas = false);
    }
  }

  void _filtrar(VoidCallback cambio) {
    setState(cambio);
    _cargar();
  }

  void _buscar(String texto) {
    _espera?.cancel();
    _espera = Timer(const Duration(milliseconds: 400), () => _filtrar(() => _q = texto));
  }

  @override
  Widget build(BuildContext context) {
    final validos = _filas.where((r) => r.estado != 'CANCELLED');
    final litrosPagina = validos.fold(0.0, (s, r) => s + r.litros);
    return HuataScaffold(
      titulo: 'Recojos',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay recojos con estos filtros.',
        cabecera: [
          SelectorDia(dia: _dia, alCambiar: (d) => _filtrar(() => _dia = d)),
          CampoBusqueda(alCambiar: _buscar, pista: 'Proveedor, código o acopiador'),
          FiltroPildoras<String?>(
            opciones: const [(null, 'Todos'), ('RUTA', 'En ruta'), ('PLANTA', 'En planta')],
            valor: _fuente,
            alCambiar: (v) => _filtrar(() => _fuente = v),
          ),
          FiltroPildoras<String?>(
            opciones: const [
              (null, 'Toda calidad'),
              ('ACEPTADA', 'Buena'),
              ('OBSERVADA', 'Observada'),
              ('RECHAZADA', 'Mala'),
              ('PENDIENTE', 'Sin análisis'),
            ],
            valor: _resultado,
            alCambiar: (v) => _filtrar(() => _resultado = v),
          ),
          if (!_cargando && _error == null && _filas.isNotEmpty)
            Padding(
              padding: const EdgeInsets.only(bottom: 10, left: 4),
              child: Text(
                '$_total recojo(s) · ${litros(litrosPagina)} en los mostrados',
                style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
              ),
            ),
        ],
        hijos: [
          for (final r in _filas) _FilaRecojo(recojo: r),
          if (_filas.length < _total)
            Padding(
              padding: const EdgeInsets.only(top: 4),
              child: OutlinedButton(
                onPressed: _cargandoMas ? null : _cargarMas,
                child: Text(_cargandoMas ? 'Cargando…' : 'Ver más (${_total - _filas.length})'),
              ),
            ),
        ],
      ),
    );
  }
}

class _FilaRecojo extends StatelessWidget {
  const _FilaRecojo({required this.recojo});
  final Recojo recojo;

  @override
  Widget build(BuildContext context) {
    final r = recojo;
    final anulado = r.estado == 'CANCELLED';
    final noEntrego = r.estadoVisita == 'NO_ENTREGADO';
    return FilaTarjeta(
      icono: r.fuente == 'PLANTA' ? Icons.storefront_outlined : Icons.local_shipping_outlined,
      titulo: '${r.productorNombre} · ${noEntrego ? 'No entregó' : litros(r.litros)}',
      subtitulo: [
        fechaHoraCorta(r.fechaHora.toIso8601String()),
        if (r.rutaNombre != null) r.rutaNombre!,
        if (r.acopiadorNombre != null) 'Acopió: ${r.acopiadorNombre}',
        if (anulado) 'ANULADO',
      ].join(' · '),
      derecha: anulado || noEntrego ? null : CalidadChip(resultado: r.resultadoCalidad ?? 'PENDIENTE'),
      onTap: () => _detalle(context),
    );
  }

  void _detalle(BuildContext context) {
    final r = recojo;
    showModalBottomSheet<void>(
      context: context,
      showDragHandle: true,
      builder: (_) => SafeArea(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(20, 0, 20, 20),
          child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
            Text(r.productorNombre, style: const TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            FilaDato('Fecha y hora', fechaHoraCorta(r.fechaHora.toIso8601String())),
            FilaDato('Litros', litros(r.litros), destacado: true),
            FilaDato('Origen', r.fuente == 'PLANTA' ? 'Entrega en planta' : 'Recojo en ruta'),
            if (r.rutaNombre != null) FilaDato('Ruta', r.rutaNombre!),
            if (r.acopiadorNombre != null) FilaDato('Acopiador', r.acopiadorNombre!),
            if (r.temperatura != null) FilaDato('Temperatura', '${r.temperatura!.toStringAsFixed(1)} °C'),
            if (r.numeroEnvases != null) FilaDato('Envases', '${r.numeroEnvases}'),
            FilaDato('Calidad', switch (r.resultadoCalidad) {
              'ACEPTADA' => 'Buena',
              'OBSERVADA' => 'Observada',
              'RECHAZADA' => 'Mala',
              _ => 'Sin análisis',
            }),
            if (r.motivoNoEntrega != null) FilaDato('Motivo de no entrega', r.motivoNoEntrega!),
            if (r.observado && r.motivoObservacion != null) FilaDato('Corrección', r.motivoObservacion!),
            if (r.gpsFueraDeRango) const FilaDato('GPS', 'Registrado lejos del proveedor', color: HuataColors.amber),
            if (r.estado == 'CANCELLED') FilaDato('Anulado', r.motivoCancelacion ?? 'Sí', color: HuataColors.red),
          ]),
        ),
      ),
    );
  }
}
