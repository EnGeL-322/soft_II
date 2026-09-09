import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { Queso } from '../models/models';

export interface QuesoRequest {
  fecha: string;
  fuente: string;
  producidosKg: number;
  vendidosKg?: number;
  totalVenta?: number;
  observaciones?: string;
  comprador?: string;
  numeroBoleta?: string;
}

@Injectable({ providedIn: 'root' })
export class QuesosService {
  constructor(private readonly http: HttpClient) {}

  listar(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<Queso[]>(`${API_BASE_URL}/quesos`, { params });
  }

  crear(datos: QuesoRequest) {
    return this.http.post<Queso>(`${API_BASE_URL}/quesos`, datos);
  }

  actualizar(id: number, datos: QuesoRequest) {
    return this.http.put<Queso>(`${API_BASE_URL}/quesos/${id}`, datos);
  }

  eliminar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/quesos/${id}`);
  }
}
