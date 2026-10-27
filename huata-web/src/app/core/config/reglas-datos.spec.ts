import { enmascarar, REGLAS } from './reglas-datos';

/**
 * Las reglas del formulario tienen que decir lo mismo que el backend
 * (`org.ramiro.huata.common.DatosPersonales`). No son la validación de verdad —esa es la del
 * servidor— pero si divergen, el formulario acepta algo que la API rechaza y el usuario se queda sin
 * entender por qué no puede guardar.
 *
 * Se prueban con las mismas entradas que el backend, a propósito: es la forma de notar la divergencia.
 */
describe('REGLAS de datos personales', () => {
  /**
   * Se compila **sin banderas**, igual que `Validators.pattern` de Angular. Es deliberado: con la
   * bandera `u` un patrón que use `\p{L}` parecería correcto aquí y luego rechazaría todos los nombres
   * en el formulario real. La prueba tiene que compilar el patrón como lo compila el framework.
   */
  const cumple = (patron: string, valor: string) => new RegExp(`^${patron}$`).test(valor);

  describe('DNI: 8 dígitos exactos', () => {
    it('acepta un DNI peruano real', () => {
      expect(cumple(REGLAS.dni, '40404040')).toBe(true);
      expect(cumple(REGLAS.dni, '70707070')).toBe(true);
    });

    it('rechaza 7 y 9 dígitos', () => {
      expect(cumple(REGLAS.dni, '1234567')).toBe(false);
      expect(cumple(REGLAS.dni, '123456789')).toBe(false);
    });

    it('rechaza letras, espacios y guiones', () => {
      ['4040404a', '4040 404', '404-4040', '', '        '].forEach((malo) =>
        expect(cumple(REGLAS.dni, malo)).toBe(false),
      );
    });
  });

  describe('nombre: sin números', () => {
    it('rechaza números y símbolos', () => {
      ['Elsa2', '123', 'Elsa@', 'Elsa_Ramos', '#Elsa', '4'].forEach((malo) =>
        expect(cumple(REGLAS.nombre, malo)).toBe(false),
      );
    });

    it('rechaza una sola letra: no es un nombre, es un dato de prueba que se quedó', () => {
      expect(cumple(REGLAS.nombre, 'r')).toBe(false);
    });

    /** Los nombres reales de la zona tienen que seguir entrando. */
    it('acepta tildes, ñ, apellidos compuestos y apóstrofo', () => {
      ['Elsa', 'José María', 'Ñawpa', 'Quispe Mamani', 'Quispe-Mamani', "D'Angelo", 'RAMOS CALSIN'].forEach((bueno) =>
        expect(cumple(REGLAS.nombre, bueno)).toBe(true),
      );
    });
  });

  describe('teléfono: 6 a 15 dígitos', () => {
    it('acepta un móvil peruano con y sin separadores', () => {
      ['951000111', '951 000 111', '+51 951 000 111'].forEach((bueno) =>
        expect(cumple(REGLAS.telefono, bueno)).toBe(true),
      );
    });

    /** Cuenta dígitos, no caracteres: con un rango de longitud total, "12345" colaba. */
    it('rechaza menos de 6 dígitos', () => {
      expect(cumple(REGLAS.telefono, '12345')).toBe(false);
    });

    it('rechaza letras', () => {
      ['abcdefghi', '951-000-11a'].forEach((malo) => expect(cumple(REGLAS.telefono, malo)).toBe(false));
    });
  });
});

/**
 * El enmascarado es lo que hace compatibles "el código es el DNI" con la prohibición de exponer el
 * DNI en el panel público (sección 30, RNF-12). Tiene que dar lo mismo que el backend.
 */
describe('enmascarar', () => {
  it('deja ver solo los últimos cuatro caracteres', () => {
    expect(enmascarar('40404040')).toBe('****4040');
  });

  it('oculta por completo un valor corto, que no se podría enmascarar sin dejarlo reconocible', () => {
    expect(enmascarar('12')).toBe('**');
  });

  it('no asume la forma del dato: un código heredado también se enmascara', () => {
    expect(enmascarar('DEMO-001')).toBe('****-001');
  });

  it('tolera vacío y nulo sin romper la pantalla', () => {
    expect(enmascarar('')).toBe('');
    expect(enmascarar(undefined)).toBe('');
    expect(enmascarar(null)).toBe('');
  });
});
