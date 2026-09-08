import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardResponse } from '../../core/models/models';
import { ReportesService } from '../../core/services/reportes.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

/** KPIs primero + filtros escribibles, igual que "Reportes v1.11" del prototipo HTML de Huata. */
@Component({
  selector: 'app-reportes',
  standalone: true,
  imports: [FormsModule, DecimalPipe, BadgeEstadoComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Seguimiento de producción</div>
        <h1>Reportes</h1>
        <p>Producción, calidad, incidencias y liquidaciones por periodo.</p>
      </div>
    </div>

    <div class="toolbar">
      <label class="muted">Desde</label>
      <input type="date" [(ngModel)]="desde" />
      <label class="muted">Hasta</label>
      <input type="date" [(ngModel)]="hasta" />
      <button type="button" class="btn btn-primary" (click)="aplicar()">Aplicar filtro</button>
    </div>

    @if (data(); as d) {
      <div class="kpi-grid">
        <div class="kpi"><span>Litros totales</span><strong>{{ d.litrosTotales | number: '1.0-1' }}</strong></div>
        <div class="kpi"><span>Recojos</span><strong>{{ d.recojosTotales }}</strong></div>
        <div class="kpi"><span>Incidencias abiertas</span><strong>{{ d.incidenciasAbiertas }}</strong></div>
        <div class="kpi"><span>Liquidaciones pendientes</span><strong>{{ d.liquidacionesPendientes }}</strong></div>
      </div>

      <div class="table-title">
        <div><h3>Producción por ruta</h3></div>
      </div>
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Ruta</th>
              <th class="num">Litros</th>
              <th class="num">Recojos</th>
            </tr>
          </thead>
          <tbody>
            @for (r of d.produccionPorRuta; track r.clave) {
              <tr>
                <td>{{ r.clave }}</td>
                <td class="num">{{ r.litros | number: '1.0-1' }}</td>
                <td class="num">{{ r.recojos }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="table-title">
        <div><h3>Producción por productor</h3></div>
      </div>
      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Productor</th>
              <th class="num">Litros</th>
              <th class="num">Recojos</th>
            </tr>
          </thead>
          <tbody>
            @for (p of d.produccionPorProductor; track p.clave) {
              <tr>
                <td>{{ p.clave }}</td>
                <td class="num">{{ p.litros | number: '1.0-1' }}</td>
                <td class="num">{{ p.recojos }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>

      <div class="table-title">
        <div><h3>Tendencia de calidad</h3></div>
      </div>
      <div class="card" style="display:flex; gap:18px; flex-wrap:wrap">
        @for (t of d.tendenciaCalidad; track t.resultado) {
          <div style="display:flex; align-items:center; gap:8px">
            <app-badge-estado [estado]="t.resultado" />
            <strong>{{ t.cantidad }}</strong>
          </div>
        }
      </div>
    }
  `,
})
export class ReportesComponent implements OnInit {
  readonly data = signal<DashboardResponse | null>(null);

  desde = '';
  hasta = '';

  constructor(private readonly reportesService: ReportesService) {}

  ngOnInit(): void {
    this.aplicar();
  }

  aplicar(): void {
    const desdeIso = this.desde ? new Date(this.desde).toISOString() : undefined;
    const hastaIso = this.hasta ? new Date(this.hasta + 'T23:59:59').toISOString() : undefined;
    this.reportesService.dashboard(desdeIso, hastaIso).subscribe((d) => this.data.set(d));
  }
}
