import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import {
  AsignacionRuta,
  PageResponse,
  PlanRutaDia,
  ProveedorDeRuta,
  PuntoAcopioAgregado,
  Ruta,
  RutaPunto,
  ZonaAsignacion,
  ZonaPoligono,
  ZonaPoligonoPunto,
  ZonaPoligonoVersion,
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class RutasService {
  constructor(private readonly http: HttpClient) {}

  listar(q = '', page = 0, size = 50) {
    return this.http.get<PageResponse<Ruta>>(`${API_BASE_URL}/rutas`, { params: { q, page, size } });
  }

  zonasAsignadas() {
    return this.http.get<ZonaAsignacion[]>(`${API_BASE_URL}/zonas/asignaciones`);
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

  // ---- Geometría de la ruta: dibujar/editar/guardar/eliminar en el mapa (2.1.3-2.1.5) ----

  puntos(rutaId: number) {
    return this.http.get<RutaPunto[]>(`${API_BASE_URL}/rutas/${rutaId}/puntos`);
  }

  guardarPuntos(rutaId: number, puntos: RutaPunto[]) {
    return this.http.put<RutaPunto[]>(`${API_BASE_URL}/rutas/${rutaId}/puntos`, { puntos });
  }

  eliminarPuntos(rutaId: number) {
    return this.http.delete<void>(`${API_BASE_URL}/rutas/${rutaId}/puntos`);
  }

  /**
   * Punto de acopio dinámico: se agrega por latitud/longitud sin rehacer el trazo, y de paso
   * reordena a los proveedores por cercanía real a ese punto (vecino más cercano) — es el "cambio
   * de destino" que decide el administrador.
   */
  agregarPuntoDeAcopio(rutaId: number, latitud: number, longitud: number) {
    return this.http.post<PuntoAcopioAgregado>(`${API_BASE_URL}/rutas/${rutaId}/puntos/agregar`, {
      latitud,
      longitud,
    });
  }

  /** Reordena por cercanía sin agregar un punto nuevo al trazo (origen opcional). */
  reordenarPorCercania(rutaId: number, origen?: { latitud: number; longitud: number }) {
    return this.http.post<ProveedorDeRuta[]>(`${API_BASE_URL}/rutas/${rutaId}/reordenar`, origen ?? {});
  }

  // ---- Proveedores de la ruta, en orden de visita (2.1.6) ----

  proveedores(rutaId: number) {
    return this.http.get<ProveedorDeRuta[]>(`${API_BASE_URL}/rutas/${rutaId}/proveedores`);
  }

  // ---- Polígono de zona: dibuja el territorio en el mapa de Rutas y movilidad ----

  poligonosDeZona() {
    return this.http.get<ZonaPoligono[]>(`${API_BASE_URL}/zonas/poligonos`);
  }

  guardarPoligonoDeZona(zona: string, puntos: ZonaPoligonoPunto[], color?: string) {
    return this.http.put<ZonaPoligono>(`${API_BASE_URL}/zonas/poligonos`, { zona, color, puntos });
  }

  eliminarPoligonoDeZona(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/zonas/poligonos/${id}`);
  }

  /** Versiones anteriores del límite: cada cambio o borrado deja una copia restaurable. */
  historialDeLimite(zona: string) {
    return this.http.get<ZonaPoligonoVersion[]>(`${API_BASE_URL}/zonas/poligonos/historial`, {
      params: { zona },
    });
  }

  restaurarLimite(versionId: number) {
    return this.http.post<ZonaPoligono>(
      `${API_BASE_URL}/zonas/poligonos/historial/${versionId}/restaurar`,
      {},
    );
  }

  // ---- La zona como tal: renombrar y dar de baja ----
  // El nombre de la zona vive a la vez en Ruta.zona y en ZonaPoligono.zona, así que ambas
  // operaciones se resuelven en el backend en una sola transacción y no encadenando PUTs aquí.

  renombrarZona(zona: string, nuevoNombre: string) {
    return this.http.patch<void>(`${API_BASE_URL}/zonas`, { zona, nuevoNombre });
  }

  eliminarZona(zona: string) {
    return this.http.delete<void>(`${API_BASE_URL}/zonas`, { params: { zona } });
  }
}
