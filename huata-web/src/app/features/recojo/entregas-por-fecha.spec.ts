import { Recojo } from '../../core/models/models';
import {
  agruparPorDia,
  diasDelRango,
  fechaIsoLocal,
  moverPeriodo,
  rangoDePeriodo,
  resumirEntregas,
} from './entregas-por-fecha';

function entrega(id: number, fechaHora: Date, litros: number, extra: Partial<Recojo> = {}): Recojo {
  return {
    id,
    clientUuid: `u-${id}`,
    productorId: extra.productorId ?? id,
    productorNombre: `Proveedor ${id}`,
    fechaHora: fechaHora.toISOString(),
    litros,
    estado: 'SYNCED',
    fuente: 'PLANTA',
    ...extra,
  } as Recojo;
}

describe('Entregas directas por fechas', () => {
  it('la semana es la de liquidación: de jueves a miércoles', () => {
    // 2026-10-27 es martes: su semana empezó el jueves 22.
    expect(rangoDePeriodo('SEMANA', '2026-10-27')).toEqual({ desde: '2026-10-22', hasta: '2026-10-28' });
    // Un jueves abre su propia semana.
    expect(rangoDePeriodo('SEMANA', '2026-10-29')).toEqual({ desde: '2026-10-29', hasta: '2026-11-04' });
  });

  it('el mes cubre del primero al último día, también en febrero', () => {
    expect(rangoDePeriodo('MES', '2026-02-15')).toEqual({ desde: '2026-02-01', hasta: '2026-02-28' });
  });

  it('el rango libre se ordena aunque se elijan las fechas al revés', () => {
    expect(rangoDePeriodo('RANGO', '2026-10-27', { desde: '2026-10-20', hasta: '2026-10-10' })).toEqual({
      desde: '2026-10-10',
      hasta: '2026-10-20',
    });
  });

  it('las flechas mueven un día, una semana, un mes o un rango del mismo largo', () => {
    const r = { desde: '2026-10-01', hasta: '2026-10-05' };
    expect(moverPeriodo('DIA', '2026-10-01', r, -1).referencia).toBe('2026-09-30');
    expect(moverPeriodo('SEMANA', '2026-10-27', r, 1).referencia).toBe('2026-11-03');
    expect(moverPeriodo('MES', '2026-01-31', r, 1).referencia).toBe('2026-02-01');
    expect(moverPeriodo('RANGO', '2026-10-01', r, 1).rango).toEqual({ desde: '2026-10-06', hasta: '2026-10-10' });
  });

  it('lista todos los días del rango, incluidos los que no tienen entregas', () => {
    expect(diasDelRango({ desde: '2026-10-30', hasta: '2026-11-02' })).toEqual([
      '2026-10-30',
      '2026-10-31',
      '2026-11-01',
      '2026-11-02',
    ]);
  });

  it('agrupa por día (el más reciente arriba) y no suma las canceladas', () => {
    const lunes = new Date(2026, 9, 26, 8, 0);
    const martes = new Date(2026, 9, 27, 7, 30);
    const martesTarde = new Date(2026, 9, 27, 9, 15);
    const dias = agruparPorDia([
      entrega(1, lunes, 40),
      entrega(2, martes, 25, { productorId: 7 }),
      entrega(3, martesTarde, 30, { productorId: 7 }),
      entrega(4, martesTarde, 99, { estado: 'CANCELLED' }),
    ]);

    expect(dias.map((d) => d.fecha)).toEqual([fechaIsoLocal(martes), fechaIsoLocal(lunes)]);
    expect(dias[0].litros).toBe(55);
    expect(dias[0].cantidad).toBe(2);
    expect(dias[0].proveedores).toBe(1);
    expect(dias[0].canceladas).toBe(1);
    expect(dias[0].entregas[0].id).not.toBe(2); // la más tardía va primero
  });

  it('el resumen cuenta litros, proveedores y las que faltan analizar', () => {
    const d = new Date(2026, 9, 27, 8);
    const resumen = resumirEntregas([
      entrega(1, d, 20, { resultadoCalidad: 'ACEPTADA' }),
      entrega(2, d, 30),
      entrega(3, d, 50, { estado: 'CANCELLED' }),
    ]);
    expect(resumen.litros).toBe(50);
    expect(resumen.entregas).toBe(2);
    expect(resumen.promedio).toBe(25);
    expect(resumen.pendientesAnalisis).toBe(1);
    expect(resumen.canceladas).toBe(1);
    expect(resumen.diasConEntrega).toBe(1);
  });
});
