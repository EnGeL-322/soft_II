import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import {
  CierreCiclo,
  EstadoLiquidacion,
  Liquidacion,
  PageResponse,
  TarifaLeche,
  TipoAjuste,
} from '../models/models';

/** Una fila de la planilla del ciclo (RF-25 a RF-31): lo que le queda por cobrar a cada proveedor. */
export interface FilaPlanilla {
  proveedorId: number;
  proveedorCodigo: string;
  proveedorNombre: string;
  /** Solo apellidos: proveedorNombre va "Nombres Apellidos" y no sirve para ordenar alfabéticamente
   *  por apellido (nombres compuestos de largo variable). Se usa en "Imprimir todo". */
  proveedorApellidos: string;
  zona: string | null;
  excelencia: boolean;
  litrosEntregados: number;
  litrosRechazados: number;
  litrosPagados: number;
  litrosPendientes: number;
  tarifaAplicada: number;
  importePendiente: number;
  /** Sin pendiente por cobrar. Puede ser false aunque tieneLiquidacionCerrada sea true: hay una
   *  liquidación cerrada, pero quedan acopios sin cubrir (por ejemplo, uno registrado después del
   *  cierre) que solo se pueden cobrar reabriendo esa liquidación. */
  liquidado: boolean;
  /** La liquidación del periodo si ya se calculó o cerró alguna vez; null si nunca se calculó. */
  liquidacionId: number | null;
  /** true = ya está cerrada (con o sin saldo pendiente) y solo se puede tocar reabriéndola. */
  tieneLiquidacionCerrada: boolean;
}

export interface AjusteLiquidacionRequest {
  tipo: TipoAjuste;
  monto: number;
  motivo?: string;
}

/** Lo que la pantalla necesita saber del código enviado, sin que el código salga nunca aquí. */
export interface CodigoEmitido {
  destinatarioEnmascarado: string;
  expiraEn: string;
  minutosVigencia: number;
}

export interface TarifaLecheRequest {
  precioNormal: number;
  precioObservada: number;
  precioExcelencia?: number;
  vigenteDesde: string;
  vigenteHasta?: string;
  /** Código de 6 dígitos enviado al correo de quien cambia los precios. */
  codigo: string;
}

/**
 * Pagos y liquidaciones (RF-25 a RF-31): planilla del ciclo, pago único, pago global ("Pagar a
 * todos"), ajustes y tarifa de leche. El backend genera el asiento contable (libro diario) al cerrar
 * cada liquidación; ver módulo Contabilidad para verlo.
 */
@Injectable({ providedIn: 'root' })
export class LiquidacionesService {
  constructor(private readonly http: HttpClient) {}

  planilla(periodoInicio: string, periodoFin: string) {
    return this.http.get<FilaPlanilla[]>(`${API_BASE_URL}/liquidaciones/planilla`, {
      params: { periodoInicio, periodoFin },
    });
  }

  listar(productorId?: number, estado?: EstadoLiquidacion, page = 0, size = 20) {
    const params: Record<string, string> = { page: String(page), size: String(size) };
    if (productorId != null) params['productorId'] = String(productorId);
    if (estado) params['estado'] = estado;
    return this.http.get<PageResponse<Liquidacion>>(`${API_BASE_URL}/liquidaciones`, { params });
  }

  obtener(id: number) {
    return this.http.get<Liquidacion>(`${API_BASE_URL}/liquidaciones/${id}`);
  }

  calcular(productorId: number, periodoInicio: string, periodoFin: string) {
    return this.http.post<Liquidacion>(`${API_BASE_URL}/liquidaciones/calcular`, {
      productorId,
      periodoInicio,
      periodoFin,
    });
  }

  cambiarEstado(id: number, estado: EstadoLiquidacion) {
    return this.http.patch<Liquidacion>(`${API_BASE_URL}/liquidaciones/${id}/estado`, { estado });
  }

  /** Pide el código de 6 dígitos para reabrir un pago (llega al correo de quien lo pide). */
  solicitarCodigoReapertura() {
    return this.http.post<CodigoEmitido>(`${API_BASE_URL}/liquidaciones/reabrir/codigo`, {});
  }

  /** Reabre una liquidación CERRADA con el código ya recibido por correo. */
  reabrir(id: number, codigo: string) {
    return this.http.post<Liquidacion>(`${API_BASE_URL}/liquidaciones/${id}/reabrir`, { codigo });
  }

  /** "Pagar a todos" (RF-25): calcula y cierra de una vez a todos los proveedores con entregas en el ciclo. */
  cerrarCiclo(periodoInicio: string, periodoFin: string) {
    return this.http.post<CierreCiclo>(`${API_BASE_URL}/liquidaciones/cerrar-ciclo`, { periodoInicio, periodoFin });
  }

  agregarAjuste(liquidacionId: number, datos: AjusteLiquidacionRequest) {
    return this.http.post<Liquidacion>(`${API_BASE_URL}/liquidaciones/${liquidacionId}/ajustes`, datos);
  }

  eliminarAjuste(liquidacionId: number, ajusteId: number) {
    return this.http.delete<Liquidacion>(`${API_BASE_URL}/liquidaciones/${liquidacionId}/ajustes/${ajusteId}`);
  }

  tarifas() {
    return this.http.get<TarifaLeche[]>(`${API_BASE_URL}/tarifas-leche`);
  }

  guardarTarifa(datos: TarifaLecheRequest) {
    return this.http.post<TarifaLeche>(`${API_BASE_URL}/tarifas-leche`, datos);
  }
}
