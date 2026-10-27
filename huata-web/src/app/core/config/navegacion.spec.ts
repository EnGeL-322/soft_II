import { ItemNav, NAVEGACION, GRUPOS_NAV, PERMISO, permisosDeItem } from './navegacion';

/**
 * El menu lateral se deriva de los permisos que envia el backend. Estas pruebas no comprueban que
 * ocultar algo sea una barrera —no lo es, la barrera es @PreAuthorize— sino dos cosas distintas:
 * que el mapa que comparten el sidebar y los guards sea coherente, y que lo que ve cada rol
 * coincida con la matriz de MatrizPermisos.java, acotada al pliego consolidado (solo produccion,
 * compras, inventario, acopio, rutas, zonas, proveedores, acopiadores, quesos y usuarios).
 */
const CODIGOS: string[] = Object.values(PERMISO);

/** Misma regla que ShellComponent.etiquetaDe: la palabra la decide el permiso de escritura. */
function etiquetaDe(item: ItemNav, permisos: string[]): string {
  return item.etiquetaEscritura && item.permisoEscritura && permisos.includes(item.permisoEscritura)
    ? item.etiquetaEscritura
    : item.etiqueta;
}

function menuDe(permisos: string[]) {
  return NAVEGACION.filter((item) => permisosDeItem(item).some((p) => permisos.includes(p))).map((item) => ({
    grupo: item.grupo,
    ruta: item.ruta,
    etiqueta: etiquetaDe(item, permisos),
  }));
}

// Subconjuntos de MatrizPermisos.POR_ROL (huata/.../usuarios/MatrizPermisos.java) restringidos a los
// permisos que existen en el catalogo de esta web. Si el backend cambia una asignacion, el backend manda.
const ADMINISTRADOR = [
  PERMISO.panelRead,
  PERMISO.calidadRead,
  PERMISO.acopioRead,
  PERMISO.rutasRead,
  PERMISO.zonasRead,
  PERMISO.proveedoresRead,
  PERMISO.acopiadoresRead,
  PERMISO.quesosRead,
  PERMISO.usuariosRead,
  PERMISO.proveedoresWrite,
  PERMISO.acopiadoresWrite,
  PERMISO.rutasWrite,
  PERMISO.usuariosWrite,
  PERMISO.produccionRead,
  PERMISO.recetasRead,
  PERMISO.recetasWrite,
  PERMISO.comprasRead,
  PERMISO.inventarioRead,
  PERMISO.inventarioWrite,
];

const ACOPIADOR = [
  PERMISO.panelRead,
  PERMISO.acopioRead,
  PERMISO.rutasRead,
  PERMISO.proveedoresRead,
  PERMISO.acopioWrite,
];

const PRODUCTOR_QUESO = [
  PERMISO.panelRead,
  PERMISO.produccionRead,
  PERMISO.produccionWrite,
  PERMISO.recetasRead,
  PERMISO.inventarioRead,
];

describe('NAVEGACION · coherencia del mapa', () => {
  it('cada item apunta a un permiso existente del catalogo', () => {
    for (const item of NAVEGACION) {
      for (const permiso of permisosDeItem(item)) {
        expect(CODIGOS).toContain(permiso);
      }
      if (item.permisoEscritura) {
        expect(CODIGOS).toContain(item.permisoEscritura);
      }
    }
  });

  it('cada item pertenece a uno de los grupos declarados', () => {
    for (const item of NAVEGACION) {
      expect(GRUPOS_NAV).toContain(item.grupo);
    }
  });

  it('no hay rutas repetidas', () => {
    const rutas = NAVEGACION.map((i) => i.ruta);
    expect(new Set(rutas).size).toBe(rutas.length);
  });

  /**
   * Una pantalla que sirve para consultar Y para registrar debe abrirse con el `.read`: si exigiera
   * el `.write`, quien solo consulta no veria una pantalla que si puede abrir.
   */
  it('todo item se abre con un permiso de lectura', () => {
    for (const item of NAVEGACION) {
      for (const permiso of permisosDeItem(item)) {
        expect(permiso.endsWith('.read')).toBe(true);
      }
    }
  });

  it('la etiqueta alternativa siempre viene acompanada de su permiso, y viceversa', () => {
    for (const item of NAVEGACION) {
      expect(Boolean(item.etiquetaEscritura)).toBe(Boolean(item.permisoEscritura));
    }
  });
});

describe('NAVEGACION · lo que ve cada rol', () => {
  it('el administrador ve los modulos maestros pero no las pantallas operativas de registro', () => {
    const rutas = menuDe(ADMINISTRADOR).map((i) => i.ruta);
    expect(rutas).toEqual([
      'dashboard',
      'calidad',
      'recojo',
      'conciliacion',
      'entregas-planta',
      'usuarios',
      'rutas',
      'zonas',
      'solicitudes-zona',
      'recetas',
      'produccion-queso',
      'compras',
      'inventario',
      'venta-queso',
    ]);
    // Los recojos en ruta se consultan en la web: los registra el acopiador con la app móvil.
    expect(menuDe(ADMINISTRADOR).find((i) => i.ruta === 'recojo')?.etiqueta).toBe('Recojos en ruta');
  });

  it('el acopiador ve su ruta del dia, sus recojos (en consulta) y los proveedores que visita', () => {
    // Tiene proveedores.read (backend) pero no proveedores.write ni acopiadores.read: entra a Usuarios
    // solo con la pestaña Proveedores (consulta) y puede leer solicitudes de zona.
    const menu = menuDe(ACOPIADOR);
    expect(menu.map((i) => i.ruta)).toEqual([
      'dashboard',
      'recojo',
      'conciliacion',
      'entregas-planta',
      'usuarios',
      'rutas',
      'solicitudes-zona',
    ]);
    // Registra sus recojos con la app móvil (2026-10-14): en la web el item es de consulta.
    expect(menu.find((i) => i.ruta === 'recojo')?.etiqueta).toBe('Recojos en ruta');
  });

  it('el productor de queso ve produccion y receta en consulta, nada de compras ni ventas', () => {
    const rutas = menuDe(PRODUCTOR_QUESO).map((i) => i.ruta);
    expect(rutas).toEqual(['dashboard', 'recetas', 'produccion-queso', 'inventario']);
    expect(rutas).not.toContain('compras');
    expect(rutas).not.toContain('venta-queso');
  });

  it('Usuarios se ve con cualquiera de los permisos de sus pestanas (internos, acopiadores, proveedores)', () => {
    for (const permiso of [PERMISO.usuariosRead, PERMISO.acopiadoresRead, PERMISO.proveedoresRead]) {
      expect(menuDe([permiso]).map((i) => i.ruta)).toContain('usuarios');
    }
  });

  it('el rol proveedor no tiene ningun item: su sitio es el portal publico y sus endpoints /me', () => {
    expect(menuDe([])).toEqual([]);
  });
});
