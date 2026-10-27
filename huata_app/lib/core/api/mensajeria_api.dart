import '../models/mensajeria.dart';
import 'api_client.dart';

class MensajeriaApi {
  MensajeriaApi(this._client);
  final ApiClient _client;

  /// Abre la conversación con la planta: trae todo el historial y marca como leídos los mensajes
  /// que mandó Huata.
  Future<ConversacionDetalle> miConversacion() async {
    final r = await _client.dio.get('/mensajeria/mi-conversacion');
    return ConversacionDetalle.fromJson(r.data as Map<String, dynamic>);
  }

  /// Resumen para la pantalla de inicio: cuántos mensajes nuevos hay y los últimos 3. No marca nada
  /// como leído (si lo hiciera, el contador de nuevos siempre diría cero).
  Future<ConversacionDetalle> bandeja() async {
    final r = await _client.dio.get('/mensajeria/mi-conversacion/bandeja');
    return ConversacionDetalle.fromJson(r.data as Map<String, dynamic>);
  }

  /// Con `clientUuid` el envío es idempotente: si se cortó la señal después de que el servidor lo
  /// guardó, el reintento de la cola offline devuelve el mismo mensaje en vez de duplicarlo.
  Future<MensajeProveedor> enviar(String cuerpo, {String? clientUuid}) async {
    final r = await _client.dio.post(
      '/mensajeria/mi-conversacion/mensajes',
      data: {'cuerpo': cuerpo, 'clientUuid': ?clientUuid},
    );
    return MensajeProveedor.fromJson(r.data as Map<String, dynamic>);
  }
}
