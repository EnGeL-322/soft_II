import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import {
  AjusteInventarioRequest,
  MateriaPrima,
  MovimientoInventario,
  PageResponse,
  TipoMateriaPrima,
  UnidadMedida,
} from '../models/models';

export interface MateriaPrimaRequest {
  nombre: string;
  unidadMedida: UnidadMedida;
  costoUnitarioReferencial?: number;
  /** V61: opcionales; al editar, si no se mandan se conservan. */
  tipo?: TipoMateriaPrima;
  sinonimos?: string;
}

@Injectable({ providedIn: 'root' })
export class InventarioService {
  constructor(private readonly http: HttpClient) {}

  /** Solo las activas, salvo que se pidan también las dadas de baja (el recetario las necesita). */
  materiasPrimas(incluirInactivos = false) {
    const params: Record<string, string> = incluirInactivos ? { incluirInactivos: 'true' } : {};
    return this.http.get<MateriaPrima[]>(`${API_BASE_URL}/inventario/materias-primas`, { params });
  }

  crearMateriaPrima(datos: MateriaPrimaRequest) {
    return this.http.post<MateriaPrima>(`${API_BASE_URL}/inventario/materias-primas`, datos);
  }

  actualizarMateriaPrima(id: number, datos: MateriaPrimaRequest) {
    return this.http.put<MateriaPrima>(`${API_BASE_URL}/inventario/materias-primas/${id}`, datos);
  }

  inactivarMateriaPrima(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/inventario/materias-primas/${id}`);
  }

  /** Ajuste manual de stock (merma, conteo físico): exige motivo y queda en el kardex. */
  ajustar(datos: AjusteInventarioRequest) {
    return this.http.post<unknown>(`${API_BASE_URL}/inventario/ajustes`, datos);
  }

  kardex(opts: { materiaPrimaId?: number; loteQuesoId?: number; page?: number; size?: number }) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.materiaPrimaId) params['materiaPrimaId'] = String(opts.materiaPrimaId);
    if (opts.loteQuesoId) params['loteQuesoId'] = String(opts.loteQuesoId);
    return this.http.get<PageResponse<MovimientoInventario>>(`${API_BASE_URL}/inventario/kardex`, { params });
  }
}
