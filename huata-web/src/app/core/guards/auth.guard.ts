import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  if (auth.autenticado()) {
    return true;
  }
  inject(Router).navigateByUrl('/login');
  return false;
};

/** Guard de fabrica: roleGuard('ADMINISTRADOR') restringe una ruta a ese rol. */
export function roleGuard(...rolesPermitidos: string[]): CanActivateFn {
  return () => {
    const auth = inject(AuthService);
    if (rolesPermitidos.some((rol) => auth.tieneRol(rol))) {
      return true;
    }
    inject(Router).navigateByUrl('/dashboard');
    return false;
  };
}
