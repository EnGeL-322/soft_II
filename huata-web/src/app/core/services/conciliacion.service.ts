import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { ConciliacionRuta } from '../models/models';

export interface ConciliacionRutaRequest {
  fecha: string;
  rutaId: number;
  acopiadorId?: number;
  litrosRecibidosPlanta: number;
  /** Quién recibe no se envía: es la cuenta que registra (lo pone el backend). */
  observacion?: string;
}

/** Comparar recolectado vs recibido en planta, por ruta y fecha (2.2.4). */
@Injectable({ providedIn: 'root' })
export class ConciliacionService {
  constructor(private readonly http: HttpClient) {}

  deFecha(fecha: string) {
    return this.http.get<ConciliacionRuta[]>(`${API_BASE_URL}/conciliaciones-ruta`, { params: { fecha } });
  }

  registrar(datos: ConciliacionRutaRequest) {
    return this.http.post<ConciliacionRuta>(`${API_BASE_URL}/conciliaciones-ruta`, datos);
  }
}
