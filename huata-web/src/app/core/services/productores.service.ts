import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { EstadoProductor, PageResponse, Productor } from '../models/models';

export interface ProductorRequest {
  codigo: string;
  nombres: string;
  apellidos: string;
  documentoIdentidad?: string;
  telefono?: string;
  rutaId?: number;
  centroAcopioId?: number;
  notas?: string;
}

@Injectable({ providedIn: 'root' })
export class ProductoresService {
  constructor(private readonly http: HttpClient) {}

  listar(opts: { q?: string; estado?: EstadoProductor; rutaId?: number; page?: number; size?: number }) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.q) params['q'] = opts.q;
    if (opts.estado) params['estado'] = opts.estado;
    if (opts.rutaId) params['rutaId'] = String(opts.rutaId);
    return this.http.get<PageResponse<Productor>>(`${API_BASE_URL}/productores`, { params });
  }

  crear(datos: ProductorRequest) {
    return this.http.post<Productor>(`${API_BASE_URL}/productores`, datos);
  }

  actualizar(id: number, datos: ProductorRequest) {
    return this.http.put<Productor>(`${API_BASE_URL}/productores/${id}`, datos);
  }

  cambiarEstado(id: number, estado: EstadoProductor, motivo?: string) {
    return this.http.patch<Productor>(`${API_BASE_URL}/productores/${id}/estado`, { estado, motivo });
  }

  inactivar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/productores/${id}`);
  }
}
