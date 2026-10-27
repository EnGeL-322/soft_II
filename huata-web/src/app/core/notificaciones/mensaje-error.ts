import { HttpErrorResponse } from '@angular/common/http';

/** Mensaje para cuando no hay conexión con el servidor (lo usa también el interceptor). */
export const SIN_CONEXION = 'No se pudo conectar con el servidor. Revisa tu conexión e inténtalo de nuevo.';

/**
 * Convierte cualquier error de una petición en un texto que una persona entienda:
 * - sin conexión o tiempo agotado: mensaje de conexión;
 * - 4xx con mensaje del backend (validaciones, reglas de negocio): ese mensaje;
 * - 403: falta de permiso;
 * - 5xx u otros: `porDefecto` (nunca trazas ni códigos técnicos).
 */
export function mensajeDeError(error: unknown, porDefecto = 'No se pudo completar la operación.'): string {
  if (error instanceof HttpErrorResponse || esErrorHttp(error)) {
    const e = error as HttpErrorResponse;
    if (e.status === 0 || e.status === 504) return SIN_CONEXION;
    if (e.status === 403) return 'No tienes permiso para realizar esta acción.';
    const delBackend = typeof e.error?.message === 'string' ? e.error.message.trim() : '';
    if (e.status >= 400 && e.status < 500 && delBackend) return delBackend;
    return porDefecto;
  }
  if ((error as { name?: string })?.name === 'TimeoutError') return SIN_CONEXION;
  return porDefecto;
}

/** Errores de validación por campo que manda el backend (ApiError.fields: { campo: 'mensaje' }). */
export function erroresDeCampo(error: unknown): Record<string, string> {
  const e = error as HttpErrorResponse;
  const campos = e?.error?.fields;
  return campos && typeof campos === 'object' && !Array.isArray(campos) ? campos : {};
}

function esErrorHttp(error: unknown): boolean {
  return !!error && typeof error === 'object' && 'status' in error && 'error' in error;
}
