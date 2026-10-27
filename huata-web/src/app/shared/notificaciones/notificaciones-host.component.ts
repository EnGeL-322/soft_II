import { Component, inject } from '@angular/core';
import { NotificacionesService, TipoNotificacion } from '../../core/notificaciones/notificaciones.service';

const ICONO: Record<TipoNotificacion, string> = { exito: '✓', error: '!', aviso: '!', info: 'i' };
const ETIQUETA: Record<TipoNotificacion, string> = {
  exito: 'Listo',
  error: 'Error',
  aviso: 'Atención',
  info: 'Información',
};

/** Pila de notificaciones rápidas, arriba a la derecha. Se coloca una sola vez en la raíz. */
@Component({
  selector: 'app-notificaciones',
  standalone: true,
  template: `
    <!-- Errores y avisos se anuncian de inmediato; éxito e info, sin interrumpir al lector. -->
    <div class="pila" aria-live="polite" aria-relevant="additions">
      @for (n of servicio.notificaciones(); track n.id) {
        <div
          class="toast"
          [class]="'toast toast-' + n.tipo"
          [attr.role]="n.tipo === 'error' || n.tipo === 'aviso' ? 'alert' : 'status'"
          (mouseenter)="servicio.pausar(n.id)"
          (mouseleave)="servicio.reanudar(n.id)"
          (focusin)="servicio.pausar(n.id)"
          (focusout)="servicio.reanudar(n.id)"
        >
          <span class="icono" aria-hidden="true">{{ icono[n.tipo] }}</span>
          <p>
            <span class="sr-only">{{ etiqueta[n.tipo] }}: </span>{{ n.mensaje }}
            @if (n.repeticiones > 1) {
              <span class="veces">×{{ n.repeticiones }}</span>
            }
          </p>
          <button type="button" class="cerrar" (click)="servicio.cerrar(n.id)" aria-label="Cerrar notificación">×</button>
        </div>
      }
    </div>
  `,
  styleUrl: './notificaciones.css',
})
export class NotificacionesHostComponent {
  protected readonly servicio = inject(NotificacionesService);
  protected readonly icono = ICONO;
  protected readonly etiqueta = ETIQUETA;
}
