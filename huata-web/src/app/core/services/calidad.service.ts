import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import {
  Aspecto,
  AguaAnadida,
  ControlCalidad,
  EstadoIncidencia,
  IncidenciaCalidad,
  LugarAnalisis,
  Olor,
  ParametroCalidad,
  ResultadoCalidad,
  ResultadoLugol,
} from '../models/models';

export interface ControlCalidadRequest {
  /** Recojo analizado, o null si el testeador se adelantó al acopiador (entonces va productorId). */
  recojoId: number | null;
  productorId?: number;
  lugarAnalisis?: LugarAnalisis;
  fechaHora?: string;
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
  aguaAnadida?: AguaAnadida;
  lugol?: ResultadoLugol;
  olor?: Olor;
  aspecto?: Aspecto;
  motivoRechazo?: string;
}

/** Evidencia de un control (backend `EvidenciaCalidadResponse`). */
export interface EvidenciaCalidad {
  id: number;
  controlCalidadId: number | null;
  tipo: 'FOTO' | 'FIRMA' | 'VOUCHER' | 'OTRO';
  urlArchivo: string;
  /** La foto está en el almacén propio y se descarga con el token de la sesión. */
  tieneArchivo: boolean;
  tipoContenido?: string;
  tamanoBytes?: number;
  textoOcr?: string;
  valoresOcr?: string;
  confianzaOcr?: number;
  verificadoPor?: string;
  fecha?: string;
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
  /** Análisis registrados con la app que todavía no se aprobaron (V48). */
  porAprobar(dias = 14) {
    return this.http.get<ControlCalidad[]>(`${API_BASE_URL}/calidad/controles/por-aprobar`, {
      params: { dias },
    });
  }

  /** Aprueba un análisis con advertencia (calidad.aprobar). Aprobar dos veces no cambia quién aprobó. */
  aprobar(controlId: number) {
    return this.http.patch<void>(`${API_BASE_URL}/calidad/controles/${controlId}/aprobar`, {});
  }

  /** Análisis de campo de los últimos días que todavía esperan el recojo del acopiador. */
  controlesSinRecojo(dias = 7) {
    return this.http.get<ControlCalidad[]>(`${API_BASE_URL}/calidad/controles/sin-recojo`, {
      params: { dias },
    });
  }

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

  evidenciasDeControl(controlId: number) {
    return this.http.get<EvidenciaCalidad[]>(`${API_BASE_URL}/calidad/evidencias/control/${controlId}`);
  }

  /**
   * La foto se pide como blob por HttpClient (que pone el token) y no con un <img src> directo: el
   * endpoint exige sesión, y un enlace sin token respondería 401.
   */
  archivoEvidencia(evidenciaId: number) {
    return this.http.get(`${API_BASE_URL}/calidad/evidencias/${evidenciaId}/archivo`, { responseType: 'blob' });
  }

  /** Seguimiento de incidencias/adulteraciones de un proveedor. */
  seguimiento(productorId: number) {
    return this.http.get<IncidenciaCalidad[]>(`${API_BASE_URL}/calidad/seguimiento/${productorId}`);
  }
}
