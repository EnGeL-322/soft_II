import { ConciliacionRuta } from '../../core/models/models';
import { porZona } from './por-zona';

function fila(
  rutaId: number,
  zona: string | undefined,
  enRuta: number,
  recibido?: number,
  diferencia?: number,
  enPlanta = 0,
): ConciliacionRuta {
  return {
    rutaId,
    rutaNombre: `Ruta ${rutaId}`,
    zona,
    totalRecolectado: enRuta + enPlanta,
    litrosEnRuta: enRuta,
    litrosEntregadosEnPlanta: enPlanta,
    proveedoresAcopiados: 1,
    proveedoresNoEntregaron: 0,
    litrosRecibidosPlanta: recibido,
    diferencia,
    detalle: [],
  } as ConciliacionRuta;
}

describe('Conciliación por zona', () => {
  it('suma las rutas de cada zona y ordena las zonas por nombre', () => {
    const z = porZona([
      fila(1, 'YASIN', 100, 98, 2),
      fila(2, 'FAON', 80, 80, 0),
      fila(3, 'YASIN', 50, 45, 5, 10),
    ]);
    expect(z.map((x) => x.zona)).toEqual(['FAON', 'YASIN']);
    const yasin = z[1];
    expect(yasin.rutas).toBe(2);
    expect(yasin.litrosEnRuta).toBe(150);
    expect(yasin.litrosEntregadosEnPlanta).toBe(10);
    expect(yasin.litrosRecibidosPlanta).toBe(143);
    expect(yasin.diferencia).toBe(7);
    expect(yasin.porcentaje).toBeCloseTo((7 / 150) * 100);
  });

  it('la diferencia de la zona cuenta solo las rutas que planta ya recibió', () => {
    const [z] = porZona([fila(1, 'FAON', 100, 97, 3), fila(2, 'FAON', 60)]);
    expect(z.rutasSinRecibir).toBe(1);
    expect(z.diferencia).toBe(3);
    expect(z.porcentaje).toBeCloseTo(3);
  });

  it('una zona sin ninguna ruta recibida no inventa diferencia', () => {
    const [z] = porZona([fila(1, 'COLLANA I', 70)]);
    expect(z.litrosRecibidosPlanta).toBeNull();
    expect(z.diferencia).toBeNull();
    expect(z.porcentaje).toBeNull();
  });

  it('las rutas sin zona se agrupan aparte', () => {
    expect(porZona([fila(1, undefined, 10)])[0].zona).toBe('Sin zona');
  });
});
