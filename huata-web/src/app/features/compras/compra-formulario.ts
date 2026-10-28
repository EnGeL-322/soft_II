import { MateriaPrima } from '../../core/models/models';

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
    return `«${existente.nombre}» no lo lleva ninguna receta. Agrégalo a una receta en Recetas y productos para comprarlo.`;
  }
  return `«${mostrado}» no existe. Escríbelo en una receta (Recetas y productos) y aparecerá aquí.`;
}

function normalizar(texto: string): string {
  return texto.normalize('NFD').replace(/[̀-ͯ]/g, '').toLowerCase().replace(/\s+/g, ' ').trim();
}

function redondear(n: number): string {
  return (Math.round(n * 100) / 100).toString();
}
