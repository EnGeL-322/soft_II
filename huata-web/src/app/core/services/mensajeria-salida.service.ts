import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject, signal } from '@angular/core';
import { Subject } from 'rxjs';
import { MensajeProveedor, MensajeriaService } from './mensajeria.service';
import { esperaReconexion } from './mensajeria-tiempo-real.service';

export type EstadoEnCola = 'PENDIENTE' | 'ERROR';
export interface MensajeEnCola { clientUuid:string; productorId:number; cuerpo:string; creadoEn:string; estado:EstadoEnCola; error?:string; }

/**
 * Un rechazo que no se arregla reintentando: la peticion esta mal o no tiene permiso. Todo lo demas
 * (sin red = 0, 401 de sesion vencida, 409, 429, 5xx) se reintenta solo.
 */
export function esErrorDefinitivo(status:number): boolean {
  return status === 400 || status === 403 || status === 404 || status === 422;
}

/** UUID v4. `crypto.randomUUID` solo existe en https o localhost, y el despliegue interno va por http. */
export function nuevoUuid(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') return crypto.randomUUID();
  const b = crypto.getRandomValues(new Uint8Array(16));
  b[6] = (b[6] & 0x0f) | 0x40;
  b[8] = (b[8] & 0x3f) | 0x80;
  const h = Array.from(b, (x) => x.toString(16).padStart(2, '0')).join('');
  return `${h.slice(0, 8)}-${h.slice(8, 12)}-${h.slice(12, 16)}-${h.slice(16, 20)}-${h.slice(20)}`;
}

/**
 * Bandeja de salida de la web: lo que se escribe se muestra al instante con reloj y se manda en orden
 * en cuanto hay conexion. Vive en memoria (no en localStorage): la bandeja de la planta es compartida
 * y dejar texto pendiente en el navegador lo enviaria a nombre de quien inicie sesion despues.
 */
@Injectable({providedIn:'root'})
export class MensajeriaSalidaService {
  private readonly api = inject(MensajeriaService);
  readonly cola = signal<MensajeEnCola[]>([]);
  /** El servidor confirmo un mensaje de la cola; la pantalla lo cambia por el definitivo. */
  readonly confirmados$ = new Subject<MensajeProveedor>();
  private enviando = false;
  private intento = 0;
  private reintento?: ReturnType<typeof setTimeout>;

  encolar(productorId:number, cuerpo:string): MensajeEnCola {
    const mensaje: MensajeEnCola = { clientUuid: nuevoUuid(), productorId, cuerpo, creadoEn: new Date().toISOString(), estado: 'PENDIENTE' };
    this.cola.update((c) => [...c, mensaje]);
    this.vaciar();
    return mensaje;
  }

  /** Manda lo pendiente, de a uno y en el orden en que se escribio. */
  vaciar(): void {
    if (this.enviando) return;
    const siguiente = this.cola().find((m) => m.estado === 'PENDIENTE');
    if (!siguiente) { this.intento = 0; return; }
    clearTimeout(this.reintento);
    this.enviando = true;
    this.api.enviar(siguiente.productorId, siguiente.cuerpo, siguiente.clientUuid).subscribe({
      next: (mensaje) => {
        this.enviando = false;
        this.intento = 0;
        this.confirmar(siguiente.clientUuid);
        this.confirmados$.next(mensaje);
        this.vaciar();
      },
      error: (e: HttpErrorResponse) => {
        this.enviando = false;
        if (esErrorDefinitivo(e.status)) {
          this.marcar(siguiente.clientUuid, 'ERROR', e.error?.message || 'No se pudo enviar.');
          this.vaciar();
        } else {
          // Sin red o servidor caido: el mensaje sigue pendiente y se reintenta con espera creciente.
          this.reintento = setTimeout(() => this.vaciar(), esperaReconexion(this.intento++));
        }
      },
    });
  }

  /** El mensaje ya esta en el servidor (por la respuesta del POST o porque llego por el canal). */
  confirmar(clientUuid:string|null|undefined): void {
    if (!clientUuid) return;
    this.cola.update((c) => c.filter((m) => m.clientUuid !== clientUuid));
  }

  reintentar(clientUuid:string): void {
    this.marcar(clientUuid, 'PENDIENTE');
    this.vaciar();
  }

  eliminar(clientUuid:string): void {
    this.confirmar(clientUuid);
  }

  pendientes(): number {
    return this.cola().filter((m) => m.estado === 'PENDIENTE').length;
  }

  private marcar(clientUuid:string, estado:EstadoEnCola, error?:string): void {
    this.cola.update((c) => c.map((m) => (m.clientUuid === clientUuid ? { ...m, estado, error } : m)));
  }
}
