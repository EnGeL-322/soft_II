import { Injectable, signal } from '@angular/core';

export type TipoDialogo = 'confirmacion' | 'peligro' | 'aviso' | 'info';

export interface OpcionesConfirmacion {
  titulo: string;
  mensaje?: string;
  /** Texto del botón principal: «Eliminar», «Pagar», «Guardar cambios»... */
  confirmar?: string;
  cancelar?: string;
  /** `peligro` pinta el botón principal en rojo (eliminar, anular, dar de baja). */
  tipo?: TipoDialogo;
}

export interface OpcionesTexto extends OpcionesConfirmacion {
  etiqueta: string;
  placeholder?: string;
  /** Si es obligatorio, el botón principal queda deshabilitado mientras esté vacío. */
  obligatorio?: boolean;
  valorInicial?: string;
}

export interface DialogoAbierto {
  id: number;
  opciones: OpcionesConfirmacion & Partial<OpcionesTexto>;
  /** true si pide texto (reemplazo de window.prompt). */
  conTexto: boolean;
  resolver: (valor: string | boolean | null) => void;
}

/**
 * Ventanas de confirmación (reemplazan window.confirm / window.prompt). Devuelven una promesa: la
 * operación se ejecuta solo si se resuelve en true (o con el texto); al cancelar no se toca nada.
 *
 *   if (!(await this.dialogo.confirmar({ titulo: '¿Eliminar este registro?',
 *         mensaje: 'Esta acción no se puede deshacer.', confirmar: 'Eliminar', tipo: 'peligro' }))) return;
 *
 * Se muestra una a la vez; si se pide otra mientras hay una abierta, espera su turno.
 */
@Injectable({ providedIn: 'root' })
export class DialogoService {
  readonly actual = signal<DialogoAbierto | null>(null);
  private readonly cola: DialogoAbierto[] = [];
  private siguienteId = 1;

  confirmar(opciones: OpcionesConfirmacion): Promise<boolean> {
    return this.abrir(opciones, false).then((v) => v === true);
  }

  /** Pide un texto (motivo, observación). Devuelve null si se cancela. */
  pedirTexto(opciones: OpcionesTexto): Promise<string | null> {
    return this.abrir(opciones, true).then((v) => (typeof v === 'string' ? v : null));
  }

  /** Lo llama la ventana al pulsar un botón, Escape o fuera de ella. */
  responder(valor: string | boolean | null): void {
    const d = this.actual();
    if (!d) return;
    this.actual.set(this.cola.shift() ?? null);
    d.resolver(valor);
  }

  private abrir(opciones: OpcionesConfirmacion | OpcionesTexto, conTexto: boolean): Promise<string | boolean | null> {
    return new Promise((resolver) => {
      const dialogo: DialogoAbierto = { id: this.siguienteId++, opciones, conTexto, resolver };
      if (this.actual()) this.cola.push(dialogo);
      else this.actual.set(dialogo);
    });
  }
}
