import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { ControlCalidad, EstadoIncidencia, IncidenciaCalidad, ParametroCalidad, ResultadoCalidad } from '../models/models';

export interface ControlCalidadRequest {
  recojoId: number;
  densidad?: number;
  puntoCongelacion?: number;
  acidez?: number;
  temperatura?: number;
  grasa?: number;
  solidosNoGrasos?: number;
  solidosTotales?: number;
  revisionVisual?: string;
  resultado: ResultadoCalidad;
  equipoUtilizado?: string;
  responsable?: string;
}

@Injectable({ providedIn: 'root' })
export class CalidadService {
  constructor(private readonly http: HttpClient) {}

  parametros() {
    return this.http.get<ParametroCalidad[]>(`${API_BASE_URL}/calidad/parametros`);
  }

  guardarParametro(id: number | null, datos: Partial<ParametroCalidad>) {
    return id
      ? this.http.put<ParametroCalidad>(`${API_BASE_URL}/calidad/parametros/${id}`, datos)
      : this.http.post<ParametroCalidad>(`${API_BASE_URL}/calidad/parametros`, datos);
  }

  registrarControl(datos: ControlCalidadRequest) {
    return this.http.post<ControlCalidad>(`${API_BASE_URL}/calidad/controles`, datos);
  }

  /** Historial de calidad de un proveedor (boton "Historial de calidad" del UI/UX). */
  historial(productorId: number) {
    return this.http.get<ControlCalidad[]>(`${API_BASE_URL}/calidad/historial/${productorId}`);
  }

  incidencias() {
    return this.http.get<IncidenciaCalidad[]>(`${API_BASE_URL}/calidad/incidencias`);
  }

  registrarIncidencia(datos: { productorId: number; recojoId?: number; tipo: string; descripcion?: string }) {
    return this.http.post<IncidenciaCalidad>(`${API_BASE_URL}/calidad/incidencias`, datos);
  }

  cambiarEstadoIncidencia(id: number, estado: EstadoIncidencia) {
    return this.http.patch<IncidenciaCalidad>(`${API_BASE_URL}/calidad/incidencias/${id}/estado`, { estado });
  }

  /** Seguimiento de incidencias/adulteraciones de un proveedor. */
  seguimiento(productorId: number) {
    return this.http.get<IncidenciaCalidad[]>(`${API_BASE_URL}/calidad/seguimiento/${productorId}`);
  }
}
