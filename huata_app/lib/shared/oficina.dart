import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/auth/auth_controller.dart';
import '../core/theme/huata_theme.dart';

/// Piezas comunes de las pantallas de oficina de la app (las vistas de la web llevadas al celular):
/// carga, error, vacío, filas de datos y formatos. Así todas se ven y se comportan igual.

/// ¿La sesión tiene este permiso? Para mostrar u ocultar botones; el backend vuelve a validarlo.
bool puede(BuildContext context, String permiso) => context.read<AuthController>().sesion?.puede(permiso) ?? false;

String soles(num? monto) => monto == null ? '—' : 'S/ ${monto.toStringAsFixed(2)}';

String litros(num? l) => l == null ? '—' : '${l.toStringAsFixed(1)} L';

/// "2026-10-23" o "2026-10-23T10:30:00Z" → "23/10/2026".
String fechaCorta(String? iso) {
  if (iso == null || iso.isEmpty) return '—';
  final d = DateTime.tryParse(iso);
  if (d == null) return iso;
  final l = iso.length > 10 ? d.toLocal() : d;
  return '${l.day.toString().padLeft(2, '0')}/${l.month.toString().padLeft(2, '0')}/${l.year}';
}

/// "2026-10-23T10:30:00Z" → "23/10 05:30" (hora local).
String fechaHoraCorta(String? iso) {
  final d = iso == null ? null : DateTime.tryParse(iso)?.toLocal();
  if (d == null) return iso ?? '—';
  return '${d.day.toString().padLeft(2, '0')}/${d.month.toString().padLeft(2, '0')} '
      '${d.hour.toString().padLeft(2, '0')}:${d.minute.toString().padLeft(2, '0')}';
}

/// "2026-10-23" para la API.
String isoDia(DateTime d) =>
    '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';

/// Cuerpo de una pantalla que carga datos: spinner, error con reintento, vacío o la lista. Siempre
/// desplazable, para que "jalar para actualizar" funcione también con la lista vacía.
class CuerpoConsulta extends StatelessWidget {
  const CuerpoConsulta({
    super.key,
    required this.cargando,
    required this.error,
    required this.alRecargar,
    required this.hijos,
    this.vacio,
    this.cabecera = const [],
  });

  final bool cargando;
  final String? error;
  final Future<void> Function() alRecargar;

  /// Encima de la lista (filtros, totales): se muestra aunque no haya filas.
  final List<Widget> cabecera;
  final List<Widget> hijos;

  /// Texto si no hay filas. Null: no se muestra nada.
  final String? vacio;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: alRecargar,
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(16),
        children: [
          ...cabecera,
          if (cargando)
            const Padding(padding: EdgeInsets.symmetric(vertical: 48), child: Center(child: CircularProgressIndicator()))
          else if (error != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 32),
              child: Column(children: [
                Text(error!, textAlign: TextAlign.center),
                const SizedBox(height: 12),
                OutlinedButton(onPressed: alRecargar, child: const Text('Reintentar')),
              ]),
            )
          else if (hijos.isEmpty && vacio != null)
            Padding(
              padding: const EdgeInsets.symmetric(vertical: 32),
              child: Text(vacio!, textAlign: TextAlign.center, style: const TextStyle(color: HuataColors.muted)),
            )
          else
            ...hijos,
        ],
      ),
    );
  }
}

/// Etiqueta a la izquierda y valor a la derecha, en una fila.
class FilaDato extends StatelessWidget {
  const FilaDato(this.etiqueta, this.valor, {super.key, this.destacado = false, this.color});
  final String etiqueta;
  final String valor;
  final bool destacado;
  final Color? color;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.symmetric(vertical: 3),
    child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
      Expanded(child: Text(etiqueta, style: const TextStyle(color: HuataColors.muted, fontSize: 13.5))),
      const SizedBox(width: 12),
      Flexible(
        child: Text(
          valor,
          textAlign: TextAlign.right,
          style: TextStyle(
            fontWeight: destacado ? FontWeight.w800 : FontWeight.w600,
            fontSize: destacado ? 15 : 13.5,
            color: color,
          ),
        ),
      ),
    ]),
  );
}

/// Una fila de lista con aspecto de tarjeta: título, subtítulo, algo a la derecha y toque opcional.
class FilaTarjeta extends StatelessWidget {
  const FilaTarjeta({super.key, required this.titulo, this.subtitulo, this.derecha, this.icono, this.onTap});
  final String titulo;
  final String? subtitulo;
  final Widget? derecha;
  final IconData? icono;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 8),
    child: Material(
      color: HuataColors.panel,
      borderRadius: BorderRadius.circular(14),
      child: InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(14),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 12),
          decoration: BoxDecoration(
            borderRadius: BorderRadius.circular(14),
            border: Border.all(color: const Color(0xFFE8EDF3)),
          ),
          child: Row(children: [
            if (icono != null) ...[
              Container(
                width: 36,
                height: 36,
                decoration: BoxDecoration(color: HuataColors.greenSoft, borderRadius: BorderRadius.circular(10)),
                child: Icon(icono, size: 19, color: HuataColors.green),
              ),
              const SizedBox(width: 12),
            ],
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text(titulo, style: const TextStyle(fontWeight: FontWeight.w700, fontSize: 14.5)),
                if (subtitulo != null) ...[
                  const SizedBox(height: 2),
                  Text(subtitulo!, style: const TextStyle(color: HuataColors.muted, fontSize: 12.5)),
                ],
              ]),
            ),
            if (derecha != null) ...[const SizedBox(width: 8), derecha!],
            if (onTap != null) const Icon(Icons.chevron_right, color: HuataColors.muted),
          ]),
        ),
      ),
    ),
  );
}

/// Campo de búsqueda compacto para filtrar listas.
class CampoBusqueda extends StatelessWidget {
  const CampoBusqueda({super.key, required this.alCambiar, this.pista = 'Buscar…', this.controlador});
  final ValueChanged<String> alCambiar;
  final String pista;
  final TextEditingController? controlador;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: TextField(
      controller: controlador,
      onChanged: alCambiar,
      textInputAction: TextInputAction.search,
      decoration: InputDecoration(
        hintText: pista,
        prefixIcon: const Icon(Icons.search),
        isDense: true,
        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
      ),
    ),
  );
}

/// Selector de día (atrás, la fecha, adelante; no deja pasar de hoy).
class SelectorDia extends StatelessWidget {
  const SelectorDia({super.key, required this.dia, required this.alCambiar});
  final DateTime dia;
  final ValueChanged<DateTime> alCambiar;

  @override
  Widget build(BuildContext context) {
    final hoy = DateTime.now();
    final esHoy = dia.year == hoy.year && dia.month == hoy.month && dia.day == hoy.day;
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Row(children: [
        IconButton.outlined(
          tooltip: 'Día anterior',
          onPressed: () => alCambiar(dia.subtract(const Duration(days: 1))),
          icon: const Icon(Icons.chevron_left),
        ),
        Expanded(
          child: OutlinedButton.icon(
            icon: const Icon(Icons.calendar_month_outlined),
            label: Text(esHoy ? 'Hoy · ${fechaCorta(isoDia(dia))}' : fechaCorta(isoDia(dia))),
            onPressed: () async {
              final elegido = await showDatePicker(
                context: context,
                initialDate: dia,
                firstDate: DateTime(2020),
                lastDate: hoy,
              );
              if (elegido != null) alCambiar(elegido);
            },
          ),
        ),
        IconButton.outlined(
          tooltip: 'Día siguiente',
          onPressed: esHoy ? null : () => alCambiar(dia.add(const Duration(days: 1))),
          icon: const Icon(Icons.chevron_right),
        ),
      ]),
    );
  }
}

/// Píldoras de filtro (una elegida).
class FiltroPildoras<T> extends StatelessWidget {
  const FiltroPildoras({super.key, required this.opciones, required this.valor, required this.alCambiar});
  final List<(T, String)> opciones;
  final T valor;
  final ValueChanged<T> alCambiar;

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsets.only(bottom: 12),
    child: SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      child: Row(children: [
        for (final (v, texto) in opciones)
          Padding(
            padding: const EdgeInsets.only(right: 8),
            child: ChoiceChip(label: Text(texto), selected: v == valor, onSelected: (_) => alCambiar(v)),
          ),
      ]),
    ),
  );
}

/// Muestra un error de la API como aviso breve.
void avisar(BuildContext context, String texto) =>
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(texto)));

/// Pide confirmar una acción (regla del cliente: todo cambio se confirma).
Future<bool> confirmar(BuildContext context, {required String titulo, required String mensaje, String si = 'Confirmar'}) async {
  final r = await showDialog<bool>(
    context: context,
    builder: (ctx) => AlertDialog(
      title: Text(titulo),
      content: Text(mensaje),
      actions: [
        TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Cancelar')),
        FilledButton(onPressed: () => Navigator.pop(ctx, true), child: Text(si)),
      ],
    ),
  );
  return r ?? false;
}
