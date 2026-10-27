/**
 * Nombres visibles de los roles predefinidos (mismos que MatrizPermisos.ROLES en el backend). Solo
 * sirve para mostrar el rol de quien inicia sesión sin pedirlo al servidor: el catálogo vivo, con los
 * roles personalizados y sus permisos, lo da GET /roles.
 */
export const ROLES_NEGOCIO = [
  { codigo: 'ADMINISTRADOR', nombre: 'Administrador', descripcion: 'Administración del sistema y de los usuarios.' },
  { codigo: 'SUPERVISOR_PLANTA', nombre: 'Supervisor de planta', descripcion: 'Supervisa acopio, planta y conciliación.' },
  { codigo: 'OPERADOR_PLANTA', nombre: 'Operador de planta', descripcion: 'Recepción en planta y cierre operativo.' },
  { codigo: 'CALIDAD', nombre: 'Analista de calidad', descripcion: 'Control y seguimiento de la calidad de la leche.' },
  { codigo: 'TESTEADOR', nombre: 'Testeador', descripcion: 'Testeo de leche en campo.' },
  { codigo: 'CAJA_CONTABILIDAD', nombre: 'Caja / Contabilidad', descripcion: 'Liquidaciones, pagos y comprobantes.' },
  { codigo: 'SECRETARIA', nombre: 'Secretaría y finanzas', descripcion: 'Gestión de pagos y comercialización.' },
  { codigo: 'ACOPIADOR', nombre: 'Acopiador / Conductor', descripcion: 'Recolección de leche y consulta de sus rutas.' },
  { codigo: 'PROVEEDOR', nombre: 'Proveedor de leche', descripcion: 'Consulta de sus entregas e información personal.' },
  { codigo: 'PRODUCTOR_QUESO', nombre: 'Producción', descripcion: 'Elaboración de productos, insumos y costos.' },
  { codigo: 'COMPRAS_ALMACEN', nombre: 'Compras / Almacén', descripcion: 'Compras, existencias y movimientos.' },
  { codigo: 'VENTAS', nombre: 'Ventas', descripcion: 'Ventas y stock disponible.' },
  { codigo: 'GERENCIA', nombre: 'Gerencia / Consulta', descripcion: 'Consulta ejecutiva en solo lectura.' },
] as const;

/**
 * Roles que no se asignan desde "Usuarios y roles" sino al dar de alta el registro en su propio
 * módulo: el acopiador en Acopiadores (donde además se elige la zona y la ruta donde acopiará) y el
 * proveedor en Proveedores.
 *
 * El backend aplica la misma regla (`Rol.GESTIONADOS_EN_SU_MODULO`): esto es solo la interfaz.
 */
export const ROLES_DE_SU_PROPIO_MODULO: ReadonlySet<string> = new Set(['ACOPIADOR', 'PROVEEDOR']);

/** Nombre visible de un código de rol. Un rol personalizado se muestra a partir de su código. */
export function nombreRol(codigo: string): string {
  const conocido = ROLES_NEGOCIO.find((r) => r.codigo === codigo)?.nombre;
  if (conocido) return conocido;
  const texto = codigo.replace(/_+/g, ' ').toLowerCase().trim();
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}
