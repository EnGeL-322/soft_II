import { MateriaPrima, Receta, UnidadMedida } from '../../core/models/models';
import { RecetaItemRequest } from '../../core/services/produccion.service';
import { claveNombre, nombresParecidos } from '../../core/util/nombres-parecidos';

/**
 * Lógica del editor de recetas, sin Angular: cómo se reconoce un ingrediente escrito a mano, cuándo
 * es nuevo, cuándo se parece a otro y qué se envía al guardar. Vive aparte para poder probarla.
 */

/** Una fila del editor tal como la escribe la persona. */
export interface FilaIngrediente {
  nombre: string;
  cantidad: number | null;
  /** Solo cuenta si el ingrediente es nuevo: el de uno existente lo fija el inventario. */
  unidad: UnidadMedida;
  /** Ingrediente con el que llegó la fila al abrir una receta guardada (puede estar dado de baja). */
  original?: { id: number; nombre: string; unidad: UnidadMedida };
  /** La persona eligió volver a usar un ingrediente dado de baja. */
  reactivar?: boolean;
}

export type EstadoFila =
  | { tipo: 'vacia' }
  | { tipo: 'existente'; materia: MateriaPrima }
  | { tipo: 'nuevo'; parecido: MateriaPrima | null }
  | { tipo: 'baja'; nombre: string; unidad: UnidadMedida }
  | { tipo: 'reactivar'; nombre: string; unidad: UnidadMedida };

export const UNIDADES: ReadonlyArray<{ valor: UnidadMedida; texto: string }> = [
  { valor: 'GRAMO', texto: 'gramos (g)' },
  { valor: 'KG', texto: 'kilos (kg)' },
  { valor: 'ML', texto: 'mililitros (ml)' },
  { valor: 'LITRO', texto: 'litros (L)' },
  { valor: 'UNIDAD', texto: 'unidades' },
];

export const filaVacia = (): FilaIngrediente => ({ nombre: '', cantidad: null, unidad: 'GRAMO' });

export { claveNombre };

/**
 * Un ingrediente casi igual a lo escrito ("zal" → "sal"), activo o dado de baja. Con la regla del
 * cliente esto bloquea: no se crea un segundo insumo por un error de tipeo. Primero el activo.
 */
export function ingredienteParecido(nombre: string, materias: readonly MateriaPrima[]): MateriaPrima | null {
  const parecidos = materias.filter((m) => nombresParecidos(nombre, m.nombre));
  return parecidos.find((m) => m.activo !== false) ?? parecidos[0] ?? null;
}

/**
 * Qué es la fila ahora mismo. `materias` puede traer también las dadas de baja (`activo: false`):
 * - escrita a mano con el nombre de una dada de baja → se reactivará (la persona la pidió por nombre);
 * - venida de la receta guardada y hoy dada de baja → se avisa, y vuelve solo si se pide a propósito.
 */
export function estadoDeFila(fila: FilaIngrediente, materias: readonly MateriaPrima[]): EstadoFila {
  const clave = claveNombre(fila.nombre);
  if (!clave) return { tipo: 'vacia' };
  const activas = materias.filter((m) => m.activo !== false);
  const materia = activas.find((m) => claveNombre(m.nombre) === clave);
  if (materia) return { tipo: 'existente', materia };

  const o = fila.original;
  const deLaReceta = o && claveNombre(o.nombre) === clave;
  if (deLaReceta && !fila.reactivar) return { tipo: 'baja', nombre: o.nombre, unidad: o.unidad };
  const dadaDeBaja = materias.find((m) => m.activo === false && claveNombre(m.nombre) === clave);
  if (dadaDeBaja) return { tipo: 'reactivar', nombre: dadaDeBaja.nombre, unidad: dadaDeBaja.unidadMedida };
  if (deLaReceta) return { tipo: 'reactivar', nombre: o.nombre, unidad: o.unidad };

  return { tipo: 'nuevo', parecido: ingredienteParecido(fila.nombre, materias) };
}

/** Las filas al abrir una receta guardada. Los nombres salen de la receta, no del inventario activo. */
export function filasDeReceta(receta: Receta | null): FilaIngrediente[] {
  if (!receta?.items.length) return [filaVacia()];
  return receta.items.map((i) => {
    const unidad = i.unidadMedida as UnidadMedida;
    return {
      nombre: i.materiaPrimaNombre,
      cantidad: i.cantidadPorUnidad,
      unidad,
      original: { id: i.materiaPrimaId, nombre: i.materiaPrimaNombre, unidad },
    };
  });
}

/** Problema de una fila, o null si está lista. La vacía del todo no es problema: se ignora. */
export function problemaDeFila(
  fila: FilaIngrediente,
  estado: EstadoFila,
  repetida: boolean,
): string | null {
  const sinCantidad = fila.cantidad == null || !(fila.cantidad > 0);
  if (estado.tipo === 'vacia') return fila.cantidad != null ? 'Escribe qué ingrediente es.' : null;
  if (repetida) return 'Este ingrediente ya está en otra fila. Déjalo una sola vez con la cantidad total.';
  if (estado.tipo === 'baja') return 'Fue dado de baja en Inventario: quítalo o pulsa «Volver a usarlo».';
  if (estado.tipo === 'nuevo' && estado.parecido) {
    return `Es casi igual a «${estado.parecido.nombre}», que ya existe. Usa «${estado.parecido.nombre}» o escribe un nombre que se distinga.`;
  }
  if (sinCantidad) return 'Falta la cantidad (mayor que cero).';
  return null;
}

/** Índices de las filas cuyo ingrediente ya aparece más arriba. */
export function filasRepetidas(filas: readonly FilaIngrediente[]): Set<number> {
  const vistas = new Set<string>();
  const repetidas = new Set<number>();
  filas.forEach((f, i) => {
    const clave = claveNombre(f.nombre);
    if (!clave) return;
    if (vistas.has(clave)) repetidas.add(i);
    vistas.add(clave);
  });
  return repetidas;
}

/**
 * Lo que se envía: los existentes por id; los nuevos (y los que se reactivan) por nombre y unidad,
 * para que el servidor los cree o reactive en la misma transacción que la receta.
 */
export function itemsParaGuardar(
  filas: readonly FilaIngrediente[],
  materias: readonly MateriaPrima[],
): RecetaItemRequest[] {
  return filas
    .map((fila) => ({ fila, estado: estadoDeFila(fila, materias) }))
    .filter(({ estado }) => estado.tipo !== 'vacia')
    .map(({ fila, estado }) =>
      estado.tipo === 'existente'
        ? { materiaPrimaId: estado.materia.id, cantidadPorUnidad: fila.cantidad! }
        : {
            nombre: fila.nombre.trim().replace(/\s+/g, ' '),
            unidadMedida: estado.tipo === 'reactivar' ? estado.unidad : fila.unidad,
            cantidadPorUnidad: fila.cantidad!,
          },
    );
}
