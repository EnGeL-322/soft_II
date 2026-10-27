import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { Compra, PageResponse, ProveedorCompra } from '../models/models';

export interface ProveedorCompraRequest {
  nombre: string;
  contacto?: string;
  documento?: string;
  observaciones?: string;
}

export interface CompraItemRequest {
  materiaPrimaId: number;
  cantidad: number;
  costoUnitario: number;
}

export interface CompraRequest {
  proveedorCompraId: number;
  fecha: string;
  documento?: string;
  observaciones?: string;
  items: CompraItemRequest[];
}

@Injectable({ providedIn: 'root' })
export class ComprasService {
  constructor(private readonly http: HttpClient) {}

  proveedores() {
    return this.http.get<ProveedorCompra[]>(`${API_BASE_URL}/compras/proveedores`);
  }

  crearProveedor(datos: ProveedorCompraRequest) {
    return this.http.post<ProveedorCompra>(`${API_BASE_URL}/compras/proveedores`, datos);
  }

  actualizarProveedor(id: number, datos: ProveedorCompraRequest) {
    return this.http.put<ProveedorCompra>(`${API_BASE_URL}/compras/proveedores/${id}`, datos);
  }

  inactivarProveedor(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/compras/proveedores/${id}`);
  }

  listar(opts: { desde?: string; hasta?: string; page?: number; size?: number }) {
    const params: Record<string, string> = { page: String(opts.page ?? 0), size: String(opts.size ?? 20) };
    if (opts.desde) params['desde'] = opts.desde;
    if (opts.hasta) params['hasta'] = opts.hasta;
    return this.http.get<PageResponse<Compra>>(`${API_BASE_URL}/compras`, { params });
  }

  registrar(datos: CompraRequest) {
    return this.http.post<Compra>(`${API_BASE_URL}/compras`, datos);
  }

  /** Anula una compra registrada por error: saca del almacén lo que entró por ella. */
  anular(id: number, motivo: string) {
    return this.http.post<Compra>(`${API_BASE_URL}/compras/${id}/anular`, { motivo });
  }
}
