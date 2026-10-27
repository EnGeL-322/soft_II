/// Espejo de `LiquidacionResponse` (backend `org.ramiro.huata.liquidaciones.LiquidacionDtos`).
/// Solo los campos que necesita "mi historial de pagos" (RF-81); no el detalle línea a línea.
class Liquidacion {
  Liquidacion({
    required this.id,
    required this.periodoInicio,
    required this.periodoFin,
    required this.litrosTotales,
    required this.montoTotal,
    required this.montoNeto,
    required this.estado,
  });

  final int id;
  final String periodoInicio;
  final String periodoFin;
  final double litrosTotales;
  final double montoTotal;
  final double montoNeto;
  final String estado; // CALCULATED | REVIEWED | CLOSED

  factory Liquidacion.fromJson(Map<String, dynamic> j) => Liquidacion(
    id: j['id'] as int,
    periodoInicio: j['periodoInicio'] as String,
    periodoFin: j['periodoFin'] as String,
    litrosTotales: (j['litrosTotales'] as num).toDouble(),
    montoTotal: (j['montoTotal'] as num).toDouble(),
    montoNeto: (j['montoNeto'] as num).toDouble(),
    estado: j['estado'] as String,
  );
}

/// Espejo de `LiquidacionService.FilaPlanilla` (cobro estimado del ciclo en curso, RF-80). El monto
/// es un estimado hasta que se cierre el ciclo: puede diferir de la liquidación final si después se
/// detecta una incidencia de calidad retroactiva sobre entregas ya contadas aquí.
class CobroEstimado {
  CobroEstimado({
    required this.zona,
    required this.excelencia,
    required this.litrosEntregados,
    required this.litrosRechazados,
    required this.litrosPendientes,
    required this.tarifaAplicada,
    required this.importePendiente,
    required this.liquidado,
  });

  final String? zona;
  final bool excelencia;
  final double litrosEntregados;
  final double litrosRechazados;
  final double litrosPendientes;
  final double tarifaAplicada;
  final double importePendiente;
  final bool liquidado;

  factory CobroEstimado.fromJson(Map<String, dynamic> j) => CobroEstimado(
    zona: j['zona'] as String?,
    excelencia: j['excelencia'] as bool? ?? false,
    litrosEntregados: (j['litrosEntregados'] as num).toDouble(),
    litrosRechazados: (j['litrosRechazados'] as num).toDouble(),
    litrosPendientes: (j['litrosPendientes'] as num).toDouble(),
    tarifaAplicada: (j['tarifaAplicada'] as num).toDouble(),
    importePendiente: (j['importePendiente'] as num).toDouble(),
    liquidado: j['liquidado'] as bool? ?? false,
  );
}

class ResumenPagoPeriodo {
  ResumenPagoPeriodo({
    required this.desde,
    required this.hasta,
    required this.litrosProducidos,
    required this.litrosPagados,
    required this.litrosPendientes,
    required this.montoPagado,
    required this.montoPendiente,
    required this.entregas,
    required this.aceptadas,
    required this.observadas,
    required this.rechazadas,
  });

  final String desde;
  final String hasta;
  final double litrosProducidos;
  final double litrosPagados;
  final double litrosPendientes;
  final double montoPagado;
  final double montoPendiente;
  final int entregas;
  final int aceptadas;
  final int observadas;
  final int rechazadas;

  factory ResumenPagoPeriodo.fromJson(Map<String, dynamic> j) => ResumenPagoPeriodo(
    desde: j['desde'] as String,
    hasta: j['hasta'] as String,
    litrosProducidos: (j['litrosProducidos'] as num).toDouble(),
    litrosPagados: (j['litrosPagados'] as num).toDouble(),
    litrosPendientes: (j['litrosPendientes'] as num).toDouble(),
    montoPagado: (j['montoPagado'] as num).toDouble(),
    montoPendiente: (j['montoPendiente'] as num).toDouble(),
    entregas: j['entregas'] as int,
    aceptadas: j['aceptadas'] as int,
    observadas: j['observadas'] as int,
    rechazadas: j['rechazadas'] as int,
  );
}
