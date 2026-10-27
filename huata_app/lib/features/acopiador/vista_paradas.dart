import 'package:flutter/material.dart';

import '../../core/theme/huata_theme.dart';
import 'parada_ruta.dart';

/// Vista "Paradas": todos los proveedores de la ruta, con búsqueda y filtro por estado. Agrupa con la
/// regla vigente de la app: pendientes arriba (en orden de visita), acopiados y no entregados al final.
class VistaParadas extends StatefulWidget {
  const VistaParadas({super.key, required this.cabecera, required this.paradas, required this.onTocar});

  /// Cabecera de la jornada: se desplaza con la lista para dejarle espacio.
  final Widget cabecera;
  final List<ParadaRuta> paradas;
  final void Function(ParadaRuta) onTocar;

  @override
  State<VistaParadas> createState() => _VistaParadasState();
}

class _VistaParadasState extends State<VistaParadas> {
  final _busquedaCtrl = TextEditingController();
  String _busqueda = '';
  EstadoVisita? _filtro;

  @override
  void dispose() {
    _busquedaCtrl.dispose();
    super.dispose();
  }

  List<ParadaRuta> get _visibles {
    final q = _busqueda.trim().toLowerCase();
    final lista = widget.paradas.where((p) {
      if (_filtro != null && p.estado != _filtro) return false;
      if (q.isEmpty) return true;
      return p.proveedor.nombreCompleto.toLowerCase().contains(q) ||
          p.proveedor.codigo.toLowerCase().contains(q) ||
          p.numero == q;
    }).toList();
    lista.sort((a, b) {
      final porEstado = a.estado.index.compareTo(b.estado.index);
      if (porEstado != 0) return porEstado;
      final porOrden = (a.proveedor.ordenEnRuta ?? 999999).compareTo(b.proveedor.ordenEnRuta ?? 999999);
      return porOrden != 0 ? porOrden : a.proveedor.codigo.compareTo(b.proveedor.codigo);
    });
    return lista;
  }

  @override
  Widget build(BuildContext context) {
    final visibles = _visibles;
    final testeados = widget.paradas.where((p) => p.testeoHoy?.isNotEmpty ?? false).length;
    final faltanTestear = widget.paradas.where((p) => p.testeoHoy == '').length;
    final conteo = {for (final e in EstadoVisita.values) e: widget.paradas.where((p) => p.estado == e).length};

    final hijos = <Widget>[];
    for (final estado in EstadoVisita.values) {
      final grupo = visibles.where((p) => p.estado == estado).toList();
      if (grupo.isEmpty) continue;
      final litros = grupo.fold(0.0, (s, p) => s + (p.recojo?.litros ?? 0));
      hijos.add(_EncabezadoGrupo(
        estado: estado,
        cantidad: grupo.length,
        detalle: estado == EstadoVisita.acopiado ? formatoLitros(litros) : null,
      ));
      hijos.add(Container(
        decoration: BoxDecoration(
          color: HuataColors.panel,
          borderRadius: BorderRadius.circular(16),
          border: Border.all(color: const Color(0xFFE8EDF3)),
          boxShadow: HuataMarca.sombra,
        ),
        child: Column(
          children: [
            for (var i = 0; i < grupo.length; i++) ...[
              if (i > 0) const Divider(height: 1, indent: 68),
              _FilaParada(parada: grupo[i], onTap: () => widget.onTocar(grupo[i])),
            ],
          ],
        ),
      ));
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 4, 16, 24),
      children: [
        widget.cabecera,
        const SizedBox(height: 16),
        TextField(
          controller: _busquedaCtrl,
          onChanged: (v) => setState(() => _busqueda = v),
          textInputAction: TextInputAction.search,
          decoration: InputDecoration(
            hintText: 'Buscar por nombre, código o número',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: _busqueda.isEmpty
                ? null
                : IconButton(
                    tooltip: 'Limpiar',
                    icon: const Icon(Icons.close),
                    onPressed: () => setState(() {
                      _busquedaCtrl.clear();
                      _busqueda = '';
                    }),
                  ),
          ),
        ),
        const SizedBox(height: 10),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: Row(
            children: [
              _ChipFiltro(
                texto: 'Todas',
                cantidad: widget.paradas.length,
                activo: _filtro == null,
                onTap: () => setState(() => _filtro = null),
              ),
              for (final e in EstadoVisita.values) ...[
                const SizedBox(width: 8),
                _ChipFiltro(
                  texto: e.plural,
                  cantidad: conteo[e]!,
                  color: e.color,
                  activo: _filtro == e,
                  onTap: () => setState(() => _filtro = _filtro == e ? null : e),
                ),
              ],
            ],
          ),
        ),
        if (testeados + faltanTestear > 0) ...[
          const SizedBox(height: 10),
          Row(
            children: [
              const Icon(Icons.science_outlined, size: 15, color: HuataColors.muted),
              const SizedBox(width: 6),
              Text(
                'Testeados hoy: $testeados · Faltan testear: $faltanTestear',
                style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
              ),
            ],
          ),
        ],
        const SizedBox(height: 4),
        if (hijos.isEmpty)
          EstadoVacio(
            icono: Icons.search_off,
            titulo: 'Sin resultados',
            mensaje: widget.paradas.isEmpty
                ? 'Esta ruta todavía no tiene proveedores asignados.'
                : 'Ningún proveedor coincide con la búsqueda o el filtro.',
          )
        else
          ...hijos,
      ],
    );
  }
}

class _ChipFiltro extends StatelessWidget {
  const _ChipFiltro({required this.texto, required this.cantidad, required this.activo, required this.onTap, this.color});

  final String texto;
  final int cantidad;
  final bool activo;
  final VoidCallback onTap;
  final Color? color;

  @override
  Widget build(BuildContext context) {
    final fondo = activo ? HuataColors.green : HuataColors.panel;
    final tinta = activo ? Colors.white : HuataColors.text;
    return Material(
      color: fondo,
      shape: StadiumBorder(side: BorderSide(color: activo ? HuataColors.green : HuataColors.line)),
      child: InkWell(
        customBorder: const StadiumBorder(),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 8, 8),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (color != null) ...[
                Container(
                  width: 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: activo ? Colors.white : color,
                    shape: BoxShape.circle,
                  ),
                ),
                const SizedBox(width: 6),
              ],
              Text(texto, style: TextStyle(color: tinta, fontSize: 13, fontWeight: FontWeight.w600)),
              const SizedBox(width: 6),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 1),
                decoration: BoxDecoration(
                  color: activo ? const Color(0x33FFFFFF) : HuataColors.graySoft,
                  borderRadius: BorderRadius.circular(999),
                ),
                child: Text('$cantidad', style: TextStyle(color: tinta, fontSize: 12, fontWeight: FontWeight.w700)),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _EncabezadoGrupo extends StatelessWidget {
  const _EncabezadoGrupo({required this.estado, required this.cantidad, this.detalle});
  final EstadoVisita estado;
  final int cantidad;
  final String? detalle;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 18, 4, 8),
      child: Row(
        children: [
          Container(width: 8, height: 8, decoration: BoxDecoration(color: estado.color, shape: BoxShape.circle)),
          const SizedBox(width: 8),
          Text(
            '${estado.plural.toUpperCase()} · $cantidad',
            style: const TextStyle(fontSize: 11.5, fontWeight: FontWeight.w700, letterSpacing: 0.8, color: HuataColors.muted),
          ),
          const Spacer(),
          if (detalle != null)
            Text(detalle!, style: TextStyle(fontSize: 12.5, fontWeight: FontWeight.w700, color: estado.color)),
        ],
      ),
    );
  }
}

class _FilaParada extends StatelessWidget {
  const _FilaParada({required this.parada, required this.onTap});
  final ParadaRuta parada;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final p = parada.proveedor;
    final estado = parada.estado;
    final pendiente = estado == EstadoVisita.pendiente;
    final habilitado = p.puedeEntregar;

    final Widget derecha = switch (estado) {
      EstadoVisita.acopiado => Column(
        crossAxisAlignment: CrossAxisAlignment.end,
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(formatoLitros(parada.recojo?.litros ?? 0), style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 15)),
          const SizedBox(height: 2),
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (parada.sinSincronizar)
                const Tooltip(
                  message: 'Guardado en el teléfono; se enviará solo cuando haya señal',
                  child: Icon(Icons.cloud_off_outlined, size: 14, color: HuataColors.amber),
                ),
              if (parada.recojo?.observado == true) ...[
                const SizedBox(width: 4),
                const Tooltip(
                  message: 'Corregido: queda observado para revisión',
                  child: Icon(Icons.edit_note, size: 16, color: HuataColors.amber),
                ),
              ],
              const SizedBox(width: 4),
              const Text('Corregir', style: TextStyle(color: HuataColors.muted, fontSize: 11.5)),
            ],
          ),
        ],
      ),
      EstadoVisita.noEntregado => _Pildora(texto: 'No entregó', color: estado.color, fondo: estado.fondo),
      EstadoVisita.pendiente => habilitado
          ? const Icon(Icons.chevron_right, color: HuataColors.muted)
          : const _Pildora(texto: 'Por aprobar', color: HuataColors.amber, fondo: HuataColors.amberSoft),
    };

    final subtitulo = estado == EstadoVisita.noEntregado && (parada.recojo?.motivoNoEntrega ?? '').isNotEmpty
        ? '${p.codigo} · ${parada.recojo!.motivoNoEntrega}'
        : p.codigo;

    return InkWell(
      onTap: onTap,
      child: Padding(
        padding: const EdgeInsets.fromLTRB(14, 12, 14, 12),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: pendiente ? Colors.white : estado.color,
                borderRadius: BorderRadius.circular(12),
                border: pendiente ? Border.all(color: HuataColors.blue, width: 2) : null,
              ),
              child: pendiente
                  ? Text(parada.numero, style: const TextStyle(color: HuataColors.blue, fontWeight: FontWeight.w800, fontSize: 14))
                  : Icon(estado == EstadoVisita.acopiado ? Icons.check_rounded : Icons.close_rounded, color: Colors.white, size: 22),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    p.nombreCompleto,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(
                      fontWeight: FontWeight.w600,
                      fontSize: 14.5,
                      color: habilitado ? HuataColors.text : HuataColors.muted,
                    ),
                  ),
                  const SizedBox(height: 2),
                  Text(
                    pendiente ? '#${parada.numero} · $subtitulo' : subtitulo,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: HuataColors.muted, fontSize: 12.5),
                  ),
                  if (parada.testeoHoy != null) ...[
                    const SizedBox(height: 3),
                    EtiquetaTesteo(resultado: parada.testeoHoy!),
                  ],
                ],
              ),
            ),
            const SizedBox(width: 10),
            derecha,
          ],
        ),
      ),
    );
  }
}

class _Pildora extends StatelessWidget {
  const _Pildora({required this.texto, required this.color, required this.fondo});
  final String texto;
  final Color color;
  final Color fondo;

  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(horizontal: 9, vertical: 4),
    decoration: BoxDecoration(color: fondo, borderRadius: BorderRadius.circular(999)),
    child: Text(texto, style: TextStyle(color: color, fontSize: 11.5, fontWeight: FontWeight.w700)),
  );
}
