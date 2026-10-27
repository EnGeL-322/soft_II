import { Receta } from '../../core/models/models';
import { etiquetaUnidad } from '../../core/config/unidades';

/**
 * La receta que rige hoy para un producto: la más reciente que ya empezó. Es el mismo criterio que
 * `RecetaService.vigente` en el backend (la fecha "hasta" no cuenta en recetas).
 */
export function recetaVigente(
  recetas: Receta[],
  productoId: number,
  hoy: string,
): Receta | null {
  return (
    recetas
      .filter((r) => r.productoId === productoId && r.vigenteDesde <= hoy)
      .sort((a, b) => b.vigenteDesde.localeCompare(a.vigenteDesde) || b.id - a.id)[0] ?? null
  );
}

/** Una receta programada para una fecha futura (todavía no rige). */
export function recetaProgramada(
  recetas: Receta[],
  productoId: number,
  hoy: string,
): Receta | null {
  return (
    recetas
      .filter((r) => r.productoId === productoId && r.vigenteDesde > hoy)
      .sort((a, b) => a.vigenteDesde.localeCompare(b.vigenteDesde))[0] ?? null
  );
}

/** Cantidad sin ceros de relleno, con el formato de Perú: 6 y no 6.0000; 0.035 y no 0.0350. */
export function cantidadLegible(n: number): string {
  return Number(n).toLocaleString('es-PE', { maximumFractionDigits: 4 });
}

/**
 * Los ingredientes de una receta en palabras, uno por línea: "6 litros de Leche fresca". Es lo que
 * se muestra en la tarjeta del producto para que se entienda sin leer una tabla.
 */
export function ingredientesEnPalabras(receta: Receta): string[] {
  return receta.items.map(
    (i) =>
      `${cantidadLegible(i.cantidadPorUnidad)} ${etiquetaUnidad(i.unidadMedida)} de ${i.materiaPrimaNombre}`,
  );
}
