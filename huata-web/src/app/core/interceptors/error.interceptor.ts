import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { SIN_CONEXION } from '../notificaciones/mensaje-error';
import { NotificacionesService } from '../notificaciones/notificaciones.service';

/**
 * Errores comunes a todas las peticiones:
 * - 401: el token ya no vale; cierra la sesión local y vuelve al login.
 * - Sin conexión (status 0) o tiempo agotado (504): una sola notificación de conexión, aunque
 *   fallen varias peticiones a la vez (el servicio no repite mensajes idénticos).
 * El resto lo maneja cada pantalla junto a su formulario, para no duplicar mensajes.
 */
export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const notificaciones = inject(NotificacionesService);
  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (error.status === 401) {
        auth.logout();
      } else if (error.status === 0 || error.status === 504) {
        notificaciones.error(SIN_CONEXION);
      }
      return throwError(() => error);
    }),
  );
};
