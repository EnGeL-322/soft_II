const LETRAS = 'ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789';

/**
 * Contraseña inicial legible (sin 0/O ni 1/l/I) para entregar a la persona por un canal seguro. Se
 * usa en las tres fichas que crean una cuenta: interna, acopiador y proveedor.
 */
export function generarClave(): string {
  const valores = new Uint32Array(12);
  crypto.getRandomValues(valores);
  const cuerpo = Array.from(valores, (v) => LETRAS[v % LETRAS.length]).join('');
  return `${cuerpo.slice(0, 6)}-${cuerpo.slice(6)}*`;
}
