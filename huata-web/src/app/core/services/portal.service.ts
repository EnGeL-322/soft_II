import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { ConsultaPublica, ResumenPublicoHoy, ZonaCalidadPublica } from '../models/models';

/** Sin autenticación: el mismo backend expone /api/v1/public/** con permitAll (ver SecurityConfig). */
@Injectable({ providedIn: 'root' })
export class PortalService {
  constructor(private readonly http: HttpClient) {}

  consultar(codigo: string) {
    return this.http.get<ConsultaPublica>(`${API_BASE_URL}/public/productores/${codigo}/calidad`);
  }

  /** RF-48: qué zonas produjeron leche apta/observada/con adulteración hoy. */
  zonasDelDia(fecha?: string) {
    const params = fecha ? { fecha } : undefined;
    return this.http.get<ZonaCalidadPublica[]>(`${API_BASE_URL}/public/calidad/zonas`, { params });
  }

  /** RF-49: zonas + proveedores con incidencia de calidad confirmada hoy. */
  incidenciasDeHoy(fecha?: string) {
    const params = fecha ? { fecha } : undefined;
    return this.http.get<ResumenPublicoHoy>(`${API_BASE_URL}/public/incidencias/hoy`, { params });
  }
}
