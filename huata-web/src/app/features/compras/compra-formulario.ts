import { Compra, MateriaPrima } from '../../core/models/models';

/** Una fila del formulario "Registrar compra", tal como la llena la persona. */
export interface FilaCompraItem {
  materiaPrimaId: number | null;
  cantidad: number | null;
  costoUnitario: number | null;
}

export function filaCompraVacia(): FilaCompraItem {
  return { materiaPrimaId: null, cantidad: null, costoUnitario: null };
}

/** Sin nada escrito: no es un error, simplemente no se manda. */
export function filaEnBlanco(f: FilaCompraItem): boolean {
  return f.materiaPrimaId == null && f.cantidad == null && f.costoUnitario == null;
}

/**
 * Qué impide guardar una fila, en palabras de la persona, o null si está bien.
 *
 * <p>Antes, una fila a medio llenar (insumo sin precio, por ejemplo) se descartaba sin aviso al
 * guardar: la compra se registraba sin ese insumo y nadie se enteraba. Ahora cada fila dice qué le
 * falta. Las filas en blanco no cuentan, salvo que sean la única.
 */
export function problemaDeFila(
  filas: readonly FilaCompraItem[],
  index: number,
  materias: readonly MateriaPrima[],
): string | null {
  const f = filas[index];
  if (filaEnBlanco(f)) {
    return filas.every(filaEnBlanco) ? 'Elige al menos un insumo.' : null;
  }
  if (f.materiaPrimaId == null) return 'Elige el insumo.';

  const repetida = filas.findIndex((o, i) => i < index && o.materiaPrimaId === f.materiaPrimaId);
  if (repetida >= 0) {
    const nombre = materias.find((m) => m.id === f.materiaPrimaId)?.nombre ?? 'Este insumo';
    return `${nombre} ya está en la fila ${repetida + 1}: suma la cantidad ahí.`;
  }
  if (f.cantidad == null || !(f.cantidad > 0)) return 'Escribe cuánto se compró.';
  if (f.costoUnitario == null) return 'Escribe el precio.';
  if (f.costoUnitario < 0) return 'El precio no puede ser negativo.';
  return null;
}

/** Cuántas veces más (o menos) que el último precio hace sospechar un error de unidad. */
const FACTOR_SOSPECHOSO = 5;

/**
 * Aviso (no bloquea) cuando el precio se aleja mucho del último pagado. Atrapa el error típico de
 * poner el precio del kilo donde se pide el del gramo: S/ 20 "por g" en vez de por kg.
 */
export function avisoDePrecio(f: FilaCompraItem, materia: MateriaPrima | undefined, unidad: string): string | null {
  const antes = materia?.costoUnitarioReferencial;
  const ahora = f.costoUnitario;
  if (!antes || antes <= 0 || ahora == null || ahora <= 0) return null;
  if (ahora < antes * FACTOR_SOSPECHOSO && ahora > antes / FACTOR_SOSPECHOSO) return null;
  return `La última vez costó S/ ${redondear(antes)} por ${unidad}. Revisa que el precio sea por ${unidad}.`;
}

/**
 * Por qué lo escrito no aparece en la lista de insumos, y dónde se arregla. La lista solo ofrece
 * lo que lleva alguna receta vigente (pedido del cliente, 2026-10-27) y nunca la leche del acopio.
 */
export function porQueNoAparece(texto: string, materias: readonly MateriaPrima[]): string {
  const q = normalizar(texto);
  if (!q) return 'No hay insumos para comprar: primero agrégalos a una receta.';
  const existente = materias.find((m) => normalizar(m.nombre) === q);
  const mostrado = texto.trim();
  if (existente?.lecheAcopio) return 'La leche no se compra: entra al almacén al recibir las rutas.';
  if (existente) {
    return `«${existente.nombre}» no lo lleva ninguna receta. Agrégalo a una receta (Catálogo y recetas → Recetas) para comprarlo.`;
  }
  return `«${mostrado}» no existe. Escríbelo en una receta (Catálogo y recetas → Recetas) y aparecerá aquí.`;
}

// ---- Recepción (V61): lo que realmente llegó de una compra ----

/** Una línea de la compra en el formulario de recepción. */
export interface FilaRecepcion {
  compraItemId: number;
  nombre: string;
  codigo?: string;
  unidad: string;
  pendiente: number;
  /** Lo que llegó ahora. Vacío o 0: no llegó nada de esta línea. */
  cantidad: number | null;
  lote: string;
  fechaVencimiento: string;
}

/** Las líneas que faltan recibir, propuestas con todo lo pendiente (lo normal es que llegue completo). */
export function filasDeRecepcion(compra: Compra, unidad: (u?: string) => string): FilaRecepcion[] {
  return compra.items
    .filter((i) => (i.pendiente ?? i.cantidad) > 0)
    .map((i) => ({
      compraItemId: i.id,
      nombre: i.materiaPrimaNombre,
      codigo: i.materiaPrimaCodigo,
      unidad: unidad(i.unidadMedida),
      pendiente: i.pendiente ?? i.cantidad,
      cantidad: i.pendiente ?? i.cantidad,
      lote: '',
      fechaVencimiento: '',
    }));
}

/** Qué impide recibir esta línea, o null. `hoy` y `fecha` en ISO (yyyy-mm-dd). */
export function problemaDeRecepcion(f: FilaRecepcion, fecha: string): string | null {
  if (f.cantidad == null || f.cantidad === 0) return null;
  if (f.cantidad < 0) return 'La cantidad no puede ser negativa.';
  if (f.cantidad > f.pendiente + 1e-9) return `Quedan por recibir ${f.pendiente} ${f.unidad}.`;
  if (f.fechaVencimiento && fecha && f.fechaVencimiento < fecha) return 'Llega ya vencido: revisa la fecha.';
  return null;
}

/** Las líneas que sí llegaron (cantidad > 0). */
export function filasRecibidas(filas: readonly FilaRecepcion[]): FilaRecepcion[] {
  return filas.filter((f) => f.cantidad != null && f.cantidad > 0);
}

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
}

function redondear(n: number): string {
  return (Math.round(n * 100) / 100).toString();
}
