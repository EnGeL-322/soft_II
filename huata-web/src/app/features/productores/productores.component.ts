import { Component, OnInit, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { EstadoProductor, PageResponse, Productor, Ruta } from '../../core/models/models';
import { ProductoresService, ProductorRequest } from '../../core/services/productores.service';
import { RutasService } from '../../core/services/rutas.service';
import { BadgeEstadoComponent } from '../../shared/ui/badge-estado.component';
import { PaginadorComponent } from '../../shared/ui/paginador.component';

const VACIO: ProductorRequest = { codigo: '', nombres: '', apellidos: '', documentoIdentidad: '', telefono: '', notas: '' };

@Component({
  selector: 'app-productores',
  standalone: true,
  imports: [FormsModule, BadgeEstadoComponent, PaginadorComponent],
  template: `
    <div class="page-head">
      <div>
        <div class="kicker">Proveedores</div>
        <h1>Productores</h1>
        <p>
          Administración supervisa y consulta; el registro operativo (recojo, calidad) lo hacen acopiadores y
          planta.
        </p>
      </div>
      <div class="actions">
        <button type="button" class="btn btn-primary" (click)="nuevo()">+ Nuevo productor</button>
      </div>
    </div>

    <div class="toolbar">
      <input type="text" placeholder="Buscar por código o nombre…" [(ngModel)]="q" (ngModelChange)="buscar()" />
      <select [(ngModel)]="estado" (ngModelChange)="buscar()">
        <option [ngValue]="undefined">Todos los estados</option>
        <option value="ACTIVO">Activo</option>
        <option value="EN_REVISION">En revisión</option>
        <option value="INACTIVO">Inactivo</option>
      </select>
      <select [(ngModel)]="rutaId" (ngModelChange)="buscar()">
        <option [ngValue]="undefined">Todas las rutas</option>
        @for (r of rutas(); track r.id) {
          <option [ngValue]="r.id">{{ r.nombre }}</option>
        }
      </select>
    </div>

    <div class="tablebox">
      <table>
        <thead>
          <tr>
            <th>Código</th>
            <th>Nombre</th>
            <th>Ruta</th>
            <th>Centro</th>
            <th>Estado</th>
            <th class="no-print">Acciones</th>
          </tr>
        </thead>
        <tbody>
          @for (p of pagina()?.content ?? []; track p.id) {
            <tr>
              <td>{{ p.codigo }}</td>
              <td>{{ p.nombres }} {{ p.apellidos }}</td>
              <td>{{ p.rutaNombre ?? '—' }}</td>
              <td>{{ p.centroAcopioNombre ?? '—' }}</td>
              <td><app-badge-estado [estado]="p.estado" /></td>
              <td style="white-space:nowrap">
                <button type="button" class="btn btn-soft" (click)="editar(p)">Editar</button>
                <button type="button" class="btn btn-blue" (click)="irA(p, 'historial')">Historial de calidad</button>
                <button type="button" class="btn btn-amber" (click)="irA(p, 'seguimiento')">Seguimiento</button>
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

    @if (mostrarForm()) {
      <div class="table-title">
        <div>
          <h3>{{ editandoId() ? 'Editar productor' : 'Nuevo productor' }}</h3>
        </div>
      </div>
      <form class="card" (ngSubmit)="guardar()">
        <div class="form-grid">
          <div class="field">
            <label>Código</label>
            <input type="text" [(ngModel)]="form.codigo" name="codigo" required [disabled]="!!editandoId()" />
          </div>
          <div class="field">
            <label>Nombres</label>
            <input type="text" [(ngModel)]="form.nombres" name="nombres" required />
          </div>
          <div class="field">
            <label>Apellidos</label>
            <input type="text" [(ngModel)]="form.apellidos" name="apellidos" required />
          </div>
          <div class="field">
            <label>Documento de identidad</label>
            <input type="text" [(ngModel)]="form.documentoIdentidad" name="documentoIdentidad" />
          </div>
          <div class="field">
            <label>Teléfono</label>
            <input type="text" [(ngModel)]="form.telefono" name="telefono" />
          </div>
          <div class="field">
            <label>Ruta</label>
            <select [(ngModel)]="form.rutaId" name="rutaId">
              <option [ngValue]="undefined">Sin asignar</option>
              @for (r of rutas(); track r.id) {
                <option [ngValue]="r.id">{{ r.nombre }}</option>
              }
            </select>
          </div>
        </div>
        <div class="field" style="margin-top:12px">
          <label>Notas</label>
          <textarea [(ngModel)]="form.notas" name="notas"></textarea>
        </div>
        <div class="actions" style="margin-top:14px">
          <button type="submit" class="btn btn-primary">Guardar</button>
          <button type="button" class="btn btn-soft" (click)="cerrarForm()">Cancelar</button>
        </div>
      </form>
    }
  `,
})
export class ProductoresComponent implements OnInit {
  readonly pagina = signal<PageResponse<Productor> | null>(null);
  readonly rutas = signal<Ruta[]>([]);
  readonly mostrarForm = signal(false);
  readonly editandoId = signal<number | null>(null);

  q = '';
  estado?: EstadoProductor;
  rutaId?: number;
  page = 0;
  form: ProductorRequest = { ...VACIO };

  constructor(
    private readonly productoresService: ProductoresService,
    private readonly rutasService: RutasService,
    private readonly router: Router,
  ) {}

  ngOnInit(): void {
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
    this.productoresService
      .listar({ q: this.q, estado: this.estado, rutaId: this.rutaId, page: this.page })
      .subscribe((pg) => this.pagina.set(pg));
  }

  nuevo(): void {
    this.editandoId.set(null);
    this.form = { ...VACIO };
    this.mostrarForm.set(true);
  }

  editar(p: Productor): void {
    this.editandoId.set(p.id);
    this.form = {
      codigo: p.codigo,
      nombres: p.nombres,
      apellidos: p.apellidos,
      documentoIdentidad: p.documentoIdentidad,
      telefono: p.telefono,
      rutaId: p.rutaId,
      centroAcopioId: p.centroAcopioId,
      notas: p.notas,
    };
    this.mostrarForm.set(true);
  }

  cerrarForm(): void {
    this.mostrarForm.set(false);
  }

  guardar(): void {
    const id = this.editandoId();
    const accion = id ? this.productoresService.actualizar(id, this.form) : this.productoresService.crear(this.form);
    accion.subscribe(() => {
      this.mostrarForm.set(false);
      this.cargar();
    });
  }

  irA(p: Productor, tab: 'historial' | 'seguimiento'): void {
    this.router.navigate(['/calidad'], { queryParams: { productorId: p.id, tab } });
  }
}
