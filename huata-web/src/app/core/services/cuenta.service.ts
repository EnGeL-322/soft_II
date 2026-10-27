import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { tap } from 'rxjs';
import { API_BASE_URL } from '../config/api-config';
import { AuthService, LoginResponse } from './auth.service';

/** Mis datos (`GET /cuenta`). */
export interface MiCuenta {
  nombreCompleto: string;
  username: string;
  email?: string | null;
  telefono?: string | null;
  roles: string[];
  ultimoAcceso?: string | null;
  /** Proveedor y acopiador no: su nombre identifica sus pagos y lo corrige la planta en su ficha. */
  puedeEditarNombre: boolean;
}

/** Mínimo de caracteres de la contraseña; el backend aplica la misma regla. */
export const CLAVE_MINIMA = 8;

/** "Mi cuenta": siempre la cuenta del token, nunca la de otra persona. */
@Injectable({ providedIn: 'root' })
export class CuentaService {
  constructor(
    private readonly http: HttpClient,
    private readonly auth: AuthService,
  ) {}

  obtener() {
    return this.http.get<MiCuenta>(`${API_BASE_URL}/cuenta`);
  }

  actualizarDatos(datos: { nombreCompleto: string; email: string; telefono: string }) {
    return this.http
      .put<MiCuenta>(`${API_BASE_URL}/cuenta/datos`, datos)
      .pipe(tap(() => this.auth.refrescarSesion()));
  }

  cambiarClave(actual: string, nueva: string) {
    return this.http.put<void>(`${API_BASE_URL}/cuenta/clave`, { actual, nueva });
  }

  /** La respuesta trae la sesión nueva y se aplica en el acto: el token anterior ya no vale. */
  cambiarUsuario(clave: string, nuevoUsuario: string) {
    return this.http
      .put<LoginResponse>(`${API_BASE_URL}/cuenta/usuario`, { clave, nuevoUsuario })
      .pipe(tap((sesion) => this.auth.aplicarSesion(sesion)));
  }
}
