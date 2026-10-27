/// Valores conocidos de los enums de calidad (backend `org.ramiro.huata.calidad`), para los combos
/// del formulario del testeador. Se listan explícitos y no por reflexión: si el backend agrega un
/// valor, este archivo debe tocarse a propósito (mismo criterio que ya usa la web).
class ResultadoCalidad {
  static const aceptada = 'ACEPTADA';
  static const observada = 'OBSERVADA';
  static const rechazada = 'RECHAZADA';
  static const valores = [aceptada, observada, rechazada];
}

class AguaAnadida {
  static const noEvidenciada = 'NO_EVIDENCIADA';
  static const sospecha = 'SOSPECHA';
  static const confirmada = 'CONFIRMADA';
  static const valores = [noEvidenciada, sospecha, confirmada];
}

class ResultadoLugol {
  static const negativa = 'NEGATIVA';
  static const noRealizada = 'NO_REALIZADA';
  static const positiva = 'POSITIVA';
  static const valores = [negativa, noRealizada, positiva];
}

/// Dónde se analizó la leche (backend `LugarAnalisis`, V47).
class LugarAnalisis {
  static const campo = 'CAMPO';
  static const planta = 'PLANTA';
}

/// Lo que se envía a `POST /api/v1/calidad/controles` (espejo de `ControlCalidadRequest`).
///
/// Desde V47 el testeador puede adelantarse al acopiador (pliego, sección 10): si el recojo del día
/// todavía no existe, el control va con `productorId` y `recojoId` nulo, y el backend lo vincula solo
/// cuando el acopiador registra el recojo de ese proveedor ese día.
class ControlCalidadRequest {
  ControlCalidadRequest({
    this.recojoId,
    this.productorId,
    this.lugarAnalisis = LugarAnalisis.campo,
    this.fechaHora,
    this.densidad,
    this.puntoCongelacion,
    this.temperatura,
    this.grasa,
    this.solidosNoGrasos,
    this.solidosTotales,
    required this.resultado,
    this.equipoUtilizado,
    this.responsable,
    this.aguaAnadida,
    this.lugol,
    this.revisionVisual,
    this.motivoRechazo,
  });

  final int? recojoId;
  final int? productorId;
  final String lugarAnalisis;
  final DateTime? fechaHora;
  final double? densidad;
  final double? puntoCongelacion;
  final double? temperatura;
  final double? grasa;
  final double? solidosNoGrasos;
  final double? solidosTotales;
  final String resultado;
  final String? equipoUtilizado;
  final String? responsable;
  final String? aguaAnadida;
  final String? lugol;
  final String? revisionVisual;
  final String? motivoRechazo;

  Map<String, dynamic> toJson() => {
    if (recojoId != null) 'recojoId': recojoId,
    if (productorId != null) 'productorId': productorId,
    'lugarAnalisis': lugarAnalisis,
    if (fechaHora != null) 'fechaHora': fechaHora!.toUtc().toIso8601String(),
    if (densidad != null) 'densidad': densidad,
    if (puntoCongelacion != null) 'puntoCongelacion': puntoCongelacion,
    if (temperatura != null) 'temperatura': temperatura,
    if (grasa != null) 'grasa': grasa,
    if (solidosNoGrasos != null) 'solidosNoGrasos': solidosNoGrasos,
    if (solidosTotales != null) 'solidosTotales': solidosTotales,
    'resultado': resultado,
    if (equipoUtilizado != null) 'equipoUtilizado': equipoUtilizado,
    if (responsable != null) 'responsable': responsable,
    if (aguaAnadida != null) 'aguaAnadida': aguaAnadida,
    if (lugol != null) 'lugol': lugol,
    if (revisionVisual != null) 'revisionVisual': revisionVisual,
    if (motivoRechazo != null) 'motivoRechazo': motivoRechazo,
  };
}

/// Valores que el OCR propone leyendo el voucher del analizador (SmartScan AI / LACTOMAT — pliego
/// sección 10). Nunca se guardan solos: siempre pasan por la pantalla de confirmación humana antes
/// de convertirse en un [ControlCalidadRequest] (RN-14).
class LecturaVoucherOcr {
  LecturaVoucherOcr({
    this.analizadorSn,
    this.temperatura,
    this.grasa,
    this.solidosNoGrasos,
    this.densidad,
    this.solidosTotales,
    this.aguaAnadidaPorcentaje,
    this.puntoCongelacion,
    this.textoCrudo,
    this.advertencias = const [],
  });

  final String? analizadorSn;
  final double? temperatura;
  final double? grasa;
  final double? solidosNoGrasos;
  final double? densidad;
  final double? solidosTotales;

  /// Porcentaje de agua añadida que imprime el analizador. El backend no tiene columna numérica
  /// para este dato (solo el enum [AguaAnadida]), así que la pantalla lo muestra como referencia
  /// para que el testeador decida; no se traduce solo a SOSPECHA/CONFIRMADA (RN-10, RN-14).
  final double? aguaAnadidaPorcentaje;
  final double? puntoCongelacion;
  final String? textoCrudo;

  /// Avisos para el testeador sobre valores que conviene revisar (p. ej. un signo que el OCR no vio).
  final List<String> advertencias;

  /// Lo que propuso el OCR, para guardarlo junto a la foto y comparar con lo confirmado (RNF-20).
  Map<String, dynamic> valoresPropuestos() => {
    'analizadorSn': ?analizadorSn,
    'temperatura': ?temperatura,
    'grasa': ?grasa,
    'solidosNoGrasos': ?solidosNoGrasos,
    'densidad': ?densidad,
    'solidosTotales': ?solidosTotales,
    'aguaAnadidaPorcentaje': ?aguaAnadidaPorcentaje,
    'puntoCongelacion': ?puntoCongelacion,
  };

  /// Cuántos campos numéricos logró leer: si es 0 la foto no sirvió y conviene repetirla.
  int get camposLeidos => [temperatura, grasa, solidosNoGrasos, densidad, solidosTotales, aguaAnadidaPorcentaje, puntoCongelacion]
      .where((v) => v != null)
      .length;
}

/// Foto del voucher pendiente de subir. Se serializa tal cual en la cola offline: si no hay señal
/// cuando el testeador guarda el control, la foto espera en el teléfono hasta que la haya.
class SubidaVoucher {
  SubidaVoucher({
    required this.controlId,
    required this.rutaFoto,
    required this.clientUuid,
    this.textoOcr,
    this.valoresOcr,
    this.confianzaOcr,
  });

  final int controlId;

  /// Copia de la foto en el almacenamiento de la app (no la temporal de la cámara, que el sistema
  /// puede borrar antes de que vuelva la señal).
  final String rutaFoto;
  final String clientUuid;
  final String? textoOcr;
  final String? valoresOcr;
  final double? confianzaOcr;

  Map<String, dynamic> toJson() => {
    'controlId': controlId,
    'rutaFoto': rutaFoto,
    'clientUuid': clientUuid,
    'textoOcr': ?textoOcr,
    'valoresOcr': ?valoresOcr,
    'confianzaOcr': ?confianzaOcr,
  };

  factory SubidaVoucher.fromJson(Map<String, dynamic> j) => SubidaVoucher(
    controlId: (j['controlId'] as num).toInt(),
    rutaFoto: j['rutaFoto'] as String,
    clientUuid: j['clientUuid'] as String,
    textoOcr: j['textoOcr'] as String?,
    valoresOcr: j['valoresOcr'] as String?,
    confianzaOcr: (j['confianzaOcr'] as num?)?.toDouble(),
  );
}


/// Permiso para aprobar análisis desde la app (backend `calidad.aprobar`, V49). Se concede por rol o
/// por usuario desde la web (Usuarios): la testeadora lo tiene por defecto.
const permisoAprobarCalidad = 'calidad.aprobar';

/// Análisis pendiente de aprobación (subconjunto de `ControlCalidadResponse`).
class AnalisisPorAprobar {
  AnalisisPorAprobar({
    required this.id,
    required this.productorNombre,
    this.productorId,
    this.productorCodigo,
    this.recojoId,
    this.lugarAnalisis,
    required this.resultado,
    this.densidad,
    this.grasa,
    this.temperatura,
    this.responsable,
    this.fechaHora,
    this.advertencia,
    this.aprobadoPor,
  });

  final int id;
  final String productorNombre;
  final int? productorId;
  final String? productorCodigo;
  final int? recojoId;
  final String? lugarAnalisis;
  final String resultado;
  final double? densidad;
  final double? grasa;
  final double? temperatura;
  final String? responsable;
  final DateTime? fechaHora;

  /// Por qué no cumplió el estándar de calidad (V50). Los que lo cumplen se aprueban solos.
  final String? advertencia;

  /// Quién lo aprobó: SISTEMA si cumplió el estándar y se aprobó solo; null si sigue pendiente.
  final String? aprobadoPor;

  bool get aprobado => aprobadoPor != null;

  /// De dónde viene la leche analizada.
  String get origen {
    if (recojoId == null) return 'Esperando recojo';
    return lugarAnalisis == LugarAnalisis.planta ? 'Entrega directa en planta' : 'Recojo en ruta';
  }

  factory AnalisisPorAprobar.fromJson(Map<String, dynamic> j) => AnalisisPorAprobar(
    id: (j['id'] as num).toInt(),
    productorNombre: j['productorNombre'] as String,
    productorId: (j['productorId'] as num?)?.toInt(),
    productorCodigo: j['productorCodigo'] as String?,
    recojoId: (j['recojoId'] as num?)?.toInt(),
    lugarAnalisis: j['lugarAnalisis'] as String?,
    resultado: j['resultado'] as String,
    densidad: (j['densidad'] as num?)?.toDouble(),
    grasa: (j['grasa'] as num?)?.toDouble(),
    temperatura: (j['temperatura'] as num?)?.toDouble(),
    responsable: j['responsable'] as String?,
    fechaHora: j['fechaHora'] == null ? null : DateTime.parse(j['fechaHora'] as String).toLocal(),
    advertencia: j['advertencia'] as String?,
    aprobadoPor: j['aprobadoPor'] as String?,
  );
}
