import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { TrazadoAcopiador } from '../models/models';

/**
 * Recorridos que los acopiadores pintan en la app móvil, y el recorrido predeterminado de cada uno
 * (V27).
 *
 * Predeterminar exige `rutas.write`: fija la referencia permanente de una persona y es una decisión
 * de administración. Pintar, en cambio, lo hace el acopiador desde la app con `acopio.write`.
 */
@Injectable({ providedIn: 'root' })
export class TrazadosService {
  constructor(private readonly http: HttpClient) {}

  /**
   * Los recorridos pintados de una fecha. La fecha es obligatoria también en el tipo: el backend la
   * exige para no devolver el historial completo, y dejarla opcional aquí solo movería el 400 a
   * tiempo de ejecución.
   */
  pintados(fecha: string) {
    return this.http.get<TrazadoAcopiador[]>(`${API_BASE_URL}/trazados-acopiador`, { params: { fecha } });
  }

  predeterminados() {
    return this.http.get<TrazadoAcopiador[]>(`${API_BASE_URL}/trazados-acopiador/predeterminados`);
  }

  predeterminar(trazadoId: number) {
    return this.http.post<TrazadoAcopiador>(
      `${API_BASE_URL}/trazados-acopiador/${trazadoId}/predeterminar`,
      {},
    );
  }

  quitarPredeterminado(acopiadorId: number) {
    return this.http.delete<void>(`${API_BASE_URL}/trazados-acopiador/predeterminados/${acopiadorId}`);
  }
}
