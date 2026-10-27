import 'dart:typed_data';

import 'package:dio/dio.dart';

import '../models/calidad.dart';
import 'api_client.dart';

class CalidadApi {
  CalidadApi(this._client);
  final ApiClient _client;

  /// Devuelve el id del control creado: hace falta para colgarle la foto del voucher.
  Future<int> registrarControl(ControlCalidadRequest request) async {
    final r = await _client.dio.post('/calidad/controles', data: request.toJson());
    return ((r.data as Map<String, dynamic>)['id'] as num).toInt();
  }

  /// Análisis de los últimos días que todavía no se aprobaron (V48).
  Future<List<AnalisisPorAprobar>> porAprobar({int dias = 14}) async {
    final r = await _client.dio.get('/calidad/controles/por-aprobar', queryParameters: {'dias': dias});
    return (r.data as List).map((e) => AnalisisPorAprobar.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Todos los análisis de los últimos días, aprobados o no (los que se aprobaron solos incluidos).
  Future<List<AnalisisPorAprobar>> recientes({int dias = 1}) async {
    final r = await _client.dio.get('/calidad/controles/recientes', queryParameters: {'dias': dias});
    return (r.data as List).map((e) => AnalisisPorAprobar.fromJson(e as Map<String, dynamic>)).toList();
  }

  /// Aprueba un análisis (permiso calidad.aprobar). Aprobar dos veces no cambia quién aprobó.
  Future<void> aprobar(int controlId) async {
    await _client.dio.patch('/calidad/controles/$controlId/aprobar');
  }

  /// Foto del voucher del análisis, o null si no tiene. Se descarga con el token de la sesión.
  Future<Uint8List?> fotoVoucher(int controlId) async {
    final r = await _client.dio.get('/calidad/evidencias/control/$controlId');
    final evidencias = (r.data as List).cast<Map<String, dynamic>>();
    if (evidencias.isEmpty) return null;
    final id = (evidencias.last['id'] as num).toInt();
    final archivo = await _client.dio.get<List<int>>(
      '/calidad/evidencias/$id/archivo',
      options: Options(responseType: ResponseType.bytes),
    );
    return archivo.data == null ? null : Uint8List.fromList(archivo.data!);
  }

  /// Sube la foto del voucher de un control ya guardado (RF-85, RNF-20). Idempotente por
  /// [SubidaVoucher.clientUuid]: la cola offline puede reintentarla sin duplicar la evidencia.
  Future<void> subirVoucher(SubidaVoucher subida) async {
    final datos = FormData.fromMap({
      'foto': await MultipartFile.fromFile(subida.rutaFoto, filename: 'voucher.jpg'),
      'clientUuid': subida.clientUuid,
      if (subida.textoOcr != null) 'textoOcr': subida.textoOcr,
      if (subida.valoresOcr != null) 'valoresOcr': subida.valoresOcr,
      if (subida.confianzaOcr != null) 'confianzaOcr': subida.confianzaOcr.toString(),
    });
    await _client.dio.post(
      '/calidad/controles/${subida.controlId}/voucher',
      data: datos,
      // Una foto de 1-2 MB en una conexión rural tarda más que un JSON.
      options: Options(sendTimeout: const Duration(minutes: 2), receiveTimeout: const Duration(minutes: 1)),
    );
  }
}
