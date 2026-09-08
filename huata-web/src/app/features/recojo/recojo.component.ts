import { DatePipe } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { EstadoRecojo, PageResponse, Productor, Recojo, Ruta } from '../../core/models/models';
import { ProductoresService } from '../../core/services/productores.service';
import { RecojoService } from '../../core/services/recojo.service';
import { RutasService } from '../../core/services/rutas.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

@Component({
  selector: 'app-recojo',
  standalone: true,
  imports: [FormsModule, DatePipe, BadgeEstadoComponent, PaginadorComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Recepción / acopio</div>
        <h1>Recojos de leche</h1>
        <p>
          Registro del acopio en campo. El <code>clientUuid</code> de cada recojo es la llave que evitará
          duplicados cuando la app móvil (Fase 2) sincronice offline.
        </p>
      </div>
      <div class="actions">
        <button type="button" class="btn btn-primary" (click)="mostrarForm.set(!mostrarForm())">
          + Registrar recojo
        </button>
      </div>
    </div>

    @if (mostrarForm()) {
      <form class="card" style="margin-bottom:20px" (ngSubmit)="registrar()">
        <div class="form-grid">
          <div class="field">
            <label>Productor</label>
            <select [(ngModel)]="form.productorId" name="productorId" required>
              <option [ngValue]="undefined" disabled>Seleccionar…</option>
              @for (p of productores(); track p.id) {
                <option [ngValue]="p.id">{{ p.codigo }} · {{ p.nombres }} {{ p.apellidos }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label>Ruta</label>
            <select [(ngModel)]="form.rutaId" name="rutaId">
              <option [ngValue]="undefined">Según el productor</option>
              @for (r of rutas(); track r.id) {
                <option [ngValue]="r.id">{{ r.nombre }}</option>
              }
            </select>
          </div>
          <div class="field">
            <label>Fecha y hora</label>
            <input type="datetime-local" [(ngModel)]="form.fechaHoraLocal" name="fechaHora" required />
          </div>
          <div class="field">
            <label>Litros</label>
            <input type="number" step="0.1" min="0" [(ngModel)]="form.litros" name="litros" required />
          </div>
          <div class="field">
            <label>Temperatura (°C)</label>
            <input type="number" step="0.1" [(ngModel)]="form.temperatura" name="temperatura" />
          </div>
        </div>
        <div class="actions" style="margin-top:14px">
          <button type="submit" class="btn btn-primary">Guardar recojo</button>
          <button type="button" class="btn btn-soft" (click)="mostrarForm.set(false)">Cancelar</button>
        </div>
      </form>
    }

    <div class="toolbar">
      <select [(ngModel)]="filtroProductorId" (ngModelChange)="buscar()">
        <option [ngValue]="undefined">Todos los productores</option>
        @for (p of productores(); track p.id) {
          <option [ngValue]="p.id">{{ p.codigo }} · {{ p.nombres }} {{ p.apellidos }}</option>
        }
      </select>
      <select [(ngModel)]="filtroEstado" (ngModelChange)="buscar()">
        <option [ngValue]="undefined">Todos los estados</option>
        <option value="SYNCED">Sincronizado</option>
        <option value="PENDING_SYNC">Pendiente de sincronizar</option>
        <option value="CANCELLED">Cancelado</option>
      </select>
    </div>

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Fecha</th>
            <th>Productor</th>
            <th>Ruta</th>
            <th class="num">Litros</th>
            <th>Estado</th>
            <th class="no-print">Acciones</th>
          </tr>
        </thead>
        <tbody>
          @for (r of pagina()?.content ?? []; track r.id) {
            <tr>
              <td>{{ r.fechaHora | date: 'short' }}</td>
              <td>{{ r.productorNombre }}</td>
              <td>{{ r.rutaNombre ?? '—' }}</td>
              <td class="num">{{ r.litros }}</td>
              <td><app-badge-estado [estado]="r.estado" /></td>
              <td>
                @if (r.estado !== 'CANCELLED') {
                  <button type="button" class="btn btn-danger" (click)="cancelar(r)">Cancelar</button>
                }
              </td>
            </tr>
          }
          @if ((pagina()?.content?.length ?? 0) === 0) {
            <tr>
              <td colspan="6" class="muted" style="text-align:center; padding:24px">Sin resultados.</td>
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
  `,
})
export class RecojoComponent implements OnInit {
  readonly pagina = signal<PageResponse<Recojo> | null>(null);
  readonly productores = signal<Productor[]>([]);
  readonly rutas = signal<Ruta[]>([]);
  readonly mostrarForm = signal(false);

  filtroProductorId?: number;
  filtroEstado?: EstadoRecojo;
  page = 0;

  form: { productorId?: number; rutaId?: number; fechaHoraLocal: string; litros?: number; temperatura?: number } = {
    fechaHoraLocal: new Date().toISOString().slice(0, 16),
  };

  constructor(
    private readonly recojoService: RecojoService,
    private readonly productoresService: ProductoresService,
    private readonly rutasService: RutasService,
  ) {}

  ngOnInit(): void {
    this.productoresService.listar({ size: 200 }).subscribe((pg) => this.productores.set(pg.content));
    this.rutasService.listar().subscribe((pg) => this.rutas.set(pg.content));
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
    this.recojoService
      .listar({ productorId: this.filtroProductorId, estado: this.filtroEstado, page: this.page })
      .subscribe((pg) => this.pagina.set(pg));
  }

  registrar(): void {
    if (!this.form.productorId || !this.form.litros) {
      return;
    }
    this.recojoService
      .registrar({
        productorId: this.form.productorId,
        rutaId: this.form.rutaId,
        fechaHora: new Date(this.form.fechaHoraLocal).toISOString(),
        litros: this.form.litros,
        temperatura: this.form.temperatura,
      })
      .subscribe(() => {
        this.mostrarForm.set(false);
        this.form = { fechaHoraLocal: new Date().toISOString().slice(0, 16) };
        this.buscar();
      });
  }

  cancelar(r: Recojo): void {
    const motivo = window.prompt('Motivo de la cancelación:');
    if (!motivo) {
      return;
    }
    this.recojoService.cancelar(r.id, motivo).subscribe(() => this.cargar());
  }
}
