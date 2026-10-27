import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import { CapacidadProduccion, Producto, Receta, TrabajadorProduccion } from '../models/models';

export interface RecetaItemRequest {
  materiaPrimaId: number;
  cantidadPorUnidad: number;
}

export interface ProductoRequest {
  nombre: string;
  unidadVenta: string;
  descripcion?: string;
  vidaUtilDias?: number;
}

export interface RecetaRequest {
  productoId: number;
  vigenteDesde: string;
  vigenteHasta?: string;
  rendimientoLitrosPorUnidad?: number;
  observaciones?: string;
  items: RecetaItemRequest[];
}

@Injectable({ providedIn: 'root' })
export class ProduccionService {
  constructor(private readonly http: HttpClient) {}

  recetas() {
    return this.http.get<Receta[]>(`${API_BASE_URL}/produccion/recetas`);
  }

  // ---- Catálogo de productos ----

  productos(incluirInactivos = false) {
    return this.http.get<Producto[]>(`${API_BASE_URL}/productos`, {
      params: { incluirInactivos },
    });
  }

  crearProducto(datos: ProductoRequest) {
    return this.http.post<Producto>(`${API_BASE_URL}/productos`, datos);
  }

  actualizarProducto(id: number, datos: ProductoRequest) {
    return this.http.put<Producto>(`${API_BASE_URL}/productos/${id}`, datos);
  }

  cambiarEstadoProducto(id: number, activo: boolean) {
    return this.http.patch<Producto>(
      `${API_BASE_URL}/productos/${id}/${activo ? 'reactivar' : 'baja'}`,
      {},
    );
  }

  /** Días que dura el producto desde que se produce (se configura en Configuración). */
  cambiarVencimiento(id: number, vidaUtilDias: number) {
    return this.http.patch<Producto>(`${API_BASE_URL}/productos/${id}/vencimiento`, { vidaUtilDias });
  }

  eliminarProducto(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/productos/${id}`);
  }

  crearReceta(datos: RecetaRequest) {
    return this.http.post<Receta>(`${API_BASE_URL}/produccion/recetas`, datos);
  }

  /**
   * Cuánto se puede producir con lo que hay en el almacén: por insumo, para cuántas unidades alcanza,
   * el máximo y el insumo que limita. Sin producto solo trae la leche disponible.
   */
  capacidad(productoId?: number | null, fecha?: string) {
    const params: Record<string, string> = {};
    if (productoId != null) params['productoId'] = String(productoId);
    if (fecha) params['fecha'] = fecha;
    return this.http.get<CapacidadProduccion>(`${API_BASE_URL}/produccion/capacidad`, { params });
  }

  trabajadores() {
    return this.http.get<TrabajadorProduccion[]>(`${API_BASE_URL}/produccion/trabajadores`);
  }

  crearTrabajador(nombre: string) {
    return this.http.post<TrabajadorProduccion>(`${API_BASE_URL}/produccion/trabajadores`, { nombre });
  }

  inactivarTrabajador(id: number) {
    return this.http.delete<void>(`${API_BASE_URL}/produccion/trabajadores/${id}`);
  }
}
