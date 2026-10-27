import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

/** Reglas configurables de la leche (V33). */
export interface ReglasLeche {
  /** Con cuántas detecciones de mala calidad el proveedor queda retirado. 0 = nunca. */
  deteccionesParaRetiro: number;
  /** Si la leche OBSERVADA cuenta como mala calidad (la RECHAZADA siempre cuenta). */
  observadaEsMalaCalidad: boolean;
  /** Si la leche OBSERVADA entra al almacén (la RECHAZADA nunca entra). */
  observadaEntraAInventario: boolean;
  /** Costo por litro para producir; null = tarifa normal del día. */
  costoLecheLitro: number | null;
}

@Injectable({ providedIn: 'root' })
export class ReglasLecheService {
  constructor(private readonly http: HttpClient) {}

  vigentes() {
    return this.http.get<ReglasLeche>(`${API_BASE_URL}/reglas-leche`);
  }

  guardar(reglas: ReglasLeche) {
    return this.http.put<ReglasLeche>(`${API_BASE_URL}/reglas-leche`, reglas);
  }
}
