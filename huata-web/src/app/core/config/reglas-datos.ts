/**
 * Reglas de forma de los datos personales, iguales a las del backend
 * (`org.ramiro.huata.common.DatosPersonales`).
 *
 * Existen aquí para que el usuario vea el error mientras escribe, **no** como la validación de
 * verdad: la que manda es la del servidor, y estas expresiones tienen que decir lo mismo. Si un día
 * divergen, gana el backend y el formulario habrá mentido.
 */
export const REGLAS = {
  /**
   * DNI peruano: 8 dígitos exactos. El código del proveedor es este mismo número.
   *
   * Las barras van dobladas porque esto es una cadena, no un literal de expresión regular: escrito
   * `'\d{8}'`, TypeScript se come la barra y el patrón queda como `d{8}` — rechazaría todos los DNI
   * reales y el formulario no dejaría guardar a nadie.
   */
  dni: '\\d{8}',
  dniMensaje: 'El DNI debe tener exactamente 8 dígitos numéricos.',

  /**
   * Nombres y apellidos: letras (con tildes y ñ), espacios, apóstrofo y guion. Sin números.
   *
   * Se escriben las letras acentuadas una por una en vez de usar `\p{L}` como hace el backend:
   * `\p{L}` **exige la bandera `u`**, y `Validators.pattern` de Angular compila el patrón sin
   * banderas. Sin `u`, `\p{L}` se interpreta como el literal `p{L}` y el campo habría rechazado
   * cualquier nombre real, empezando por "Elsa".
   *
   * Queda un poco más estricto que el backend (que acepta cualquier letra Unicode). Es el lado seguro:
   * el servidor acepta un superconjunto, así que el formulario nunca deja pasar algo que la API
   * rechace. Si algún día hace falta un alfabeto que no esté aquí, se amplía este rango.
   */
  nombre: "[A-Za-zÁÉÍÓÚÜÑáéíóúüñ][A-Za-zÁÉÍÓÚÜÑáéíóúüñ .'\\-]{1,}",
  nombreMensaje: 'Solo letras, espacios, apóstrofo y guion; sin números ni símbolos.',

  /** Teléfono opcional: 6 a 15 dígitos, admite espacios, guiones, paréntesis y un "+" inicial. */
  telefono: '\\+?(?:\\d[ .()\\-]?){6,15}',
  telefonoMensaje: 'Teléfono inválido: use entre 6 y 15 dígitos.',
} as const;

/** Oculta todo menos los últimos 4 caracteres: `****4040`. Mismo criterio que el backend. */
export function enmascarar(valor: string | undefined | null): string {
  if (!valor) return '';
  const limpio = valor.trim();
  if (limpio.length <= 4) return '*'.repeat(limpio.length);
  return '*'.repeat(limpio.length - 4) + limpio.slice(-4);
}
