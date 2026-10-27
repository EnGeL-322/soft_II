/// Espejo de `TrazadoPuntoResponse`: un vértice ordenado de un recorrido pintado.
class TrazadoPunto {
  TrazadoPunto({required this.secuencia, required this.latitud, required this.longitud});

  final int secuencia;
  final double latitud;
  final double longitud;

  factory TrazadoPunto.fromJson(Map<String, dynamic> j) => TrazadoPunto(
    secuencia: j['secuencia'] as int,
    latitud: (j['latitud'] as num).toDouble(),
    longitud: (j['longitud'] as num).toDouble(),
  );

  Map<String, dynamic> toJson() => {'secuencia': secuencia, 'latitud': latitud, 'longitud': longitud};
}

/// Espejo de `TrazadoAcopiadorResponse`: el recorrido que pintó un acopiador (V27).
///
/// `tipo` es `DIA` (el de una fecha) o `PREDETERMINADO` (su recorrido habitual, que administración
/// crea copiando uno del día desde la web).
class TrazadoAcopiador {
  TrazadoAcopiador({
    required this.id,
    required this.acopiadorId,
    required this.acopiadorNombre,
    required this.rutaId,
    required this.rutaNombre,
    required this.tipo,
    required this.sigueRutaDesignada,
    required this.puntos,
    this.zona,
    this.fecha,
    this.observacion,
    this.predeterminadoEn,
  });

  final int id;
  final int acopiadorId;
  final String acopiadorNombre;
  final int rutaId;
  final String rutaNombre;
  final String? zona;
  final String tipo;
  final String? fecha;
  final bool sigueRutaDesignada;
  final String? observacion;
  final String? predeterminadoEn;
  final List<TrazadoPunto> puntos;

  bool get esPredeterminado => tipo == 'PREDETERMINADO';

  factory TrazadoAcopiador.fromJson(Map<String, dynamic> j) => TrazadoAcopiador(
    id: j['id'] as int,
    acopiadorId: j['acopiadorId'] as int,
    acopiadorNombre: j['acopiadorNombre'] as String,
    rutaId: j['rutaId'] as int,
    rutaNombre: j['rutaNombre'] as String,
    zona: j['zona'] as String?,
    tipo: j['tipo'] as String,
    fecha: j['fecha'] as String?,
    sigueRutaDesignada: j['sigueRutaDesignada'] as bool? ?? true,
    observacion: j['observacion'] as String?,
    predeterminadoEn: j['predeterminadoEn'] as String?,
    puntos: (j['puntos'] as List? ?? const [])
        .map((e) => TrazadoPunto.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}

/// Espejo de `MiTrazadoResponse`: todo lo que necesita "Pintar ruta del día" para abrirse.
///
/// Los tres recorridos llegan juntos porque el mapa los necesita los tres: lo que ya pintó hoy, su
/// recorrido habitual (si administración se lo aprobó) y el de la ruta, que es el respaldo de quien
/// no tiene uno propio.
class MiTrazado {
  MiTrazado({
    required this.acopiadorId,
    required this.acopiadorNombre,
    required this.rutaId,
    required this.rutaNombre,
    required this.fecha,
    required this.deLaRuta,
    this.zona,
    this.delDia,
    this.predeterminado,
  });

  final int acopiadorId;
  final String acopiadorNombre;
  final int rutaId;
  final String rutaNombre;
  final String? zona;
  final String fecha;
  final TrazadoAcopiador? delDia;
  final TrazadoAcopiador? predeterminado;
  final List<TrazadoPunto> deLaRuta;

  /// Con qué se parte a dibujar si el acopiador no ha pintado nada hoy: su recorrido habitual si lo
  /// tiene y, si no, la geometría de la ruta. Sin esto, quien "va igualmente por la designada"
  /// tendría que redibujarla punto por punto.
  List<TrazadoPunto> get baseParaPintar =>
      delDia?.puntos ?? predeterminado?.puntos ?? deLaRuta;

  factory MiTrazado.fromJson(Map<String, dynamic> j) => MiTrazado(
    acopiadorId: j['acopiadorId'] as int,
    acopiadorNombre: j['acopiadorNombre'] as String,
    rutaId: j['rutaId'] as int,
    rutaNombre: j['rutaNombre'] as String,
    zona: j['zona'] as String?,
    fecha: j['fecha'] as String,
    delDia: j['delDia'] == null ? null : TrazadoAcopiador.fromJson(j['delDia'] as Map<String, dynamic>),
    predeterminado: j['predeterminado'] == null
        ? null
        : TrazadoAcopiador.fromJson(j['predeterminado'] as Map<String, dynamic>),
    deLaRuta: (j['deLaRuta'] as List? ?? const [])
        .map((e) => TrazadoPunto.fromJson(e as Map<String, dynamic>))
        .toList(),
  );
}
