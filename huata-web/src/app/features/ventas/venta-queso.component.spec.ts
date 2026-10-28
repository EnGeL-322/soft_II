import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { VentaQuesoComponent } from './venta-queso.component';
import { LoteQueso } from '../../core/models/models';
import { estadoVencimiento, filasFefo, productosParaVender, repartirFefo } from './reparto-fefo';

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

describe('vender lo que vence antes, a la vista', () => {
  it('dice claro cuánto falta para que venza cada lote', () => {
    const hoy = '2026-10-28';
    expect(estadoVencimiento('2026-10-27', hoy)).toEqual({ dias: -1, texto: 'Venció hace 1 día', tono: 'vencido' });
    expect(estadoVencimiento('2026-10-28', hoy).texto).toBe('Vence hoy');
    expect(estadoVencimiento('2026-10-29', hoy)).toEqual({ dias: 1, texto: 'Vence mañana', tono: 'urgente' });
    expect(estadoVencimiento('2026-11-02', hoy).tono).toBe('pronto');
    expect(estadoVencimiento('2026-11-20', hoy).tono).toBe('ok');
    expect(estadoVencimiento(null, hoy).tono).toBe('sin');
  });

  it('la tabla lista los lotes en el orden en que salen, cuánto sale de cada uno y cuánto queda', () => {
    const lotes = [lote(1, 7, 10, 9, '2026-11-10'), lote(2, 7, 5, 9, '2026-10-29'), lote(3, 7, 4, 9, null), lote(4, 8, 9, 9, '2026-10-28')];
    const filas = filasFefo(lotes, 7, 8, '2026-10-28');
    expect(filas.map((f) => [f.orden, f.lote.id, f.sale, f.queda, f.vencimiento.tono])).toEqual([
      [1, 2, 5, 0, 'urgente'],
      [2, 1, 3, 7, 'ok'],
      [3, 3, 0, 4, 'sin'],
    ]);
  });

  it('avisa del lote que sale primero si vence pronto y de lo vencido que sigue en cámara', () => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
    const c = TestBed.createComponent(VentaQuesoComponent).componentInstance;
    c.fecha = '2026-10-28';
    c.lotes.set([lote(1, 7, 10, 9, '2026-10-29'), lote(2, 7, 3, 9, '2026-12-01')]);
    c.todosLotes.set([lote(1, 7, 10, 9, '2026-10-29'), lote(2, 7, 3, 9, '2026-12-01'), lote(9, 7, 6, 9, '2026-10-20')]);
    c.productoId = 7;
    expect(c.primeroPorVencer()?.lote.id).toBe(1);
    expect(c.unidadesVencidas()).toBe(6);
    expect(c.vencidosEnCamara().map((l) => l.id)).toEqual([9]);
  });
});

/** Compara solo los campos dados. */
function jasmineLike(parcial: object) {
  return expect.objectContaining(parcial);
}
