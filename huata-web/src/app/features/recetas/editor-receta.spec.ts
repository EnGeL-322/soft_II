import { MateriaPrima, Receta } from '../../core/models/models';
import {
  claveNombre,
  estadoDeFila,
  filasDeReceta,
  filasRepetidas,
  ingredienteParecido,
  itemsParaGuardar,
  problemaDeFila,
} from './editor-receta';

const mp = (id: number, nombre: string, unidadMedida: MateriaPrima['unidadMedida'] = 'GRAMO'): MateriaPrima => ({
  id,
  nombre,
  unidadMedida,
  stock: 0,
  activo: true,
});

const SAL = mp(1, 'sal', 'KG');
const AZUCAR = mp(2, 'Azúcar');
const CLORURO = mp(3, 'Cloruro de calcio', 'ML');
const INVENTARIO = [SAL, AZUCAR, CLORURO];

describe('editor de receta', () => {
  it('compara nombres sin tildes, mayúsculas ni espacios de más', () => {
    expect(claveNombre('  AZUCAR  ')).toBe(claveNombre('Azúcar'));
    expect(claveNombre('Cloruro   de calcio')).toBe('cloruro de calcio');
  });

  it('reconoce un ingrediente existente aunque se escriba distinto, y usa su unidad', () => {
    const estado = estadoDeFila({ nombre: 'azucar', cantidad: 5, unidad: 'KG' }, INVENTARIO);
    expect(estado).toEqual({ tipo: 'existente', materia: AZUCAR });
    // La unidad elegida en la fila no cuenta: manda la del inventario.
    expect(itemsParaGuardar([{ nombre: 'azucar', cantidad: 5, unidad: 'KG' }], INVENTARIO)).toEqual([
      { materiaPrimaId: 2, cantidadPorUnidad: 5 },
    ]);
  });

  it('bloquea «zal» si existe «sal» (regla del cliente): no se puede guardar así', () => {
    expect(ingredienteParecido('zal', INVENTARIO)).toBe(SAL);
    const fila = { nombre: 'zal', cantidad: 1, unidad: 'GRAMO' as const };
    const estado = estadoDeFila(fila, INVENTARIO);
    expect(estado).toEqual({ tipo: 'nuevo', parecido: SAL });
    expect(problemaDeFila(fila, estado, false)).toContain('casi igual a «sal»');
    // También contra uno dado de baja: lo correcto es reactivarlo.
    const salDeBaja = { ...SAL, activo: false };
    expect(ingredienteParecido('zal', [salDeBaja])).toBe(salDeBaja);
    // Nombres cortos o muy distintos no se sugieren.
    expect(ingredienteParecido('al', INVENTARIO)).toBeNull();
    expect(ingredienteParecido('vainilla', INVENTARIO)).toBeNull();
  });

  it('un ingrediente nuevo se envía por nombre y con la unidad elegida', () => {
    expect(itemsParaGuardar([{ nombre: '  Fermento   láctico ', cantidad: 2, unidad: 'GRAMO' }], INVENTARIO)).toEqual([
      { nombre: 'Fermento láctico', unidadMedida: 'GRAMO', cantidadPorUnidad: 2 },
    ]);
  });

  it('las filas vacías se ignoran y las repetidas se marcan', () => {
    const filas = [
      { nombre: 'Sal', cantidad: 1, unidad: 'GRAMO' as const },
      { nombre: '', cantidad: null, unidad: 'GRAMO' as const },
      { nombre: 'SAL ', cantidad: 2, unidad: 'GRAMO' as const },
    ];
    expect([...filasRepetidas(filas)]).toEqual([2]);
    expect(itemsParaGuardar(filas.slice(0, 2), INVENTARIO)).toHaveLength(1);
    expect(problemaDeFila(filas[2], estadoDeFila(filas[2], INVENTARIO), true)).toContain('ya está en otra fila');
  });

  it('pide la cantidad y el nombre cuando faltan', () => {
    const sinCantidad = { nombre: 'sal', cantidad: null, unidad: 'GRAMO' as const };
    expect(problemaDeFila(sinCantidad, estadoDeFila(sinCantidad, INVENTARIO), false)).toContain('cantidad');
    const sinNombre = { nombre: ' ', cantidad: 3, unidad: 'GRAMO' as const };
    expect(problemaDeFila(sinNombre, estadoDeFila(sinNombre, INVENTARIO), false)).toContain('ingrediente');
  });

  it('un ingrediente dado de baja se avisa y, al volver a usarlo, se envía por nombre con su unidad', () => {
    const receta = {
      items: [{ materiaPrimaId: 99, materiaPrimaNombre: 'Cuajo', unidadMedida: 'ML', cantidadPorUnidad: 0.5 }],
    } as Receta;
    const [fila] = filasDeReceta(receta);
    const baja = estadoDeFila(fila, INVENTARIO);
    expect(baja.tipo).toBe('baja');
    expect(problemaDeFila(fila, baja, false)).toContain('dado de baja');

    const reactivada = { ...fila, reactivar: true };
    expect(estadoDeFila(reactivada, INVENTARIO).tipo).toBe('reactivar');
    expect(itemsParaGuardar([reactivada], INVENTARIO)).toEqual([
      { nombre: 'Cuajo', unidadMedida: 'ML', cantidadPorUnidad: 0.5 },
    ]);
  });

  it('escribir el nombre de uno dado de baja avisa que se reactivará (no que es nuevo) y no lo sugiere', () => {
    const salDeBaja = { ...mp(10, 'sal', 'KG'), activo: false };
    const zal = mp(14, 'zal');
    const conBajas = [salDeBaja, zal];
    expect(estadoDeFila({ nombre: 'Sal', cantidad: 1, unidad: 'GRAMO' }, conBajas)).toEqual({
      tipo: 'reactivar',
      nombre: 'sal',
      unidad: 'KG',
    });
    // Se envía con su propia unidad, no con la que mostraba la fila.
    expect(itemsParaGuardar([{ nombre: 'Sal', cantidad: 1, unidad: 'GRAMO' }], conBajas)).toEqual([
      { nombre: 'Sal', unidadMedida: 'KG', cantidadPorUnidad: 1 },
    ]);
    // "sall" suena igual que "zal" (activa) y se sugiere esa, nunca la "sal" dada de baja.
    expect(ingredienteParecido('sall', conBajas)?.id).toBe(14);
  });

  it('cambiar el nombre de una fila guardada apunta al nuevo ingrediente, no al de antes', () => {
    const receta = {
      items: [{ materiaPrimaId: 1, materiaPrimaNombre: 'sal', unidadMedida: 'KG', cantidadPorUnidad: 1 }],
    } as Receta;
    const [fila] = filasDeReceta(receta);
    expect(itemsParaGuardar([{ ...fila, nombre: 'Azúcar' }], INVENTARIO)).toEqual([
      { materiaPrimaId: 2, cantidadPorUnidad: 1 },
    ]);
  });
});
