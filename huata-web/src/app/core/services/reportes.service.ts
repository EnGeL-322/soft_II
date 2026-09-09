import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { DashboardResponse, PuntoSerieDiaria } from '../models/models';

export interface EventoReporte {
  id: string; fecha: string; tipo: string; referencia: string; codigo: string | null;
  zona: string | null; litros: number | null; monto: number | null; estado: string; detalle: string | null;
}

@Injectable({ providedIn: 'root' })
export class ReportesService {
  constructor(private readonly http: HttpClient) {}

  eventos(desde: string, hasta: string) {
    return this.http.get<EventoReporte[]>(`${API_BASE_URL}/reportes/eventos`, { params: { desde, hasta } });
  }

  dashboard(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<DashboardResponse>(`${API_BASE_URL}/reportes/dashboard`, { params });
  }

  serieDiaria(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<PuntoSerieDiaria[]>(`${API_BASE_URL}/reportes/serie-diaria`, { params });
  }
}
