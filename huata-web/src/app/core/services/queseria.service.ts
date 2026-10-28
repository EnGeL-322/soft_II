import { HttpClient } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { API_BASE_URL } from '../config/api-config';
import {
  CondicionVenta,
  LoteQueso,
  MedioCobro,
  OrigenLeche,
  ResumenVentasQueso,
  TarifaQueso,
  ClienteVentas,
  TipoComprador,
  VentaQueso,
} from '../models/models';

export interface ManoObraItemRequest {
  trabajadorId: number;
  monto: number;
}

/**
 * No lleva consumos ni litros: el servidor descuenta exactamente receta × `cantidadObjetivo`, y los
 * litros son la leche que pide la receta.
 */
export interface LoteQuesoRequest {
  fecha: string;
  productoId: number;
  origenLeche: OrigenLeche;
  /** Lo que salió y entra a "Guardado para vender" (puede ser menos que lo puesto a producir). */
  unidadesProducidas: number;
  responsable: string;
  observaciones?: string;
  recetaId: number;
  /** Lo que se puso a producir: sobre esto se descuenta la receta. */
  cantidadObjetivo: number;
  manoObra: ManoObraItemRequest[];
  quesosReparados?: number;
  quesosRecorte?: number;
  recorteKg?: number;
  /** Opcional: si falta, el servidor la calcula con los días que dura el producto. */
  fechaVencimiento?: string;
  /** Opcional (V61): la orden de producción que este lote cumple. */
  ordenId?: number;
}

/**
 * La venta no manda precio: lo pone la tarifa vigente según `tipoComprador`. Si la pantalla pudiera
 * enviarlo, la lista de precios de la cooperativa sería una sugerencia.
 */
export interface VentaQuesoRequest {
  loteId: number;
  cliente: string;
  unidades: number;
  tipoComprador: TipoComprador;
  condicion: CondicionVenta;
  medioCobro: MedioCobro;
}

export interface TarifaQuesoRequest {
  productoId: number;
  precioPublicoGeneral: number;
  precioMayorista: number;
  precioProveedor: number;
  vigenteDesde: string;
  /** Con fecha de fin el período es una promoción: al terminar vuelve el precio anterior. */
  vigenteHasta?: string;
  descripcion?: string;
  /** Código de 6 dígitos enviado al correo de quien cambia los precios. */
  codigo: string;
}

/**
 * Producción y venta de queso por lote.
 *
 * <p>Va aparte de `QuesosService` porque son dos modelos distintos: aquel mantiene la tabla plana
 * histórica que siguen leyendo el exportador y la línea de tiempo de reportes; este es el lote con
 * stock de las dos pantallas de Secretaría.
 */
@Injectable({ providedIn: 'root' })
export class QueseriaService {
  constructor(private readonly http: HttpClient) {}

  lotes(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<LoteQueso[]>(`${API_BASE_URL}/quesos/lotes`, { params });
  }

  /** Solo los lotes con unidades en cámara: es lo único que se puede vender. */
  lotesConStock() {
    return this.http.get<LoteQueso[]>(`${API_BASE_URL}/quesos/lotes/con-stock`);
  }

  crearLote(datos: LoteQuesoRequest) {
    return this.http.post<LoteQueso>(`${API_BASE_URL}/quesos/lotes`, datos);
  }

  /** Anula un lote registrado por error: devuelve los insumos. Solo si no se vendió nada de él. */
  anularLote(id: number, motivo: string) {
    return this.http.post<LoteQueso>(`${API_BASE_URL}/quesos/lotes/${id}/anular`, { motivo });
  }

  ventas(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<VentaQueso[]>(`${API_BASE_URL}/quesos/ventas`, { params });
  }

  resumenVentas(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<ResumenVentasQueso>(`${API_BASE_URL}/quesos/ventas/resumen`, { params });
  }

  registrarVenta(datos: VentaQuesoRequest) {
    return this.http.post<VentaQueso>(`${API_BASE_URL}/quesos/ventas`, datos);
  }

  /** Venta por producto: el servidor saca primero lo que vence antes, repartido entre lotes. */
  venderPorProducto(datos: Omit<VentaQuesoRequest, 'loteId'> & { productoId: number }) {
    return this.http.post<VentaQueso[]>(`${API_BASE_URL}/quesos/ventas/por-producto`, datos);
  }

  marcarCobrada(id: number) {
    return this.http.patch<VentaQueso>(`${API_BASE_URL}/quesos/ventas/${id}/cobrada`, {});
  }

  anularVenta(id: number, motivo: string) {
    return this.http.post<VentaQueso>(`${API_BASE_URL}/quesos/ventas/${id}/anular`, { motivo });
  }

  /** A quién se le vende: cada cliente con lo que compró y lo que debe. */
  clientes(desde?: string, hasta?: string) {
    const params: Record<string, string> = {};
    if (desde) params['desde'] = desde;
    if (hasta) params['hasta'] = hasta;
    return this.http.get<ClienteVentas[]>(`${API_BASE_URL}/quesos/ventas/clientes`, { params });
  }

  /** Todas las compras de un cliente (las anuladas vienen marcadas). */
  historialCliente(cliente: string, productoId?: number) {
    const params: Record<string, string> = { cliente };
    if (productoId != null) params['productoId'] = String(productoId);
    return this.http.get<VentaQueso[]>(`${API_BASE_URL}/quesos/ventas/historial`, { params });
  }

  /** Precio que rige hoy (o en la fecha dada) para un producto, incluida una promoción en curso. */
  tarifaVigente(productoId: number, fecha?: string) {
    const params: Record<string, string> = { productoId: String(productoId) };
    if (fecha) params['fecha'] = fecha;
    return this.http.get<TarifaQueso>(`${API_BASE_URL}/quesos/tarifas/vigente`, { params });
  }

  tarifas() {
    return this.http.get<TarifaQueso[]>(`${API_BASE_URL}/quesos/tarifas`);
  }

  guardarTarifa(datos: TarifaQuesoRequest) {
    return this.http.post<TarifaQueso>(`${API_BASE_URL}/quesos/tarifas`, datos);
  }
}
