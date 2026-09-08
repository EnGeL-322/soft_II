import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { EstadoLiquidacion, Liquidacion, PageResponse, TarifaLeche } from '../models/models';

@Injectable({ providedIn: 'root' })
export class LiquidacionesService {
  constructor(private readonly http: HttpClient) {}

  listar(opts: { productorId?: number; estado?: EstadoLiquidacion; page?: number; size?: number }) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.productorId) params['productorId'] = String(opts.productorId);
    if (opts.estado) params['estado'] = opts.estado;
    return this.http.get<PageResponse<Liquidacion>>(`${API_BASE_URL}/liquidaciones`, { params });
  }

  obtener(id: number) {
    return this.http.get<Liquidacion>(`${API_BASE_URL}/liquidaciones/${id}`);
  }

  calcular(datos: { productorId: number; periodoInicio: string; periodoFin: string }) {
    return this.http.post<Liquidacion>(`${API_BASE_URL}/liquidaciones/calcular`, datos);
  }

  cambiarEstado(id: number, estado: EstadoLiquidacion) {
    return this.http.patch<Liquidacion>(`${API_BASE_URL}/liquidaciones/${id}/estado`, { estado });
  }

  tarifas() {
    return this.http.get<TarifaLeche[]>(`${API_BASE_URL}/tarifas-leche`);
  }

  crearTarifa(datos: { precioNormal: number; precioObservada: number; vigenteDesde: string; vigenteHasta?: string }) {
    return this.http.post<TarifaLeche>(`${API_BASE_URL}/tarifas-leche`, datos);
  }
}
