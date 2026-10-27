import { Injectable, signal } from '@angular/core';

export type TipoNotificacion = 'exito' | 'error' | 'aviso' | 'info';

export interface Notificacion {
  id: number;
  tipo: TipoNotificacion;
  mensaje: string;
  /** Cuántas veces llegó el mismo mensaje mientras estaba visible (se muestra «×3»). */
  repeticiones: number;
}

export interface OpcionesNotificacion {
  /** Milisegundos visible; 0 = queda hasta que la persona la cierre. */
  duracion?: number;
}

/** Tiempo visible por tipo: los errores quedan más para que se alcancen a leer. */
const DURACION: Record<TipoNotificacion, number> = {
  exito: 3500,
  info: 4000,
  aviso: 6000,
  error: 8000,
};
const MAXIMO_VISIBLES = 4;

/**
 * Notificaciones rápidas (toasts) para acciones rutinarias: «Registro guardado», «No se pudo
 * conectar»... No bloquean la pantalla, desaparecen solas y un mismo mensaje repetido no se apila:
 * se renueva la que ya está visible. Las confirmaciones van por DialogoService.
 *
 * Solo se llaman tras el resultado real del backend (en `next` o `error` de la petición).
 */
@Injectable({ providedIn: 'root' })
export class NotificacionesService {
  readonly notificaciones = signal<Notificacion[]>([]);
  private siguienteId = 1;
  private readonly temporizadores = new Map<number, ReturnType<typeof setTimeout>>();

  exito(mensaje: string, opciones?: OpcionesNotificacion): void {
    this.mostrar('exito', mensaje, opciones);
  }

  error(mensaje: string, opciones?: OpcionesNotificacion): void {
    this.mostrar('error', mensaje, opciones);
  }

  aviso(mensaje: string, opciones?: OpcionesNotificacion): void {
    this.mostrar('aviso', mensaje, opciones);
  }

  info(mensaje: string, opciones?: OpcionesNotificacion): void {
    this.mostrar('info', mensaje, opciones);
  }

  cerrar(id: number): void {
    clearTimeout(this.temporizadores.get(id));
    this.temporizadores.delete(id);
    this.notificaciones.update((lista) => lista.filter((n) => n.id !== id));
  }

  /** Pausa el cierre automático mientras el puntero o el foco están sobre la notificación. */
  pausar(id: number): void {
    clearTimeout(this.temporizadores.get(id));
    this.temporizadores.delete(id);
  }

  reanudar(id: number): void {
    const n = this.notificaciones().find((x) => x.id === id);
    if (n) this.programarCierre(n.id, DURACION[n.tipo]);
  }

  private mostrar(tipo: TipoNotificacion, mensaje: string, opciones?: OpcionesNotificacion): void {
    const texto = mensaje?.trim();
    if (!texto) return;
    const duracion = opciones?.duracion ?? DURACION[tipo];
    const repetida = this.notificaciones().find((n) => n.tipo === tipo && n.mensaje === texto);
    if (repetida) {
      this.notificaciones.update((lista) =>
        lista.map((n) => (n.id === repetida.id ? { ...n, repeticiones: n.repeticiones + 1 } : n)),
      );
      this.programarCierre(repetida.id, duracion);
      return;
    }
    const nueva: Notificacion = { id: this.siguienteId++, tipo, mensaje: texto, repeticiones: 1 };
    const lista = [...this.notificaciones(), nueva];
    // Si se acumulan, se descartan las más antiguas: nunca tapan media pantalla.
    lista.slice(0, Math.max(0, lista.length - MAXIMO_VISIBLES)).forEach((n) => this.cerrar(n.id));
    this.notificaciones.set(lista.slice(-MAXIMO_VISIBLES));
    this.programarCierre(nueva.id, duracion);
  }

  private programarCierre(id: number, duracion: number): void {
    clearTimeout(this.temporizadores.get(id));
    if (duracion > 0) this.temporizadores.set(id, setTimeout(() => this.cerrar(id), duracion));
  }
}
