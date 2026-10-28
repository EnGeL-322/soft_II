import { MateriaPrima } from '../../core/models/models';
import {
  avisoDePrecio,
  filaCompraVacia,
  filasDeRecepcion,
  filasRecibidas,
  porQueNoAparece,
  problemaDeFila,
  problemaDeRecepcion,
} from './compra-formulario';

const CUAJO: MateriaPrima = { id: 9, nombre: 'cuajo', unidadMedida: 'GRAMO', stock: 0, activo: true, costoUnitarioReferencial: 0.02 };
const SAL: MateriaPrima = { id: 4, nombre: 'Sal', unidadMedida: 'KG', stock: 0, activo: true };
const LECHE: MateriaPrima = { id: 1, nombre: 'Leche fresca', unidadMedida: 'LITRO', stock: 262, activo: true, lecheAcopio: true };
const MATERIAS = [CUAJO, SAL, LECHE];

describe('Registrar compra: cada fila dice qué le falta', () => {
  it('una fila a medio llenar ya no se descarta en silencio', () => {
    const filas = [{ materiaPrimaId: 9, cantidad: 1000, costoUnitario: null }];
    expect(problemaDeFila(filas, 0, MATERIAS)).toBe('Escribe el precio.');
  });

  it('el mismo insumo dos veces pide sumarlo en la primera fila', () => {
    const filas = [
      { materiaPrimaId: 9, cantidad: 1000, costoUnitario: 0.02 },
      { materiaPrimaId: 9, cantidad: 10, costoUnitario: 0.02 },
    ];
    expect(problemaDeFila(filas, 0, MATERIAS)).toBeNull();
    expect(problemaDeFila(filas, 1, MATERIAS)).toBe('cuajo ya está en la fila 1: suma la cantidad ahí.');
  });

  it('cantidad cero o vacía y precio negativo no pasan; precio cero sí (regalo, muestra)', () => {
    expect(problemaDeFila([{ materiaPrimaId: 9, cantidad: 0, costoUnitario: 1 }], 0, MATERIAS)).toBe('Escribe cuánto se compró.');
    expect(problemaDeFila([{ materiaPrimaId: 9, cantidad: 5, costoUnitario: -1 }], 0, MATERIAS)).toBe(
      'El precio no puede ser negativo.',
    );
    expect(problemaDeFila([{ materiaPrimaId: 9, cantidad: 5, costoUnitario: 0 }], 0, MATERIAS)).toBeNull();
  });

  it('cantidad o precio sin insumo pide elegirlo', () => {
    expect(problemaDeFila([{ materiaPrimaId: null, cantidad: 5, costoUnitario: null }], 0, MATERIAS)).toBe('Elige el insumo.');
  });

  it('las filas en blanco se ignoran, salvo que todas lo estén', () => {
    const filas = [{ materiaPrimaId: 9, cantidad: 5, costoUnitario: 1 }, filaCompraVacia()];
    expect(problemaDeFila(filas, 1, MATERIAS)).toBeNull();
    expect(problemaDeFila([filaCompraVacia()], 0, MATERIAS)).toBe('Elige al menos un insumo.');
  });
});

describe('Registrar compra: precio sospechoso', () => {
  it('S/ 20 por gramo cuando antes costaba S/ 0.02 avisa (el error de la captura del cliente)', () => {
    expect(avisoDePrecio({ materiaPrimaId: 9, cantidad: 1000, costoUnitario: 20 }, CUAJO, 'g')).toBe(
      'La última vez costó S/ 0.02 por g. Revisa que el precio sea por g.',
    );
  });

  it('un precio parecido al anterior, o sin precio anterior, no avisa', () => {
    expect(avisoDePrecio({ materiaPrimaId: 9, cantidad: 1000, costoUnitario: 0.03 }, CUAJO, 'g')).toBeNull();
    expect(avisoDePrecio({ materiaPrimaId: 4, cantidad: 1, costoUnitario: 999 }, SAL, 'kg')).toBeNull();
  });
});

describe('Registrar compra: por qué no aparece lo que escribo', () => {
  it('existe en el inventario pero ninguna receta lo lleva (el caso «sal»)', () => {
    expect(porQueNoAparece('sal', MATERIAS)).toBe(
      '«Sal» no lo lleva ninguna receta. Agrégalo a una receta (Catálogo y recetas → Recetas) para comprarlo.',
    );
  });

  it('no existe (el caso «zal»)', () => {
    expect(porQueNoAparece('zal', MATERIAS)).toBe('«zal» no existe. Escríbelo en una receta (Catálogo y recetas → Recetas) y aparecerá aquí.');
  });

  it('la leche no se compra', () => {
    expect(porQueNoAparece('leche fresca', MATERIAS)).toBe('La leche no se compra: entra al almacén al recibir las rutas.');
  });
});

describe('Recepción: lo que realmente llegó', () => {
  const COMPRA = {
    id: 1,
    proveedorCompraId: 1,
    proveedorCompraNombre: 'Insumos SAC',
    fecha: '2026-10-28',
    total: 0,
    activo: true,
    items: [
      { id: 10, materiaPrimaId: 4, materiaPrimaNombre: 'Sal', unidadMedida: 'KG' as const, cantidad: 10, costoUnitario: 2, total: 20, cantidadRecibida: 4, pendiente: 6 },
      { id: 11, materiaPrimaId: 9, materiaPrimaNombre: 'cuajo', unidadMedida: 'GRAMO' as const, cantidad: 500, costoUnitario: 0.1, total: 50, cantidadRecibida: 500, pendiente: 0 },
    ],
  };

  it('propone solo lo que falta recibir, con todo lo pendiente', () => {
    const filas = filasDeRecepcion(COMPRA, (u) => (u === 'KG' ? 'kg' : 'g'));
    expect(filas.map((f) => [f.compraItemId, f.pendiente, f.cantidad, f.unidad])).toEqual([[10, 6, 6, 'kg']]);
  });

  it('no deja recibir más de lo pendiente ni algo ya vencido; cero es «no llegó»', () => {
    const [f] = filasDeRecepcion(COMPRA, () => 'kg');
    expect(problemaDeRecepcion({ ...f, cantidad: 7 }, '2026-10-28')).toBe('Quedan por recibir 6 kg.');
    expect(problemaDeRecepcion({ ...f, fechaVencimiento: '2026-10-27' }, '2026-10-28')).toBe('Llega ya vencido: revisa la fecha.');
    expect(problemaDeRecepcion({ ...f, cantidad: 0 }, '2026-10-28')).toBeNull();
    expect(filasRecibidas([{ ...f, cantidad: 0 }, { ...f, cantidad: 2 }])).toHaveLength(1);
  });
});
