import { LoteQueso } from '../../core/models/models';

/**
 * Venta por producto (pedido del cliente, 2026-10-27): sale primero lo que vence antes (FEFO) y, si
 * un lote no alcanza, se completa con el siguiente. Es la vista previa de la pantalla; quien decide
 * es el servidor (QueseriaService.venderPorProducto), con el mismo orden.
 */
export interface TomaDeLote {
  loteId: number;
  codigo: string;
  fechaVencimiento?: string | null;
  unidades: number;
  costoUnitario?: number | null;
}

export interface ProductoEnCamara {
  productoId: number;
  productoNombre: string;
  unidadVenta: string;
  stock: number;
  /** Fecha en que vence el lote que saldrá primero. */
  proximoVencimiento?: string | null;
}

/** Del que vence antes al que vence después; sin vencimiento al final, del más viejo al más nuevo. */
export function ordenFefo(lotes: LoteQueso[]): LoteQueso[] {
  return [...lotes].sort((a, b) => {
    const va = a.fechaVencimiento ?? '9999-12-31';
    const vb = b.fechaVencimiento ?? '9999-12-31';
    return va !== vb ? va.localeCompare(vb) : a.fecha.localeCompare(b.fecha);
  });
}

/** Productos con unidades para vender, con su stock total y su próximo vencimiento. */
export function productosParaVender(lotes: LoteQueso[]): ProductoEnCamara[] {
  const porProducto = new Map<number, ProductoEnCamara>();
  for (const l of ordenFefo(lotes)) {
    if (l.stock <= 0) continue;
    const p = porProducto.get(l.productoId);
    if (p) {
      p.stock += l.stock;
    } else {
      porProducto.set(l.productoId, {
        productoId: l.productoId,
        productoNombre: l.productoNombre,
        unidadVenta: l.unidadVenta,
        stock: l.stock,
        proximoVencimiento: l.fechaVencimiento ?? null,
      });
    }
  }
  return [...porProducto.values()].sort((a, b) => a.productoNombre.localeCompare(b.productoNombre, 'es'));
}

/** De qué lotes saldrán `unidades` del producto. Si no alcanza, reparte lo que hay. */
export function repartirFefo(lotes: LoteQueso[], productoId: number | null, unidades: number | null): TomaDeLote[] {
  if (productoId == null || !unidades || unidades <= 0) return [];
  let faltan = Math.floor(unidades);
  const tomas: TomaDeLote[] = [];
  for (const l of ordenFefo(lotes.filter((x) => x.productoId === productoId && x.stock > 0))) {
    if (faltan <= 0) break;
    const toma = Math.min(faltan, l.stock);
    tomas.push({
      loteId: l.id,
      codigo: l.codigo,
      fechaVencimiento: l.fechaVencimiento,
      unidades: toma,
      costoUnitario: l.costoUnitario,
    });
    faltan -= toma;
  }
  return tomas;
}
