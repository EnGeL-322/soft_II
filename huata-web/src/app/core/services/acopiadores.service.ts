import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { Acopiador, PageResponse } from '../models/models';

export interface AcopiadorRequest {
  nombres: string;
  apellidos: string;
  documentoIdentidad?: string;
  telefono?: string;
  rutaId?: number;
  username?: string;
  password?: string;
}

export interface FiltroAcopiadores {
  q?: string;
  zona?: string;
  rutaId?: number;
  page?: number;
  size?: number;
}

@Injectable({ providedIn: 'root' })
export class AcopiadoresService {
  constructor(private readonly http: HttpClient) {}

  listar(q = '', page = 0, size = 20) {
    return this.buscar({ q, page, size });
  }

  /** Con filtros de zona y ruta (pestaña Acopiadores de Usuarios). Lo vacío no se envía. */
  buscar(f: FiltroAcopiadores) {
    const params: Record<string, string> = { page: String(f.page ?? 0), size: String(f.size ?? 20) };
    if (f.q) params['q'] = f.q;
    if (f.zona) params['zona'] = f.zona;
    if (f.rutaId) params['rutaId'] = String(f.rutaId);
    return this.http.get<PageResponse<Acopiador>>(`${API_BASE_URL}/acopiadores`, { params });
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
