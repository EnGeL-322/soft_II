import { DecimalPipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EstadoLiquidacion, Liquidacion, PageResponse, Productor, TarifaLeche } from '../../core/models/models';
import { LiquidacionesService } from '../../core/services/liquidaciones.service';
import { ProductoresService } from '../../core/services/productores.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

@Component({
  selector: 'app-liquidaciones',
  standalone: true,
  imports: [FormsModule, DecimalPipe, BadgeEstadoComponent, PaginadorComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Pagos y liquidación</div>
        <h1>Liquidaciones</h1>
        <p>
          El precio de leche es una tarifa configurable (no una constante): confirmar los valores vigentes con
          Administración antes de producción.
        </p>
      </div>
    </div>

    <div class="tabs">
      <button type="button" class="tab-btn" [class.active]="tab() === 'liquidaciones'" (click)="tab.set('liquidaciones')">
        Liquidaciones
      </button>
      <button type="button" class="tab-btn" [class.active]="tab() === 'tarifas'" (click)="tab.set('tarifas')">
        Tarifas
      </button>
    </div>

    @if (tab() === 'liquidaciones') {
      <form class="card" style="margin-bottom:16px" (ngSubmit)="calcular()">
        <div class="form-grid">
          <div class="field">
            <label>Productor</label>
            <select [(ngModel)]="calc.productorId" name="productorId" required>
              <option [ngValue]="undefined" disabled>Seleccionar…</option>
              @for (p of productores(); track p.id) {
                <option [ngValue]="p.id">{{ p.codigo }} · {{ p.nombres }} {{ p.apellidos }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label>Periodo inicio</label>
            <input type="date" [(ngModel)]="calc.periodoInicio" name="periodoInicio" required />
          </div>
          <div class="field">
            <label>Periodo fin</label>
            <input type="date" [(ngModel)]="calc.periodoFin" name="periodoFin" required />
          </div>
        </div>
        <div class="actions" style="margin-top:12px">
          <button type="submit" class="btn btn-primary">Calcular liquidación</button>
        </div>
      </form>

      @if (ultimaCalculada(); as l) {
        <div class="note" style="margin-bottom:16px">
          Liquidación calculada para {{ l.productorNombre }}: {{ l.litrosTotales | number: '1.0-1' }} L ·
          S/ {{ l.montoTotal | number: '1.2-2' }} ({{ l.detalle.length }} recojos).
        </div>
      }

      <div class="toolbar">
        <select [(ngModel)]="filtroEstado" (ngModelChange)="buscar()">
          <option [ngValue]="undefined">Todos los estados</option>
          <option value="CALCULATED">Calculada</option>
          <option value="REVIEWED">Revisada</option>
          <option value="CLOSED">Cerrada</option>
        </select>
      </div>

      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Productor</th>
              <th>Periodo</th>
              <th class="num">Litros</th>
              <th class="num">Monto (S/)</th>
              <th>Estado</th>
              <th class="no-print">Acciones</th>
            </tr>
          </thead>
          <tbody>
            @for (l of pagina()?.content ?? []; track l.id) {
              <tr>
                <td>{{ l.productorNombre }}</td>
                <td>{{ l.periodoInicio }} – {{ l.periodoFin }}</td>
                <td class="num">{{ l.litrosTotales | number: '1.0-1' }}</td>
                <td class="num">{{ l.montoTotal | number: '1.2-2' }}</td>
                <td><app-badge-estado [estado]="l.estado" /></td>
                <td style="white-space:nowrap">
                  @if (l.estado === 'CALCULATED') {
                    <button type="button" class="btn btn-soft" (click)="cambiarEstado(l, 'REVIEWED')">Revisar</button>
                  }
                  @if (l.estado === 'REVIEWED') {
                    <button type="button" class="btn btn-primary" (click)="cambiarEstado(l, 'CLOSED')">Cerrar</button>
                  }
                </td>
              </tr>
            }
            @if ((pagina()?.content?.length ?? 0) === 0) {
              <tr>
                <td colspan="6" class="muted" style="text-align:center; padding:24px">Sin liquidaciones.</td>
              </tr>
            }
          </tbody>
        </table>
        @if (pagina(); as pg) {
          <app-paginador
            [page]="pg.page"
            [totalElements]="pg.totalElements"
            [totalPages]="pg.totalPages"
            (anterior)="cambiarPagina(pg.page - 1)"
            (siguiente)="cambiarPagina(pg.page + 1)"
          />
        }
      </div>
    } @else {
      <form class="card" style="margin-bottom:16px" (ngSubmit)="crearTarifa()">
        <div class="form-grid">
          <div class="field">
            <label>Precio normal (S//L)</label>
            <input type="number" step="0.01" [(ngModel)]="nuevaTarifa.precioNormal" name="precioNormal" required />
          </div>
          <div class="field">
            <label>Precio observada (S//L)</label>
            <input type="number" step="0.01" [(ngModel)]="nuevaTarifa.precioObservada" name="precioObservada" required />
          </div>
          <div class="field">
            <label>Vigente desde</label>
            <input type="date" [(ngModel)]="nuevaTarifa.vigenteDesde" name="vigenteDesde" required />
          </div>
        </div>
        <div class="actions" style="margin-top:12px">
          <button type="submit" class="btn btn-primary">+ Nueva tarifa</button>
        </div>
      </form>

      <div class="tablebox">
        <table>
          <thead>
            <tr>
              <th>Vigente desde</th>
              <th>Vigente hasta</th>
              <th class="num">Precio normal</th>
              <th class="num">Precio observada</th>
            </tr>
          </thead>
          <tbody>
            @for (t of tarifas(); track t.id) {
              <tr>
                <td>{{ t.vigenteDesde }}</td>
                <td>{{ t.vigenteHasta ?? 'Vigente' }}</td>
                <td class="num">S/ {{ t.precioNormal | number: '1.2-2' }}</td>
                <td class="num">S/ {{ t.precioObservada | number: '1.2-2' }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    }
  `,
})
export class LiquidacionesComponent implements OnInit {
  readonly tab = signal<'liquidaciones' | 'tarifas'>('liquidaciones');
  readonly pagina = signal<PageResponse<Liquidacion> | null>(null);
  readonly productores = signal<Productor[]>([]);
  readonly tarifas = signal<TarifaLeche[]>([]);
  readonly ultimaCalculada = signal<Liquidacion | null>(null);

  filtroEstado?: EstadoLiquidacion;
  page = 0;

  calc: { productorId?: number; periodoInicio: string; periodoFin: string } = { periodoInicio: '', periodoFin: '' };
  nuevaTarifa: { precioNormal?: number; precioObservada?: number; vigenteDesde: string } = { vigenteDesde: '' };

  constructor(
    private readonly liquidacionesService: LiquidacionesService,
    private readonly productoresService: ProductoresService,
  ) {}

  ngOnInit(): void {
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.liquidacionesService.tarifas().subscribe((t) => this.tarifas.set(t));
    this.buscar();
  }

  buscar(): void {
    this.page = 0;
    this.cargar();
  }

  cambiarPagina(p: number): void {
    this.page = p;
    this.cargar();
  }

  private cargar(): void {
    this.liquidacionesService.listar({ estado: this.filtroEstado, page: this.page }).subscribe((pg) => this.pagina.set(pg));
  }

  calcular(): void {
    if (!this.calc.productorId) {
      return;
    }
    this.liquidacionesService
      .calcular({ productorId: this.calc.productorId, periodoInicio: this.calc.periodoInicio, periodoFin: this.calc.periodoFin })
      .subscribe((l) => {
        this.ultimaCalculada.set(l);
        this.cargar();
      });
  }

  cambiarEstado(l: Liquidacion, estado: EstadoLiquidacion): void {
    this.liquidacionesService.cambiarEstado(l.id, estado).subscribe(() => this.cargar());
  }

  crearTarifa(): void {
    if (this.nuevaTarifa.precioNormal == null || this.nuevaTarifa.precioObservada == null) {
      return;
    }
    this.liquidacionesService
      .crearTarifa({
        precioNormal: this.nuevaTarifa.precioNormal,
        precioObservada: this.nuevaTarifa.precioObservada,
        vigenteDesde: this.nuevaTarifa.vigenteDesde,
      })
      .subscribe(() => {
        this.nuevaTarifa = { vigenteDesde: '' };
        this.liquidacionesService.tarifas().subscribe((t) => this.tarifas.set(t));
      });
  }
}
