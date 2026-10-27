import { Receta } from '../../core/models/models';
import {
  cantidadLegible,
  ingredientesEnPalabras,
  recetaProgramada,
  recetaVigente,
} from './recetas-texto';

function receta(id: number, productoId: number, desde: string): Receta {
  return {
    id,
    productoId,
    productoNombre: 'Queso fresco',
    unidadVenta: 'unidad',
    vigenteDesde: desde,
    items: [
      { materiaPrimaId: 1, materiaPrimaNombre: 'Leche fresca', unidadMedida: 'LITRO', cantidadPorUnidad: 6 },
      { materiaPrimaId: 2, materiaPrimaNombre: 'Sal', unidadMedida: 'KG', cantidadPorUnidad: 0.035 },
    ],
  };
}

describe('recetas en palabras', () => {
  const HOY = '2026-09-27';
  const vieja = receta(1, 10, '2026-01-01');
  const actual = receta(2, 10, '2026-06-01');
  const futura = receta(3, 10, '2026-12-01');
  const todas = [vieja, actual, futura, receta(4, 20, '2026-01-01')];

  it('la receta que rige es la más reciente que ya empezó, no la futura', () => {
    expect(recetaVigente(todas, 10, HOY)?.id).toBe(2);
    expect(recetaVigente(todas, 99, HOY)).toBeNull();
  });

  it('una receta con fecha futura se reconoce como programada', () => {
    expect(recetaProgramada(todas, 10, HOY)?.id).toBe(3);
    expect(recetaProgramada(todas, 20, HOY)).toBeNull();
  });

  it('describe los ingredientes como se dicen, sin ceros de relleno', () => {
    expect(ingredientesEnPalabras(actual)).toEqual(['6 litros de Leche fresca', '0.035 kg de Sal']);
  });

  it('las cantidades usan el punto decimal de Perú (es-PE)', () => {
    expect(cantidadLegible(1.5)).toBe('1.5');
    expect(cantidadLegible(0.0001)).toBe('0.0001');
  });
});
