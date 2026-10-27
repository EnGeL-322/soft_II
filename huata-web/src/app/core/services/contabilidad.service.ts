import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

export interface PlanCuenta {
  id: number;
  codigo: string;
  nombre: string;
  tipo: 'ACTIVO' | 'PASIVO' | 'PATRIMONIO' | 'GASTO' | 'INGRESO';
}

export interface AsientoLinea {
  codigoCuenta: string;
  nombreCuenta: string;
  debe: number;
  haber: number;
}

export type OrigenAsiento =
  | 'COMPRA'
  | 'PRODUCCION_CONSUMO'
  | 'PRODUCCION_MANO_OBRA'
  | 'PRODUCCION_TERMINADO'
  | 'VENTA'
  | 'VENTA_COSTO'
  | 'ANULACION_VENTA';

export interface AsientoContable {
  id: number;
  fecha: string;
  glosa: string;
  comentario: string | null;
  origenTipo: OrigenAsiento;
  origenId: number | null;
  lineas: AsientoLinea[];
  totalDebe: number;
  totalHaber: number;
}

export interface MovimientoMayor {
  fecha: string;
  asientoId: number;
  glosa: string;
  debe: number;
  haber: number;
  saldo: number;
}

export interface LibroMayor {
  codigoCuenta: string;
  nombreCuenta: string;
  movimientos: MovimientoMayor[];
  saldoFinal: number;
}

/**
 * Plan de cuentas, libro diario y libro mayor (V21): solo lectura, los asientos se generan
 * automaticamente desde compras, produccion y venta de queso.
 */
@Injectable({ providedIn: 'root' })
export class ContabilidadService {
  constructor(private readonly http: HttpClient) {}

  planCuentas() {
    return this.http.get<PlanCuenta[]>(`${API_BASE_URL}/contabilidad/plan-cuentas`);
  }

  libroDiario(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<AsientoContable[]>(`${API_BASE_URL}/contabilidad/libro-diario`, { params });
  }

  cuentasConMovimiento() {
    return this.http.get<PlanCuenta[]>(`${API_BASE_URL}/contabilidad/libro-mayor/cuentas`);
  }

  libroMayor(cuenta: string, desde?: string, hasta?: string) {
    const params: Record<string, string> = { cuenta };
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<LibroMayor>(`${API_BASE_URL}/contabilidad/libro-mayor`, { params });
  }
}
