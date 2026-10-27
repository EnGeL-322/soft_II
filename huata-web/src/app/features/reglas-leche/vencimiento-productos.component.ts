import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Producto } from '../../core/models/models';
import { AuthService } from '../../core/services/auth.service';
import { ProduccionService } from '../../core/services/produccion.service';
import { NotificacionesService } from '../../core/notificaciones/notificaciones.service';
import { mensajeDeError } from '../../core/notificaciones/mensaje-error';

/**
 * Cuántos días dura cada producto desde que se produce (pedido del cliente, 2026-10-27: se configura
 * en Configuración). Todo lote nace con su vencimiento; sin este dato el producto no se puede producir.
 */
@Component({
  selector: 'app-vencimiento-productos',
  standalone: true,
  imports: [FormsModule],
  template: `
    <section class="card panel">
      <div class="panel-cabecera">
        <h2>Vencimiento de productos</h2>
        @if (sinConfigurar() > 0) {
          <span class="badge badge-ambar">{{ sinConfigurar() }} sin configurar</span>
        } @else if (!puedeEditar()) {
          <span class="badge badge-gris">Solo lectura</span>
        }
      </div>
      <p class="muted vp-ayuda">
        Cuántos días dura cada producto desde que se produce. Cada lote vence en esa fecha y al vender sale
        primero lo que vence antes. Un producto sin días no se puede producir.
      </p>
      <ul class="vp-lista">
        @for (p of productos(); track p.id) {
          <li>
            <span class="vp-nombre">{{ p.nombre }}</span>
            @if (puedeEditar()) {
              <span class="vp-dias">
                <input
                  type="number"
                  min="1"
                  max="3650"
                  step="1"
                  [attr.aria-label]="'Días que dura ' + p.nombre"
                  [ngModel]="dias[p.id]"
                  (ngModelChange)="dias[p.id] = $event"
                  placeholder="—"
                />
                días
              </span>
              <button
                type="button"
                class="btn btn-soft btn-sm"
                [disabled]="guardandoId() === p.id || !cambio(p)"
                (click)="guardar(p)"
              >
                {{ guardandoId() === p.id ? 'Guardando…' : 'Guardar' }}
              </button>
            } @else {
              <span class="vp-dias">{{ p.vidaUtilDias ? p.vidaUtilDias + ' días' : 'Sin configurar' }}</span>
            }
          </li>
        } @empty {
          <li class="muted">Todavía no hay productos. Créalos en Recetas y productos.</li>
        }
      </ul>
    </section>
  `,
  styles: `
    .vp-ayuda { margin: 0 0 12px; font-size: 13px; }
    .vp-lista { list-style: none; margin: 0; padding: 0; }
    .vp-lista li { display: flex; align-items: center; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--line); }
    .vp-lista li:last-child { border-bottom: 0; }
    .vp-nombre { flex: 1; font-weight: 600; }
    .vp-dias { display: inline-flex; align-items: center; gap: 6px; color: var(--muted); }
    .vp-dias input { width: 80px; text-align: right; }
  `,
})
export class VencimientoProductosComponent implements OnInit {
  private readonly produccion = inject(ProduccionService);
  private readonly auth = inject(AuthService);
  private readonly notificaciones = inject(NotificacionesService);

  readonly productos = signal<Producto[]>([]);
  readonly guardandoId = signal<number | null>(null);
  readonly sinConfigurar = computed(() => this.productos().filter((p) => !p.vidaUtilDias).length);
  /** Lo escrito en cada fila, por id de producto. */
  dias: Record<number, number | null> = {};

  ngOnInit(): void {
    this.cargar();
  }

  puedeEditar(): boolean {
    return this.auth.puede('parametros.write') || this.auth.puede('recetas.write');
  }

  cambio(p: Producto): boolean {
    const d = this.dias[p.id];
    return !!d && d >= 1 && d !== p.vidaUtilDias;
  }

  private cargar(): void {
    this.produccion.productos().subscribe({
      next: (lista) => {
        this.productos.set(lista);
        this.dias = Object.fromEntries(lista.map((p) => [p.id, p.vidaUtilDias ?? null]));
      },
      error: () => this.notificaciones.error('No se pudo cargar la lista de productos.'),
    });
  }

  guardar(p: Producto): void {
    const d = this.dias[p.id];
    if (!d || d < 1) return;
    this.guardandoId.set(p.id);
    this.produccion.cambiarVencimiento(p.id, Math.round(d)).subscribe({
      next: (actualizado) => {
        this.guardandoId.set(null);
        this.productos.update((l) => l.map((x) => (x.id === p.id ? actualizado : x)));
        this.notificaciones.exito(`${p.nombre} vence a los ${actualizado.vidaUtilDias} días de producido.`);
      },
      error: (e) => {
        this.guardandoId.set(null);
        this.notificaciones.error(mensajeDeError(e, 'No se pudo guardar el vencimiento.'));
      },
    });
  }
}
