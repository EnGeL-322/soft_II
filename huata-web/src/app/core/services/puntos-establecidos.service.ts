import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { PuntoEstablecido } from '../models/models';

export interface PuntoEstablecidoRequest {
  nombre: string;
  latitud: number;
  longitud: number;
  rutaId: number;
  acopiadorId?: number;
  proveedorId?: number;
  observacion?: string;
}

/**
 * "Puntos establecidos" (pedido del cliente): puntos de referencia que administración crea a mano en
 * el mapa, por ruta, con acopiador y proveedor asociados opcionales. Mismos permisos que el resto de
 * "Rutas y movilidad" — crear/editar/desactivar exige `rutas.write`, que solo tiene ADMINISTRADOR.
 */
@Injectable({ providedIn: 'root' })
export class PuntosEstablecidosService {
  constructor(private readonly http: HttpClient) {}

  listar(rutaId?: number, incluirInactivos = false) {
    const params: Record<string, string> = rutaId ? { rutaId: String(rutaId) } : {};
    if (incluirInactivos) params['incluirInactivos'] = 'true';
    return this.http.get<PuntoEstablecido[]>(`${API_BASE_URL}/puntos-establecidos`, { params });
  }

  crear(datos: PuntoEstablecidoRequest) {
    return this.http.post<PuntoEstablecido>(`${API_BASE_URL}/puntos-establecidos`, datos);
  }

  actualizar(id: number, datos: PuntoEstablecidoRequest) {
    return this.http.put<PuntoEstablecido>(`${API_BASE_URL}/puntos-establecidos/${id}`, datos);
  }

  desactivar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/puntos-establecidos/${id}`);
  }

  activar(id: number) {
    return this.http.put<PuntoEstablecido>(`${API_BASE_URL}/puntos-establecidos/${id}/estado`, {
      activo: true,
    });
  }

  eliminar(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/puntos-establecidos/${id}/definitivo`);
  }
}
