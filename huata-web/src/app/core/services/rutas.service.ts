import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { AsignacionRuta, CentroAcopio, PageResponse, PlanRutaDia, Ruta } from '../models/models';

@Injectable({ providedIn: 'root' })
export class RutasService {
  constructor(private readonly http: HttpClient) {}

  listar(q = '', page = 0, size = 50) {
    return this.http.get<PageResponse<Ruta>>(`${API_BASE_URL}/rutas`, { params: { q, page, size } });
  }

  crear(datos: Partial<Ruta>) {
    return this.http.post<Ruta>(`${API_BASE_URL}/rutas`, datos);
  }

  actualizar(id: number, datos: Partial<Ruta>) {
    return this.http.put<Ruta>(`${API_BASE_URL}/rutas/${id}`, datos);
  }

  inactivar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/rutas/${id}`);
  }

  listarCentros(q = '', page = 0, size = 50) {
    return this.http.get<PageResponse<CentroAcopio>>(`${API_BASE_URL}/centros-acopio`, { params: { q, page, size } });
  }

  crearCentro(datos: { nombre: string; ubicacion?: string; rutaId?: number }) {
    return this.http.post<CentroAcopio>(`${API_BASE_URL}/centros-acopio`, datos);
  }

  inactivarCentro(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/centros-acopio/${id}`);
  }

  asignaciones(fecha: string, acopiadorId?: number) {
    const params: Record<string, string> = { fecha };
    if (acopiadorId) params['acopiadorId'] = String(acopiadorId);
    return this.http.get<AsignacionRuta[]>(`${API_BASE_URL}/asignaciones-ruta`, { params });
  }

  asignar(datos: { rutaId: number; acopiadorId: number; fecha: string; movilidad?: string }) {
    return this.http.post<AsignacionRuta>(`${API_BASE_URL}/asignaciones-ruta`, datos);
  }

  actualizarAsignacion(id: number, datos: { movilidad?: string; observacion?: string }) {
    return this.http.patch<AsignacionRuta>(`${API_BASE_URL}/asignaciones-ruta/${id}`, datos);
  }

  planOperativo(fecha: string) {
    return this.http.get<PlanRutaDia[]>(`${API_BASE_URL}/rutas/plan-operativo`, { params: { fecha } });
  }
}
