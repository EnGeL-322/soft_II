import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { VentaQuesoComponent } from './venta-queso.component';
import { LoteQueso } from '../../core/models/models';
import { productosParaVender, repartirFefo } from './reparto-fefo';

function lote(id: number, productoId: number, stock: number, costo: number, vence: string | null, fecha = '2026-09-10'): LoteQueso {
  return {
    id,
    codigo: `L2026-${id}`,
    productoId,
    productoNombre: productoId === 7 ? 'Queso paria' : 'Yogur',
    unidadVenta: 'unidad',
    stock,
    costoUnitario: costo,
    fechaVencimiento: vence,
    fecha,
  } as LoteQueso;
}

describe('Venta de queso: cálculos del formulario', () => {
  function crear() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
    const c = TestBed.createComponent(VentaQuesoComponent).componentInstance;
    c.fecha = '2026-09-16';
    c.tarifas.set([
      {
        id: 1,
        productoId: 7,
        productoNombre: 'Queso paria',
        promocion: true,
        vigenteDesde: '2026-09-01',
        vigenteHasta: '2026-09-30',
        precioPublicoGeneral: 20,
        precioMayorista: 16,
        precioProveedor: 14,
      },
    ]);
    c.lotes.set([
      lote(1, 7, 10, 9, '2026-09-25'),
      lote(2, 7, 5, 11, '2026-09-20'),
      lote(3, 8, 4, 3, '2026-09-22'),
    ]);
    c.productoId = 7;
    return c;
  }

  it('recalcula precio y total al cambiar comprador y cantidad, sin recargar el catálogo', () => {
    const c = crear();
    c.unidades = 2;
    expect(c.total()).toBe(40);
    c.tipoComprador = 'MAYORISTA';
    c.unidades = 3;
    expect(c.precio()).toBe(16);
    expect(c.total()).toBe(48);
  });

  it('el stock es el del producto (todos sus lotes) y sale primero lo que vence antes', () => {
    const c = crear();
    expect(c.producto()?.stock).toBe(15);
    c.unidades = 7;
    expect(c.reparto().map((t) => [t.loteId, t.unidades])).toEqual([
      [2, 5],
      [1, 2],
    ]);
  });

  it('la diferencia por unidad usa el costo de los lotes de los que sale', () => {
    const c = crear();
    c.unidades = 5; // todo del lote 2 (costo 11)
    expect(c.margenUnidad()).toBe(9);
    c.unidades = 10; // 5 a 11 y 5 a 9: costo promedio 10
    expect(c.margenUnidad()).toBe(10);
  });

  it('el precio es el del producto elegido: otro producto sin precio no hereda este', () => {
    const c = crear();
    c.productoId = 8;
    expect(c.precio()).toBe(0);
  });

  it('no ofrece una tarifa vencida ni una tarifa futura', () => {
    const c = crear();
    c.fecha = '2026-10-01';
    expect(c.precio()).toBe(0);
    c.fecha = '2026-08-31';
    expect(c.precio()).toBe(0);
  });
});

describe('reparto FEFO', () => {
  it('los productos llevan su stock total y el vencimiento del lote que sale primero', () => {
    const p = productosParaVender([lote(1, 7, 3, 9, '2026-10-30'), lote(2, 7, 4, 9, '2026-10-28'), lote(3, 7, 0, 9, '2026-10-01')]);
    expect(p).toEqual([
      jasmineLike({ productoId: 7, stock: 7, proximoVencimiento: '2026-10-28' }),
    ]);
  });

  it('lo que no tiene vencimiento sale al final y no se pide más de lo que hay', () => {
    const tomas = repartirFefo([lote(1, 7, 3, 9, null), lote(2, 7, 2, 9, '2026-11-01')], 7, 10);
    expect(tomas.map((t) => [t.loteId, t.unidades])).toEqual([
      [2, 2],
      [1, 3],
    ]);
  });
});

/** Compara solo los campos dados. */
function jasmineLike(parcial: object) {
  return expect.objectContaining(parcial);
}
