/**
 * Regla de nombres de productos e insumos (pedido del cliente, 2026-10-27): no puede haber dos casi
 * iguales ("yogurd" junto a "yogurt", "zal" junto a "sal"). Es la misma regla que
 * `NombresParecidos.java`; allá es la que manda, aquí sirve para avisar mientras se escribe.
 */

/** Sin tildes, mayúsculas ni espacios de más. */
export function claveNombre(nombre: string): string {
  return nombre
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .replace(/\s+/g, ' ')
    .trim()
    .toLowerCase();
}

export function nombresIguales(a: string, b: string): boolean {
  return claveNombre(a) === claveNombre(b);
}

/**
 * Casi iguales (pero no iguales): a una letra de distancia, o dos si el nombre tiene 8 o más. Menos de
 * 3 letras no se compara, y si los números difieren son distintos ("Envase 1 L" / "Envase 5 L").
 */
export function nombresParecidos(a: string, b: string): boolean {
  const x = claveNombre(a);
  const y = claveNombre(b);
  if (x === y || Math.min(x.length, y.length) < 3) return false;
  if (x.replace(/\D/g, '') !== y.replace(/\D/g, '')) return false;
  const tolerancia = Math.max(x.length, y.length) >= 8 ? 2 : 1;
  return Math.abs(x.length - y.length) <= tolerancia && distancia(x, y) <= tolerancia;
}

/** El primero igual o casi igual al nombre (primero el igual exacto). */
export function buscarChoque<T>(nombre: string, candidatos: readonly T[], nombreDe: (c: T) => string): T | null {
  return (
    candidatos.find((c) => nombresIguales(nombre, nombreDe(c))) ??
    candidatos.find((c) => nombresParecidos(nombre, nombreDe(c))) ??
    null
  );
}

/** Cuántas letras hay que cambiar, poner o quitar para pasar de una a otra. */
function distancia(a: string, b: string): number {
  const fila = Array.from({ length: b.length + 1 }, (_, j) => j);
  for (let i = 1; i <= a.length; i++) {
    let diagonal = fila[0];
    fila[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const arriba = fila[j];
      fila[j] = Math.min(fila[j] + 1, fila[j - 1] + 1, diagonal + (a[i - 1] === b[j - 1] ? 0 : 1));
      diagonal = arriba;
    }
  }
  return fila[b.length];
}
