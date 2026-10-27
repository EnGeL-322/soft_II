import '../models/acopiador.dart';
import 'api_client.dart';

class AcopiadoresApi {
  AcopiadoresApi(this._client);
  final ApiClient _client;

  Future<Acopiador> yo() async {
    final r = await _client.dio.get('/acopiadores/me');
    return Acopiador.fromJson(r.data as Map<String, dynamic>);
  }
}
