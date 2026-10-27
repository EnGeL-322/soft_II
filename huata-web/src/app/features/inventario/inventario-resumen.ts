import { LoteQueso, Receta } from '../../core/models/models';

/**
 * Qué productos usan cada materia prima, según la receta que rige hoy para cada producto (la más
 * reciente que ya empezó: mismo criterio que `RecetaService.vigente`). Las versiones viejas no
 * cuentan: un insumo que se dejó de usar no debe figurar como necesario.
 */
export function usosPorMateriaPrima(recetas: Receta[], hoy: string): Map<number, string[]> {
  const vigentePorProducto = new Map<number, Receta>();
  for (const r of recetas) {
    if (r.vigenteDesde > hoy) continue;
    const actual = vigentePorProducto.get(r.productoId);
    if (!actual || r.vigenteDesde > actual.vigenteDesde) vigentePorProducto.set(r.productoId, r);
  }
  const usos = new Map<number, string[]>();
  for (const r of vigentePorProducto.values()) {
    for (const item of r.items) {
      const lista = usos.get(item.materiaPrimaId) ?? [];
      if (!lista.includes(r.productoNombre)) lista.push(r.productoNombre);
      usos.set(item.materiaPrimaId, lista.sort((a, b) => a.localeCompare(b, 'es')));
    }
  }
  return usos;
}

export interface StockProducto {
  productoId: number;
  productoNombre: string;
  unidadVenta: string;
  stock: number;
  lotes: number;
}

/** Producto terminado en cámara, sumado por producto (solo lotes con stock). */
export function stockPorProducto(lotes: LoteQueso[]): StockProducto[] {
  const porProducto = new Map<number, StockProducto>();
  for (const l of lotes) {
    if (l.stock <= 0) continue;
    const fila = porProducto.get(l.productoId) ?? {
      productoId: l.productoId,
      productoNombre: l.productoNombre,
      unidadVenta: l.unidadVenta,
      stock: 0,
      lotes: 0,
    };
    fila.stock += l.stock;
    fila.lotes += 1;
    porProducto.set(l.productoId, fila);
  }
  return [...porProducto.values()].sort((a, b) =>
    a.productoNombre.localeCompare(b.productoNombre, 'es'),
  );
}

export interface AvisoVencimiento {
  loteCodigo: string;
  productoNombre: string;
  stock: number;
  fechaVencimiento: string;
  /** Días que faltan (negativo = ya venció). */
  dias: number;
}

/**
 * Lotes con stock vencidos o que vencen dentro de `dentroDeDias`, del más urgente al menos. Los lotes
 * sin fecha de vencimiento no aparecen: no se inventa una.
 */
export function avisosDeVencimiento(lotes: LoteQueso[], hoy: string, dentroDeDias = 3): AvisoVencimiento[] {
  const base = new Date(hoy + 'T12:00:00').getTime();
  return lotes
    .filter((l) => l.stock > 0 && !!l.fechaVencimiento)
    .map((l) => ({
      loteCodigo: l.codigo,
      productoNombre: l.productoNombre,
      stock: l.stock,
      fechaVencimiento: l.fechaVencimiento!,
      dias: Math.round((new Date(l.fechaVencimiento + 'T12:00:00').getTime() - base) / 86_400_000),
    }))
    .filter((a) => a.dias <= dentroDeDias)
    .sort((a, b) => a.dias - b.dias);
}

/** La receta que rige hoy para un producto (la más reciente que ya empezó), o null. */
export function recetaVigenteDe(recetas: Receta[], productoId: number, hoy: string): Receta | null {
  return (
    recetas
      .filter((r) => r.productoId === productoId && r.vigenteDesde <= hoy)
      .sort((a, b) => b.vigenteDesde.localeCompare(a.vigenteDesde))[0] ?? null
  );
}
