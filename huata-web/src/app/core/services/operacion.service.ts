import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
export interface CierreAcopio {
  fecha: string;
  litrosSistema: number;
  litrosPlanta: number;
  observacion: string;
  cerrado: boolean;
}
export interface ResumenOperacion {
  fecha: string;
  aceptados: number;
  observados: number;
  rechazados: number;
  recepcionesAceptadas: number;
  controlesObservados: number;
  entregasRechazadas: number;
  proveedores: number;
  pendientesCalidad: number;
  liquidacionesPendientes: number;
  compraEstimada?: number;
  rutas: { nombre: string; litros: number; recepciones: number }[];
  cierre?: CierreAcopio;
}
export interface TotalesDia {
  recogido: number;
  recibidoPlanta: number;
  rutasSinRecibir: string[];
}
@Injectable({ providedIn: 'root' })
export class OperacionService {
  constructor(private readonly http: HttpClient) {}
  resumen(fecha: string) {
    return this.http.get<ResumenOperacion>(`${API_BASE_URL}/operacion/${fecha}`);
  }
  /** Lo que el cierre compara: se calcula de las rutas, no se escribe a mano. */
  totales(fecha: string) {
    return this.http.get<TotalesDia>(`${API_BASE_URL}/operacion/${fecha}/totales`);
  }

  /** Desde V33 los litros de planta se calculan de las rutas: solo se envía la observación. */
  cerrar(fecha: string, observacion: string) {
    return this.http.put<CierreAcopio>(`${API_BASE_URL}/operacion/${fecha}/cierre`, { observacion });
  }
  reabrir(fecha: string) {
    return this.http.post<CierreAcopio>(`${API_BASE_URL}/operacion/${fecha}/reabrir`, {});
  }
}
