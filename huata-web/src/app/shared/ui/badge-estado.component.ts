import { Component, computed, input } from '@angular/core';

const MAPA_CLASE: Record<string, string> = {
  ACEPTADA: 'badge-verde',
  ACTIVO: 'badge-verde',
  SYNCED: 'badge-verde',
  CERRADA: 'badge-verde',
  CLOSED: 'badge-verde',
  REVIEWED: 'badge-verde',

  OBSERVADA: 'badge-ambar',
  EN_REVISION: 'badge-ambar',
  EN_SEGUIMIENTO: 'badge-ambar',
  PENDING_SYNC: 'badge-ambar',
  SAVED_OFFLINE: 'badge-ambar',
  CALCULATED: 'badge-ambar',
  ABIERTA: 'badge-ambar',
  DRAFT: 'badge-ambar',

  RECHAZADA: 'badge-rojo',
  INACTIVO: 'badge-rojo',
  CANCELLED: 'badge-rojo',
};

const ETIQUETAS: Record<string, string> = {
  ACEPTADA: 'Apta', OBSERVADA: 'Observada', RECHAZADA: 'Rechazada',
  ACTIVO: 'Activo', INACTIVO: 'Inactivo', EN_REVISION: 'En revisión',
  SYNCED: 'Registrada', CANCELLED: 'Cancelada', DRAFT: 'Borrador',
  CALCULATED: 'Calculada', REVIEWED: 'Revisada', CLOSED: 'Cerrada',
  CERRADA: 'Cerrada', ABIERTA: 'Abierta', EN_SEGUIMIENTO: 'En seguimiento',
  PENDING_SYNC: 'Pendiente de sincronizar', SAVED_OFFLINE: 'Guardada sin conexión',
};

/** Distingue ACEPTADA/OBSERVADA/RECHAZADA (y estados analogos) con color, como pide el UI/UX de Huata. */
@Component({
  selector: 'app-badge-estado',
  standalone: true,
  template: `<span class="badge" [class]="clase()">{{ etiqueta() }}</span>`,
})
export class BadgeEstadoComponent {
  estado = input.required<string>();

  clase = computed(() => MAPA_CLASE[this.estado()] ?? 'badge-gris');
  etiqueta = computed(() => ETIQUETAS[this.estado()] ?? this.estado().replaceAll('_', ' '));
}
