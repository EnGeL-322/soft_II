import { LoteQueso, Receta } from '../../core/models/models';
import { avisosDeVencimiento, stockPorProducto, usosPorMateriaPrima } from './inventario-resumen';

function receta(id: number, productoId: number, nombre: string, desde: string, mps: number[]): Receta {
  return {
    id,
    productoId,
    productoNombre: nombre,
    unidadVenta: 'unidad',
    vigenteDesde: desde,
    items: mps.map((m) => ({
      materiaPrimaId: m,
      materiaPrimaNombre: `MP${m}`,
      unidadMedida: 'KG',
      cantidadPorUnidad: 1,
    })),
  };
}

describe('usosPorMateriaPrima', () => {
  it('lista los productos cuya receta vigente lleva cada materia prima', () => {
    const usos = usosPorMateriaPrima(
      [receta(1, 10, 'Queso fresco', '2026-01-01', [1, 2]), receta(2, 20, 'Yogur', '2026-01-01', [1, 3])],
      '2026-09-27',
    );
    expect(usos.get(1)).toEqual(['Queso fresco', 'Yogur']);
    expect(usos.get(2)).toEqual(['Queso fresco']);
    expect(usos.get(3)).toEqual(['Yogur']);
  });

  it('solo cuenta la versión que rige: un insumo que se dejó de usar no figura', () => {
    const usos = usosPorMateriaPrima(
      [
        receta(1, 10, 'Queso fresco', '2026-01-01', [1, 2]),
        receta(2, 10, 'Queso fresco', '2026-06-01', [1]),
        receta(3, 10, 'Queso fresco', '2026-12-01', [1, 9]),
      ],
      '2026-09-27',
    );
    expect(usos.get(2)).toBeUndefined();
    expect(usos.get(9)).toBeUndefined();
    expect(usos.get(1)).toEqual(['Queso fresco']);
  });
});

describe('stockPorProducto', () => {
  it('suma el stock en cámara por producto e ignora los lotes agotados', () => {
    const lotes = [
      { id: 1, productoId: 10, productoNombre: 'Queso fresco', unidadVenta: 'unidad', stock: 5 },
      { id: 2, productoId: 10, productoNombre: 'Queso fresco', unidadVenta: 'unidad', stock: 3 },
      { id: 3, productoId: 20, productoNombre: 'Yogur', unidadVenta: 'botella', stock: 12 },
      { id: 4, productoId: 20, productoNombre: 'Yogur', unidadVenta: 'botella', stock: 0 },
    ] as LoteQueso[];

    expect(stockPorProducto(lotes)).toEqual([
      { productoId: 10, productoNombre: 'Queso fresco', unidadVenta: 'unidad', stock: 8, lotes: 2 },
      { productoId: 20, productoNombre: 'Yogur', unidadVenta: 'botella', stock: 12, lotes: 1 },
    ]);
  });
});

describe('avisosDeVencimiento', () => {
  const lote = (codigo: string, stock: number, vence?: string) =>
    ({ codigo, productoNombre: 'Yogur', stock, fechaVencimiento: vence }) as LoteQueso;

  it('muestra lo vencido y lo que vence pronto, del más urgente al menos', () => {
    const avisos = avisosDeVencimiento(
      [
        lote('L1', 5, '2026-09-30'),
        lote('L2', 3, '2026-09-25'),
        lote('L3', 8, '2026-10-20'),
        lote('L4', 2),
      ],
      '2026-09-27',
    );
    expect(avisos.map((a) => [a.loteCodigo, a.dias])).toEqual([
      ['L2', -2],
      ['L1', 3],
    ]);
  });

  it('un lote sin stock no avisa aunque esté vencido', () => {
    expect(avisosDeVencimiento([lote('L1', 0, '2026-09-01')], '2026-09-27')).toEqual([]);
  });
});
