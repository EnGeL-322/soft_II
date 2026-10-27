import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

/** Reglas de ubicación del acopio (V57): se apagan para una demostración sin perderlas. */
export interface ReglasAcopio {
  /** Solo se empieza el recorrido y se registra leche dentro del límite de la zona. */
  exigirGeocerca: boolean;
  /** La app no deja registrar leche lejos del proveedor (con conexión). */
  exigirDistancia: boolean;
  /** Hasta cuántos metros del proveedor se considera "en su punto". */
  distanciaMaximaMetros: number;
}

@Injectable({ providedIn: 'root' })
export class ReglasAcopioService {
  constructor(private readonly http: HttpClient) {}

  vigentes() {
    return this.http.get<ReglasAcopio>(`${API_BASE_URL}/reglas-acopio`);
  }

  guardar(reglas: ReglasAcopio) {
    return this.http.put<ReglasAcopio>(`${API_BASE_URL}/reglas-acopio`, reglas);
  }
}
