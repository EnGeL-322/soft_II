import 'package:flutter/material.dart';

import '../../core/models/recojo.dart';
import '../../core/models/ruta.dart';
import '../../core/theme/huata_theme.dart';

/// Estado de la visita del día (RF-75): blanco/pendiente, verde/acopiado, rojo/no entregado.
enum EstadoVisita {
  pendiente('Pendiente', 'Pendientes', HuataColors.blue, HuataColors.blueSoft),
  acopiado('Acopiado', 'Acopiados', HuataColors.estadoAcopiado, HuataColors.greenSoft),
  noEntregado('No entregó', 'No entregaron', HuataColors.estadoNoEntregado, HuataColors.redSoft);

  const EstadoVisita(this.etiqueta, this.plural, this.color, this.fondo);
  final String etiqueta;
  final String plural;
  final Color color;
  final Color fondo;
}

/// Un proveedor de la ruta de hoy con lo que ya pasó en su visita.
class ParadaRuta {
  ParadaRuta(this.proveedor, this.estado, this.recojo);
  final ProveedorDeRuta proveedor;
  EstadoVisita estado;
  Recojo? recojo;

  /// Solo informativo: resultado del testeo de hoy (ACEPTADA/OBSERVADA/RECHAZADA), '' si aún falta
  /// testearlo, o null si no se pudo consultar (sin señal). No cambia el estado de la visita.
  String? testeoHoy;

  /// Recojo guardado en el teléfono que todavía no llegó al servidor.
  bool get sinSincronizar => recojo?.estado == 'PENDING_SYNC';

  String get numero => proveedor.ordenEnRuta?.toString() ?? '–';
}

/// Texto y color del testeo de hoy, para la etiqueta de la lista y la insignia del mapa.
(String, Color) textoTesteo(String resultado) => switch (resultado) {
  '' => ('Falta testear', HuataColors.amber),
  'ACEPTADA' => ('Testeo: buena', HuataColors.green),
  'OBSERVADA' => ('Testeo: observada', HuataColors.amber),
  'RECHAZADA' => ('Testeo: mala', HuataColors.red),
  _ => ('Testeado', HuataColors.green),
};

const _dias = ['lunes', 'martes', 'miércoles', 'jueves', 'viernes', 'sábado', 'domingo'];
const _meses = ['ene', 'feb', 'mar', 'abr', 'may', 'jun', 'jul', 'ago', 'set', 'oct', 'nov', 'dic'];

/// "lunes 27 oct" (sin depender de que intl tenga cargado el idioma).
String fechaLarga(DateTime d) => '${_dias[d.weekday - 1]} ${d.day} ${_meses[d.month - 1]}';

/// "27 oct" a partir de "2026-10-27"; devuelve el texto tal cual si no tiene ese formato.
String fechaCortaIso(String iso) {
  final d = DateTime.tryParse(iso);
  return d == null ? iso : '${d.day} ${_meses[d.month - 1]}';
}

/// Inicial del día de la semana ("Lu", "Ma"...) para las barras del reporte semanal.
String diaCortoIso(String iso) {
  final d = DateTime.tryParse(iso);
  if (d == null) return iso;
  final n = _dias[d.weekday - 1];
  return '${n[0].toUpperCase()}${n[1]}';
}

String formatoLitros(double l) => '${l.toStringAsFixed(1)} L';

/// "350 m" o "1,2 km".
String formatoDistancia(double metros) =>
    metros < 1000 ? '${metros.round()} m' : '${(metros / 1000).toStringAsFixed(1).replaceAll('.', ',')} km';

/// "Testeo: buena" o "Falta testear" bajo el proveedor. Solo se muestra; no bloquea nada.
class EtiquetaTesteo extends StatelessWidget {
  const EtiquetaTesteo({super.key, required this.resultado});
  final String resultado;

  @override
  Widget build(BuildContext context) {
    final (texto, color) = textoTesteo(resultado);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(resultado.isEmpty ? Icons.science_outlined : Icons.science, size: 13, color: color),
        const SizedBox(width: 4),
        Text(texto, style: TextStyle(color: color, fontSize: 12, fontWeight: FontWeight.w600)),
      ],
    );
  }
}

/// Estado vacío o de error con ícono, título y acción opcional.
class EstadoVacio extends StatelessWidget {
  const EstadoVacio({super.key, required this.icono, required this.titulo, required this.mensaje, this.accion});

  final IconData icono;
  final String titulo;
  final String mensaje;
  final Widget? accion;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 32),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(
            width: 64,
            height: 64,
            decoration: const BoxDecoration(color: HuataColors.greenSoft, shape: BoxShape.circle),
            child: Icon(icono, size: 30, color: HuataColors.green),
          ),
          const SizedBox(height: 16),
          Text(titulo, textAlign: TextAlign.center, style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w700)),
          const SizedBox(height: 6),
          Text(mensaje, textAlign: TextAlign.center, style: const TextStyle(color: HuataColors.muted, height: 1.4)),
          if (accion != null) ...[const SizedBox(height: 18), accion!],
        ],
      ),
    );
  }
}
