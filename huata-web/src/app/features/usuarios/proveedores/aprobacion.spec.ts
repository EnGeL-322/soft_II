import { faltaParaAprobar } from './aprobacion';

describe('Qué le falta a un proveedor para aprobarlo', () => {
  it('un alta completa no tiene pendientes', () => {
    expect(
      faltaParaAprobar({ documentoIdentidad: '44556677', nombres: 'Elsa', apellidos: 'Ramos Calsin', rutaId: 1 }),
    ).toEqual([]);
  });

  it('lista todo lo que falta de una vez, como lo exige el servidor', () => {
    expect(
      faltaParaAprobar({ documentoIdentidad: '987654321', nombres: 'r', apellidos: 'a1', rutaId: undefined }),
    ).toEqual([
      'un DNI válido de 8 dígitos (o dejarlo vacío)',
      'nombres sin números ni símbolos',
      'apellidos sin números ni símbolos',
      'una zona y ruta de acopio',
    ]);
  });

  it('sin DNI no le falta nada: el DNI es opcional', () => {
    expect(faltaParaAprobar({ documentoIdentidad: undefined, nombres: 'Elsa', apellidos: 'Ramos', rutaId: 1 })).toEqual([]);
  });

  it('acepta tildes, ñ, apóstrofo y guion en los nombres', () => {
    expect(
      faltaParaAprobar({ documentoIdentidad: '44556677', nombres: 'Teófila', apellidos: "D'Ángelo Quispe-Ñahui", rutaId: 3 }),
    ).toEqual([]);
  });
});
