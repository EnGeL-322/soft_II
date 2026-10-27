import { Component, computed, input } from '@angular/core';

const MAPA_CLASE: Record<string, string> = {
  ACEPTADA: 'badge-verde',
  ACTIVO: 'badge-verde',
  SYNCED: 'badge-verde',
  CERRADA: 'badge-verde',
  CLOSED: 'badge-verde',
  REVIEWED: 'badge-verde',
  APROBADA: 'badge-verde',
  ACOPIADO: 'badge-verde',

  OBSERVADA: 'badge-ambar',
  EN_REVISION: 'badge-ambar',
  PENDIENTE_APROBACION: 'badge-ambar',
  EN_SEGUIMIENTO: 'badge-ambar',
  PENDING_SYNC: 'badge-ambar',
  SAVED_OFFLINE: 'badge-ambar',
  CALCULATED: 'badge-ambar',
  ABIERTA: 'badge-ambar',
  DRAFT: 'badge-ambar',

  RECHAZADA: 'badge-rojo',
  INACTIVO: 'badge-rojo',
  RETIRADO: 'badge-rojo',
  CANCELLED: 'badge-rojo',
  NO_ENTREGADO: 'badge-rojo',

  // Gris = pendiente: todavía no hay resultado ni decisión.
  PENDIENTE: 'badge-gris',
};

const ETIQUETAS: Record<string, string> = {
  ACEPTADA: 'Apta', OBSERVADA: 'Observada', RECHAZADA: 'Rechazada',
  ACTIVO: 'Activo', INACTIVO: 'Inactivo', EN_REVISION: 'En revisión',
  PENDIENTE_APROBACION: 'Pendiente de aprobación', RETIRADO: 'Retirado',
  SYNCED: 'Registrada', CANCELLED: 'Cancelada', DRAFT: 'Borrador',
  CALCULATED: 'Calculada', REVIEWED: 'Revisada', CLOSED: 'Cerrada',
  CERRADA: 'Cerrada', ABIERTA: 'Abierta', EN_SEGUIMIENTO: 'En seguimiento',
  PENDING_SYNC: 'Pendiente de sincronizar', SAVED_OFFLINE: 'Guardada sin conexión',
  PENDIENTE: 'Pendiente', APROBADA: 'Aprobada',
  ACOPIADO: 'Recolectado', NO_ENTREGADO: 'No recolectado',
};

/** Estado desconocido: «EN CAMINO» se muestra «En camino», no en mayúsculas crudas. */
function capitalizar(texto: string): string {
  const t = texto.toLowerCase();
  return t.charAt(0).toUpperCase() + t.slice(1);
}

/** Distingue ACEPTADA/OBSERVADA/RECHAZADA (y estados analogos) con color, como pide el UI/UX de Huata. */
@Component({
  selector: 'app-badge-estado',
  standalone: true,
  template: `<span class="badge" [class]="clase()">{{ etiqueta() }}</span>`,
})
export class BadgeEstadoComponent {
  estado = input.required<string>();

  clase = computed(() => MAPA_CLASE[this.estado()] ?? 'badge-gris');
  etiqueta = computed(() => ETIQUETAS[this.estado()] ?? capitalizar(this.estado().replaceAll('_', ' ')));
}
