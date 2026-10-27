import '../models/produccion.dart';
import '../models/lote_queso.dart';
import 'api_client.dart';
import 'rutas_api.dart' show FechaIso;

class QuesosApi {
  QuesosApi(this._client);
  final ApiClient _client;

  /// El backend no filtra "lotes donde participé": `LoteQuesoResponse.responsable` es un texto libre
  /// fijado al crear el lote, no una relación con el usuario logueado. Esta app trae todos los lotes
  /// del rango de fechas (mismo endpoint que usa la web) — no hay forma de acotar "los míos" sin
  /// cambiar esa respuesta en el backend.
  Future<List<LoteQueso>> lotes({DateTime? desde, DateTime? hasta}) async {
    final r = await _client.dio.get(
      '/quesos/lotes',
      queryParameters: {
        if (desde != null) 'desde': _fecha(desde),
        if (hasta != null) 'hasta': _fecha(hasta),
      },
    );
    return (r.data as List).map((e) => LoteQueso.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Registra un lote (3.2/RF-67/RF-69). No manda consumos ni litros: el backend descuenta
  /// exactamente receta × `cantidadObjetivo` y los litros son la leche que pide la receta. Todo en una
  /// sola transacción; esta app no calcula ningún costo ni descuento.
  Future<LoteQueso> crearLote({
    required DateTime fecha,
    required int productoId,
    required String origenLeche,
    required int cantidadObjetivo,
    required int unidadesProducidas,
    required String responsable,
    String? observaciones,
    required int recetaId,
    List<ManoObraItem> manoObra = const [],
  }) async {
    final r = await _client.dio.post(
      '/quesos/lotes',
      data: {
        'fecha': fecha.toIsoDate(),
        'productoId': productoId,
        'origenLeche': origenLeche,
        'cantidadObjetivo': cantidadObjetivo,
        'unidadesProducidas': unidadesProducidas,
        'responsable': responsable,
        if (observaciones != null && observaciones.isNotEmpty) 'observaciones': observaciones,
        'recetaId': recetaId,
        'manoObra': manoObra.map((m) => m.toJson()).toList(),
      },
    );
    return LoteQueso.fromJson(r.data as Map<String, dynamic>);
  }

  String _fecha(DateTime d) =>
      '${d.year.toString().padLeft(4, '0')}-${d.month.toString().padLeft(2, '0')}-${d.day.toString().padLeft(2, '0')}';
}
