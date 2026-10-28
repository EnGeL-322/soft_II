import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { of } from 'rxjs';
import { CapacidadProduccion, LoteQueso } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { QueseriaService } from '../../core/services/queseria.service';
import { ProduccionQuesoComponent } from './produccion-queso.component';

/**
 * 1 queso lleva 10 L de leche y 0.05 kg de sal. Hay 1000 L y 0.75 kg: la leche alcanza para 100 y
 * la sal para 15, así que la sal limita.
 */
const CAPACIDAD: CapacidadProduccion = {
  productoId: 1,
  productoNombre: 'Queso fresco',
  unidadVenta: 'unidad',
  recetaId: 7,
  tieneReceta: true,
  recetaLlevaLeche: true,
  maximoProducible: 15,
  limitanteId: 3,
  limitanteNombre: 'Sal',
  lecheDisponible: 1000,
  items: [
    {
      materiaPrimaId: 2,
      materiaPrimaNombre: 'Leche fresca',
      unidadMedida: 'LITRO',
      cantidadPorUnidad: 10,
      stockActual: 1000,
      alcanzaPara: 100,
      limitante: false,
      esLeche: true,
    },
    {
      materiaPrimaId: 3,
      materiaPrimaNombre: 'Sal',
      unidadMedida: 'KG',
      cantidadPorUnidad: 0.05,
      stockActual: 0.75,
      alcanzaPara: 15,
      limitante: true,
      esLeche: false,
    },
  ],
};

describe('Producción: cuánto se puede producir y qué se descuenta', () => {
  let crearLote: ReturnType<typeof vi.fn>;

  function crear() {
    crearLote = vi.fn(() => of({ codigo: 'L2026-0001', unidadesProducidas: 15, productoNombre: 'Queso fresco' } as LoteQueso));
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideRouter([]),
        { provide: QueseriaService, useValue: { crearLote, lotes: () => of([]) } },
        { provide: AuthService, useValue: { puede: () => true, nombreCompleto: () => 'Ana Ccama' } },
      ],
    });
    const c = TestBed.createComponent(ProduccionQuesoComponent).componentInstance;
    c.productoId = 1;
    c.capacidad.set(CAPACIDAD);
    c.responsable = 'Ana Ccama';
    return c;
  }

  it('marca el insumo limitante y los demás como que alcanzan', () => {
    const c = crear();
    expect(c.limitante()?.materiaPrimaNombre).toBe('Sal');
    expect(c.estadoInsumo(CAPACIDAD.items[0])).toBe('alcanza');
    expect(c.estadoInsumo(CAPACIDAD.items[1])).toBe('limita');
  });

  it('pasar del máximo marca en falta al insumo que no alcanza y bloquea guardar', () => {
    const c = crear();
    c.cantidad = 16;
    expect(c.excedeMaximo()).toBe(true);
    expect(c.estadoInsumo(CAPACIDAD.items[1])).toBe('falta');
    expect(c.insumosQueFaltan().map((i) => i.materiaPrimaNombre)).toEqual(['Sal']);
    expect(c.puedeGuardar()).toBe(false);
    expect(c.faltantes()).toContain('no alcanza para más de 15');
  });

  it('"Máximo" llena la cantidad con lo que se puede producir', () => {
    const c = crear();
    c.usarMaximo();
    expect(c.cantidad).toBe(15);
    expect(c.puedeGuardar()).toBe(true);
  });

  it('muestra antes de guardar cuánto se descuenta de cada insumo y cuánto queda', () => {
    const c = crear();
    c.cantidad = 10;
    const previsto = c.consumoPrevisto();
    expect(previsto.map((p) => [p.item.materiaPrimaNombre, p.descontar, p.queda])).toEqual([
      ['Leche fresca', 100, 900],
      ['Sal', 0.5, 0.25],
    ]);
  });

  it('sin receta no deja guardar y lo dice', () => {
    const c = crear();
    c.capacidad.set({ ...CAPACIDAD, tieneReceta: false, recetaId: undefined, items: [], maximoProducible: undefined });
    c.cantidad = 5;
    expect(c.faltantes()).toContain('falta la receta');
    expect(c.puedeGuardar()).toBe(false);
  });

  it('una receta sin leche no deja guardar', () => {
    const c = crear();
    c.capacidad.set({ ...CAPACIDAD, recetaLlevaLeche: false });
    c.cantidad = 5;
    expect(c.faltantes()).toContain('la receta no dice cuánta leche lleva');
  });

  it('guarda sin mandar consumos: el servidor descuenta la receta sobre lo puesto a producir', () => {
    const c = crear();
    c.cantidad = 15;
    c.salieron = 14;
    c.guardar();
    expect(crearLote).toHaveBeenCalledTimes(1);
    const enviado = crearLote.mock.calls[0][0];
    expect(enviado.cantidadObjetivo).toBe(15);
    expect(enviado.unidadesProducidas).toBe(14);
    expect(enviado.recetaId).toBe(7);
    expect('consumos' in enviado).toBe(false);
    expect('litrosDestinados' in enviado).toBe(false);
  });

  it('si no se dice cuántas salieron, se guarda lo que se puso a producir', () => {
    const c = crear();
    c.cantidad = 12;
    c.guardar();
    expect(crearLote.mock.calls[0][0].unidadesProducidas).toBe(12);
  });

  it('el costo aproximado usa lo que se va a descontar; sin precio de un insumo no inventa cero', () => {
    const c = crear();
    c.cantidad = 10;
    expect(c.costoMaterias()).toBeNull();
    c.materias.set([
      { id: 2, nombre: 'Leche fresca', unidadMedida: 'LITRO', stock: 1000, activo: true, costoUnitarioReferencial: 1.7 },
      { id: 3, nombre: 'Sal', unidadMedida: 'KG', stock: 0.75, activo: true, costoUnitarioReferencial: 4 },
    ]);
    // 100 L x 1.70 + 0.5 kg x 4 = 172
    expect(c.costoMaterias()).toBe(172);
    c.manoObraFilas.set([{ trabajadorId: 1, monto: 28 }]);
    expect(c.costoEstimado()).toBe(200);
    expect(c.costoPorUnidad()).toBe(20);
    c.salieron = 8;
    expect(c.costoPorUnidad()).toBe(25);
  });

  it('cada tarjeta de producto dice cuánto alcanza o por qué no se puede', () => {
    const c = crear();
    c.productos.set([
      { id: 1, nombre: 'Queso fresco', unidadVenta: 'unidad', activo: true },
      { id: 2, nombre: 'Yogurt', unidadVenta: 'botella 1 L', activo: true },
      { id: 3, nombre: 'Manjar', unidadVenta: 'unidad', activo: true },
      { id: 4, nombre: 'Paria', unidadVenta: 'unidad', activo: true },
    ]);
    c.capacidades.set(
      new Map([
        [1, CAPACIDAD],
        [2, { ...CAPACIDAD, unidadVenta: 'botella 1 L', maximoProducible: 0 }],
        [3, { ...CAPACIDAD, tieneReceta: false, items: [] }],
      ]),
    );
    expect(c.tarjetas().map((t) => [t.texto, t.tono])).toEqual([
      ['Alcanza para 15 unidades', 'ok'],
      ['No alcanza para producir', 'falta'],
      ['Sin receta', 'falta'],
      ['Mirando el almacén…', 'cargando'],
    ]);
  });

  it('la tabla del almacén muestra lo que se usa y lo que queda solo con una cantidad escrita', () => {
    const c = crear();
    expect(c.filasInsumos().map((f) => [f.usa, f.queda])).toEqual([
      [null, null],
      [null, null],
    ]);
    c.cantidad = 10;
    expect(c.filasInsumos().map((f) => [f.item.materiaPrimaNombre, f.estado, f.usa, f.queda])).toEqual([
      ['Leche fresca', 'alcanza', 100, 900],
      ['Sal', 'limita', 0.5, 0.25],
    ]);
  });

  it('al guardar, el formulario queda abierto y limpio para la siguiente producción', () => {
    const c = crear();
    c.panelAbierto.set(true);
    c.cantidad = 15;
    c.guardar();
    expect(c.panelAbierto()).toBe(true);
    expect(c.ultimoGuardado()?.codigo).toBe('L2026-0001');
    expect(c.productoId).toBeNull();
    expect(c.cantidad).toBeNull();
  });

  it('dice "unidades" en plural y respeta otras formas de venta', () => {
    const c = crear();
    expect(c.unidadVenta(15)).toBe('unidades');
    expect(c.unidadVenta(1)).toBe('unidad');
    c.capacidad.set({ ...CAPACIDAD, unidadVenta: 'botella 1 L' });
    expect(c.unidadVenta(15)).toBe('botella 1 L');
  });
});
