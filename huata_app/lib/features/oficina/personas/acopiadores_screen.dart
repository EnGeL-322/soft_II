import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/personas_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/models/acopiador.dart';
import '../../../core/theme/huata_theme.dart';
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

/// Pestaña "Acopiadores" de Usuarios en la web (acopiadores.read): quién recoge, en qué ruta y zona.
/// El alta y la cuenta de acceso del acopiador se gestionan en la web.
class AcopiadoresScreen extends StatefulWidget {
  const AcopiadoresScreen({super.key});

  @override
  State<AcopiadoresScreen> createState() => _AcopiadoresScreenState();
}

class _AcopiadoresScreenState extends State<AcopiadoresScreen> with EnVivo<AcopiadoresScreen> {
  String _q = '';
  Timer? _espera;
  bool _cargando = true;
  String? _error;
  List<Acopiador> _filas = [];

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'acopiadores', 'rutas'}, () => _cargar(silencioso: true));
    _cargar();
  }

  @override
  void dispose() {
    _espera?.cancel();
    super.dispose();
  }

  Future<void> _cargar({bool silencioso = false}) async {
    if (!silencioso) {
      setState(() {
        _cargando = true;
        _error = null;
      });
    }
    try {
      final r = await context.read<PersonasApi>().acopiadores(q: _q, tamano: 200);
      if (!mounted) return;
      setState(() {
        _filas = r.filas..sort((a, b) => a.nombreCompleto.compareTo(b.nombreCompleto));
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

  @override
  Widget build(BuildContext context) {
    return HuataScaffold(
      titulo: 'Acopiadores',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay acopiadores registrados.',
        cabecera: [
          CampoBusqueda(
            pista: 'Nombre o DNI',
            alCambiar: (v) {
              _espera?.cancel();
              _espera = Timer(const Duration(milliseconds: 400), () {
                _q = v;
                _cargar();
              });
            },
          ),
        ],
        hijos: [
          for (final a in _filas)
            FilaTarjeta(
              icono: Icons.local_shipping_outlined,
              titulo: a.nombreCompleto,
              subtitulo: [
                a.rutaNombre ?? 'Sin ruta asignada',
                if (a.zona != null) a.zona!,
                if (a.telefono != null) a.telefono!,
              ].join(' · '),
              derecha: a.activo
                  ? null
                  : const EtiquetaEstado(texto: 'Inactivo', color: HuataColors.gray, fondo: HuataColors.graySoft),
            ),
        ],
      ),
    );
  }
}
