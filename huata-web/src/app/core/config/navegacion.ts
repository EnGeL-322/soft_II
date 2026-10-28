import type { NombreIcono } from '../../shared/ui/icono.component';

/**
 * Codigos de permiso del backend (ver Permiso.java). Se escriben aqui una sola vez para no repartir
 * cadenas sueltas por los componentes: un permiso mal escrito no falla, simplemente oculta algo para
 * siempre, y eso es dificil de notar.
 *
 * Ojo con `acopioCerrarJornada` y `pagosCerrarCiclo`: son permisos de ACCION, no de modulo.
 */
export const PERMISO = {
  panelRead: 'panel.read',
  acopioRead: 'acopio.read',
  acopioWrite: 'acopio.write',
  /** Registrar lo recibido en planta (Calidad y Administrador). No lo tiene el acopiador. */
  plantaRecepcion: 'planta.recepcion',
  calidadRead: 'calidad.read',
  /** Aprobar análisis con advertencia (web y app). */
  calidadAprobar: 'calidad.aprobar',
  rutasRead: 'rutas.read',
  rutasWrite: 'rutas.write',
  zonasRead: 'zonas.read',
  proveedoresRead: 'proveedores.read',
  proveedoresWrite: 'proveedores.write',
  acopiadoresRead: 'acopiadores.read',
  acopiadoresWrite: 'acopiadores.write',
  quesosRead: 'quesos.read',
  /** Desde V54 solo lotes; vender, cobrar y anular tienen su propio permiso. */
  quesosWrite: 'quesos.write',
  ventasRegistrar: 'ventas.registrar',
  ventasCobrar: 'ventas.cobrar',
  ventasAnular: 'ventas.anular',
  usuariosRead: 'usuarios.read',
  usuariosWrite: 'usuarios.write',
  produccionRead: 'produccion.read',
  produccionWrite: 'produccion.write',
  recetasRead: 'recetas.read',
  recetasWrite: 'recetas.write',
  comprasRead: 'compras.read',
  comprasWrite: 'compras.write',
  inventarioRead: 'inventario.read',
  inventarioWrite: 'inventario.write',
  contabilidadRead: 'contabilidad.read',
  pagosRead: 'pagos.read',
  pagosWrite: 'pagos.write',
  pagosCerrarCiclo: 'pagos.cerrar-ciclo',
  pagosReabrir: 'pagos.reabrir',
  parametrosRead: 'parametros.read',
  parametrosWrite: 'parametros.write',
  /** Importar el padrón desde Excel (con proveedores.write). */
  respaldoWrite: 'respaldo.write',
  /** Cerrar el día de acopio (acción, no módulo). */
  acopioCerrarJornada: 'acopio.cerrar-jornada',
} as const;

/** Módulos organizados según el ciclo del negocio lácteo. */
/**
 * Acopio = lo que pasa en ruta (acopiadores con la app, testeador) y su calidad.
 * Planta = lo que llega a la planta: las rutas al volver y las entregas directas de proveedores.
 */
export const GRUPOS_NAV = [
  'Acopio',
  'Planta',
  'Personas y rutas',
  // Producción → Abastecimiento → Inventario: el flujo del prototipo aprobado (2026-10-28).
  'Producción',
  'Abastecimiento',
  'Inventario',
  'Ventas y pagos',
  'Configuración',
] as const;

export type GrupoNav = (typeof GRUPOS_NAV)[number];

export interface ItemNav {
  ruta: string;
  icono: NombreIcono;
  etiqueta: string;
  etiquetaEscritura?: string;
  /** Permiso que decide si el item se ve. Lo usan el sidebar Y el guard de la ruta: una sola lista. */
  permiso: string;
  /** Otros permisos que también abren la pantalla (Usuarios: cada pestaña tiene el suyo). */
  tambienCon?: readonly string[];
  permisoEscritura?: string;
  grupo: GrupoNav;
  /**
   * Vistas de la pantalla que se abren desde el menú lateral (`?vista=`). La primera que el usuario
   * puede ver es la que abre la pantalla sin `vista`: la pantalla debe usar el mismo orden.
   */
  subitems?: readonly SubitemNav[];
}

export interface SubitemNav {
  vista: string;
  icono: NombreIcono;
  etiqueta: string;
  /** Sin permiso, basta con ver la pantalla. Con varios, basta con uno. */
  permisos?: readonly string[];
}

/** Navegación del negocio. Los permisos del backend determinan las vistas de cada sesión. */
export const NAVEGACION: readonly ItemNav[] = [
  {
    ruta: 'dashboard',
    icono: 'inicio',
    etiqueta: 'Inicio',
    permiso: PERMISO.panelRead,
    grupo: 'Acopio',
  },
  {
    ruta: 'calidad',
    icono: 'calidad',
    etiqueta: 'Calidad',
    permiso: PERMISO.calidadRead,
    grupo: 'Acopio',
  },
  {
    // Solo consulta: los recojos en ruta los registra el acopiador desde la app móvil.
    ruta: 'recojo',
    icono: 'acopio',
    etiqueta: 'Recojos en ruta',
    permiso: PERMISO.acopioRead,
    grupo: 'Acopio',
  },
  {
    ruta: 'conciliacion',
    icono: 'planta',
    etiqueta: 'Recepción de rutas',
    permiso: PERMISO.acopioRead,
    grupo: 'Planta',
  },
  {
    // La leche que el proveedor trae él mismo a planta: el único recojo que nace en la web.
    ruta: 'entregas-planta',
    icono: 'entrega',
    etiqueta: 'Entregas directas',
    etiquetaEscritura: 'Entregas directas',
    permiso: PERMISO.acopioRead,
    // Las registra planta (Calidad, Administrador), no el acopiador.
    permisoEscritura: PERMISO.plantaRecepcion,
    grupo: 'Planta',
  },
  {
    // Una sola pantalla para las personas: internos, acopiadores y proveedores en pestañas (V55).
    ruta: 'usuarios',
    icono: 'usuarios',
    etiqueta: 'Usuarios',
    permiso: PERMISO.usuariosRead,
    tambienCon: [PERMISO.acopiadoresRead, PERMISO.proveedoresRead],
    grupo: 'Personas y rutas',
  },
  {
    ruta: 'rutas',
    icono: 'rutas',
    etiqueta: 'Rutas y mapa',
    permiso: PERMISO.rutasRead,
    grupo: 'Personas y rutas',
  },
  {
    ruta: 'zonas',
    icono: 'zonas',
    etiqueta: 'Zonas',
    permiso: PERMISO.zonasRead,
    grupo: 'Personas y rutas',
  },
  {
    ruta: 'solicitudes-zona',
    icono: 'cambio',
    etiqueta: 'Cambios de zona',
    permiso: PERMISO.proveedoresRead,
    grupo: 'Personas y rutas',
  },
  {
    ruta: 'recetas',
    icono: 'recetas',
    etiqueta: 'Catálogo y recetas',
    permiso: PERMISO.recetasRead,
    grupo: 'Producción',
    subitems: [
      { vista: 'catalogo', icono: 'produccion', etiqueta: 'Catálogo' },
      { vista: 'recetas', icono: 'recetas', etiqueta: 'Recetas' },
    ],
  },
  {
    ruta: 'produccion-queso',
    icono: 'produccion',
    etiqueta: 'Producción',
    permiso: PERMISO.produccionRead,
    grupo: 'Producción',
    subitems: [
      { vista: 'ordenes', icono: 'lista', etiqueta: 'Órdenes de producción' },
      {
        vista: 'registrar',
        icono: 'agregar',
        etiqueta: 'Registrar producción',
        permisos: [PERMISO.produccionWrite, PERMISO.quesosWrite],
      },
      { vista: 'historial', icono: 'historial', etiqueta: 'Historial de producción' },
      { vista: 'reportes', icono: 'reportes', etiqueta: 'Reportes', permisos: [PERMISO.quesosRead] },
    ],
  },
  {
    ruta: 'compras',
    icono: 'compras',
    etiqueta: 'Compras',
    permiso: PERMISO.comprasRead,
    grupo: 'Abastecimiento',
    subitems: [
      { vista: 'compras', icono: 'compras', etiqueta: 'Compras' },
      { vista: 'recepciones', icono: 'entrega', etiqueta: 'Recepciones' },
    ],
  },
  {
    ruta: 'inventario',
    icono: 'inventario',
    etiqueta: 'Inventario',
    permiso: PERMISO.inventarioRead,
    grupo: 'Inventario',
    subitems: [
      { vista: 'stock', icono: 'inventario', etiqueta: 'Stock' },
      { vista: 'kardex', icono: 'historial', etiqueta: 'Kardex' },
      { vista: 'lotes', icono: 'calendario', etiqueta: 'Lotes y vencimientos' },
    ],
  },
  {
    ruta: 'venta-queso',
    icono: 'ventas',
    etiqueta: 'Ventas',
    permiso: PERMISO.quesosRead,
    grupo: 'Ventas y pagos',
    subitems: [
      { vista: 'cobros', icono: 'lista', etiqueta: 'Ventas y cobros' },
      { vista: 'registrar', icono: 'agregar', etiqueta: 'Registrar venta', permisos: [PERMISO.ventasRegistrar] },
      { vista: 'clientes', icono: 'proveedores', etiqueta: 'Historial de clientes' },
      { vista: 'precios', icono: 'precio', etiqueta: 'Precios de venta' },
    ],
  },
  {
    ruta: 'pagos',
    icono: 'pagos',
    etiqueta: 'Pagos',
    permiso: PERMISO.pagosRead,
    grupo: 'Ventas y pagos',
    subitems: [
      { vista: 'unico', icono: 'persona', etiqueta: 'Pagar a un proveedor' },
      { vista: 'global', icono: 'proveedores', etiqueta: 'Pagar a varios' },
      { vista: 'historial', icono: 'historial', etiqueta: 'Historial de pagos' },
    ],
  },
  {
    ruta: 'contabilidad',
    icono: 'contabilidad',
    etiqueta: 'Contabilidad',
    permiso: PERMISO.contabilidadRead,
    grupo: 'Ventas y pagos',
  },
  {
    ruta: 'reglas-leche',
    icono: 'ajustes',
    etiqueta: 'Precios y calidad',
    permiso: PERMISO.parametrosRead,
    grupo: 'Configuración',
  },
];

export const DESCRIPCIONES_NAV: Readonly<Record<string, string>> = {
  dashboard: 'Elige una tarea y revisa lo que requiere atención.',
  recojo: 'Consulta lo que recogen los acopiadores en ruta.',
  calidad: 'Análisis de la app por aprobar, avisos e historial.',
  conciliacion: 'Anota cuánta leche llegó de cada ruta y compárala con lo que recogió el acopiador.',
  'entregas-planta': 'Registra proveedor y litros de la leche que el proveedor trae a planta.',
  rutas: 'Ubica proveedores, organiza rutas y sigue el recorrido.',
  zonas: 'Consulta las personas y rutas de cada zona.',
  'solicitudes-zona': 'Revisa las solicitudes para cambiar de zona.',
  recetas: 'Catálogo de productos terminados y la receta de cada uno.',
  'produccion-queso': 'Planifica órdenes, registra lo producido y revisa los lotes.',
  compras: 'Compra insumos y confirma lo que llega al almacén.',
  inventario: 'Stock del almacén, kardex y lotes con su vencimiento.',
  'venta-queso': 'Registra ventas y confirma los cobros.',
  pagos: 'Calcula y paga las liquidaciones de proveedores.',
  contabilidad: 'Consulta los movimientos contables del negocio.',
  usuarios: 'Crea y gestiona usuarios internos, acopiadores y proveedores.',
  'reglas-leche': 'Configura los precios por litro y los criterios de calidad.',
};

/** Permisos con los que se ve el item (el principal y sus alternativos). */
export function permisosDeItem(item: ItemNav): string[] {
  return [item.permiso, ...(item.tambienCon ?? [])];
}

export function normalizarBusquedaNav(texto: string): string {
  return texto
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase()
    .trim();
}
export function coincideBusquedaNav(item: ItemNav, busqueda: string): boolean {
  const texto = normalizarBusquedaNav(
    [
      item.etiqueta,
      item.etiquetaEscritura ?? '',
      item.grupo,
      DESCRIPCIONES_NAV[item.ruta] ?? '',
      item.ruta,
      ...(item.subitems ?? []).map((sub) => sub.etiqueta),
    ].join(' '),
  );
  return normalizarBusquedaNav(busqueda)
    .split(/\s+/)
    .every((palabra) => texto.includes(palabra));
}
