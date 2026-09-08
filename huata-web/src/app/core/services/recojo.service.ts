import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { EstadoRecojo, PageResponse, Recojo } from '../models/models';

export interface RecojoRequest {
  productorId: number;
  acopiadorId?: number;
  rutaId?: number;
  fechaHora: string;
  litros: number;
  temperatura?: number;
}

@Injectable({ providedIn: 'root' })
export class RecojoService {
  constructor(private readonly http: HttpClient) {}

  listar(opts: { productorId?: number; rutaId?: number; estado?: EstadoRecojo; page?: number; size?: number }) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.productorId) params['productorId'] = String(opts.productorId);
    if (opts.rutaId) params['rutaId'] = String(opts.rutaId);
    if (opts.estado) params['estado'] = opts.estado;
    return this.http.get<PageResponse<Recojo>>(`${API_BASE_URL}/recojos`, { params });
  }

  registrar(datos: RecojoRequest) {
    return this.http.post<Recojo>(`${API_BASE_URL}/recojos`, datos);
  }

  cancelar(id: number, motivo: string) {
    return this.http.patch<Recojo>(`${API_BASE_URL}/recojos/${id}/cancelar`, { motivo });
  }
}
