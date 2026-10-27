import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';
import { NAVEGACION, permisosDeItem } from '../config/navegacion';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.autenticado()) {
    return true;
  }
  inject(Router).navigateByUrl('/login');
  return false;
};

/**
 * Primera ruta interna a la que el usuario si puede entrar. Sirve de destino cuando un guard lo
 * rechaza: mandarlo siempre a /dashboard provocaria un bucle infinito de redirecciones con quien no
 * tiene panel.read (el rol PROVEEDOR no tiene ningun permiso interno, su sitio es el portal).
 */
function destinoPorDefecto(auth: AuthService): string {
  const primera = NAVEGACION.find((item) => permisosDeItem(item).some((p) => auth.puede(p)));
  return primera ? `/${primera.ruta}` : '/portal';
}

/**
 * Guard de fabrica por permiso: permisoGuard('pagos.read') deja pasar a quien tenga alguno de los
 * permisos indicados. Es la contraparte de @PreAuthorize en el backend, pero NO es la barrera: su
 * unica funcion es evitar que alguien aterrice en una pantalla que le devolveria 403 en cada
 * peticion. Quien autoriza de verdad es el servidor.
 */
export function permisoGuard(...permisosRequeridos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (auth.puedeAlguno(...permisosRequeridos)) {
      return true;
    }
    inject(Router).navigateByUrl(destinoPorDefecto(auth));
    return false;
  };
}

/**
 * Guard por rol. Se mantiene para lo que dependa del rol como identidad, no para autorizar acceso a
 * un modulo: para eso esta permisoGuard, que es lo que el backend comprueba de verdad.
 */
export function roleGuard(...rolesPermitidos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (rolesPermitidos.some((rol) => auth.tieneRol(rol))) {
      return true;
    }
    inject(Router).navigateByUrl(destinoPorDefecto(auth));
    return false;
  };
}
