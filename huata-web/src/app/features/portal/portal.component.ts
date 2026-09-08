import { DatePipe } from '@angular/common';
import { Component, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ConsultaPublica } from '../../core/models/models';
import { PortalService } from '../../core/services/portal.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

/**
 * Portal público: "Calidad visible para la comunidad" del prototipo. Sin login — un proveedor
 * consulta su propio historial con solo su código (DEMO-001, DEMO-002…).
 */
@Component({
  selector: 'app-portal',
  standalone: true,
  imports: [FormsModule, DatePipe, RouterLink, BadgeEstadoComponent],
  template: `
    <div class="login-shell">
      <div class="login-card" style="max-width:480px">
        <div class="kicker">Huata Lácteos</div>
        <h1>Calidad visible para la comunidad</h1>
        <p>Ingresa tu código de proveedor para consultar tu historial de calidad. Prueba con <b>DEMO-001</b>.</p>

        <div class="form-grid" style="grid-template-columns: 1fr">
          <div class="field">
            <label>Código de proveedor</label>
            <input type="text" [(ngModel)]="codigo" placeholder="DEMO-001" (keydown.enter)="buscar()" />
          </div>
        </div>

        <button type="button" class="btn btn-primary" style="width:100%; margin-top:12px" (click)="buscar()">
          Consultar
        </button>

        @if (error()) {
          <p class="error-text" style="margin-top:12px">{{ error() }}</p>
        }

        @if (resultado(); as r) {
          <div class="card" style="margin-top:18px; text-align:left">
            <div style="display:flex; justify-content:space-between; align-items:center">
              <strong>{{ r.codigo }}</strong>
              <app-badge-estado [estado]="r.estado" />
            </div>
            <p class="muted" style="font-size:11.5px; margin:4px 0 12px">Zona: {{ r.zona ?? '—' }}</p>

            @for (h of r.historial; track $index) {
              <div style="display:flex; justify-content:space-between; font-size:11.5px; padding:6px 0; border-top:1px solid var(--line)">
                <span>{{ h.fecha | date: 'short' }}</span>
                <app-badge-estado [estado]="h.resultado" />
                <span>{{ h.litros }} L</span>
              </div>
            }
            @if (r.historial.length === 0) {
              <p class="muted" style="font-size:11.5px">Sin controles de calidad registrados todavía.</p>
            }
          </div>
        }

        <p class="muted" style="margin-top:20px; font-size:11px">
          <a routerLink="/login">Soy personal de Huata → ir al panel administrativo</a>
        </p>
      </div>
    </div>
  `,
})
export class PortalComponent {
  codigo = '';
  readonly resultado = signal<ConsultaPublica | null>(null);
  readonly error = signal('');

  constructor(private readonly portalService: PortalService) {}

  buscar(): void {
    this.error.set('');
    this.resultado.set(null);
    if (!this.codigo.trim()) {
      return;
    }
    this.portalService.consultar(this.codigo.trim()).subscribe({
      next: (r) => this.resultado.set(r),
      error: () => this.error.set('No se encontró ese código de proveedor.'),
    });
  }
}
