import { Injectable, signal } from '@angular/core';

/** Cómo se dibuja un recorrido GPS real: como línea, como puntos o ambos. */
export type ModoRecorrido = 'TRAZADO' | 'PUNTOS' | 'AMBOS';

export const MODOS_RECORRIDO: { valor: ModoRecorrido; etiqueta: string }[] = [
  { valor: 'TRAZADO', etiqueta: 'Trazado' },
  { valor: 'PUNTOS', etiqueta: 'Puntos' },
  { valor: 'AMBOS', etiqueta: 'Ambos' },
];

const CLAVE = 'huata.modoRecorrido';

/**
 * Preferencia de cada persona sobre cómo ver el recorrido. Se guarda en el navegador (es una
 * comodidad de quien mira, no un dato del negocio): si el almacenamiento no está disponible, se usa
 * "Trazado" y la elección vale solo para esta sesión.
 */
@Injectable({ providedIn: 'root' })
export class PreferenciasMapaService {
  readonly modoRecorrido = signal<ModoRecorrido>(leer());

  cambiarModo(modo: ModoRecorrido): void {
    this.modoRecorrido.set(modo);
    try {
      localStorage.setItem(CLAVE, modo);
    } catch {
      // Sin almacenamiento (modo privado, bloqueado): se aplica solo en esta sesión.
    }
  }
}

function leer(): ModoRecorrido {
  try {
    const v = localStorage.getItem(CLAVE);
    if (v === 'TRAZADO' || v === 'PUNTOS' || v === 'AMBOS') return v;
  } catch {
    // Se usa el valor por defecto.
  }
  return 'TRAZADO';
}
