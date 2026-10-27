/**
 * Qué panel ve cada rol al entrar.
 *
 * <p>Antes había un solo panel que escondía tarjetas según los permisos. El resultado era que
 * Calidad, Secretaría y Productor de queso entraban a un panel de acopio de leche con huecos: lo
 * suyo no estaba, y lo que quedaba visible no era su trabajo. Aquí cada rol declara **su** panel:
 * su encabezado y sus bloques, en su orden.
 *
 * <p>Esto NO es control de acceso — eso lo deciden los permisos, en el backend y en el guard de la
 * ruta (sección 0 del CLAUDE.md, RNF-15). Un bloque solo se dibuja si el rol lo declara **y**
 * además tiene el permiso para leer sus datos: declarar uno de más no da acceso a nada.
 */

/** Bloques disponibles. Cada uno se dibuja solo si además hay permiso para leer sus datos. */
export type BloquePanel =
  | 'litrosPorDia'
  | 'atencionDelDia'
  | 'cadenaProductiva'
  | 'acopioPorRuta'
  | 'calidadDelDia'
  | 'liquidacionesDelCiclo'
  | 'produccionDelDia'
  | 'accesosDeMiRol';

export interface PanelDeRol {
  kicker: string;
  titulo: string;
  entradilla: string;
  bloques: readonly BloquePanel[];
}

/**
 * Panel por rol. Están los cinco roles que pueden entrar a la web; el Proveedor no aparece porque
 * no tiene `panel.read` — su sitio es la app móvil.
 *
 * El orden de las claves importa: quien tenga varios roles ve el primero que coincida, para que un
 * administrador que además es de calidad siga entrando a su panel completo.
 */
export const PANEL_POR_ROL: ReadonlyArray<readonly [string, PanelDeRol]> = [
  [
    'ADMINISTRADOR',
    {
      kicker: 'Supervisión del negocio',
      titulo: 'Panel general',
      entradilla: 'Del acopio de leche a la venta de quesos. Toda la operación, en un solo lugar.',
      bloques: [
        'litrosPorDia',
        'atencionDelDia',
        'cadenaProductiva',
        'acopioPorRuta',
        'accesosDeMiRol',
      ],
    },
  ],
  [
    'CALIDAD',
    {
      kicker: 'Control de calidad',
      titulo: 'Panel de calidad',
      entradilla: 'Los controles del día y las incidencias que están esperando revisión.',
      bloques: ['calidadDelDia', 'litrosPorDia', 'atencionDelDia', 'accesosDeMiRol'],
    },
  ],
  [
    'SECRETARIA',
    {
      kicker: 'Secretaría y finanzas',
      titulo: 'Panel de pagos',
      entradilla: 'Liquidaciones del ciclo, ventas del día y lo que queda por cobrar o pagar.',
      bloques: ['liquidacionesDelCiclo', 'cadenaProductiva', 'litrosPorDia', 'accesosDeMiRol'],
    },
  ],
  [
    'PRODUCTOR_QUESO',
    {
      kicker: 'Producción de queso',
      titulo: 'Panel de producción',
      entradilla: 'Los lotes del día, lo que se produjo y lo que entró al inventario.',
      bloques: ['produccionDelDia', 'accesosDeMiRol'],
    },
  ],
  [
    'ACOPIADOR',
    {
      kicker: 'Recojo en ruta',
      titulo: 'Panel de acopio',
      entradilla: 'Lo recogido hoy y cómo va cada ruta.',
      bloques: ['litrosPorDia', 'acopioPorRuta', 'accesosDeMiRol'],
    },
  ],
];

/**
 * Red de seguridad: una cuenta sin ninguno de los seis roles no debería existir (el backend exige
 * al menos uno), pero si llegara una no se la deja con la pantalla en blanco.
 */
export const PANEL_POR_DEFECTO: PanelDeRol = {
  kicker: 'Consulta',
  titulo: 'Panel general',
  entradilla: 'Resumen de la operación del día.',
  bloques: ['litrosPorDia', 'atencionDelDia', 'cadenaProductiva', 'accesosDeMiRol'],
};

export function panelDe(roles: readonly string[]): PanelDeRol {
  return PANEL_POR_ROL.find(([rol]) => roles.includes(rol))?.[1] ?? PANEL_POR_DEFECTO;
}
