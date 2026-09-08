import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { Acopiador, PageResponse } from '../models/models';

export interface AcopiadorRequest {
  nombres: string;
  apellidos: string;
  documentoIdentidad?: string;
  telefono?: string;
  usuarioId?: number;
}

@Injectable({ providedIn: 'root' })
export class AcopiadoresService {
  constructor(private readonly http: HttpClient) {}

  listar(q = '', page = 0, size = 20) {
    return this.http.get<PageResponse<Acopiador>>(`${API_BASE_URL}/acopiadores`, { params: { q, page, size } });
  }

  crear(datos: AcopiadorRequest) {
    return this.http.post<Acopiador>(`${API_BASE_URL}/acopiadores`, datos);
  }

  actualizar(id: number, datos: AcopiadorRequest) {
    return this.http.put<Acopiador>(`${API_BASE_URL}/acopiadores/${id}`, datos);
  }

  inactivar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/acopiadores/${id}`);
  }
}
