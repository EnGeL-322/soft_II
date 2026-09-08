import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { DashboardResponse } from '../../core/models/models';
import { ReportesService } from '../../core/services/reportes.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';

/** KPIs primero, filtros/detalle despues: mismo criterio que "Reportes v1.11" del prototipo HTML. */
@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [DecimalPipe, BadgeEstadoComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Panel general</div>
        <h1>Dashboard</h1>
        <p>Resumen de los últimos 30 días: producción, calidad, incidencias y liquidaciones pendientes.</p>
      </div>
    </div>

    @if (data(); as d) {
      <div class="kpi-grid">
        <div class="kpi">
          <span>Litros totales</span>
          <strong>{{ d.litrosTotales | number: '1.0-1' }}</strong>
        </div>
        <div class="kpi">
          <span>Recojos</span>
          <strong>{{ d.recojosTotales }}</strong>
        </div>
        <div class="kpi">
          <span>Incidencias abiertas</span>
          <strong>{{ d.incidenciasAbiertas }}</strong>
        </div>
        <div class="kpi">
          <span>Liquidaciones pendientes</span>
          <strong>{{ d.liquidacionesPendientes }}</strong>
        </div>
      </div>

      <div class="table-title">
        <div><h3>Producción por ruta</h3></div>
      </div>
      <div class="card">
        @if (d.produccionPorRuta.length === 0) {
          <p class="muted">Sin recojos en el periodo.</p>
        }
        <div class="bars">
          @for (r of d.produccionPorRuta; track r.clave) {
            <div class="barrow">
              <span>{{ r.clave }}</span>
              <div class="bartrack"><div class="barfill" [style.width.%]="pct(r.litros, maxRuta(d))"></div></div>
              <span class="num">{{ r.litros | number: '1.0-1' }} L</span>
            </div>
          }
        </div>
      </div>

      <div class="table-title">
        <div><h3>Producción por productor (top 15)</h3></div>
      </div>
      <div class="card">
        @if (d.produccionPorProductor.length === 0) {
          <p class="muted">Sin recojos en el periodo.</p>
        }
        <div class="bars">
          @for (p of d.produccionPorProductor; track p.clave) {
            <div class="barrow">
              <span>{{ p.clave }}</span>
              <div class="bartrack"><div class="barfill" [style.width.%]="pct(p.litros, maxProductor(d))"></div></div>
              <span class="num">{{ p.litros | number: '1.0-1' }} L</span>
            </div>
          }
        </div>
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
    } @else {
      <p class="muted">Cargando…</p>
    }
  `,
})
export class DashboardComponent implements OnInit {
  readonly data = signal<DashboardResponse | null>(null);

  constructor(private readonly reportes: ReportesService) {}

  ngOnInit(): void {
    this.reportes.dashboard().subscribe((d) => this.data.set(d));
  }

  maxRuta(d: DashboardResponse): number {
    return Math.max(1, ...d.produccionPorRuta.map((r) => r.litros));
  }

  maxProductor(d: DashboardResponse): number {
    return Math.max(1, ...d.produccionPorProductor.map((r) => r.litros));
  }

  pct(valor: number, max: number): number {
    return Math.min(100, (valor / max) * 100);
  }
}
