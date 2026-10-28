import { buscarChoque, nombresIguales, nombresParecidos } from './nombres-parecidos';

/** Misma batería que NombresParecidosTest.java: la web y el servidor deben decidir igual. */
describe('nombres parecidos (regla del cliente)', () => {
  it('los casos del cliente son casi iguales', () => {
    expect(nombresParecidos('zal', 'sal')).toBe(true);
    expect(nombresParecidos('yogurd', 'yogurt')).toBe(true);
    expect(nombresParecidos('yogut', 'yogurt')).toBe(true);
  });

  it('iguales sin tildes, mayúsculas ni espacios; igual no cuenta como parecido', () => {
    expect(nombresIguales('  Azúcar   rubia ', 'AZUCAR RUBIA')).toBe(true);
    expect(nombresParecidos('Azúcar', 'azucar')).toBe(false);
  });

  it('en nombres largos tolera dos letras', () => {
    expect(nombresParecidos('queso fresco', 'queso frezca')).toBe(true);
    expect(nombresParecidos('queso fresco', 'queso andino')).toBe(false);
  });

  it('nombres distintos de verdad no se bloquean', () => {
    expect(nombresParecidos('sal', 'té')).toBe(false);
    expect(nombresParecidos('leche', 'cuajo')).toBe(false);
    expect(nombresParecidos('yogurt', 'yogurt de fresa')).toBe(false);
    expect(nombresParecidos('té', 'tú')).toBe(false);
  });

  it('si los números difieren no son parecidos', () => {
    expect(nombresParecidos('Envase 1 L', 'Envase 5 L')).toBe(false);
    expect(nombresParecidos('Balde 4 kg', 'Balde 4 kgs')).toBe(true);
  });

  it('buscarChoque prefiere el igual exacto', () => {
    expect(buscarChoque('sal', ['zal', 'Sal'], (s) => s)).toBe('Sal');
    expect(buscarChoque('vainilla', ['zal', 'Sal'], (s) => s)).toBeNull();
  });
});
