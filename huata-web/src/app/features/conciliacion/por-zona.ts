import { ConciliacionRuta } from '../../core/models/models';

/** Una zona (sector) con la suma de sus rutas del día. */
export interface ConciliacionZona {
  zona: string;
  rutas: number;
  rutasSinRecibir: number;
  litrosEnRuta: number;
  litrosEntregadosEnPlanta: number;
  /** Solo de las rutas que ya tienen total de planta; null si ninguna lo tiene todavía. */
  litrosRecibidosPlanta: number | null;
  /** Suma de las diferencias que calculó el servidor para cada ruta ya recibida. */
  diferencia: number | null;
  /** Diferencia sobre lo acopiado en camino de las rutas recibidas, en %. */
  porcentaje: number | null;
}

const SIN_ZONA = 'Sin zona';

/**
 * Agrupa por zona las filas que ya calculó el servidor. Solo SUMA: la diferencia de cada ruta la
 * decide el backend (no se recalcula aquí), así que la vista por zona nunca contradice a la de rutas.
 * La diferencia de la zona se mide solo contra las rutas que planta ya recibió: sumar lo acopiado de
 * una ruta que todavía no llegó inventaría una pérdida que no existe.
 */
export function porZona(filas: ConciliacionRuta[]): ConciliacionZona[] {
  const grupos = new Map<string, ConciliacionRuta[]>();
  for (const f of filas) {
    const z = f.zona?.trim() || SIN_ZONA;
    grupos.set(z, [...(grupos.get(z) ?? []), f]);
  }
  return [...grupos.entries()]
    .map(([zona, rutas]) => {
      const recibidas = rutas.filter((r) => r.litrosRecibidosPlanta != null);
      const enRutaRecibidas = recibidas.reduce((s, r) => s + r.litrosEnRuta, 0);
      const diferencia = recibidas.length ? recibidas.reduce((s, r) => s + (r.diferencia ?? 0), 0) : null;
      return {
        zona,
        rutas: rutas.length,
        rutasSinRecibir: rutas.length - recibidas.length,
        litrosEnRuta: rutas.reduce((s, r) => s + r.litrosEnRuta, 0),
        litrosEntregadosEnPlanta: rutas.reduce((s, r) => s + r.litrosEntregadosEnPlanta, 0),
        litrosRecibidosPlanta: recibidas.length
          ? recibidas.reduce((s, r) => s + (r.litrosRecibidosPlanta ?? 0), 0)
          : null,
        diferencia,
        porcentaje: diferencia != null && enRutaRecibidas > 0 ? (diferencia / enRutaRecibidas) * 100 : null,
      };
    })
    .sort((a, b) => a.zona.localeCompare(b.zona, 'es'));
}
