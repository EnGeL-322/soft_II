import { Liquidacion, LiquidacionDetalle } from '../../core/models/models';
import { bonificacionesDeBoleta, descuentosDeBoleta, diasDeBoleta, litrosAdicionalesDeBoleta, tarifaDeBoleta } from './boleta-pago';

function liquidacion(cambios: Partial<Liquidacion> = {}): Liquidacion {
  return {
    id: 10,
    productorId: 1,
    productorNombre: 'Ana Apaza',
    periodoInicio: '2026-10-29',
    periodoFin: '2026-11-04',
    litrosTotales: 0,
    montoTotal: 0,
    ajustesTotal: 0,
    montoNeto: 0,
    estado: 'CLOSED',
    detalle: [],
    ajustes: [],
    ...cambios,
  };
}

function entrega(fechaHora: string, litros: number, precioAplicado: number, recojoId = 1): LiquidacionDetalle {
  return { recojoId, fechaHora, litros, precioAplicado, subtotal: litros * precioAplicado };
}

describe('Importes de la boleta guardada', () => {
  it('separa el descuento de S/ 20 y el bono de S/ 30 aunque el ajuste neto sea positivo', () => {
    const pago = liquidacion({
      litrosTotales: 50,
      montoTotal: 120,
      ajustesTotal: 10,
      montoNeto: 130,
      ajustes: [
        { id: 1, tipo: 'PENALIDAD', monto: 20, montoConSigno: -20 },
        { id: 2, tipo: 'BONIFICACION', monto: 30, montoConSigno: 30 },
      ],
    });

    expect(descuentosDeBoleta(pago)).toBe(20);
    expect(bonificacionesDeBoleta(pago)).toBe(30);
    expect(pago.montoTotal - descuentosDeBoleta(pago) + bonificacionesDeBoleta(pago)).toBe(pago.montoNeto);
  });

  it('muestra la tarifa aplicada en la liquidación sin rebajarla por litros rechazados a precio cero', () => {
    const pago = liquidacion({
      litrosTotales: 100,
      montoTotal: 52,
      montoNeto: 52,
      detalle: [
        entrega('2026-10-29T12:00:00Z', 20, 2.6),
        entrega('2026-10-30T12:00:00Z', 80, 0, 2),
      ],
    });

    // La tarifa histórica es 2.60: dividir el bruto entre todos los litros daría 0.52.
    expect(tarifaDeBoleta(pago)).toBe('S/ 2.60');
  });

  it('identifica las tarifas mixtas por su rango aunque lleguen desordenadas y haya rechazos', () => {
    const pago = liquidacion({
      detalle: [
        entrega('2026-10-29T12:00:00Z', 10, 2.9),
        entrega('2026-10-30T12:00:00Z', 30, 0, 2),
        entrega('2026-10-31T12:00:00Z', 10, 2.5, 3),
        entrega('2026-11-01T12:00:00Z', 10, 2.7, 4),
        entrega('2026-11-02T12:00:00Z', 10, 2.5, 5),
      ],
    });

    expect(tarifaDeBoleta(pago)).toBe('S/ 2.50 – S/ 2.90');
  });

  it('no inventa una tarifa si ninguna entrega fue valorizada', () => {
    const pago = liquidacion({ detalle: [entrega('2026-10-29T12:00:00Z', 100, 0)] });

    expect(tarifaDeBoleta(pago)).toBe('Sin entregas valorizadas');
  });
});

describe('Días de la boleta guardada', () => {
  it('usa el periodo de esa liquidación y excluye entregas de otro ciclo', () => {
    const pago = liquidacion({
      periodoInicio: '2025-04-10',
      periodoFin: '2025-04-16',
      detalle: [
        entrega('2025-04-10T12:00:00Z', 12, 2.5),
        entrega('2025-04-16T12:00:00Z', 8, 2.5, 2),
        entrega('2026-10-29T12:00:00Z', 999, 2.5, 3),
      ],
    });
    const dias = diasDeBoleta(pago);

    expect(dias.map((dia) => dia.fecha)).toEqual(['10/04', '11/04', '12/04', '13/04', '14/04', '15/04', '16/04']);
    expect(dias.map((dia) => dia.litros)).toEqual([12, 0, 0, 0, 0, 0, 8]);
  });

  it('continúa del jueves al miércoles cuando la semana cruza de mes', () => {
    const dias = diasDeBoleta(liquidacion());

    expect(dias.map((dia) => dia.fecha)).toEqual(['29/10', '30/10', '31/10', '01/11', '02/11', '03/11', '04/11']);
    expect(dias.map((dia) => dia.abbr)).toEqual(['JU', 'VI', 'SA', 'DO', 'LU', 'MA', 'MI']);
  });

  it('continúa al año siguiente sin perder el detalle del primer día de enero', () => {
    const pago = liquidacion({
      periodoInicio: '2026-12-31',
      periodoFin: '2027-01-06',
      detalle: [entrega('2027-01-01T12:00:00Z', 18, 2.5)],
    });
    const dias = diasDeBoleta(pago);

    expect(dias.map((dia) => dia.fecha)).toEqual(['31/12', '01/01', '02/01', '03/01', '04/01', '05/01', '06/01']);
    expect(dias.map((dia) => dia.abbr)).toEqual(['JU', 'VI', 'SA', 'DO', 'LU', 'MA', 'MI']);
    expect(dias.map((dia) => dia.litros)).toEqual([0, 18, 0, 0, 0, 0, 0]);
  });

  it('agrupa los Instants por su fecha UTC del backend y suma las entregas del mismo día', () => {
    const pago = liquidacion({
      detalle: [
        // En Lima este instante aún cae el 28; el snapshot conserva la fecha UTC 29.
        entrega('2026-10-29T04:59:59.999999Z', 7, 2.5),
        entrega('2026-10-29T23:59:59Z', 8, 2.5, 2),
        entrega('2026-10-30T00:00:00Z', 4, 2.5, 3),
      ],
    });

    expect(diasDeBoleta(pago).map((dia) => dia.litros)).toEqual([15, 4, 0, 0, 0, 0, 0]);
  });
});

describe('Litros adicionales a las siete casillas de la boleta', () => {
  it('conserva el registro de fin + 1 a medianoche UTC como adicional aunque en Lima siga siendo miércoles', () => {
    const pago = liquidacion({
      litrosTotales: 18,
      detalle: [
        entrega('2026-11-04T23:59:59.999999Z', 11, 2.5),
        entrega('2026-11-05T00:00:00Z', 7, 2.5, 2),
      ],
    });
    const enCasillas = diasDeBoleta(pago).reduce((total, dia) => total + dia.litros, 0);

    expect(enCasillas).toBe(11);
    expect(litrosAdicionalesDeBoleta(pago)).toBe(7);
    expect(enCasillas + litrosAdicionalesDeBoleta(pago)).toBe(pago.litrosTotales);
  });

  it('en un periodo largo suma los litros de todos los registros que exceden la primera semana', () => {
    const pago = liquidacion({
      periodoFin: '2026-11-11',
      litrosTotales: 45,
      detalle: [
        entrega('2026-10-29T12:00:00Z', 10, 2.5),
        entrega('2026-11-04T12:00:00Z', 20, 2.5, 2),
        entrega('2026-11-05T12:00:00Z', 7, 2.5, 3),
        entrega('2026-11-11T12:00:00Z', 8, 2.5, 4),
      ],
    });
    const dias = diasDeBoleta(pago);
    const enCasillas = dias.reduce((total, dia) => total + dia.litros, 0);

    expect(dias).toHaveLength(7);
    expect(enCasillas).toBe(30);
    expect(litrosAdicionalesDeBoleta(pago)).toBe(15);
    expect(enCasillas + litrosAdicionalesDeBoleta(pago)).toBe(pago.litrosTotales);
  });
});
