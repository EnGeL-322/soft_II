import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { UbicacionGps } from '../models/models';

/** GPS real: reporta y consulta posiciones capturadas con la Geolocation API del navegador. */
@Injectable({ providedIn: 'root' })
export class UbicacionesService {
  constructor(private readonly http: HttpClient) {}

  reportar(latitud: number, longitud: number, precisionMetros?: number, velocidadMs?: number) {
    return this.http.post<UbicacionGps>(`${API_BASE_URL}/ubicaciones`, { latitud, longitud, precisionMetros, velocidadMs });
  }

  ultimas() {
    return this.http.get<UbicacionGps[]>(`${API_BASE_URL}/ubicaciones/ultimas`);
  }

  historial(acopiadorId: number, desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<UbicacionGps[]>(`${API_BASE_URL}/ubicaciones/acopiador/${acopiadorId}`, { params });
  }
}
