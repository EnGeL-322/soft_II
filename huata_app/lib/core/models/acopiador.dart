/// Espejo de `AcopiadorResponse`.
class Acopiador {
  Acopiador({
    required this.id,
    required this.nombres,
    required this.apellidos,
    this.documentoIdentidad,
    this.telefono,
    this.rutaId,
    this.rutaNombre,
    this.zona,
    required this.activo,
  });

  final int id;
  final String nombres;
  final String apellidos;
  final String? documentoIdentidad;
  final String? telefono;
  final int? rutaId;
  final String? rutaNombre;
  final String? zona;
  final bool activo;

  String get nombreCompleto => '$nombres $apellidos';

  factory Acopiador.fromJson(Map<String, dynamic> j) => Acopiador(
    id: j['id'] as int,
    nombres: j['nombres'] as String,
    apellidos: j['apellidos'] as String,
    documentoIdentidad: j['documentoIdentidad'] as String?,
    telefono: j['telefono'] as String?,
    rutaId: j['rutaId'] as int?,
    rutaNombre: j['rutaNombre'] as String?,
    zona: j['zona'] as String?,
    activo: j['activo'] as bool? ?? true,
  );
}
