import { mismoNombre } from './inventario.component';

describe('mismoNombre (confirmar borrado de insumo)', () => {
  it('acepta el nombre sin tildes, en minúsculas o con espacios de más', () => {
    expect(mismoNombre('azucar demo', 'Azúcar DEMO')).toBe(true);
    expect(mismoNombre('  Azúcar   DEMO ', 'Azúcar DEMO')).toBe(true);
    expect(mismoNombre('CLORURO DE CALCIO demo', 'Cloruro de calcio DEMO')).toBe(true);
  });

  it('rechaza un nombre distinto', () => {
    expect(mismoNombre('azucar', 'Azúcar DEMO')).toBe(false);
    expect(mismoNombre('sal', 'Sal DEMO')).toBe(false);
  });
});
