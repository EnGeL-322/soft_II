import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';

export interface VentaQuesoDiaRow {
  fecha: string;
  dia: string;
  producidos: number;
  despachados: number;
  vendidos: number;
  diferencia: number;
}

export interface RendimientoDiaRow {
  fecha: string;
  dia: string;
  litrosPorZona: number[];
  totalAcopio: number;
  produccion: number;
  rendimiento: number;
}

export interface RegistroDiaRow {
  fecha: string;
  dia: string;
  ingresos: number;
}

export interface EgresoRow {
  fecha: string;
  documento: string | null;
  descripcion: string;
  monto: number;
}

export interface ReporteQuesoSemanal {
  desde: string;
  hasta: string;
  ventaPorDia: VentaQuesoDiaRow[];
  totalProducidos: number;
  totalDespachados: number;
  totalVendidos: number;
  totalDiferencia: number;
  totalQuesosEnDescuento: number;
  stockAlmacen: number;
  zonas: string[];
  rendimientoPorDia: RendimientoDiaRow[];
  totalAcopioCiclo: number;
  registroPorDia: RegistroDiaRow[];
  totalIngresos: number;
  totalPagoProveedoresLeche: number;
  egresos: EgresoRow[];
  totalEgresos: number;
  totalSaldo: number;
}

/** "Reporte de venta de quesos" + "Reporte de rendimiento" + "Registro de ventas", igual formato que el cuaderno físico de la Unidad de la Cadena Productiva de Lácteos - Huata. */
@Injectable({ providedIn: 'root' })
export class ReporteQuesoService {
  constructor(private readonly http: HttpClient) {}

  semanal(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<ReporteQuesoSemanal>(`${API_BASE_URL}/reportes/quesos/semanal`, { params });
  }

  descargarExcel(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get(`${API_BASE_URL}/reportes/quesos/semanal.xlsx`, { params, responseType: 'blob' });
  }
}
