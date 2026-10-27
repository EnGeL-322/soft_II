import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../../../core/api/personas_api.dart';
import '../../../core/api/rutas_api.dart' show mensajeDeError;
import '../../../core/tiempo_real/cambios_en_vivo.dart';
import '../../../shared/huata_scaffold.dart';
import '../../../shared/oficina.dart';

/// Vista "Zonas" de la web (zonas.read): las personas y rutas de cada zona.
class ZonasScreen extends StatefulWidget {
  const ZonasScreen({super.key});

  @override
  State<ZonasScreen> createState() => _ZonasScreenState();
}

class _ZonasScreenState extends State<ZonasScreen> with EnVivo<ZonasScreen> {
  bool _cargando = true;
  String? _error;
  List<ZonaAsignacion> _zonas = [];
  String _q = '';

  @override
  void initState() {
    super.initState();
    recargarEnVivo({'rutas', 'proveedores', 'acopiadores'}, () => _cargar(silencioso: true));
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
      final z = await context.read<PersonasApi>().zonas();
      if (!mounted) return;
      setState(() {
        _zonas = z..sort((a, b) => '${a.zona}${a.rutaNombre}'.compareTo('${b.zona}${b.rutaNombre}'));
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
    final q = _q.trim().toLowerCase();
    final visibles = _zonas
        .where((z) =>
            q.isEmpty ||
            z.zona.toLowerCase().contains(q) ||
            z.rutaNombre.toLowerCase().contains(q) ||
            z.proveedores.any((p) => p.nombre.toLowerCase().contains(q) || p.codigo.toLowerCase().contains(q)))
        .toList();
    return HuataScaffold(
      titulo: 'Zonas',
      body: CuerpoConsulta(
        cargando: _cargando,
        error: _error,
        alRecargar: _cargar,
        vacio: 'No hay zonas con rutas asignadas.',
        cabecera: [CampoBusqueda(alCambiar: (v) => setState(() => _q = v), pista: 'Zona, ruta o proveedor')],
        hijos: [
          for (final z in visibles)
            Card(
              margin: const EdgeInsets.only(bottom: 8),
              clipBehavior: Clip.antiAlias,
              child: ExpansionTile(
                shape: const Border(),
                collapsedShape: const Border(),
                leading: const Icon(Icons.place_outlined),
                title: Text('${z.zona} · ${z.rutaNombre}', style: const TextStyle(fontWeight: FontWeight.w700)),
                subtitle: Text('${z.proveedores.length} proveedor(es) · ${z.acopiadores.isEmpty ? 'sin acopiador' : z.acopiadores.join(', ')}'),
                childrenPadding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
                children: [
                  for (final p in [...z.proveedores]..sort((a, b) => (a.orden ?? 999).compareTo(b.orden ?? 999)))
                    FilaDato('${p.orden != null ? '${p.orden}. ' : ''}${p.nombre}', [p.codigo, if (p.telefono != null) p.telefono!].join(' · ')),
                ],
              ),
            ),
        ],
      ),
    );
  }
}
