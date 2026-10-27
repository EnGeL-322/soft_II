/// Reglas de ubicación del acopio (backend V57, `GET /reglas-acopio`). Se configuran en la web,
/// en "Precios y calidad", y se pueden apagar para una demostración sin perderlas.
class ReglasAcopio {
  const ReglasAcopio({
    required this.exigirGeocerca,
    required this.exigirDistancia,
    required this.distanciaMaximaMetros,
  });

  /// Solo se empieza el recorrido y se registra leche dentro del límite de la zona.
  final bool exigirGeocerca;

  /// Con conexión, no se registra leche a más de [distanciaMaximaMetros] del proveedor.
  final bool exigirDistancia;
  final double distanciaMaximaMetros;

  /// Las reglas vigentes (RN-25: 200 m). Rigen mientras no se sepa otra cosa del servidor.
  static const estrictas = ReglasAcopio(
    exigirGeocerca: true,
    exigirDistancia: true,
    distanciaMaximaMetros: 200,
  );

  factory ReglasAcopio.fromJson(Map<String, dynamic> json) => ReglasAcopio(
        exigirGeocerca: json['exigirGeocerca'] as bool? ?? true,
        exigirDistancia: json['exigirDistancia'] as bool? ?? true,
        distanciaMaximaMetros: (json['distanciaMaximaMetros'] as num?)?.toDouble() ?? 200,
      );
}
