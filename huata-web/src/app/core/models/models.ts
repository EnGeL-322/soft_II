// Modelos alineados 1:1 con los DTOs del backend (huata). Mantenerlos sincronizados es el
// contrato entre huata-web y huata; ver PROMPT_ARQUITECTURA_HUATA.md.

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export type EstadoProductor = 'ACTIVO' | 'EN_REVISION' | 'INACTIVO';
export type EstadoRecojo = 'DRAFT' | 'SAVED_OFFLINE' | 'PENDING_SYNC' | 'SYNCED' | 'CANCELLED';
export type ResultadoCalidad = 'ACEPTADA' | 'OBSERVADA' | 'RECHAZADA';
export type EstadoIncidencia = 'ABIERTA' | 'EN_SEGUIMIENTO' | 'CERRADA';
export type EstadoLiquidacion = 'DRAFT' | 'CALCULATED' | 'REVIEWED' | 'CLOSED';

export interface Usuario {
  id: number;
  nombreCompleto: string;
  username: string;
  email?: string;
  telefono?: string;
  roles: string[];
  activo: boolean;
}

export interface Productor {
  id: number;
  codigo: string;
  nombres: string;
  apellidos: string;
  documentoIdentidad?: string;
  telefono?: string;
  rutaId?: number;
  rutaNombre?: string;
  centroAcopioId?: number;
  centroAcopioNombre?: string;
  estado: EstadoProductor;
  notas?: string;
  activo: boolean;
}

export interface Acopiador {
  id: number;
  nombres: string;
  apellidos: string;
  documentoIdentidad?: string;
  telefono?: string;
  activo: boolean;
}

export interface Ruta {
  id: number;
  nombre: string;
  zona?: string;
  descripcion?: string;
  activo: boolean;
}

export interface CentroAcopio {
  id: number;
  nombre: string;
  ubicacion?: string;
  rutaId?: number;
  rutaNombre?: string;
  activo: boolean;
}

export interface AsignacionRuta {
  id: number;
  rutaId: number;
  rutaNombre: string;
  acopiadorId: number;
  acopiadorNombre: string;
  fecha: string;
}

export interface Recojo {
  id: number;
  clientUuid: string;
  productorId: number;
  productorNombre: string;
  acopiadorId?: number;
  acopiadorNombre?: string;
  rutaId?: number;
  rutaNombre?: string;
  fechaHora: string;
  litros: number;
  temperatura?: number;
  estado: EstadoRecojo;
  motivoCancelacion?: string;
}

export interface ParametroCalidad {
  id: number;
  nombre: string;
  valorMin?: number;
  valorMax?: number;
  unidad?: string;
  descripcion?: string;
}

export interface ControlCalidad {
  id: number;
  recojoId: number;
  productorId: number;
  productorNombre: string;
  densidad?: number;
  puntoCongelacion?: number;
  acidez?: number;
  temperatura?: number;
  grasa?: number;
  solidosNoGrasos?: number;
  solidosTotales?: number;
  revisionVisual?: string;
  resultado: ResultadoCalidad;
  equipoUtilizado?: string;
  responsable?: string;
  fecha: string;
}

export interface IncidenciaCalidad {
  id: number;
  productorId: number;
  productorNombre: string;
  recojoId?: number;
  tipo: string;
  descripcion?: string;
  fecha: string;
  estado: EstadoIncidencia;
}

export interface TarifaLeche {
  id: number;
  precioNormal: number;
  precioObservada: number;
  vigenteDesde: string;
  vigenteHasta?: string;
}

export interface LiquidacionDetalle {
  recojoId: number;
  fechaHora: string;
  litros: number;
  precioAplicado: number;
  subtotal: number;
}

export interface Liquidacion {
  id: number;
  productorId: number;
  productorNombre: string;
  periodoInicio: string;
  periodoFin: string;
  litrosTotales: number;
  montoTotal: number;
  estado: EstadoLiquidacion;
  detalle: LiquidacionDetalle[];
}

export interface ParametroNegocio {
  id: number;
  clave: string;
  valor: string;
  descripcion?: string;
}

export interface Notificacion {
  id: number;
  titulo: string;
  mensaje: string;
  entidadTipo?: string;
  entidadId?: number;
  leida: boolean;
  fecha: string;
}

export interface Queso {
  id: number;
  fecha: string;
  fuente: string;
  producidosKg: number;
  vendidosKg: number;
  totalVenta?: number;
  observaciones?: string;
}

export interface RegistroAuditoria {
  id: number;
  fecha: string;
  actor?: string;
  accion: string;
  entidad?: string;
  detalle?: string;
}

export interface RolInfo {
  id: number;
  nombre: string;
  descripcion?: string;
  accesos?: string;
}

export interface ZonaResumen {
  zona: string;
  litrosPeriodoAnterior: number;
  litrosPeriodoActual: number;
  variacionPorciento: number;
  participacionPorciento: number;
}

export interface TrazabilidadItem {
  recojoId: number;
  clientUuid: string;
  productorCodigo: string;
  productorNombre: string;
  zona?: string;
  rutaNombre?: string;
  fechaHora: string;
  litros: number;
  estado: string;
}

export interface ConsultaPublica {
  codigo: string;
  zona?: string;
  estado: string;
  historial: { fecha: string; resultado: string; litros: number }[];
}

export interface ProduccionPorClave {
  clave: string;
  litros: number;
  recojos: number;
}

export interface TendenciaCalidad {
  resultado: ResultadoCalidad;
  cantidad: number;
}

export interface DashboardResponse {
  litrosTotales: number;
  recojosTotales: number;
  produccionPorRuta: ProduccionPorClave[];
  produccionPorProductor: ProduccionPorClave[];
  tendenciaCalidad: TendenciaCalidad[];
  incidenciasAbiertas: number;
  liquidacionesPendientes: number;
}
