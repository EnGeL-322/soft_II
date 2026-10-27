import { REGLAS } from '../../../core/config/reglas-datos';
import { Productor } from '../../../core/models/models';

/**
 * Lo que le falta a un proveedor pendiente para poder aprobarlo. Es la MISMA lista que exige el
 * backend (`ProductorService.exigirListoParaActivar`); aquí se usa para abrir su ficha y decir qué
 * completar antes de intentarlo, en vez de dejar que la aprobación falle.
 */
export function faltaParaAprobar(p: Pick<Productor, 'documentoIdentidad' | 'nombres' | 'apellidos' | 'rutaId'>): string[] {
  const falta: string[] = [];
  // Angular ancla los patrones de formulario; aquí hay que anclarlo a mano.
  const nombre = new RegExp('^(?:' + REGLAS.nombre + ')$');
  // El DNI es opcional; solo cuenta si se escribió uno que no es válido.
  const dni = p.documentoIdentidad?.trim();
  if (dni && !/^\d{8}$/.test(dni)) {
    falta.push('un DNI válido de 8 dígitos (o dejarlo vacío)');
  }
  if (!p.nombres || !nombre.test(p.nombres.trim())) {
    falta.push('nombres sin números ni símbolos');
  }
  if (!p.apellidos || !nombre.test(p.apellidos.trim())) {
    falta.push('apellidos sin números ni símbolos');
  }
  if (p.rutaId == null) {
    falta.push('una zona y ruta de acopio');
  }
  return falta;
}
