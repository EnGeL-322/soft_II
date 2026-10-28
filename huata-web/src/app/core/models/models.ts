// Modelos alineados 1:1 con los DTOs del backend (huata). Mantenerlos sincronizados es el
// contrato entre huata-web y huata; ver PROMPT_ARQUITECTURA_HUATA.md.

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * Estados de la maquina de 7.3 (ver EstadoProductor.java). RETIRADO existe en el backend pero hoy
 * nadie llega a el: la regla 7.2 que lo dispara esta pendiente de confirmacion por la cooperativa.
 * Se declara igual para que la interfaz sepa pintarlo el dia que aparezca en un registro.
 */
/**
 * Una fila de la planilla del ciclo (LiquidacionService.FilaPlanilla). El importe lo calcula el
 * backend con la misma funcion de tarifa que el pago real: la pantalla no lo recalcula.
 */
export interface FilaPlanilla {
  proveedorId: number;
  proveedorCodigo: string;
  proveedorNombre: string;
  zona: string | null;
  excelencia: boolean;
  litrosEntregados: number;
  /** Se muestran aparte y no se pagan (regla 7.1). */
  litrosRechazados: number;
  litrosPagados: number;
  litrosPendientes: number;
  /** Promedio real del ciclo: con calidades mezcladas no existe "una" tarifa. */
  tarifaAplicada: number;
  importePendiente: number;
  liquidado: boolean;
}

export type EstadoProductor =
  | 'PENDIENTE_APROBACION'
  | 'ACTIVO'
  | 'EN_REVISION'
  | 'INACTIVO'
  | 'RETIRADO';
export type EstadoRecojo = 'DRAFT' | 'SAVED_OFFLINE' | 'PENDING_SYNC' | 'SYNCED' | 'CANCELLED';
export type ResultadoCalidad = 'ACEPTADA' | 'OBSERVADA' | 'RECHAZADA';
export type EstadoIncidencia = 'ABIERTA' | 'EN_SEGUIMIENTO' | 'CERRADA';
export type EstadoLiquidacion = 'DRAFT' | 'CALCULATED' | 'REVIEWED' | 'CLOSED';
/** Regla 7.7: los tres primeros restan del pago, la bonificacion suma. */
export type TipoAjuste = 'DESCUENTO_QUESO' | 'PENALIDAD' | 'ADELANTO' | 'BONIFICACION';
export type FuenteRecojo = 'PLANTA' | 'RUTA';
export type AguaAnadida = 'NO_EVIDENCIADA' | 'SOSPECHA' | 'CONFIRMADA';
export type ResultadoLugol = 'NEGATIVA' | 'NO_REALIZADA' | 'POSITIVA';
export type Olor = 'NORMAL' | 'AGRIO' | 'RANCIO';
export type Aspecto = 'NORMAL' | 'GRUMOS' | 'SEDIMENTO';

export interface Usuario {
  id: number;
  nombreCompleto: string;
  username: string;
  email?: string;
  telefono?: string;
  roles: string[];
  activo: boolean;
  /** Cuenta de acopiador o proveedor: se edita en su propio módulo. */
  gestionadoEnSuModulo?: boolean;
  /** Permisos efectivos: roles + concedidos − quitados. */
  permisos?: string[];
  permisosConcedidos?: string[];
  permisosRevocados?: string[];
  ultimoAcceso?: string | null;
  creadoEn?: string | null;
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
  estado: EstadoProductor;
  /** Marca de Excelencia (7.4): cobra tarifa preferencial mientras el ciclo salga sin incidencias. */
  excelencia: boolean;
  notas?: string;
  activo: boolean;
  acopiadorResponsableId?: number;
  acopiadorResponsableNombre?: string;
  zonaPrincipal?: string;
  ordenEnRuta?: number;
  usuarioId?: number;
  username?: string;
}

export interface Acopiador {
  id: number;
  nombres: string;
  apellidos: string;
  documentoIdentidad?: string;
  telefono?: string;
  rutaId?: number;
  rutaNombre?: string;
  zona?: string;
  usuarioId?: number;
  username?: string;
  activo: boolean;
}

export interface Ruta {
  id: number;
  nombre: string;
  zona?: string;
  descripcion?: string;
  activo: boolean;
}

export interface ZonaAsignacion {
  zona: string;
  rutaId: number;
  rutaNombre: string;
  rutaDescripcion?: string;
  proveedores: Array<{
    id: number;
    codigo: string;
    nombres: string;
    apellidos: string;
    telefono?: string;
    ordenEnRuta?: number;
    acopiadorResponsableNombre?: string;
  }>;
  acopiadores: Array<{ id: number; nombres: string; apellidos: string }>;
}

export interface AsignacionRuta {
  id: number;
  rutaId: number;
  rutaNombre: string;
  acopiadorId: number;
  acopiadorNombre: string;
  fecha: string;
  movilidad?: string;
  observacion?: string;
}

export type EstadoPlanRuta = 'SIN_ASIGNAR' | 'ASIGNADA' | 'CON_REGISTROS';

export interface PlanRutaDia {
  rutaId: number;
  rutaNombre: string;
  zona?: string;
  asignacionId?: number;
  acopiadorId?: number;
  acopiadorNombre?: string;
  movilidad?: string;
  observacion?: string;
  estado: EstadoPlanRuta;
  litros: number;
  recepciones: number;
  ultimoRegistro?: string;
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
  fuente: FuenteRecojo;
  numeroEnvases?: number;
  capacidadReferenciaL?: number;
  responsableRecepcion?: string;
  litrosAppMovil?: number;
  /** Resultado de la visita (V14). Nulo en recojos anteriores a V14: no se infiere. */
  estadoVisita?: 'ACOPIADO' | 'NO_ENTREGADO' | null;
  motivoNoEntrega?: string | null;
  // Distancia (m) entre el GPS del acopiador y la última ubicación conocida del proveedor al
  // registrar (pedido del cliente, 2026-09-24). undefined si no se pudo comparar; gpsFueraDeRango
  // solo es true si se superó RECOJO_DISTANCIA_MAXIMA_METROS. No bloquea el recojo, solo lo marca
  // para revisión de calidad/administración.
  distanciaProveedorM?: number;
  // Lo mismo, pero contra el "punto establecido" del proveedor si tiene uno asignado (opción B1,
  // 2026-09-30). Complementa distanciaProveedorM: gpsFueraDeRango es true si cualquiera de las dos
  // distancias superó el límite.
  distanciaPuntoEstablecidoM?: number;
  gpsFueraDeRango?: boolean;
  // Corrección con justificación (pedido del cliente, 2026-09-24): un proveedor entrega una sola vez
  // al día, así que corregir un litros mal tecleado usa este mismo recojo en vez de crear otro.
  // observado queda en true apenas se corrige una vez; motivoObservacion trae la justificación.
  observado?: boolean;
  motivoObservacion?: string;
  resultadoCalidad?: ResultadoCalidad;
}

export interface ReporteAcopiadorDia {
  fecha: string;
  litros: number;
  recepciones: number;
  proveedoresVisitados: number;
  noEntregados: number;
}

export interface ReporteAcopiador {
  acopiadorId: number;
  acopiadorNombre: string;
  rutaId?: number;
  rutaNombre?: string;
  zona?: string;
  desde: string;
  hasta: string;
  litros: number;
  recepciones: number;
  proveedoresVisitados: number;
  noEntregados: number;
  dias: ReporteAcopiadorDia[];
}

export interface ParametroCalidad {
  id: number;
  nombre: string;
  valorMin?: number;
  valorMax?: number;
  unidad?: string;
  descripcion?: string;
}

/** Dónde se analizó la leche (V47). null en controles antiguos de ruta: no consta. */
export type LugarAnalisis = 'CAMPO' | 'PLANTA';

export interface ControlCalidad {
  id: number;
  /** null mientras un análisis de campo espera el recojo del acopiador. */
  recojoId: number | null;
  productorId: number;
  productorNombre: string;
  productorCodigo?: string;
  lugarAnalisis?: LugarAnalisis | null;
  fechaHora?: string;
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
  aguaAnadida?: AguaAnadida;
  lugol?: ResultadoLugol;
  olor?: Olor;
  aspecto?: Aspecto;
  motivoRechazo?: string;
  fecha: string;
  /** V48: quién aprobó el análisis en la web; null = pendiente de aprobación. */
  aprobadoPor?: string | null;
  aprobadoEn?: string | null;
  /** V50: por qué no cumplió el estándar de calidad; null si lo cumple (se aprueba solo). */
  advertencia?: string | null;
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
  /** Tarifa preferencial (7.4). Null en tarifas anteriores a la regla. */
  precioExcelencia?: number;
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

export interface AjusteLiquidacion {
  id: number;
  tipo: TipoAjuste;
  /** Siempre positivo: el signo lo aporta el tipo. */
  monto: number;
  /** El mismo monto ya con su signo, tal como entra en el total. */
  montoConSigno: number;
  motivo?: string;
}

export interface Liquidacion {
  id: number;
  productorId: number;
  productorNombre: string;
  periodoInicio: string;
  periodoFin: string;
  litrosTotales: number;
  /** Bruto, antes de ajustes. */
  montoTotal: number;
  /** Suma de los ajustes con signo. Negativa si predominan descuentos. */
  ajustesTotal: number;
  /** Lo que se paga: montoTotal + ajustesTotal. Es el importe de la boleta. */
  montoNeto: number;
  estado: EstadoLiquidacion;
  detalle: LiquidacionDetalle[];
  ajustes: AjusteLiquidacion[];
}

/** Resultado de "Pagar a todos" (cierre masivo del ciclo). */
export interface CierreCiclo {
  periodoInicio: string;
  periodoFin: string;
  proveedoresLiquidados: number;
  proveedoresYaCerrados: number;
  litrosTotales: number;
  montoNetoTotal: number;
  liquidaciones: Liquidacion[];
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
  comprador?: string;
  numeroBoleta?: string;
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
  /** Código inmutable (VENTAS). */
  nombre: string;
  /** Nombre visible y editable (Ventas). */
  etiqueta: string;
  descripcion?: string | null;
  accesos?: string | null;
  /** Predefinido: no se elimina y se puede restaurar a su estándar. */
  sistema: boolean;
  gestionadoEnSuModulo: boolean;
  permisosBloqueados: boolean;
  usuarios: number;
  permisos: string[];
}

export interface AccionAcceso {
  permiso: string;
  etiqueta: string;
  descripcion?: string | null;
  /** Otras vistas donde aparece este mismo permiso. */
  compartidaCon: string[];
}

export interface VistaAcceso {
  codigo: string;
  nombre: string;
  descripcion?: string | null;
  ruta?: string | null;
  icono?: string | null;
  permisoAcceso: string;
  acciones: AccionAcceso[];
}

export interface ModuloAcceso {
  codigo: string;
  nombre: string;
  vistas: VistaAcceso[];
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

export interface RutaDia {
  nombre: string;
  litros: number;
  recepciones: number;
}

export interface CierreAcopio {
  id: number;
  fecha: string;
  litrosSistema: number;
  litrosPlanta: number;
  observacion?: string;
  cerrado: boolean;
}

export interface ResumenDia {
  fecha: string;
  aceptados: number;
  observados: number;
  rechazados: number;
  recepcionesAceptadas: number;
  controlesObservados: number;
  entregasRechazadas: number;
  proveedores: number;
  pendientesCalidad: number;
  liquidacionesPendientes: number;
  compraEstimada: number | null;
  rutas: RutaDia[];
  cierre: CierreAcopio | null;
}

export interface UbicacionGps {
  acopiadorId: number;
  acopiadorNombre: string;
  latitud: number;
  longitud: number;
  precisionMetros?: number;
  velocidadMs?: number;
  fecha: string;
}

export interface ConsultaPublica {
  codigo: string;
  zona?: string;
  estado: string;
  historial: { fecha: string; resultado: string; litros: number }[];
}

/** RF-48: calidad de una zona en el día. `estado` es APTA | OBSERVADA | ADULTERACION. */
export interface ZonaCalidadPublica {
  zona: string;
  controles: number;
  aceptadas: number;
  observadas: number;
  rechazadas: number;
  estado: 'APTA' | 'OBSERVADA' | 'ADULTERACION';
}

/** RF-49: proveedor con una incidencia de calidad confirmada en el día. Solo código, nunca nombre completo. */
export interface IncidenciaPublica {
  productorCodigo: string;
  zona?: string;
  tipo: string;
  fecha: string;
}

export interface ResumenPublicoHoy {
  fecha: string;
  zonas: ZonaCalidadPublica[];
  incidencias: IncidenciaPublica[];
}

export interface ProduccionPorClave {
  clave: string;
  litros: number;
  recojos: number;
}

export interface PuntoSerieDiaria {
  fecha: string;
  litros: number;
  recepciones: number;
  litrosObservados: number;
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

// ---- Quesería: lote con stock y venta (Registrar producción / Registrar venta) ----

/**
 * Producto terminado (queso, yogur...). Reemplaza al tipo fijo de queso: el catálogo se administra
 * en "Recetas y productos" y cada producto tiene su receta, sus lotes y su lista de precios.
 */
export interface Producto {
  id: number;
  nombre: string;
  /** Cómo se cuenta lo que sale del lote ("unidad", "botella 1 L"). */
  unidadVenta: string;
  descripcion?: string;
  activo: boolean;
  /** Cuántos días dura desde que se produce; sin dato = no se calcula vencimiento. */
  vidaUtilDias?: number;
  /** Producciones registradas (anuladas incluidas). Solo en el listado; con alguna no se puede eliminar. */
  lotesRegistrados?: number;
}

export type OrigenLeche = 'ACOPIO_DEL_DIA' | 'STOCK_CAMARA';
export type TipoComprador = 'PUBLICO_GENERAL' | 'MAYORISTA' | 'PROVEEDOR';
export type CondicionVenta = 'CONTADO' | 'CREDITO_7D';
export type MedioCobro = 'EFECTIVO' | 'TRANSFERENCIA' | 'YAPE_PLIN';
export type EstadoCobro = 'POR_COBRAR' | 'COBRADO';

export interface LoteQueso {
  id: number;
  codigo: string;
  fecha: string;
  productoId: number;
  productoNombre: string;
  unidadVenta: string;
  origenLeche: OrigenLeche;
  litrosDestinados: number;
  unidadesProducidas: number;
  /** Calculado en el backend sobre el kardex de inventario: aquí no se recalcula. */
  unidadesVendidas: number;
  stock: number;
  rendimientoLitrosPorUnidad: number;
  responsable: string;
  observaciones?: string;
  recetaId?: number;
  cantidadObjetivo?: number;
  merma?: number;
  costoMateriaPrima?: number;
  costoManoObra?: number;
  costoTotal?: number;
  costoUnitario?: number;
  quesosReparados?: number;
  quesosRecorte?: number;
  recorteKg?: number;
  /** Hasta cuándo se puede vender. Sin dato en lotes antiguos o productos sin días de duración. */
  fechaVencimiento?: string;
}

/** Un insumo de la receta frente a su stock: para cuántas unidades completas alcanza. */
export interface CapacidadItem {
  materiaPrimaId: number;
  materiaPrimaNombre: string;
  unidadMedida: string;
  cantidadPorUnidad: number;
  stockActual: number;
  alcanzaPara: number;
  /** El insumo que decide el máximo. */
  limitante: boolean;
  /** Es la leche que llega del acopio. */
  esLeche: boolean;
}

/**
 * Cuánto se puede producir de un producto con lo que hay en el almacén (receta vigente vs stock).
 * Sin producto elegido solo trae la leche disponible; sin receta no hay máximo.
 */
export interface CapacidadProduccion {
  productoId?: number;
  productoNombre?: string;
  unidadVenta?: string;
  recetaId?: number;
  tieneReceta: boolean;
  recetaLlevaLeche: boolean;
  maximoProducible?: number;
  limitanteId?: number;
  limitanteNombre?: string;
  lecheDisponible?: number;
  items: CapacidadItem[];
}

/** Un cliente y lo que compró (historial de ventas). */
export interface ClienteVentas {
  cliente: string;
  compras: number;
  unidades: number;
  total: number;
  porCobrar: number;
  ultimaCompra: string;
  productos: string[];
  ultimoTipoComprador: TipoComprador;
}

export interface VentaQueso {
  id: number;
  loteId: number;
  loteCodigo: string;
  productoNombre: string;
  fecha: string;
  cliente: string;
  unidades: number;
  tipoComprador: TipoComprador;
  /** El que se cobró: lo puso la tarifa vigente ese día, no el formulario. */
  precioUnitario: number;
  total: number;
  /** Congelado con la tasa vigente al vender; nulo en ventas anteriores al modulo contable. */
  montoIgv?: number;
  condicion: CondicionVenta;
  medioCobro: MedioCobro;
  estado: EstadoCobro;
  anulada: boolean;
  motivoAnulacion?: string;
}

/**
 * Un periodo de precios de UN producto. Con fecha de fin es una promoción: rige solo entre sus
 * fechas y después vuelve el precio anterior.
 */
export interface TarifaQueso {
  id: number;
  productoId: number;
  productoNombre: string;
  precioPublicoGeneral: number;
  precioMayorista: number;
  precioProveedor: number;
  vigenteDesde: string;
  vigenteHasta?: string;
  descripcion?: string;
  promocion: boolean;
}

export interface ResumenVentasQueso {
  ventaAcumulada: number;
  operaciones: number;
  stockDisponible: number;
  porCobrar: number;
}

// ---- Inventario: materias primas y kardex ----

export type UnidadMedida = 'KG' | 'LITRO' | 'UNIDAD' | 'GRAMO' | 'ML';

export interface MateriaPrima {
  id: number;
  nombre: string;
  unidadMedida: UnidadMedida;
  costoUnitarioReferencial?: number;
  stock: number;
  activo: boolean;
  /** La leche del acopio: entra sola cuando planta la recibe; no se compra ni se da de baja. */
  lecheAcopio?: boolean;
}

export type TipoMovimientoInventario =
  | 'ENTRADA_COMPRA'
  | 'SALIDA_PRODUCCION_CONSUMO'
  | 'ENTRADA_PRODUCCION_TERMINADO'
  | 'SALIDA_VENTA'
  | 'ENTRADA_ANULACION_VENTA'
  | 'AJUSTE_ENTRADA'
  | 'AJUSTE_SALIDA'
  | 'ENTRADA_ACOPIO';

export interface MovimientoInventario {
  id: number;
  tipo: TipoMovimientoInventario;
  materiaPrimaId?: number;
  materiaPrimaNombre?: string;
  loteQuesoId?: number;
  loteQuesoCodigo?: string;
  cantidad: number;
  fecha: string;
  documentoOrigenTipo: 'COMPRA' | 'PRODUCCION' | 'VENTA' | 'AJUSTE' | 'CONCILIACION' | 'RECOJO';
  documentoOrigenId: number;
  usuario: string;
  observaciones?: string;
}

// ---- Compras ----

export interface ProveedorCompra {
  id: number;
  codigo: string;
  nombre: string;
  contacto?: string;
  documento?: string;
  observaciones?: string;
}

export interface CompraItem {
  id: number;
  materiaPrimaId: number;
  materiaPrimaNombre: string;
  cantidad: number;
  costoUnitario: number;
  total: number;
}

export interface Compra {
  id: number;
  proveedorCompraId: number;
  proveedorCompraNombre: string;
  fecha: string;
  documento?: string;
  observaciones?: string;
  items: CompraItem[];
  total: number;
  /** false si se anuló (V59): sigue listada con su motivo. */
  activo: boolean;
  motivoAnulacion?: string | null;
}

/** Ajuste manual de stock de una materia prima (V59): merma, conteo físico, leche descartada. */
export interface AjusteInventarioRequest {
  materiaPrimaId: number;
  tipo: 'ENTRADA' | 'SALIDA';
  cantidad: number;
  motivo: string;
  fecha?: string;
}

// ---- Producción: receta/BOM y simulación ----

export interface RecetaItem {
  materiaPrimaId: number;
  materiaPrimaNombre: string;
  unidadMedida: string;
  cantidadPorUnidad: number;
}

export interface Receta {
  id: number;
  productoId: number;
  productoNombre: string;
  unidadVenta: string;
  vigenteDesde: string;
  vigenteHasta?: string;
  rendimientoLitrosPorUnidad?: number;
  observaciones?: string;
  items: RecetaItem[];
}

export interface TrabajadorProduccion {
  id: number;
  nombre: string;
}

// ---- Rutas: geometría GPS y proveedores de la ruta (2.1.3-2.1.6) ----

export interface RutaPunto {
  id?: number;
  secuencia: number;
  latitud: number;
  longitud: number;
}

export interface ProveedorDeRuta {
  /** ACTIVO, PENDIENTE_APROBACION, RETIRADO...: solo ACTIVO puede entregar leche. */
  estado?: string;
  id: number;
  codigo: string;
  nombres: string;
  apellidos: string;
  ordenEnRuta?: number;
  /** Última ubicación conocida; ausente mientras nadie la haya marcado (RF-59). */
  latitud?: number;
  longitud?: number;
  tipoUbicacion?: TipoUbicacion;
  fechaUbicacion?: string;
}

/** Resultado de agregar un punto de acopio: trazo actualizado + proveedores ya reordenados por cercanía. */
export interface PuntoAcopioAgregado {
  puntos: RutaPunto[];
  proveedores: ProveedorDeRuta[];
}

// ---- Puntos establecidos: referencia fija que administración crea a mano en el mapa ----

/**
 * Punto de referencia con nombre propio, por ruta, con acopiador y proveedor asociados opcionales.
 * Cuando tiene proveedor, entra en la misma regla de cercanía (200 m) que ya aplica al registrar su
 * recojo — ver `distanciaPuntoEstablecidoM` en `Recojo`.
 */
export interface PuntoEstablecido {
  id: number;
  nombre: string;
  latitud: number;
  longitud: number;
  rutaId: number;
  rutaNombre: string;
  zona?: string;
  acopiadorId?: number;
  acopiadorNombre?: string;
  proveedorId?: number;
  proveedorCodigo?: string;
  proveedorNombre?: string;
  observacion?: string;
  activo: boolean;
  creadoPor?: string;
  fechaCreacion?: string;
}

// ---- Polígono de zona: dibuja el territorio en el mapa de Rutas y movilidad ----

export interface ZonaPoligonoPunto {
  secuencia: number;
  latitud: number;
  longitud: number;
}

export interface ZonaPoligono {
  id: number;
  zona: string;
  color?: string;
  puntos: ZonaPoligonoPunto[];
}

/** Copia del límite tomada antes de cambiarlo o borrarlo (V56): permite deshacer un accidente. */
export interface ZonaPoligonoVersion {
  id: number;
  zona: string;
  color?: string;
  puntos: ZonaPoligonoPunto[];
  motivo: 'MODIFICADO' | 'ELIMINADO' | 'ZONA_ELIMINADA' | 'RESTAURADO';
  fecha: string;
  usuario?: string;
}

// ---- Conciliación recolectado vs planta, por ruta (2.2.4) ----

/** Una parada ya registrada de la ruta: lo que se acopió en ese proveedor y a qué hora (RF-53). */
export interface ConciliacionVisita {
  recojoId?: number;
  productorId?: number;
  productorCodigo?: string;
  productorNombre?: string;
  fechaHora: string;
  litros: number;
  fuente?: 'RUTA' | 'PLANTA';
  estadoVisita?: 'ACOPIADO' | 'NO_ENTREGADO';
  motivoNoEntrega?: string;
  acopiadorNombre?: string;
  /** Resultado de calidad de la parada; sin dato si todavía no se analizó. */
  calidad?: 'ACEPTADA' | 'OBSERVADA' | 'RECHAZADA';
}

export interface ConciliacionRuta {
  id?: number;
  fecha?: string;
  rutaId: number;
  rutaNombre: string;
  /** Zona (sector) de la ruta: la conciliación también se ve agrupada por zona. */
  zona?: string;
  acopiadorId?: number;
  acopiadorNombre?: string;
  /** litrosEnRuta + litrosEntregadosEnPlanta. */
  totalRecolectado: number;
  /** Lo que viajó en la movilidad: es contra esto que se mide la diferencia. */
  litrosEnRuta: number;
  /** Proveedores que llevaron su leche ellos mismos; no pasó por el camión. */
  litrosEntregadosEnPlanta: number;
  proveedoresAcopiados: number;
  proveedoresNoEntregaron: number;
  ultimoAcopio?: string;
  litrosRecibidosPlanta?: number;
  diferencia?: number;
  /** Leche de la ruta que no entra al almacén (rechazada, u observada si así se configuró). */
  litrosNoAptos?: number;
  /** Lo que entró al inventario: recibido menos no aptos. */
  litrosAlInventario?: number;
  responsable?: string;
  observacion?: string;
  detalle: ConciliacionVisita[];
  /**
   * Si hay algo que recibir: visitas del acopiador o un total anotado. False si solo hubo entregas
   * directas en planta (ya están en planta: no hay llegada que anotar).
   */
  requiereRecepcion?: boolean;
}

/** Mensaje del canal `/api/ws/acopio`: la fila de una ruta, recalculada por el servidor. */
export interface EventoAcopio {
  tipo: 'RECOJO' | 'PLANTA';
  conciliacion: ConciliacionRuta;
}

// ---- Recorrido pintado por el acopiador (V27) ----

export type TipoTrazado = 'DIA' | 'PREDETERMINADO';

export interface TrazadoPunto {
  secuencia: number;
  latitud: number;
  longitud: number;
}

/**
 * Recorrido que el acopiador pintó en la app móvil.
 *
 * `DIA` es el de una fecha concreta; `PREDETERMINADO` es su recorrido habitual, que administración
 * crea copiando uno del día. Es **por acopiador**, no por ruta: dos acopiadores de la misma ruta
 * pueden tener recorridos distintos, y quien no tenga uno propio sigue usando la geometría de la
 * ruta (`RutaPunto`) como referencia.
 */
export interface TrazadoAcopiador {
  id: number;
  acopiadorId: number;
  acopiadorNombre: string;
  rutaId: number;
  rutaNombre: string;
  zona?: string;
  tipo: TipoTrazado;
  fecha?: string;
  sigueRutaDesignada: boolean;
  observacion?: string;
  origenTrazadoId?: number;
  predeterminadoEn?: string;
  predeterminadoPor?: string;
  puntos: TrazadoPunto[];
}

// ---- Ubicación y cambio de zona del proveedor (2.5-2.6) ----

export type TipoUbicacion = 'CASA' | 'ACTUAL';

export interface ProductorUbicacion {
  id: number;
  productorId: number;
  productorNombre: string;
  latitud: number;
  longitud: number;
  precisionMetros?: number;
  tipo: TipoUbicacion;
  fecha: string;
}

export type EstadoSolicitudZona = 'PENDIENTE' | 'APROBADA' | 'RECHAZADA';

export interface SolicitudCambioZona {
  id: number;
  productorId: number;
  productorNombre: string;
  zonaActual?: string;
  zonaSolicitada: string;
  motivo?: string;
  estado: EstadoSolicitudZona;
  fechaSolicitud: string;
  fechaDecision?: string;
  aprobador?: string;
  observacionDecision?: string;
}
