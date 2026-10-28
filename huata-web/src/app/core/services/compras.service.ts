import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { Compra, PageResponse, ProveedorCompra, Recepcion } from '../models/models';

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
  /** false: queda pendiente de recepción (V61). true: llegó con la compra y entra ya al almacén. */
  recibirAhora?: boolean;
}

export interface RecepcionItemRequest {
  compraItemId: number;
  cantidad: number;
  lote?: string;
  fechaVencimiento?: string;
}

export interface RecepcionRequest {
  fecha?: string;
  documento?: string;
  items: RecepcionItemRequest[];
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

  /** Compras que todavía no llegan del todo: la bandeja de Recepciones. */
  pendientes() {
    return this.http.get<Compra[]>(`${API_BASE_URL}/compras/pendientes`);
  }

  recepciones() {
    return this.http.get<Recepcion[]>(`${API_BASE_URL}/compras/recepciones`);
  }

  /** Lo que llegó de una compra: es lo que mete el insumo al almacén. */
  recibir(compraId: number, datos: RecepcionRequest) {
    return this.http.post<Compra>(`${API_BASE_URL}/compras/${compraId}/recepciones`, datos);
  }

  /** Anula una compra registrada por error: saca del almacén lo que entró por ella. */
  anular(id: number, motivo: string) {
    return this.http.post<Compra>(`${API_BASE_URL}/compras/${id}/anular`, { motivo });
  }
}
