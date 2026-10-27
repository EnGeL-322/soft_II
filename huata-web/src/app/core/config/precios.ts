import { TarifaQueso } from '../models/models';

/**
 * El periodo de precios que rige para un producto en una fecha. Es la MISMA regla que
 * `QueseriaService.tarifaVigente` en el backend, repetida aquí solo para mostrar el precio antes de
 * vender; quien cobra es el servidor, que lo vuelve a resolver.
 *
 * - Cuenta un periodo si ya empezó y, si tiene fecha de fin (promoción), todavía no terminó.
 * - Si varios cubren la fecha, manda el que empezó más tarde (a igual inicio, el último registrado):
 *   así una promoción manda sobre el precio normal y, al terminar, vuelve el normal solo.
 */
export function tarifaQueRige(
  tarifas: TarifaQueso[],
  productoId: number | null | undefined,
  fecha: string,
): TarifaQueso | null {
  if (productoId == null) return null;
  return (
    tarifas
      .filter(
        (t) =>
          t.productoId === productoId &&
          t.vigenteDesde <= fecha &&
          (!t.vigenteHasta || t.vigenteHasta >= fecha),
      )
      .sort((a, b) => b.vigenteDesde.localeCompare(a.vigenteDesde) || b.id - a.id)[0] ?? null
  );
}

export type EstadoPeriodo = 'RIGE' | 'PROGRAMADO' | 'TERMINADO' | 'REEMPLAZADO';

/**
 * En qué situación está un periodo hoy. REEMPLAZADO es un periodo que cubre la fecha pero no manda
 * porque otro empezó después (p. ej. el precio normal mientras dura una promoción).
 */
export function estadoPeriodo(t: TarifaQueso, tarifas: TarifaQueso[], hoy: string): EstadoPeriodo {
  if (t.vigenteDesde > hoy) return 'PROGRAMADO';
  if (t.vigenteHasta && t.vigenteHasta < hoy) return 'TERMINADO';
  return tarifaQueRige(tarifas, t.productoId, hoy)?.id === t.id ? 'RIGE' : 'REEMPLAZADO';
}
