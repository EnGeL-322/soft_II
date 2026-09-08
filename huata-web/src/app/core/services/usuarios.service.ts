import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { PageResponse, Usuario } from '../models/models';

export interface UsuarioRequest {
  nombreCompleto: string;
  username: string;
  email?: string;
  telefono?: string;
  password?: string;
  roles: string[];
}

@Injectable({ providedIn: 'root' })
export class UsuariosService {
  constructor(private readonly http: HttpClient) {}

  listar(q = '', page = 0, size = 20) {
    return this.http.get<PageResponse<Usuario>>(`${API_BASE_URL}/usuarios`, { params: { q, page, size } });
  }

  roles() {
    return this.http.get<string[]>(`${API_BASE_URL}/usuarios/roles`);
  }

  crear(datos: UsuarioRequest) {
    return this.http.post<Usuario>(`${API_BASE_URL}/usuarios`, datos);
  }

  actualizar(id: number, datos: UsuarioRequest) {
    return this.http.put<Usuario>(`${API_BASE_URL}/usuarios/${id}`, datos);
  }

  inactivar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/usuarios/${id}`);
  }
}
