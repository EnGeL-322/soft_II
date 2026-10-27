import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { EstadoRecojo, FuenteRecojo, PageResponse, Recojo, ReporteAcopiador } from '../models/models';

export interface RecojoRequest {
  productorId: number;
  acopiadorId?: number;
  rutaId?: number;
  fechaHora: string;
  litros: number;
  temperatura?: number;
  fuente?: FuenteRecojo;
  numeroEnvases?: number;
  capacidadReferenciaL?: number;
  responsableRecepcion?: string;
  litrosAppMovil?: number;
  /** Ticket del analizador, si se uso "Llamar datos". */
  medicion?: LecturaInstrumento;
}

/**
 * Ticket del analizador de leche (LACTOMAT / SmartScan AI). Todos los campos son opcionales.
 *
 * OJO: `densidadLactometrica` son GRADOS de lactometro (23.9 / 31.4 en los tickets reales), no
 * g/mL; y `aguaAnadidaPct` es el porcentaje medido, no el dictamen de una persona.
 */
export interface LecturaInstrumento {
  analizadorSn?: string;
  modo?: string;
  medidoEn?: string;
  temperatura?: number;
  grasaPct?: number;
  solidosNoGrasosPct?: number;
  densidadLactometrica?: number;
  proteinaPct?: number;
  lactosaPct?: number;
  salesPct?: number;
  solidosTotalesPct?: number;
  aguaAnadidaPct?: number;
  puntoCongelacion?: number;
  ph?: number;
}

/** Lo que el sistema deduce del ticket aplicando la regla 7.5. Se calcula en el servidor. */
export interface EvaluacionCalidad {
  densidadCorregida: number;
  categoria: string;
  categoriaEtiqueta: string;
  resultado: 'ACEPTADA' | 'OBSERVADA' | 'RECHAZADA';
  precio?: number;
}

/** Respuesta de "Llamar datos": el ticket y su evaluacion (null si falta densidad o temperatura). */
export interface LecturaEvaluada {
  lectura: LecturaInstrumento;
  evaluacion?: EvaluacionCalidad;
}

@Injectable({ providedIn: 'root' })
export class RecojoService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Pide al backend la medicion actual del equipo. La llamada al ESP32 la hace el SERVIDOR: si la
   * hiciera el navegador harian falta cabeceras CORS en el microcontrolador, estar en su misma red,
   * y dejaria de funcionar al servir la web por HTTPS (contenido mixto).
   */
  leerInstrumento() {
    return this.http.get<LecturaEvaluada>(`${API_BASE_URL}/ingesta/lectura`);
  }

  /** Si el servidor tiene configurado el analizador de planta (HUATA_INSTRUMENTO_URL). */
  estadoInstrumento() {
    return this.http.get<{ configurado: boolean }>(`${API_BASE_URL}/ingesta/estado`);
  }

  listar(opts: {
    productorId?: number;
    rutaId?: number;
    acopiadorId?: number;
    estado?: EstadoRecojo;
    desde?: string;
    hasta?: string;
    /** Nombre o código del proveedor, o nombre del acopiador. */
    q?: string;
    fuente?: FuenteRecojo;
    /** ACEPTADA, OBSERVADA, RECHAZADA o PENDIENTE (sin control de calidad). */
    resultado?: string;
    page?: number;
    size?: number;
  }) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.productorId) params['productorId'] = String(opts.productorId);
    if (opts.rutaId) params['rutaId'] = String(opts.rutaId);
    if (opts.acopiadorId) params['acopiadorId'] = String(opts.acopiadorId);
    if (opts.estado) params['estado'] = opts.estado;
    if (opts.desde) params['desde'] = opts.desde;
    if (opts.hasta) params['hasta'] = opts.hasta;
    if (opts.q?.trim()) params['q'] = opts.q.trim();
    if (opts.fuente) params['fuente'] = opts.fuente;
    if (opts.resultado) params['resultado'] = opts.resultado;
    return this.http.get<PageResponse<Recojo>>(`${API_BASE_URL}/recojos`, { params });
  }

  registrar(datos: RecojoRequest) {
    return this.http.post<Recojo>(`${API_BASE_URL}/recojos`, datos);
  }

  cancelar(id: number, motivo: string) {
    return this.http.patch<Recojo>(`${API_BASE_URL}/recojos/${id}/cancelar`, { motivo });
  }

  reporteAcopiadores(desde: string, hasta: string, acopiadorId?: number) {
    const params: Record<string, string> = { desde, hasta };
    if (acopiadorId) params['acopiadorId'] = String(acopiadorId);
    return this.http.get<ReporteAcopiador[]>(`${API_BASE_URL}/recojos/reportes/acopiadores`, { params });
  }
}
