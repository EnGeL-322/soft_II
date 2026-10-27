import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { ModuloAcceso, PageResponse, RolInfo, Usuario } from '../models/models';

export interface UsuarioRequest {
  nombreCompleto: string;
  username: string;
  email?: string;
  telefono?: string;
  password?: string;
  roles: string[];
  /** Excepciones de esta persona respecto de sus roles. Omitirlas = no tocarlas. */
  permisosConcedidos?: string[];
  permisosRevocados?: string[];
}

export interface RolRequest {
  etiqueta: string;
  descripcion?: string;
  permisos: string[];
}

export interface ResultadoEliminacion {
  nombre: string;
  conservaHistorial: boolean;
  mensajes: number;
}

export type EstadoUsuarios = 'ACTIVOS' | 'INACTIVOS' | 'TODOS';

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  constructor(private readonly http: HttpClient) {}

  listar(q = '', page = 0, size = 20, estado: EstadoUsuarios = 'ACTIVOS', internos = false) {
    return this.http.get<PageResponse<Usuario>>(`${API_BASE_URL}/usuarios`, {
      params: { q, page, size, estado, internos },
    });
  }

  obtener(id: number) {
    return this.http.get<Usuario>(`${API_BASE_URL}/usuarios/${id}`);
  }

  crear(datos: UsuarioRequest) {
    return this.http.post<Usuario>(`${API_BASE_URL}/usuarios`, datos);
  }

  actualizar(id: number, datos: UsuarioRequest) {
    return this.http.put<Usuario>(`${API_BASE_URL}/usuarios/${id}`, datos);
  }

  /** Elimina la cuenta. Si escribió mensajes a proveedores, se conserva solo como autor de ellos. */
  eliminar(id: number) {
    return this.http.delete<ResultadoEliminacion>(`${API_BASE_URL}/usuarios/${id}`);
  }

  /** Solo las excepciones de permisos (cuentas de acopiador: datos y rol se editan en su módulo). */
  actualizarPermisos(id: number, permisosConcedidos: string[], permisosRevocados: string[]) {
    return this.http.put<Usuario>(`${API_BASE_URL}/usuarios/${id}/permisos`, { permisosConcedidos, permisosRevocados });
  }

  reactivar(id: number) {
    return this.http.post<Usuario>(`${API_BASE_URL}/usuarios/${id}/reactivar`, {});
  }

  /** Todos los roles activos, con sus permisos y cuántos usuarios los tienen. */
  roles() {
    return this.http.get<RolInfo[]>(`${API_BASE_URL}/roles`);
  }

  catalogoVistas() {
    return this.http.get<ModuloAcceso[]>(`${API_BASE_URL}/roles/catalogo-vistas`);
  }

  crearRol(datos: RolRequest) {
    return this.http.post<RolInfo>(`${API_BASE_URL}/roles`, datos);
  }

  actualizarRol(id: number, datos: RolRequest) {
    return this.http.put<RolInfo>(`${API_BASE_URL}/roles/${id}`, datos);
  }

  restaurarRol(id: number) {
    return this.http.post<RolInfo>(`${API_BASE_URL}/roles/${id}/restaurar`, {});
  }

  eliminarRol(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/roles/${id}`);
  }
}
