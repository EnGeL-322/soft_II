import 'package:dio/dio.dart';

/// Traduce el cuerpo de error uniforme del backend
/// (`{timestamp, status, error, message, path}` — ver `ApiExceptionHandler` /
/// `RestAuthenticationHandlers` en el backend) a algo que la UI pueda mostrar directo.
class ApiException implements Exception {
  ApiException(this.statusCode, this.message);

  final int? statusCode;
  final String message;

  bool get esNoAutenticado => statusCode == 401;
  bool get esSinPermiso => statusCode == 403;

  factory ApiException.from(DioException e) {
    final data = e.response?.data;
    if (data is Map && data['message'] is String) {
      return ApiException(e.response?.statusCode, data['message'] as String);
    }
    if (e.type == DioExceptionType.connectionError || e.type == DioExceptionType.connectionTimeout) {
      return ApiException(null, 'Sin conexión con el servidor. Se guardó localmente si aplica.');
    }
    return ApiException(e.response?.statusCode, 'No se pudo completar la solicitud.');
  }

  @override
  String toString() => message;
}
