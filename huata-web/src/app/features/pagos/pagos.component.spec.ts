import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { PagosComponent, ordenarPorApellido } from './pagos.component';
import { FilaPlanilla } from '../../core/services/liquidaciones.service';

function fila(parcial: Partial<FilaPlanilla>): FilaPlanilla {
  return {
    proveedorId: 1,
    proveedorCodigo: 'DEMO-001',
    proveedorNombre: 'Demo',
    proveedorApellidos: 'Demo',
    zona: null,
    excelencia: false,
    litrosEntregados: 0,
    litrosRechazados: 0,
    litrosPagados: 0,
    litrosPendientes: 0,
    tarifaAplicada: 0,
    importePendiente: 0,
    liquidado: false,
    liquidacionId: null,
    tieneLiquidacionCerrada: false,
    ...parcial,
  };
}

describe('"Imprimir todo" de Pago global: filtro y orden', () => {
  function crear() {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideRouter([])] });
    return TestBed.createComponent(PagosComponent).componentInstance;
  }

  it('solo cuenta como "para imprimir" a los pagados con liquidación, nunca a los pendientes', () => {
    const c = crear();
    c.planilla.set([
      fila({ proveedorId: 1, liquidado: true, liquidacionId: 10, tieneLiquidacionCerrada: true }),
      fila({ proveedorId: 2, liquidado: false, liquidacionId: 11 }),
      // liquidacionId null: no hay boleta que buscar aunque liquidado viniera true por error de dato.
      fila({ proveedorId: 3, liquidado: true, liquidacionId: null }),
    ]);

    expect(c.pagadosParaImprimir().map((f) => f.proveedorId)).toEqual([1]);
  });

  it('el contador se actualiza al cambiar el ciclo (recarga la planilla)', () => {
    const c = crear();
    c.planilla.set([fila({ proveedorId: 1, liquidado: true, liquidacionId: 10, tieneLiquidacionCerrada: true })]);
    expect(c.pagadosParaImprimir().length).toBe(1);

    c.planilla.set([]);
    expect(c.pagadosParaImprimir().length).toBe(0);
  });

  it('ordena por apellido, no por el nombre completo ni por el orden de llegada', () => {
    const items = [
      { liquidacion: null as any, fila: fila({ proveedorId: 1, proveedorApellidos: 'Zapana' }) },
      { liquidacion: null as any, fila: fila({ proveedorId: 2, proveedorApellidos: 'Apaza' }) },
      { liquidacion: null as any, fila: fila({ proveedorId: 3, proveedorApellidos: 'Mamani' }) },
    ];

    expect(ordenarPorApellido(items).map((i) => i.fila.proveedorApellidos)).toEqual([
      'Apaza',
      'Mamani',
      'Zapana',
    ]);
  });

  it('no muta el arreglo original al ordenar', () => {
    const original = [
      { liquidacion: null as any, fila: fila({ proveedorApellidos: 'Zapana' }) },
      { liquidacion: null as any, fila: fila({ proveedorApellidos: 'Apaza' }) },
    ];
    const copiaOrden = original.map((i) => i.fila.proveedorApellidos);

    ordenarPorApellido(original);

    expect(original.map((i) => i.fila.proveedorApellidos)).toEqual(copiaOrden);
  });
});
