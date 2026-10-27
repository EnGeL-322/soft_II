import { TarifaQueso } from '../models/models';
import { estadoPeriodo, tarifaQueRige } from './precios';

function periodo(
  id: number,
  productoId: number,
  publico: number,
  desde: string,
  hasta?: string,
): TarifaQueso {
  return {
    id,
    productoId,
    productoNombre: `P${productoId}`,
    precioPublicoGeneral: publico,
    precioMayorista: publico - 1,
    precioProveedor: publico - 2,
    vigenteDesde: desde,
    vigenteHasta: hasta,
    promocion: !!hasta,
  };
}

describe('tarifaQueRige (misma regla que el backend)', () => {
  const normal = periodo(1, 10, 20, '2026-01-01');
  const promo = periodo(2, 10, 15, '2026-09-10', '2026-09-20');
  const yogur = periodo(3, 20, 8, '2026-01-01');
  const todas = [normal, promo, yogur];

  it('cada producto tiene su propio precio', () => {
    expect(tarifaQueRige(todas, 20, '2026-09-15')?.precioPublicoGeneral).toBe(8);
    expect(tarifaQueRige(todas, 10, '2026-09-01')?.precioPublicoGeneral).toBe(20);
  });

  it('la promoción rige entre sus fechas, ambas inclusive', () => {
    expect(tarifaQueRige(todas, 10, '2026-09-10')?.id).toBe(2);
    expect(tarifaQueRige(todas, 10, '2026-09-20')?.id).toBe(2);
  });

  it('al terminar la promoción vuelve el precio normal', () => {
    expect(tarifaQueRige(todas, 10, '2026-09-21')?.id).toBe(1);
  });

  it('un producto sin precios no hereda el de otro', () => {
    expect(tarifaQueRige(todas, 99, '2026-09-15')).toBeNull();
    expect(tarifaQueRige(todas, null, '2026-09-15')).toBeNull();
  });

  it('a igual fecha de inicio manda el último registrado', () => {
    const corregido = periodo(4, 10, 22, '2026-01-01');
    expect(tarifaQueRige([normal, corregido], 10, '2026-05-01')?.id).toBe(4);
  });

  it('dice si un periodo rige, está programado, terminó o quedó reemplazado', () => {
    const futuro = periodo(5, 10, 30, '2026-12-01');
    const lista = [...todas, futuro];
    expect(estadoPeriodo(promo, lista, '2026-09-15')).toBe('RIGE');
    expect(estadoPeriodo(normal, lista, '2026-09-15')).toBe('REEMPLAZADO');
    expect(estadoPeriodo(promo, lista, '2026-09-25')).toBe('TERMINADO');
    expect(estadoPeriodo(futuro, lista, '2026-09-25')).toBe('PROGRAMADO');
    expect(estadoPeriodo(normal, lista, '2026-09-25')).toBe('RIGE');
  });
});
